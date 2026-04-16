package com.iticbcn.kronos.ui.adapter

import android.content.Intent
import android.net.Uri
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.RecyclerView
import com.iticbcn.kronos.domain.model.ObjecteUE
import com.iticbcn.kronos.R
import com.iticbcn.kronos.data.local.DataManager
import com.iticbcn.kronos.ui.formulario.FormularioUE
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.iticbcn.kronos.data.remote.S3Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.bumptech.glide.Glide

class ObjecteAdapter(
    private var objectesList: List<ObjecteUE>,
    private val isDatabaseSource: Boolean = false
) : RecyclerView.Adapter<ObjecteAdapter.ObjecteViewHolder>() {

    class ObjecteViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvJaciment: TextView = view.findViewById(R.id.tvObjetoJaciment)
        val tvUeId: TextView = view.findViewById(R.id.tvObjetoName)
        val tvTipus: TextView = view.findViewById(R.id.tvTipus)
        val tvSector: TextView = view.findViewById(R.id.tvSector)
        val tvFecha: TextView = view.findViewById(R.id.tvFecha)
        val ivIcon: ImageView = view.findViewById(R.id.ivObjetoIcon)
        val ivOptions: ImageView = view.findViewById(R.id.ivOptions)
        val lLBody: LinearLayout = view.findViewById(R.id.lLBody)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ObjecteViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_object_ue, parent, false)
        return ObjecteViewHolder(view)
    }

    override fun onBindViewHolder(holder: ObjecteViewHolder, position: Int) {
        val objecte = objectesList[position]

        holder.tvJaciment.text = "Jaciment: ${objecte.jaciment}"
        holder.tvUeId.text = "UE: ${objecte.codi_ue}"
        holder.tvTipus.text = "Tipus UE: ${objecte.tipus_ue}"
        holder.tvSector.text = "Sector: ${objecte.codi_sector}"
        
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val dateString = sdf.format(Date(objecte.fecha_creacio))
        holder.tvFecha.text = "Data: $dateString"

        val primeraImagen = objecte.imatges_urls?.firstOrNull()

        if (!primeraImagen.isNullOrEmpty()) {
            // Glide gestiona automáticamente si es una URL de internet (S3) o una URI local del móvil
            Glide.with(holder.itemView.context)
                .load(primeraImagen)
                .placeholder(R.drawable.ic_launcher_foreground) // Imagen mientras carga
                .error(R.drawable.ic_launcher_foreground)       // Imagen si falla (ej. sin internet)
                .centerCrop()                                   // Ajusta la imagen al tamaño del ImageView
                .into(holder.ivIcon)
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_launcher_foreground)
        }

        val showPopup = { view: View ->
            val popup = PopupMenu(view.context, view)
            popup.inflate(R.menu.menu_item_options)

            val uploadItem = popup.menu.findItem(R.id.action_upload)
            if (isDatabaseSource) {
                uploadItem.title = "Sincronitzar Oracle"
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit -> {
                        val intent = Intent(view.context, FormularioUE::class.java)
                        intent.putExtra("EXTRA_OBJETO", objecte)
                        view.context.startActivity(intent)
                        true
                    }
                    R.id.action_upload -> {
                        val context = view.context
                        if (isDatabaseSource) {
                            // Sincronizar con Oracle
                            CoroutineScope(Dispatchers.Main).launch {
                                val success = S3Service.synchronizeWithOracle()
                                if (success) {
                                    Toast.makeText(context, "Sincronitzat amb Oracle!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Error en la sincronització", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } else {
                            // SUBIR: Local -> S3 -> Firestore
                            CoroutineScope(Dispatchers.Main).launch {
                                val dialog = MaterialAlertDialogBuilder(context)
                                    .setTitle("Pujant UE...")
                                    .setMessage("S'estan pujant les imatges al S3 i les dades a Firestore.")
                                    .setCancelable(false)
                                    .show()

                                val publicUrls = mutableListOf<String>()
                                var hasError = false
                                
                                objecte.imatges_urls.forEach { uriString ->
                                    val publicUrl = S3Service.uploadImage(context, Uri.parse(uriString))
                                    if (publicUrl != null) {
                                        publicUrls.add(publicUrl)
                                    } else {
                                        hasError = true
                                    }
                                }

                                if (hasError && publicUrls.isEmpty() && objecte.imatges_urls.isNotEmpty()) {
                                    dialog.dismiss()
                                    Toast.makeText(context, "Error al pujar les imatges al S3", Toast.LENGTH_LONG).show()
                                    return@launch
                                }

                                val finalObjecte = objecte.copy(imatges_urls = publicUrls, sincronitzat = false)
                                Log.d("ObjecteAdapter", "Subiendo a Firestore con URLs: $publicUrls")

                                val db = FirebaseFirestore.getInstance()
                                val docId = "${finalObjecte.jaciment}_${finalObjecte.codi_ue}".replace("/", "_")
                                
                                db.collection("unitats_estratigrafiques")
                                    .document(docId)
                                    .set(finalObjecte)
                                    .addOnSuccessListener {
                                        dialog.dismiss()
                                        Toast.makeText(context, "UE ${objecte.codi_ue} pujada correctament!", Toast.LENGTH_SHORT).show()
                                        DataManager.deleteUE(context, objecte.codi_ue, objecte.jaciment)
                                        val mutableList = objectesList.toMutableList()
                                        mutableList.removeAt(position)
                                        updateList(mutableList)
                                    }
                                    .addOnFailureListener { e ->
                                        dialog.dismiss()
                                        Toast.makeText(context, "Error Firestore: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                            }
                        }
                        true
                    }
                    R.id.action_delete -> {
                        val context = view.context
                        MaterialAlertDialogBuilder(context).setTitle("Eliminar Unitat Estratigràfica")
                            .setMessage("Estàs segur que vols eliminar la UE ${objecte.codi_ue}? Aquesta acció no es pot desfer.")
                            .setNeutralButton("Cancel·lar") { dialog, _ ->
                                dialog.dismiss()
                            }
                            .setPositiveButton("Eliminar") { _, _ ->
                                if (!isDatabaseSource) {
                                    DataManager.deleteUE(context, objecte.codi_ue, objecte.jaciment)
                                }
                                val mutableList = objectesList.toMutableList()
                                mutableList.removeAt(position)
                                updateList(mutableList)
                            }
                            .show()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        holder.ivOptions.setOnClickListener { showPopup(it) }
        holder.lLBody.setOnClickListener { showPopup(it) }
    }

    override fun getItemCount(): Int = objectesList.size

    fun updateList(newList: List<ObjecteUE>) {
        this.objectesList = newList
        notifyDataSetChanged()
    }
}
