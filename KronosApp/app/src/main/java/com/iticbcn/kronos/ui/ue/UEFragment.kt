package com.iticbcn.kronos.ui.ue

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
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
import kotlinx.coroutines.tasks.await

class UEFragment : Fragment() {

    private lateinit var adapter: ObjecteAdapter
    private var originalList: List<ObjecteUE> = emptyList()
    private var userRol: String = "" 
    private var rolYaCargado = false

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
        
        adapter = ObjecteAdapter(mutableListOf())
        recyclerView.adapter = adapter

        fabAdd.setOnClickListener {
            val intent = Intent(requireContext(), FormularioUE::class.java)
            startActivity(intent)
        }

        fabUploadAll.setOnClickListener {
            val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
            
            // Filtro robusto para la subida (ignorando espacios y mayúsculas)
            val listaAPujar = if (userRol == "director") {
                originalList
            } else {
                originalList.filter { it.registrat_per.trim().equals(currentUserEmail.trim(), ignoreCase = true) }
            }

            if (listaAPujar.isEmpty()) {
                Toast.makeText(requireContext(), "No hi ha UEs per pujar", Toast.LENGTH_SHORT).show()
            } else {
                mostrarConfirmacionSubidaMasiva(listaAPujar)
            }
        }

        return view
    }

    private fun mostrarConfirmacionSubidaMasiva(listaAPujar: List<ObjecteUE>) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle("Pujada massiva")
            .setMessage("Vols pujar les ${listaAPujar.size} UEs locals al servidor? Aquesta operació pot trigar una estona.")
            .setNegativeButton("Cancel·lar", null)
            .setPositiveButton("Pujar tot") { _, _ ->
                subirTodasLasUEs(listaAPujar)
            }
            .show()
    }

    private fun subirTodasLasUEs(listaAPujar: List<ObjecteUE>) {
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
                for (objecte in listaAPujar) {
                    val publicUrls = mutableListOf<String>()
                    var hasError = false

                    // 1. Subida a S3
                    objecte.imatges_urls.forEach { uriString ->
                        try {
                            val publicUrl = S3Service.uploadImage(requireContext(), Uri.parse(uriString))
                            if (publicUrl != null) publicUrls.add(publicUrl) else hasError = true
                        } catch (e: Exception) {
                            hasError = true
                        }
                    }

                    if (hasError && publicUrls.isEmpty() && objecte.imatges_urls.isNotEmpty()) {
                        fallidas++
                        continue
                    }

                    // 2. Firestore
                    val finalObjecte = objecte.copy(imatges_urls = publicUrls, sincronitzat = true)
                    val db = FirebaseFirestore.getInstance()
                    val docId = "${finalObjecte.jaciment}_${finalObjecte.codi_ue}".replace("/", "_")

                    try {
                        db.collection("unitats_estratigrafiques")
                            .document(docId)
                            .set(finalObjecte)
                            .await() // Espera real a la respuesta del servidor

                        // 3. Borrado local solo tras el éxito
                        DataManager.deleteUE(requireContext(), objecte.codi_ue, objecte.jaciment)
                        exitosas++
                        Log.d("UE_UPLOAD", "✔ Sincronitzada: ${objecte.codi_ue}")
                    } catch (e: Exception) {
                        Log.e("UE_UPLOAD", "❌ Error subiendo ${objecte.codi_ue}: ${e.message}")
                        fallidas++
                    }
                }

                progressDialog.dismiss()
                Toast.makeText(requireContext(), "Pujada finalitzada: $exitosas exitoses, $fallidas fallides.", Toast.LENGTH_LONG).show()

                // REFRESCAR Y RE-FILTRAR (Crucial para la seguridad)
                originalList = DataManager.getUEListLocal(requireContext())
                aplicarFiltroPorRol()

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
        if (rolYaCargado) {
            originalList = DataManager.getUEListLocal(requireContext())
            aplicarFiltroPorRol()
        } else {
            cargarYFiltrarPorRol()
        }
    }

    private fun aplicarFiltroPorRol() {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
        val listaFiltrada = if (userRol == "director") {
            originalList
        } else {
            // Filtro estricto por usuario activo
            originalList.filter { it.registrat_per.trim().equals(currentUserEmail.trim(), ignoreCase = true) }
        }
        updateUI(listaFiltrada)
    }

    private fun cargarYFiltrarPorRol() {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
        originalList = DataManager.getUEListLocal(requireContext())

        recyclerView.visibility = View.INVISIBLE
        tvEmptyMessage.visibility = View.GONE

        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid

        if (uid != null) {
            db.collection("usuaris")
                .whereEqualTo("email", currentUserEmail)
                .get()
                .addOnSuccessListener { documents ->
                    userRol = if (!documents.isEmpty) {
                        documents.first().getString("rol")?.lowercase() ?: "tecnic"
                    } else "tecnic"

                    rolYaCargado = true
                    aplicarFiltroPorRol()
                    recyclerView.visibility = View.VISIBLE
                }
                .addOnFailureListener {
                    userRol = "tecnic"
                    rolYaCargado = true
                    aplicarFiltroPorRol()
                    recyclerView.visibility = View.VISIBLE
                }
        } else {
            recyclerView.visibility = View.VISIBLE
            updateUI(emptyList())
        }
    }

    fun applyFilters(jaciment: String, sector: String, ue: String, tipus: String, onlyMine: Boolean = false) {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
        val forcingOnlyMine = if (userRol != "director") true else onlyMine

        val filteredList = originalList.filter { item ->
            val matchJaciment = jaciment.isEmpty() || item.jaciment == jaciment
            val matchSector = sector.isEmpty() || item.codi_sector.equals(sector, ignoreCase = true)
            val matchUE = ue.isEmpty() || item.codi_ue.contains(ue, ignoreCase = true)
            val matchTipus = tipus.isEmpty() || item.tipus_ue == tipus
            val matchOnlyMine = !forcingOnlyMine || item.registrat_per.trim().equals(currentUserEmail.trim(), ignoreCase = true)

            matchJaciment && matchSector && matchUE && matchTipus && matchOnlyMine
        }

        updateUI(filteredList, isFilter = true)
    }

    private fun updateUI(list: List<ObjecteUE>, isFilter: Boolean = false) {
        activity?.runOnUiThread {
            if (list.isEmpty()) {
                recyclerView.visibility = View.GONE
                tvEmptyMessage.visibility = View.VISIBLE
                tvEmptyMessage.setText(if (isFilter) R.string.text_filtre_error else R.string.text_ue_local_list_empty)
            } else {
                recyclerView.visibility = View.VISIBLE
                tvEmptyMessage.visibility = View.GONE
                adapter.updateList(list)
            }
        }
    }
}
