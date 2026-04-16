package com.iticbcn.kronos.data.repository
import com.iticbcn.kronos.domain.model.ObjecteUE

object UERepository {

    fun getEjemplosIniciales(): List<ObjecteUE> {
        return listOf(
            ObjecteUE(
                jaciment = "Carrer del Francolí, 65",
                codi_ue = "1001",
                tipus_ue = "Estrat",
                codi_sector = "Sector 1",
                imatges_urls = emptyList(),
                cronologia = "Romana",
                descripcio = "Sedimento arcilloso con restos cerámicos",
                textura = "Argilosa",
                color = "Marró",
                material = "Pedra",
                estat_conservacio = "Bo",
                fecha_creacio = System.currentTimeMillis()
            ),
            ObjecteUE(
                jaciment = "Pedralbes",
                codi_ue = "2005",
                tipus_ue = "Estructura",
                codi_sector = "Claustre",
                imatges_urls = emptyList(),
                cronologia = "Gòtica",
                descripcio = "Pedres irregulars amb morter de calç",
                textura = "Pedregosa",
                color = "Gris",
                material = "Morter",
                estat_conservacio = "Regular",
                fecha_creacio = System.currentTimeMillis()
            )
        )
    }

    fun getSectoresPorJaciment(): Map<String, List<String>> {
        return mapOf(
            "Carrer del Francolí, 65" to listOf("Sector 1", "Sector 2", "Sector 3"),
            "Pedralbes" to listOf("Claustre", "Jardins", "Cripta"),
            "Tarraco" to listOf("Fòrum", "Amfiteatre", "Muralla")
        )
    }
}
