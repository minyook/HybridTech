package com.minyook.sllm2.gas

import org.json.JSONObject
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** TCP v1 receiver. Bound to a supplied AP address, never to all interfaces. */
class WifiMeterServer(address:InetAddress,port:Int,private val token:String,val registry:WifiMeterRegistry=WifiMeterRegistry()):AutoCloseable {
    private val running=AtomicBoolean(true)
    private val sockets=ConcurrentHashMap<Socket,Peer>()
    private val slots=Semaphore(WifiMeterProtocol.MAX_RECEIVERS)
    private val workers=Executors.newFixedThreadPool(WifiMeterProtocol.MAX_RECEIVERS){r->Thread(r,"meter-client").apply{isDaemon=true}}
    private val clock=Executors.newSingleThreadScheduledExecutor{r->Thread(r,"meter-heartbeat").apply{isDaemon=true}}
    private val server=ServerSocket().apply{reuseAddress=true;bind(InetSocketAddress(address,port))}
    val isRunning get()=running.get()
    val localPort get()=server.localPort
    private class Peer(val socket:Socket){val owner=UUID.randomUUID().toString();@Volatile var id:String?=null;@Volatile var ping:String?=null;@Volatile var sentNanos=0L
        @Synchronized fun send(json:JSONObject){socket.getOutputStream().write((json.toString()+"\n").toByteArray(Charsets.UTF_8));socket.getOutputStream().flush()}
    }
    init {
        Thread({acceptLoop()},"meter-accept").apply{isDaemon=true;start()}
        clock.scheduleWithFixedDelay({sockets.values.filter{it.id!=null}.forEach{peer->runCatching{val nonce=UUID.randomUUID().toString();peer.ping=nonce;peer.sentNanos=System.nanoTime();peer.send(JSONObject().put("type","ping").put("id",nonce))}.onFailure{runCatching{peer.socket.close()}}}},5,5,TimeUnit.SECONDS)
    }
    private fun acceptLoop(){
        while(running.get())try{
            val socket=server.accept()
            if(!slots.tryAcquire()){socket.close();continue}
            socket.tcpNoDelay=true;socket.keepAlive=true;socket.soTimeout=5000
            val peer=Peer(socket);sockets[socket]=peer
            try{workers.execute{receive(peer)}}catch(_:Exception){sockets.remove(socket);socket.close();slots.release()}
        }catch(_:Exception){if(running.get()){close();break}}
    }
    private fun receive(peer:Peer){
        var reason="연결 종료"
        try{
            val input=peer.socket.getInputStream().buffered()
            val hello=WifiMeterProtocol.hello(WifiMeterProtocol.json(WifiMeterProtocol.readFrame(input)?:return),token)
            if(!registry.connect(hello,peer.owner))throw MeterProtocolException("이미 사용 중인 장비 ID 또는 수신 한도")
            peer.id=hello.deviceId;peer.socket.soTimeout=15000
            peer.send(JSONObject().put("type","welcome").put("protocol",1).put("deviceId",hello.deviceId).put("pingIntervalMs",5000))
            while(running.get()){
                val frame=WifiMeterProtocol.readFrame(input)?:break
                val json=WifiMeterProtocol.json(frame)
                when(json.optString("type")){
                    "reading"->{
                        val sample=WifiMeterProtocol.sample(json)
                        val accepted=registry.reading(hello.deviceId,peer.owner,sample,System.currentTimeMillis(),System.nanoTime())
                        peer.send(JSONObject().put("type","ack").put("seq",sample.sequence).put("accepted",accepted))
                    }
                    "pong"->{val id=json.optString("id");if(id.isNotBlank()&&id==peer.ping){registry.rtt(hello.deviceId,peer.owner,(System.nanoTime()-peer.sentNanos)/1e6);peer.ping=null}}
                    else->throw MeterProtocolException("지원하지 않는 메시지")
                }
            }
            reason="연결 종료"
        }catch(error:Exception){reason=when(error){is java.net.SocketTimeoutException->"응답 시간 초과";is MeterProtocolException->error.message.orEmpty();else->"통신 연결 종료"};runCatching{peer.send(JSONObject().put("type","error").put("message",reason))}}
        finally{peer.id?.let{registry.disconnect(it,peer.owner,reason)};sockets.remove(peer.socket);runCatching{peer.socket.close()};slots.release()}
    }
    override fun close(){if(running.compareAndSet(true,false)){runCatching{server.close()};sockets.keys.forEach{runCatching{it.close()}};clock.shutdownNow();workers.shutdownNow();registry.disconnectAll("AP 수신 종료")}}
}
