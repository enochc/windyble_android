package com.example.windyble.ui

import android.os.Bundle
import android.view.*
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
import com.example.windyble.HiveConnection
import com.example.windyble.HiveWraper
import com.example.windyble.databinding.FragmentWindyListBinding
import com.example.windyble.databinding.WindybleFragmentBinding

//import kotlinx.android.synthetic.main.windyble_fragment.*


class POTListener(val wraper: HiveWraper):AdapterView.OnItemSelectedListener {
    var is_set = false

    override fun onItemSelected(parent: AdapterView<*>, view: View?, pos: Int, id: Long) {
        // this gets called once when the spinner is first initialized
        if(is_set) {
            debug("<<<<< Item Selected $pos, $id")
            wraper.updateProperty("pt", pos)
        } else {
            is_set = true
        }
    }

    override fun onNothingSelected(parent: AdapterView<*>) {
        debug("<<<<< nothing selected")
    }
}

class WindybleFragment(val hive:HiveWraper) : Fragment() {

    private var _binding: WindybleFragmentBinding? = null
    //FragmentWindyListBinding? = null
    private val binding get() = _binding!!

    var reversed = false
//    companion object {
//        fun newInstance() = WindybleFragment()
//    }

    private lateinit var hiveConnection: HiveConnection

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true);
    }
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = WindybleFragmentBinding.inflate(inflater, container, false)
//        return inflater.inflate(R.layout.windyble_fragment, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
//        hiveConnection = ViewModelProvider(requireActivity()).get(HiveConnection::class.java)

        initButton(binding.upButton)
        initButton(binding.downButton)

        val pot_spinner:Spinner = binding.potSpinner//pot_spinner
        pot_spinner.adapter = ArrayAdapter.createFromResource(
            requireContext(),
            R.array.pot_values,
            R.layout.pot_list_item
        )
        pot_spinner.onItemSelectedListener = POTListener(hive)

//        speed_btn.setOnClickListener {
//            val speed = speed.text.toString().toInt()
//            viewModel.hive.updateProperty("speed", speed)
//        }
        binding.speedSeek.progress = 25
        binding.speedSeek.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onProgressChanged(
                seekBar: SeekBar,
                progress: Int,
                fromUser: Boolean
            ) {
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                debug("POSITION: ${seekBar.progress}")
                hive.updateProperty("speed", seekBar.progress)
            }
        })
    }

    /*

    STOPPED = 0,
    GO= 1,
    READY_UP = 2,
    READY_DOWN = 3,

     */
    fun initButton(button: View){
        button.setOnTouchListener { arg0, arg1 ->
            val isup = arg0 == binding.upButton//up_button
            if (arg1.action == MotionEvent.ACTION_DOWN) {
                button.isPressed = true
                button.performClick()
                if((isup && !reversed) || (reversed && !isup)){
//                if(isup){
                    hive.updateProperty("turn", 2)
                } else{
                    hive.updateProperty("turn", 3)
                }


            } else if(arg1.action == MotionEvent.ACTION_UP){
                button.isPressed = false
                hive.updateProperty("turn", 0)
            }

            true
        }
    }

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)
        val rev: MenuItem = menu.findItem(R.id.menu_reverse)
        rev.isVisible = true
        rev.isChecked = reversed
        rev.setOnMenuItemClickListener {
            it.setChecked(!it.isChecked)
            reversed = !reversed
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


        hive.propertyReceived.observe(viewLifecycleOwner, Observer {
            debug("<<<< <<< <<< GOT A PROPERTY: ${it.name}")
        })

    }

    override fun onPause() {
        super.onPause()
        activity?.findViewById<View>(R.id.fab)?.visibility = View.VISIBLE
    }

}
