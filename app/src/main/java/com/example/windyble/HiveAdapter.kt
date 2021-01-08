package com.example.windyble

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.recyclerview.widget.RecyclerView
import com.example.windyble.databinding.HiveListItemBinding

class HiveAdapter(val hives:List<HiveWraper>): RecyclerView.Adapter<HiveAdapter.ViewHolder>() {

    val binding:HiveListItemBinding
        get() = _binding!!

    private var _binding: HiveListItemBinding? = null

    var hiveClicked: ((HiveWraper)->Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HiveAdapter.ViewHolder {
//        val view = LayoutInflater.from(parent.context)
//            .inflate(R.layout.hive_list_item, parent, false)
        val inflator = LayoutInflater.from(parent.context)
        _binding = HiveListItemBinding.inflate(inflator, parent, false)
        return ViewHolder(binding)
    }

    override fun getItemCount(): Int {
        return hives.size
    }

    override fun onBindViewHolder(holder: HiveAdapter.ViewHolder, position: Int) {
        val hive = hives.get(position)

        holder.name.text = hive.hiveName
        holder.name.setOnClickListener {
            hiveClicked?.invoke(hive)
        }

    }

    class ViewHolder(val view: HiveListItemBinding):RecyclerView.ViewHolder(view.root){
//        val b = binding
        val name = view.hiveBtn

    }
}