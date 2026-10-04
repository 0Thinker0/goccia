package it.goccia.app.logica

import it.goccia.app.dati.Abitudine
import it.goccia.app.dati.ClasseRicarica
import it.goccia.app.dati.Colonnina
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.ModoTariffa
import it.goccia.app.dati.Presa
import it.goccia.app.dati.StimeTariffe
import it.goccia.app.dati.TariffaCasa
import it.goccia.app.dati.TariffeColonnine
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** Da dove arriva il prezzo dell'energia di casa. */
enum class FonteTariffa(val etichetta: String) {
    TUA("La tua tariffa"),
    BOLLETTA("Dalla bolletta"),
    STIMA("Stima"),
    FOTOVOLTAICO("Fotovoltaico"),
}

data class PrezzoCasa(val euroKwh: Double, val fonte: FonteTariffa)

/** Da dove arriva il prezzo di una colonnina. */
enum class OrigineTariffa {
    /** quella del tuo abbonamento o della tua app, per tutte le colonnine della stessa classe */
    TUA,

    /** il prezzo a consumo dichiarato dal gestore alla Piattaforma Unica Nazionale */
    GESTORE,

    /** la media nazionale per la classe di ricarica */
    STIMA,
}

data class TariffaUsata(val euroKwh: Double, val origine: OrigineTariffa)

/** Come ricarica davvero l'auto a una colonnina: potenza utile e tipo di corrente. */
data class Erogazione(val kw: Double, val continua: Boolean)

data class ColonninaVicina(val colonnina: Colonnina, val distanzaKm: Double)

/** Le colonnine attorno a un punto, gia filtrate, con il raggio che e servito per trovarne abbastanza. */
data class ZonaColonnine(val vicine: List<ColonninaVicina>, val raggioKm: Int)

/** Una colonnina vicino al percorso di un viaggio. */
data class ColonninaSulPercorso(
    val colonnina: Colonnina,
    /** a che km del percorso si trova */
    val km: Double,
    /** distanza in linea d'aria dal percorso */
    val distanzaKm: Double,
    /** minuti in piu per andarci e tornare sul percorso */
    val deviazioneMin: Int,
)

data class SostaRicarica(
    val punto: ColonninaSulPercorso,
    val erogazione: Erogazione,
    /** batteria all'arrivo e alla ripartenza, da 0 a 1 */
    val arrivo: Double,
    val ripartenza: Double,
    val kwh: Double,
    val minuti: Int,
    /** €/kWh usati per il costo */
    val tariffa: Double,
) {
    val costo: Double get() = kwh * tariffa
}

data class PianoElettrico(
    val lunghezzaKm: Double,
    val partenza: Double,
    val arrivoMinimo: Double,
    val soste: List<SostaRicarica>,
    /** batteria a destinazione, da 0 a 1 (negativa se non ci si arriva) */
    val arrivo: Double,
    /** falso se lungo la strada mancano colonnine veloci raggiungibili */
    val completo: Boolean,
    /** dove si resterebbe senza carica (km dalla partenza), se il piano non e completo */
    val kmCritico: Double?,
    /** altre colonnine veloci lungo la strada, ordinate per km */
    val alternative: List<ColonninaSulPercorso>,
) {
    val minutiSoste: Int get() = soste.sumOf { it.minuti + it.punto.deviazioneMin }
    val costo: Double get() = soste.sumOf { it.costo }
    val kwhRicaricati: Double get() = soste.sumOf { it.kwh }
}

/**
 * Tutta l'energia di un viaggio: quella caricata a casa prima di partire ([kwhCasa]) e quella
 * delle soste ([kwhSoste]), con i rispettivi costi in euro.
 */
data class CostoViaggio(
    val kwhViaggio: Double,
    val kwhCasa: Double,
    val kwhSoste: Double,
    val casa: Double,
    val soste: Double,
) {
    val totale: Double get() = casa + soste
}

/** I conti delle auto elettriche: tariffe, costi, tempi di ricarica e soste nei viaggi. */
object Elettrico {
    /** Sopra questo livello la ricarica rapida rallenta molto: nei viaggi ci si ferma prima. */
    const val SOGLIA_LENTA = 0.8

    /** Mai sotto questo livello arrivando a una colonnina durante un viaggio. */
    const val RISERVA_VIAGGIO = 0.08

    /** Potenza minima perche una colonnina valga una sosta durante un viaggio (kW). */
    const val POTENZA_SOSTA = 40.0

    // ------------------------------------------------------------------ tariffe

    fun daBolletta(t: TariffaCasa): Double? = if (t.bollettaEuro > 0 && t.bollettaKwh > 0) t.bollettaEuro / t.bollettaKwh else null

    /** Il prezzo dell'energia presa dalla rete di casa, tutto compreso. */
    fun prezzoRete(t: TariffaCasa, stima: Double): PrezzoCasa = when (t.modo) {
        ModoTariffa.PREZZO -> PrezzoCasa(
            if (t.bioraria) (if (t.abitudine == Abitudine.GIORNO) t.f1 else t.f23) else t.prezzo,
            FonteTariffa.TUA,
        )
        ModoTariffa.BOLLETTA -> daBolletta(t)?.let { PrezzoCasa(it, FonteTariffa.BOLLETTA) } ?: PrezzoCasa(stima, FonteTariffa.STIMA)
        ModoTariffa.STIMA -> PrezzoCasa(stima, FonteTariffa.STIMA)
    }

    /** Il prezzo di un kWh ricaricato a casa: con i pannelli conta quanto rinunci a guadagnare vendendolo. */
    fun prezzoCasa(t: TariffaCasa, stima: Double): PrezzoCasa =
        if (t.abitudine == Abitudine.FOTOVOLTAICO) PrezzoCasa(t.fotovoltaico, FonteTariffa.FOTOVOLTAICO) else prezzoRete(t, stima)

    /** Quanto costa un kWh messo in batteria a casa, con le perdite di ricarica. */
    fun costoKwhCasa(t: TariffaCasa, stima: Double): Double = prezzoCasa(t, stima).euroKwh / efficienza(t)

    fun efficienza(t: TariffaCasa): Double = 1 - t.perdite.coerceIn(0, 40) / 100.0

    /** kWh da prelevare dalla rete per metterne [kwh] in batteria. */
    fun kwhDallaRete(t: TariffaCasa, kwh: Double): Double = kwh / efficienza(t)

    /**
     * La tariffa di una colonnina: la tua (abbonamento o app) se l'hai messa per la sua classe,
     * altrimenti il prezzo a consumo dichiarato dal gestore alla PUN, altrimenti la stima.
     */
    fun tariffa(c: Colonnina, tue: TariffeColonnine, stime: StimeTariffe): TariffaUsata {
        val classe = c.classe
        if (tariffaPersonale(classe, tue)) return TariffaUsata(tariffaColonnina(classe, tue, stime), OrigineTariffa.TUA)
        c.tariffeGestore?.per(classe)?.let { return TariffaUsata(it, OrigineTariffa.GESTORE) }
        return TariffaUsata(tariffaColonnina(classe, TariffeColonnine(), stime), OrigineTariffa.STIMA)
    }

    fun tariffaColonnina(classe: ClasseRicarica, tue: TariffeColonnine, stime: StimeTariffe): Double = when (classe) {
        ClasseRicarica.AC -> tue.ac ?: stime.ac
        ClasseRicarica.DC -> tue.dc ?: stime.dc
        ClasseRicarica.HPC -> tue.hpc ?: stime.hpc
    }

    /** La tariffa della classe e quella inserita dall'utente (non una stima). */
    fun tariffaPersonale(classe: ClasseRicarica, tue: TariffeColonnine): Boolean = when (classe) {
        ClasseRicarica.AC -> tue.ac != null
        ClasseRicarica.DC -> tue.dc != null
        ClasseRicarica.HPC -> tue.hpc != null
    }

    fun conTariffa(tue: TariffeColonnine, classe: ClasseRicarica, valore: Double?): TariffeColonnine = when (classe) {
        ClasseRicarica.AC -> tue.copy(ac = valore)
        ClasseRicarica.DC -> tue.copy(dc = valore)
        ClasseRicarica.HPC -> tue.copy(hpc = valore)
    }

    /** Costo per 100 km con un prezzo al kWh (gia comprensivo delle perdite, se servono). */
    fun costoPer100(consumoKwh: Double, euroKwh: Double): Double = consumoKwh * euroKwh

    /**
     * Costo di tutta l'energia che il viaggio consuma: quella delle soste alla loro tariffa
     * (zero se la colonnina e gratuita) e il resto, partito dalla batteria caricata a casa,
     * a [costoKwhCasa] (euro per kWh in batteria, perdite incluse).
     */
    fun costoViaggio(lunghezzaKm: Double, consumoKwh100: Double, piano: PianoElettrico, costoKwhCasa: Double): CostoViaggio {
        val viaggio = lunghezzaKm * consumoKwh100 / 100
        val soste = piano.kwhRicaricati
        val casa = (viaggio - soste).coerceAtLeast(0.0)
        val euroSoste = piano.soste.sumOf { if (it.punto.colonnina.gratuita) 0.0 else it.costo }
        return CostoViaggio(viaggio, casa, soste, casa * costoKwhCasa, euroSoste)
    }

    // ------------------------------------------------------------------ ricarica

    /**
     * Con che potenza ricarica l'auto a questa colonnina, usando solo le prese compatibili:
     * la continua se c'e ed e piu veloce, altrimenti l'alternata (limitata dal caricatore di bordo).
     */
    fun erogazione(c: Colonnina, prese: Set<Presa>, acKw: Double, dcKw: Double): Erogazione? {
        if (c.connettori.isEmpty()) {
            val kw = c.kw ?: return null
            return if (kw > 43) Erogazione(min(kw, dcKw), true) else Erogazione(min(kw, acKw), false)
        }
        val utili = if (prese.isEmpty()) c.connettori else c.connettori.filter { it.presa in prese }
        val dc = utili.filter { it.presa.continua }.maxOfOrNull { it.kw }?.let { min(it, dcKw) }
        val ac = utili.filter { !it.presa.continua }.maxOfOrNull { it.kw }?.let { min(it, acKw) }
        return when {
            dc != null && (ac == null || dc >= ac) -> Erogazione(dc, true)
            ac != null -> Erogazione(ac, false)
            else -> null
        }
    }

    /**
     * Minuti per ricaricare da [da] ad [a] (livelli da 0 a 1). In continua la potenza media fino
     * all'80% e circa il 72% della massima, poi crolla; in alternata resta quasi costante.
     */
    fun minutiRicarica(capacitaKwh: Double, da: Double, a: Double, e: Erogazione): Int {
        if (a <= da || e.kw <= 0) return 0
        val sotto = (min(a, SOGLIA_LENTA) - da).coerceAtLeast(0.0) * capacitaKwh
        val sopra = (a - max(da, SOGLIA_LENTA)).coerceAtLeast(0.0) * capacitaKwh
        val (veloce, lenta) = if (e.continua) e.kw * 0.72 to min(e.kw * 0.35, 40.0) else e.kw * 0.95 to e.kw * 0.8
        val ore = sotto / veloce + sopra / lenta
        return ceil(ore * 60 - 1e-6).toInt()
    }

    // ------------------------------------------------------------------ colonnine vicine

    fun filtra(colonnine: List<Colonnina>, prese: Set<Presa>, potenzaMinima: Int): List<Colonnina> =
        colonnine.filter { it.compatibile(prese) && (potenzaMinima <= 0 || (it.kw ?: 0.0) >= potenzaMinima) }

    /**
     * Le colonnine compatibili attorno a un punto: partiamo dal raggio scelto e lo allarghiamo
     * fino a trovarne almeno [minimo] (ma non oltre 30 km).
     */
    fun zona(
        colonnine: List<Colonnina>,
        centro: Coordinate,
        raggioKm: Int,
        prese: Set<Presa>,
        potenzaMinima: Int,
        minimo: Int = 5,
    ): ZonaColonnine {
        val tutte = filtra(colonnine, prese, potenzaMinima)
            .map { ColonninaVicina(it, Geo.distanzaKm(centro, it.coordinate)) }
            .filter { it.distanzaKm <= 30.0 }
            .sortedBy { it.distanzaKm }
        var raggio = raggioKm.coerceAtLeast(1)
        while (raggio < 30 && tutte.count { it.distanzaKm <= raggio } < minimo) raggio = min(30, raggio * 2)
        return ZonaColonnine(tutte.filter { it.distanzaKm <= raggio }, raggio)
    }

    // ------------------------------------------------------------------ tessere

    /** La tessera di mezzo grado che contiene un punto, come la chiama la pipeline ("89_22"). */
    fun chiaveTessera(lat: Double, lon: Double, passo: Double = 0.5): String =
        "${floor(lat / passo).toInt()}_${floor(lon / passo).toInt()}"

    /** Le tessere che coprono un cerchio di [km] attorno al punto. */
    fun tessereAttorno(centro: Coordinate, km: Double, passo: Double = 0.5): Set<String> {
        val dLat = km / 111.32
        val dLon = km / (111.32 * cos(Math.toRadians(centro.lat)).coerceAtLeast(0.1))
        val chiavi = LinkedHashSet<String>()
        var la = floor((centro.lat - dLat) / passo).toInt()
        val laFine = floor((centro.lat + dLat) / passo).toInt()
        while (la <= laFine) {
            var lo = floor((centro.lon - dLon) / passo).toInt()
            val loFine = floor((centro.lon + dLon) / passo).toInt()
            while (lo <= loFine) {
                chiavi += "${la}_$lo"
                lo++
            }
            la++
        }
        return chiavi
    }

    /** Le tessere attraversate da un percorso, con un margine di [km] ai lati. */
    fun tessereLungo(campioni: List<PuntoPercorso>, km: Double, passo: Double = 0.5): Set<String> {
        val chiavi = LinkedHashSet<String>()
        campioni.forEachIndexed { i, p ->
            if (i % 8 == 0 || i == campioni.lastIndex) chiavi += tessereAttorno(Coordinate(p.lat, p.lon), km, passo)
        }
        return chiavi
    }

    // ------------------------------------------------------------------ viaggi

    /** Le colonnine entro [maxDistanzaKm] dal percorso, con la posizione lungo il percorso. */
    fun lungoIlPercorso(colonnine: List<Colonnina>, campioni: List<PuntoPercorso>, maxDistanzaKm: Double): List<ColonninaSulPercorso> {
        if (campioni.isEmpty()) return emptyList()
        val cella = 0.05
        val griglia = HashMap<Long, MutableList<PuntoPercorso>>()
        fun chiave(cLat: Long, cLon: Long): Long = (cLat shl 32) or (cLon and 0xffffffffL)
        for (p in campioni) griglia.getOrPut(chiave(floor(p.lat / cella).toLong(), floor(p.lon / cella).toLong())) { ArrayList() } += p
        val risultato = ArrayList<ColonninaSulPercorso>()
        for (c in colonnine) {
            val cLat = floor(c.lat / cella).toLong()
            val cLon = floor(c.lon / cella).toLong()
            var migliore: PuntoPercorso? = null
            var distanza = Double.MAX_VALUE
            for (dl in -1L..1L) for (dn in -1L..1L) {
                val vicini = griglia[chiave(cLat + dl, cLon + dn)] ?: continue
                for (p in vicini) {
                    val x = (p.lon - c.lon) * cos(Math.toRadians((p.lat + c.lat) / 2))
                    val y = p.lat - c.lat
                    val km = sqrt(x * x + y * y) * 111.32
                    if (km < distanza) {
                        distanza = km
                        migliore = p
                    }
                }
            }
            val punto = migliore ?: continue
            if (distanza > maxDistanzaKm) continue
            risultato += ColonninaSulPercorso(c, punto.km, distanza, Tragitto.deviazioneMinuti(distanza, autostradale = false))
        }
        return risultato.sortedBy { it.km }
    }

    /**
     * Le soste di ricarica: si parte con [partenza] e si vuole arrivare con almeno [arrivoMinimo].
     * Fra le colonnine veloci raggiungibili scegliamo quella piu potente nella seconda meta del
     * tratto percorribile (meno soste), a parita la meno fuori strada; li si ricarica quanto basta
     * per arrivare, senza superare l'80% se piu avanti ci sono altre colonnine.
     */
    fun pianifica(
        lungo: List<ColonninaSulPercorso>,
        lunghezzaKm: Double,
        capacitaKwh: Double,
        consumoKwh100: Double,
        acKw: Double,
        dcKw: Double,
        prese: Set<Presa>,
        partenza: Double,
        arrivoMinimo: Double,
        tariffa: (Colonnina) -> Double,
    ): PianoElettrico {
        val perKm = consumoKwh100 / 100.0 / capacitaKwh
        val veloci = lungo.mapNotNull { p ->
            erogazione(p.colonnina, prese, acKw, dcKw)?.takeIf { it.continua && it.kw >= POTENZA_SOSTA }?.let { p to it }
        }
        val soste = ArrayList<SostaRicarica>()
        var livello = partenza
        var km = 0.0
        var completo = true
        var kmCritico: Double? = null
        var giri = 0
        while (giri++ < 12) {
            if (livello - (lunghezzaKm - km) * perKm >= arrivoMinimo) break
            val limite = km + (livello - RISERVA_VIAGGIO) / perKm
            val candidati = veloci.filter { (p, _) -> p.km > km + 1 && p.km + fuoriStrada(p) / 2 <= limite }
            if (candidati.isEmpty()) {
                completo = false
                kmCritico = (km + livello / perKm).coerceAtMost(lunghezzaKm)
                break
            }
            val meta = km + (limite - km) * 0.5
            val finestra = candidati.filter { it.first.km >= meta }.ifEmpty { candidati }
            val (scelta, e) = finestra.maxWith(
                compareBy<Pair<ColonninaSulPercorso, Erogazione>> { floor(it.second.kw / 25) }
                    .thenByDescending { it.first.deviazioneMin }
                    .thenBy { it.first.km },
            )
            val andata = fuoriStrada(scelta) / 2
            val arrivo = livello - (scelta.km - km + andata) * perKm
            val serve = (lunghezzaKm - scelta.km + andata) * perKm + arrivoMinimo + 0.03
            // oltre l'80% si carica solo se piu avanti non c'e un'altra colonnina veloce raggiungibile
            val altreDopo = veloci.any { (q, _) -> q.km > scelta.km + 1 && q.km <= scelta.km + (SOGLIA_LENTA - RISERVA_VIAGGIO) / perKm }
            val tetto = if (serve > SOGLIA_LENTA && !altreDopo) 1.0 else SOGLIA_LENTA
            val obiettivo = max(min(tetto, serve), min(tetto, arrivo + 0.1))
            val kwh = (obiettivo - arrivo) * capacitaKwh
            soste += SostaRicarica(scelta, e, arrivo, obiettivo, kwh, minutiRicarica(capacitaKwh, arrivo, obiettivo, e), tariffa(scelta.colonnina))
            livello = obiettivo - andata * perKm
            km = scelta.km
        }
        val arrivoFinale = livello - (lunghezzaKm - km) * perKm
        val scelte = soste.map { it.punto.colonnina.id }.toSet()
        val alternative = veloci.filter { it.first.colonnina.id !in scelte && it.first.km <= lunghezzaKm }
            .sortedWith(compareByDescending<Pair<ColonninaSulPercorso, Erogazione>> { floor(it.second.kw / 25) }.thenBy { it.first.deviazioneMin })
            .take(6)
            .map { it.first }
            .sortedBy { it.km }
        return PianoElettrico(
            lunghezzaKm = lunghezzaKm,
            partenza = partenza,
            arrivoMinimo = arrivoMinimo,
            soste = soste,
            arrivo = arrivoFinale,
            completo = completo && arrivoFinale >= 0,
            kmCritico = kmCritico,
            alternative = alternative,
        )
    }

    /** Km in piu per raggiungere la colonnina e tornare sul percorso (a 50 km/h). */
    private fun fuoriStrada(p: ColonninaSulPercorso): Double = p.distanzaKm * 2 * FATTORE_STRADA
}
