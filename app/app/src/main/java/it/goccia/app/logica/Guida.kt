package it.goccia.app.logica

import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Colonnina
import it.goccia.app.dati.Distributore
import it.goccia.app.dati.Presa
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

// Modalita autostrada: mentre guidi, dove sei lungo il percorso, quanta autonomia ti resta
// e quale sosta conviene adesso. Tutto si ricalcola a ogni posizione.

/** Un tratto del percorso con la stessa strada, dal km [da] al km [a]; [sigla] come "A1". */
data class TrattoStrada(val da: Double, val a: Double, val nome: String, val sigla: String?)

/** Dove sei: km del percorso e distanza dal percorso. */
data class SulPercorso(val km: Double, val distanzaKm: Double) {
    val fuori: Boolean get() = distanzaKm > Guida.FUORI_PERCORSO_KM
}

/** Autonomia: [autonomiaKm] di strada possibili dopo l'ultimo rifornimento, fatto al km [dove]. */
data class Energia(val autonomiaKm: Double, val dove: Double = 0.0) {
    fun al(km: Double): Double = autonomiaKm - (km - dove)
}

/** Tutto quello che serve alla modalita autostrada, fissato alla partenza. */
sealed interface PercorsoGuida {
    val destinazione: String
    val lunghezzaKm: Double
    val campioni: List<PuntoPercorso>
    val strade: List<TrattoStrada>

    /** chilometri con il pieno (o con la batteria carica) */
    val autonomiaPienoKm: Double

    data class AlCarburante(
        override val destinazione: String,
        override val lunghezzaKm: Double,
        override val campioni: List<PuntoPercorso>,
        override val strade: List<TrattoStrada>,
        val lungo: List<LungoIlPercorso>,
        val carburante: Carburante,
        val self: Boolean,
        val capienza: Double,
        val consumo: Double,
        val pienoCompleto: Boolean,
    ) : PercorsoGuida {
        override val autonomiaPienoKm: Double get() = if (consumo > 0) capienza * 100 / consumo else 0.0
    }

    data class Elettrico(
        override val destinazione: String,
        override val lunghezzaKm: Double,
        override val campioni: List<PuntoPercorso>,
        override val strade: List<TrattoStrada>,
        val lungo: List<ColonninaSulPercorso>,
        val capacita: Double,
        val consumo: Double,
        val acKw: Double,
        val dcKw: Double,
        val prese: Set<Presa>,
        val arrivoMinimo: Double,
        val tariffa: (Colonnina) -> Double,
    ) : PercorsoGuida {
        override val autonomiaPienoKm: Double get() = if (consumo > 0) capacita * 100 / consumo else 0.0
    }
}

/** Un distributore (o una colonnina) davanti a te. */
data class VoceGuida(
    val id: String,
    val nome: String,
    val lat: Double,
    val lon: Double,
    /** km da dove sei adesso */
    val traKm: Double,
    /** prezzo ("1,699") o potenza ("150 kW") */
    val valore: String,
    /** "Esso · area di servizio", "1,6 km dal percorso · +4 min" */
    val dettaglio: String,
    /** confronto con la sosta consigliata ("+19 cent vs consigliato"), se serve */
    val confronto: String?,
    /** vero se costa piu della sosta consigliata */
    val piuCara: Boolean,
    val consigliata: Boolean,
    val sullaStrada: Boolean,
)

/** Il quadro in un momento del viaggio. */
data class SituazioneGuida(
    val km: Double,
    val restantiKm: Double,
    val sigla: String?,
    val autonomiaKm: Double,
    /** serbatoio o batteria, da 0 a 1 */
    val livello: Double,
    val consigliata: VoceGuida?,
    /** le prossime soste possibili, in ordine di strada; comprende la consigliata */
    val prossime: List<VoceGuida>,
    val messaggio: String,
    /** poca autonomia per arrivare alla prossima sosta utile */
    val critica: Boolean,
    val arrivato: Boolean,
    val fuori: Boolean,
)

object Guida {
    /** oltre questa distanza dal percorso sei fuori strada (hai cambiato giro, o sei in un'area di servizio grande) */
    const val FUORI_PERCORSO_KM = 1.5

    /** l'avviso arriva quando la sosta consigliata e a questa distanza */
    const val PREAVVISO_KM = 10.0

    /** entro questa distanza da una stazione, fermo, ti chiediamo se hai fatto rifornimento */
    const val RAGGIO_SOSTA_KM = 0.25

    /** margine sotto il quale l'autonomia verso la sosta e scarsa */
    private const val MARGINE_SCARSO_KM = 25.0

    /**
     * Il punto del percorso piu vicino a [lat], [lon], proiettato sui tratti tra i campioni.
     * Il percorso puo passare due volte vicino allo stesso posto: tornare indietro rispetto
     * all'ultima posizione ([kmPrecedente]) o saltare molto avanti costa un poco, cosi a parita
     * vince il punto coerente con il viaggio.
     */
    fun sulPercorso(campioni: List<PuntoPercorso>, lat: Double, lon: Double, kmPrecedente: Double? = null): SulPercorso? {
        if (campioni.isEmpty()) return null
        if (campioni.size == 1) {
            return SulPercorso(campioni[0].km, distanza(campioni[0].lat, campioni[0].lon, lat, lon))
        }
        val kx = cos(Math.toRadians(lat)) * 111.32
        val ky = 111.32
        val px = lon * kx
        val py = lat * ky
        var miglioreCosto = Double.MAX_VALUE
        var migliore = SulPercorso(0.0, Double.MAX_VALUE)
        for (i in 0 until campioni.lastIndex) {
            val a = campioni[i]
            val b = campioni[i + 1]
            val ax = a.lon * kx
            val ay = a.lat * ky
            val dx = b.lon * kx - ax
            val dy = b.lat * ky - ay
            val l2 = dx * dx + dy * dy
            val t = if (l2 <= 0.0) 0.0 else (((px - ax) * dx + (py - ay) * dy) / l2).coerceIn(0.0, 1.0)
            val qx = ax + t * dx
            val qy = ay + t * dy
            val d = sqrt((px - qx) * (px - qx) + (py - qy) * (py - qy))
            val km = a.km + (b.km - a.km) * t
            var costo = d
            if (kmPrecedente != null) {
                val salto = km - kmPrecedente
                if (salto < -1) costo += -salto * 0.05
                if (salto > 30) costo += (salto - 30) * 0.02
            }
            if (costo < miglioreCosto) {
                miglioreCosto = costo
                migliore = SulPercorso(km, d)
            }
        }
        return migliore
    }

    /** La sigla della strada al km indicato ("A1"), se il percorso la conosce. */
    fun sigla(strade: List<TrattoStrada>, km: Double): String? =
        strade.firstOrNull { km >= it.da - 0.05 && km < it.a + 0.05 }?.sigla

    /** La prima sigla di un "ref" di OpenStreetMap: "A1;E35" diventa "A1". */
    fun siglaBreve(ref: String?): String? =
        ref?.split(';', ',')?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }?.replace(" ", "")?.take(8)

    /** Il quadro del viaggio a questo punto, con la sosta consigliata ricalcolata da qui. */
    fun situazione(p: PercorsoGuida, dove: SulPercorso, energia: Energia): SituazioneGuida {
        val km = dove.km.coerceIn(0.0, p.lunghezzaKm)
        val restanti = (p.lunghezzaKm - km).coerceAtLeast(0.0)
        val autonomia = energia.al(km).coerceAtLeast(0.0)
        val livello = if (p.autonomiaPienoKm > 0) (autonomia / p.autonomiaPienoKm).coerceIn(0.0, 1.0) else 0.0
        val sigla = sigla(p.strade, km)
        if (restanti < 0.5) {
            return SituazioneGuida(
                km, 0.0, sigla, autonomia, livello, null, emptyList(),
                "Sei arrivato a destinazione.", critica = false, arrivato = true, fuori = dove.fuori,
            )
        }
        val quadro = when (p) {
            is PercorsoGuida.AlCarburante -> alCarburante(p, km, restanti, autonomia, livello)
            is PercorsoGuida.Elettrico -> elettrico(p, km, restanti, autonomia, livello)
        }
        val messaggio = if (dove.fuori) "Sei fuori dal percorso calcolato: i km sono indicativi. ${quadro.messaggio}" else quadro.messaggio
        return SituazioneGuida(
            km, restanti, sigla, autonomia, livello, quadro.consigliata, quadro.prossime, messaggio,
            critica = quadro.critica, arrivato = false, fuori = dove.fuori,
        )
    }

    private class Quadro(val consigliata: VoceGuida?, val prossime: List<VoceGuida>, val messaggio: String, val critica: Boolean)

    private fun alCarburante(p: PercorsoGuida.AlCarburante, km: Double, restanti: Double, autonomia: Double, livello: Double): Quadro {
        // i distributori davanti, con i km contati da qui
        val avanti = p.lungo.filter { it.km > km + 0.2 && it.km <= p.lunghezzaKm }.map { it.copy(km = it.km - km) }
        val piano = Tragitto.pianifica(avanti, restanti, p.capienza, p.consumo, livello, p.pienoCompleto)
        val scelta = piano.soste.firstOrNull()?.punto
        val riferimento = scelta ?: piano.migliore
        // le prossime aree sulla strada, la consigliata e (se diverso) il piu conveniente raggiungibile
        val raggiungibile = piano.migliore?.takeIf { it.km <= autonomia }
        val mostrati = (avanti.filter { it.sullaStrada }.take(4) + listOfNotNull(scelta, raggiungibile))
            .distinctBy { it.distributore.id }
            .sortedBy { it.km }
        val rispetto = if (scelta != null) "vs consigliato" else "vs il più conveniente"
        val voci = mostrati.map { voceCarburante(it, it.distributore.id == scelta?.distributore?.id, riferimento, rispetto) }
        val consigliata = voci.firstOrNull { it.consigliata }
        val margine = scelta?.let { autonomia - it.km }
        // la stessa riserva del pianificatore dei viaggi
        val riserva = max(40.0, p.autonomiaPienoKm * 0.1)
        val inRiserva = autonomia < riserva
        val critica = inRiserva || (margine != null && margine < MARGINE_SCARSO_KM)
        val messaggio = when {
            scelta != null && margine != null && margine < 0 ->
                "Autonomia scarsa: fermati al primo distributore utile, la sosta consigliata è tra ${Formati.km(scelta.km)}."
            scelta != null && inRiserva ->
                "Sei in riserva: la sosta consigliata è tra ${Formati.km(scelta.km)}, non saltarla."
            scelta != null && critica ->
                "La sosta consigliata è tra ${Formati.km(scelta.km)}: ci arrivi con poco margine, non saltarla."
            scelta != null -> "La sosta consigliata è tra ${Formati.km(scelta.km)}: ci arrivi con margine."
            raggiungibile != null ->
                "Arrivi senza fermarti. Se vuoi fare il pieno, il più conveniente è tra ${Formati.km(raggiungibile.km)}."
            else -> "Arrivi a destinazione senza fermarti."
        }
        return Quadro(consigliata, voci, messaggio, critica)
    }

    private fun voceCarburante(l: LungoIlPercorso, consigliata: Boolean, riferimento: LungoIlPercorso?, rispetto: String): VoceGuida {
        val d = l.distributore
        val nome = nomeBreve(d)
        val dettaglio = when {
            d.autostradale -> listOf(d.intestazione.takeIf { it.isNotBlank() && it != nome }, "area di servizio").filterNotNull().joinToString(" · ")
            l.deviazioneMin == 0 -> "sulla strada"
            else -> "${Formati.km(l.distanzaKm)} dal percorso · +${l.deviazioneMin} min"
        }
        val cent = riferimento?.let { ((l.prezzo.millesimi - it.prezzo.millesimi) / 10.0).roundToInt() }
        val confronto = when {
            consigliata || cent == null || riferimento.distributore.id == d.id -> null
            cent > 0 -> "+$cent cent $rispetto"
            cent < 0 -> "−${-cent} cent $rispetto"
            else -> "stesso prezzo"
        }
        return VoceGuida(
            id = "d${d.id}",
            nome = nome,
            lat = d.lat,
            lon = d.lon,
            traKm = l.km,
            valore = Formati.prezzo(l.prezzo.millesimi),
            dettaglio = dettaglio,
            confronto = confronto,
            piuCara = (cent ?: 0) > 0,
            consigliata = consigliata,
            sullaStrada = l.sullaStrada,
        )
    }

    /** "Area Pioppa Est" per le aree di servizio, "Nordica · Valdarno" per gli altri. */
    fun nomeBreve(d: Distributore): String = when {
        d.autostradale && d.nome.isNotBlank() -> d.nome
        else -> listOf(d.intestazione, d.comune).filter { it.isNotBlank() }.distinct().joinToString(" · ")
    }

    private fun elettrico(p: PercorsoGuida.Elettrico, km: Double, restanti: Double, autonomia: Double, livello: Double): Quadro {
        val avanti = p.lungo.filter { it.km > km + 0.2 && it.km <= p.lunghezzaKm }.map { it.copy(km = it.km - km) }
        val piano = Elettrico.pianifica(avanti, restanti, p.capacita, p.consumo, p.acKw, p.dcKw, p.prese, livello, p.arrivoMinimo, p.tariffa)
        val sosta = piano.soste.firstOrNull()
        val scelta = sosta?.punto
        val veloci = avanti.filter { c ->
            Elettrico.erogazione(c.colonnina, p.prese, p.acKw, p.dcKw)?.let { it.continua && it.kw >= Elettrico.POTENZA_SOSTA } == true
        }
        val mostrate = (veloci.take(4) + listOfNotNull(scelta)).distinctBy { it.colonnina.id }.sortedBy { it.km }
        val voci = mostrate.map { c ->
            val col = c.colonnina
            VoceGuida(
                id = "c${col.id}",
                nome = col.titolo,
                lat = col.lat,
                lon = col.lon,
                traKm = c.km,
                valore = col.kw?.let { Formati.kw(it) } ?: "—",
                dettaglio = listOfNotNull(
                    col.descrizionePrese,
                    if (c.deviazioneMin == 0) "sulla strada" else "+${c.deviazioneMin} min",
                ).joinToString(" · "),
                confronto = null,
                piuCara = false,
                consigliata = col.id == scelta?.colonnina?.id,
                sullaStrada = c.deviazioneMin == 0,
            )
        }
        val consigliata = voci.firstOrNull { it.consigliata }
        val margine = scelta?.let { autonomia - it.km }
        val critica = !piano.completo || (margine != null && margine < MARGINE_SCARSO_KM)
        val messaggio = when {
            sosta != null && !piano.completo ->
                "Ricarica consigliata tra ${Formati.km(sosta.punto.km)}, ma dopo mancano colonnine veloci compatibili: carica il più possibile."
            sosta != null ->
                "Ricarica consigliata tra ${Formati.km(sosta.punto.km)}: arrivi con il ${Formati.percento(sosta.arrivo)} " +
                    "e riparti con il ${Formati.percento(sosta.ripartenza)}."
            !piano.completo ->
                "Con questa carica non trovi colonnine veloci compatibili entro l'autonomia: cerca una colonnina appena puoi."
            else -> "Arrivi senza ricaricare, con circa il ${Formati.percento(piano.arrivo)}."
        }
        return Quadro(consigliata, voci, messaggio, critica)
    }

    /** Distanza approssimata in km, va bene per pochi km. */
    fun distanza(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val x = (lon2 - lon1) * cos(Math.toRadians((lat1 + lat2) / 2))
        val y = lat2 - lat1
        return sqrt(x * x + y * y) * 111.32
    }

    /**
     * La stazione davanti o accanto a te entro [RAGGIO_SOSTA_KM]: se resti fermo li, forse stai facendo
     * rifornimento. Cerca tra tutte quelle del percorso, non solo tra le prossime.
     */
    fun stazioneVicina(p: PercorsoGuida, lat: Double, lon: Double): Pair<String, String>? {
        val (id, nome, distanza) = when (p) {
            is PercorsoGuida.AlCarburante -> p.lungo.map { Triple("d${it.distributore.id}", nomeBreve(it.distributore), distanza(lat, lon, it.distributore.lat, it.distributore.lon)) }
            is PercorsoGuida.Elettrico -> p.lungo.map { Triple("c${it.colonnina.id}", it.colonnina.titolo, distanza(lat, lon, it.colonnina.lat, it.colonnina.lon)) }
        }.minByOrNull { it.third } ?: return null
        return if (distanza <= RAGGIO_SOSTA_KM) id to nome else null
    }

    /** Dopo un rifornimento: pieno, oppure batteria all'80% (oltre la ricarica rallenta). */
    fun dopoRifornimento(p: PercorsoGuida, km: Double): Energia = when (p) {
        is PercorsoGuida.AlCarburante -> Energia(p.autonomiaPienoKm, km)
        is PercorsoGuida.Elettrico -> Energia(p.autonomiaPienoKm * Elettrico.SOGLIA_LENTA, km)
    }

    /** L'etichetta della lista: "Gasolio self", "Colonnine veloci". */
    fun etichetta(p: PercorsoGuida): String = when (p) {
        is PercorsoGuida.AlCarburante -> "${p.carburante.etichetta} ${if (p.self) "self" else "servito"}"
        is PercorsoGuida.Elettrico -> "Colonnine veloci"
    }

    /** Autonomia di partenza da un livello (0..1). */
    fun energiaIniziale(p: PercorsoGuida, livello: Double): Energia = Energia(p.autonomiaPienoKm * livello.coerceIn(0.0, 1.0), 0.0)

    /** "~170 km": arrotondata a 10 km, perche e una stima. */
    fun autonomiaTesto(km: Double): String = "~${max(0, (km / 10).roundToInt() * 10)} km"
}
