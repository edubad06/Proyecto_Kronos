package com.iticbcn.kronos.ui.main

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.iticbcn.kronos.databinding.ActivityMainBinding
import com.iticbcn.kronos.ui.galeria.GaleriaActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        auth = FirebaseAuth.getInstance()

        // --- COMPROBAR SESIÓN INICIADA AL ARRANCAR ---
        val prefs = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
        val rememberMe = prefs.getBoolean("remember_me", false)

        // Si el usuario marcó "Recordar" y Firebase tiene sesión activa, saltamos el login
        if (rememberMe && auth.currentUser != null) {
            irAGaleria()
            return
        }

        enableEdgeToEdge()
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.etPassword.addTextChangedListener {
            binding.tilPassword.error = null
        }

        binding.buttonLogin.setOnClickListener {
            loginUser()
        }

        binding.tvForgotPassword.setOnClickListener {
            val email = binding.etUser.text.toString().trim()
            if (email.isEmpty()) {
                binding.tilUser.error = "Siusplau, introdueix el teu correu per restablir la contrasenya"
            } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                binding.tilUser.error = "L'adreça de correu no té un format vàlid"
            } else {
                binding.tilUser.error = null
                enviarCorreuRestabliment(email)
            }
        }
    }

    private fun enviarCorreuRestabliment(email: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Canviar contrasenya")
            .setMessage("Vols rebre un correu electrònic a $email per restablir la teva contrasenya?")
            .setNegativeButton("Cancel·lar", null)
            .setPositiveButton("Enviar correu") { _, _ ->
                auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                    if (task.isSuccessful) {
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

    private fun loginUser() {
        // Limpiar errores previos
        binding.tilUser.error = null
        binding.tilPassword.error = null

        val email = binding.etUser.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        var hasError = false
        if (email.isEmpty()) {
            binding.tilUser.error = "Siusplau, omple aquest camp"
            hasError = true
        } else if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.tilUser.error = "L'adreça de correu no té un format vàlid"
            hasError = true
        }

        if (password.isEmpty()) {
            binding.tilPassword.error = "Siusplau, omple aquest camp"
            hasError = true
        }

        if (hasError) return

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // --- GUARDAR PREFERENCIA USANDO KTX ---
                    val prefs = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
                    prefs.edit { 
                        putBoolean("remember_me", binding.cbRememberMe.isChecked) 
                    }

                    irAGaleria()
                } else {
                    val exception = task.exception
                    val message = exception?.message ?: ""
                    
                    when {
                        exception is FirebaseAuthInvalidUserException -> {
                            binding.tilUser.error = "L'adreça de correu no està registrada."
                        }
                        exception is FirebaseAuthInvalidCredentialsException -> {
                            binding.tilPassword.error = "La contrasenya és incorrecta."
                        }
                        else -> {
                            Toast.makeText(this, "Error d'accés: ${exception?.localizedMessage}", 
                                Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
    }

    private fun irAGaleria() {
        val intent = Intent(this, GaleriaActivity::class.java)
        startActivity(intent)
        finish()
    }
}
