package com.example.windyble.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.SeekBar
import android.widget.SeekBar.OnSeekBarChangeListener
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.example.windyble.R
import com.example.windyble.debug
import com.example.windyble.models.HiveConnection
import com.example.windyble.models.HiveViewModel
import kotlinx.android.synthetic.main.windyble_fragment.*

class POTListener(val viewModel:HiveViewModel):AdapterView.OnItemSelectedListener {

    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
       println("<<<<< Item Selected $pos, $id")
        viewModel.updateProperty("pt", pos)
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        println("<<<<< nothing selected")
    }
}

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

        val pot_spinner:Spinner = pot_spinner
        pot_spinner.adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.pot_values,
            R.layout.pot_list_item
        )
        pot_spinner.onItemSelectedListener = POTListener(viewModel.hive)

//        speed_btn.setOnClickListener {
//            val speed = speed.text.toString().toInt()
//            viewModel.hive.updateProperty("speed", speed)
//        }
        speed_seek.progress = 25
        speed_seek.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar,
                progress: Int,
                fromUser: Boolean
            ) {}

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                println("POSITION: ${seekBar.progress}")
                viewModel.hive.updateProperty("speed", seekBar.progress)
            }
        })
    }

    fun initButton(button:View){
        button.setOnTouchListener { arg0, arg1 ->
            val isup = arg0 == up_button
            if (arg1.action == MotionEvent.ACTION_DOWN) {
                button.isPressed = true
                button.performClick()
                if(isup){
                    viewModel.hive.updateProperty("moveup", true)
                } else{
                    viewModel.hive.updateProperty("movedown", true)
                }


            } else if(arg1.action == MotionEvent.ACTION_UP){
                button.isPressed = false
                if(isup){
                    viewModel.hive.updateProperty("moveup", false)
                } else{
                    viewModel.hive.updateProperty("movedown", false)
                }
            }

            true
        }
    }

    override fun onResume() {
        super.onResume()
        activity?.findViewById<View>(R.id.fab)?.visibility = View.GONE

//        val speed_prop = viewModel.hive.properties()

//        val speedval = viewModel.hive.properties().first {
//            it.name == "speed"
//        }.property.value?.toString()
//        speed.setText(speedval)


        viewModel.hive.propertyReceived.observe(viewLifecycleOwner, Observer {
            debug("<<<< <<< <<< GOT A PROPERTY: ${it.name}")
        })

    }

    override fun onPause() {
        super.onPause()
        activity?.findViewById<View>(R.id.fab)?.visibility = View.VISIBLE
    }

}
