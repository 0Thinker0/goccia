package it.goccia.app.logica

import it.goccia.app.dati.Comune
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.Provincia
import java.text.Normalizer
import kotlin.math.cos

object Territorio {

    /**
     * Province da scaricare per mostrare i distributori entro [km] da un punto.
     * Con l'elenco dei comuni e preciso (i comuni con distributori entro [km]); senza, usiamo
     * i riquadri delle province e teniamo le piu vicine.
     */
    fun provinceAttorno(province: List<Provincia>, comuni: List<Comune>, punto: Coordinate, km: Double): List<String> {
        if (comuni.isNotEmpty()) {
            val vicine = comuni
                .map { it to Geo.distanzaKm(punto, it.coordinate) }
                .filter { it.second <= km }
                .sortedBy { it.second }
                .map { it.first.provincia }
                .distinct()
            if (vicine.isNotEmpty()) return vicine.take(4)
        }
        val dLat = km / 111.0
        val dLon = km / (111.0 * cos(Math.toRadians(punto.lat)).coerceAtLeast(0.2))
        val candidate = province.filter { p ->
            punto.lat >= p.minLat - dLat && punto.lat <= p.maxLat + dLat &&
                punto.lon >= p.minLon - dLon && punto.lon <= p.maxLon + dLon
        }
        val scelte = candidate.ifEmpty { province }
            .sortedBy { Geo.distanzaKm(punto.lat, punto.lon, it.centroLat, it.centroLon) }
        return scelte.take(if (candidate.isEmpty()) 1 else 3).map { it.sigla }
    }

    /** La provincia "di" un punto: quella del comune piu vicino. */
    fun provinciaDi(province: List<Provincia>, comuni: List<Comune>, punto: Coordinate): Provincia? {
        val sigla = comunePiuVicino(comuni, punto)?.provincia
            ?: provinceAttorno(province, emptyList(), punto, 1.0).firstOrNull()
        return province.firstOrNull { it.sigla == sigla }
    }

    fun comunePiuVicino(comuni: List<Comune>, punto: Coordinate): Comune? =
        comuni.minByOrNull { Geo.distanzaKm(punto, it.coordinate) }

    /** minuscolo, senza accenti e apostrofi: "Reggio nell'Emilia" -> "reggio nell emilia" */
    fun normalizza(testo: String): String {
        val senzaAccenti = Normalizer.normalize(testo.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return senzaAccenti.replace(Regex("[^a-z0-9]+"), " ").trim()
    }

    private val paroleVuote = setOf("di", "del", "della", "dei", "degli", "delle", "nel", "nell", "nella", "sul", "sull", "in", "d", "l", "e")

    /**
     * Cerca un comune: ogni parola scritta deve essere l'inizio di una parola del nome,
     * cosi "reggio emilia" trova "Reggio nell'Emilia". Prima quelli che iniziano con il testo,
     * poi i piu grandi (piu distributori).
     */
    fun cerca(comuni: List<Comune>, testo: String, massimo: Int = 8): List<Comune> {
        val parole = normalizza(testo).split(' ').filter { it.isNotEmpty() && it !in paroleVuote }
        if (parole.isEmpty()) return emptyList()
        val sigla = parole.singleOrNull()?.takeIf { it.length == 2 }?.uppercase()
        return comuni
            .asSequence()
            .mapNotNull { c ->
                val nome = normalizza(c.nome)
                val paroleNome = nome.split(' ')
                val trovato = parole.all { p -> paroleNome.any { it.startsWith(p) } }
                when {
                    trovato -> c to (if (nome.startsWith(parole.joinToString(" "))) 0 else 1)
                    sigla != null && c.provincia == sigla -> c to 2
                    else -> null
                }
            }
            .sortedWith(compareBy<Pair<Comune, Int>> { it.second }.thenByDescending { it.first.distributori }.thenBy { it.first.nome })
            .take(massimo)
            .map { it.first }
            .toList()
    }
}
