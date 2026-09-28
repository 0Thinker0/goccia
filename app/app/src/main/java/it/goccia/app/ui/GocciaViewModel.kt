package it.goccia.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.goccia.app.Contenitore
import it.goccia.app.dati.Auto
import it.goccia.app.dati.Avviso
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Comune
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.DatiUtente
import it.goccia.app.dati.Distributore
import it.goccia.app.dati.Impostazioni
import it.goccia.app.dati.Indice
import it.goccia.app.dati.Luogo
import it.goccia.app.dati.PosizioneSalvata
import it.goccia.app.dati.Preferito
import it.goccia.app.dati.Provincia
import it.goccia.app.dati.Rifornimento
import it.goccia.app.dati.StoricoDistributore
import it.goccia.app.dati.StoricoZona
import it.goccia.app.dati.TipoLuogo
import it.goccia.app.logica.Consiglio
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.Geo
import it.goccia.app.logica.StatoSerbatoio
import it.goccia.app.logica.Territorio
import it.goccia.app.logica.Zona
import java.util.UUID
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
        impostaCentroInterno(centro)
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

    private suspend fun impostaCentroInterno(centro: Centro?) {
        _dati.update { it.copy(centro = centro) }
        if (centro == null) {
            _dati.update { it.copy(caricamento = false) }
            return
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
            stato.copy(perProvincia = nuove, distributori = nuove.values.flatten(), offline = offline)
        }
    }

    fun caricaProvinceDi(sigle: List<String>) {
        viewModelScope.launch { caricaProvince(sigle) }
    }

    /** La mappa si e fermata qui: carichiamo i distributori della zona se serve. */
    fun mappaSpostata(punto: Coordinate, zoom: Double) {
        _centroMappa.value = punto
        if (zoom >= 9.0) viewModelScope.launch { caricaAttorno(punto, 15.0) }
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

    /** Distanza dal centro attuale, per le liste (preferiti, avvisi). */
    fun distanzaDalCentro(punto: Coordinate): Double? =
        _dati.value.centro?.let { Geo.distanzaKm(it.coordinate, punto) }
}
