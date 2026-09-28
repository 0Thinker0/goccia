package it.goccia.app.logica

import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.Distributore
import it.goccia.app.dati.Prezzo
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Un punto del percorso con i km percorsi dalla partenza. */
data class PuntoPercorso(val lat: Double, val lon: Double, val km: Double)

/** Un distributore vicino al percorso. */
data class LungoIlPercorso(
    val distributore: Distributore,
    val prezzo: Prezzo,
    val self: Boolean,
    /** a che km del percorso si trova */
    val km: Double,
    /** distanza in linea d'aria dal percorso */
    val distanzaKm: Double,
    /** minuti in piu per andarci e tornare sul percorso */
    val deviazioneMin: Int,
) {
    /** sulla strada: aree di servizio autostradali o distributori praticamente sul percorso */
    val sullaStrada: Boolean get() = distributore.autostradale || distanzaKm < 0.15
}

data class Sosta(
    val punto: LungoIlPercorso,
    /** litri (o kg) da mettere */
    val quantita: Double,
    /** livello del serbatoio all'arrivo alla sosta, da 0 a 1 */
    val livelloArrivo: Double,
)

data class PianoViaggio(
    val lunghezzaKm: Double,
    val autonomiaKm: Int,
    /** fin dove si arriva con il carburante di partenza, tenendo la riserva */
    val kmLimite: Double,
    val soste: List<Sosta>,
    /** vero se si arriva senza fermarsi */
    val senzaSoste: Boolean,
    /** il piu conveniente lungo il percorso, anche quando non serve fermarsi */
    val migliore: LungoIlPercorso?,
    /** altre possibilita, ordinate per km */
    val alternative: List<LungoIlPercorso>,
    /** media delle aree di servizio autostradali lungo il percorso */
    val mediaAutostrada: Int?,
    /** media dei distributori lungo il percorso */
    val mediaPercorso: Int?,
    /** chilometri di autonomia rimasti all'arrivo */
    val margineArrivoKm: Int,
)

/** Calcoli del navigatore: percorso, distributori lungo la strada e soste consigliate. */
object Tragitto {

    /** Decodifica una polyline (formato Google/OSRM) con la precisione indicata (6 per OSRM polyline6). */
    fun decodifica(codificata: String, precisione: Int = 6): List<Coordinate> {
        val fattore = Math.pow(10.0, precisione.toDouble())
        val punti = ArrayList<Coordinate>()
        var indice = 0
        var lat = 0L
        var lon = 0L
        while (indice < codificata.length) {
            for (quale in 0..1) {
                var risultato = 0L
                var spostamento = 0
                var b: Int
                do {
                    b = codificata[indice++].code - 63
                    risultato = risultato or ((b and 0x1f).toLong() shl spostamento)
                    spostamento += 5
                } while (b >= 0x20 && indice < codificata.length)
                val delta = if (risultato and 1L != 0L) (risultato shr 1).inv() else risultato shr 1
                if (quale == 0) lat += delta else lon += delta
            }
            punti += Coordinate(lat / fattore, lon / fattore)
        }
        return punti
    }

    /** Ricampiona il percorso con un punto ogni [passoKm] circa, con i km progressivi. */
    fun campiona(punti: List<Coordinate>, passoKm: Double = 0.5): List<PuntoPercorso> {
        if (punti.isEmpty()) return emptyList()
        val risultato = ArrayList<PuntoPercorso>()
        var km = 0.0
        var ultimoAggiunto = 0.0
        var precedente = punti.first()
        risultato += PuntoPercorso(precedente.lat, precedente.lon, 0.0)
        for (i in 1 until punti.size) {
            val p = punti[i]
            val tratto = Geo.distanzaKm(precedente, p)
            if (tratto > passoKm) {
                // tratti lunghi: aggiungiamo punti intermedi
                val parti = ceil(tratto / passoKm).toInt()
                for (k in 1 until parti) {
                    val t = k.toDouble() / parti
                    risultato += PuntoPercorso(
                        precedente.lat + (p.lat - precedente.lat) * t,
                        precedente.lon + (p.lon - precedente.lon) * t,
                        km + tratto * t,
                    )
                }
                ultimoAggiunto = km + tratto * (parti - 1) / parti
            }
            km += tratto
            if (km - ultimoAggiunto >= passoKm || i == punti.lastIndex) {
                risultato += PuntoPercorso(p.lat, p.lon, km)
                ultimoAggiunto = km
            }
            precedente = p
        }
        return risultato
    }

    /** Minuti per uscire dal percorso, arrivare al distributore e tornare. */
    fun deviazioneMinuti(distanzaKm: Double, autostradale: Boolean): Int =
        if (autostradale || distanzaKm < 0.15) 0 else ceil(1 + 2 * distanzaKm * FATTORE_STRADA / 50.0 * 60).toInt()

    /** Distanza massima dal percorso per stare entro [minuti] di deviazione. */
    fun distanzaPerMinuti(minuti: Int): Double = max(0.2, (minuti - 1) * 50.0 / 60 / (2 * FATTORE_STRADA))

    /**
     * I distributori entro [maxDistanzaKm] dal percorso, con la posizione lungo il percorso.
     * Le aree autostradali contano solo se sono proprio sul percorso (altrimenti sono sull'altra autostrada).
     */
    fun lungoIlPercorso(
        distributori: List<Distributore>,
        carburante: Carburante,
        preferisciSelf: Boolean,
        campioni: List<PuntoPercorso>,
        maxDistanzaKm: Double,
        adessoSecondi: Long,
    ): List<LungoIlPercorso> {
        if (campioni.isEmpty()) return emptyList()
        // griglia di circa 5 km per trovare in fretta i punti vicini
        val cella = 0.05
        val griglia = HashMap<Long, MutableList<Int>>()
        fun chiave(lat: Double, lon: Double): Long = (floor(lat / cella).toLong() shl 32) or (floor(lon / cella).toLong() and 0xffffffffL)
        campioni.forEachIndexed { i, p -> griglia.getOrPut(chiave(p.lat, p.lon)) { ArrayList() } += i }
        val risultato = ArrayList<LungoIlPercorso>()
        for (d in distributori) {
            val (prezzo, self) = Convenienza.prezzoPer(d, carburante, preferisciSelf) ?: continue
            if (Convenienza.giorniDa(prezzo.comunicato, adessoSecondi) > GIORNI_VECCHIO) continue
            val cLat = floor(d.lat / cella).toLong()
            val cLon = floor(d.lon / cella).toLong()
            var migliore = -1
            var distanza = Double.MAX_VALUE
            for (dl in -1L..1L) for (dn in -1L..1L) {
                val k = ((cLat + dl) shl 32) or ((cLon + dn) and 0xffffffffL)
                val vicini = griglia[k] ?: continue
                for (i in vicini) {
                    val p = campioni[i]
                    val km = distanzaVeloce(d.lat, d.lon, p.lat, p.lon)
                    if (km < distanza) {
                        distanza = km
                        migliore = i
                    }
                }
            }
            if (migliore < 0) continue
            val limite = if (d.autostradale) min(0.25, maxDistanzaKm) else maxDistanzaKm
            if (distanza > limite) continue
            // un'area di servizio sulla carreggiata opposta non si puo raggiungere
            if (d.autostradale && !dalNostroLato(d, campioni, migliore)) continue
            risultato += LungoIlPercorso(d, prezzo, self, campioni[migliore].km, distanza, deviazioneMinuti(distanza, d.autostradale))
        }
        return risultato.sortedBy { it.km }
    }

    /**
     * Vero se l'area di servizio sta sul lato della strada dove si viaggia (in Italia a destra).
     * Il nome lo dice quasi sempre ("Cantagallo Ovest": sul lato ovest, per chi va verso sud);
     * altrimenti decide la posizione rispetto al percorso, se non e proprio sulla linea.
     */
    fun dalNostroLato(d: Distributore, campioni: List<PuntoPercorso>, indice: Int): Boolean {
        val a = campioni[max(0, indice - 2)]
        val b = campioni[min(campioni.lastIndex, indice + 2)]
        val kx = cos(Math.toRadians(a.lat))
        val dx = (b.lon - a.lon) * kx
        val dy = b.lat - a.lat
        if (dx == 0.0 && dy == 0.0) return true
        val destra = Math.toDegrees(atan2(dx, dy)) + 90
        latoDalNome(d.nome)?.let { lato ->
            val scarto = abs(((lato - destra) % 360 + 540) % 360 - 180)
            return scarto < 90
        }
        // prodotto vettoriale: negativo = a destra della direzione di marcia
        val px = (d.lon - a.lon) * kx
        val py = d.lat - a.lat
        val croce = dx * py - dy * px
        val distanzaDallaLinea = abs(croce) / sqrt(dx * dx + dy * dy) * 111.32
        return croce <= 0 || distanzaDallaLinea < 0.03
    }

    /** "Area Cantagallo Ovest" -> 270 gradi; null se il nome non dice il lato. */
    fun latoDalNome(nome: String): Double? =
        when (nome.trim().split(' ', '-', '(', ')', '.').lastOrNull { it.isNotBlank() }?.lowercase()) {
            "nord" -> 0.0
            "est" -> 90.0
            "sud" -> 180.0
            "ovest" -> 270.0
            else -> null
        }

    /** Distanza approssimata (equirettangolare): va benissimo per pochi km e costa poco. */
    private fun distanzaVeloce(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val x = (lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
        val y = lat2 - lat1
        return sqrt(x * x + y * y) * 111.32
    }

    /** Prezzo "vero" per litro: il prezzo piu il carburante bruciato per la deviazione. */
    private fun prezzoEffettivo(p: LungoIlPercorso, quantita: Double, consumoPer100: Double): Double {
        val litriDeviazione = p.deviazioneMin / 60.0 * 50.0 * consumoPer100 / 100.0
        return p.prezzo.euro * (1 + litriDeviazione / max(quantita, 5.0))
    }

    /**
     * Le soste: con il carburante di partenza si arriva fino a [PianoViaggio.kmLimite] (tenendo una riserva);
     * prima di quel punto scegliamo il distributore con il prezzo effettivo piu basso, preferendo a parita
     * quello piu avanti. Poi si riparte con il pieno (o con quanto basta) e si ripete.
     */
    fun pianifica(
        lungo: List<LungoIlPercorso>,
        lunghezzaKm: Double,
        capienza: Double,
        consumoPer100: Double,
        livelloIniziale: Double,
        pienoCompleto: Boolean,
    ): PianoViaggio {
        val kmPerUnita = if (consumoPer100 > 0) 100.0 / consumoPer100 else 0.0
        val autonomiaPieno = capienza * kmPerUnita
        val riservaKm = max(40.0, autonomiaPieno * 0.1)
        val autonomia = capienza * livelloIniziale * kmPerUnita

        val autostrade = lungo.filter { it.distributore.autostradale }.map { it.prezzo.millesimi }
        val mediaAutostrada = autostrade.takeIf { it.size >= 2 }?.average()?.roundToInt()
        val mediaPercorso = lungo.filter { !it.distributore.autostradale }.map { it.prezzo.millesimi }.takeIf { it.size >= 3 }?.average()?.roundToInt()

        val soste = ArrayList<Sosta>()
        var partenzaKm = 0.0
        var carburanteKm = autonomia
        var sicurezza = 0
        while (partenzaKm + carburanteKm - riservaKm < lunghezzaKm && sicurezza++ < 6) {
            val limite = partenzaKm + carburanteKm - riservaKm
            val candidati = lungo.filter { it.km > partenzaKm + 1 && it.km <= limite }
            // se nessun distributore prima della riserva, prendiamo il primo disponibile dopo
            val scelti = candidati.ifEmpty { lungo.filter { it.km > partenzaKm + 1 }.take(1) }
            if (scelti.isEmpty()) break
            val scelta = scelti.minWith(
                compareBy<LungoIlPercorso> { p ->
                    val arrivo = (carburanteKm - (p.km - partenzaKm)) / autonomiaPieno
                    val quantita = capienza * (1 - arrivo.coerceIn(0.0, 1.0))
                    // arrotondiamo al mezzo centesimo: a parita di prezzo vince quello piu avanti
                    (prezzoEffettivo(p, quantita, consumoPer100) * 200).roundToInt()
                }.thenByDescending { it.km },
            )
            val livelloArrivo = ((carburanteKm - (scelta.km - partenzaKm)) / autonomiaPieno).coerceIn(0.0, 1.0)
            val quantita = if (pienoCompleto) {
                capienza * (1 - livelloArrivo)
            } else {
                // quanto basta per arrivare a destinazione con la riserva, senza superare il pieno
                val servono = ((lunghezzaKm - scelta.km + riservaKm) / kmPerUnita) - capienza * livelloArrivo
                servono.coerceIn(capienza * 0.2, capienza * (1 - livelloArrivo))
            }
            soste += Sosta(scelta, quantita, livelloArrivo)
            carburanteKm = (capienza * livelloArrivo + quantita) * kmPerUnita
            partenzaKm = scelta.km
        }
        val ultimaPartenza = soste.lastOrNull()?.punto?.km ?: 0.0
        val margine = (carburanteKm - (lunghezzaKm - ultimaPartenza)).roundToInt()
        val migliore = lungo.filter { it.km <= lunghezzaKm }.minWithOrNull(
            compareBy<LungoIlPercorso> { (prezzoEffettivo(it, capienza * 0.6, consumoPer100) * 200).roundToInt() }.thenBy { it.km },
        )
        val sceltiId = soste.map { it.punto.distributore.id }.toSet()
        val alternative = (
            lungo.filter { it.distributore.id !in sceltiId && !it.distributore.autostradale }
                .sortedBy { it.prezzo.millesimi }.take(3) +
                lungo.filter { it.distributore.id !in sceltiId && it.distributore.autostradale }
                    .sortedBy { it.km }.take(2)
            ).sortedBy { it.km }
        return PianoViaggio(
            lunghezzaKm = lunghezzaKm,
            autonomiaKm = autonomia.roundToInt(),
            kmLimite = autonomia - riservaKm,
            soste = soste,
            senzaSoste = soste.isEmpty(),
            migliore = migliore,
            alternative = alternative,
            mediaAutostrada = mediaAutostrada,
            mediaPercorso = mediaPercorso,
            margineArrivoKm = margine,
        )
    }
}
