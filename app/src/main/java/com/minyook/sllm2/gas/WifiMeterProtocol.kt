package com.minyook.sllm2.gas

import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

data class MeterHello(val deviceId:String,val name:String,val test:Boolean,val intervalMs:Long)
data class MeterSample(val sequence:Long,val ageMs:Long,val oxygen:Double?,val h2s:Double?,val co:Double?,val lel:Double?,val healthy:Boolean,val alarm:String)
class MeterProtocolException(message:String):Exception(message)

/** Field Guard experimental protocol v1. UTF-8 JSON, one bounded frame per LF. */
object WifiMeterProtocol {
    const val MAX_FRAME_BYTES=8192
    const val DEFAULT_PORT=8765
    const val MAX_RECEIVERS=16 // Software limit, not a claim about a phone's AP capacity.
    fun readFrame(input:InputStream):String? {
        val bytes=ByteArrayOutputStream()
        while(true){
            val value=input.read()
            if(value<0){if(bytes.size()==0)return null;throw MeterProtocolException("완료되지 않은 프레임")}
            if(value==10)break
            if(bytes.size()>=MAX_FRAME_BYTES)throw MeterProtocolException("프레임 크기 초과")
            bytes.write(value)
        }
        return try{Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes.toByteArray())).toString().trimEnd('\r')}
        catch(_:Exception){throw MeterProtocolException("UTF-8 형식 오류")}
    }
    fun json(frame:String):JSONObject=try{JSONObject(frame)}catch(_:Exception){throw MeterProtocolException("JSON 형식 오류")}
    fun hello(json:JSONObject,token:String):MeterHello {
        keys(json,setOf("type","protocol","token","deviceId","name","test","intervalMs"))
        if(json.optString("type")!="hello"||integer(json,"protocol")!=1L)throw MeterProtocolException("지원하지 않는 연결 규격")
        val supplied=json.opt("token") as? String?:throw MeterProtocolException("연결 코드 필요")
        if(supplied.length>128||!MessageDigest.isEqual(supplied.toByteArray(),token.toByteArray()))throw MeterProtocolException("연결 코드 불일치")
        val id=json.opt("deviceId") as? String?:throw MeterProtocolException("장비 ID 필요")
        if(!Regex("[A-Za-z0-9._-]{1,48}").matches(id))throw MeterProtocolException("장비 ID 형식 오류")
        val name=if(!json.has("name"))id else json.opt("name") as? String?:throw MeterProtocolException("장비 이름 형식 오류")
        if(name.isBlank()||name.length>64||name.any{it.isISOControl()})throw MeterProtocolException("장비 이름 형식 오류")
        val test=json.opt("test") as? Boolean?:throw MeterProtocolException("테스트 송신기 여부 필요")
        val interval=integer(json,"intervalMs")
        if(interval !in 100..60_000)throw MeterProtocolException("전송 주기 범위 오류")
        return MeterHello(id,name,test,interval)
    }
    fun sample(json:JSONObject):MeterSample {
        keys(json,setOf("type","seq","sampleAgeMs","o2Percent","h2sPpm","coPpm","lelPercent","status","alarm"))
        if(json.optString("type")!="reading")throw MeterProtocolException("측정 프레임 필요")
        val sequence=integer(json,"seq");val age=integer(json,"sampleAgeMs")
        if(sequence !in 0..9_007_199_254_740_991L||age !in 0..300_000)throw MeterProtocolException("순번/측정 경과시간 오류")
        val status=json.optString("status");val alarm=json.optString("alarm")
        if(status !in setOf("ok","fault")||alarm !in setOf("none","warning","danger","unknown"))throw MeterProtocolException("장비 상태/경보 형식 오류")
        fun channel(key:String,max:Double):Double? {
            if(!json.has(key)||json.isNull(key))return null
            val value=(json.opt(key) as? Number)?.toDouble()?:throw MeterProtocolException("센서값 숫자 형식 오류")
            if(!value.isFinite()||value<0||value>max)throw MeterProtocolException("센서값 범위 오류")
            return value
        }
        val result=MeterSample(sequence,age,channel("o2Percent",100.0),channel("h2sPpm",1000.0),channel("coPpm",10000.0),channel("lelPercent",100.0),status=="ok",alarm)
        if(result.healthy&&listOf(result.oxygen,result.h2s,result.co,result.lel).all{it==null})throw MeterProtocolException("측정 채널 없음")
        return result
    }
    fun integer(json:JSONObject,key:String):Long {
        val raw=json.opt(key)
        if(raw !is Int&&raw !is Long)throw MeterProtocolException("$key 정수 필요")
        return (raw as Number).toLong()
    }
    private fun keys(json:JSONObject,allowed:Set<String>){if(json.keys().asSequence().any{it !in allowed})throw MeterProtocolException("알 수 없는 필드")}
}

enum class MeterLink { WAITING, RECEIVING, STALE, FAULT, DISCONNECTED }
data class WifiMeterView(val deviceId:String,val name:String,val test:Boolean,val intervalMs:Long,val link:MeterLink,val sample:MeterSample?=null,val receivedAtMillis:Long=0,val rejected:Long=0,val missed:Long=0,val rttMs:Double?=null,val reason:String="") {
    val hasRecentSample get()=sample!=null&&link in setOf(MeterLink.RECEIVING,MeterLink.FAULT)
    val current get()=link==MeterLink.RECEIVING&&sample?.healthy==true
    fun reading()=GasReading(oxygenPercent=sample?.oxygen,h2sPpm=sample?.h2s,carbonMonoxidePpm=sample?.co,lelPercent=sample?.lel,receivedAtMillis=receivedAtMillis-(sample?.ageMs?:0L),deviceName="$name · $deviceId",source=if(test)GasReadingSource.SIMULATION else GasReadingSource.WIFI_AP,sessionActive=current,freshnessMs=maxOf(3000,intervalMs*3))
}

/** Per-connection ownership prevents one socket from overwriting another meter. */
class WifiMeterRegistry {
    private data class Entry(val owner:String,var view:WifiMeterView,var receivedNanos:Long=0)
    private val entries=linkedMapOf<String,Entry>()
    @Synchronized fun connect(hello:MeterHello,owner:String):Boolean {
        if(entries.values.any{it.owner==owner&&it.view.link!=MeterLink.DISCONNECTED})return false
        if(entries[hello.deviceId]?.view?.link?.let{it!=MeterLink.DISCONNECTED}==true)return false
        if(entries.size>=64&&!entries.containsKey(hello.deviceId)){
            val old=entries.entries.firstOrNull{it.value.view.link==MeterLink.DISCONNECTED}?:return false
            entries.remove(old.key)
        }
        entries[hello.deviceId]=Entry(owner,WifiMeterView(hello.deviceId,hello.name,hello.test,hello.intervalMs,MeterLink.WAITING))
        return true
    }
    @Synchronized fun reading(id:String,owner:String,sample:MeterSample,wall:Long,nanos:Long):Boolean {
        val entry=entries[id]?.takeIf{it.owner==owner&&it.view.link!=MeterLink.DISCONNECTED}?:return false
        val previous=entry.view.sample?.sequence
        if(previous!=null&&sample.sequence<=previous){entry.view=entry.view.copy(rejected=entry.view.rejected+1);return false}
        entry.view=entry.view.copy(sample=sample,receivedAtMillis=wall,link=if(sample.healthy)MeterLink.RECEIVING else MeterLink.FAULT,missed=entry.view.missed+if(previous==null)0 else sample.sequence-previous-1,reason="")
        entry.receivedNanos=nanos
        return true
    }
    @Synchronized fun rtt(id:String,owner:String,millis:Double){entries[id]?.takeIf{it.owner==owner}?.let{it.view=it.view.copy(rttMs=millis)}}
    @Synchronized fun disconnect(id:String,owner:String,reason:String){entries[id]?.takeIf{it.owner==owner}?.let{it.view=it.view.copy(link=MeterLink.DISCONNECTED,reason=reason)}}
    @Synchronized fun disconnectAll(reason:String){entries.values.forEach{it.view=it.view.copy(link=MeterLink.DISCONNECTED,reason=reason)}}
    @Synchronized fun snapshot(nowNanos:Long=System.nanoTime()):List<WifiMeterView> = entries.values.map{entry->
        val view=entry.view
        if(view.hasRecentSample&&view.sample!=null&&(view.sample.ageMs+(nowNanos-entry.receivedNanos).coerceAtLeast(0)/1_000_000>=maxOf(3000,view.intervalMs*3)))view.copy(link=MeterLink.STALE) else view
    }.sortedBy{it.deviceId}
}
