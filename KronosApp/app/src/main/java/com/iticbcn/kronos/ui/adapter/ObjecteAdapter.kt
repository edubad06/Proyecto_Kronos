package com.iticbcn.kronos.ui.adapter

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.iticbcn.kronos.R
import com.iticbcn.kronos.data.local.db.entities.ObjecteUE
import com.iticbcn.kronos.ui.formulario.FormularioUE
import java.text.SimpleDateFormat
import java.util.Locale

class ObjecteAdapter(
    private var objectesList: List<ObjecteUE>,
    private val isDatabaseSource: Boolean = false,
    private val currentUserEmail: String = ""
) : RecyclerView.Adapter<ObjecteAdapter.ObjecteViewHolder>() {

    private var userRole: String = "tecnic"
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    class ObjecteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvJaciment: TextView = view.findViewById(R.id.tvObjetoJaciment)
        val tvSector: TextView = view.findViewById(R.id.tvSector)
        val tvCodiUE: TextView = view.findViewById(R.id.tvObjetoName)
        val tvTipus: TextView = view.findViewById(R.id.tvTipus)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val ivPreview: ImageView = view.findViewById(R.id.ivObjetoIcon)
        val lLBody: LinearLayout = view.findViewById(R.id.lLBody)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ObjecteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_object_ue, parent, false)
        return ObjecteViewHolder(view)
    }

    override fun onBindViewHolder(holder: ObjecteViewHolder, position: Int) {
        val objecte = objectesList[position]
        
        holder.tvJaciment.text = objecte.jaciment
        holder.tvSector.text = objecte.codi_sector
        holder.tvCodiUE.text = "UE: ${objecte.codi_ue}"
        holder.tvTipus.text = objecte.tipus_ue
        holder.tvFecha.text = dateFormat.format(objecte.data)

        if (objecte.imatges_urls.isNotEmpty()) {
            Glide.with(holder.itemView.context)
                .load(objecte.imatges_urls[0])
                .placeholder(R.drawable.upload_document)
                .centerCrop()
                .into(holder.ivPreview)

            holder.ivPreview.setOnClickListener {
                mostrarImagenAmpliada(holder.itemView.context, objecte.imatges_urls[0])
            }
        } else {
            holder.ivPreview.setImageResource(R.drawable.upload_document)
            holder.ivPreview.setOnClickListener(null)
        }

        val canEditOrDelete = objecte.registrat_per.trim().equals(currentUserEmail.trim(), ignoreCase = true) || userRole == "director"

        val mainAction = View.OnClickListener { v ->
            val intent = Intent(v.context, FormularioUE::class.java).apply {
                putExtra("EXTRA_OBJETO", objecte)
                putExtra("EXTRA_IS_DB", isDatabaseSource)
                if (isDatabaseSource && !canEditOrDelete) putExtra("EXTRA_READ_ONLY", true)
            }
            v.context.startActivity(intent)
        }

        holder.itemView.setOnClickListener(mainAction)
        holder.lLBody.setOnClickListener(mainAction)
    }

    private fun mostrarImagenAmpliada(context: Context, url: String) {
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(32, 32, 32, 32)
        }
        
        Glide.with(context).load(url).into(imageView)

        val dialog = MaterialAlertDialogBuilder(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
            .setView(imageView)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.argb(204, 0, 0, 0))) // 80% negro

        imageView.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    override fun getItemCount(): Int = objectesList.size

    fun updateList(newList: List<ObjecteUE>) {
        objectesList = newList
        notifyDataSetChanged()
    }

    fun setUserRole(role: String) {
        this.userRole = role
        notifyDataSetChanged()
    }
}
