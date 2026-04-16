package com.iticbcn.kronos.data.local

import android.content.Context
import com.iticbcn.kronos.domain.model.ObjecteUE
import com.iticbcn.kronos.data.repository.UERepository
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

object DataManager {
    private const val PREFS_NAME = "UE_DATA"
    private const val KEY_LIST_LOCAL = "ue_list_local"
    private const val KEY_LIST_DB = "ue_list_db"
    private const val KEY_JACIMENTS = "jaciments_list"

    fun getJaciments(context: Context): MutableList<String> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_JACIMENTS, null)
        return if (json == null) {
            mutableListOf("Carrer del Francolí, 65", "Pedralbes", "Tarraco")
        } else {
            Gson().fromJson(json, object : TypeToken<MutableList<String>>() {}.type)
        }
    }

    fun getSectorsByJaciment(context: Context, jacimentNom: String): List<String> {
        val mapa = UERepository.getSectoresPorJaciment()
        return mapa[jacimentNom] ?: emptyList()
    }

    fun existsUE(context: Context, codi_ue: String, jaciment: String): Boolean {
        return getUEListLocal(context).any { it.codi_ue == codi_ue && it.jaciment == jaciment } ||
               getUEListDB(context).any { it.codi_ue == codi_ue && it.jaciment == jaciment }
    }

    fun saveUE(context: Context, objecte: ObjecteUE) {
        val currentList = getUEListLocal(context).toMutableList()
        val index = currentList.indexOfFirst { it.codi_ue == objecte.codi_ue && it.jaciment == objecte.jaciment }
        if (index != -1) currentList[index] = objecte else currentList.add(objecte)

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(currentList)
        prefs.edit().putString(KEY_LIST_LOCAL, json).apply()
    }

    fun getUEListLocal(context: Context): List<ObjecteUE> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_LIST_LOCAL, null)
        return if (json == null) {
            val ejemplos = UERepository.getEjemplosIniciales()
            saveFullList(context, ejemplos, KEY_LIST_LOCAL)
            ejemplos
        } else {
            val type = object : TypeToken<List<ObjecteUE>>() {}.type
            Gson().fromJson(json, type)
        }
    }

    fun getUEListDB(context: Context): List<ObjecteUE> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = prefs.getString(KEY_LIST_DB, null)
        return if (json == null) {
            emptyList()
        } else {
            val type = object : TypeToken<List<ObjecteUE>>() {}.type
            Gson().fromJson(json, type)
        }
    }

    private fun saveFullList(context: Context, list: List<ObjecteUE>, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = Gson().toJson(list)
        prefs.edit().putString(key, json).apply()
    }

    fun deleteUE(context: Context, codi_ue: String, jaciment: String) {
        val currentLocal = getUEListLocal(context).toMutableList()
        if (currentLocal.removeAll { it.codi_ue == codi_ue && it.jaciment == jaciment }) {
            saveFullList(context, currentLocal, KEY_LIST_LOCAL)
        }
        
        val currentDB = getUEListDB(context).toMutableList()
        if (currentDB.removeAll { it.codi_ue == codi_ue && it.jaciment == jaciment }) {
            saveFullList(context, currentDB, KEY_LIST_DB)
        }
    }
}