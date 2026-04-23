package com.iticbcn.kronos.ui.ue

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iticbcn.kronos.data.local.DataManager
import com.iticbcn.kronos.ui.adapter.ObjecteAdapter
import com.iticbcn.kronos.domain.model.ObjecteUE
import com.iticbcn.kronos.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.iticbcn.kronos.ui.formulario.FormularioUE
import com.google.firebase.auth.FirebaseAuth
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.firestore.FirebaseFirestore
import com.iticbcn.kronos.data.remote.S3Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class UEFragment : Fragment() {

    private lateinit var adapter: ObjecteAdapter
    private var originalList: List<ObjecteUE> = emptyList()

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyMessage: TextView
    private var uploadJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_ue_local, container, false)

        recyclerView = view.findViewById(R.id.rvObjectes)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)
        val fabAdd: FloatingActionButton = view.findViewById(R.id.fab_add_ue)
        val fabUploadAll: FloatingActionButton = view.findViewById(R.id.fab_upload_all)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        originalList = DataManager.getUEListLocal(requireContext())

        adapter = ObjecteAdapter(originalList.toMutableList())
        recyclerView.adapter = adapter

        fabAdd.setOnClickListener {
            val intent = Intent(requireContext(), FormularioUE::class.java)
            startActivity(intent)
        }

        fabUploadAll.setOnClickListener {
            if (originalList.isEmpty()) {
                Toast.makeText(requireContext(), "No hi ha UEs per pujar", Toast.LENGTH_SHORT).show()
            } else {
                mostrarConfirmacionSubidaMasiva()
            }
        }

        return view
    }

    private fun mostrarConfirmacionSubidaMasiva() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pujada massiva")
            .setMessage("Vols pujar les ${originalList.size} UEs locals al servidor? Aquesta operació pot trigar una estona.")
            .setNegativeButton("Cancel·lar", null)
            .setPositiveButton("Pujar tot") { _, _ ->
                subirTodasLasUEs()
            }
            .show()
    }

    private fun subirTodasLasUEs() {
        uploadJob = CoroutineScope(Dispatchers.Main).launch {
            val progressDialog = MaterialAlertDialogBuilder(requireContext())
                .setTitle("Pujant UEs...")
                .setMessage("Si us plau espera mentre es processen totes les dades.")
                .setCancelable(false)
                .setNegativeButton("Aturar") { _, _ ->
                    uploadJob?.cancel()
                    Toast.makeText(requireContext(), "Pujada cancel·lada", Toast.LENGTH_SHORT).show()
                }
                .show()

            var exitosas = 0
            var fallidas = 0

            try {
                // Iteramos sobre una copia para evitar problemas al modificar la lista original si fuera necesario
                val listaAPujar = originalList.toList()
                
                for (objecte in listaAPujar) {
                    val publicUrls = mutableListOf<String>()
                    var hasError = false
                    
                    // 1. Subir imágenes
                    objecte.imatges_urls.forEach { uriString ->
                        val publicUrl = S3Service.uploadImage(requireContext(), Uri.parse(uriString))
                        if (publicUrl != null) publicUrls.add(publicUrl) else hasError = true
                    }

                    if (hasError && publicUrls.isEmpty() && objecte.imatges_urls.isNotEmpty()) {
                        fallidas++
                        continue 
                    }

                    // 2. Guardar en Firestore
                    val finalObjecte = objecte.copy(imatges_urls = publicUrls, sincronitzat = true)
                    val db = FirebaseFirestore.getInstance()
                    val docId = "${finalObjecte.jaciment}_${finalObjecte.codi_ue}".replace("/", "_")
                    
                    try {
                        // Usamos un listener síncrono simulado con corrutinas si fuera necesario, 
                        // pero aquí usamos la lógica estándar de éxito/error.
                        db.collection("unitats_estratigrafiques").document(docId).set(finalObjecte)
                        
                        // Si tiene éxito, borramos local y sumamos contador
                        DataManager.deleteUE(requireContext(), objecte.codi_ue, objecte.jaciment)
                        exitosas++
                    } catch (e: Exception) {
                        fallidas++
                    }
                }

                progressDialog.dismiss()
                val mensajeFinal = "Pujada finalitzada: $exitosas exitoses, $fallidas fallides."
                Toast.makeText(requireContext(), mensajeFinal, Toast.LENGTH_LONG).show()
                
                // Refrescar lista local
                originalList = DataManager.getUEListLocal(requireContext())
                updateUI(originalList)

            } catch (e: Exception) {
                progressDialog.dismiss()
                if (e !is kotlinx.coroutines.CancellationException) {
                    Toast.makeText(requireContext(), "Error en la pujada massiva", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        originalList = DataManager.getUEListLocal(requireContext())
        updateUI(originalList)
    }

    fun applyFilters(jaciment: String, sector: String, ue: String, tipus: String, onlyMine: Boolean = false) {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
        val filteredList = originalList.filter { item ->
            val matchJaciment = jaciment.isEmpty() || item.jaciment == jaciment
            val matchSector = sector.isEmpty() || item.codi_sector == sector
            val matchUE = ue.isEmpty() || item.codi_ue.contains(ue, ignoreCase = true)
            val matchTipus = tipus.isEmpty() || item.tipus_ue == tipus
            val matchOnlyMine = !onlyMine || item.registrat_per == currentUserEmail

            matchJaciment && matchSector && matchUE && matchTipus && matchOnlyMine
        }
        updateUI(filteredList, isFilter = true)
    }

    private fun updateUI(list: List<ObjecteUE>, isFilter: Boolean = false) {
        if (list.isEmpty()) {
            recyclerView.visibility = View.GONE
            tvEmptyMessage.visibility = View.VISIBLE
            if (isFilter) {
                tvEmptyMessage.setText(R.string.text_filtre_error)
            } else {
                tvEmptyMessage.setText(R.string.text_ue_local_list_empty)
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            tvEmptyMessage.visibility = View.GONE
            adapter.updateList(list)
        }
    }
}
