package it.goccia.app.logica

import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.Distributore
import it.goccia.app.dati.Prezzo
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Oltre 3 giorni mostriamo un avviso, oltre 8 il prezzo resta fuori da medie e consigli. */
const val GIORNI_DA_VERIFICARE = 3
const val GIORNI_VECCHIO = 8

/** La strada vera e piu lunga della linea d'aria. */
const val FATTORE_STRADA = 1.3

/** Raggio massimo a cui si allarga la ricerca quando vicino ci sono pochi distributori. */
const val RAGGIO_MASSIMO_KM = 30

enum class Tono { CONVENIENTE, MEDIA, CARO }

enum class Ordinamento(val etichetta: String) {
    PREZZO("Prezzo"),
    DISTANZA("Distanza"),
    CONVENIENZA("Convenienza reale"),
}

data class Offerta(
    val distributore: Distributore,
    val prezzo: Prezzo,
    /** true se il prezzo e self (per GPL e metano e sempre il prezzo unico) */
    val self: Boolean,
    val distanzaKm: Double,
    /** Costo di un pieno piu il carburante per andare e tornare. */
    val costoReale: Double,
    val differenzaCent: Int?,
    val tono: Tono,
    val giorniDaAggiornamento: Int,
) {
    val vecchio: Boolean get() = giorniDaAggiornamento > GIORNI_VECCHIO
    val daVerificare: Boolean get() = giorniDaAggiornamento > GIORNI_DA_VERIFICARE
}

data class Zona(
    /** distributori entro il raggio usato, nell'ordine in cui arrivano */
    val offerte: List<Offerta>,
    /** tutti i distributori caricati con un prezzo per il carburante (per la mappa) */
    val tutte: List<Offerta>,
    /** media dei prezzi nella zona, millesimi */
    val media: Int?,
    /** true se la media e quella locale, false se e quella della provincia */
    val mediaLocale: Boolean,
    val raggioUsatoKm: Int,
)

object Geo {
    fun distanzaKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = Math.PI / 180
        val a = sin((lat2 - lat1) * r / 2).let { it * it } +
            cos(lat1 * r) * cos(lat2 * r) * sin((lon2 - lon1) * r / 2).let { it * it }
        return 12742.0 * asin(sqrt(a.coerceIn(0.0, 1.0)))
    }

    fun distanzaKm(a: Coordinate, b: Coordinate): Double = distanzaKm(a.lat, a.lon, b.lat, b.lon)
}

object Convenienza {

    /**
     * Prezzo di un distributore per il carburante scelto: la modalita preferita se c'e,
     * altrimenti l'altra (un distributore solo servito resta in lista, con il suo prezzo).
     */
    fun prezzoPer(d: Distributore, carburante: Carburante, preferisciSelf: Boolean): Pair<Prezzo, Boolean>? {
        val p = d.prezzi[carburante] ?: return null
        return if (preferisciSelf || !carburante.haSelf) {
            p.self?.let { it to true } ?: p.servito?.let { it to false }
        } else {
            p.servito?.let { it to false } ?: p.self?.let { it to true }
        }
    }

    /** Solo il prezzo della modalita richiesta: le medie non mescolano self e servito. */
    fun prezzoEsatto(d: Distributore, carburante: Carburante, self: Boolean): Prezzo? {
        val p = d.prezzi[carburante] ?: return null
        return when {
            !carburante.haSelf -> p.self ?: p.servito
            self -> p.self
            else -> p.servito
        }
    }

    fun tono(differenzaCent: Int?): Tono = when {
        differenzaCent == null -> Tono.MEDIA
        differenzaCent <= -3 -> Tono.CONVENIENTE
        differenzaCent >= 3 -> Tono.CARO
        else -> Tono.MEDIA
    }

    fun differenzaCent(prezzo: Int, media: Int?): Int? = media?.let { ((prezzo - it) / 10.0).roundToInt() }

    fun giorniDa(epochSecondi: Long, adessoSecondi: Long): Int =
        if (epochSecondi <= 0) 99 else ((adessoSecondi - epochSecondi) / 86_400).toInt().coerceAtLeast(0)

    /**
     * Media dei prezzi recenti dei distributori stradali entro [raggioKm] da [centro].
     * Con meno di [minimo] prezzi la media non e affidabile e restituiamo null.
     */
    fun mediaLocale(
        distributori: List<Distributore>,
        carburante: Carburante,
        self: Boolean,
        centro: Coordinate,
        raggioKm: Double,
        adessoSecondi: Long,
        minimo: Int = 5,
    ): Int? {
        var somma = 0L
        var conta = 0
        for (d in distributori) {
            if (d.autostradale) continue
            val prezzo = prezzoEsatto(d, carburante, self) ?: continue
            if (giorniDa(prezzo.comunicato, adessoSecondi) > GIORNI_VECCHIO) continue
            if (Geo.distanzaKm(centro.lat, centro.lon, d.lat, d.lon) > raggioKm) continue
            somma += prezzo.millesimi
            conta++
        }
        return if (conta >= minimo) (somma.toDouble() / conta).roundToInt() else null
    }

    /**
     * Distributori attorno a un punto con prezzo, distanza, costo reale e confronto con la media.
     * Se nel raggio ci sono meno di 5 distributori il raggio si allarga fino a 30 km.
     */
    fun zona(
        distributori: List<Distributore>,
        carburante: Carburante,
        preferisciSelf: Boolean,
        centro: Coordinate,
        raggioKm: Int,
        escludiAutostrade: Boolean,
        litri: Double,
        consumoPer100: Double,
        adessoSecondi: Long,
        mediaDiRiserva: Int?,
    ): Zona {
        data class Grezzo(val d: Distributore, val prezzo: Prezzo, val self: Boolean, val km: Double)

        val tutti = distributori.mapNotNull { d ->
            if (escludiAutostrade && d.autostradale) return@mapNotNull null
            val (prezzo, self) = prezzoPer(d, carburante, preferisciSelf) ?: return@mapNotNull null
            Grezzo(d, prezzo, self, Geo.distanzaKm(centro.lat, centro.lon, d.lat, d.lon))
        }

        var raggio = raggioKm.coerceIn(1, RAGGIO_MASSIMO_KM)
        var vicini = tutti.filter { it.km <= raggio }
        while (vicini.size < 5 && raggio < RAGGIO_MASSIMO_KM) {
            raggio = (raggio * 2).coerceAtMost(RAGGIO_MASSIMO_KM)
            vicini = tutti.filter { it.km <= raggio }
        }

        // media della zona: stradali, prezzo recente, entro almeno 10 km
        val self = preferisciSelf || !carburante.haSelf
        val locale = mediaLocale(distributori, carburante, self, centro, maxOf(raggio, 10).toDouble(), adessoSecondi)
        val media = locale ?: mediaDiRiserva

        fun offerta(g: Grezzo): Offerta {
            val differenza = differenzaCent(g.prezzo.millesimi, media)
            val viaggioLitri = 2 * g.km * FATTORE_STRADA * consumoPer100 / 100.0
            return Offerta(
                distributore = g.d,
                prezzo = g.prezzo,
                self = g.self,
                distanzaKm = g.km,
                costoReale = g.prezzo.euro * (litri + viaggioLitri),
                differenzaCent = differenza,
                tono = tono(differenza),
                giorniDaAggiornamento = giorniDa(g.prezzo.comunicato, adessoSecondi),
            )
        }

        val tutte = tutti.map { offerta(it) }
        val idVicini = vicini.mapTo(HashSet()) { it.d.id }
        return Zona(
            offerte = tutte.filter { it.distributore.id in idVicini },
            tutte = tutte,
            media = media,
            mediaLocale = locale != null,
            raggioUsatoKm = raggio,
        )
    }

    fun ordina(offerte: List<Offerta>, ordinamento: Ordinamento): List<Offerta> {
        // i prezzi vecchi restano in fondo in ogni caso
        val chiave: Comparator<Offerta> = when (ordinamento) {
            Ordinamento.PREZZO -> compareBy<Offerta> { it.prezzo.millesimi }.thenBy { it.distanzaKm }
            Ordinamento.DISTANZA -> compareBy { it.distanzaKm }
            Ordinamento.CONVENIENZA -> compareBy<Offerta> { it.costoReale }.thenBy { it.distanzaKm }
        }
        return offerte.sortedWith(compareBy<Offerta> { it.vecchio }.then(chiave))
    }

    /** I consigli escludono autostrade e prezzi vecchi. */
    fun consigliati(zona: Zona, quanti: Int = 3): List<Offerta> =
        ordina(zona.offerte.filter { !it.vecchio && !it.distributore.autostradale }, Ordinamento.CONVENIENZA).take(quanti)
}
