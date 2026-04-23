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

        val prefs = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
        val rememberMe = prefs.getBoolean("remember_me", false)

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

        binding.buttonLogin.setOnClickListener {
            loginUser()
        }
    }

    private fun loginUser() {
        val email = binding.etUser.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(this, "Siusplau, omple tots els camps", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val prefs = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
                    prefs.edit { 
                        putBoolean("remember_me", binding.cbRememberMe.isChecked) 
                    }
                    irAGaleria()
                } else {
                    val exception = task.exception
                    val errorMessage = when (exception) {
                        is FirebaseAuthInvalidUserException -> "L'adreça de correu no és vàlida o no existeix."
                        is FirebaseAuthInvalidCredentialsException -> "La contrasenya és incorrecta."
                        else -> "Error d'accés: ${exception?.localizedMessage}"
                    }
                    Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun irAGaleria() {
        val intent = Intent(this, GaleriaActivity::class.java)
        startActivity(intent)
        finish()
    }
}
