package it.goccia.app.logica

import it.goccia.app.dati.Avviso
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.Distributore
import it.goccia.app.dati.Preferito
import it.goccia.app.dati.Prezzo

/** Il miglior prezzo trovato attorno a un luogo. */
data class Trovato(val distributore: Distributore, val prezzo: Prezzo, val self: Boolean, val distanzaKm: Double)

data class DecisioneSoglia(val notifica: Boolean, val avviso: Avviso)

data class DecisioneCalo(val notifica: Boolean, val caloMillesimi: Int, val preferito: Preferito)

/** Regole degli avvisi, separate dalle notifiche per poterle provare nei test. */
object ValutaAvvisi {

    /** Per gli avvisi contano solo prezzi comunicati da pochi giorni. */
    const val GIORNI_VALIDI = 3

    fun migliore(
        distributori: List<Distributore>,
        carburante: Carburante,
        preferisciSelf: Boolean,
        centro: Coordinate,
        raggioKm: Int,
        escludiAutostrade: Boolean,
        adessoSecondi: Long,
    ): Trovato? = distributori
        .asSequence()
        .filter { !(escludiAutostrade && it.autostradale) }
        .mapNotNull { d ->
            val (prezzo, self) = Convenienza.prezzoPer(d, carburante, preferisciSelf) ?: return@mapNotNull null
            if (Convenienza.giorniDa(prezzo.comunicato, adessoSecondi) > GIORNI_VALIDI) return@mapNotNull null
            val km = Geo.distanzaKm(centro.lat, centro.lon, d.lat, d.lon)
            if (km > raggioKm) null else Trovato(d, prezzo, self, km)
        }
        .minWithOrNull(compareBy<Trovato> { it.prezzo.millesimi }.thenBy { it.distanzaKm })

    /**
     * Avviso a soglia: notifica quando si scende sotto soglia, e di nuovo solo se il prezzo
     * scende ancora. Quando si torna sopra soglia l'avviso si "riarma".
     */
    fun decidiSoglia(avviso: Avviso, trovato: Trovato?, estrazione: String, adessoMillis: Long): DecisioneSoglia {
        val base = avviso.copy(ultimoControlloIl = adessoMillis, minimoVisto = trovato?.prezzo?.millesimi, ultimaEstrazione = estrazione)
        if (trovato == null || trovato.prezzo.millesimi > avviso.soglia) {
            return DecisioneSoglia(false, base.copy(ultimoMinimo = null))
        }
        val minimo = trovato.prezzo.millesimi
        val gia = avviso.ultimoMinimo
        return if (gia == null || minimo < gia) {
            val esito = "${trovato.distributore.intestazione} a ${Formati.prezzo(minimo)}"
            DecisioneSoglia(true, base.copy(ultimoMinimo = minimo, ultimaNotificaIl = adessoMillis, ultimoEsito = esito))
        } else {
            DecisioneSoglia(false, base)
        }
    }

    /**
     * Calo di un preferito: confronta il prezzo di oggi con l'ultimo controllato per lo stesso carburante.
     * La prima volta (o se cambia il carburante) salva solo il riferimento.
     */
    fun decidiCalo(preferito: Preferito, prezzoOggi: Int?, carburante: Carburante, estrazione: String): DecisioneCalo {
        if (prezzoOggi == null) return DecisioneCalo(false, 0, preferito)
        if (preferito.ultimaEstrazione == estrazione && preferito.ultimoCarburante == carburante.codice) {
            return DecisioneCalo(false, 0, preferito)
        }
        val aggiornato = preferito.copy(ultimoPrezzo = prezzoOggi, ultimoCarburante = carburante.codice, ultimaEstrazione = estrazione)
        val prima = preferito.ultimoPrezzo
        if (prima == null || preferito.ultimoCarburante != carburante.codice) {
            return DecisioneCalo(false, 0, aggiornato)
        }
        val calo = prima - prezzoOggi
        return DecisioneCalo(calo > 0, calo.coerceAtLeast(0), aggiornato)
    }

    /** Orario silenzioso: niente notifiche dalle 21 alle 7. */
    fun silenzioso(ora: Int): Boolean = ora >= 21 || ora < 7
}
