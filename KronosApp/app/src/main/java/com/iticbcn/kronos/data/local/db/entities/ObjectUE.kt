package com.iticbcn.kronos.data.local.db.entities

import androidx.room.Entity
import androidx.room.Ignore
import androidx.room.PrimaryKey
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.Exclude
import java.io.Serializable
import java.util.Date

data class RelacioUE(
    var tipus: String = "",
    var desti: String = ""
) : Serializable

@Entity(tableName = "ue_table")
data class ObjecteUE(

    @PrimaryKey
    @DocumentId
    var id: String = "",

    @Ignore
    @get:Exclude
    var firestoreId: String? = null,

    var codi_ue: String = "",
    var codi_sector: String = "",
    var tipus_ue: String = "",
    var descripcio: String = "",
    var registrat_per: String = "",
    var material: String = "",
    var estat_conservacio: String = "",
    var cronologia: String = "",
    var textura: String = "",
    var color: String = "",

    var relacions: List<RelacioUE> = emptyList(),
    var imatges_urls: List<String> = emptyList(),

    var sincronitzat: Boolean = false,
    var data: Date = Date(),
    var jaciment: String = ""

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
