package com.example.windyble

import android.content.Context.MODE_PRIVATE
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.windyble.databinding.AddHiveDiagBinding
import com.example.windyble.databinding.FragmentWindyListBinding

import com.example.windyble.ui.WindybleFragment
import com.google.android.material.floatingactionbutton.FloatingActionButton

//import kotlinx.android.synthetic.main.fragment_windy_list.*


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class WindyListFragment : Fragment() {

    val hives = mutableListOf<HiveWraper>()
    var hiveAdapter:HiveAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


    }

    private var _binding: FragmentWindyListBinding? = null
    private val binding get() = _binding!!


    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        // TODO this currently only supports a single hive, to have multiple ie: more then one windyble
        //  this will require modification



        _binding = FragmentWindyListBinding.inflate(inflater, container, false)
//        findViewById<FloatingActionButton>(R.id.fab).setOnClickListener { view ->
//            addHiveDialogue()
//        }
        _binding?.fab?.setOnClickListener{view ->
            addHiveDialogue()
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hiveAdapter = HiveAdapter(hives as ArrayList<HiveWraper>, requireContext(), this)
        binding.hivesList.adapter = hiveAdapter!!

        hiveAdapter?.hiveClicked = {
            val trans = parentFragmentManager.beginTransaction()
            trans.replace(R.id.nav_host_fragment, WindybleFragment(it))
            trans.addToBackStack("windyble")
            trans.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            trans.commit()
        }

        val hiveConnection: HiveConnection = ViewModelProvider(requireActivity()).get(HiveConnection::class.java)


        var is_connected = false

        hiveConnection.hiveWraper.connected.observe(viewLifecycleOwner, Observer {
            debug("CONNECTED !! <<< <<< <<<< <<< <<<<<<<<<<<<<<<<<<<<<<<  $it")
            if(it) {
                is_connected = true
                hives.add(hiveConnection.hiveWraper)
                hiveAdapter?.notifyItemInserted(hiveAdapter!!.itemCount+1)
                debug("Stuff: ${hiveAdapter?.itemCount}")
            } else {
                if(is_connected) {
                    is_connected = false
                    hives.clear()
                    hiveAdapter?.notifyItemRemoved(0)
                    AlertDialog.Builder(requireContext())
                        .setTitle("Connection Failed")
                        .setMessage(hiveConnection.hiveWraper.errorString)
                        .setPositiveButton(android.R.string.ok) { _, _ ->
                        }
                        .show()
                }

            }
        })

    }

    private fun addHiveDialogue() {
        val view = AddHiveDiagBinding.inflate(layoutInflater)
        val addrPrefs = requireContext().getSharedPreferences(MyPREFERENCES, MODE_PRIVATE);

        val savedAddress = addrPrefs?.getString(ADDRESS, "10.0.2.2")

        view.inputAddress.setText(savedAddress)

        AlertDialog.Builder(requireContext())
            .setTitle("Add Windyble")
            .setView(view.root)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->

                val addr = view.inputAddress.text.toString()
                if (addr != savedAddress) {
                    addrPrefs.edit().putString(ADDRESS, addr).apply()
                }
                val port = view.inputPort.text.toString().toInt()
                val hive = HiveWraper("android_clinet", addr,null,null, port)
                hiveAdapter?.addOne(hive)
//                hives.add(hive)

    //                hiveConnection.connect("Windyble", addr, port)
                debug("You clicked ok")

            }
            .show()
    }

    override fun onResume() {
        super.onResume()


    }


}