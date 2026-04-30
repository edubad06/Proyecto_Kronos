package com.iticbcn.kronos.ui.galeria

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import com.iticbcn.kronos.R
import com.iticbcn.kronos.data.local.DataManager
import com.iticbcn.kronos.domain.model.TipusUEOptions
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.switchmaterial.SwitchMaterial
import androidx.core.graphics.drawable.toDrawable


class FilterPopup(
    context: Context,
    private val userJaciment: String,
    private val showOnlyMineSwitch: Boolean = true,
    private val onApply: (sector: String, ueId: String, tipus: String, onlyMine: Boolean) -> Unit
) : Dialog(context) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(R.layout.popup_filter)

        // ✅ Elimina el fondo blanco con esquinas detrás de la card
        window?.setBackgroundDrawable(android.graphics.Color.TRANSPARENT.toDrawable())

        val actvSector = findViewById<AutoCompleteTextView>(R.id.actv_popup_sector)
        val etUe = findViewById<TextInputEditText>(R.id.et_popup_ue)
        val actvTipus = findViewById<AutoCompleteTextView>(R.id.actv_popup_tipus_ue)
        val swMyUEs = findViewById<SwitchMaterial>(R.id.sw_my_UEs)
        val btnApply = findViewById<Button>(R.id.btn_apply_filter)

        // Mostrar u ocultar el switch según la pestaña activa
        swMyUEs?.visibility = if (showOnlyMineSwitch) View.VISIBLE else View.GONE

        // Configuración de Sectores filtrados por el Jaciment del usuario
        val sectors = if (userJaciment.isNotEmpty()) {
            DataManager.getSectorsByJaciment(context, userJaciment)
        } else {
            DataManager.getSectors(context)
        }

        val adapterSector = ArrayAdapter(context, android.R.layout.simple_list_item_1, sectors)
        actvSector?.setAdapter(adapterSector)

        // Configuración de Tipus UE
        val tipusOptions = TipusUEOptions.getNames()
        val adapterTipus = ArrayAdapter(context, android.R.layout.simple_list_item_1, tipusOptions)
        actvTipus?.setAdapter(adapterTipus)

        btnApply?.setOnClickListener {
            onApply(
                actvSector?.text.toString(),
                etUe?.text.toString(),
                actvTipus?.text.toString(),
                if (showOnlyMineSwitch) (swMyUEs?.isChecked ?: false) else false
            )
            dismiss()
        }
    }
}
