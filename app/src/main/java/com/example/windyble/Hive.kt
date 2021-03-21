package com.example.windyble

import android.R.attr.x
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.OutputStream
import java.net.ConnectException
import java.net.Socket
import java.net.SocketException
import java.nio.ByteBuffer
import java.nio.ByteOrder
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
    val property: Property,
    val type: PropertyType,
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


@ExperimentalUnsignedTypes
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

                // send header with peer name
                var byteArray = "HVEP\n".toByteArray()
                byteArray += HEADER_NAME
                byteArray += "${name}\n".toByteArray()
                writeRaw(byteArray)

                connected = true
            } catch (e: ConnectException) {
                hveDebug("<<<<<<<<<<<<<<<<<<<<<<<<< Error: $e")
                errorString = e.message
//                connected = false
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
        val msg = message.sliceArray(1 until message.size)
        when (msgType) {
            HEADER -> {
                debug("||||||||| im a header!!")
            }
        }

        if (msgType == HEADER) {
            var pointer = 1
            while (pointer < message.size){
                var subhead = message[pointer]
                var theRest = msg.sliceArray(1 until msg.size)
                // TODO this can be expanded, currently only looks for the name header
                when (subhead) {
                    HEADER_NAME -> {
                        val name = String(theRest)
                        debug("name =  $name")
                        pointer += name.length+1
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
            val props = bytesToProperties(msg)

            debug("processed: $props");
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
            Log.e(javaClass.name, "ERROR: unknown message: $msgType: $msg")
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

    fun hangup(){
        val ba = ByteArray(1)
        ba[0] = HANGUP
//        return hiveGatt?.writeProperty(ba)
        this.write(ba)
        disconnect()
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
        debug("... wrighting!! $connected")
        if (connected) {
            if (this.hiveGatt != null){

                val wrote = this.hiveGatt?.writeProperty(message)
                debug("Wrote to bluetooth GAT: $message, $wrote")
            } else {
                debug("...... send send send")
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
        debug("<<<< PREP ,, ${String(bytes)}")
        return bytes
    }

    private fun setOrAddProperty(pt: PropType) {
        debug(" |||||||||||||||||||||||||||||||||||||||||||| $pt")
        val p = getProperty(pt.name)
        if (p != null) {
            debug(".. set")
            p.property.set(pt.property)
        } else {
            debug("... add ${pt.name} ${pt.property}")
            _properties.add(pt)
        }
    }

    fun updateProperty(prop_name: String, value: Any?) {
        val p = getProperty(prop_name)
        p?.property?.set(Property(value))
        val bytes = propertyToBytes(prop_name, value)

        debug("Prepare to write message: $bytes")
        write(bytes)
    }

    private fun getProperty(name: String): PropType? {
        for (p in _properties) {
            debug("................................ im here ${p.name}")
            if (p.name == name) {
                return p
            }
        }
        return null
    }

    companion object {
        const val PEER_MESSAGE_DIV = "\n"

        const val PEER_MESSAGE: Byte = 0x13;
        const val PONG: Byte = 0x61;
        const val HANGUP: Byte = 0x62;
        const val PING: Byte = 0x63;

        const val DELETE: Byte = 0x12
        const val HEADER: Byte = 0x72
        const val HEADER_NAME: Byte = 0x78
        const val PROPERTIES: Byte = 0x10
        const val PROPERTY: Byte = 0x11
        const val PEER_RESPONSE:Byte = 0x66
        const val PEER_REQUEST:Byte = 0x65

        const val IS_STR: Byte = 0x20
        const val IS_BOOL: Byte = 0x19
        const val IS_SHORT: Byte = 0x21 // 8 bits
        const val IS_SMALL: Byte = 0x14 // 16 bits
        const val IS_NONE: Byte = 0x18;

        // todo there is more to be done here!!
        fun propertyToBytes(prop_name: String, value: Any?):ByteArray {
            val bytes = ArrayList<Byte>()
            bytes.add(PROPERTY)
            bytes.add(prop_name.length.toUByte().toByte())
            bytes.addAll(prop_name.encodeToByteArray().asList())
            when (value) {
                is String -> {
                    val strVal = value.toString()
                    bytes.add(IS_STR)
                    bytes.addAll(strVal.encodeToByteArray().asList())
                }
                is Byte -> {
                    bytes.add(IS_SHORT)
                    bytes.add(value.toByte())
                }
                is Int -> {
                    if (value > Byte.MIN_VALUE && value < Byte.MAX_VALUE)  {
                        bytes.add(IS_SHORT)
                        bytes.add(value.toByte())
                    } else if(value> Short.MIN_VALUE && value <Short.MAX_VALUE){
                        bytes.add(IS_SMALL)
                        val arr = byteArrayOf(
                            (value shr 8 and 0xFF).toByte(),
                            (value and 0xFF).toByte()
                        )
                        bytes.addAll(arr.toList())
                    }
                }
                is Short -> {
                    bytes.add(IS_SMALL)
                    val num = value as Int
                    val arr = byteArrayOf(
                        (num shr 8 and 0xFF).toByte(),
                        (num and 0xFF).toByte()
                    )
                    bytes.addAll(arr.toList())
                }
                else -> {
                    bytes.add(IS_NONE)
                }
            }
            return bytes.toByteArray()
        }

        fun bytesToProperties(bytes:ByteArray):ArrayList<PropType> {
            val props = ArrayList<PropType>()
            var currentPos = 0;

            while (currentPos < bytes.size) {
                val s = bytes[currentPos].toUByte().toInt()
                val name_end = currentPos + s
                debug(".. <<<<<<<<: size: ${bytes.size}, pos: $currentPos, until: $name_end, ${bytes.slice(currentPos..name_end)}")
                val name = String(bytes.sliceArray(currentPos..name_end))
                debug("p_name: $name")
                currentPos = name_end +1
                val pType = bytes[currentPos]

                currentPos += 1

                when(pType) {
                    IS_STR -> {
                        val strLength = bytes[currentPos].toUInt().toInt()
                        val strVal = String(bytes.sliceArray(s+3..(s+3+strLength)))
                        debug(".... string $strVal")
                        props.add(PropType(name, Property(strVal), PropertyType.STRING))
                        currentPos += strLength
                    }
                    IS_BOOL -> {
                        val b = bytes[currentPos].toInt() > 0
                        debug("... bool: $b")
                        props.add(PropType(name, Property(b), PropertyType.BOOL))
                        currentPos += 1
                    }
                    IS_SHORT -> { // 8 bit
                        val b = bytes[currentPos].toInt()
                        debug("... short: $b")
                        props.add(PropType(name, Property(b), PropertyType.NUM))
                        currentPos += 1
                    }
                    IS_SMALL -> {
                        // 16 bits, kotlin doesn't have a Small, a Short is 16 bits
                        // 8 bits is just called a Byte
                        val bb = ByteBuffer.allocate(2)
                        bb.order(ByteOrder.BIG_ENDIAN)
                        bb.put(bytes[currentPos])
                        bb.put(bytes[currentPos+1])
                        val shortVal = bb.getShort(0)
                        debug("short= $shortVal, ${bytes.slice(currentPos..currentPos+1)}")
                        props.add(PropType(name, Property(shortVal), PropertyType.NUM))
                        currentPos += 2
                    }
                    else -> {
                        debug("<<<<<< something else:: $pType")
                        props.add(PropType(name, Property(null), PropertyType.NONE))
                    }
                }
            }
            return props
        }
    }
}

class Property(default: Any?) {

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