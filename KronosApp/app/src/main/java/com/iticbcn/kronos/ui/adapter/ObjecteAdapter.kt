package com.iticbcn.kronos.ui.adapter

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.iticbcn.kronos.R
import com.iticbcn.kronos.data.local.DataManager
import com.iticbcn.kronos.domain.model.ObjecteUE
import com.iticbcn.kronos.data.remote.S3Service
import com.iticbcn.kronos.ui.formulario.FormularioUE
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class ObjecteAdapter(
    private var objectesList: List<ObjecteUE>,
    private val isDatabaseSource: Boolean = false,
    private val currentUserEmail: String = ""
) : RecyclerView.Adapter<ObjecteAdapter.ObjecteViewHolder>() {

    private var userRole: String = "tecnic"

    class ObjecteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvJaciment: TextView = view.findViewById(R.id.tvObjetoJaciment)
        val tvSector: TextView = view.findViewById(R.id.tvSector)
        val tvCodiUE: TextView = view.findViewById(R.id.tvObjetoName)
        val tvTipus: TextView = view.findViewById(R.id.tvTipus)
        val ivPreview: ImageView = view.findViewById(R.id.ivObjetoIcon)
        val ivOptions: ImageView = view.findViewById(R.id.ivOptions)
        val lLBody: LinearLayout = view.findViewById(R.id.lLBody)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ObjecteViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_object_ue, parent, false)
        return ObjecteViewHolder(view)
    }

    override fun onBindViewHolder(holder: ObjecteViewHolder, position: Int) {
        val objecte = objectesList[position]
        holder.tvJaciment.text = "Jaciment: ${objecte.jaciment}"
        holder.tvSector.text = "Sector: ${objecte.codi_sector}"
        holder.tvCodiUE.text = "UE: ${objecte.codi_ue}"
        holder.tvTipus.text = "Tipus UE: ${objecte.tipus_ue}"

        if (objecte.imatges_urls.isNotEmpty()) {
            val imageUrl = objecte.imatges_urls[0]
            Glide.with(holder.itemView.context)
                .load(Uri.parse(imageUrl))
                .placeholder(R.drawable.upload_document)
                .into(holder.ivPreview)
            
            // Listener específico para la imagen para ampliarla
            holder.ivPreview.setOnClickListener {
                mostrarImagenAmpliada(holder.itemView.context, imageUrl)
            }
        } else {
            holder.ivPreview.setImageResource(R.drawable.upload_document)
            holder.ivPreview.setOnClickListener(null)
        }

        // Definimos la acción principal (Ficha o Menú)
        val mainAction = View.OnClickListener { v ->
            if (isDatabaseSource) {
                val canEdit = objecte.registrat_per.trim().equals(currentUserEmail.trim(), ignoreCase = true) || userRole == "director"
                val intent = Intent(v.context, FormularioUE::class.java).apply {
                    putExtra("EXTRA_OBJETO", objecte)
                    putExtra("EXTRA_IS_DB", true)
                    if (!canEdit) putExtra("EXTRA_READ_ONLY", true)
                }
                v.context.startActivity(intent)
            } else {
                showPopupMenu(holder.ivOptions, objecte, position)
            }
        }

        // Aplicamos la acción a todo el item y al cuerpo de texto para que no se pierda el clic
        holder.itemView.setOnClickListener(mainAction)
        holder.lLBody.setOnClickListener(mainAction)

        if (isDatabaseSource) {
            holder.ivOptions.visibility = View.GONE
        } else {
            holder.ivOptions.visibility = View.VISIBLE
            holder.ivOptions.setOnClickListener(mainAction)
        }
    }

    private fun mostrarImagenAmpliada(context: Context, url: String) {
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setPadding(16, 16, 16, 16)
        }
        
        Glide.with(context)
            .load(Uri.parse(url))
            .placeholder(R.drawable.upload_document)
            .into(imageView)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(imageView)
            .create()

        // Al clicar en la imagen o fuera se cierra el diálogo
        imageView.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun showPopupMenu(anchor: View, objecte: ObjecteUE, position: Int) {
        val popup = PopupMenu(anchor.context, anchor)
        popup.menuInflater.inflate(R.menu.menu_item_options, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    val intent = Intent(anchor.context, FormularioUE::class.java).apply {
                        putExtra("EXTRA_OBJETO", objecte)
                        putExtra("EXTRA_IS_DB", isDatabaseSource)
                    }
                    anchor.context.startActivity(intent)
                    true
                }
                R.id.action_upload -> {
                    subirAFirestore(anchor.context, objecte, position)
                    true
                }
                R.id.action_delete -> {
                    confirmarEliminacion(anchor.context, objecte, position)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun subirAFirestore(context: Context, objecte: ObjecteUE, position: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle("Pujant UE...")
                .setMessage("Si us plau espera")
                .setCancelable(false).show()

            try {
                val publicUrls = mutableListOf<String>()
                var hasError = false
                
                for (uriString in objecte.imatges_urls) {
                    val publicUrl = S3Service.uploadImage(context, Uri.parse(uriString))
                    if (publicUrl != null) publicUrls.add(publicUrl) else hasError = true
                }

                if (hasError && publicUrls.isEmpty() && objecte.imatges_urls.isNotEmpty()) {
                    throw Exception("Error pujant imatges. Comprova la teva conexió.")
                }

                val finalObjecte = objecte.copy(imatges_urls = publicUrls, sincronitzat = true)
                val docId = "${finalObjecte.jaciment}_${finalObjecte.codi_ue}".replace("/", "_")
                
                FirebaseFirestore.getInstance()
                    .collection("unitats_estratigrafiques")
                    .document(docId)
                    .set(finalObjecte)
                    .await()

                DataManager.deleteUE(context, objecte.codi_ue, objecte.jaciment)
                val mutableList = objectesList.toMutableList()
                if (position < mutableList.size) {
                    mutableList.removeAt(position)
                    updateList(mutableList)
                }
                
                dialog.dismiss()
                Toast.makeText(context, "UE ${objecte.codi_ue} pujada correctament!", Toast.LENGTH_SHORT).show()

            } catch (e: Exception) {
                dialog.dismiss()
                MaterialAlertDialogBuilder(context)
                    .setTitle("Error en la pujada")
                    .setMessage(e.message ?: "Error de conexió o de servidor")
                    .setPositiveButton("D'acord", null)
                    .show()
            }
        }
    }

    private fun confirmarEliminacion(context: Context, objecte: ObjecteUE, position: Int) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Eliminar UE")
            .setMessage("Estàs segur que vols eliminar aquesta UE localment?")
            .setPositiveButton("Eliminar") { _, _ ->
                DataManager.deleteUE(context, objecte.codi_ue, objecte.jaciment)
                val mutableList = objectesList.toMutableList()
                if (position < mutableList.size) {
                    mutableList.removeAt(position)
                    updateList(mutableList)
                }
                Toast.makeText(context, "UE eliminada localment", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel·lar", null)
            .show()
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
