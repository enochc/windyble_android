package com.example.windyble.models

import android.util.Log
import androidx.lifecycle.*
import com.example.windyble.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect


class HiveViewModelFactory(private val name: String,
                           private val address:String,
                           private val port:Int) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T = HiveViewModel() as T
}

// default address is to host that emulator is running on
class HiveViewModel() {
    var hiveName:String=""
    var hiveAddress:String=""
    var hivePort:Int = 0

    fun setConnectTo(hiveName: String = "Android Hive",
//                    private val hiveAddress:String="10.0.2.2",
//                     hiveAddress:String="192.168.5.41",
                     hiveAddress:String="10.0.2.2",
                     hivePort:Int =3000){

        this.hiveName = hiveName
        this.hiveAddress = hiveAddress
        this.hivePort = hivePort
    }
    private val hive = Hive()

    val errorString = hive.errorString


    fun properties():List<PropType> {
        return hive.currentProperties()
    }

    var connected:MutableLiveData<Boolean> = MutableLiveData(false)
    var serverName:MutableLiveData<String> = MutableLiveData("")

    var propertyReceived: MutableLiveData<PropType> = MutableLiveData()

    var peers: MutableLiveData<List<Peer>> = MutableLiveData()
    var peerMessage: MutableLiveData<String> = MutableLiveData()

    private var job: Job? = null

    fun updateProperty(prop_name: String, value: Any?) {
        hive.updateProperty(prop_name, value)
    }

    fun deleteProperty(name: String): Int {
        return hive.deleteProperty(name)
    }

    fun writeToPeer(peerName: String, msg: String) {
        hive.writeToPeer(peerName, msg)
    }

//    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun resumeHive(scope:CoroutineScope) {
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
                    println("CONNECTION CHANGED: $it")
                    launch {
                        withContext(Dispatchers.Main) {
                            println("<<<<<<<<<<<<<<<<<<<<<<<<<<<<<  ${connected.value} || $it")
                            if(!it.equals(connected.value)) {
                                connected.value = it
                            }
                        }
                    }
                }

                hive.connect(hiveAddress, hivePort).collect {
                    println(" <<< ............ Connecting hive $hiveAddress : $hivePort")
                    withContext(Dispatchers.Main) {
                        propertyReceived.value = it


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