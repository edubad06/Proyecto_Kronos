package com.iticbcn.kronos.ui.galeria

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import com.iticbcn.kronos.R
import com.iticbcn.kronos.ui.adapter.GaleriaPagerAdapter
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.iticbcn.kronos.ui.main.MainActivity
import com.iticbcn.kronos.ui.ue.UEFragment
import com.iticbcn.kronos.ui.ue.DBUEFragment
import com.iticbcn.kronos.ui.accountConfig.AccountConfigActivity
import androidx.core.view.get

class GaleriaActivity : AppCompatActivity() {

    private var userJaciment: String = ""
    private var userRol: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_galeria)

        val cabeceraPrincipal: View = findViewById(R.id.cabecera_principal)
        val bottomNavContainer: View = findViewById(R.id.bottom_navigation_container)

        // ✅ Ajuste dinámico para notch y barra de navegación (actualizado al nuevo layout)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Ajustamos la cabecera para que no quede bajo el notch
            cabeceraPrincipal.setPadding(
                cabeceraPrincipal.paddingLeft, 
                systemBars.top, 
                cabeceraPrincipal.paddingRight, 
                cabeceraPrincipal.paddingBottom
            )
            
            // Ajustamos la navegación inferior para que no quede bajo la barra del sistema
            bottomNavContainer.setPadding(
                bottomNavContainer.paddingLeft, 
                bottomNavContainer.paddingTop, 
                bottomNavContainer.paddingRight, 
                systemBars.bottom
            )
            
            insets
        }

        val viewPager: ViewPager2 = findViewById(R.id.view_pager)
        val bottomNavigation: BottomNavigationView = findViewById(R.id.bottom_navigation)
        val btnShowFilter: Button = findViewById(R.id.btn_show_filter)
        val ivOptions: ImageView = findViewById(R.id.iv_galeria_options)

        val adapter = GaleriaPagerAdapter(this)
        viewPager.adapter = adapter
        viewPager.isUserInputEnabled = true





        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_local -> { viewPager.currentItem = 0; true }
                R.id.nav_db -> { viewPager.currentItem = 1; true }
                else -> false
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                bottomNavigation.menu[position].isChecked = true
            }
        })

        fetchUserJaciment()

        ivOptions.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.menu_galeria_options, popup.menu)
            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_settings -> {
                        startActivity(Intent(this, AccountConfigActivity::class.java))
                        true
                    }
                    R.id.action_logout -> {
                        FirebaseAuth.getInstance().signOut()
                        getSharedPreferences("AUTH_PREFS", MODE_PRIVATE).edit { putBoolean("remember_me", false) }
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                        true
                    }
                    R.id.action_exit -> {
                        finishAffinity()
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        btnShowFilter.setOnClickListener {
            val isDatabase = viewPager.currentItem == 1

            val popup = FilterPopup(this, userJaciment, isDatabase) { sector, ueId, tipus, onlyMine ->

                val finalOnlyMine =
                    if (userRol.lowercase() != "director" && !isDatabase) true
                    else onlyMine

                val fragment = (viewPager.adapter as GaleriaPagerAdapter)
                    .getFragment(viewPager.currentItem)

                when (fragment) {
                    is UEFragment -> fragment.applyFilters(
                        userJaciment, sector, ueId, tipus, finalOnlyMine
                    )
                    is DBUEFragment -> fragment.applyFilters(
                        userJaciment, sector, ueId, tipus, finalOnlyMine
                    )
                }
            }
            popup.show()
        }
    }

    private fun fetchUserJaciment() {
        val userEmail = FirebaseAuth.getInstance().currentUser?.email
        if (userEmail != null) {
            FirebaseFirestore.getInstance().collection("usuaris")
                .whereEqualTo("email", userEmail)
                .get()
                .addOnSuccessListener { query ->
                    if (!query.isEmpty) {
                        val doc = query.documents[0]
                        userJaciment = doc.getString("excavacio") ?: ""
                        userRol = doc.getString("rol") ?: ""
                    }
                }
        }
    }
}
