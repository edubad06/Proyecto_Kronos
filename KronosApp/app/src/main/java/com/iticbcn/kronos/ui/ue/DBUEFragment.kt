package com.iticbcn.kronos.ui.ue

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view. View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iticbcn.kronos.ui.adapter.ObjecteAdapter
import com.iticbcn.kronos.data.local.db.entities.ObjecteUE
import com.iticbcn.kronos.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DBUEFragment : Fragment() {

    private lateinit var adapter: ObjecteAdapter
    private var originalList: List<ObjecteUE> = emptyList()

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyMessage: TextView
    private lateinit var tvCacheWarning: TextView
    private var userRole: String = "tecnic"
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_ue_db, container, false)

        recyclerView = view.findViewById(R.id.rvObjectes)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)
        tvCacheWarning = view.findViewById(R.id.tvCacheWarning)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        // Cargar caché inicial silenciosamente (sin mostrar el aviso aún)
        originalList = DataManager.getUEListDB(requireContext())
        
        // ✅ CORRECCIÓN: Usar UID para consistencia en la lógica de permisos del Adapter
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        adapter = ObjecteAdapter(originalList, isDatabaseSource = true, currentUserEmail = currentUid)
        recyclerView.adapter = adapter

        if (originalList.isEmpty()) {
            tvEmptyMessage.visibility = View.VISIBLE
            tvEmptyMessage.setText(R.string.text_ue_external_list_empty)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        cargarDatosDesdeFirestore()
    }

    private fun cargarDatosDesdeFirestore() {
        val currentUser = FirebaseAuth.getInstance().currentUser
        val email = currentUser?.email?.trim()

        if (email == null) {
            updateUI(originalList)
            return
        }

        val db = FirebaseFirestore.getInstance()
        
        db.collection("usuaris")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { userDocs ->
                if (!userDocs.isEmpty) {
                    val userData = userDocs.documents[0]
                    userRole = userData.getString("rol") ?: "tecnic"
                    adapter.setUserRole(userRole)

                    val excavacions = when (val value = userData.get("excavacio")) {
                        is List<*> -> value.filterIsInstance<String>()
                        is String -> listOf(value)
                        else -> emptyList()
                    }

                    if (excavacions.isNotEmpty()) {
                        db.collection("unitats_estratigrafiques")
                            .whereIn("jaciment", excavacions)
                            .get()
                            .addOnSuccessListener { ueDocs ->
                                val listUE = ueDocs.toObjects(ObjecteUE::class.java)
                                originalList = listUE
                                DataManager.saveUEListDB(requireContext(), originalList)
                                
                                // El aviso solo aparece si los datos vienen de la caché de Firestore (ej. offline)
                                if (ueDocs.metadata.isFromCache) {
                                    tvCacheWarning.visibility = View.VISIBLE
                                } else {
                                    tvCacheWarning.visibility = View.GONE
                                }
                                
                                updateUI(originalList)
                            }
                            .addOnFailureListener { e ->
                                Log.e("FirestoreDB", "Error cargando UEs, usando caché", e)
                                tvCacheWarning.visibility = View.VISIBLE
                                updateUI(originalList)
                            }
                    } else {
                        tvCacheWarning.visibility = View.GONE
                        updateUI(emptyList())
                    }
                } else {
                    tvCacheWarning.visibility = View.GONE
                    updateUI(emptyList())
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreDB", "Error consultando usuario, usando caché", e)
                tvCacheWarning.visibility = View.VISIBLE
                updateUI(originalList)
            }
    }

    fun applyFilters(jaciment: String, sector: String, ue: String, tipus: String, onlyMine: Boolean = false) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid ?: ""
        val filteredList = originalList.filter { item ->
            val matchJaciment = jaciment.isEmpty() || item.jaciment == jaciment
            val matchSector = sector.isEmpty() || item.codi_sector == sector
            val matchUE = ue.isEmpty() || item.codi_ue.contains(ue, ignoreCase = true)
            val matchTipus = tipus.isEmpty() || item.tipus_ue == tipus
            
            // ✅ CORRECCIÓN: Filtrar por UID (registrat_per) en lugar de Email para consistencia
            val matchOnlyMine = !onlyMine || item.registrat_per == currentUid

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
                tvEmptyMessage.setText(R.string.text_ue_external_list_empty)
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            tvEmptyMessage.visibility = View.GONE
            adapter.updateList(list)
        }
    }
}
