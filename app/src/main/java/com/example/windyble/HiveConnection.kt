package com.example.windyble

import android.app.Application
import androidx.lifecycle.*
import com.example.windyble.HiveWraper
import com.example.windyble.debug
import java.util.*

/*
    Initialize with lifecycle.addObserver(this) within the main activity
    to add the Pause/Resume events to the main activity.
 */
class HiveConnection(application: Application) : AndroidViewModel(application), LifecycleObserver {
    var hiveWraper: HiveWraper = HiveWraper()
    val context = application
    lateinit var name:String

    fun connect_bt(name:String, address:String, myUUID:UUID){
        this.name = name
//        hiveWraper.setConnectTo(name, null, address, myUUID,0)
        debug("connect bt")
        resume()
    }

    fun connect(name:String, address:String, port:Int){
        this.name = name
//        hiveWraper.setConnectTo(name, address, null, null, port)
        debug("connect")
        resume()
    }

//    fun hangup():Boolean?
//    {
//        return hiveWraper.hangup()
//    }

//    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun resume(){
        debug("RESUME!!")
        hiveWraper.resumeHive(context, viewModelScope)
    }
    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    fun pauseHive() {
        debug("PAUSE")
        hiveWraper.pauseHive()
    }

}