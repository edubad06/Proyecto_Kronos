package com.iticbcn.kronos.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.iticbcn.kronos.domain.model.ObjecteUE
import kotlinx.coroutines.tasks.await

object UERepository {

    // Eliminamos la variable 'db' de aquí para evitar el memory leak

    /**
     * Obtiene todos los IDs de los yacimientos disponibles en Firestore
     */
    suspend fun getJacimentsFromFirestore(): List<String> {
        // Obtenemos la instancia de Firestore localmente dentro de la función
        val db = FirebaseFirestore.getInstance()
        return try {
            val snapshot = db.collection("jaciments").get().await()
            // Extraemos el campo 'id_jaciment'
            snapshot.documents.mapNotNull { it.getString("id_jaciment") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Obtiene los sectores filtrados por el yacimiento seleccionado
     */
    suspend fun getSectoresPorJaciment(codiJaciment: String): List<String> {
        // Obtenemos la instancia de Firestore localmente dentro de la función
        val db = FirebaseFirestore.getInstance()
        return try {
            val snapshot = db.collection("sectors")
                .whereEqualTo("codi_jaciment", codiJaciment)
                .get()
                .await()

            snapshot.documents.mapNotNull { it.getString("codi_sector") }
        } catch (e: Exception) {
            emptyList()
        }
    }
}