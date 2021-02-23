package com.example.windyble

import android.content.Context
import androidx.lifecycle.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.util.*


// default address is to host that emulator is running on
class HiveWraper() {
    var hiveName: String? = null
    var hiveAddress: String? = null
    var hivePort: Int = 0
    var btAddress: String? = null
    var mybtUUID:UUID? = null

    //                    private val hiveAddress:String="10.0.2.2",
    //                    hiveAddress:String="192.168.5.41",
    constructor(
        hiveName: String = "Android Hive",
        hiveAddress: String? = null,
        btAddress: String? = null,
        mybtUUID: UUID? = null,
        hivePort: Int = 3000
    ):this() {
        this.hiveName = hiveName
        this.hiveAddress = hiveAddress
        this.hivePort = hivePort
        this.btAddress = btAddress
        this.mybtUUID = mybtUUID
    }

    private val hive = Hive()

    val errorString = hive.errorString


    fun properties(): List<PropType> {
        return hive.currentProperties()
    }

    val connected: MutableLiveData<Boolean> = MutableLiveData(false)
    var serverName: MutableLiveData<String> = MutableLiveData("")

    var propertyReceived: MutableLiveData<PropType> = MutableLiveData()

    var peers: MutableLiveData<List<Peer>> = MutableLiveData()
    var peerMessage: MutableLiveData<String> = MutableLiveData()

    private var job: Job? = null

    fun updateProperty(prop_name: String, value: Any?) {
        hive.updateProperty(prop_name, value)
    }
    fun hangup(){
        return hive.hangup()
    }

    fun deleteProperty(name: String): Int {
        return hive.deleteProperty(name)
    }

    fun writeToPeer(peerName: String, msg: String) {
        hive.writeToPeer(peerName, msg)
    }

    //    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun resumeHive(context: Context, scope: CoroutineScope) {
        job = scope.launch {
            withContext(Dispatchers.IO) {

                scope.launch(Dispatchers.Main) {
                    hive.peerMessages().collect { peerMessage.value = it }
                }
                hive.peersChanged = {
                    scope.launch(Dispatchers.Main) {
                        val p = hive.peersList.filter {
                            it.name() != hiveName
                        }
                        peers.value = p
                        // TODO, this isn't working
//                        serverName.value = p.first { it.address().startsWith(hiveAddress) }.name()
                    }
                }

                hive.connectedChanged = {
                    if (!it.equals(connected.value)) {
                        GlobalScope.launch {
                            withContext(Dispatchers.Main) {
                                debug("<<<<<<<<<<<<<<<<  ${connected.value} || $it")
                                connected.value = it
                            }
                        }
                    }
                }
                if(!btAddress.isNullOrEmpty()){
                    // connect to bt address
                    hive.connect_bt(context, btAddress!!, mybtUUID!!).collect {
                        //debug("<<<< received $it")
                        withContext(Dispatchers.Main) {
                            propertyReceived.value = it
                        }
                    }
                }
                if(!hiveAddress.isNullOrEmpty()){
                    hive.connect(hiveAddress!!, hivePort).collect {

                        debug(" <<< ............ property: $hiveAddress : $hivePort, $it")
                        withContext(Dispatchers.Main) {
                            propertyReceived.value = it


                        }
                    }
                }

            }
        }
    }

    //    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun pauseHive() {
        job?.cancel()
        hive.disconnect()
    }
}