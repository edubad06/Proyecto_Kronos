package com.iticbcn.kronos.ui.formulario

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iticbcn.kronos.domain.model.ObjecteUE
import com.iticbcn.kronos.domain.model.RelacioUE
import com.iticbcn.kronos.R
import com.iticbcn.kronos.ui.adapter.FotoAdapter
import com.iticbcn.kronos.data.local.DataManager
import com.iticbcn.kronos.domain.model.TipusUEOptions
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import java.util.ArrayList

class FormularioUE : AppCompatActivity() {

    private lateinit var fotoAdapter: FotoAdapter
    private val selectedUris = mutableListOf<Uri>()
    private var objetoAEditar: ObjecteUE? = null

    private val getContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            selectedUris.add(it)
            fotoAdapter.notifyItemInserted(selectedUris.size - 1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_formulario_ue)

        // 1. VINCULACIÓN DE VISTAS
        val btnAddImage: Button = findViewById(R.id.button_add_image)
        val btnSave: Button = findViewById(R.id.button_save)
        val tilUe: TextInputLayout = findViewById(R.id.til_ue)
        val etUe: TextInputEditText = findViewById(R.id.et_ue)
        val actvJaciment = findViewById<AutoCompleteTextView>(R.id.actv_jaciment)
        val actvSector = findViewById<AutoCompleteTextView>(R.id.actv_sector)
        val tilSector = findViewById<TextInputLayout>(R.id.til_sector)
        val rvFotos = findViewById<RecyclerView>(R.id.rv_fotos_formulario)
        
        val callback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                mostrarDialogoConfirmacion()
            }
        }
        onBackPressedDispatcher.addCallback(this, callback)

        // 2. CONFIGURACIÓN RECYCLERVIEW
        rvFotos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        fotoAdapter = FotoAdapter(selectedUris)
        rvFotos.adapter = fotoAdapter

        // 3. INICIALIZACIÓN DE DESPLEGABLES
        setupDropdownManual(actvJaciment, DataManager.getJaciments(this))
        setupDropdownManual(findViewById(R.id.actv_tipus_ue), TipusUEOptions.getNames())
        setupDropdownManual(actvSector, emptyList())

        // 4. CARGA DE DATOS (Persistencia tras rotación o Edición)
        if (savedInstanceState == null) {
            objetoAEditar = intent.getSerializableExtra("EXTRA_OBJETO") as? ObjecteUE
            objetoAEditar?.let { prellenarFormulario(it) }
        } else {
            objetoAEditar = savedInstanceState.getSerializable("OBJETO_EDITAR") as? ObjecteUE
            val uriStrings = savedInstanceState.getStringArrayList("SELECTED_URIS")
            selectedUris.clear()
            uriStrings?.forEach { selectedUris.add(Uri.parse(it)) }
            fotoAdapter.notifyDataSetChanged()
        }

        // 5. LISTENERS
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        actvJaciment.setOnItemClickListener { parent, _, position, _ ->
            val jacimentSeleccionado = parent.getItemAtPosition(position).toString()
            tilSector.isEnabled = true
            actvSector.isEnabled = true
            actvSector.text = null

            val sectoresFiltrados = DataManager.getSectorsByJaciment(this, jacimentSeleccionado)
            val adapterSectores = ArrayAdapter(this, android.R.layout.simple_list_item_1, sectoresFiltrados)
            actvSector.setAdapter(adapterSectores)
        }

        btnAddImage.setOnClickListener {
            getContent.launch(arrayOf("image/*"))
        }

        btnSave.setOnClickListener {
            guardarFormulario(etUe, tilUe)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        val uriStrings = ArrayList(selectedUris.map { it.toString() })
        outState.putStringArrayList("SELECTED_URIS", uriStrings)
        outState.putSerializable("OBJETO_EDITAR", objetoAEditar)
    }

    private fun actualizarSectores(jaciment: String, actvSector: AutoCompleteTextView) {
        val sectores = DataManager.getSectorsByJaciment(this, jaciment)
        val adapterSectores = ArrayAdapter(this, android.R.layout.simple_list_item_1, sectores)
        actvSector.setAdapter(adapterSectores)
    }

    private fun setupDropdownManual(autoComplete: AutoCompleteTextView, items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        autoComplete.setAdapter(adapter)
        autoComplete.inputType = android.text.InputType.TYPE_NULL
    }

    private fun prellenarFormulario(obj: ObjecteUE) {
        findViewById<TextInputEditText>(R.id.et_ue).setText(obj.codi_ue)
        findViewById<TextInputEditText>(R.id.et_cronologia).setText(obj.cronologia)
        findViewById<TextInputEditText>(R.id.et_descripcio).setText(obj.descripcio)

        findViewById<TextInputEditText>(R.id.Textura).setText(obj.textura)
        findViewById<TextInputEditText>(R.id.et_color).setText(obj.color)
        findViewById<TextInputEditText>(R.id.et_material).setText(obj.material)
        findViewById<TextInputEditText>(R.id.et_estat_conservacio)?.setText(obj.estat_conservacio)

        findViewById<AutoCompleteTextView>(R.id.actv_jaciment).setText(obj.jaciment, false)

        val actvSector = findViewById<AutoCompleteTextView>(R.id.actv_sector)
        val tilSector = findViewById<TextInputLayout>(R.id.til_sector)
        tilSector.isEnabled = true
        actvSector.isEnabled = true
        actualizarSectores(obj.jaciment, actvSector)
        actvSector.setText(obj.codi_sector, false)

        findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue).setText(obj.tipus_ue, false)

        // Mapeo inverso de relaciones
        obj.relacions.forEach { rel ->
            when (rel.tipus) {
                "igual" -> findViewById<TextInputEditText>(R.id.et_igual_a)?.setText(rel.desti)
                "se_li_lliura" -> findViewById<TextInputEditText>(R.id.et_se_li_lliura)?.setText(rel.desti)
                "cobert" -> findViewById<TextInputEditText>(R.id.et_cobert_per)?.setText(rel.desti)
                "tallat" -> findViewById<TextInputEditText>(R.id.et_tallat_per)?.setText(rel.desti)
                "reblert" -> findViewById<TextInputEditText>(R.id.et_reblert_per)?.setText(rel.desti)
                "lliura" -> findViewById<TextInputEditText>(R.id.et_es_lliura_a)?.setText(rel.desti)
                "cobreix" -> findViewById<TextInputEditText>(R.id.et_cobreix_a)?.setText(rel.desti)
                "talla" -> findViewById<TextInputEditText>(R.id.et_talla_a)?.setText(rel.desti)
                "rebleix" -> findViewById<TextInputEditText>(R.id.et_rebleix_a)?.setText(rel.desti)
            }
        }

        selectedUris.clear()
        obj.imatges_urls.let { listaDeImagenes ->
            for (uriString in listaDeImagenes) {
                try {
                    selectedUris.add(Uri.parse(uriString))
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        fotoAdapter.notifyDataSetChanged()
    }

    private fun mostrarDialogoConfirmacion() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Descartar canvis?")
            .setMessage("Si surts ara, perdràs tota la informació que hagis introduït en aquesta fitxa.")
            .setNeutralButton("Continuar editant") { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton("Sortir sense desar") { _, _ ->
                finish()
            }
            .show()
    }

    private fun guardarFormulario(etUe: TextInputEditText, tilUe: TextInputLayout) {
        val ueText = etUe.text.toString()
        val jacimentText = findViewById<AutoCompleteTextView>(R.id.actv_jaciment).text.toString()

        if (ueText.isBlank()) {
            tilUe.error = "Aquest camp és obligatori"
            return
        }

        val haCambiatIdOJaciment = objetoAEditar?.let { it.codi_ue != ueText || it.jaciment != jacimentText } ?: true

        if (haCambiatIdOJaciment && DataManager.existsUE(this, ueText, jacimentText)) {
            tilUe.error = "Ja existeix la UE $ueText en aquest yaciment"
            return
        } else {
            tilUe.error = null
        }

        val relacionsList = mutableListOf<RelacioUE>()
        findViewById<TextInputEditText>(R.id.et_igual_a)?.text?.toString()?.takeIf { it.isNotEmpty() }?.let { relacionsList.add(RelacioUE("igual", it)) }
        findViewById<TextInputEditText>(R.id.et_se_li_lliura)?.text?.toString()?.takeIf { it.isNotEmpty() }?.let { relacionsList.add(RelacioUE("se_li_lliura", it)) }
        findViewById<TextInputEditText>(R.id.et_cobert_per)?.text?.toString()?.takeIf { it.isNotEmpty() }?.let { relacionsList.add(RelacioUE("cobert", it)) }
        findViewById<TextInputEditText>(R.id.et_tallat_per)?.text?.toString()?.takeIf { it.isNotEmpty() }?.let { relacionsList.add(RelacioUE("tallat", it)) }
        findViewById<TextInputEditText>(R.id.et_reblert_per)?.text?.toString()?.takeIf { it.isNotEmpty() }?.let { relacionsList.add(RelacioUE("reblert", it)) }
        findViewById<TextInputEditText>(R.id.et_es_lliura_a)?.text?.toString()?.takeIf { it.isNotEmpty() }?.let { relacionsList.add(RelacioUE("lliura", it)) }
        findViewById<TextInputEditText>(R.id.et_cobreix_a)?.text?.toString()?.takeIf { it.isNotEmpty() }?.let { relacionsList.add(RelacioUE("cobreix", it)) }
        findViewById<TextInputEditText>(R.id.et_talla_a)?.text?.toString()?.takeIf { it.isNotEmpty() }?.let { relacionsList.add(RelacioUE("talla", it)) }
        findViewById<TextInputEditText>(R.id.et_rebleix_a)?.text?.toString()?.takeIf { it.isNotEmpty() }?.let { relacionsList.add(RelacioUE("rebleix", it)) }

        val email = FirebaseAuth.getInstance().currentUser?.email ?: objetoAEditar?.registrat_per ?: ""

        val nouObjecte = ObjecteUE(
            codi_ue = ueText,
            codi_sector = findViewById<AutoCompleteTextView>(R.id.actv_sector).text.toString(),
            tipus_ue = findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue).text.toString(),
            descripcio = findViewById<TextInputEditText>(R.id.et_descripcio).text.toString(),
            registrat_per = email,
            material = findViewById<TextInputEditText>(R.id.et_material).text.toString(),
            estat_conservacio = findViewById<TextInputEditText>(R.id.et_estat_conservacio)?.text.toString() ?: "",
            cronologia = findViewById<TextInputEditText>(R.id.et_cronologia).text.toString(),
            textura = findViewById<TextInputEditText>(R.id.Textura).text.toString(),
            color = findViewById<TextInputEditText>(R.id.et_color).text.toString(),
            relacions = relacionsList,
            imatges_urls = selectedUris.map { it.toString() },
            sincronitzat = false,
            fecha_creacio = objetoAEditar?.fecha_creacio ?: System.currentTimeMillis(),
            jaciment = jacimentText
        )

        if (objetoAEditar != null && haCambiatIdOJaciment) {
            DataManager.deleteUE(this, objetoAEditar!!.codi_ue, objetoAEditar!!.jaciment)
        }

        DataManager.saveUE(this, nouObjecte)
        finish()
    }
}
