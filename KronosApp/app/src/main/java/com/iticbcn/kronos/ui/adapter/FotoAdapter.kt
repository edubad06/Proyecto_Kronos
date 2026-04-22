package com.iticbcn.kronos.ui.adapter

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.iticbcn.kronos.R

class FotoAdapter(
    private val uris: MutableList<Uri>,
    private val isReadOnly: Boolean = false // Nuevo parámetro
) : RecyclerView.Adapter<FotoAdapter.ViewHolder>() {
    
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.iv_mini_foto)
        val btnDelete: ImageView = view.findViewById(R.id.btn_delete_foto)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_foto_mini, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val uri = uris[position]
        
        Glide.with(holder.img.context)
            .load(uri)
            .placeholder(R.drawable.ic_launcher_foreground)
            .into(holder.img)

        // Si es solo lectura, escondemos la cruz de borrar
        if (isReadOnly) {
            holder.btnDelete.visibility = View.GONE
        } else {
            holder.btnDelete.visibility = View.VISIBLE
            holder.btnDelete.setOnClickListener {
                val currentPos = holder.adapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    uris.removeAt(currentPos)
                    notifyItemRemoved(currentPos)
                    notifyItemRangeChanged(currentPos, uris.size)
                }
            }
        }
    }

    override fun getItemCount() = uris.size
}
