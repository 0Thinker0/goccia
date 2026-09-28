package it.goccia.app.dati

import kotlinx.serialization.Serializable

// Tutto quello che l'utente inserisce vive solo sul telefono (file utente.json).

@Serializable
enum class Alimentazione(val etichetta: String) {
    BENZINA("Benzina"),
    GASOLIO("Gasolio"),
    GPL("GPL"),
    METANO("Metano"),
    BENZINA_GPL("Benzina + GPL"),
    BENZINA_METANO("Benzina + metano"),
    ELETTRICA("Elettrica"),
    IBRIDA_PLUGIN("Ibrida plug-in");

    /** Il carburante che conviene cercare per quest'auto (per le bifuel quello piu economico). */
    val carburante: Carburante?
        get() = when (this) {
            BENZINA, IBRIDA_PLUGIN -> Carburante.BENZINA
            GASOLIO -> Carburante.GASOLIO
            GPL, BENZINA_GPL -> Carburante.GPL
            METANO, BENZINA_METANO -> Carburante.METANO
            ELETTRICA -> null
        }

    val elettrica: Boolean get() = this == ELETTRICA

    val unitaCapienza: String
        get() = when (this) {
            ELETTRICA -> "kWh"
            METANO, BENZINA_METANO -> "kg"
            else -> "l"
        }

    val unitaConsumo: String
        get() = when (this) {
            ELETTRICA -> "kWh/100 km"
            METANO, BENZINA_METANO -> "kg/100 km"
            else -> "l/100 km"
        }

    /** Valori tipici per partire: l'utente li corregge, e il consumo poi lo calcoliamo dai pieni. */
    val capienzaTipica: Double
        get() = when (this) {
            ELETTRICA -> 60.0
            METANO, BENZINA_METANO -> 15.0
            GPL, BENZINA_GPL -> 40.0
            else -> 50.0
        }

    val consumoTipico: Double
        get() = when (this) {
            ELETTRICA -> 16.0
            METANO, BENZINA_METANO -> 4.5
            GPL, BENZINA_GPL -> 8.0
            GASOLIO -> 5.4
            else -> 6.5
        }
}

@Serializable
data class Auto(
    val id: String,
    val nome: String,
    val alimentazione: Alimentazione,
    /** litri, kg o kWh */
    val capienza: Double,
    /** per 100 km, nella stessa unita della capienza */
    val consumo: Double,
    /** livello del serbatoio indicato dall'utente, da 0 a 1 */
    val livello: Double = 0.5,
    /** quando e stato indicato il livello (epoch millis): da li in poi lo stimiamo */
    val livelloIl: Long = 0,
)

@Serializable
data class Rifornimento(
    val id: String,
    val quando: Long,
    val autoId: String? = null,
    val distributoreId: Long? = null,
    val provincia: String? = null,
    val distributore: String,
    val carburante: String,
    val self: Boolean = true,
    /** prezzo pagato, millesimi di euro */
    val prezzo: Int,
    val litri: Double,
    val importo: Double,
    val km: Int? = null,
    val pieno: Boolean = true,
    /** media della zona quel giorno, millesimi: serve per calcolare il risparmio */
    val mediaZona: Int? = null,
) {
    val risparmio: Double? get() = mediaZona?.let { (it - prezzo) / 1000.0 * litri }
}

@Serializable
enum class TipoLuogo(val etichetta: String) {
    CASA("Casa"),
    LAVORO("Lavoro"),
    ALTRO("Altro"),
}

@Serializable
data class Luogo(
    val id: String,
    val nome: String,
    val tipo: TipoLuogo,
    val lat: Double,
    val lon: Double,
    /** "vicino a Bologna (BO)" */
    val descrizione: String = "",
) {
    val coordinate: Coordinate get() = Coordinate(lat, lon)
}

@Serializable
data class Preferito(
    val id: Long,
    val provincia: String,
    val titolo: String,
    val lat: Double,
    val lon: Double,
    val avvisaCalo: Boolean = true,
    /** ultimo prezzo controllato per l'avviso di calo (millesimi) e a cosa si riferisce */
    val ultimoPrezzo: Int? = null,
    val ultimoCarburante: String? = null,
    val ultimaEstrazione: String? = null,
)

@Serializable
data class Avviso(
    val id: String,
    val carburante: String,
    /** soglia in millesimi: avvisa quando in zona si trova un prezzo uguale o piu basso */
    val soglia: Int,
    /** luogo salvato attorno a cui controllare; null = ultima posizione nota */
    val luogoId: String? = null,
    val raggioKm: Int = 5,
    val attivo: Boolean = true,
    val creatoIl: Long = 0,
    /** miglior prezzo gia notificato finche si resta sotto soglia: evita notifiche ripetute */
    val ultimoMinimo: Int? = null,
    val ultimaNotificaIl: Long? = null,
    /** "Aurora a 1,689" */
    val ultimoEsito: String? = null,
    val ultimoControlloIl: Long? = null,
    /** il prezzo piu basso trovato all'ultimo controllo, anche se sopra soglia */
    val minimoVisto: Int? = null,
    val ultimaEstrazione: String? = null,
)

@Serializable
enum class AppNavigazione(val etichetta: String) {
    CHIEDI("Chiedi ogni volta"),
    GOOGLE_MAPS("Google Maps"),
    WAZE("Waze"),
}

@Serializable
data class Impostazioni(
    /** carburante mostrato; null = quello dell'auto attiva */
    val carburante: String? = null,
    val preferisciSelf: Boolean = true,
    val raggioKm: Int = 5,
    val escludiAutostrade: Boolean = false,
    val avvisiPrezzo: Boolean = true,
    val caloPreferiti: Boolean = true,
    val orarioSilenzioso: Boolean = true,
    val navigazione: AppNavigazione = AppNavigazione.CHIEDI,
)

/** Un viaggio calcolato di recente, per ripeterlo con un tocco. */
@Serializable
data class ViaggioRecente(
    val partenza: String,
    val partenzaLat: Double,
    val partenzaLon: Double,
    val arrivo: String,
    val arrivoLat: Double,
    val arrivoLon: Double,
    val quando: Long,
)

@Serializable
data class PosizioneSalvata(val lat: Double, val lon: Double, val quando: Long) {
    val coordinate: Coordinate get() = Coordinate(lat, lon)
}

@Serializable
data class DatiUtente(
    val introduzioneVista: Boolean = false,
    val auto: List<Auto> = emptyList(),
    val autoAttiva: String? = null,
    val preferiti: List<Preferito> = emptyList(),
    val avvisi: List<Avviso> = emptyList(),
    val luoghi: List<Luogo> = emptyList(),
    val rifornimenti: List<Rifornimento> = emptyList(),
    val impostazioni: Impostazioni = Impostazioni(),
    val ultimaPosizione: PosizioneSalvata? = null,
    val viaggiRecenti: List<ViaggioRecente> = emptyList(),
    /** quando l'utente ha chiuso l'invito a sostenere il progetto */
    val donazioneChiusaIl: Long = 0,
) {
    val autoCorrente: Auto? get() = auto.firstOrNull { it.id == autoAttiva } ?: auto.firstOrNull()

    /** Il carburante da mostrare: scelto dall'utente, altrimenti quello dell'auto. */
    val carburante: Carburante
        get() = impostazioni.carburante?.let { Carburante.daCodice(it) }
            ?: autoCorrente?.alimentazione?.carburante
            ?: Carburante.BENZINA

    fun ePreferito(id: Long): Boolean = preferiti.any { it.id == id }

    fun preferito(id: Long): Preferito? = preferiti.firstOrNull { it.id == id }

    fun luogo(id: String?): Luogo? = id?.let { i -> luoghi.firstOrNull { it.id == i } }

    val casa: Luogo? get() = luoghi.firstOrNull { it.tipo == TipoLuogo.CASA }
}
