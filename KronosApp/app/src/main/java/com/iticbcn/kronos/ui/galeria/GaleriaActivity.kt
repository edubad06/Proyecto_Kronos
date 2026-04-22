package com.iticbcn.kronos.ui.galeria

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.edit
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

class GaleriaActivity : AppCompatActivity() {

    private var userJaciment: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_galeria)

        val viewPager: ViewPager2 = findViewById(R.id.view_pager)
        val bottomNavigation: BottomNavigationView = findViewById(R.id.bottom_navigation)
        val btnShowFilter: Button = findViewById(R.id.btn_show_filter)
        val ivOptions: ImageView = findViewById(R.id.iv_galeria_options)

        viewPager.adapter = GaleriaPagerAdapter(this)
        viewPager.isUserInputEnabled = false

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
                bottomNavigation.menu.getItem(position).isChecked = true
            }
        })

        // Fetch user jaciment to use in filters
        fetchUserJaciment()

        // MENU SUPERIOR DERECHO (Logout / Salir / Config)
        ivOptions.setOnClickListener { view ->
            val popup = PopupMenu(this, view)
            popup.menuInflater.inflate(R.menu.menu_galeria_options, popup.menu)

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_settings -> {
                        val intent = Intent(this, AccountConfigActivity::class.java)
                        startActivity(intent)
                        true
                    }
                    R.id.action_logout -> {
                        // 1. Cerrar sesión en Firebase
                        FirebaseAuth.getInstance().signOut()
                        
                        // 2. Limpiar preferencia de "Recordar"
                        val prefs = getSharedPreferences("AUTH_PREFS", Context.MODE_PRIVATE)
                        prefs.edit { putBoolean("remember_me", false) }

                        // 3. Volver al Login
                        val intent = Intent(this, MainActivity::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        startActivity(intent)
                        finish()
                        true
                    }
                    R.id.action_exit -> {
                        finishAffinity() // Cierra todas las actividades
                        System.exit(0)
                        true
                    }
                    else -> false
                }
            }
            popup.show()
        }

        // BOTON DEL FILTRE
        btnShowFilter.setOnClickListener {
            val isDatabase = viewPager.currentItem == 1
            val popup = FilterPopup(this, userJaciment, isDatabase) { sector, ueId, tipus, onlyMine ->
                val fragment = when (viewPager.currentItem) {
                    0 -> supportFragmentManager.findFragmentByTag("f0") as? UEFragment
                    1 -> supportFragmentManager.findFragmentByTag("f1") as? DBUEFragment
                    else -> null
                }

                when (fragment) {
                    is UEFragment -> fragment.applyFilters(userJaciment, sector, ueId, tipus, onlyMine)
                    is DBUEFragment -> fragment.applyFilters(userJaciment, sector, ueId, tipus, onlyMine)
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
                        userJaciment = query.documents[0].get("excavacio")?.toString() ?: ""
                    }
                }
        }
    }
}