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
        
        // Usamos UID para consistencia con FormularioUE y Web
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        adapter = ObjecteAdapter(mutableListOf(), false, currentUid)
        recyclerView.adapter = adapter

        fabAdd.setOnClickListener {
            startActivity(Intent(requireContext(), FormularioUE::class.java))
        }

        fabUploadAll.setOnClickListener { iniciarProcesoSubidaMasiva() }

        return view
    }

    private fun aplicarFiltroPorRol() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val listaFiltrada = if (userRol == "director") {
            originalList
        } else {
            // ✅ CORRECCIÓN: Filtrar por UID, no por Email
            originalList.filter { it.registrat_per == currentUid }
        }
        updateUI(listaFiltrada)
    }

    private fun cargarYFiltrarPorRol() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        originalList = DataManager.getUEListLocal(requireContext())
        
        if (currentUser != null) {
            FirebaseFirestore.getInstance().collection("usuaris")
                .whereEqualTo("email", currentUser.email).get()
                .addOnSuccessListener { documents ->
                    userRol = documents.firstOrNull()?.getString("rol")?.lowercase() ?: "tecnic"
                    rolYaCargado = true
                    aplicarFiltroPorRol()
                }
                .addOnFailureListener {
                    userRol = "tecnic"
                    rolYaCargado = true
                    aplicarFiltroPorRol()
                }
        } else {
            updateUI(emptyList())
        }
    }

    override fun onResume() {
        super.onResume()
        cargarYFiltrarPorRol()
    }

    fun applyFilters(jaciment: String, sector: String, ue: String, tipus: String, onlyMine: Boolean = false) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val filteredList = originalList.filter { item ->
            val matchJaciment = jaciment.isEmpty() || item.jaciment == jaciment
            val matchSector = sector.isEmpty() || item.codi_sector == sector
            val matchUE = ue.isEmpty() || item.codi_ue.contains(ue, ignoreCase = true)
            val matchTipus = tipus.isEmpty() || item.tipus_ue == tipus
            val matchOnlyMine = !onlyMine || item.registrat_per == currentUid

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

    private fun iniciarProcesoSubidaMasiva() {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val listaAPujar = if (userRol == "director") originalList else originalList.filter { it.registrat_per == currentUid }

        if (listaAPujar.isEmpty()) {
            Toast.makeText(requireContext(), "No hi ha UEs per pujar", Toast.LENGTH_SHORT).show()
        } else {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Pujada massiva")
                .setMessage("Vols pujar les ${listaAPujar.size} UEs locals?")
                .setNegativeButton("Cancel·lar", null)
                .setPositiveButton("Pujar tot") { _, _ -> subirTodasLasUEs(listaAPujar) }
                .show()
        }
    }

    private fun subirTodasLasUEs(listaAPujar: List<ObjecteUE>) {
        uploadJob = CoroutineScope(Dispatchers.Main).launch {
            val progress = MaterialAlertDialogBuilder(requireContext()).setTitle("Pujant...").setCancelable(false).show()
            try {
                val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                for (obj in listaAPujar) {
                    val urls = mutableListOf<String>()
                    for (uri in obj.imatges_urls) {
                        S3Service.uploadImage(requireContext(), Uri.parse(uri), token)?.let { urls.add(it) }
                    }
                    val finalObj = obj.copy(imatges_urls = urls, sincronitzat = true)
                    val docId = "${finalObj.jaciment}_${finalObj.codi_ue}".replace("/", "_")
                    FirebaseFirestore.getInstance().collection("unitats_estratigrafiques").document(docId).set(finalObj).await()
                    DataManager.deleteUE(requireContext(), obj.codi_ue, obj.jaciment)
                }
                progress.dismiss()
                cargarYFiltrarPorRol()
            } catch (e: Exception) {
                progress.dismiss()
                Toast.makeText(requireContext(), "Error en la pujada", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
