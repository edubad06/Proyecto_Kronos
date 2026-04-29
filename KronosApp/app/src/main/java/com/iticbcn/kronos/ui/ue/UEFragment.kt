package com.iticbcn.kronos.ui.ue

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Log.e
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
    private var userRol: String = "" // Guardamos el rol para usarlo en los filtros
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

        adapter = ObjecteAdapter(originalList.toMutableList())
        recyclerView.adapter = adapter

        fabAdd.setOnClickListener {
            val intent = Intent(requireContext(), FormularioUE::class.java)
            startActivity(intent)
        }

        fabUploadAll.setOnClickListener {
            val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""

            Log.d("UE_DEBUG", "===== CLICK PUJADA MASIVA =====")
            Log.d("UE_DEBUG", "Rol usuario: '$userRol'")
            Log.d("UE_DEBUG", "Email usuario: '$currentUserEmail'")
            Log.d("UE_DEBUG", "Total UEs en local: ${originalList.size}")

            originalList.forEach { ue ->
                Log.d("UE_DEBUG", "UE -> codi: ${ue.codi_ue} | registrat_per: '${ue.registrat_per}'")
            }

            val listaAPujar = if (userRol == "director") {
                Log.d("UE_DEBUG", "Usuario es DIRECTOR → sube TODO")
                originalList
            } else {
                Log.d("UE_DEBUG", "Usuario es TECNIC → filtrando por email...")

                val filtradas = originalList.filter { ue ->
                    val registrat = ue.registrat_per
                    val email = currentUserEmail

                    val matchExacto = registrat == email
                    val matchTrim = registrat.trim() == email.trim()
                    val matchIgnoreCase = registrat.equals(email, ignoreCase = true)

                    Log.d("UE_DEBUG", """
        UE: ${ue.codi_ue}
        registrat_per: '$registrat'
        email actual: '$email'
        matchExacto: $matchExacto
        matchTrim: $matchTrim
        matchIgnoreCase: $matchIgnoreCase
    """.trimIndent())

                    matchExacto
                }

                filtradas
            }

            Log.d("UE_DEBUG", "Resultado filtro: ${listaAPujar.size} UEs válidas")

            if (listaAPujar.isEmpty()) {
                Log.w("UE_DEBUG", "⚠️ NO HAY UEs PARA SUBIR")
                Toast.makeText(requireContext(), "No hi ha UEs per pujar", Toast.LENGTH_SHORT).show()
            } else {
                listaAPujar.forEach {
                    Log.d("UE_DEBUG", "✔ Se subirá UE: ${it.codi_ue}")
                }
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

                    objecte.imatges_urls.forEach { uriString ->
                        val publicUrl = S3Service.uploadImage(requireContext(), Uri.parse(uriString))
                        if (publicUrl != null) publicUrls.add(publicUrl) else hasError = true
                    }

                    if (hasError && publicUrls.isEmpty() && objecte.imatges_urls.isNotEmpty()) {
                        fallidas++
                        continue
                    }

                    val finalObjecte = objecte.copy(imatges_urls = publicUrls, sincronitzat = true)
                    val db = FirebaseFirestore.getInstance()
                    val docId = "${finalObjecte.jaciment}_${finalObjecte.codi_ue}".replace("/", "_")

                    try {
                        db.collection("unitats_estratigrafiques")
                            .document(docId)
                            .set(finalObjecte)
                            .await() // 🔥 IMPORTANTE

                        Log.d("UE_UPLOAD", "✔ Subida correcta: ${finalObjecte.codi_ue}")

                        DataManager.deleteUE(requireContext(), objecte.codi_ue, objecte.jaciment)
                        exitosas++

                    } catch (e: Exception) {
                        Log.e("UE_UPLOAD", "❌ Error subiendo UE ${finalObjecte.codi_ue}: ${e.message}")
                        e.printStackTrace()
                        fallidas++
                    }
                }

                progressDialog.dismiss()
                Toast.makeText(requireContext(), "Pujada finalitzada: $exitosas exitoses, $fallidas fallides.", Toast.LENGTH_LONG).show()

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
        if (rolYaCargado) {
            // Si ya tenemos el rol, recargamos la lista sin consultar Firestore
            originalList = DataManager.getUEListLocal(requireContext())
            val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""
            val listaInicial = if (userRol == "director") originalList
            else originalList.filter { it.registrat_per == currentUserEmail }
            updateUI(listaInicial)
        } else {
            cargarYFiltrarPorRol()
        }
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

                    val listaInicial = if (userRol == "director") originalList
                    else originalList.filter { it.registrat_per == currentUserEmail }

                    recyclerView.visibility = View.VISIBLE
                    updateUI(listaInicial)
                }
                .addOnFailureListener {
                    userRol = "tecnic"
                    rolYaCargado = true
                    recyclerView.visibility = View.VISIBLE
                    updateUI(originalList.filter { it.registrat_per == currentUserEmail })
                }
        } else {
            recyclerView.visibility = View.VISIBLE
            updateUI(emptyList())
        }
    }

    fun applyFilters(jaciment: String, sector: String, ue: String, tipus: String, onlyMine: Boolean = false) {
        val currentUserEmail = FirebaseAuth.getInstance().currentUser?.email ?: ""

        // REGLA DE ORO: Si no es director, forzamos onlyMine a true internamente
        val forcingOnlyMine = if (userRol != "director") true else onlyMine

        val filteredList = originalList.filter { item ->
            val matchJaciment = jaciment.isEmpty() || item.jaciment == jaciment
            // Filtro de sector: comprobamos si está vacío o si coincide exactamente
            val matchSector = sector.isEmpty() || item.codi_sector.equals(sector, ignoreCase = true)
            val matchUE = ue.isEmpty() || item.codi_ue.contains(ue, ignoreCase = true)
            val matchTipus = tipus.isEmpty() || item.tipus_ue == tipus
            val matchOnlyMine = !forcingOnlyMine || item.registrat_per == currentUserEmail

            matchJaciment && matchSector && matchUE && matchTipus && matchOnlyMine
        }

        updateUI(filteredList, isFilter = true)
    }


    private fun updateUI(list: List<ObjecteUE>, isFilter: Boolean = false) {
        // Aseguramos que el cambio de UI ocurra en el hilo principal
        activity?.runOnUiThread {
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
}
