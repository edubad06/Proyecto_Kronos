package com.iticbcn.kronos.ui.formulario

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.iticbcn.kronos.data.local.db.entities.ObjecteUE
import com.iticbcn.kronos.data.local.db.entities.RelacioUE
import com.iticbcn.kronos.R
import com.iticbcn.kronos.ui.adapter.FotoAdapter
import com.iticbcn.kronos.data.local.db.entities.TipusUEOptions
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.iticbcn.kronos.data.local.db.AppDatabase
import com.iticbcn.kronos.data.remote.S3Service
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.ArrayList
import java.util.Date
import java.util.UUID

class FormularioUE : AppCompatActivity() {

    private lateinit var fotoAdapter: FotoAdapter
    private val selectedUris = mutableListOf<Uri>()
    private var objetoAEditar: ObjecteUE? = null
    private var isReadOnly: Boolean = false
    private var isFromDatabase: Boolean = false
    private var saveJob: Job? = null
    private var deleteJob: Job? = null

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
        val etJaciment = findViewById<TextInputEditText>(R.id.et_jaciment)
        val tilJaciment = findViewById<TextInputLayout>(R.id.til_jaciment)
        val actvSector = findViewById<AutoCompleteTextView>(R.id.actv_sector)
        val actvTipus = findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue)
        val rvFotos = findViewById<RecyclerView>(R.id.rv_fotos_formulario)

        rvFotos.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        fotoAdapter = FotoAdapter(selectedUris, isReadOnly) { uri ->
            val url = uri.toString()

            if (url.startsWith("http")) {

                lifecycleScope.launch {
                    try {
                        S3Service.deleteImage(url)
                    } catch (_: Exception) {
                    }
                }
            }
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

        // --- CONFIGURACIÓN DE RETROCESO CON AVISO ---
        val onBackPressedCallback = object : androidx.activity.OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (hasThereBeenChanges()) {
                    MaterialAlertDialogBuilder(this@FormularioUE)
                        .setTitle("Sortir sense desar?")
                        .setMessage("Tens canvis sense guardar. Si surts ara, es perdran definitivament.")
                        .setNegativeButton("Cancel·lar", null)
                        .setPositiveButton("Sortir") { _, _ ->
                            isEnabled = false // Desactiva el callback para permitir la salida
                            onBackPressedDispatcher.onBackPressed()
                        }
                        .show()
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        // Actualizamos el botón back para que use el dispatcher
        findViewById<Button>(R.id.btn_back).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

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

            val progress = MaterialAlertDialogBuilder(this@FormularioUE)
                .setTitle("Pujant...")
                .setCancelable(false)
                .show()

            try {
                val user = FirebaseAuth.getInstance().currentUser
                val token = user?.getIdToken(false)?.await()?.token

                val publicUrls = mutableListOf<String>()

                for (uri in selectedUris) {
                    if (uri.toString().startsWith("http")) {
                        publicUrls.add(uri.toString())
                    } else {
                        S3Service.uploadImage(this@FormularioUE, uri, token)
                            ?.let { publicUrls.add(it) }
                    }
                }

                val db = FirebaseFirestore.getInstance()
                    .collection("unitats_estratigrafiques")

                val docRef = if (objetoAEditar?.id != null) {
                    db.document(objetoAEditar!!.id)
                } else {
                    db.document() // 🔥 ID automático
                }

                val id = docRef.id

                val finalObjecte = ObjecteUE(
                    id = id,
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

                docRef.set(finalObjecte).await()

                progress.dismiss()
                finish()

            } catch (e: Exception) {
                progress.dismiss()
                Toast.makeText(this@FormularioUE, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
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
        findViewById<TextInputEditText>(R.id.et_jaciment).setText(obj.jaciment)
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
        val jac = findViewById<TextInputEditText>(R.id.et_jaciment).text.toString()
        if (ue.isEmpty()) { tilUe.error = "Obligatori"; return }

        if (isFromDatabase || objetoAEditar?.id != null) actualizarEnBaseDeDatos(ue, jac)
        else guardarEnLocal(ue, jac)
    }

    private fun actualizarEnBaseDeDatos(ueText: String, jacimentText: String) {
        saveJob = CoroutineScope(Dispatchers.Main).launch {
            try {

                val finalUrls = mutableListOf<String>()

                selectedUris.forEach { uri ->
                    if (uri.toString().startsWith("http")) {
                        finalUrls.add(uri.toString())
                    } else {
                        S3Service.uploadImage(this@FormularioUE, uri)
                            ?.let { finalUrls.add(it) }
                    }
                }

                val db = FirebaseFirestore.getInstance()
                    .collection("unitats_estratigrafiques")

                val docRef = objetoAEditar?.id?.let {
                    db.document(it)
                } ?: db.document()

                val id = docRef.id

                val finalObject = ObjecteUE(
                    id = id,
                    codi_ue = ueText,
                    codi_sector = findViewById<AutoCompleteTextView>(R.id.actv_sector).text.toString(),
                    tipus_ue = findViewById<AutoCompleteTextView>(R.id.actv_tipus_ue).text.toString(),
                    descripcio = findViewById<TextInputEditText>(R.id.et_descripcio).text.toString(),
                    registrat_per = objetoAEditar?.registrat_per
                        ?: FirebaseAuth.getInstance().currentUser?.uid ?: "",
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

                docRef.set(finalObject).await()
                withContext(Dispatchers.IO) {
                    AppDatabase.getDatabase(this@FormularioUE)
                        .ueDao()
                        .insert(finalObject)
                }

                finish()

            } catch (e: Exception) {
                Toast.makeText(this@FormularioUE, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun eliminarFitxa() {

        val docId = objetoAEditar?.id ?: return

        if (isFromDatabase) {

            deleteJob = CoroutineScope(Dispatchers.Main).launch {

                val progress = MaterialAlertDialogBuilder(this@FormularioUE)
                    .setTitle("Eliminant...")
                    .show()

                try {

                    val fotos = objetoAEditar?.imatges_urls ?: emptyList()

                    // Borrar imágenes del bucket S3
                    fotos.forEach { url ->
                        try {
                            S3Service.deleteImage(url)
                        } catch (_: Exception) {
                        }
                    }

                    FirebaseFirestore.getInstance()
                        .collection("unitats_estratigrafiques")
                        .document(docId)
                        .delete()
                        .await()

                    withContext(Dispatchers.IO) {
                        objetoAEditar?.let {
                            AppDatabase.getDatabase(this@FormularioUE)
                                .ueDao()
                                .delete(it)
                        }
                    }

                    progress.dismiss()

                    finish()

                } catch (e: Exception) {

                    progress.dismiss()

                    Toast.makeText(
                        this@FormularioUE,
                        "Error al eliminar",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        } else {
            lifecycleScope.launch {
                objetoAEditar?.let {
                    AppDatabase.getDatabase(this@FormularioUE)
                        .ueDao()
                        .delete(it)
                }

                withContext(Dispatchers.Main) {
                    finish()
                }
            }
        }
    }

    private fun cargarJacimentUsuario(etJaciment: TextInputEditText, actvSector: AutoCompleteTextView) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        FirebaseFirestore.getInstance().collection("usuaris").whereEqualTo("email", user.email).get().addOnSuccessListener { query ->
            if (!query.isEmpty) {
                val jac = query.documents[0].getString("excavacio") ?: ""
                etJaciment.setText(jac)
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

    private fun deshabilitarVistasRecursivo(view: View) {
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                deshabilitarVistasRecursivo(view.getChildAt(i))
            }
        } else {
            // Solo deshabilitar si no es uno de los botones de "volver"
            if (view.id != R.id.btn_back && view.id != R.id.button_save) {
                view.isEnabled = false
            }
        }
    }

    private fun setupDropdownManual(autoComplete: AutoCompleteTextView, items: List<String>) {
        autoComplete.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, items))
    }

    private fun guardarEnLocal(ueText: String, jacimentText: String) {
        val user = FirebaseAuth.getInstance().currentUser

        val id = objetoAEditar?.id ?: UUID.randomUUID().toString()

        val nou = ObjecteUE(
            id = id,
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

        lifecycleScope.launch {
            try {
                val dao = AppDatabase.getDatabase(this@FormularioUE).ueDao()

                dao.insert(nou)

                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FormularioUE, "Desat localment", Toast.LENGTH_SHORT).show()
                    finish()
                }

            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@FormularioUE, "Error al guardar localment", Toast.LENGTH_SHORT).show()
                }
            }
        }
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
    private fun hasThereBeenChanges(): Boolean {
        if (isReadOnly) return false

        // Si es una ficha nueva y hay algo escrito o alguna foto
        if (objetoAEditar == null) {
            return findViewById<TextInputEditText>(R.id.et_ue).text?.isNotEmpty() == true ||
                    selectedUris.isNotEmpty() ||
                    findViewById<TextInputEditText>(R.id.et_descripcio).text?.isNotEmpty() == true
        }

        // Si estamos editando, comparamos los campos críticos
        val ueActual = findViewById<TextInputEditText>(R.id.et_ue).text.toString()
        val descActual = findViewById<TextInputEditText>(R.id.et_descripcio).text.toString()
        val jacimentActual = findViewById<TextInputEditText>(R.id.et_jaciment).text.toString()

        // Comparamos también si la lista de URIs ha cambiado de tamaño o contenido
        val fotosOriginales = objetoAEditar!!.imatges_urls.filter { it.isNotEmpty() }
        val fotosCambiadas = selectedUris.size != fotosOriginales.size

        return ueActual != objetoAEditar!!.codi_ue ||
                descActual != objetoAEditar!!.descripcio ||
                jacimentActual != objetoAEditar!!.jaciment ||
                fotosCambiadas
    }

    override fun onDestroy() {
        saveJob?.cancel()
        deleteJob?.cancel()
        super.onDestroy()
    }
}
