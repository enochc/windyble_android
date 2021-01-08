package com.example.windyble

import android.bluetooth.*
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.*


data class Host(val name: String, val address: String)

class HiveBluetoothGattCallback(val host: Host): BluetoothGattCallback() {

    companion object {
        var notifyDescriptor:BluetoothGattDescriptor? = null
        var myGatt:BluetoothGatt? = null

        fun unsubscribe(){
            debug("try unsubscribe")
            notifyDescriptor?.let {
                it.value = BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE
                myGatt?.writeDescriptor(it)?.let {worked ->
                    if(worked){debug("UNSUBSCRIBED")}else{
                        debug("Failed to unsubscribe")
                    }
                }
            }
        }
    }
    var writeDescriptor:BluetoothGattDescriptor? = null
    var propertiesDescriptor:BluetoothGattDescriptor? = null

    fun writeProperty(msg:ByteArray):Boolean?{
        writeDescriptor?.value = msg
        return myGatt?.writeDescriptor(writeDescriptor)
    }

    var wait = false
    var on_subscribed:(()->Unit)? = null

    val messageChanel: Channel<ByteArray> = Channel()

    var connectedChanged: ((Boolean) -> Unit)? = null
    fun onConnectedChanged(f: (Boolean) -> Unit) {
        debug("<<<<< connected 0")
        connectedChanged = f
    }



    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
        super.onConnectionStateChange(gatt, status, newState)
        when(newState){
            BluetoothProfile.STATE_DISCONNECTED -> {
                connectedChanged?.invoke(false)
                debug("DISCONNECTED")
            }
            BluetoothProfile.STATE_CONNECTED -> {
                debug("CONNECTED!!")
                connectedChanged?.invoke(true)
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

        // This does not run in the GlobalScope, the characteristic value
        // can get overwritten before the value is sent if changes occure very fast.
        runBlocking {
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
                            gatt.setCharacteristicNotification(char, true)
                            on_subscribed = {
                                // This is the connect byte sequence
                                val s = 0x9876.toShort() // Send connect bites
                                val buffer = ByteBuffer.allocate(2)
                                buffer.putShort(s)
                                val ssss = "${host.name},${host.address}".encodeToByteArray()
                                char.value = buffer.array()+ssss

                                if (gatt.writeCharacteristic(char)){
                                    debug("did a thing")

                                    for (desc in char.descriptors){
                                        if (desc.uuid.toString().startsWith("00001236")) {
                                            writeDescriptor = desc
                                            debug("Desk: ${desc.uuid}, ${desc.value}")

                                        }
                                    }

                                } else {
                                    debug("character write failed")
                                }
                            }

                            val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

                            for (desc in char.descriptors) {
                                debug("<<<<<<<<< ${desc.uuid.toString()}")
                                if(cccd.equals(desc.uuid)){
                                    notifyDescriptor = desc
                                    myGatt = gatt
                                    desc.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                    if (gatt.writeDescriptor(desc)) {
                                        wait = true
                                        debug("subscribed for notifications")
                                    } else {
                                        debug("subscribe failed")
                                    }
                                }
                                else if(desc.uuid.toString().startsWith("00001237")){
                                    // is properties descriptor
                                    propertiesDescriptor = desc
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