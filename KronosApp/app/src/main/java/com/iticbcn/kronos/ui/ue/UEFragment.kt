package com.iticbcn.kronos.ui.ue

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iticbcn.kronos.data.local.DataManager
import com.iticbcn.kronos.ui.adapter.ObjecteAdapter
import com.iticbcn.kronos.domain.model.ObjecteUE
import com.iticbcn.kronos.R
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.iticbcn.kronos.ui.formulario.FormularioUE

class UEFragment : Fragment() {

    private lateinit var adapter: ObjecteAdapter
    private var originalList: List<ObjecteUE> = emptyList()

    private lateinit var recyclerView: RecyclerView
    private lateinit var tvEmptyMessage: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_ue_local, container, false)

        recyclerView = view.findViewById(R.id.rvObjectes)
        tvEmptyMessage = view.findViewById(R.id.tvEmptyMessage)
        val fabAdd: FloatingActionButton = view.findViewById(R.id.fab_add_ue)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())

        originalList = DataManager.getUEListLocal(requireContext())

        adapter = ObjecteAdapter(originalList.toMutableList())
        recyclerView.adapter = adapter

        // Listener para el botón flotante de añadir UE
        fabAdd.setOnClickListener {
            val intent = Intent(requireContext(), FormularioUE::class.java)
            startActivity(intent)
        }

        return view
    }

    override fun onResume() {
        super.onResume()
        originalList = DataManager.getUEListLocal(requireContext())
        updateUI(originalList)
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
                tvEmptyMessage.setText(R.string.text_ue_local_list_empty)
            }
        } else {
            recyclerView.visibility = View.VISIBLE
            tvEmptyMessage.visibility = View.GONE
            adapter.updateList(list)
        }
    }
}