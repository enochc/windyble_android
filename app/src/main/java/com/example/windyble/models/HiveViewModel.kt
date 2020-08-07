package com.example.windyble.models

import android.util.Log
import androidx.lifecycle.*
import com.example.windyble.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect


class HiveViewModelFactory(private val name: String,
                           private val address:String,
                           private val port:Int) : ViewModelProvider.NewInstanceFactory() {
    override fun <T : ViewModel?> create(modelClass: Class<T>): T =
        HiveViewModel(name, address, port) as T
}

// default address is to host that emulator is running on
class HiveViewModel(val hiveName: String = "Android Hive",
                    private val hiveAddress:String="10.0.2.2",
                    private val hivePort:Int =3000) {
    private val hive = Hive(hiveName)

    fun properties():List<PropType> {
        return hive.currentProperties()
    }
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

                hive.connect(hiveAddress, hivePort).collect {

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