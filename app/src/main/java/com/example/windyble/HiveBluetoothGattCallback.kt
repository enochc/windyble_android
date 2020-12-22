package com.example.windyble

import android.bluetooth.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*


data class Host(val name: String, val address: String)

class HiveBluetoothGattCallback(val host: Host): BluetoothGattCallback() {
    var wait = false
    var on_subscribed:(()->Unit)? = null

    val messageChanel: Channel<ByteArray> = Channel()

    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        when(newState){
            BluetoothProfile.STATE_DISCONNECTED -> {
                debug("DISCONNECTED")
            }
            BluetoothProfile.STATE_CONNECTED -> {
                debug("CONNECTED!!")
                gatt?.discoverServices()
            }
            else -> {
                debug("status changed: $status, state: $newState")
            }
        }
        debug("onConnectionStateChange $status, $newState")

    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        super.onCharacteristicWrite(gatt, characteristic, status)
        wait = false
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        // This is where character subscribe notifications come in
        super.onCharacteristicChanged(gatt, characteristic)

        debug("<< onCharacteristicChanged::: ${characteristic?.value}")

        GlobalScope.launch {
            characteristic?.value?.let { messageChanel.send(it) }
        }

    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        super.onCharacteristicRead(gatt, characteristic, status)
        val v = characteristic?.value
        val hex1 = String.format("%02X", v?.get(0))
        val hex2 =  String.format("%02X", v?.get(1))
        debug("onCharacteristicRead: ${v?.size}, $v, ($hex1 : $hex2)")
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        super.onDescriptorWrite(gatt, descriptor, status)
        wait = false
        debug("onDescriptorWrite: $status")
        on_subscribed?.invoke()
        on_subscribed = null
    }



    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        super.onServicesDiscovered(gatt, status)
        val services = gatt?.services
        if (services != null) {
            for (service in services) {
                val has = service.uuid.toString().startsWith("00001234")
                debug("service uuid: ${service.uuid.toString()}, mine? $has")
                if (has) {
                    for (char in service.characteristics){
                        val mine = char.uuid.toString().startsWith("00001235")
                        debug("char: ${char.uuid}, mine: $mine")
                        if (mine) {
                            on_subscribed = {
                                // This is the connect byte sequence
                                val s = 0x9876.toShort()
                                val buffer = ByteBuffer.allocate(2)
                                buffer.putShort(s)
                                val ssss = "${host.address},${host.name}".encodeToByteArray()
                                char.value = buffer.array()+ssss

                                if (gatt.writeCharacteristic(char)){
                                    debug("did a thing")
                                    if(gatt.setCharacteristicNotification(char, true)){
                                        for (desc in char.descriptors){

                                            if (desc.uuid.toString().startsWith("00001236")) {
                                                debug("Desk: ${desc.uuid}, ${desc.value}")

                                            }
                                        }
                                    }

                                    debug("did another thing")
                                } else {
                                    debug("character write failed")
                                }
                            }

                            val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
                            for (desc in char.descriptors) {
                                if(cccd.equals(desc.uuid)){
                                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    if (gatt.writeDescriptor(desc)) {
                                        wait = true
                                        debug("subscribed for notifications")
                                    } else {
                                        debug("subscribe failed")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        //debug("servies: $services")

    }
    private fun shortToByteArray(value: Short): ByteArray {
        val buffer = ByteBuffer.allocate(2)
        buffer.order(ByteOrder.BIG_ENDIAN) // BIG_ENDIAN is default byte order, so it is not necessary.
        buffer.putShort(value)

        return buffer.array()
    }
    @OptIn(kotlin.ExperimentalUnsignedTypes::class)
    private fun byteArrayToShort(vararg byte: Byte): Int {
        return (byte[2].toUByte().toInt().shl(8) +
                byte[3].toUByte().toInt().shl(0))
    }
}