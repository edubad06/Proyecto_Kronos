package com.iticbcn.kronos.ui.ue

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iticbcn.kronos.ui.adapter.ObjecteAdapter
import com.iticbcn.kronos.domain.model.ObjecteUE
import com.iticbcn.kronos.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class DBUEFragment : Fragment() {

    private lateinit var adapter: ObjecteAdapter
    private var originalList: List<ObjecteUE> = emptyList()

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyMessage: TextView
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_ue_db, container, false)

        recyclerView = view.findViewById(R.id.rvObjectes)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        adapter = ObjecteAdapter(emptyList(), isDatabaseSource = true)
        recyclerView.adapter = adapter

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
            updateUI(emptyList())
            return
        }

        val db = FirebaseFirestore.getInstance()
        
        db.collection("usuaris")
            .whereEqualTo("email", email)
            .get()
            .addOnSuccessListener { userDocs ->
                if (!userDocs.isEmpty) {
                    val userData = userDocs.documents[0]
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
                                updateUI(originalList)
                            }
                            .addOnFailureListener { e ->
                                Log.e("FirestoreDB", "Error cargando UEs", e)
                                updateUI(emptyList())
                            }
                    } else {
                        updateUI(emptyList())
                    }
                } else {
                    updateUI(emptyList())
                }
            }
            .addOnFailureListener { e ->
                Log.e("FirestoreDB", "Error consultando usuario", e)
                updateUI(emptyList())
            }
    }

    fun applyFilters(jaciment: String, sector: String, ue: String, tipus: String) {
        val filteredList = originalList.filter { item ->
            val matchJaciment = jaciment.isEmpty() || item.jaciment == jaciment
            val matchSector = sector.isEmpty() || item.codi_sector == sector
            val matchUE = ue.isEmpty() || item.codi_ue.contains(ue, ignoreCase = true)
            val matchTipus = tipus.isEmpty() || item.tipus_ue == tipus

            matchJaciment && matchSector && matchUE && matchTipus
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