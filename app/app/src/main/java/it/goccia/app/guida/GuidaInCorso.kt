package it.goccia.app.guida

import android.util.Log
import it.goccia.app.logica.Energia
import it.goccia.app.logica.Guida
import it.goccia.app.logica.PercorsoGuida
import it.goccia.app.logica.SituazioneGuida
import it.goccia.app.logica.SulPercorso
import it.goccia.app.logica.VoceGuida
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Quello che la schermata della modalita autostrada mostra. */
data class StatoGuida(
    val destinazione: String,
    /** "Gasolio self", "Colonnine veloci" */
    val etichetta: String,
    val elettrica: Boolean,
    /** null finche non arriva la prima posizione */
    val situazione: SituazioneGuida?,
    /** avviso 10 km prima della sosta consigliata */
    val avviso: Boolean,
    /** sei fermo a questa stazione da un po': hai fatto rifornimento? (id, nome) */
    val domanda: Pair<String, String>?,
    /** nessuna posizione da piu di un minuto (galleria, GPS spento) */
    val senzaSegnale: Boolean,
    /** rifornimento segnato da poco: si puo annullare */
    val rifornimentoAnnullabile: Boolean,
)

/** Cosa deve fare il servizio dopo una nuova posizione. */
data class EsitoGuida(
    val situazione: SituazioneGuida,
    /** la sosta consigliata e entro il preavviso: avvisa (una volta per sosta) */
    val preavviso: VoceGuida?,
    /** chiedi se hai fatto rifornimento qui */
    val domanda: Pair<String, String>?,
)

/**
 * La modalita autostrada in corso: il percorso fissato alla partenza e quello che cambia mentre
 * guidi. Vive in memoria (se il sistema chiude l'app, la modalita finisce); la aggiorna il
 * [ServizioGuida] a ogni posizione e la legge la schermata.
 */
object GuidaInCorso {
    private val _stato = MutableStateFlow<StatoGuida?>(null)
    val stato: StateFlow<StatoGuida?> = _stato

    @Volatile
    var percorso: PercorsoGuida? = null
        private set

    private var energia = Energia(0.0)
    private var energiaPrima: Energia? = null
    private var ultimo: SulPercorso? = null
    private var ultimaSituazione: SituazioneGuida? = null
    private var avviso = true
    private val avvisati = HashSet<String>()

    // sosta a una stazione: dove, da quando, e a quali abbiamo gia chiesto
    private var fermoA: Pair<String, String>? = null
    private var fermoDal = 0L
    private val chieste = HashSet<String>()
    private var domanda: Pair<String, String>? = null

    // per stimare la velocita quando il GPS non la dice
    private var latPrima = 0.0
    private var lonPrima = 0.0
    private var tempoPrima = 0L
    private var ultimaPosizione = 0L

    val attiva: Boolean get() = percorso != null

    /** Prepara la modalita per un viaggio che parte con il serbatoio (o la batteria) a [livello]. */
    @Synchronized
    fun prepara(p: PercorsoGuida, livello: Double) {
        percorso = p
        energia = Guida.energiaIniziale(p, livello)
        energiaPrima = null
        ultimo = null
        ultimaSituazione = null
        avvisati.clear()
        chieste.clear()
        domanda = null
        fermoA = null
        tempoPrima = 0L
        consigliataScritta = null
        ultimaPosizione = System.currentTimeMillis()
        pubblica(senzaSegnale = false)
        // qualche punto del percorso nel log: servono anche alla prova automatica per simulare il viaggio
        val punti = listOf(20.0, 60.0, 120.0).mapNotNull { km -> p.campioni.firstOrNull { it.km >= km } }
        Log.i("Goccia", "guida: percorso di ${p.lunghezzaKm.toInt()} km; " + punti.joinToString("; ") { "km ${it.km.roundToInt()} = ${it.lat},${it.lon}" })
    }

    private var consigliataScritta: String? = null

    /** Scrive nel log dove sta la sosta consigliata e il punto del percorso 8 km prima. */
    private fun scriviConsigliata(p: PercorsoGuida, s: SituazioneGuida) {
        val c = s.consigliata ?: return
        if (c.id == consigliataScritta) return
        consigliataScritta = c.id
        val prima = p.campioni.firstOrNull { it.km >= s.km + c.traKm - 8 }
        Log.i(
            "Goccia",
            "guida: consigliata ${c.id} al km ${(s.km + c.traKm).roundToInt()} = ${c.lat},${c.lon}" +
                (prima?.let { "; 8 km prima = ${it.lat},${it.lon}" } ?: ""),
        )
    }

    /** Una nuova posizione dal GPS. [velocita] in m/s, se il GPS la conosce. */
    @Synchronized
    fun posizione(lat: Double, lon: Double, velocita: Float?, adesso: Long): EsitoGuida? {
        val p = percorso ?: return null
        val dove = Guida.sulPercorso(p.campioni, lat, lon, ultimo?.km) ?: return null
        ultimo = dove
        ultimaPosizione = adesso

        // fermo accanto a una stazione del percorso per un po': forse stai facendo rifornimento
        val stimata = if (tempoPrima > 0 && adesso > tempoPrima) {
            (Guida.distanza(latPrima, lonPrima, lat, lon) * 1000 / ((adesso - tempoPrima) / 1000.0)).toFloat()
        } else {
            null
        }
        latPrima = lat
        lonPrima = lon
        tempoPrima = adesso
        val fermo = (velocita ?: stimata ?: 0f) < 2f
        val vicina = Guida.stazioneVicina(p, lat, lon)
        var nuovaDomanda: Pair<String, String>? = null
        if (vicina != null && fermo) {
            if (fermoA?.first != vicina.first) {
                fermoA = vicina
                fermoDal = adesso
            } else if (adesso - fermoDal >= SOSTA_MINIMA_MS && vicina.first !in chieste) {
                chieste += vicina.first
                domanda = vicina
                nuovaDomanda = vicina
            }
        } else if (vicina == null) {
            fermoA = null
        }

        val s = Guida.situazione(p, dove, energia)
        ultimaSituazione = s
        scriviConsigliata(p, s)
        val preavviso = s.consigliata?.takeIf { avviso && it.traKm <= Guida.PREAVVISO_KM && avvisati.add(it.id) }
        pubblica(senzaSegnale = false)
        return EsitoGuida(s, preavviso, nuovaDomanda)
    }

    /** Risposta alla domanda (o tasto "Ho fatto il pieno"): con [si] l'autonomia riparte da qui. */
    @Synchronized
    fun rifornito(si: Boolean) {
        val p = percorso ?: return
        if (si) {
            energiaPrima = energia
            energia = Guida.dopoRifornimento(p, ultimo?.km ?: 0.0)
        }
        domanda = null
        ricalcola()
    }

    /** Annulla l'ultimo rifornimento segnato (toccato per sbaglio). */
    @Synchronized
    fun annullaRifornimento() {
        energiaPrima?.let { energia = it }
        energiaPrima = null
        ricalcola()
    }

    @Synchronized
    fun impostaAvviso(attivo: Boolean) {
        avviso = attivo
        pubblica(senzaSegnale = false)
    }

    /** Controllo periodico: se il GPS tace da troppo lo diciamo. */
    @Synchronized
    fun controllaSegnale(adesso: Long) {
        if (percorso == null) return
        pubblica(senzaSegnale = adesso - ultimaPosizione > SENZA_SEGNALE_MS)
    }

    @Synchronized
    fun termina() {
        percorso = null
        _stato.value = null
    }

    private fun ricalcola() {
        val p = percorso ?: return
        val dove = ultimo
        if (dove != null) ultimaSituazione = Guida.situazione(p, dove, energia)
        pubblica(senzaSegnale = false)
    }

    private fun pubblica(senzaSegnale: Boolean) {
        val p = percorso ?: return
        _stato.value = StatoGuida(
            destinazione = p.destinazione,
            etichetta = Guida.etichetta(p),
            elettrica = p is PercorsoGuida.Elettrico,
            situazione = ultimaSituazione,
            avviso = avviso,
            domanda = domanda,
            senzaSegnale = senzaSegnale,
            rifornimentoAnnullabile = energiaPrima != null,
        )
    }

    /** dopo quanto tempo fermo accanto a una stazione chiediamo se hai fatto rifornimento */
    private const val SOSTA_MINIMA_MS = 90_000L
    private const val SENZA_SEGNALE_MS = 60_000L
}
