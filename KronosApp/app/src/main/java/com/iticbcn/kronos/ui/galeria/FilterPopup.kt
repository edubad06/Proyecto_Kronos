package com.iticbcn.kronos.ui.galeria

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import com.iticbcn.kronos.R
import com.iticbcn.kronos.data.local.DataManager
import com.iticbcn.kronos.domain.model.TipusUEOptions
import com.google.android.material.textfield.TextInputEditText

class FilterPopup(
    context: Context,
    private val onApply: (jaciment: String, sector: String, ueId: String, tipus: String) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.popup_filter)

        val actvJaciment = findViewById<AutoCompleteTextView>(R.id.actv_popup_jaciment)
        val actvSector = findViewById<AutoCompleteTextView>(R.id.actv_popup_sector)
        val etUe = findViewById<TextInputEditText>(R.id.et_popup_ue)
        val actvTipus = findViewById<AutoCompleteTextView>(R.id.actv_popup_tipus_ue)
        val btnApply = findViewById<Button>(R.id.btn_apply_filter)

        // Configuración de Jaciment
        val jaciments = DataManager.getJaciments(context)
        val adapterJaciment = ArrayAdapter(context, android.R.layout.simple_list_item_1, jaciments)
        actvJaciment?.setAdapter(adapterJaciment)

        // Configuración de Tipus UE
        val tipusOptions = TipusUEOptions.getNames()
        val adapterTipus = ArrayAdapter(context, android.R.layout.simple_list_item_1, tipusOptions)
        actvTipus?.setAdapter(adapterTipus)

        // Listener para cargar sectores según el yacimiento seleccionado
        actvJaciment?.setOnItemClickListener { parent, _, position, _ ->
            val selectedJaciment = parent.getItemAtPosition(position).toString()
            val sectors = DataManager.getSectorsByJaciment(context, selectedJaciment)
            val adapterSector = ArrayAdapter(context, android.R.layout.simple_list_item_1, sectors)
            actvSector?.setAdapter(adapterSector)
            actvSector?.setText("", false)
        }

        btnApply?.setOnClickListener {
            onApply(
                actvJaciment?.text.toString(),
                actvSector?.text.toString(),
                etUe?.text.toString(),
                actvTipus?.text.toString()
            )
            dismiss()
        }
    }
}
