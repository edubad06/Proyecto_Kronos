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
    private val isDatabaseSource: Boolean = false,
    private val currentUserEmail: String = ""
) : RecyclerView.Adapter<ObjecteAdapter.ObjecteViewHolder>() {

    private var userRole: String = "tecnic"

    fun setUserRole(role: String) {
        this.userRole = role
        notifyDataSetChanged()
    }

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
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_object_ue, parent, false)
        return ObjecteViewHolder(view)
    }

    override fun onBindViewHolder(holder: ObjecteViewHolder, position: Int) {
        val objecte = objectesList[position]

        holder.tvJaciment.text = "Jaciment: ${objecte.jaciment}"
        holder.tvUeId.text = "UE: ${objecte.codi_ue}"
        holder.tvTipus.text = "Tipus UE: ${objecte.tipus_ue}"
        holder.tvSector.text = "Sector: ${objecte.codi_sector}"
        
        val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        holder.tvFecha.text = "Data: ${sdf.format(Date(objecte.fecha_creacio))}"

        val primeraImagen = objecte.imatges_urls?.firstOrNull()

        if (!primeraImagen.isNullOrEmpty()) {
            Glide.with(holder.itemView.context)
                .load(primeraImagen)
                .placeholder(R.drawable.ic_launcher_foreground)
                .error(R.drawable.ic_launcher_foreground)
                .centerCrop()
                .into(holder.ivIcon)
                
            holder.ivIcon.setOnClickListener {
                mostrarImagenAmpliada(holder.itemView.context, primeraImagen)
            }
        } else {
            holder.ivIcon.setImageResource(R.drawable.ic_launcher_foreground)
            holder.ivIcon.setOnClickListener(null)
        }

        val showPopup = { view: View ->
            val popup = PopupMenu(view.context, view)
            val canEditOrDelete = !isDatabaseSource || userRole == "director" || (userRole == "tecnic" && objecte.registrat_per == currentUserEmail)

            if (canEditOrDelete) {
                popup.inflate(R.menu.menu_item_options)
                val uploadItem = popup.menu.findItem(R.id.action_upload)
                if (isDatabaseSource) uploadItem.isVisible = false
            } else {
                popup.menu.add("Visualitzar").setOnMenuItemClickListener {
                    val intent = Intent(view.context, FormularioUE::class.java)
                    intent.putExtra("EXTRA_OBJETO", objecte)
                    intent.putExtra("EXTRA_READ_ONLY", true)
                    view.context.startActivity(intent)
                    true
                }
            }

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_edit -> {
                        val intent = Intent(view.context, FormularioUE::class.java)
                        intent.putExtra("EXTRA_OBJETO", objecte)
                        intent.putExtra("EXTRA_IS_DB", isDatabaseSource)
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

        holder.ivOptions.setOnClickListener { showPopup(it) }
        holder.lLBody.setOnClickListener { showPopup(it) }
    }

    private fun mostrarImagenAmpliada(context: android.content.Context, url: String) {
        Log.d("ZoomDebug", "Intentando cargar imagen en grande: $url")
        
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(android.graphics.Color.BLACK) // Fondo negro para ver si el ImageView se crea
        }
        
        Glide.with(context)
            .load(url)
            .listener(object : com.bumptech.glide.request.RequestListener<android.graphics.drawable.Drawable> {
                override fun onLoadFailed(
                    e: com.bumptech.glide.load.engine.GlideException?,
                    model: Any?,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.e("ZoomDebug", "Glide falló al cargar la imagen: ${e?.message}")
                    return false
                }

                override fun onResourceReady(
                    resource: android.graphics.drawable.Drawable,
                    model: Any,
                    target: com.bumptech.glide.request.target.Target<android.graphics.drawable.Drawable>?,
                    dataSource: com.bumptech.glide.load.DataSource,
                    isFirstResource: Boolean
                ): Boolean {
                    Log.d("ZoomDebug", "Imagen cargada correctamente en el diálogo")
                    return false
                }
            })
            .into(imageView)

        MaterialAlertDialogBuilder(context)
            .setView(imageView)
            .setPositiveButton("Tancar", null)
            .show()
    }

    private fun subirAFirestore(context: android.content.Context, objecte: ObjecteUE, position: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle("Pujant UE...")
                .setMessage("S'estan pujant les imatges al S3 i les dades a Firestore.")
                .setCancelable(false).show()

            val publicUrls = mutableListOf<String>()
            var hasError = false
            objecte.imatges_urls.forEach { uriString ->
                val publicUrl = S3Service.uploadImage(context, Uri.parse(uriString))
                if (publicUrl != null) publicUrls.add(publicUrl) else hasError = true
            }

            if (hasError && publicUrls.isEmpty() && objecte.imatges_urls.isNotEmpty()) {
                dialog.dismiss()
                Toast.makeText(context, "Error al pujar les imatges al S3", Toast.LENGTH_LONG).show()
                return@launch
            }

            val finalObjecte = objecte.copy(imatges_urls = publicUrls, sincronitzat = false)
            val db = FirebaseFirestore.getInstance()
            val docId = "${finalObjecte.jaciment}_${finalObjecte.codi_ue}".replace("/", "_")
            
            db.collection("unitats_estratigrafiques").document(docId).set(finalObjecte)
                .addOnSuccessListener {
                    dialog.dismiss()
                    Toast.makeText(context, "UE ${objecte.codi_ue} pujada correctament!", Toast.LENGTH_SHORT).show()
                    DataManager.deleteUE(context, objecte.codi_ue, objecte.jaciment)
                    val mutableList = objectesList.toMutableList()
                    mutableList.removeAt(position)
                    updateList(mutableList)
                }
                .addOnFailureListener { dialog.dismiss() }
        }
    }

    private fun confirmarEliminacion(context: android.content.Context, objecte: ObjecteUE, position: Int) {
        MaterialAlertDialogBuilder(context).setTitle("Eliminar Unitat Estratigràfica")
            .setMessage("Estàs segur que vols eliminar la UE ${objecte.codi_ue}? Aquesta acció no es pot desfer.")
            .setNeutralButton("Cancel·lar", null)
            .setPositiveButton("Eliminar") { _, _ ->
                if (isDatabaseSource) {
                    val docId = "${objecte.jaciment}_${objecte.codi_ue}".replace("/", "_")
                    FirebaseFirestore.getInstance().collection("unitats_estratigrafiques").document(docId).delete()
                } else {
                    DataManager.deleteUE(context, objecte.codi_ue, objecte.jaciment)
                }
                val mutableList = objectesList.toMutableList()
                mutableList.removeAt(position)
                updateList(mutableList)
            }
            .show()
    }

    override fun getItemCount(): Int = objectesList.size
    fun updateList(newList: List<ObjecteUE>) {
        this.objectesList = newList
        notifyDataSetChanged()
    }
}
