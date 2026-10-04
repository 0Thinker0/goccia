package it.goccia.app.dati

// Colonnine di ricarica pubblicate dalla pipeline: dalla Piattaforma Unica Nazionale (PUN, GSE)
// o, se la PUN non risponde, da OpenStreetMap (licenza ODbL).

/** Tipo di presa, con il codice usato nei file pubblicati. */
enum class Presa(val codice: String, val etichetta: String, val continua: Boolean) {
    CCS2("C", "CCS2", true),
    TYPE2("T", "Type 2", false),
    CHADEMO("H", "CHAdeMO", true),
    DOMESTICA("S", "Presa domestica", false),
    TESLA("X", "Supercharger Tesla", true),
    ALTRA("A", "Altra presa", false),
    ;

    val corrente: String get() = if (continua) "corrente continua" else "corrente alternata"

    companion object {
        fun daCodice(codice: String?): Presa? = entries.firstOrNull { it.codice == codice }

        /** Le prese che si possono scegliere nei filtri. */
        val filtrabili = listOf(CCS2, TYPE2, CHADEMO)
    }
}

data class Connettore(val presa: Presa, val numero: Int, val kw: Double)

/** Le fasce di prezzo delle colonnine: quasi tutti gli operatori fanno pagare cosi. */
enum class ClasseRicarica(val etichetta: String, val sigla: String) {
    AC("lenta, in corrente alternata", "AC"),
    DC("veloce, in corrente continua", "DC"),
    HPC("ultraveloce, da 150 kW", "HPC"),
}

/** Prezzi a consumo (senza abbonamento) dichiarati dal gestore alla PUN, in euro al kWh. */
data class TariffeGestore(val ac: Double?, val dc: Double?, val hpc: Double?) {
    /** Il prezzo per la classe di ricarica; se manca, quello della classe vicina in continua. */
    fun per(classe: ClasseRicarica): Double? = when (classe) {
        ClasseRicarica.AC -> ac
        ClasseRicarica.DC -> dc ?: hpc
        ClasseRicarica.HPC -> hpc ?: dc
    }
}

data class Colonnina(
    val id: String,
    val lat: Double,
    val lon: Double,
    val nome: String?,
    val operatore: String?,
    /** potenza massima in kW, null se OpenStreetMap non la indica */
    val kw: Double?,
    val connettori: List<Connettore>,
    val h24: Boolean = false,
    val soloClienti: Boolean = false,
    val gratuita: Boolean = false,
    /** la potenza e dedotta dal tipo di presa, non indicata */
    val potenzaStimata: Boolean = false,
    val indirizzo: String? = null,
    val orari: String? = null,
    /** identificativi dei punti di ricarica (EVSE) nella PUN, per lo stato in tempo reale */
    val punti: List<String> = emptyList(),
    val tariffeGestore: TariffeGestore? = null,
    /** il gestore trasmette alla PUN lo stato dei punti in tempo reale */
    val tempoReale: Boolean = false,
) {
    val coordinate: Coordinate get() = Coordinate(lat, lon)

    /** Viene dalla Piattaforma Unica Nazionale (gli id di OpenStreetMap iniziano con n, w o r). */
    val daPun: Boolean get() = id.startsWith("p")

    val titolo: String get() = nome ?: operatore ?: "Colonnina di ricarica"

    /** Ha almeno una presa in corrente continua (ricarica veloce). */
    val continua: Boolean get() = connettori.any { it.presa.continua } || (connettori.isEmpty() && (kw ?: 0.0) > 43)

    val classe: ClasseRicarica
        get() = when {
            continua && (kwContinua ?: 0.0) >= 150 -> ClasseRicarica.HPC
            continua -> ClasseRicarica.DC
            else -> ClasseRicarica.AC
        }

    /** La potenza piu alta in corrente continua. */
    val kwContinua: Double?
        get() = connettori.filter { it.presa.continua }.maxOfOrNull { it.kw } ?: kw?.takeIf { connettori.isEmpty() && it > 43 }

    /** La potenza piu alta in corrente alternata. */
    val kwAlternata: Double?
        get() = connettori.filter { !it.presa.continua }.maxOfOrNull { it.kw } ?: kw?.takeIf { connettori.isEmpty() && it <= 43 }

    fun ha(presa: Presa): Boolean = connettori.any { it.presa == presa }

    /**
     * Si puo usare con almeno una delle prese indicate (nessun filtro = tutte). Le colonnine
     * senza prese indicate su OpenStreetMap restano: sono vere, solo descritte male.
     */
    fun compatibile(prese: Set<Presa>): Boolean = prese.isEmpty() || connettori.isEmpty() || connettori.any { it.presa in prese }

    /** "CCS2 ×4 · Type 2 ×2" */
    val descrizionePrese: String
        get() = connettori.joinToString(" · ") { if (it.numero > 1) "${it.presa.etichetta} ×${it.numero}" else it.presa.etichetta }
            .ifBlank { "Prese non indicate" }
}

/** Stime delle tariffe finche l'utente non inserisce le sue (aggiornate dalla pipeline). */
data class StimeTariffe(
    val casa: Double = 0.24,
    val notaCasa: String = "media nazionale per le famiglie, tutto compreso",
    val ac: Double = 0.64,
    val dc: Double = 0.73,
    val hpc: Double = 0.76,
    val notaColonnine: String = "prezzi medi a consumo",
)

data class IndiceColonnine(
    val generato: String,
    val conteggio: Int,
    val passo: Double,
    /** tessere pubblicate, con il numero di colonnine */
    val tessere: Map<String, Int>,
    val stime: StimeTariffe,
    /** [FONTE_PUN] o [FONTE_OSM] */
    val fonte: String = FONTE_OSM,
) {
    val daPun: Boolean get() = fonte == FONTE_PUN
}

const val FONTE_PUN = "PUN"
const val FONTE_OSM = "OpenStreetMap"
