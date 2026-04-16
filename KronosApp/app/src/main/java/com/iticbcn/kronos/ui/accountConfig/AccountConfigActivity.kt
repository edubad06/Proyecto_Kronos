package com.iticbcn.kronos.ui.accountConfig

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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

        binding.btnGuardarCambis.setOnClickListener {
            Toast.makeText(this, "Funcionalitat per guardar en implementació", Toast.LENGTH_SHORT).show()
        }

        binding.btnCambiarContrasenya.setOnClickListener {
            val email = auth.currentUser?.email
            if (email != null) {
                auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Correu de restabliment enviat a $email", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
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
                        val codiIntervencio = document.getString("codi_intervencio") ?: "---"

                        binding.tvNombreHeader.text = nom
                        binding.tvRolHeader.text = rol
                        binding.tvNombre.text = nom
                        binding.tvEmail.text = email
                        binding.tvExcavacio.text = excavacio
                        binding.tvRol.text = rol
                        binding.tvCodiEx.text = codiIntervencio
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
