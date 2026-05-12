package com.iticbcn.kronos.ui.formulario

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.ArrayList
import java.util.Date

class FormularioUE : AppCompatActivity() {

    private lateinit var fotoAdapter: FotoAdapter
    private val selectedUris = mutableListOf<Uri>()
    private val deletedCloudUrls = mutableListOf<String>()
    private var objetoAEditar: ObjecteUE? = null
    private var isReadOnly: Boolean = false
    private var isFromDatabase: Boolean = false
    private var saveJob: Job? = null
    private var deleteJob: Job? = null
    private lateinit var progressBar: ProgressBar

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) getContent.launch(arrayOf("image/*"))
        else Toast.makeText(this, "Cal acceptar el permís", Toast.LENGTH_SHORT).show()
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                contentResolver.takePersistableUriPermission(it, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                selectedUris.add(it)
                fotoAdapter.notifyItemInserted(selectedUris.size - 1)
            } catch (e: Exception) {
                selectedUris.add(it)
                fotoAdapter.notifyItemInserted(selectedUris.size - 1)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_formulario_ue)

        val topBar: View = findViewById(R.id.topBar)
        val llButtonsContainer: View = findViewById(R.id.llButtonsContainer)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            topBar.setPadding(topBar.paddingLeft, systemBars.top, topBar.paddingRight, topBar.paddingBottom)
            llButtonsContainer.setPadding(llButtonsContainer.paddingLeft, llButtonsContainer.paddingTop, llButtonsContainer.paddingRight, systemBars.bottom)
            insets
        }

        isReadOnly = intent.getBooleanExtra("EXTRA_READ_ONLY", false)
        isFromDatabase = intent.getBooleanExtra("EXTRA_IS_DB", false)

        val etUe: TextInputEditText = findViewById(R.id.et_ue)
        val etJaciment = findViewById<AutoCompleteTextView>(R.id.et_jaciment)
        val tilJaciment = findViewById<TextInputLayout>(R.id.til_jaciment)
        val actvSector = findViewById<AutoCompleteTextView>(R.id.actv_sector)
        val actvTipus = findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue)
        val rvFotos = findViewById<RecyclerView>(R.id.rv_fotos_formulario)
        progressBar = findViewById(R.id.progressBar)

        rvFotos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        fotoAdapter = FotoAdapter(selectedUris, isReadOnly) { uri ->
            if (uri.toString().startsWith("http")) deletedCloudUrls.add(uri.toString())
        }
        rvFotos.adapter = fotoAdapter

        tilJaciment?.isEnabled = false

        if (savedInstanceState != null) {
            objetoAEditar = savedInstanceState.getSerializable("OBJETO_EDITAR") as? ObjecteUE
            savedInstanceState.getStringArrayList("SELECTED_URIS")?.forEach { selectedUris.add(it.toUri()) }
            fotoAdapter.notifyDataSetChanged()
        } else {
            objetoAEditar = intent.getSerializableExtra("EXTRA_OBJETO") as? ObjecteUE
            if (objetoAEditar != null) prellenarFormulario(objetoAEditar!!)
            else cargarJacimentUsuario(etJaciment, actvSector)
        }

        setupDropdownManual(actvTipus, TipusUEOptions.getNames())

        findViewById<Button>(R.id.btn_back).setOnClickListener { finish() }
        
        val btnEnviar = findViewById<Button>(R.id.btn_enviar)
        btnEnviar.setOnClickListener {
            val ue = etUe.text.toString().trim()
            if (ue.isEmpty()) findViewById<TextInputLayout>(R.id.til_ue).error = "Obligatori"
            else subirUEABaseDeDades(ue, etJaciment.text.toString())
        }

        // Si venim de DB, el botó d'enviar (pujar) no té sentit perquè ja hi és
        if (isFromDatabase) {
            btnEnviar.visibility = View.GONE
        }

        if (isReadOnly) {
            bloquearFormulario()
            findViewById<Button>(R.id.button_add_image).visibility = View.GONE
            findViewById<Button>(R.id.button_save).apply {
                text = getString(R.string.back)
                setOnClickListener { finish() }
            }
        } else {
            findViewById<Button>(R.id.button_add_image).setOnClickListener { checkAndRequestPermission() }
            findViewById<Button>(R.id.button_save).setOnClickListener { 
                guardarOActualizar(etUe, findViewById(R.id.til_ue)) 
            }
        }

        configurarBotonEliminar(findViewById(R.id.button_delete_ficha))
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putSerializable("OBJETO_EDITAR", objetoAEditar)
        outState.putStringArrayList("SELECTED_URIS", ArrayList(selectedUris.map { it.toString() }))
    }

    private fun subirUEABaseDeDades(ueText: String, jacimentText: String) {
        saveJob = CoroutineScope(Dispatchers.Main).launch {
            val progress = MaterialAlertDialogBuilder(this@FormularioUE).setTitle("Pujant...").setCancelable(false).show()
            try {
                val user = FirebaseAuth.getInstance().currentUser
                val token = user?.getIdToken(false)?.await()?.token
                val publicUrls = mutableListOf<String>()

                for (uri in selectedUris) {
                    if (uri.toString().startsWith("http")) publicUrls.add(uri.toString())
                    else S3Service.uploadImage(this@FormularioUE, uri, token)?.let { publicUrls.add(it) }
                }

                // Generar ID compuesta compatible con Web
                val docId = "${jacimentText}_${ueText}".replace("/", "_")
                val db = FirebaseFirestore.getInstance().collection("unitats_estratigrafiques")

                val finalObjecte = ObjecteUE(
                    codi_ue = ueText,
                    codi_sector = findViewById<AutoCompleteTextView>(R.id.actv_sector).text.toString(),
                    tipus_ue = findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue).text.toString(),
                    descripcio = findViewById<TextInputEditText>(R.id.et_descripcio).text.toString(),
                    registrat_per = objetoAEditar?.registrat_per ?: user?.uid ?: "",
                    material = findViewById<TextInputEditText>(R.id.et_material).text.toString(),
                    cronologia = findViewById<TextInputEditText>(R.id.et_cronologia).text.toString(),
                    textura = findViewById<TextInputEditText>(R.id.Textura).text.toString(),
                    color = findViewById<TextInputEditText>(R.id.et_color).text.toString(),
                    estat_conservacio = findViewById<TextInputEditText>(R.id.et_estat_conservacio)?.text?.toString() ?: "",
                    relacions = obtenerRelacionesDeVista(),
                    imatges_urls = publicUrls,
                    sincronitzat = true,
                    jaciment = jacimentText,
                    data = objetoAEditar?.data ?: Date()
                )

                db.document(docId).set(finalObjecte).await()
                
                if (objetoAEditar != null) {
                    val oldId = "${objetoAEditar!!.jaciment}_${objetoAEditar!!.codi_ue}".replace("/", "_")
                    if (oldId != docId) db.document(oldId).delete().await()
                    DataManager.deleteUE(this@FormularioUE, objetoAEditar!!.codi_ue, objetoAEditar!!.jaciment)
                }
                
                progress.dismiss()
                finish()
            } catch (e: Exception) {
                progress.dismiss()
                Toast.makeText(this@FormularioUE, "Error: ${e.message}", Toast.LENGTH_LONG).show()
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
        findViewById<AutoCompleteTextView>(R.id.et_jaciment).setText(obj.jaciment, false)
        actualizarSectores(obj.jaciment, findViewById(R.id.actv_sector))
        findViewById<AutoCompleteTextView>(R.id.actv_sector).setText(obj.codi_sector, false)
        findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue).setText(obj.tipus_ue, false)

        selectedUris.clear()
        obj.imatges_urls.forEach { if (it.isNotEmpty()) selectedUris.add(it.toUri()) }
        
        // Rellenar relaciones con claves compatibles Web
        val fields = mapOf(
            "igual_a" to R.id.et_igual_a, "se_li_lliura" to R.id.et_se_li_lliura,
            "cobert_per" to R.id.et_cobert_per, "tallat_per" to R.id.et_tallat_per,
            "farcit_per" to R.id.et_reblert_per, "lliura" to R.id.et_es_lliura_a,
            "cobreix" to R.id.et_cobreix_a, "talla" to R.id.et_talla_a, "farceix" to R.id.et_rebleix_a
        )
        obj.relacions.forEach { rel ->
            fields[rel.tipus]?.let { id -> findViewById<TextInputEditText>(id).setText(rel.desti) }
        }
        fotoAdapter.notifyDataSetChanged()
    }

    private fun guardarOActualizar(etUe: TextInputEditText, tilUe: TextInputLayout) {
        val ue = etUe.text.toString().trim()
        val jac = findViewById<AutoCompleteTextView>(R.id.et_jaciment).text.toString()
        if (ue.isEmpty()) { tilUe.error = "Obligatori"; return }

        if (isFromDatabase || objetoAEditar?.id != null) actualizarEnBaseDeDatos(ue, jac)
        else guardarEnLocal(ue, jac)
    }

    private fun actualizarEnBaseDeDatos(ueText: String, jacimentText: String) {
        saveJob = CoroutineScope(Dispatchers.Main).launch {
            progressBar.visibility = View.VISIBLE
            try {
                val finalUrls = mutableListOf<String>()
                selectedUris.forEach { uri ->
                    if (uri.toString().startsWith("http")) finalUrls.add(uri.toString())
                    else S3Service.uploadImage(this@FormularioUE, uri)?.let { finalUrls.add(it) }
                }

                val docId = "${jacimentText}_${ueText}".replace("/", "_")
                val finalObject = ObjecteUE(
                    codi_ue = ueText,
                    codi_sector = findViewById<AutoCompleteTextView>(R.id.actv_sector).text.toString(),
                    tipus_ue = findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue).text.toString(),
                    descripcio = findViewById<TextInputEditText>(R.id.et_descripcio).text.toString(),
                    registrat_per = objetoAEditar?.registrat_per ?: FirebaseAuth.getInstance().currentUser?.uid ?: "",
                    material = findViewById<TextInputEditText>(R.id.et_material).text.toString(),
                    cronologia = findViewById<TextInputEditText>(R.id.et_cronologia).text.toString(),
                    textura = findViewById<TextInputEditText>(R.id.Textura).text.toString(),
                    color = findViewById<TextInputEditText>(R.id.et_color).text.toString(),
                    estat_conservacio = findViewById<TextInputEditText>(R.id.et_estat_conservacio)?.text?.toString() ?: "",
                    relacions = obtenerRelacionesDeVista(),
                    imatges_urls = finalUrls,
                    sincronitzat = true,
                    jaciment = jacimentText,
                    data = objetoAEditar?.data ?: Date()
                )

                FirebaseFirestore.getInstance().collection("unitats_estratigrafiques").document(docId).set(finalObject).await()
                if (objetoAEditar != null) {
                    val oldId = "${objetoAEditar!!.jaciment}_${objetoAEditar!!.codi_ue}".replace("/", "_")
                    if (oldId != docId) FirebaseFirestore.getInstance().collection("unitats_estratigrafiques").document(oldId).delete().await()
                }
                finish()
            } catch (e: Exception) {
                Toast.makeText(this@FormularioUE, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            } finally { progressBar.visibility = View.GONE }
        }
    }

    private fun eliminarFitxa() {
        val ue = findViewById<TextInputEditText>(R.id.et_ue).text.toString()
        val jac = findViewById<AutoCompleteTextView>(R.id.et_jaciment).text.toString()
        val docId = "${jac}_${ue}".replace("/", "_")

        if (isFromDatabase) {
            deleteJob = CoroutineScope(Dispatchers.Main).launch {
                val progress = MaterialAlertDialogBuilder(this@FormularioUE).setTitle("Eliminant...").show()
                try {
                    FirebaseFirestore.getInstance().collection("unitats_estratigrafiques").document(docId).delete().await()
                    progress.dismiss()
                    finish()
                } catch (e: Exception) {
                    progress.dismiss()
                    Toast.makeText(this@FormularioUE, "Error al eliminar", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            DataManager.deleteUE(this, ue, jac)
            finish()
        }
    }

    private fun cargarJacimentUsuario(etJaciment: AutoCompleteTextView, actvSector: AutoCompleteTextView) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseFirestore.getInstance().collection("usuaris").whereEqualTo("email", user.email).get().addOnSuccessListener { query ->
            if (!query.isEmpty) {
                val jac = query.documents[0].getString("excavacio") ?: ""
                etJaciment.setText(jac, false)
                actualizarSectores(jac, actvSector)
            }
        }
    }

    private fun actualizarSectores(jaciment: String, actvSector: AutoCompleteTextView) {
        CoroutineScope(Dispatchers.Main).launch {
            val sectores = com.iticbcn.kronos.data.repository.UERepository.getSectoresPorJaciment(jaciment)
            actvSector.setAdapter(ArrayAdapter(this@FormularioUE, android.R.layout.simple_dropdown_item_1line, sectores))
        }
    }

    private fun obtenerRelacionesDeVista(): List<RelacioUE> {
        val list = mutableListOf<RelacioUE>()
        // ✅ Claves actualizadas para compatibilidad total con la Web
        val fields = mapOf(
            "igual_a" to R.id.et_igual_a, "se_li_lliura" to R.id.et_se_li_lliura,
            "cobert_per" to R.id.et_cobert_per, "tallat_per" to R.id.et_tallat_per,
            "farcit_per" to R.id.et_reblert_per, "lliura" to R.id.et_es_lliura_a,
            "cobreix" to R.id.et_cobreix_a, "talla" to R.id.et_talla_a, "farceix" to R.id.et_rebleix_a
        )
        fields.forEach { (tipo, id) -> 
            findViewById<TextInputEditText>(id)?.text?.toString()?.takeIf { it.isNotEmpty() }?.let { list.add(RelacioUE(tipo, it)) }
        }
        return list
    }

    private fun bloquearFormulario() {
        val root = findViewById<ViewGroup>(R.id.main)
        deshabilitarVistasRecursivo(root)
    }

    private fun deshabilitarVistasRecursivo(layout: ViewGroup) {
        for (i in 0 until layout.childCount) {
            val child = layout.getChildAt(i)
            // Mantener habilitados los botones de cerrar/volver
            if (child.id != R.id.button_save && child.id != R.id.btn_back) {
                child.isEnabled = false
                if (child is ViewGroup) deshabilitarVistasRecursivo(child)
            }
        }
    }

    private fun setupDropdownManual(autoComplete: AutoCompleteTextView, items: List<String>) {
        autoComplete.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, items))
    }

    private fun guardarEnLocal(ueText: String, jacimentText: String) {
        val user = FirebaseAuth.getInstance().currentUser
        val nou = ObjecteUE(
            codi_ue = ueText,
            jaciment = jacimentText,
            codi_sector = findViewById<AutoCompleteTextView>(R.id.actv_sector).text.toString(),
            tipus_ue = findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue).text.toString(),
            descripcio = findViewById<TextInputEditText>(R.id.et_descripcio).text.toString(),
            material = findViewById<TextInputEditText>(R.id.et_material).text.toString(),
            cronologia = findViewById<TextInputEditText>(R.id.et_cronologia).text.toString(),
            textura = findViewById<TextInputEditText>(R.id.Textura).text.toString(),
            color = findViewById<TextInputEditText>(R.id.et_color).text.toString(),
            estat_conservacio = findViewById<TextInputEditText>(R.id.et_estat_conservacio)?.text?.toString() ?: "",
            registrat_per = objetoAEditar?.registrat_per ?: user?.uid ?: "",
            relacions = obtenerRelacionesDeVista(),
            imatges_urls = selectedUris.map { it.toString() },
            sincronitzat = false,
            data = objetoAEditar?.data ?: Date()
        )
        if (objetoAEditar != null) DataManager.deleteUE(this, objetoAEditar!!.codi_ue, objetoAEditar!!.jaciment)
        DataManager.saveUE(this, nou)
        Toast.makeText(this, "Desat localment", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun configurarBotonEliminar(btn: Button) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (objetoAEditar == null || isReadOnly) return
        FirebaseFirestore.getInstance().collection("usuaris").whereEqualTo("email", user.email).get().addOnSuccessListener { docs ->
            val rol = docs.firstOrNull()?.getString("rol")?.lowercase() ?: "tecnic"
            val esSeu = objetoAEditar?.registrat_per?.equals(user.uid, true) == true
            if (esSeu || rol == "director") {
                btn.visibility = View.VISIBLE
                btn.setOnClickListener { mostrarConfirmacioEliminar() }
            }
        }
    }

    private fun mostrarConfirmacioEliminar() {
        MaterialAlertDialogBuilder(this).setTitle("Eliminar").setMessage("Estàs segur?").setNegativeButton("No", null).setPositiveButton("Sí") { _, _ -> eliminarFitxa() }.show()
    }

    private fun checkAndRequestPermission() {
        val p = if (android.os.Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES else Manifest.permission.READ_EXTERNAL_STORAGE
        if (ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED) getContent.launch(arrayOf("image/*"))
        else requestPermissionLauncher.launch(p)
    }
}
