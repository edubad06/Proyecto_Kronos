package com.iticbcn.kronos.ui.formulario

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.Toast
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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.iticbcn.kronos.data.remote.S3Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.ArrayList

class FormularioUE : AppCompatActivity() {

    private lateinit var fotoAdapter: FotoAdapter
    private val selectedUris = mutableListOf<Uri>()
    private var objetoAEditar: ObjecteUE? = null
    private var isReadOnly: Boolean = false
    private var isFromDatabase: Boolean = false

    private val getContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (e: Exception) { }
            selectedUris.add(it)
            fotoAdapter.notifyItemInserted(selectedUris.size - 1)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_formulario_ue)

        isReadOnly = intent.getBooleanExtra("EXTRA_READ_ONLY", false)
        isFromDatabase = intent.getBooleanExtra("EXTRA_IS_DB", false)

        val btnAddImage: Button = findViewById(R.id.button_add_image)
        val btnSave: Button = findViewById(R.id.button_save)
        val etUe: TextInputEditText = findViewById(R.id.et_ue)
        val etJaciment = findViewById<TextInputEditText>(R.id.et_jaciment)
        val actvSector = findViewById<AutoCompleteTextView>(R.id.actv_sector)
        val tilSector = findViewById<TextInputLayout>(R.id.til_sector)
        val actvTipus = findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue)
        val rvFotos = findViewById<RecyclerView>(R.id.rv_fotos_formulario)
        
        onBackPressedDispatcher.addCallback(this, object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { 
                if (isReadOnly) finish() else mostrarDialogoConfirmacion() 
            }
        })

        rvFotos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        fotoAdapter = FotoAdapter(selectedUris, isReadOnly)
        rvFotos.adapter = fotoAdapter

        // Configuración inicial de adaptadores
        setupDropdownManual(actvTipus, TipusUEOptions.getNames())

        if (savedInstanceState == null) {
            objetoAEditar = intent.getSerializableExtra("EXTRA_OBJETO") as? ObjecteUE
            if (objetoAEditar != null) {
                prellenarFormulario(objetoAEditar!!)
            } else {
                cargarJacimentUsuario(etJaciment, actvSector)
            }
        } else {
            objetoAEditar = savedInstanceState.getSerializable("OBJETO_EDITAR") as? ObjecteUE
            val uriStrings = savedInstanceState.getStringArrayList("SELECTED_URIS")
            selectedUris.clear()
            uriStrings?.forEach { selectedUris.add(Uri.parse(it)) }
            fotoAdapter.notifyDataSetChanged()
        }

        if (isReadOnly) {
            bloquearFormulario()
            btnAddImage.visibility = View.GONE
            btnSave.text = "Enrera"
            btnSave.setOnClickListener { finish() }
        } else {
            btnAddImage.setOnClickListener { getContent.launch(arrayOf("image/*")) }
            btnSave.setOnClickListener { 
                val tilUe = findViewById<TextInputLayout>(R.id.til_ue)
                guardarOActualizar(etUe, tilUe) 
            }
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }

    private fun cargarJacimentUsuario(etJaciment: TextInputEditText, actvSector: AutoCompleteTextView) {
        val userEmail = FirebaseAuth.getInstance().currentUser?.email
        if (userEmail != null) {
            FirebaseFirestore.getInstance().collection("usuaris")
                .whereEqualTo("email", userEmail)
                .get()
                .addOnSuccessListener { query ->
                    if (!query.isEmpty) {
                        val excavacio = query.documents[0].get("excavacio")?.toString()
                        if (excavacio != null) {
                            etJaciment.setText(excavacio)
                            etJaciment.isEnabled = false 
                            
                            findViewById<TextInputLayout>(R.id.til_sector).isEnabled = true
                            actvSector.isEnabled = true
                            actualizarSectores(excavacio, actvSector)
                        }
                    }
                }
        }
    }

    private fun guardarOActualizar(etUe: TextInputEditText, tilUe: TextInputLayout) {
        val ueText = etUe.text.toString().trim()
        val jacimentText = findViewById<TextInputEditText>(R.id.et_jaciment).text.toString()

        if (ueText.isBlank()) {
            tilUe.error = "Aquest camp és obligatori"
            return
        }

        if (isFromDatabase) {
            actualizarEnBaseDeDatos(ueText, jacimentText)
        } else {
            guardarEnLocal(ueText, jacimentText)
        }
    }

    private fun actualizarEnBaseDeDatos(ueText: String, jacimentText: String) {
        CoroutineScope(Dispatchers.Main).launch {
            val progressDialog = MaterialAlertDialogBuilder(this@FormularioUE)
                .setTitle("Actualitzant base de dades...")
                .setMessage("S'estan pujant imatges noves i guardant canvis.")
                .setCancelable(false).show()

            val finalUrls = mutableListOf<String>()
            for (uri in selectedUris) {
                val uriString = uri.toString()
                if (uriString.startsWith("http")) {
                    finalUrls.add(uriString)
                } else {
                    val publicUrl = S3Service.uploadImage(this@FormularioUE, uri)
                    if (publicUrl != null) finalUrls.add(publicUrl)
                }
            }

            val objecteActualitzat = ObjecteUE(
                codi_ue = ueText,
                codi_sector = findViewById<AutoCompleteTextView>(R.id.actv_sector).text.toString(),
                tipus_ue = findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue).text.toString(),
                descripcio = findViewById<TextInputEditText>(R.id.et_descripcio).text.toString(),
                registrat_per = FirebaseAuth.getInstance().currentUser?.email ?: objetoAEditar?.registrat_per ?: "",
                material = findViewById<TextInputEditText>(R.id.et_material).text.toString(),
                estat_conservacio = findViewById<TextInputEditText>(R.id.et_estat_conservacio)?.text.toString() ?: "",
                cronologia = findViewById<TextInputEditText>(R.id.et_cronologia).text.toString(),
                textura = findViewById<TextInputEditText>(R.id.Textura).text.toString(),
                color = findViewById<TextInputEditText>(R.id.et_color).text.toString(),
                relacions = obtenerRelacionesDeVista(),
                imatges_urls = finalUrls,
                sincronitzat = false,
                fecha_creacio = objetoAEditar?.fecha_creacio ?: System.currentTimeMillis(),
                jaciment = jacimentText
            )

            val db = FirebaseFirestore.getInstance()
            val collection = db.collection("unitats_estratigrafiques")
            val newDocId = "${jacimentText}_$ueText".replace("/", "_")

            objetoAEditar?.let {
                val oldDocId = "${it.jaciment}_${it.codi_ue}".replace("/", "_")
                if (oldDocId != newDocId) collection.document(oldDocId).delete()
            }

            collection.document(newDocId).set(objecteActualitzat)
                .addOnSuccessListener {
                    progressDialog.dismiss()
                    Toast.makeText(this@FormularioUE, "UE Actualitzada a la base de dades", Toast.LENGTH_SHORT).show()
                    finish()
                }
                .addOnFailureListener { e ->
                    progressDialog.dismiss()
                    Toast.makeText(this@FormularioUE, "Error: ${e.message}", Toast.LENGTH_LONG).show()
                }
        }
    }

    private fun guardarEnLocal(ueText: String, jacimentText: String) {
        val nouObjecte = ObjecteUE(
            codi_ue = ueText,
            codi_sector = findViewById<AutoCompleteTextView>(R.id.actv_sector).text.toString(),
            tipus_ue = findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue).text.toString(),
            descripcio = findViewById<TextInputEditText>(R.id.et_descripcio).text.toString(),
            registrat_per = FirebaseAuth.getInstance().currentUser?.email ?: objetoAEditar?.registrat_per ?: "",
            material = findViewById<TextInputEditText>(R.id.et_material).text.toString(),
            estat_conservacio = findViewById<TextInputEditText>(R.id.et_estat_conservacio)?.text.toString() ?: "",
            cronologia = findViewById<TextInputEditText>(R.id.et_cronologia).text.toString(),
            textura = findViewById<TextInputEditText>(R.id.Textura).text.toString(),
            color = findViewById<TextInputEditText>(R.id.et_color).text.toString(),
            relacions = obtenerRelacionesDeVista(),
            imatges_urls = selectedUris.map { it.toString() },
            sincronitzat = false,
            fecha_creacio = objetoAEditar?.fecha_creacio ?: System.currentTimeMillis(),
            jaciment = jacimentText
        )

        if (objetoAEditar != null && (objetoAEditar!!.codi_ue != ueText || objetoAEditar!!.jaciment != jacimentText)) {
            DataManager.deleteUE(this, objetoAEditar!!.codi_ue, objetoAEditar!!.jaciment)
        }
        DataManager.saveUE(this, nouObjecte)
        finish()
    }

    private fun bloquearFormulario() {
        val root = findViewById<ViewGroup>(R.id.main)
        deshabilitarVistasRecursivo(root)
    }

    private fun deshabilitarVistasRecursivo(layout: ViewGroup) {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            if (child.id != R.id.button_save) {
                child.isEnabled = false
                if (child is ViewGroup) deshabilitarVistasRecursivo(child)
            }
        }
    }

    private fun prellenarFormulario(obj: ObjecteUE) {
        findViewById<TextInputEditText>(R.id.et_ue).setText(obj.codi_ue)
        findViewById<TextInputEditText>(R.id.et_cronologia).setText(obj.cronologia)
        findViewById<TextInputEditText>(R.id.et_descripcio).setText(obj.descripcio)
        findViewById<TextInputEditText>(R.id.Textura).setText(obj.textura)
        findViewById<TextInputEditText>(R.id.et_color).setText(obj.color)
        findViewById<TextInputEditText>(R.id.et_material).setText(obj.material)
        findViewById<TextInputEditText>(R.id.et_estat_conservacio)?.setText(obj.estat_conservacio)

        val etJaciment = findViewById<TextInputEditText>(R.id.et_jaciment)
        etJaciment.setText(obj.jaciment)
        etJaciment.isEnabled = false 
        
        val actvSector = findViewById<AutoCompleteTextView>(R.id.actv_sector)
        findViewById<TextInputLayout>(R.id.til_sector).isEnabled = !isReadOnly
        actvSector.isEnabled = !isReadOnly
        actualizarSectores(obj.jaciment, actvSector)
        actvSector.setText(obj.codi_sector, false)

        findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue).setText(obj.tipus_ue, false)

        selectedUris.clear()
        obj.imatges_urls.forEach { url -> if (url.isNotEmpty()) selectedUris.add(Uri.parse(url)) }
        fotoAdapter.notifyDataSetChanged()

        val relMap = mapOf(
            "igual" to R.id.et_igual_a, "se_li_lliura" to R.id.et_se_li_lliura,
            "cobert" to R.id.et_cobert_per, "tallat" to R.id.et_tallat_per,
            "reblert" to R.id.et_reblert_per, "lliura" to R.id.et_es_lliura_a,
            "cobreix" to R.id.et_cobreix_a, "talla" to R.id.et_talla_a, "rebleix" to R.id.et_rebleix_a
        )
        obj.relacions.forEach { rel ->
            relMap[rel.tipus]?.let { findViewById<TextInputEditText>(it)?.setText(rel.desti) }
        }
    }

    private fun obtenerRelacionesDeVista(): List<RelacioUE> {
        val list = mutableListOf<RelacioUE>()
        val fields = mapOf(
            "igual" to R.id.et_igual_a, "se_li_lliura" to R.id.et_se_li_lliura,
            "cobert" to R.id.et_cobert_per, "tallat" to R.id.et_tallat_per,
            "reblert" to R.id.et_reblert_per, "lliura" to R.id.et_es_lliura_a,
            "cobreix" to R.id.et_cobreix_a, "talla" to R.id.et_talla_a, "rebleix" to R.id.et_rebleix_a
        )
        fields.forEach { (tipo, id) ->
            findViewById<TextInputEditText>(id)?.text?.toString()?.takeIf { it.isNotEmpty() }?.let { list.add(RelacioUE(tipo, it)) }
        }
        return list
    }

    private fun actualizarSectores(jaciment: String, actvSector: AutoCompleteTextView) {
        val sectores = DataManager.getSectorsByJaciment(this, jaciment)
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, sectores)
        actvSector.setAdapter(adapter)
    }

    private fun setupDropdownManual(autoComplete: AutoCompleteTextView, items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        autoComplete.setAdapter(adapter)
        autoComplete.inputType = android.text.InputType.TYPE_NULL
    }

    private fun mostrarDialogoConfirmacion() {
        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Descartar canvis?")
            .setMessage("Si surts ara, perdràs tota la informació.")
            .setPositiveButton("Sortir") { _, _ -> finish() }
            .setNegativeButton("Continuar", null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList("SELECTED_URIS", ArrayList(selectedUris.map { it.toString() }))
        outState.putSerializable("OBJETO_EDITAR", objetoAEditar)
    }
}