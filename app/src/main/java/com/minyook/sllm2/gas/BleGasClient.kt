package com.minyook.sllm2.gas

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Build
import java.util.UUID

/**
 * Generic BLE connection for gas meters that expose UTF-8 notifications.
 * It does not invent readings: only packets accepted by [GasPacketParser]
 * update the shared gas state. A vendor-specific parser can replace this
 * class when the meter's protocol/UUID specification is available.
 */
class BleGasClient(
    context: Context,
    private val listener: Listener,
) {
    interface Listener {
        fun onScanResult(device: BluetoothDevice, rssi: Int)
        fun onConnectionStatus(message: String)
        fun onReading(reading: GasReading)
    }

    private val appContext = context.applicationContext
    private val adapter: BluetoothAdapter?
        get() = (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
    private var scanner: BluetoothLeScanner? = null
    private var gatt: BluetoothGatt? = null
    private var connectedDeviceName: String? = null

    @SuppressLint("MissingPermission")
    fun scan() {
        val bluetooth = adapter
        when {
            bluetooth == null -> listener.onConnectionStatus("이 기기는 Bluetooth LE를 지원하지 않습니다.")
            !bluetooth.isEnabled -> listener.onConnectionStatus("Bluetooth를 켠 뒤 다시 검색해 주세요.")
            else -> {
                stopScan()
                scanner = bluetooth.bluetoothLeScanner
                scanner?.startScan(scanCallback)
                listener.onConnectionStatus("주변 가스 측정기를 검색하는 중입니다…")
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        scanner?.stopScan(scanCallback)
        scanner = null
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        stopScan()
        close()
        connectedDeviceName = device.name ?: device.address
        listener.onConnectionStatus("${connectedDeviceName}에 연결하는 중입니다…")
        gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } else {
            device.connectGatt(appContext, false, gattCallback)
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val bluetooth = adapter
        if (bluetooth == null || !bluetooth.isEnabled) {
            listener.onConnectionStatus("Bluetooth를 켠 뒤 다시 연결해 주세요.")
            return
        }
        runCatching { bluetooth.getRemoteDevice(address) }
            .onSuccess(::connect)
            .onFailure { listener.onConnectionStatus("저장된 가스 측정기를 찾지 못했습니다.") }
    }

    @SuppressLint("MissingPermission")
    fun close() {
        gatt?.disconnect()
        gatt?.close()
        gatt = null
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            listener.onScanResult(result.device, result.rssi)
        }

        override fun onScanFailed(errorCode: Int) {
            listener.onConnectionStatus("Bluetooth 검색을 시작하지 못했습니다. 오류 코드: $errorCode")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when {
                status != BluetoothGatt.GATT_SUCCESS -> {
                    listener.onConnectionStatus("가스 측정기 연결 오류: $status")
                    gatt.close()
                }
                newState == BluetoothProfile.STATE_CONNECTED -> {
                    listener.onConnectionStatus("연결됨 · 측정 채널을 확인하는 중입니다…")
                    gatt.discoverServices()
                }
                newState == BluetoothProfile.STATE_DISCONNECTED -> {
                    listener.onConnectionStatus("가스 측정기 연결이 끊겼습니다.")
                    gatt.close()
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                listener.onConnectionStatus("가스 측정기의 서비스를 읽지 못했습니다.")
                return
            }
            val notificationChannels = gatt.services
                .flatMap { it.characteristics }
                .filter { characteristic ->
                    characteristic.properties and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                }
            notificationChannels.forEach { characteristic -> enableNotifications(gatt, characteristic) }
            listener.onConnectionStatus(
                if (notificationChannels.isEmpty()) "알림 측정 채널이 없습니다. 기기 프로토콜을 확인해 주세요."
                else "연결됨 · 측정값 수신 대기 중"
            )
        }

        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
            consumePacket(characteristic.value)
        }

        override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
            consumePacket(value)
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableNotifications(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
        gatt.setCharacteristicNotification(characteristic, true)
        val descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG) ?: return
        val value = if (characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE != 0) {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeDescriptor(descriptor, value)
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = value
            @Suppress("DEPRECATION")
            gatt.writeDescriptor(descriptor)
        }
    }

    private fun consumePacket(bytes: ByteArray) {
        val reading = GasPacketParser.parse(bytes, connectedDeviceName) ?: return
        GasReadingStore.update(appContext, reading)
        listener.onReading(reading)
    }

    private companion object {
        val CLIENT_CHARACTERISTIC_CONFIG: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
