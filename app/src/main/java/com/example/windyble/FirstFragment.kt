package com.example.windyble

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.viewModels
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.ListAdapter

import com.example.windyble.models.HiveConnection
import com.example.windyble.models.HiveViewModel
import com.example.windyble.ui.WindybleFragment
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import kotlinx.android.synthetic.main.fragment_first.*
import kotlin.concurrent.fixedRateTimer


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class FirstFragment : Fragment() {

    lateinit var hiveConnection: HiveConnection
//    val hiveConnection = ViewModelProvider(requireActivity()).get(HiveConnection::class.java)
    val hives = mutableListOf<HiveViewModel>()
    var hiveAdapter:HiveAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_first, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hiveAdapter = HiveAdapter(hives)
        hives_list.adapter = hiveAdapter!!

        hiveAdapter?.hiveClicked = {
            val trans = parentFragmentManager.beginTransaction()
            trans.replace(R.id.nav_host_fragment, WindybleFragment())
            trans.addToBackStack("windyble")
            trans.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            trans.commit()
        }

        // TODO this currently only stupports a single hive, to have multiple ie: more then one windyble
        //  this will require modification
        hiveConnection = ViewModelProvider(requireActivity()).get(HiveConnection::class.java)

        // Disconnect hive when activity is closed
        activity?.lifecycle?.addObserver(hiveConnection)
//        lifecycle.addObserver(hiveConnection)

        hiveConnection.connected.observe(viewLifecycleOwner, Observer {
            debug("CONNECTED !! $it")
            if(it) {
                hives.add(hiveConnection.hive!!)
                hiveAdapter?.notifyItemInserted(hiveAdapter!!.itemCount+1)
                debug("Stuff: ${hiveAdapter?.itemCount}")
            } else {
                hives.clear()
                hiveAdapter?.notifyItemRemoved(0)
            }
        })

    }

    override fun onResume() {
        super.onResume()

        debug("Resume: is connected ${hiveConnection?.connected?.value}")
    }


}