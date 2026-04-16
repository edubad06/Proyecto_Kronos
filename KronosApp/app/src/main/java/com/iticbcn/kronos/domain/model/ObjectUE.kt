package com.iticbcn.kronos.domain.model

import java.io.Serializable

data class RelacioUE(
    val tipus: String = "",
    val desti: String = ""
) : Serializable

data class ObjecteUE(
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
    val fecha_creacio: Long = System.currentTimeMillis(),
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
