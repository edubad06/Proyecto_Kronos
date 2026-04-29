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
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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

class FormularioUE : AppCompatActivity() {

    private lateinit var fotoAdapter: FotoAdapter
    private val selectedUris = mutableListOf<Uri>()
    private val deletedCloudUrls = mutableListOf<String>()
    private var objetoAEditar: ObjecteUE? = null
    private var isReadOnly: Boolean = false
    private var isFromDatabase: Boolean = false
    private var saveJob: Job? = null
    private var deleteJob: Job? = null

    // Solicitud de permisos
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            getContent.launch(arrayOf("image/*"))
        } else {
            Toast.makeText(this, "Cal acceptar el permís per accedir a la galeria", Toast.LENGTH_SHORT).show()
        }
    }

    private val getContent = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        uri?.let {
            try {
                // Tomar permisos persistentes de lectura para esta URI específica
                val takeFlags: Int = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                contentResolver.takePersistableUriPermission(it, takeFlags)
                
                selectedUris.add(it)
                fotoAdapter.notifyItemInserted(selectedUris.size - 1)
            } catch (e: Exception) {
                Log.e("FormularioUE", "Error al tomar permisos persistentes: ${e.message}")
                // Si falla takePersistableUriPermission, aún añadimos la URI pero avisamos
                selectedUris.add(it)
                fotoAdapter.notifyItemInserted(selectedUris.size - 1)
            }
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
        val btnDeleteFicha: Button = findViewById(R.id.button_delete_ficha)
        val etUe: TextInputEditText = findViewById(R.id.et_ue)
        val etJaciment = findViewById<TextInputEditText>(R.id.et_jaciment)
        val actvSector = findViewById<AutoCompleteTextView>(R.id.actv_sector)
        val actvTipus = findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue)
        val rvFotos = findViewById<RecyclerView>(R.id.rv_fotos_formulario)
        
        rvFotos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        fotoAdapter = FotoAdapter(selectedUris, isReadOnly) { uriEliminada ->
            val uriStr = uriEliminada.toString()
            if (uriStr.startsWith("http")) deletedCloudUrls.add(uriStr)
        }
        rvFotos.adapter = fotoAdapter

        setupDropdownManual(actvTipus, TipusUEOptions.getNames())

        if (savedInstanceState == null) {
            objetoAEditar = intent.getSerializableExtra("EXTRA_OBJETO") as? ObjecteUE
            if (objetoAEditar != null) prellenarFormulario(objetoAEditar!!)
            else cargarJacimentUsuario(etJaciment, actvSector)
        } else {
            objetoAEditar = savedInstanceState.getSerializable("OBJETO_EDITAR") as? ObjecteUE
            val uriStrings = savedInstanceState.getStringArrayList("SELECTED_URIS")
            uriStrings?.forEach { selectedUris.add(Uri.parse(it)) }
            fotoAdapter.notifyDataSetChanged()
        }

        if (isReadOnly) {
            bloquearFormulario()
            btnAddImage.visibility = View.GONE
            btnSave.text = "Enrera"
            btnSave.setOnClickListener { finish() }
        } else {
            btnAddImage.setOnClickListener { 
                checkAndRequestPermission() 
            }
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

    private fun checkAndRequestPermission() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            getContent.launch(arrayOf("image/*"))
        } else {
            requestPermissionLauncher.launch(permission)
        }
    }

    // El resto de funciones (guardarEnLocal, guardarOActualizar, etc.) se mantienen igual...
    
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

    private fun prellenarFormulario(obj: ObjecteUE) {
        findViewById<TextInputEditText>(R.id.et_ue).setText(obj.codi_ue)
        findViewById<TextInputEditText>(R.id.et_cronologia).setText(obj.cronologia)
        findViewById<TextInputEditText>(R.id.et_descripcio).setText(obj.descripcio)
        findViewById<TextInputEditText>(R.id.Textura).setText(obj.textura)
        findViewById<TextInputEditText>(R.id.et_color).setText(obj.color)
        findViewById<TextInputEditText>(R.id.et_material).setText(obj.material)
        findViewById<TextInputEditText>(R.id.et_estat_conservacio)?.setText(obj.estat_conservacio)
        findViewById<TextInputEditText>(R.id.et_jaciment).setText(obj.jaciment)
        
        actualizarSectores(obj.jaciment, findViewById(R.id.actv_sector))
        findViewById<AutoCompleteTextView>(R.id.actv_sector).setText(obj.codi_sector, false)
        findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue).setText(obj.tipus_ue, false)

        selectedUris.clear()
        obj.imatges_urls.forEach { url -> if (url.isNotEmpty()) selectedUris.add(Uri.parse(url)) }
        fotoAdapter.notifyDataSetChanged()
    }

    private fun setupDropdownManual(autoComplete: AutoCompleteTextView, items: List<String>) {
        val adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, items)
        autoComplete.setAdapter(adapter)
    }

    private fun cargarJacimentUsuario(etJaciment: TextInputEditText, actvSector: AutoCompleteTextView) {
        val userEmail = FirebaseAuth.getInstance().currentUser?.email ?: return
        FirebaseFirestore.getInstance().collection("usuaris")
            .whereEqualTo("email", userEmail).get()
            .addOnSuccessListener { query ->
                if (!query.isEmpty) {
                    val excavacio = query.documents[0].getString("excavacio") ?: return@addOnSuccessListener
                    etJaciment.setText(excavacio)
                    actualizarSectores(excavacio, actvSector)
                }
            }
    }

    private fun actualizarSectores(jaciment: String, actvSector: AutoCompleteTextView) {
        val sectores = DataManager.getSectorsByJaciment(this, jaciment)
        actvSector.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, sectores))
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

    private fun guardarOActualizar(etUe: TextInputEditText, tilUe: TextInputLayout) {
        val ueText = etUe.text.toString().trim()
        val jacimentText = findViewById<TextInputEditText>(R.id.et_jaciment).text.toString()
        if (ueText.isBlank()) { tilUe.error = "Obligatori"; return }
        if (isFromDatabase) actualizarEnBaseDeDatos(ueText, jacimentText) else guardarEnLocal(ueText, jacimentText)
    }

    private fun actualizarEnBaseDeDatos(ueText: String, jacimentText: String) {
        saveJob = CoroutineScope(Dispatchers.Main).launch {
            val progress = MaterialAlertDialogBuilder(this@FormularioUE).setTitle("Actualitzant...").setCancelable(false).show()
            try {
                val finalUrls = mutableListOf<String>()
                for (uri in selectedUris) {
                    val s = uri.toString()
                    if (s.startsWith("http")) finalUrls.add(s)
                    else S3Service.uploadImage(this@FormularioUE, uri)?.let { finalUrls.add(it) }
                }
                val obj = ObjecteUE(codi_ue = ueText, codi_sector = findViewById<AutoCompleteTextView>(R.id.actv_sector).text.toString(),
                    tipus_ue = findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue).text.toString(),
                    descripcio = findViewById<TextInputEditText>(R.id.et_descripcio).text.toString(),
                    registrat_per = FirebaseAuth.getInstance().currentUser?.email ?: "",
                    imatges_urls = finalUrls, sincronitzat = true, jaciment = jacimentText)
                
                val db = FirebaseFirestore.getInstance().collection("unitats_estratigrafiques")
                val docId = "${jacimentText}_$ueText".replace("/", "_")
                db.document(docId).set(obj).await()
                progress.dismiss()
                finish()
            } catch (e: Exception) { progress.dismiss() }
        }
    }
}
