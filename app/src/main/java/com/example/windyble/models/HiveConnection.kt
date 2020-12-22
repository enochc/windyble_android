package com.example.windyble.models

import android.app.Application
import androidx.lifecycle.*
import com.example.windyble.debug
import kotlin.coroutines.coroutineContext

/*
    Initialize with lifecycle.addObserver(this) within the main activity
    to add the Pause/Resume events to the main activity.
 */
class HiveConnection(application: Application) : AndroidViewModel(application), LifecycleObserver {
    var hive:HiveViewModel = HiveViewModel()
    val context = application
    lateinit var name:String

    fun connect_bt(name:String, address:String){
        this.name = name
        hive.setConnectTo(name, null, address, 0)
        debug("connect bt")
        resume()
    }

    fun connect(name:String, address:String, port:Int){
        this.name = name
        hive.setConnectTo(name, address, null, port)
        debug("connect")
        resume()
//        debug("Changing connected to true")
//        connected.value = true
//        debug("Set to true")

    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun resume(){
        debug("RESUME!!")
        hive.resumeHive(context, viewModelScope)
    }
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun pauseHive() {
        debug("PAUSE")
        hive.pauseHive()
    }

}