package com.iticbcn.kronos.ui.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.iticbcn.kronos.R

class FotoAdapter(private val uris: MutableList<Uri>) : RecyclerView.Adapter<FotoAdapter.ViewHolder>() {
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.iv_mini_foto)
        val btnDelete: ImageView = view.findViewById(R.id.btn_delete_foto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_foto_mini, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.img.setImageURI(uris[position])
        holder.btnDelete.setOnClickListener {
            uris.removeAt(position)
            notifyDataSetChanged()
        }
    }

    override fun getItemCount() = uris.size
    fun getUris() = uris
}