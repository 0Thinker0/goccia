package it.goccia.app.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.goccia.app.Contenitore
import it.goccia.app.guida.GuidaInCorso
import it.goccia.app.guida.ServizioGuida
import it.goccia.app.logica.PercorsoGuida
import it.goccia.app.dati.Auto
import it.goccia.app.dati.Avviso
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Colonnina
import it.goccia.app.dati.Comune
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.DatiUtente
import it.goccia.app.dati.Distributore
import it.goccia.app.dati.Impostazioni
import it.goccia.app.dati.Indice
import it.goccia.app.dati.IndiceColonnine
import it.goccia.app.dati.Luogo
import it.goccia.app.dati.Osservaprezzi
import it.goccia.app.dati.PosizioneSalvata
import it.goccia.app.dati.Preferito
import it.goccia.app.dati.Presa
import it.goccia.app.dati.Provincia
import it.goccia.app.dati.Percorso
import it.goccia.app.dati.PercorsoNonTrovato
import it.goccia.app.dati.Rifornimento
import it.goccia.app.dati.SchedaImpianto
import it.goccia.app.dati.StatoColonnina
import it.goccia.app.dati.StimeTariffe
import it.goccia.app.dati.StoricoDistributore
import it.goccia.app.dati.StoricoZona
import it.goccia.app.dati.TariffaCasa
import it.goccia.app.dati.TariffeColonnine
import it.goccia.app.dati.TipoLuogo
import it.goccia.app.dati.ViaggioRecente
import it.goccia.app.logica.ColonninaSulPercorso
import it.goccia.app.logica.Consiglio
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.Elettrico
import it.goccia.app.logica.Geo
import it.goccia.app.logica.ImpiantoLive
import it.goccia.app.logica.LungoIlPercorso
import it.goccia.app.logica.PianoElettrico
import it.goccia.app.logica.PianoViaggio
import it.goccia.app.logica.PuntoPercorso
import it.goccia.app.logica.StatoSerbatoio
import it.goccia.app.logica.TariffaUsata
import it.goccia.app.logica.TempoReale
import it.goccia.app.logica.Territorio
import it.goccia.app.logica.Tragitto
import it.goccia.app.logica.Zona
import it.goccia.app.logica.ZonaColonnine
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val DIECI_MINUTI = 10 * 60_000L

enum class TipoCentro { POSIZIONE, LUOGO, COMUNE, ULTIMA }

/** Il punto attorno a cui cerchiamo i distributori. */
data class Centro(val coordinate: Coordinate, val tipo: TipoCentro, val etichetta: String)

enum class ProblemaPosizione { NESSUNO, PERMESSO_MANCANTE, GPS_SPENTO, NON_TROVATA }

data class StatoDati(
    val indice: Indice? = null,
    val comuni: List<Comune> = emptyList(),
    val centro: Centro? = null,
    val perProvincia: Map<String, List<Distributore>> = emptyMap(),
    /** tutti i distributori caricati (le province attorno al centro e quelle viste sulla mappa) */
    val distributori: List<Distributore> = emptyList(),
    /** primo avvio: non abbiamo ancora dati da mostrare */
    val caricamento: Boolean = true,
    val aggiornando: Boolean = false,
    val cercoPosizione: Boolean = false,
    val problemaPosizione: ProblemaPosizione = ProblemaPosizione.NESSUNO,
    /** stiamo mostrando dati salvati perche la rete non risponde */
    val offline: Boolean = false,
    val scaricatoIl: Long? = null,
    /** nessun dato, nemmeno salvato: serve la connessione */
    val senzaDati: Boolean = false,
    val provincia: Provincia? = null,
    val storicoZona: StoricoZona? = null,
    /** prezzi in vigore letti da Osservaprezzi, per distributore (gia applicati a [distributori]) */
    val live: Map<Long, ImpiantoLive> = emptyMap(),
    /** ultima lettura in tempo reale riuscita (millisecondi) e attorno a dove */
    val tempoRealeIl: Long? = null,
    val tempoRealeCentro: Coordinate? = null,
    /** l'ultima richiesta in tempo reale non e andata: restano i prezzi del file */
    val tempoRealeErrore: Boolean = false,
)

/** Quello che mostrano Home e Lista: i distributori attorno al centro per il carburante scelto. */
data class VistaZona(
    val carburante: Carburante,
    val self: Boolean,
    val zona: Zona,
    val provincia: Provincia?,
    val mediaProvincia: Int?,
    val mediaNazionale: Int?,
    val centro: Centro,
    val adessoMillis: Long,
)

/** Partenza o arrivo di un viaggio. */
data class Tappa(val nome: String, val coordinate: Coordinate, val posizioneAttuale: Boolean = false)

data class RichiestaViaggio(
    val partenza: Tappa,
    val arrivo: Tappa,
    val livello: Double,
    val deviazioneMin: Int,
    val pienoCompleto: Boolean,
    /** solo elettriche: livello minimo della batteria all'arrivo */
    val arrivoMinimo: Double = 0.2,
    /** calcolato per un'auto elettrica: [livello] e la batteria, non il serbatoio */
    val elettrica: Boolean = false,
)

/** Lo stesso viaggio con un'auto a carburante, per confronto. */
/**
 * Lo stesso tragitto con un'auto a carburante: la tua, se ne hai una, altrimenti un'auto media
 * a benzina ([media]). [consumo] e per 100 km, [litri] per il tragitto.
 */
data class ConfrontoCarburante(
    val etichetta: String,
    val litri: Double,
    val unita: String,
    val costo: Double,
    val consumo: Double,
    val media: Boolean,
)

sealed interface StatoViaggio {
    data object Vuoto : StatoViaggio

    data class Calcolo(val fase: String) : StatoViaggio

    data class Pronto(
        val partenza: Tappa,
        val arrivo: Tappa,
        val percorso: Percorso,
        val campioni: List<PuntoPercorso>,
        val lungo: List<LungoIlPercorso>,
        val piano: PianoViaggio,
        val carburante: Carburante,
        val livello: Double,
        val nomeAuto: String?,
        val capienza: Double,
        val consumo: Double = 6.0,
        val pienoCompleto: Boolean = true,
        val self: Boolean = true,
    ) : StatoViaggio

    data class ProntoElettrico(
        val partenza: Tappa,
        val arrivo: Tappa,
        val percorso: Percorso,
        val campioni: List<PuntoPercorso>,
        val lungo: List<ColonninaSulPercorso>,
        val piano: PianoElettrico,
        val auto: Auto,
        val confronto: ConfrontoCarburante?,
    ) : StatoViaggio

    data class Errore(val messaggio: String) : StatoViaggio
}

/** Le colonnine scaricate finora (per tessere di mezzo grado). */
data class StatoColonnine(
    val indice: IndiceColonnine? = null,
    val perTessera: Map<String, List<Colonnina>> = emptyMap(),
    val tutte: List<Colonnina> = emptyList(),
    val caricamento: Boolean = false,
    /** ne indice ne copia salvata: senza rete non possiamo mostrarle */
    val nonDisponibili: Boolean = false,
)

/** Le colonnine attorno a un centro, filtrate secondo le prese e la potenza scelte. */
data class VistaColonnine(val centro: Centro, val zona: ZonaColonnine)

class GocciaViewModel(private val c: Contenitore) : ViewModel() {
    private val repo = c.repository
    private val archivio = c.archivio
    private val posizione = c.posizione

    val utente: StateFlow<DatiUtente> = archivio.dati

    private val _dati = MutableStateFlow(StatoDati())
    val dati: StateFlow<StatoDati> = _dati.asStateFlow()

    private val _centroMappa = MutableStateFlow<Coordinate?>(null)

    private val caricamentoProvince = Mutex()
    private var avviato = false
    private var ultimoAggiornamento = 0L
    private var lavoroCentro: Job? = null

    val vista: StateFlow<VistaZona?> = combine(_dati, utente) { d, u -> calcolaVista(d, u, d.centro) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Per la mappa: gli stessi calcoli ma attorno al centro della mappa. */
    val vistaMappa: StateFlow<VistaZona?> = combine(_dati, utente, _centroMappa) { d, u, m ->
        val centro = m?.let { Centro(it, TipoCentro.COMUNE, "Mappa") } ?: d.centro
        calcolaVista(d, u, centro)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    // ------------------------------------------------------------------ colonnine

    private val _colonnine = MutableStateFlow(StatoColonnine())
    val colonnine: StateFlow<StatoColonnine> = _colonnine.asStateFlow()
    private val caricamentoTessere = Mutex()

    /** Colonnine attorno al centro (Home delle elettriche). */
    val vistaColonnine: StateFlow<VistaColonnine?> = combine(_dati, utente, _colonnine) { d, u, c ->
        d.centro?.let { centro -> VistaColonnine(centro, zonaColonnine(c, u, centro.coordinate)) }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Colonnine attorno al centro della mappa. */
    val vistaColonnineMappa: StateFlow<VistaColonnine?> = combine(_dati, utente, _colonnine, _centroMappa) { d, u, c, m ->
        val centro = m?.let { Centro(it, TipoCentro.COMUNE, "Mappa") } ?: d.centro ?: return@combine null
        VistaColonnine(centro, zonaColonnine(c, u, centro.coordinate))
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun zonaColonnine(c: StatoColonnine, u: DatiUtente, punto: Coordinate): ZonaColonnine =
        Elettrico.zona(c.tutte, punto, u.impostazioni.raggioKm, u.prese, u.impostazioni.potenzaMinima)

    /** La mappa mostra le colonnine invece dei distributori: di serie per le auto elettriche. */
    private val _sceltaMappaColonnine = MutableStateFlow<Boolean?>(null)
    val mappaColonnine: StateFlow<Boolean> = combine(_sceltaMappaColonnine, utente) { scelta, u -> scelta ?: u.elettrica }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    fun mostraColonnine(si: Boolean) {
        _sceltaMappaColonnine.value = si
        if (si) (_centroMappa.value ?: _dati.value.centro?.coordinate)?.let { caricaColonnineAttornoA(it, 15.0) }
    }

    /** Le colonnine vengono dalla Piattaforma Unica Nazionale (altrimenti da OpenStreetMap). */
    val colonnineDaPun: Boolean get() = _colonnine.value.indice?.daPun ?: true

    /** Stime delle tariffe (dalla pipeline, o quelle dell'app se non ancora scaricate). */
    val stime: StimeTariffe get() = _colonnine.value.indice?.stime ?: StimeTariffe()

    /** La tariffa usata per i costi di una colonnina: la tua, quella del gestore o la stima. */
    fun tariffaUsata(c: Colonnina): TariffaUsata = Elettrico.tariffa(c, utente.value.tariffeColonnine, stime)

    fun tariffaDi(c: Colonnina): Double = tariffaUsata(c).euroKwh

    suspend fun indiceColonnine(): IndiceColonnine? {
        _colonnine.value.indice?.let { return it }
        val indice = try {
            repo.indiceColonnine()
        } catch (e: Exception) {
            null
        }
        _colonnine.update { it.copy(indice = indice, nonDisponibili = indice == null) }
        return indice
    }

    /** Scarica le tessere che mancano (solo quelle che esistono davvero). */
    suspend fun caricaTessere(chiavi: Set<String>) {
        val indice = indiceColonnine() ?: return
        caricamentoTessere.withLock {
            val mancanti = chiavi.filter { it in indice.tessere && it !in _colonnine.value.perTessera }
            if (mancanti.isEmpty()) return
            _colonnine.update { it.copy(caricamento = true) }
            val esiti = coroutineScope {
                mancanti.map { k ->
                    async {
                        k to try {
                            repo.tessera(k, indice.generato)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }.awaitAll()
            }
            _colonnine.update { s ->
                val nuove = s.perTessera.toMutableMap()
                for ((k, lista) in esiti) if (lista != null) nuove[k] = lista
                s.copy(perTessera = nuove, tutte = nuove.values.flatten(), caricamento = false)
            }
        }
    }

    suspend fun caricaColonnineAttorno(punto: Coordinate, km: Double = 20.0) {
        val indice = indiceColonnine() ?: return
        caricaTessere(Elettrico.tessereAttorno(punto, km, indice.passo))
    }

    fun caricaColonnineAttornoA(punto: Coordinate, km: Double = 20.0) {
        viewModelScope.launch { caricaColonnineAttorno(punto, km) }
    }

    fun colonnina(id: String): Colonnina? = _colonnine.value.tutte.firstOrNull { it.id == id }

    // ------------------------------------------------------------------ calcoli

    fun litriPieno(u: DatiUtente, carburante: Carburante, adessoMillis: Long): Double {
        val auto = u.autoCorrente?.takeIf { !it.alimentazione.elettrica }
        return if (auto != null) {
            val livello = Consiglio.serbatoio(auto, u.rifornimenti, adessoMillis).livello
            (auto.capienza * (1 - livello)).coerceIn(10.0, auto.capienza.coerceAtLeast(10.0))
        } else if (carburante == Carburante.METANO) 12.0 else 40.0
    }

    private fun calcolaVista(d: StatoDati, u: DatiUtente, centro: Centro?): VistaZona? {
        if (centro == null || d.indice == null) return null
        val adesso = System.currentTimeMillis()
        val carburante = u.carburante
        val self = u.impostazioni.preferisciSelf || !carburante.haSelf
        val provincia = Territorio.provinciaDi(d.indice.province, d.comuni, centro.coordinate)
        val mediaProvincia = provincia?.medie?.get(carburante)?.let { if (self) it.self ?: it.servito else it.servito ?: it.self }
        val mediaNazionale = d.indice.medieNazionali[carburante]?.let { if (self) it.self ?: it.servito else it.servito ?: it.self }
        val auto = u.autoCorrente?.takeIf { !it.alimentazione.elettrica }
        val zona = Convenienza.zona(
            distributori = d.distributori,
            carburante = carburante,
            preferisciSelf = u.impostazioni.preferisciSelf,
            centro = centro.coordinate,
            raggioKm = u.impostazioni.raggioKm,
            escludiAutostrade = u.impostazioni.escludiAutostrade,
            litri = litriPieno(u, carburante, adesso),
            consumoPer100 = auto?.consumo ?: 6.0,
            adessoSecondi = adesso / 1000,
            mediaDiRiserva = mediaProvincia,
        )
        return VistaZona(carburante, self, zona, provincia, mediaProvincia, mediaNazionale, centro, adesso)
    }

    fun serbatoio(u: DatiUtente): StatoSerbatoio? {
        val auto = u.autoCorrente ?: return null
        return Consiglio.serbatoio(auto, u.rifornimenti.filter { it.autoId == null || it.autoId == auto.id }, System.currentTimeMillis())
    }

    // ------------------------------------------------------------------ avvio e aggiornamento

    /** Da chiamare all'apertura: scarica i dati, trova la posizione e carica i distributori attorno. */
    fun avvia() {
        if (avviato) return
        avviato = true
        viewModelScope.launch { carica(forza = false, nuovaPosizione = true) }
    }

    /** Tornando nell'app dopo un po' ricontrolliamo prezzi e posizione. */
    fun alRitorno() {
        if (!avviato) return
        if (System.currentTimeMillis() - ultimoAggiornamento > 30 * 60 * 1000L) {
            viewModelScope.launch { carica(forza = false, nuovaPosizione = _dati.value.centro?.tipo != TipoCentro.COMUNE) }
        }
    }

    fun aggiorna() {
        viewModelScope.launch {
            _dati.update { it.copy(aggiornando = true) }
            carica(forza = true, nuovaPosizione = _dati.value.centro?.tipo != TipoCentro.COMUNE && _dati.value.centro?.tipo != TipoCentro.LUOGO)
            _dati.update { it.copy(aggiornando = false) }
        }
    }

    private suspend fun carica(forza: Boolean, nuovaPosizione: Boolean) {
        ultimoAggiornamento = System.currentTimeMillis()
        val esitoIndice = try {
            repo.indice(forza)
        } catch (e: Exception) {
            null
        }
        if (esitoIndice == null) {
            _dati.update { it.copy(caricamento = false, senzaDati = it.indice == null, offline = true) }
            if (_dati.value.indice == null) return
        }
        val comuni = if (_dati.value.comuni.isEmpty()) {
            try {
                repo.comuni()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            _dati.value.comuni
        }
        val estrazionePrima = _dati.value.indice?.estrazione
        if (esitoIndice != null) {
            val nuovoGiorno = estrazionePrima != null && estrazionePrima != esitoIndice.dati.estrazione
            _dati.update {
                it.copy(
                    indice = esitoIndice.dati,
                    comuni = comuni,
                    offline = esitoIndice.offline,
                    scaricatoIl = esitoIndice.scaricatoIl,
                    senzaDati = false,
                    perProvincia = if (nuovoGiorno) emptyMap() else it.perProvincia,
                    distributori = if (nuovoGiorno) emptyList() else it.distributori,
                )
            }
        }
        val centro = if (nuovaPosizione || _dati.value.centro == null) trovaCentro() ?: _dati.value.centro else _dati.value.centro
        impostaCentroInterno(centro, forzaTempoReale = forza)
        c.aggiornaWidget()
    }

    private suspend fun trovaCentro(): Centro? {
        val u = utente.value
        var problema = ProblemaPosizione.NESSUNO
        if (posizione.haPermesso()) {
            _dati.update { it.copy(cercoPosizione = true) }
            val trovata = posizione.attuale()
            _dati.update { it.copy(cercoPosizione = false) }
            if (trovata != null) {
                _dati.update { it.copy(problemaPosizione = ProblemaPosizione.NESSUNO) }
                archivio.aggiorna { it.copy(ultimaPosizione = PosizioneSalvata(trovata.lat, trovata.lon, System.currentTimeMillis())) }
                return Centro(trovata, TipoCentro.POSIZIONE, "Vicino a te")
            }
            problema = if (posizione.localizzazioneAttiva()) ProblemaPosizione.NON_TROVATA else ProblemaPosizione.GPS_SPENTO
        } else {
            problema = ProblemaPosizione.PERMESSO_MANCANTE
        }
        _dati.update { it.copy(problemaPosizione = problema) }
        u.casa?.let { return Centro(it.coordinate, TipoCentro.LUOGO, "Vicino a Casa") }
        u.ultimaPosizione?.let { return Centro(it.coordinate, TipoCentro.ULTIMA, "Ultima posizione nota") }
        return null
    }

    private suspend fun impostaCentroInterno(centro: Centro?, forzaTempoReale: Boolean = false) {
        _dati.update { it.copy(centro = centro) }
        if (centro == null) {
            _dati.update { it.copy(caricamento = false) }
            return
        }
        // i prezzi in vigore adesso arrivano mentre carichiamo quelli del file
        viewModelScope.launch { aggiornaInTempoReale(centro.coordinate, utente.value.impostazioni.raggioKm, forzaTempoReale) }
        // per chi ricarica, le colonnine arrivano insieme ai prezzi (senza farli aspettare)
        if (utente.value.autoCorrente?.alimentazione?.ricaricabile == true || mappaColonnine.value) {
            caricaColonnineAttornoA(centro.coordinate, 20.0)
        }
        caricaAttorno(centro.coordinate, 20.0)
        val d = _dati.value
        val provincia = d.indice?.let { Territorio.provinciaDi(it.province, d.comuni, centro.coordinate) }
        _dati.update { it.copy(caricamento = false, provincia = provincia) }
        if (provincia != null && d.indice != null) {
            val storico = try {
                repo.storico(provincia.sigla, d.indice.estrazione)
            } catch (e: Exception) {
                null
            }
            _dati.update { it.copy(storicoZona = storico) }
        }
    }

    /** L'utente ha concesso la posizione (o vuole tornare a "vicino a me"). */
    fun usaPosizione() {
        lavoroCentro?.cancel()
        lavoroCentro = viewModelScope.launch {
            if (_dati.value.indice == null) {
                carica(forza = false, nuovaPosizione = true)
            } else {
                trovaCentro()?.let { impostaCentroInterno(it) }
            }
        }
    }

    fun centraSu(comune: Comune) {
        lavoroCentro?.cancel()
        lavoroCentro = viewModelScope.launch {
            impostaCentroInterno(Centro(comune.coordinate, TipoCentro.COMUNE, comune.etichetta))
        }
    }

    fun centraSu(luogo: Luogo) {
        lavoroCentro?.cancel()
        lavoroCentro = viewModelScope.launch {
            val etichetta = when (luogo.tipo) {
                TipoLuogo.CASA -> "Vicino a Casa"
                TipoLuogo.LAVORO -> "Vicino al Lavoro"
                TipoLuogo.ALTRO -> "Vicino a ${luogo.nome}"
            }
            impostaCentroInterno(Centro(luogo.coordinate, TipoCentro.LUOGO, etichetta))
        }
    }

    fun cercaComuni(testo: String): List<Comune> = Territorio.cerca(_dati.value.comuni, testo)

    fun haPermessoPosizione(): Boolean = posizione.haPermesso()

    fun localizzazioneAttiva(): Boolean = posizione.localizzazioneAttiva()

    /** Posizione di adesso (per salvare un luogo): null se non disponibile. */
    suspend fun posizioneAttuale(): Coordinate? = posizione.attuale()

    /** Assicura che l'elenco dei comuni sia caricato (serve anche senza posizione). */
    fun caricaComuni() {
        if (_dati.value.comuni.isNotEmpty()) return
        viewModelScope.launch {
            val comuni = try {
                repo.comuni()
            } catch (e: Exception) {
                emptyList()
            }
            if (comuni.isNotEmpty()) _dati.update { it.copy(comuni = comuni) }
        }
    }

    fun comunePiuVicino(punto: Coordinate): Comune? = Territorio.comunePiuVicino(_dati.value.comuni, punto)

    // ------------------------------------------------------------------ province

    /** Carica le province attorno a un punto (se non sono gia in memoria). */
    suspend fun caricaAttorno(punto: Coordinate, km: Double) {
        val d = _dati.value
        val indice = d.indice ?: return
        caricaProvince(Territorio.provinceAttorno(indice.province, d.comuni, punto, km))
    }

    fun caricaAttornoA(punto: Coordinate, km: Double = 20.0) {
        viewModelScope.launch { caricaAttorno(punto, km) }
    }

    suspend fun caricaProvince(sigle: List<String>) {
        caricamentoProvince.withLock { caricaProvinceMancanti(sigle) }
    }

    private suspend fun caricaProvinceMancanti(sigle: List<String>) {
        val indice = _dati.value.indice ?: return
        val mancanti = sigle.distinct().filter { it !in _dati.value.perProvincia }
        if (mancanti.isEmpty()) return
        val esiti = coroutineScope {
            mancanti.map { sigla ->
                async {
                    sigla to try {
                        repo.distributori(sigla, indice.estrazione)
                    } catch (e: Exception) {
                        null
                    }
                }
            }.awaitAll()
        }
        _dati.update { stato ->
            val nuove = stato.perProvincia.toMutableMap()
            var offline = stato.offline
            for ((sigla, esito) in esiti) {
                if (esito != null) {
                    nuove[sigla] = esito.dati
                    if (esito.offline) offline = true
                }
            }
            stato.copy(perProvincia = nuove, distributori = TempoReale.applica(nuove.values.flatten(), stato.live), offline = offline)
        }
    }

    fun caricaProvinceDi(sigle: List<String>) {
        viewModelScope.launch { caricaProvince(sigle) }
    }

    /** La mappa si e fermata qui: carichiamo i distributori (o le colonnine) della zona se serve. */
    fun mappaSpostata(punto: Coordinate, zoom: Double) {
        _centroMappa.value = punto
        if (zoom >= 9.0) {
            viewModelScope.launch { caricaAttorno(punto, 15.0) }
            if (mappaColonnine.value) caricaColonnineAttornoA(punto, 15.0)
        }
        if (zoom >= 10.5 && !mappaColonnine.value) {
            viewModelScope.launch { aggiornaInTempoReale(punto, Osservaprezzi.RAGGIO_MASSIMO_KM) }
        }
    }

    // ------------------------------------------------------------------ prezzi in tempo reale

    private data class Lettura(val centro: Coordinate, val raggioKm: Int, val quando: Long)

    private val letture = ArrayList<Lettura>()
    private val accessoLetture = Mutex()

    /**
     * Chiede a Osservaprezzi i prezzi in vigore adesso attorno a [centro] e li sovrappone a quelli
     * del file del mattino. Una zona gia letta da meno di 10 minuti non si richiede (se non [forza]).
     */
    suspend fun aggiornaInTempoReale(centro: Coordinate, raggioKm: Int, forza: Boolean = false) {
        val raggio = raggioKm.coerceIn(3, Osservaprezzi.RAGGIO_MASSIMO_KM)
        val adesso = System.currentTimeMillis()
        val lettura = Lettura(centro, raggio, adesso)
        accessoLetture.withLock {
            letture.removeAll { adesso - it.quando !in 0 until DIECI_MINUTI }
            // gia letta: il nuovo cerchio sta (quasi) tutto dentro uno di quelli letti
            if (!forza && letture.any { Geo.distanzaKm(it.centro, centro) <= it.raggioKm - raggio + 3.0 }) return
            letture += lettura
        }
        val impianti = try {
            c.osservaprezzi.attorno(centro, raggio)
        } catch (e: CancellationException) {
            accessoLetture.withLock { letture.remove(lettura) }
            throw e
        } catch (e: Exception) {
            accessoLetture.withLock { letture.remove(lettura) }
            Log.w("Goccia", "tempo reale: Osservaprezzi non risponde (${e.message})")
            _dati.update { it.copy(tempoRealeErrore = true) }
            return
        }
        Log.i("Goccia", "tempo reale: ${impianti.size} distributori entro $raggio km, in ${System.currentTimeMillis() - adesso} ms")
        _dati.update { s ->
            val live = s.live.toMutableMap()
            for (i in impianti) live[i.id] = TempoReale.unisci(live[i.id], i)
            s.copy(
                live = live,
                distributori = TempoReale.applica(s.perProvincia.values.flatten(), live),
                tempoRealeIl = adesso,
                tempoRealeCentro = centro,
                tempoRealeErrore = false,
            )
        }
    }

    /** I prezzi sono in tempo reale attorno a [punto] (letti da poco, li vicino). */
    fun inTempoReale(d: StatoDati, punto: Coordinate): Boolean {
        val quando = d.tempoRealeIl ?: return false
        val dove = d.tempoRealeCentro ?: return false
        return System.currentTimeMillis() - quando < 30 * 60_000L && Geo.distanzaKm(dove, punto) <= Osservaprezzi.RAGGIO_MASSIMO_KM
    }

    private val _schede = MutableStateFlow<Map<Long, SchedaImpianto>>(emptyMap())

    /** Schede dei distributori aperti: prezzi con la loro data e servizi dichiarati al Ministero. */
    val schede: StateFlow<Map<Long, SchedaImpianto>> = _schede.asStateFlow()
    private val schedeLette = ConcurrentHashMap<Long, Long>()

    fun caricaScheda(id: Long) {
        val adesso = System.currentTimeMillis()
        schedeLette[id]?.let { if (adesso - it in 0 until DIECI_MINUTI) return }
        schedeLette[id] = adesso
        viewModelScope.launch {
            val scheda = try {
                c.osservaprezzi.scheda(id)
            } catch (e: CancellationException) {
                schedeLette.remove(id)
                throw e
            } catch (e: Exception) {
                schedeLette.remove(id)
                Log.w("Goccia", "tempo reale: scheda di $id non disponibile (${e.message})")
                return@launch
            }
            Log.i("Goccia", "tempo reale: scheda di $id, ${scheda.prezzi.prezzi.size} prezzi, servizi ${scheda.servizi}")
            _schede.update { it + (id to scheda) }
            _dati.update { s ->
                val live = s.live + (id to TempoReale.unisci(s.live[id], scheda.prezzi))
                s.copy(live = live, distributori = TempoReale.applica(s.perProvincia.values.flatten(), live))
            }
        }
    }

    /** Stato in tempo reale dei punti di una colonnina della PUN (null se non disponibile). */
    suspend fun statoColonnina(colonnina: Colonnina): StatoColonnina? = try {
        c.pun.stato(colonnina)?.also {
            Log.i("Goccia", "pun: ${colonnina.id} tempo reale ${it.tempoReale}, liberi ${it.liberi} su ${it.totale} (${it.punti.size} punti)")
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w("Goccia", "pun: stato di ${colonnina.id} non disponibile (${e.message})")
        null
    }

    fun distributoreCaricato(id: Long): Distributore? = _dati.value.distributori.firstOrNull { it.id == id }

    suspend fun trovaDistributore(provincia: String, id: Long): Distributore? {
        distributoreCaricato(id)?.let { return it }
        caricaProvince(listOf(provincia))
        return distributoreCaricato(id)
    }

    suspend fun storicoDistributore(provincia: String, id: Long): StoricoDistributore? {
        val estrazione = _dati.value.indice?.estrazione ?: return null
        return try {
            repo.storicoDistributore(provincia, estrazione, id)
        } catch (e: Exception) {
            null
        }
    }

    /** Media locale attorno a un distributore (per i badge del dettaglio e per il risparmio). */
    fun mediaAttorno(d: Distributore, carburante: Carburante, self: Boolean): Int? {
        val stato = _dati.value
        val locale = Convenienza.mediaLocale(
            stato.distributori, carburante, self || !carburante.haSelf, d.coordinate, 10.0, System.currentTimeMillis() / 1000,
        )
        if (locale != null) return locale
        val media = stato.indice?.province?.firstOrNull { it.sigla == d.provincia }?.medie?.get(carburante) ?: return null
        return if (self) media.self ?: media.servito else media.servito ?: media.self
    }

    // ------------------------------------------------------------------ dati dell'utente

    private fun modifica(trasforma: (DatiUtente) -> DatiUtente) {
        viewModelScope.launch { archivio.aggiorna(trasforma) }
    }

    fun impostazioni(trasforma: (Impostazioni) -> Impostazioni) = modifica { it.copy(impostazioni = trasforma(it.impostazioni)) }

    fun scegliCarburante(carburante: Carburante) = impostazioni { it.copy(carburante = carburante.codice) }

    fun completaIntroduzione() = modifica { it.copy(introduzioneVista = true) }

    fun chiudiDonazione() = modifica { it.copy(donazioneChiusaIl = System.currentTimeMillis()) }

    fun preferito(d: Distributore, aggiungi: Boolean) = modifica { u ->
        if (aggiungi) {
            if (u.ePreferito(d.id)) u
            else u.copy(preferiti = u.preferiti + Preferito(d.id, d.provincia, d.titolo, d.lat, d.lon))
        } else {
            u.copy(preferiti = u.preferiti.filterNot { it.id == d.id })
        }
    }

    fun avvisaCalo(id: Long, attivo: Boolean) = modifica { u ->
        u.copy(preferiti = u.preferiti.map { if (it.id == id) it.copy(avvisaCalo = attivo) else it })
    }

    fun salvaAvviso(avviso: Avviso) = modifica { u ->
        val esiste = u.avvisi.any { it.id == avviso.id }
        u.copy(avvisi = if (esiste) u.avvisi.map { if (it.id == avviso.id) avviso else it } else u.avvisi + avviso)
    }

    fun attivaAvviso(id: String, attivo: Boolean) = modifica { u ->
        u.copy(avvisi = u.avvisi.map { if (it.id == id) it.copy(attivo = attivo, ultimoMinimo = null) else it })
    }

    fun eliminaAvviso(id: String) = modifica { u -> u.copy(avvisi = u.avvisi.filterNot { it.id == id }) }

    fun salvaLuogo(luogo: Luogo) = modifica { u ->
        val esiste = u.luoghi.any { it.id == luogo.id }
        u.copy(luoghi = if (esiste) u.luoghi.map { if (it.id == luogo.id) luogo else it } else u.luoghi + luogo)
    }

    fun eliminaLuogo(id: String) = modifica { u ->
        u.copy(
            luoghi = u.luoghi.filterNot { it.id == id },
            avvisi = u.avvisi.map { if (it.luogoId == id) it.copy(luogoId = null) else it },
        )
    }

    fun salvaAuto(auto: Auto, attiva: Boolean = false) = modifica { u ->
        val esiste = u.auto.any { it.id == auto.id }
        val lista = if (esiste) u.auto.map { if (it.id == auto.id) auto else it } else u.auto + auto
        u.copy(auto = lista, autoAttiva = if (attiva || u.autoAttiva == null) auto.id else u.autoAttiva)
    }

    fun eliminaAuto(id: String) = modifica { u ->
        val rimaste = u.auto.filterNot { it.id == id }
        u.copy(auto = rimaste, autoAttiva = if (u.autoAttiva == id) rimaste.firstOrNull()?.id else u.autoAttiva)
    }

    /** Cambiando auto torna al suo carburante. */
    fun scegliAuto(id: String) = modifica { u ->
        u.copy(autoAttiva = id, impostazioni = u.impostazioni.copy(carburante = null))
    }

    fun impostaLivello(autoId: String, livello: Double) = modifica { u ->
        u.copy(auto = u.auto.map { if (it.id == autoId) it.copy(livello = livello.coerceIn(0.0, 1.0), livelloIl = System.currentTimeMillis()) else it })
    }

    /** Salva un rifornimento e aggiorna il livello stimato dell'auto. */
    fun salvaRifornimento(r: Rifornimento) = modifica { u ->
        val auto = u.auto.firstOrNull { it.id == r.autoId }
        val autoAggiornate = if (auto == null) u.auto else u.auto.map {
            if (it.id != auto.id) it
            else {
                val adesso = System.currentTimeMillis()
                val livelloPrima = Consiglio.serbatoio(it, u.rifornimenti, adesso).livello
                val nuovo = if (r.pieno || it.capienza <= 0) 1.0 else (livelloPrima + r.litri / it.capienza).coerceAtMost(1.0)
                it.copy(livello = nuovo, livelloIl = adesso)
            }
        }
        u.copy(rifornimenti = (u.rifornimenti.filterNot { it.id == r.id } + r).sortedBy { it.quando }, auto = autoAggiornate)
    }

    fun eliminaRifornimento(id: String) = modifica { u -> u.copy(rifornimenti = u.rifornimenti.filterNot { it.id == id }) }

    fun nuovoId(): String = UUID.randomUUID().toString()

    fun esportaBackup(): String = archivio.esporta()

    suspend fun importaBackup(testo: String): Boolean = try {
        archivio.importa(testo)
        true
    } catch (e: Exception) {
        false
    }

    // ------------------------------------------------------------------ viaggio

    private val _viaggio = MutableStateFlow<StatoViaggio>(StatoViaggio.Vuoto)
    val viaggio: StateFlow<StatoViaggio> = _viaggio.asStateFlow()
    private var lavoroViaggio: Job? = null

    /** L'ultimo viaggio chiesto: il modulo lo ripropone tornando indietro dal risultato. */
    var ultimaRichiesta: RichiestaViaggio? = null
        private set

    /** Percorso, distributori (o colonnine) lungo la strada e soste consigliate. */
    fun calcolaViaggio(partenza: Tappa, arrivo: Tappa, livello: Double, deviazioneMin: Int, pienoCompleto: Boolean, arrivoMinimo: Double = 0.2) {
        lavoroViaggio?.cancel()
        ultimaRichiesta = RichiestaViaggio(
            partenza, arrivo, livello, deviazioneMin, pienoCompleto, arrivoMinimo,
            elettrica = utente.value.autoCorrente?.alimentazione?.elettrica == true,
        )
        lavoroViaggio = viewModelScope.launch {
            try {
                _viaggio.value = StatoViaggio.Calcolo("Calcolo il percorso…")
                if (_dati.value.indice == null) carica(forza = false, nuovaPosizione = false)
                val da = if (partenza.posizioneAttuale) posizione.attuale() ?: partenza.coordinate else partenza.coordinate
                val percorso = c.instradamento.percorso(da, arrivo.coordinate)
                val campioni = withContext(Dispatchers.Default) { Tragitto.campiona(percorso.punti, 0.5) }
                val u = utente.value
                val elettrica = u.autoCorrente?.takeIf { it.alimentazione.elettrica }
                if (elettrica != null) {
                    _viaggio.value = StatoViaggio.Calcolo("Cerco le colonnine lungo la strada…")
                    val indiceEv = indiceColonnine() ?: throw IOException("colonnine non disponibili")
                    caricaTessere(Elettrico.tessereLungo(campioni, 4.0, indiceEv.passo))
                    val tutte = _colonnine.value.tutte
                    val lungo = withContext(Dispatchers.Default) {
                        Elettrico.lungoIlPercorso(tutte, campioni, Tragitto.distanzaPerMinuti(deviazioneMin))
                    }
                    val piano = withContext(Dispatchers.Default) {
                        Elettrico.pianifica(
                            lungo, percorso.distanzaKm, elettrica.capienza, elettrica.consumo, elettrica.acKw, elettrica.dcKw,
                            u.prese, livello, arrivoMinimo, ::tariffaDi,
                        )
                    }
                    _viaggio.value = StatoViaggio.ProntoElettrico(
                        partenza = partenza.copy(coordinate = da),
                        arrivo = arrivo,
                        percorso = percorso,
                        campioni = campioni,
                        lungo = lungo,
                        piano = piano,
                        auto = elettrica,
                        confronto = confrontoCarburante(u, percorso.distanzaKm),
                    )
                } else {
                    val indice = _dati.value.indice ?: throw IOException("dati non disponibili")
                    _viaggio.value = StatoViaggio.Calcolo("Cerco i distributori lungo la strada…")
                    val comuni = _dati.value.comuni
                    val sigle = withContext(Dispatchers.Default) {
                        (campioni.filterIndexed { i, _ -> i % 20 == 0 } + campioni.last())
                            .flatMap { Territorio.provinceAttorno(indice.province, comuni, Coordinate(it.lat, it.lon), 6.0) }
                            .distinct()
                    }
                    caricaProvince(sigle)

                    val carburante = u.carburante
                    val auto = u.autoCorrente?.takeIf { !it.alimentazione.elettrica }
                    val capienza = auto?.capienza ?: 50.0
                    val consumo = auto?.consumo ?: 6.0
                    val distributori = _dati.value.perProvincia.filterKeys { it in sigle }.values.flatten()
                    val adesso = System.currentTimeMillis() / 1000
                    val lungo = withContext(Dispatchers.Default) {
                        Tragitto.lungoIlPercorso(
                            distributori, carburante, u.impostazioni.preferisciSelf, campioni, Tragitto.distanzaPerMinuti(deviazioneMin), adesso,
                        )
                    }
                    val piano = Tragitto.pianifica(lungo, percorso.distanzaKm, capienza, consumo, livello, pienoCompleto)
                    _viaggio.value = StatoViaggio.Pronto(
                        partenza = partenza.copy(coordinate = da),
                        arrivo = arrivo,
                        percorso = percorso,
                        campioni = campioni,
                        lungo = lungo,
                        piano = piano,
                        carburante = carburante,
                        livello = livello,
                        nomeAuto = auto?.nome,
                        capienza = capienza,
                        consumo = consumo,
                        pienoCompleto = pienoCompleto,
                        self = u.impostazioni.preferisciSelf,
                    )
                }
                val recente = ViaggioRecente(partenza.nome, da.lat, da.lon, arrivo.nome, arrivo.coordinate.lat, arrivo.coordinate.lon, System.currentTimeMillis())
                archivio.aggiorna { du ->
                    val altri = du.viaggiRecenti.filterNot { it.partenza == recente.partenza && it.arrivo == recente.arrivo }
                    du.copy(viaggiRecenti = (listOf(recente) + altri).take(5))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: PercorsoNonTrovato) {
                _viaggio.value = StatoViaggio.Errore("Non trovo un percorso stradale tra questi due punti.")
            } catch (e: IOException) {
                _viaggio.value = StatoViaggio.Errore("Non riesco a calcolare il viaggio: controlla la connessione e riprova.")
            } catch (e: Exception) {
                _viaggio.value = StatoViaggio.Errore("Qualcosa è andato storto nel calcolo del viaggio. Riprova tra poco.")
            }
        }
    }

    fun chiudiViaggio() {
        lavoroViaggio?.cancel()
        _viaggio.value = StatoViaggio.Vuoto
    }

    /**
     * Avvia la modalita autostrada sul viaggio appena calcolato: il servizio segue la posizione
     * lungo il percorso. Falso se manca il viaggio o il permesso di posizione.
     */
    fun avviaGuida(context: Context): Boolean {
        val u = utente.value
        val (percorso, livello) = when (val s = _viaggio.value) {
            is StatoViaggio.Pronto -> PercorsoGuida.AlCarburante(
                destinazione = s.arrivo.nome,
                lunghezzaKm = s.percorso.distanzaKm,
                campioni = s.campioni,
                strade = s.percorso.strade,
                lungo = s.lungo,
                carburante = s.carburante,
                self = s.self,
                capienza = s.capienza,
                consumo = s.consumo,
                pienoCompleto = s.pienoCompleto,
            ) to s.livello
            is StatoViaggio.ProntoElettrico -> PercorsoGuida.Elettrico(
                destinazione = s.arrivo.nome,
                lunghezzaKm = s.percorso.distanzaKm,
                campioni = s.campioni,
                strade = s.percorso.strade,
                lungo = s.lungo,
                capacita = s.auto.capienza,
                consumo = s.auto.consumo,
                acKw = s.auto.acKw,
                dcKw = s.auto.dcKw,
                prese = u.prese,
                arrivoMinimo = s.piano.arrivoMinimo,
                tariffa = ::tariffaDi,
            ) to s.piano.partenza
            else -> return false
        }
        if (!ServizioGuida.permessoPosizione(context)) return false
        GuidaInCorso.prepara(percorso, livello)
        val avviato = ServizioGuida.avvia(context)
        if (!avviato) GuidaInCorso.termina()
        return avviato
    }

    /** Distanza dal centro attuale, per le liste (preferiti, avvisi). */
    fun distanzaDalCentro(punto: Coordinate): Double? =
        _dati.value.centro?.let { Geo.distanzaKm(it.coordinate, punto) }

    /**
     * Quanto costerebbero [km] con l'auto a carburante del garage (o con una benzina media da
     * 6,5 l/100 km), ai prezzi medi nazionali di oggi: serve a confrontare con l'elettrica.
     */
    fun confrontoCarburante(u: DatiUtente, km: Double): ConfrontoCarburante? {
        val indice = _dati.value.indice ?: return null
        val altra = u.autoACarburante
        val carburante = altra?.alimentazione?.carburante ?: Carburante.BENZINA
        val consumo = altra?.consumo ?: 6.5
        val media = indice.medieNazionali[carburante]?.let { it.self ?: it.servito } ?: return null
        val quantita = km * consumo / 100
        val etichetta = if (altra != null) carburante.etichetta else "${carburante.etichetta}, auto media"
        return ConfrontoCarburante(etichetta, quantita, altra?.alimentazione?.unitaCapienza ?: "l", quantita * media / 1000.0, consumo, altra == null)
    }

    // ------------------------------------------------------------------ elettriche: tariffe e filtri

    fun salvaTariffaCasa(t: TariffaCasa) = modifica { it.copy(tariffaCasa = t) }

    fun salvaTariffeColonnine(t: TariffeColonnine) = modifica { it.copy(tariffeColonnine = t) }

    fun scegliPrese(prese: Set<Presa>) = impostazioni { it.copy(prese = prese.map { p -> p.codice }) }

    fun scegliPotenzaMinima(kw: Int) = impostazioni { it.copy(potenzaMinima = kw) }
}
