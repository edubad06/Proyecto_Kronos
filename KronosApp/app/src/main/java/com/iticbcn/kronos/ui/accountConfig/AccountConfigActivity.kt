package com.iticbcn.kronos.ui.accountConfig

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.iticbcn.kronos.databinding.ActivityAccountConfigBinding

class AccountConfigActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAccountConfigBinding
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityAccountConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)

        llegirDadesUsuari()

        binding.btnTornarEnrera.setOnClickListener {
            finish()
        }

        binding.btnCambiarContrasenya.setOnClickListener {
            val email = auth.currentUser?.email
            if (email != null) {
                // Diálogo de confirmación inicial
                MaterialAlertDialogBuilder(this)
                    .setTitle("Canviar contrasenya")
                    .setMessage("Vols rebre un correu electrònic a $email per restablir la teva contrasenya?")
                    .setNegativeButton("Cancel·lar", null)
                    .setPositiveButton("Enviar correu") { _, _ ->
                        // Petición a Firebase
                        auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                // Popup informativo de éxito y aviso de SPAM
                                MaterialAlertDialogBuilder(this)
                                    .setTitle("Correu enviat!")
                                    .setMessage("S'ha enviat un enllaç a la teva bústia. Si no el veus en uns minuts, si us plau, revisa la teva carpeta de correu brossa (SPAM).")
                                    .setPositiveButton("Entès", null)
                                    .show()
                            } else {
                                Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    .show()
            }
        }
    }

    private fun llegirDadesUsuari() {
        val emailAuth = auth.currentUser?.email

        if (emailAuth != null) {
            db.collection("usuaris")
                .whereEqualTo("email", emailAuth)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    if (!querySnapshot.isEmpty) {
                        val document = querySnapshot.documents[0]
                        
                        val nom = document.getString("nom") ?: "---"
                        val email = document.getString("email") ?: emailAuth
                        val excavacio = document.get("excavacio")?.toString() ?: "Cap assignada"
                        val rol = document.getString("rol") ?: "Usuari"

                        binding.tvNombreHeader.text = nom
                        binding.tvRolHeader.text = rol
                        binding.tvNombre.text = nom
                        binding.tvEmail.text = email
                        binding.tvExcavacio.text = excavacio
                        binding.tvRol.text = rol
                    } else {
                        Toast.makeText(this, "Usuari no trobat a la base de dades", Toast.LENGTH_SHORT).show()
                    }
                }
                .addOnFailureListener { exception ->
                    Toast.makeText(this, "Error en carregar dades: ${exception.message}", Toast.LENGTH_LONG).show()
                }
        } else {
            Toast.makeText(this, "No hi ha cap usuari autenticat", Toast.LENGTH_SHORT).show()
        }
    }
}
