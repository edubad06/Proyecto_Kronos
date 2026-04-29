package com.iticbcn.kronos.ui.adapter

import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
            Glide.with(holder.itemView.context)
                .load(Uri.parse(objecte.imatges_urls[0]))
                .placeholder(R.drawable.upload_document)
                .into(holder.ivPreview)
        } else {
            holder.ivPreview.setImageResource(R.drawable.upload_document)
        }

        val menuClickListener = View.OnClickListener { view ->
            showPopupMenu(view, objecte, position)
        }

        if (isDatabaseSource) {
            holder.ivOptions.visibility = View.GONE
            val canEdit = objecte.registrat_per.trim().equals(currentUserEmail.trim(), ignoreCase = true) || userRole == "director"
            holder.itemView.setOnClickListener { view ->
                val intent = Intent(view.context, FormularioUE::class.java).apply {
                    putExtra("EXTRA_OBJETO", objecte)
                    putExtra("EXTRA_IS_DB", isDatabaseSource)
                    if (!canEdit) putExtra("EXTRA_READ_ONLY", true)
                }
                view.context.startActivity(intent)
            }
        } else {
            holder.ivOptions.visibility = View.VISIBLE
            holder.itemView.setOnClickListener(menuClickListener)
            holder.ivOptions.setOnClickListener(menuClickListener)
        }
    }

    private fun showPopupMenu(view: View, objecte: ObjecteUE, position: Int) {
        val popup = PopupMenu(view.context, view)
        popup.menuInflater.inflate(R.menu.menu_item_options, popup.menu)
        popup.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.action_edit -> {
                    val intent = Intent(view.context, FormularioUE::class.java).apply {
                        putExtra("EXTRA_OBJETO", objecte)
                        putExtra("EXTRA_IS_DB", isDatabaseSource)
                    }
                    view.context.startActivity(intent)
                    true
                }
                R.id.action_upload -> {
                    subirAFirestore(view.context, objecte, position)
                    true
                }
                R.id.action_delete -> {
                    confirmarEliminacion(view.context, objecte, position)
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun subirAFirestore(context: android.content.Context, objecte: ObjecteUE, position: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle("Pujant UE...")
                .setMessage("Si us plau espera")
                .setCancelable(false).show()

            try {
                val publicUrls = mutableListOf<String>()
                var hasError = false
                
                // 1. Subida a S3
                for (uriString in objecte.imatges_urls) {
                    val publicUrl = S3Service.uploadImage(context, Uri.parse(uriString))
                    if (publicUrl != null) publicUrls.add(publicUrl) else hasError = true
                }

                if (hasError && publicUrls.isEmpty() && objecte.imatges_urls.isNotEmpty()) {
                    throw Exception("Error pujant imatges. Comprova la teva conexió.")
                }

                // 2. Subida a Firestore con AWAIT (Sincronización real)
                val finalObjecte = objecte.copy(imatges_urls = publicUrls, sincronitzat = true)
                val docId = "${finalObjecte.jaciment}_${finalObjecte.codi_ue}".replace("/", "_")
                
                FirebaseFirestore.getInstance()
                    .collection("unitats_estratigrafiques")
                    .document(docId)
                    .set(finalObjecte)
                    .await()

                // 3. Éxito: Borrar local y actualizar lista
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

    private fun confirmarEliminacion(context: android.content.Context, objecte: ObjecteUE, position: Int) {
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
