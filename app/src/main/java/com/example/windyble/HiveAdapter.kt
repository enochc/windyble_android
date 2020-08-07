package com.example.windyble

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Observer
import androidx.lifecycle.observe

import androidx.recyclerview.widget.RecyclerView
import com.example.windyble.models.HiveViewModel
import kotlinx.android.synthetic.main.hive_list_item.view.*

class HiveAdapter(val hives:List<HiveViewModel>): RecyclerView.Adapter<HiveAdapter.ViewHolder>() {

    var hiveClicked: ((HiveViewModel)->Unit)? = null

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