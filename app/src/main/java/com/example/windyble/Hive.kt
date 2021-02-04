package com.example.windyble

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import com.moandjiezana.toml.Toml
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.OutputStream
import java.lang.Error
import java.net.ConnectException
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.Charset
import java.util.*
import kotlin.collections.ArrayList

typealias Peer = Pair<String, String>

fun Peer.name() = this.first
fun Peer.address() = this.second

const val ACK_DURATION:Long = 30_000



enum class PropertyType() {
    STRING,
    BOOL,
    NUM,
    NONE
}

fun propertyToType(p: Any?): PropertyType {
    if (p == null) return PropertyType.NONE
    return when (p) {
        is String -> PropertyType.STRING
        is Boolean -> PropertyType.BOOL
        else -> PropertyType.NUM
    }
}

class PropType(
    val name: String,
    val property: Hive.Property,
    private val type: PropertyType,
    val doRemove: Int? = null
) {
    private fun isBool(): Boolean {
        return this.type == PropertyType.BOOL
    }

    fun isString(): Boolean {
        return this.type == PropertyType.STRING
    }

    fun getBoolValue(): Boolean {
        return if (isBool()) {
            this.property.value as Boolean
        } else {
            false
        }
    }
}


const val TAG = "Hive <<"
fun hveDebug(s: String) = Log.d(TAG, s)


class Hive() {

    private var connection: Socket? = null
    var connected: Boolean = false
        set(c: Boolean) {
            field = c
            connectedChanged?.invoke(field)
        }

    private var hiveGatt:HiveBluetoothGattCallback? = null
    private var writer: OutputStream? = null

    private val propertyChannel: Channel<PropType> = Channel()
    private val messageChanel: Channel<String> = Channel()

    var errorString:String? = null

    fun disconnect() {

        connected = false
        connection?.close()
    }

    var connectedChanged: ((Boolean) -> Unit)? = null
    var peersChanged: (() -> Unit)? = null
    var lastAckTime:Long = System.currentTimeMillis()

//    fun onConnectedChanged(f: (Boolean) -> Unit) {
//        debug("<<<<< connected 2")
//        connectedChanged = f
//    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    suspend fun peerMessages(): Flow<String> {
        return flow {
            messageChanel.consumeEach {
                emit(it)
            }
        }
    }

    @kotlinx.coroutines.ExperimentalCoroutinesApi
    fun connect_bt(context: Context, address:String, myUUID:UUID): Flow<PropType> {
        val adapter = BluetoothAdapter.getDefaultAdapter();
        //"B8:27:EB:1F:38:F0"
        val device = adapter.getRemoteDevice(address)
        val host = Host(adapter!!.name, myUUID.toString())
        val gatt = HiveBluetoothGattCallback(host)
        gatt.onConnectedChanged {
            debug("<<<<< connected 1 $it")
            connected = it
        }
        this.hiveGatt = gatt

        //todo mess with autoconnect
        device.connectGatt(context, false, gatt, BluetoothDevice.TRANSPORT_LE)

        GlobalScope.launch {
            for (msg in gatt.messageChanel){
//                val str = String(msg)//msg.toString()
                debug("<<< got message via message channel from gatt: $msg")
                runBlocking {
                    process_msg(msg)
                }

            }
        }

        return properties()

    }

    // TODO look at c
    suspend fun connect(address: String, port: Int, name:String="Android client"): Flow<PropType> {
        if (!connected && port > 0) {
            try {
                hveDebug("connecting to $address, $port");
                connection = Socket(address, port)
                writer = connection?.getOutputStream()

                // send header with peer name then request peer updates
                // write raw hive hanshake reader
                val hn = 0x66.toByte()
                var byteArray = "HVEP\n".toByteArray()
                byteArray += H_NAME.toByte()
                byteArray += "${name}\n".toByteArray()
                writeRaw(byteArray)

                connected = true
            } catch (e: ConnectException) {
                hveDebug("Error: $e")
                errorString = e.message
                connected = false
            }

            hveDebug("Connected to server at $address on port $port")
        }

        // starts the messages consumer that needs to run in a coroutine scope to collect
        // messages over the socket
        GlobalScope.launch {
            receive()
        }


        return properties()
    }

    // this reads from the channel
    @ExperimentalCoroutinesApi
    private fun properties(): Flow<PropType> {
        return flow {
            for (p in _properties) {
                emit(p)
            }

            propertyChannel.consumeEach {
                if (it.doRemove == null) {
                    setOrAddProperty(it)
                }
                emit(it)
            }
        }
    }

    private suspend fun process_msg(message:ByteArray){
        val msgType = message[0]
        val msg = message.sliceArray(1 until message.size);

        if (msgType == HEADER) {
            var pointer = 1
            while (pointer < message.size){
                var subhead = message[pointer]
                var theRest = msg.sliceArray(1 until msg.size)
                // TODO this can be expanded, currently only looks for the name header
                when (subhead) {
                    H_NAME -> {
                        val name = String(theRest)
                        debug("name =  $name")
                        pointer += name.length
                    }
                    else -> {

                        debug("nope.. $theRest")
                        pointer ++
                    }
                }

            }
        } else if (msgType == DELETE) { // delete message

            for ((i, p) in _properties.withIndex()) {
                val propName = String(msg)
                if (p.name == propName) {
                    hveDebug("remove|| $msg")
                    _properties.removeAt(i)
                    val type = propertyToType(p.property.value)
                    val prop = PropType(propName, Property(null), type, doRemove = i)
                    propertyChannel.send(prop)
                    break
                }
            }
        } else if (msgType == PROPERTY || msgType == PROPERTIES) {
            val tomlStr = String(msg)
            val toml = Toml().read(tomlStr)
            val es = toml.entrySet()
            debug("process properties: ${es.size}")
            for ((name, value) in es) {
                debug("process:: $name, $value")
                val type = propertyToType(value)
                val prop = PropType(name, Property(value), type)
                propertyChannel.send(prop)

            }

            debug("processed");
            // Send ack message
            if(System.currentTimeMillis() - lastAckTime > ACK_DURATION) {
                //write(ACK)
                lastAckTime = System.currentTimeMillis()
            }

//            emit(msg)
        } else if (msgType == PONG) {
            debug("PONG RECEIVED")
        } else if (msgType == PEER_RESPONSE) {
            debug("<<<< RECEIVED PEERS $msg")
            _peers.clear()
//            for (p in msg.split(",").iterator()) {
//                val x = p.split("|")
//                _peers.add(Peer(x[0], x[1]))
//            }
            debug("fix peer response")
            throw Error("un implimented")
            peersChanged?.invoke()
        } else if (msgType == PEER_MESSAGE) {
            debug("Received Peer Message: $msg")
            debug("fix peer message")
            throw Error("un implimented")
//            messageChanel.send(msg)
        } else {
            Log.e(javaClass.name, "ERROR: unknown message: $msg")
        }
    }


    private suspend fun receive() {
        val inputStream = connection?.getInputStream()
        while (connected) {
            try {
                val bytes = ByteArray(4)
                inputStream?.read(bytes)
                val size = byteArrayToInt(*bytes)
                val msgBytes = ByteArray(size)
                inputStream?.read(msgBytes)

                //var msg = msgBytes.toString(Charset.defaultCharset())
                if (msgBytes.isEmpty()) {
                    // no data received is usually a sign that the socket has been disconnected
                    throw SocketException()
                }
                debug("data received: $msgBytes")

                process_msg(msgBytes);


            } catch (e: SocketException) {
                debug("Socket Closed ")
                _properties.clear()
                connected = false
            }
        }
    }

    fun hangup():Boolean?{
        return hiveGatt?.writeProperty("...".toByteArray())
    }

    private fun writeRaw(raw: ByteArray) {
        runBlocking {
            withContext(Dispatchers.IO) {
                writer?.write(raw);
                writer?.flush()
                debug("written")
            }
        }
    }

    private fun write(message: ByteArray) {
        if (connected) {
            if (this.hiveGatt != null){
                this.hiveGatt?.writeProperty(message)
            } else {
                runBlocking {
                    withContext(Dispatchers.IO) {
//                        val msgByts = (message).toByteArray(Charset.defaultCharset())
                        val sBytes = intToByteArray(message.size)
                        debug("<<<< writing: $message")
                        writer?.write(sBytes)
                        writer?.write(message)
                        writer?.flush()
                        debug("written")
                    }
                }
            }
        }
    }

    fun writeToPeer(peerName: String, msg: String) {
        write("$PEER_MESSAGE$peerName$PEER_MESSAGE_DIV$msg".toByteArray(Charsets.UTF_8))
    }

//    @OptIn(kotlin.ExperimentalUnsignedTypes::class)
    @ExperimentalUnsignedTypes
    private fun byteArrayToInt(vararg byte: Byte): Int {
        return (byte[0].toUByte().toInt().shl(24) +
                byte[1].toUByte().toInt().shl(16) +
                byte[2].toUByte().toInt().shl(8) +
                byte[3].toUByte().toInt().shl(0))
    }

    private fun intToByteArray(value: Int): ByteArray {
        val buffer = ByteBuffer.allocate(4)
        buffer.order(ByteOrder.BIG_ENDIAN) // BIG_ENDIAN is default byte order, so it is not necessary.
        buffer.putInt(value)

        return buffer.array()
    }

    private val _properties: MutableList<PropType> = mutableListOf()

    fun currentProperties():List<PropType> {
        return _properties
    }

    private val _peers: MutableList<Peer> = mutableListOf()
    val peersList: List<Pair<String, String>>
        get() {
            return _peers
        }

    fun deleteProperty(name: String): Int {
        for ((i, p) in _properties.withIndex()) {
            if (p.name == name) {
                val bytes = prepareMessage(DELETE, p.name);
                write(bytes)
                _properties.removeAt(i)
                return i
            }
        }
        return -1
    }

    private fun prepareMessage(msgType:Byte, msg:String):ByteArray {
        val msgBytes = msg.toByteArray(Charsets.UTF_8)
        val bytes = ByteArray(msgBytes.size+1)
        bytes[0] = msgType
        for ((x, b) in msgBytes.withIndex()) {
            bytes[x+1] = b
        }
        debug("<<<< PREP: ${String(bytes)}")
        return bytes
    }

    private fun setOrAddProperty(pt: PropType) {
        val p = getProperty(pt.name)
        if (p != null) {
            p.property.set(pt.property)
        } else {

            _properties.add(pt)
        }
    }

    fun updateProperty(prop_name: String, value: Any?) {
        val p = getProperty(prop_name)
        p?.property?.set(Property(value))
        // Boolean values get handled here, no special logic required
        var msgVal = value
        try {
            msgVal = msgVal.toString().toFloat()
            // If it's a whole number then send an long
            // can't use an == here, doesn't like that
            if (msgVal.rem(1) <= 0) {
                msgVal = msgVal.toLong()
            }
        } catch (e: NumberFormatException) {
            if (value is String) {
                msgVal = "\"${value}\""
            }
        }
        val msg = "${prop_name}=$msgVal"
        val bytes = prepareMessage(PROPERTY, msg)

        write(bytes)
    }

    private fun getProperty(name: String): PropType? {
        for (p in _properties) {
            if (p.name == name) {
                return p
            }
        }
        return null
    }


    inner class Property(default: Any?) {

        var onChanged: ArrayList<((Any?) -> Unit)> = arrayListOf()

        var value = default
            set(value) {
                if (value != field) {
                    field = value
                    for (v in onChanged.iterator()) {
                        v(value)
                    }
                }
            }

        fun set(other: Property) {
            this.value = other.value
        }

        fun connect(fn: (Any?) -> Unit) {
            onChanged.add(fn)
        }
    }

    companion object {
//        const val DELETE = "|d|"
//        const val HEADER = "|H|"
//        const val PROPERTIES = "|P|"
//        const val PROPERTY = "|p|"
        const val REQUEST_PEERS = "<p|"
//        const val ACK = "<<|";
//        const val PEER_MESSAGE = "|s|"
        const val PEER_MESSAGE_DIV = "|=|"

        const val PEER_MESSAGE: Byte = 0x13;
        const val PONG: Byte = 0x61;
        const val HANGUP: Byte = 0x62;
        const val PING: Byte = 0x63;

        const val DELETE: Byte = 0x12
        const val HEADER: Byte = 0x14
        const val H_NAME: Byte = 0x67
        const val PROPERTIES: Byte = 0x10
        const val PROPERTY: Byte = 0x11
        const val PEER_RESPONSE:Byte = 0x66
        const val PEER_REQUEST:Byte = 0x65
    }
}