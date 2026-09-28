package it.goccia.app.logica

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Numeri e date all'italiana, senza dipendere dalla lingua del telefono. */
object Formati {
    private val italiano: Locale = Locale.ITALY

    private val mesiBrevi = listOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")
    private val mesiLunghi = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )

    /** 1689 -> "1,689" */
    fun prezzo(millesimi: Int): String = String.format(italiano, "%.3f", millesimi / 1000.0)

    /** 1234.5 -> "1.234,50" */
    fun numero(valore: Double, decimali: Int = 2): String {
        val negativo = valore < 0
        val fattore = Math.pow(10.0, decimali.toDouble())
        val totale = (abs(valore) * fattore).roundToLong()
        val intero = totale / fattore.toLong()
        val parteDecimale = totale % fattore.toLong()
        val interoConPunti = intero.toString().reversed().chunked(3).joinToString(".").reversed()
        val testo = if (decimali > 0) "$interoConPunti,${parteDecimale.toString().padStart(decimali, '0')}" else interoConPunti
        return if (negativo && totale != 0L) "−$testo" else testo
    }

    fun euro(valore: Double): String = numero(valore, 2) + " €"

    fun decimale(valore: Double, cifre: Int = 1): String = numero(valore, cifre)

    fun km(valore: Double): String = when {
        valore < 10 -> numero(valore, 1) + " km"
        else -> numero(valore, 0) + " km"
    }

    fun litri(valore: Double, unita: String = "l"): String = numero(valore, if (valore < 100) 1 else 0) + " " + unita

    /** 0.46 -> "46%" */
    fun percento(livello: Double): String = "${(livello * 100).roundToInt()}%"

    /** 150.0 -> "150 kW", 7.4 -> "7,4 kW" */
    fun kw(valore: Double): String = (if (valore % 1.0 == 0.0 || valore >= 50) numero(valore, 0) else numero(valore, 1)) + " kW"

    /** 0.59 -> "0,59 €/kWh" (tre cifre per le tariffe di casa, che cambiano di poco) */
    fun euroKwh(valore: Double, cifre: Int = 2): String = numero(valore, cifre) + " €/kWh"

    /** 22 -> "22 min", 260 -> "4 h 20 min", 120 -> "2 h" */
    fun durata(minuti: Int): String = when {
        minuti < 60 -> "$minuti min"
        minuti % 60 == 0 -> "${minuti / 60} h"
        else -> "${minuti / 60} h ${minuti % 60} min"
    }

    /** "−5 cent vs media", "+6 cent vs media", "Nella media · −1 cent" */
    fun differenza(cent: Int?): String = when {
        cent == null -> "Media non disponibile"
        cent <= -3 -> "−${abs(cent)} cent vs media"
        cent >= 3 -> "+$cent cent vs media"
        cent == 0 -> "Nella media"
        cent < 0 -> "Nella media · −${abs(cent)} cent"
        else -> "Nella media · +$cent cent"
    }

    /** Variazione in centesimi con il segno: -12 millesimi -> "−1,2 cent" */
    fun variazioneCent(millesimi: Int): String {
        val cent = millesimi / 10.0
        val testo = numero(abs(cent), if (abs(millesimi) % 10 == 0) 0 else 1)
        return when {
            millesimi < 0 -> "−$testo cent"
            millesimi > 0 -> "+$testo cent"
            else -> "0 cent"
        }
    }

    fun giorniFa(epochSecondi: Long, adessoMillis: Long): Long {
        val giorno = Instant.ofEpochSecond(epochSecondi).atZone(FUSO_ITALIA).toLocalDate()
        val oggi = Instant.ofEpochMilli(adessoMillis).atZone(FUSO_ITALIA).toLocalDate()
        return ChronoUnit.DAYS.between(giorno, oggi)
    }

    /** "aggiornato oggi", "aggiornato ieri", "3 giorni fa" rispetto a oggi in Italia. */
    fun aggiornamento(epochSecondi: Long, adessoMillis: Long): String {
        if (epochSecondi <= 0) return "data sconosciuta"
        val giorni = giorniFa(epochSecondi, adessoMillis)
        return when {
            giorni <= 0 -> "aggiornato oggi"
            giorni == 1L -> "aggiornato ieri"
            else -> "$giorni giorni fa"
        }
    }

    /** "oggi alle 07:42", "ieri alle 18:00", "24 set alle 09:10" */
    fun quando(epochSecondi: Long, adessoMillis: Long): String {
        if (epochSecondi <= 0) return "data sconosciuta"
        val z = Instant.ofEpochSecond(epochSecondi).atZone(FUSO_ITALIA)
        val ora = String.format(italiano, "%02d:%02d", z.hour, z.minute)
        return when (giorniFa(epochSecondi, adessoMillis)) {
            0L -> "oggi alle $ora"
            1L -> "ieri alle $ora"
            else -> "${z.dayOfMonth} ${mesiBrevi[z.monthValue - 1].lowercase()} alle $ora"
        }
    }

    fun quandoMillis(epochMillis: Long, adessoMillis: Long): String = quando(epochMillis / 1000, adessoMillis)

    fun ora(epochMillis: Long): String {
        val z = Instant.ofEpochMilli(epochMillis).atZone(FUSO_ITALIA)
        return String.format(italiano, "%02d:%02d", z.hour, z.minute)
    }

    /** "27 set 2026" */
    fun data(epochMillis: Long): String {
        val z = Instant.ofEpochMilli(epochMillis).atZone(FUSO_ITALIA)
        return "${z.dayOfMonth} ${mesiBrevi[z.monthValue - 1].lowercase()} ${z.year}"
    }

    /** "2026-09-27" -> "27 set" */
    fun dataIso(iso: String): String = try {
        val d = LocalDate.parse(iso)
        "${d.dayOfMonth} ${mesiBrevi[d.monthValue - 1].lowercase()}"
    } catch (e: Exception) {
        iso
    }

    /** "2026-09" -> "Set 2026" */
    fun meseIso(iso: String): String = try {
        val m = YearMonth.parse(iso)
        "${mesiBrevi[m.monthValue - 1]} ${m.year}"
    } catch (e: Exception) {
        iso
    }

    fun meseBreve(mese: YearMonth): String = mesiBrevi[mese.monthValue - 1]

    fun meseLungo(mese: YearMonth): String = mesiLunghi[mese.monthValue - 1]

    /** Legge un prezzo scritto dall'utente: "1,689", "1.689", "1689" -> millesimi */
    fun leggiPrezzo(testo: String): Int? {
        val pulito = testo.trim().replace(',', '.')
        if (pulito.isEmpty()) return null
        val valore = pulito.toDoubleOrNull() ?: return null
        val euro = if (valore >= 100) valore / 1000 else valore
        return (euro * 1000).roundToInt().takeIf { it in 100..9_999 }
    }

    /** Legge un numero decimale scritto all'italiana: "37,5" -> 37.5, "1.250,00" -> 1250.0, "37.5" -> 37.5 */
    fun leggiNumero(testo: String): Double? {
        val t = testo.trim().replace(" ", "")
        if (t.isEmpty()) return null
        val normalizzato = when {
            t.contains(',') -> t.replace(".", "").replace(',', '.')
            t.count { it == '.' } > 1 -> t.replace(".", "")
            Regex("^\\d{1,3}\\.\\d{3}$").matches(t) -> t.replace(".", "")
            else -> t
        }
        return normalizzato.toDoubleOrNull()
    }

    /** "84.312" -> 84312 */
    fun leggiIntero(testo: String): Int? = testo.trim().replace(".", "").replace(" ", "").toIntOrNull()
}
