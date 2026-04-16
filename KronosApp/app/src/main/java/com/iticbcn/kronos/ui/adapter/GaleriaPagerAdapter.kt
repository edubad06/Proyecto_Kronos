package com.iticbcn.kronos.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.iticbcn.kronos.ui.ue.DBUEFragment
import com.iticbcn.kronos.ui.ue.UEFragment

class GaleriaPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {
    override fun getItemCount(): Int = 2

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> UEFragment()    // Pestaña Local
            1 -> DBUEFragment()  // Pestaña Base de Datos
            else -> UEFragment()
        }
    }
}