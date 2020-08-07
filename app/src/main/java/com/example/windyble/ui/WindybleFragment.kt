package com.example.windyble.ui

import androidx.lifecycle.ViewModelProviders
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.windyble.R
import com.example.windyble.debug
import com.example.windyble.models.HiveConnection
import kotlinx.android.synthetic.main.windyble_fragment.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.MotionEvent
import android.view.View.OnTouchListener


class WindybleFragment : Fragment() {

    companion object {
        fun newInstance() = WindybleFragment()
    }

    private lateinit var viewModel: HiveConnection

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.windyble_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(requireActivity()).get(HiveConnection::class.java)

        initButton(up_button)
        initButton(down_button)

        speed_btn.setOnClickListener {
            val speed = speed.text.toString().toInt()
            viewModel.hive?.updateProperty("speed", speed)
        }
    }

    fun initButton(button:View){
        button.setOnTouchListener { arg0, arg1 ->
            val isup = arg0 == up_button
            if (arg1.action == MotionEvent.ACTION_DOWN) {
                button.isPressed = true
                button.performClick()
                if(isup){
                    viewModel.hive?.updateProperty("moveup", true)
                } else{
                    viewModel.hive?.updateProperty("movedown", true)
                }


            } else if(arg1.action == MotionEvent.ACTION_UP){
                button.isPressed = false
                if(isup){
                    viewModel.hive?.updateProperty("moveup", false)
                } else{
                    viewModel.hive?.updateProperty("movedown", false)
                }
            }

            true
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.findViewById<View>(R.id.fab)?.visibility = View.GONE

        val speedval = viewModel.hive?.properties()?.first {
            it.name == "speed"
        }?.property?.value?.toString()
        speed.setText(speedval)


        viewModel.hive?.propertyReceived?.observe(viewLifecycleOwner, Observer {
            debug("<<<< <<< <<< GOT A PROPERTY: ${it.name}")
        })

    }

    override fun onPause() {
        super.onPause()
        activity?.findViewById<View>(R.id.fab)?.visibility = View.VISIBLE
    }

}
