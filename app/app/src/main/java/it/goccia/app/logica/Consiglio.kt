package it.goccia.app.logica

import it.goccia.app.dati.Auto
import it.goccia.app.dati.Rifornimento
import java.time.DayOfWeek
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

data class StatoSerbatoio(
    /** livello stimato oggi, da 0 a 1 */
    val livello: Double,
    val autonomiaKm: Int,
    /** giorni di tragitti prima della riserva */
    val giorniRimasti: Int,
    val kmGiornalieri: Double,
    /** true se i km al giorno vengono dai rifornimenti registrati, false se sono una stima standard */
    val kmDaiDati: Boolean,
)

enum class Urgenza { RISERVA, OGGI, PRESTO, TRANQUILLO }

enum class Tendenza { IN_CALO, STABILE, IN_SALITA }

data class ConsiglioPieno(val urgenza: Urgenza, val titolo: String, val testo: String)

object Consiglio {
    /** Un automobilista italiano fa in media poco piu di 30 km al giorno. */
    const val KM_GIORNALIERI_PREDEFINITI = 35.0

    /** Sotto questo livello consideriamo l'auto in riserva. */
    const val RISERVA = 0.1

    private const val GIORNO_MS = 86_400_000.0

    /** Km percorsi in media al giorno, dai rifornimenti con il contachilometri (servono almeno due settimane). */
    fun kmGiornalieri(rifornimenti: List<Rifornimento>, autoId: String?): Double? {
        val conKm = rifornimenti
            .filter { it.km != null && (autoId == null || it.autoId == null || it.autoId == autoId) }
            .sortedBy { it.quando }
        if (conKm.size < 2) return null
        val primo = conKm.first()
        val ultimo = conKm.last()
        val giorni = (ultimo.quando - primo.quando) / GIORNO_MS
        val km = ((ultimo.km ?: 0) - (primo.km ?: 0)).toDouble()
        if (giorni < 14 || km <= 0) return null
        return (km / giorni).coerceIn(2.0, 400.0)
    }

    /** Livello di oggi: parte da quello indicato dall'utente e toglie i km fatti in media da allora. */
    fun serbatoio(auto: Auto, rifornimenti: List<Rifornimento>, adessoMillis: Long): StatoSerbatoio {
        val daiDati = kmGiornalieri(rifornimenti, auto.id)
        val kmGiorno = daiDati ?: KM_GIORNALIERI_PREDEFINITI
        val livello = if (auto.livelloIl <= 0 || auto.capienza <= 0 || auto.consumo <= 0) {
            auto.livello
        } else {
            val giorni = ((adessoMillis - auto.livelloIl) / GIORNO_MS).coerceAtLeast(0.0)
            val consumato = giorni * kmGiorno * auto.consumo / 100.0
            (auto.livello - consumato / auto.capienza).coerceIn(0.02, 1.0)
        }
        val autonomia = if (auto.consumo > 0) auto.capienza * livello / auto.consumo * 100 else 0.0
        val utile = if (auto.consumo > 0) auto.capienza * (livello - RISERVA).coerceAtLeast(0.0) / auto.consumo * 100 else 0.0
        return StatoSerbatoio(
            livello = livello,
            autonomiaKm = autonomia.roundToInt(),
            giorniRimasti = floor(utile / kmGiorno).toInt(),
            kmGiornalieri = kmGiorno,
            kmDaiDati = daiDati != null,
        )
    }

    /** "1/4", "1/2"... per il badge del serbatoio */
    fun quarti(livello: Double): String = when {
        livello < 0.125 -> "Riserva"
        livello < 0.375 -> "1/4"
        livello < 0.625 -> "1/2"
        livello < 0.875 -> "3/4"
        else -> "Pieno"
    }

    /**
     * Come si stanno muovendo i prezzi: confronta l'ultimo valore con quello di [giorni] giorni prima.
     * Restituisce la tendenza e la variazione in millesimi.
     */
    fun tendenza(serie: List<Int?>, giorni: Int = 7): Pair<Tendenza, Int>? {
        val valori = serie.mapIndexedNotNull { i, v -> v?.let { i to it } }
        if (valori.size < 2) return null
        val (ultimoIndice, ultimo) = valori.last()
        val inizio = valori.lastOrNull { it.first <= ultimoIndice - giorni } ?: valori.first()
        if (inizio.first == ultimoIndice) return null
        val delta = ultimo - inizio.second
        val t = when {
            delta <= -10 -> Tendenza.IN_CALO
            delta >= 10 -> Tendenza.IN_SALITA
            else -> Tendenza.STABILE
        }
        return t to delta
    }

    private fun nomeGiorno(giorno: DayOfWeek): String = when (giorno) {
        DayOfWeek.MONDAY -> "lunedì"
        DayOfWeek.TUESDAY -> "martedì"
        DayOfWeek.WEDNESDAY -> "mercoledì"
        DayOfWeek.THURSDAY -> "giovedì"
        DayOfWeek.FRIDAY -> "venerdì"
        DayOfWeek.SATURDAY -> "sabato"
        DayOfWeek.SUNDAY -> "domenica"
    }

    /** Il suggerimento della Home: quando fare il pieno e dove. */
    fun consiglio(serbatoio: StatoSerbatoio, migliore: Offerta?, tendenza: Tendenza?, oggi: LocalDate): ConsiglioPieno {
        val giorni = serbatoio.giorniRimasti
        val (urgenza, titolo) = when {
            serbatoio.livello <= RISERVA -> Urgenza.RISERVA to "Sei in riserva: fai il pieno oggi"
            giorni <= 0 -> Urgenza.OGGI to "Fai il pieno oggi"
            giorni == 1 -> Urgenza.OGGI to "Fai il pieno entro domani"
            giorni <= 6 -> Urgenza.PRESTO to "Fai il pieno entro ${nomeGiorno(oggi.plusDays(giorni.toLong()).dayOfWeek)}"
            else -> Urgenza.TRANQUILLO to "Serbatoio a posto"
        }
        val frasi = mutableListOf<String>()
        when {
            giorni in 2..30 -> frasi += "Ti restano circa $giorni giorni di tragitti."
            giorni > 30 -> frasi += "Hai autonomia per più di un mese di tragitti."
        }
        if (migliore != null) {
            val d = migliore.distributore
            val dove = if (d.indirizzo.isNotBlank()) "${d.intestazione} in ${d.indirizzo}" else d.intestazione
            val cent = migliore.differenzaCent
            frasi += if (cent != null && cent <= -2) {
                "$dove costa ${abs(cent)} cent meno della media, a ${Formati.km(migliore.distanzaKm)}."
            } else {
                "Il più conveniente vicino a te è $dove, a ${Formati.km(migliore.distanzaKm)}."
            }
        }
        if (urgenza == Urgenza.PRESTO || urgenza == Urgenza.TRANQUILLO) {
            when (tendenza) {
                Tendenza.IN_CALO -> frasi += "In zona i prezzi stanno scendendo: se puoi, aspetta qualche giorno."
                Tendenza.IN_SALITA -> frasi += "In zona i prezzi stanno salendo: meglio non aspettare troppo."
                else -> {}
            }
        }
        return ConsiglioPieno(urgenza, titolo, frasi.joinToString(" "))
    }
}
