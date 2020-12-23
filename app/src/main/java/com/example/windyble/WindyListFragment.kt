package com.example.windyble

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.FragmentTransaction
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider

import com.example.windyble.ui.WindybleFragment
import kotlinx.android.synthetic.main.fragment_windy_list.*


/**
 * A simple [Fragment] subclass as the default destination in the navigation.
 */
class WindyListFragment : Fragment() {

    val hives = mutableListOf<HiveWraper>()
    var hiveAdapter:HiveAdapter? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


    }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {


        // TODO this currently only supports a single hive, to have multiple ie: more then one windyble
        //  this will require modification

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_windy_list, container, false)
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

    override fun onResume() {
        super.onResume()


    }


}