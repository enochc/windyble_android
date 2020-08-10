package com.example.windyble.models

import androidx.lifecycle.*
import com.example.windyble.debug

class HiveConnection: ViewModel(), LifecycleObserver {
    var hive:HiveViewModel = HiveViewModel()
    lateinit var name:String

    fun connect(name:String, address:String, port:Int){
//        hive = HiveViewModelFactory(name, address, port).create(HiveViewModel::class.java)
        this.name = name
        hive.setConnectTo(name, address, port)
//        hive = HiveViewModel(name, address, port)
        debug("resuming")
        resume()
//        debug("Changing connected to true")
//        connected.value = true
//        debug("Set to true")

    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun resume(){
        hive?.resumeHive(viewModelScope)
    }
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun pauseHive() {
        hive?.pauseHive()
    }

}