package com.example.windyble

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.recyclerview.widget.RecyclerView
import kotlinx.android.synthetic.main.hive_list_item.view.*

class HiveAdapter(val hives:List<HiveWraper>): RecyclerView.Adapter<HiveAdapter.ViewHolder>() {

    var hiveClicked: ((HiveWraper)->Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HiveAdapter.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.hive_list_item, parent, false)
        return ViewHolder(view)
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

    class ViewHolder(val view: View):RecyclerView.ViewHolder(view){
        val name = view.hive_btn

    }
}