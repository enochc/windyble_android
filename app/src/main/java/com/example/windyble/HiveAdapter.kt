package com.example.windyble

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.example.windyble.databinding.HiveListItemBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope

enum class ConnectionStatus {
    NotConnected,
    Connected,
    Connecting,
}

class HiveAdapter(val hives:ArrayList<HiveWraper>, val context: Context, val scope:Fragment): RecyclerView.Adapter<HiveAdapter.ViewHolder>() {

    fun addOne(hiveWrapper:HiveWraper) {
        this.hives.add(hiveWrapper)
        this.notifyItemInserted(this.hives.size+1)
        debug("Hive added ${hiveWrapper.hiveName} ${this.itemCount}")
    }

    val binding:HiveListItemBinding
        get() = _binding!!

    private var _binding: HiveListItemBinding? = null

    var hiveClicked: ((HiveWraper)->Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflator = LayoutInflater.from(parent.context)
        _binding = HiveListItemBinding.inflate(inflator, parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return hives.size
    }


    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val hive = this.hives.get(position)

        holder.view.hiveName = hive.hiveName
        holder.view.hiveBtn.setOnClickListener {
            hiveClicked?.invoke(hive)
        }

        hive.connected.observe(scope.viewLifecycleOwner, Observer {
            debug("<<<<<< CONNECTED: $it")
            holder.view.connected = if (it) ConnectionStatus.Connected else ConnectionStatus.NotConnected
        })

        holder.view.connected = ConnectionStatus.NotConnected
        holder.view.connectBtn.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Confirm to run Hive?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("yes") {_,_->
                    hive.resumeHive(context, scope.lifecycleScope)
                    holder.view.connected = ConnectionStatus.Connecting

                }
                .show()
        }
        holder.view.disconnectBtn.setOnClickListener {
            hive.hangup()
        }
        holder.view.closeBtn.setOnClickListener {
            AlertDialog.Builder(context)
                .setTitle("Remove ${hive.hiveName}?")
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton("yes") {_, _ ->
                    hive.hangup()
                    hives.removeAt(position)
                    notifyItemRemoved(position)
                    notifyItemRangeChanged(position, itemCount);
                }
                .show()
        }
    }

    class ViewHolder(val view: HiveListItemBinding):RecyclerView.ViewHolder(view.root){

//        var name:String? ="unset"
//        set(value){
//            view.hiveName = value
//            field = value
//        }

    }
}