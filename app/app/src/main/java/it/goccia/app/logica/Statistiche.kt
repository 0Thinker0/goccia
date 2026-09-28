package it.goccia.app.logica

import it.goccia.app.dati.Rifornimento
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

val FUSO_ITALIA: ZoneId = ZoneId.of("Europe/Rome")

fun meseDi(epochMillis: Long): YearMonth = YearMonth.from(Instant.ofEpochMilli(epochMillis).atZone(FUSO_ITALIA))

data class RiepilogoRisparmio(
    val meseCorrente: Double,
    val totale: Double,
    /** ultimi 6 mesi, dal piu vecchio al corrente */
    val perMese: List<Pair<YearMonth, Double>>,
)

data class RiepilogoConsumi(
    /** per 100 km, null se non ci sono abbastanza pieni con i km */
    val consumo: Double?,
    val spesaMese: Double,
    val rifornimentiMese: Int,
    /** euro per km, null se non calcolabile */
    val costoKm: Double?,
)

object Statistiche {

    private fun ultimiMesi(adessoMillis: Long, quanti: Int): List<YearMonth> {
        val corrente = meseDi(adessoMillis)
        return (quanti - 1 downTo 0).map { corrente.minusMonths(it.toLong()) }
    }

    fun risparmio(rifornimenti: List<Rifornimento>, adessoMillis: Long): RiepilogoRisparmio {
        val perMese = ultimiMesi(adessoMillis, 6).map { mese ->
            mese to rifornimenti.filter { meseDi(it.quando) == mese }.sumOf { it.risparmio ?: 0.0 }
        }
        return RiepilogoRisparmio(
            meseCorrente = perMese.last().second,
            totale = rifornimenti.sumOf { it.risparmio ?: 0.0 },
            perMese = perMese,
        )
    }

    fun spesaPerMese(rifornimenti: List<Rifornimento>, adessoMillis: Long, mesi: Int = 6): List<Pair<YearMonth, Double>> =
        ultimiMesi(adessoMillis, mesi).map { mese ->
            mese to rifornimenti.filter { meseDi(it.quando) == mese }.sumOf { it.importo }
        }

    /**
     * Consumo reale dai pieni: tra due pieni con i km segnati, i litri messi dopo il primo
     * (compresi eventuali rifornimenti parziali) diviso i km percorsi.
     */
    fun consumo(rifornimenti: List<Rifornimento>): Double? {
        val conKm = rifornimenti.filter { it.km != null }.sortedBy { it.km }
        val pieni = conKm.filter { it.pieno }
        if (pieni.size < 2) return null
        var litri = 0.0
        var km = 0
        for (i in 1 until pieni.size) {
            val da = pieni[i - 1].km ?: continue
            val a = pieni[i].km ?: continue
            val tratto = a - da
            if (tratto < 30) continue
            litri += conKm.filter { (it.km ?: 0) > da && (it.km ?: 0) <= a }.sumOf { it.litri }
            km += tratto
        }
        return if (km > 0) litri / km * 100 else null
    }

    /** Km percorsi nel mese (dal contachilometri), se ci sono almeno due letture. */
    fun kmNelMese(rifornimenti: List<Rifornimento>, mese: YearMonth): Int? {
        val letture = rifornimenti.filter { it.km != null && meseDi(it.quando) == mese }.mapNotNull { it.km }
        if (letture.size < 2) return null
        return (letture.max() - letture.min()).takeIf { it > 0 }
    }

    fun consumi(rifornimenti: List<Rifornimento>, adessoMillis: Long, consumoDichiarato: Double?): RiepilogoConsumi {
        val corrente = meseDi(adessoMillis)
        val delMese = rifornimenti.filter { meseDi(it.quando) == corrente }
        val consumo = consumo(rifornimenti)
        val prezzoMedio = rifornimenti.sortedBy { it.quando }.takeLast(5).takeIf { it.isNotEmpty() }
            ?.map { it.prezzo / 1000.0 }?.average()
        val costoKm = prezzoMedio?.let { p -> (consumo ?: consumoDichiarato)?.let { it * p / 100 } }
        return RiepilogoConsumi(
            consumo = consumo,
            spesaMese = delMese.sumOf { it.importo },
            rifornimentiMese = delMese.size,
            costoKm = costoKm,
        )
    }
}
