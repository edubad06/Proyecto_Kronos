package com.iticbcn.kronos.domain.model

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import java.io.Serializable
import java.util.Date

data class RelacioUE(
    val tipus: String = "",
    val desti: String = ""
) : Serializable

data class ObjecteUE(
    @DocumentId
    val id: String? = null, // ✅ Recoge la ID de los metadatos (no crea campo)
    
    @get:Exclude
    val firestoreId: String? = null, // ✅ Evita el error 'firestoreId was found from document'
    
    val codi_ue: String = "",
    val codi_sector: String = "",
    val tipus_ue: String = "",
    val descripcio: String = "",
    val registrat_per: String = "",
    val material: String = "",
    val estat_conservacio: String = "",
    val cronologia: String = "",
    val textura: String = "",
    val color: String = "",
    val relacions: List<RelacioUE> = emptyList(),
    val imatges_urls: List<String> = emptyList(),
    val sincronitzat: Boolean = false,
    val data: Date = Date(), // ✅ Cambiado de fecha_creacio a 'data' para compatibilidad Web
    val jaciment: String = ""
) : Serializable

enum class TipusUEOptions(val nom: String) {
    INTERSTRAT("Interstrat"),
    ESTRAT("Estrat"),
    ESTRUCTURA("Estructura"),
    RETALL("Retall");

    companion object {
        fun getNames(): List<String> = values().map { it.nom }
    }
}
