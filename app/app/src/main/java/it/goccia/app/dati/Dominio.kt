package it.goccia.app.dati

data class Coordinate(val lat: Double, val lon: Double)

/** Un comune con almeno un distributore (serve per cercare un posto anche senza GPS). */
data class Comune(val nome: String, val provincia: String, val lat: Double, val lon: Double, val distributori: Int) {
    val etichetta: String get() = "$nome ($provincia)"
    val coordinate: Coordinate get() = Coordinate(lat, lon)
}

enum class Carburante(val codice: String, val etichetta: String, val unita: String) {
    BENZINA("B", "Benzina", "€/l"),
    GASOLIO("G", "Gasolio", "€/l"),
    GPL("L", "GPL", "€/l"),
    METANO("M", "Metano", "€/kg");

    /** GPL e metano quasi ovunque sono solo "servito": non ha senso distinguere. */
    val haSelf: Boolean get() = this == BENZINA || this == GASOLIO

    /** "€/l self", "€/l servito", "€/kg" */
    fun unitaCon(self: Boolean): String = when {
        !haSelf -> unita
        self -> "$unita self"
        else -> "$unita servito"
    }

    companion object {
        fun daCodice(codice: String?): Carburante? = entries.firstOrNull { it.codice == codice }
    }
}

data class Prezzo(val millesimi: Int, val comunicato: Long) {
    val euro: Double get() = millesimi / 1000.0
}

data class PrezziCarburante(val self: Prezzo?, val servito: Prezzo?)

data class Speciale(val nome: String, val famiglia: Carburante?, val prezzo: Prezzo, val self: Boolean)

data class Distributore(
    val id: Long,
    val bandiera: String,
    val nome: String,
    val indirizzo: String,
    val comune: String,
    val provincia: String,
    val autostradale: Boolean,
    val lat: Double,
    val lon: Double,
    val prezzi: Map<Carburante, PrezziCarburante>,
    val speciali: List<Speciale>,
) {
    /** "Esso · Via Emilia 214", oppure il nome se manca l'indirizzo. */
    val titolo: String
        get() = listOf(bandiera, indirizzo.ifBlank { nome }).filter { it.isNotBlank() }.joinToString(" · ")

    val pompaBianca: Boolean get() = bandiera.equals("Pompa bianca", ignoreCase = true)

    val coordinate: Coordinate get() = Coordinate(lat, lon)

    /** Il nome da mostrare in grande: la bandiera, o il nome dell'impianto per le pompe bianche. */
    val intestazione: String
        get() = when {
            pompaBianca && nome.isNotBlank() -> nome
            bandiera.isNotBlank() -> bandiera
            else -> nome
        }
}

/** Media di una zona per carburante e modalita. */
data class Media(val self: Int?, val servito: Int?)

data class Provincia(
    val sigla: String,
    val nome: String,
    val minLat: Double,
    val minLon: Double,
    val maxLat: Double,
    val maxLon: Double,
    val centroLat: Double,
    val centroLon: Double,
    val medie: Map<Carburante, Media>,
)

data class Indice(
    val estrazione: String,
    val generato: String,
    val province: List<Provincia>,
    val medieNazionali: Map<Carburante, Media>,
    val giorniNazionali: List<String>,
    val storicoNazionale: Map<Carburante, SerieMedie>,
)

data class SerieMedie(val self: List<Int?>, val servito: List<Int?>)

data class StoricoZona(val giorni: List<String>, val medie: Map<Carburante, SerieMedie>)

data class StoricoDistributore(
    val giorni: List<String>,
    val giornaliero: Map<Carburante, List<Int?>>,
    val mesi: List<String>,
    val mensile: Map<Carburante, List<Int?>>,
)

// --- conversioni dai file JSON ---

private fun Map<String, MediaDto>.inMedie(): Map<Carburante, Media> =
    mapNotNull { (codice, m) -> Carburante.daCodice(codice)?.let { it to Media(m.s?.p, m.v?.p) } }.toMap()

private fun Map<String, SerieDto>.inSerie(lunghezza: Int): Map<Carburante, SerieMedie> =
    mapNotNull { (codice, s) ->
        Carburante.daCodice(codice)?.let {
            it to SerieMedie(s.s ?: List(lunghezza) { null }, s.v ?: List(lunghezza) { null })
        }
    }.toMap()

fun IndiceDto.inDominio(): Indice = Indice(
    estrazione = estrazione,
    generato = generato,
    province = province.map { (sigla, p) ->
        Provincia(
            sigla = sigla,
            nome = p.nome,
            minLat = p.bbox.getOrElse(0) { 0.0 },
            minLon = p.bbox.getOrElse(1) { 0.0 },
            maxLat = p.bbox.getOrElse(2) { 0.0 },
            maxLon = p.bbox.getOrElse(3) { 0.0 },
            centroLat = p.centro.getOrElse(0) { 0.0 },
            centroLon = p.centro.getOrElse(1) { 0.0 },
            medie = p.medie.inMedie(),
        )
    }.sortedBy { it.nome },
    medieNazionali = nazionale.medie.inMedie(),
    giorniNazionali = nazionale.giorni,
    storicoNazionale = nazionale.storico.inSerie(nazionale.giorni.size),
)

fun ImpiantoDto.inDominio(provincia: String): Distributore = Distributore(
    id = id,
    bandiera = b,
    nome = n,
    indirizzo = i,
    comune = c,
    provincia = provincia,
    autostradale = t == "A",
    lat = la,
    lon = lo,
    prezzi = p.mapNotNull { (codice, prezzo) ->
        Carburante.daCodice(codice)?.let { carburante ->
            carburante to PrezziCarburante(
                self = prezzo.s?.let { Prezzo(it, prezzo.st ?: 0) },
                servito = prezzo.v?.let { Prezzo(it, prezzo.vt ?: 0) },
            )
        }
    }.toMap(),
    speciali = x.map { Speciale(it.d, Carburante.daCodice(it.f), Prezzo(it.p, it.t), it.s == 1) },
)

fun StoricoDto.inDominio(): StoricoZona = StoricoZona(giorni, medie.inSerie(giorni.size))

fun CronologiaDto.perDistributore(id: Long): StoricoDistributore? {
    val voce = impianti[id.toString()] ?: return null
    return StoricoDistributore(
        giorni = giorni,
        giornaliero = voce.g.mapNotNull { (c, v) -> Carburante.daCodice(c)?.let { it to v } }.toMap(),
        mesi = mesi,
        mensile = voce.m.mapNotNull { (c, v) -> Carburante.daCodice(c)?.let { it to v } }.toMap(),
    )
}
