package it.goccia.app.logica

import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Distributore
import it.goccia.app.dati.Prezzo
import it.goccia.app.dati.PrezziCarburante
import it.goccia.app.dati.Speciale

/** Un prezzo in vigore letto da Osservaprezzi in tempo reale. [comunicato] in secondi. */
data class PrezzoLive(val nome: String, val self: Boolean, val millesimi: Int, val comunicato: Long)

/**
 * I prezzi in vigore di un distributore, letti da Osservaprezzi alle [letto] (secondi).
 * [dettagliato]: dalla scheda del distributore, con la data di ogni prezzo; la ricerca per zona
 * da invece una data sola, quella dell'ultima comunicazione.
 */
data class ImpiantoLive(val id: Long, val prezzi: List<PrezzoLive>, val letto: Long, val dettagliato: Boolean = false)

/**
 * I prezzi del file del mattino sono quelli in vigore alle 8 del giorno prima. Osservaprezzi
 * (l'API pubblica del Ministero) da quelli in vigore adesso: li sovrapponiamo a quelli del
 * mattino, distributore per distributore, quando sono piu recenti e plausibili.
 */
object TempoReale {
    // stessi limiti della pipeline (carburanti.js): fuori da qui e un errore di battitura
    private val limiti = mapOf(
        Carburante.BENZINA to 900..3900,
        Carburante.GASOLIO to 900..3900,
        Carburante.GPL to 300..1600,
        Carburante.METANO to 500..3900,
    )
    private val limitiAltro = 300..5000

    /** "Benzina", "Gasolio", "GPL", "Metano": i carburanti base. Gli altri sono speciali. */
    fun famigliaBase(nome: String): Carburante? = when (nome.trim().lowercase()) {
        "benzina" -> Carburante.BENZINA
        "gasolio" -> Carburante.GASOLIO
        "gpl" -> Carburante.GPL
        "metano" -> Carburante.METANO
        else -> null
    }

    /** Famiglia di un carburante speciale (Blue Diesel, HVO, V-Power...), se si capisce. */
    fun famigliaSpeciale(nome: String): Carburante? {
        val d = nome.lowercase()
        return when {
            Regex("diesel|gasolio|hvo").containsMatchIn(d) -> Carburante.GASOLIO
            Regex("benzina|super|ottani|v.?power|perform|wr ?100|f101|plus 98|excellium").containsMatchIn(d) -> Carburante.BENZINA
            Regex("l-gnc|metano|gnc").containsMatchIn(d) -> Carburante.METANO
            Regex("gpl").containsMatchIn(d) -> Carburante.GPL
            else -> null
        }
    }

    fun plausibile(famiglia: Carburante?, millesimi: Int): Boolean = millesimi in (famiglia?.let { limiti[it] } ?: limitiAltro)

    /**
     * Un salto enorme rispetto al prezzo recente dello stesso distributore e quasi sempre un
     * errore di battitura del gestore (come nella pipeline: tra -20% e +30%).
     */
    private fun salto(nuovo: Int, prima: Prezzo?, adessoSecondi: Long): Boolean {
        if (prima == null || Convenienza.giorniDa(prima.comunicato, adessoSecondi) > GIORNI_VECCHIO) return false
        val rapporto = nuovo.toDouble() / prima.millesimi
        return rapporto < 0.8 || rapporto > 1.3
    }

    /** Il distributore con i prezzi in tempo reale al posto di quelli piu vecchi. */
    fun applica(d: Distributore, live: ImpiantoLive): Distributore {
        if (live.id != d.id) return d
        val prezzi = d.prezzi.toMutableMap()
        val speciali = d.speciali.toMutableList()
        var cambiato = false
        for (p in live.prezzi) {
            val famiglia = famigliaBase(p.nome)
            if (famiglia != null) {
                if (!plausibile(famiglia, p.millesimi)) continue
                val attuali = prezzi[famiglia] ?: PrezziCarburante(null, null)
                val self = p.self
                val prima = if (self) attuali.self else attuali.servito
                if (prima != null && p.comunicato < prima.comunicato) continue
                if (salto(p.millesimi, prima, live.letto)) continue
                val nuovo = Prezzo(p.millesimi, p.comunicato)
                if (nuovo == prima) continue
                prezzi[famiglia] = if (self) attuali.copy(self = nuovo) else attuali.copy(servito = nuovo)
                cambiato = true
            } else {
                val fs = famigliaSpeciale(p.nome)
                if (!plausibile(fs, p.millesimi)) continue
                val i = speciali.indexOfFirst { it.nome.equals(p.nome.trim(), ignoreCase = true) && it.self == p.self }
                val nuovo = Prezzo(p.millesimi, p.comunicato)
                if (i >= 0) {
                    val prima = speciali[i]
                    if (p.comunicato < prima.prezzo.comunicato || prima.prezzo == nuovo) continue
                    speciali[i] = prima.copy(prezzo = nuovo)
                } else {
                    speciali += Speciale(p.nome.trim(), fs, nuovo, p.self)
                }
                cambiato = true
            }
        }
        return if (cambiato) d.copy(prezzi = prezzi, speciali = speciali) else d
    }

    fun applica(distributori: List<Distributore>, live: Map<Long, ImpiantoLive>): List<Distributore> =
        if (live.isEmpty()) distributori else distributori.map { d -> live[d.id]?.let { applica(d, it) } ?: d }

    /**
     * Unisce due letture: per ogni prezzo vince la comunicazione piu recente. La scheda conosce la
     * data vera di ogni prezzo: se una ricerca successiva da lo stesso prezzo, resta quella data.
     */
    fun unisci(prima: ImpiantoLive?, dopo: ImpiantoLive): ImpiantoLive {
        if (prima == null || dopo.dettagliato) return dopo
        val chiave = { p: PrezzoLive -> p.nome.trim().lowercase() to p.self }
        val vecchi = prima.prezzi.associateBy(chiave)
        val nuovi = dopo.prezzi.map { p ->
            val v = vecchi[chiave(p)]
            if (prima.dettagliato && v != null && v.millesimi == p.millesimi) v else p
        }
        val tutti = (prima.prezzi + nuovi).groupBy(chiave).map { (_, lista) -> lista.maxBy { it.comunicato } }
        return ImpiantoLive(dopo.id, tutti, maxOf(prima.letto, dopo.letto), prima.dettagliato)
    }

    /** L'ultimo prezzo comunicato dal distributore, per qualsiasi carburante (secondi, 0 se nessuno). */
    fun ultimaComunicazione(d: Distributore): Long =
        maxOf(
            d.prezzi.values.maxOfOrNull { maxOf(it.self?.comunicato ?: 0, it.servito?.comunicato ?: 0) } ?: 0,
            d.speciali.maxOfOrNull { it.prezzo.comunicato } ?: 0,
        )
}
