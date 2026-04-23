package com.iticbcn.kronos.ui.adapter

import android.content.Intent
import android.graphics.Color
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
            Glide.with(holder.itemView.context)
                .load(Uri.parse(objecte.imatges_urls[0]))
                .placeholder(R.drawable.upload_document)
                .into(holder.ivPreview)

            holder.ivPreview.setOnClickListener {
                showImageDialog(holder.itemView.context, Uri.parse(objecte.imatges_urls[0]))
            }
        } else {
            holder.ivPreview.setImageResource(R.drawable.upload_document)
            holder.ivPreview.setOnClickListener(null)
        }

        // Listener para abrir el menú (desplegable)
        val menuClickListener = View.OnClickListener { view ->
            showPopupMenu(view, objecte, position)
        }

        // Listener para abrir el formulario directamente
        val openFormListener = View.OnClickListener { view ->
            val intent = Intent(view.context, FormularioUE::class.java).apply {
                putExtra("EXTRA_OBJETO", objecte)
                putExtra("EXTRA_IS_DB", isDatabaseSource)
            }
            view.context.startActivity(intent)
        }

        if (isDatabaseSource) {
            // En la base de datos: ocultamos botón de tres puntos y clicamos para ir directo al formulario
            holder.ivOptions.visibility = View.GONE
            
            val canEdit = objecte.registrat_per == currentUserEmail || userRole == "director"
            
            val dbClickListener = View.OnClickListener { view ->
                val intent = Intent(view.context, FormularioUE::class.java).apply {
                    putExtra("EXTRA_OBJETO", objecte)
                    putExtra("EXTRA_IS_DB", isDatabaseSource)
                    if (!canEdit) putExtra("EXTRA_READ_ONLY", true)
                }
                view.context.startActivity(intent)
            }

            holder.itemView.setOnClickListener(dbClickListener)
            holder.lLBody.setOnClickListener(dbClickListener)
            holder.tvJaciment.setOnClickListener(dbClickListener)
            holder.tvSector.setOnClickListener(dbClickListener)
            holder.tvCodiUE.setOnClickListener(dbClickListener)
            holder.tvTipus.setOnClickListener(dbClickListener)

        } else {
            // En local: mostramos botón de tres puntos y abrimos el menú al clicar en cualquier parte
            holder.ivOptions.visibility = View.VISIBLE
            
            holder.itemView.setOnClickListener(menuClickListener)
            holder.lLBody.setOnClickListener(menuClickListener)
            holder.ivOptions.setOnClickListener(menuClickListener)
            holder.tvJaciment.setOnClickListener(menuClickListener)
            holder.tvSector.setOnClickListener(menuClickListener)
            holder.tvCodiUE.setOnClickListener(menuClickListener)
            holder.tvTipus.setOnClickListener(menuClickListener)
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

    private fun confirmarEliminacion(context: android.content.Context, objecte: ObjecteUE, position: Int) {
        MaterialAlertDialogBuilder(context)
            .setTitle("Eliminar UE")
            .setMessage("Estàs segur que vols eliminar aquesta UE localment?")
            .setPositiveButton("Eliminar") { _, _ ->
                DataManager.deleteUE(context, objecte.codi_ue, objecte.jaciment)
                val mutableList = objectesList.toMutableList()
                mutableList.removeAt(position)
                updateList(mutableList)
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

    private fun showImageDialog(context: android.content.Context, imageUri: Uri) {
        val dialog = android.app.Dialog(context)
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        
        val imageView = ImageView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
            setOnClickListener { dialog.dismiss() }
        }

        Glide.with(context)
            .load(imageUri)
            .placeholder(R.drawable.upload_document)
            .error(R.drawable.delete)
            .into(imageView)

        dialog.setContentView(imageView)
        
        dialog.window?.let { window ->
            window.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setDimAmount(0.85f)
        }

        dialog.setCanceledOnTouchOutside(true)
        dialog.show()
    }

    private fun subirAFirestore(context: android.content.Context, objecte: ObjecteUE, position: Int) {
        CoroutineScope(Dispatchers.Main).launch {
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle("Pujant UE...")
                .setMessage("Si us plau espera")
                .setCancelable(false).show()

            val publicUrls = mutableListOf<String>()
            var hasError = false
            objecte.imatges_urls.forEach { uriString ->
                val publicUrl = S3Service.uploadImage(context, Uri.parse(uriString))
                if (publicUrl != null) publicUrls.add(publicUrl) else hasError = true
            }

            if (hasError && publicUrls.isEmpty() && objecte.imatges_urls.isNotEmpty()) {
                dialog.dismiss()
                MaterialAlertDialogBuilder(context)
                    .setTitle("Error de connexió")
                    .setMessage("Senyal massa fluixa o inexistent, torna a probar més endavant")
                    .setPositiveButton("D'acord", null)
                    .show()
                return@launch
            }

            val finalObjecte = objecte.copy(imatges_urls = publicUrls, sincronitzat = true)
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
                .addOnFailureListener { 
                    dialog.dismiss()
                    MaterialAlertDialogBuilder(context)
                        .setTitle("Error de connexió")
                        .setMessage("Senyal massa fluixa o inexistent, torna a probar més endavant")
                        .setPositiveButton("D'acord", null)
                        .show()
                }
        }
    }
}
