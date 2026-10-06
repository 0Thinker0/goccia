package it.goccia.app.ui.mappa

import android.Manifest
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import android.view.Gravity
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Colonnina
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.Distributore
import it.goccia.app.dati.Luogo
import it.goccia.app.dati.Presa
import it.goccia.app.dati.StatoColonnina
import it.goccia.app.dati.TipoLuogo
import it.goccia.app.logica.Bandiere
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.Elettrico
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Geo
import it.goccia.app.logica.Offerta
import it.goccia.app.logica.RAGGIO_MASSIMO_KM
import it.goccia.app.logica.Tono
import it.goccia.app.ui.Centro
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.ProblemaPosizione
import it.goccia.app.ui.TipoCentro
import it.goccia.app.ui.VistaColonnine
import it.goccia.app.ui.VistaZona
import it.goccia.app.ui.componenti.BadgeConvenienza
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.BottoneSecondario
import it.goccia.app.ui.componenti.CampoRicerca
import it.goccia.app.ui.componenti.ChipScelta
import it.goccia.app.ui.componenti.LogoBandiera
import it.goccia.app.ui.componenti.metaOfferta
import it.goccia.app.ui.elettrico.IconaColonnina
import it.goccia.app.ui.elettrico.RigaStatoPunti
import it.goccia.app.ui.elettrico.metaColonnina
import it.goccia.app.ui.elettrico.potenzaColonnina
import it.goccia.app.ui.elettrico.testoTariffa
import it.goccia.app.ui.elettrico.titoloColonnina
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.naviga
import it.goccia.app.ui.stati.SuggerimentiComuni
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private const val SORGENTE_PREZZI = "goccia-prezzi"
private const val STRATO_PREZZI = "goccia-prezzi"
private const val SORGENTE_IO = "goccia-io"
private const val STRATO_IO_ALONE = "goccia-io-alone"
private const val STRATO_IO = "goccia-io"
private const val SORGENTE_LUOGO = "goccia-luogo"
private const val STRATO_LUOGO = "goccia-luogo"
private const val MASSIMO_PIN = 1500

// icone del segnaposto quando il centro e un luogo salvato o un comune
private const val ICONA_CASA = "io-casa"
private const val ICONA_LAVORO = "io-lavoro"
private const val ICONA_LUOGO = "io-luogo"

/** Sotto questo zoom si vedono intere regioni: "Cerca in quest'area" prima avvicina la mappa. */
private const val ZOOM_PREZZI = 9.0

/** Lo zoom a cui portiamo la mappa quando si cerca da troppo lontano. */
private const val ZOOM_AREA = 11.5

/** Gli spostamenti della camera, letti dagli ascoltatori della mappa. */
private class StatoCamera {
    /** l'ultimo movimento l'ha cominciato l'utente con le dita */
    var gesto = false

    /** dov'era la camera quando sono comparsi i risultati che si vedono */
    var base: CameraPosition? = null
}

/** Il foglio in basso si sta aprendo: finche non e aperto, il suo stato "chiuso" non azzera la scelta. */
private class Apertura {
    var inCorso = false
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataMappa(
    vm: GocciaViewModel,
    onDistributore: (Distributore) -> Unit,
    onLista: () -> Unit,
    onColonnina: (Colonnina) -> Unit,
    onNuovoLuogo: (tipo: String) -> Unit,
) {
    LaunchedEffect(Unit) { vm.avvia() }
    val context = LocalContext.current
    val densita = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dati by vm.dati.collectAsStateWithLifecycle()
    val utente by vm.utente.collectAsStateWithLifecycle()
    val statoVista by vm.vistaMappa.collectAsStateWithLifecycle()
    val vista = statoVista
    // colonnine al posto dei distributori (di serie per le auto elettriche)
    val colonnine by vm.mappaColonnine.collectAsStateWithLifecycle()
    val statoVistaEv by vm.vistaColonnineMappa.collectAsStateWithLifecycle()
    val vistaEv = statoVistaEv
    val statoColonnine by vm.colonnine.collectAsStateWithLifecycle()
    // la zona cercata con "Cerca in quest'area" (null: si guarda attorno al centro)
    val area by vm.areaMappa.collectAsStateWithLifecycle()
    val cercoArea by vm.cercoArea.collectAsStateWithLifecycle()

    // il distributore (o la colonnina) aperto nel foglio in basso; nessuno all'apertura della mappa
    var selezionato by rememberSaveable { mutableStateOf<Long?>(null) }
    var selezionataEv by rememberSaveable { mutableStateOf<String?>(null) }
    var testo by rememberSaveable { mutableStateOf("") }
    var mappa by remember { mutableStateOf<MapLibreMap?>(null) }
    var stile by remember { mutableStateOf<Style?>(null) }
    // "Cerca in quest'area" compare quando l'utente sposta la mappa lontano dai risultati
    var mostraCerca by remember { mutableStateOf(false) }
    // ogni tocco sul mirino riporta la mappa sul segnaposto, anche se il centro non e cambiato
    var richiestaCentra by remember { mutableIntStateOf(0) }
    // dopo "Cerca in quest'area" la vista si allarga, se serve, e il piu conveniente si apre nel foglio
    var richiestaAdatta by remember { mutableIntStateOf(0) }
    val camera = remember { StatoCamera() }
    val pin = remember { PinPrezzo(context) }
    val immaginiCaricate = remember { HashSet<String>() }
    val iconeLuogo = mapOf(
        ICONA_CASA to rememberVectorPainter(Icone.Casa),
        ICONA_LAVORO to rememberVectorPainter(Icone.Lavoro),
        ICONA_LUOGO to rememberVectorPainter(Icone.Segnaposto),
    )

    // foglio in basso: chiuso, piccolo (si apre toccando un segnaposto) o espanso (tirando su la linguetta)
    val foglio = rememberStandardBottomSheetState(initialValue = SheetValue.Hidden, skipHiddenState = false)
    val impalcatura = rememberBottomSheetScaffoldState(bottomSheetState = foglio)
    val apertura = remember { Apertura() }
    var altezzaPiccola by remember { mutableIntStateOf(0) }
    var altezzaContenuto by remember { mutableIntStateOf(0) }
    val fogliochiuso = foglio.currentValue == SheetValue.Hidden && foglio.targetValue == SheetValue.Hidden
    val espanso = foglio.targetValue == SheetValue.Expanded

    fun apriFoglio() {
        apertura.inCorso = true
        scope.launch {
            try {
                if (foglio.currentValue == SheetValue.Hidden || foglio.targetValue == SheetValue.Hidden) foglio.partialExpand()
            } finally {
                apertura.inCorso = false
            }
        }
    }
    fun chiudiFoglio() {
        if (foglio.currentValue == SheetValue.Hidden && foglio.targetValue == SheetValue.Hidden) {
            selezionato = null
            selezionataEv = null
            return
        }
        scope.launch { foglio.hide() }
    }
    // chiuso il foglio (anche trascinandolo giu), nessun segnaposto resta scelto
    LaunchedEffect(foglio) {
        snapshotFlow { foglio.currentValue to foglio.targetValue }.collect { (attuale, destinazione) ->
            if (attuale == SheetValue.Hidden && destinazione == SheetValue.Hidden && !apertura.inCorso) {
                selezionato = null
                selezionataEv = null
            }
        }
    }
    BackHandler(enabled = !fogliochiuso) { chiudiFoglio() }

    // "Dove sei adesso": serve il permesso; se la posizione non arriva lo diciamo
    fun avvisa(problema: ProblemaPosizione) {
        val messaggio = when (problema) {
            ProblemaPosizione.NESSUNO -> return
            ProblemaPosizione.PERMESSO_MANCANTE -> "Serve il permesso di usare la posizione."
            ProblemaPosizione.GPS_SPENTO -> "La localizzazione del telefono è spenta: accendila per usare la tua posizione."
            ProblemaPosizione.NON_TROVATA -> "Non riusciamo a trovare la tua posizione. Riprova tra poco."
        }
        Toast.makeText(context, messaggio, Toast.LENGTH_LONG).show()
    }
    val richiestaPermesso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { esito ->
        if (esito.values.any { it }) {
            vm.vaiDoveSei { avvisa(it) }
        } else {
            Toast.makeText(context, "Senza il permesso puoi partire da un luogo salvato o cercare un comune.", Toast.LENGTH_LONG).show()
        }
    }
    fun vaiDoveSei() {
        chiudiFoglio()
        if (vm.haPermessoPosizione()) {
            vm.vaiDoveSei { avvisa(it) }
        } else {
            richiestaPermesso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    val vistaMappa = rememberVistaMappa { v ->
        v.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.attributionGravity = Gravity.TOP or Gravity.END
            val margine = with(densita) { 12.dp.roundToPx() }
            m.uiSettings.setAttributionMargins(0, with(densita) { 170.dp.roundToPx() }, margine, 0)
            // tornando sulla mappa con un'area cercata ripartiamo da li, altrimenti dal centro
            val areaIniziale = vm.areaMappa.value
            val iniziale = vm.dati.value.centro?.coordinate
            when {
                areaIniziale != null ->
                    m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(areaIniziale.centro.lat, areaIniziale.centro.lon), areaIniziale.zoom))
                iniziale != null -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(iniziale.lat, iniziale.lon), 13.0))
                else -> m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(42.5, 12.5), 5.0))
            }
            m.setStyle(Style.Builder().fromUri(STILE_MAPPA)) { s ->
                etichetteInItaliano(s)
                immaginiCaricate.clear()
                s.addSource(GeoJsonSource(SORGENTE_IO, FeatureCollection.fromFeatures(emptyList<Feature>())))
                s.addLayer(
                    CircleLayer(STRATO_IO_ALONE, SORGENTE_IO).withProperties(
                        PropertyFactory.circleRadius(30f),
                        PropertyFactory.circleColor(Colori.Petrolio.toArgb()),
                        PropertyFactory.circleOpacity(0.10f),
                        PropertyFactory.circleStrokeColor(Colori.Petrolio.toArgb()),
                        PropertyFactory.circleStrokeWidth(1f),
                        PropertyFactory.circleStrokeOpacity(0.3f),
                    ),
                )
                s.addLayer(
                    CircleLayer(STRATO_IO, SORGENTE_IO).withProperties(
                        PropertyFactory.circleRadius(8f),
                        PropertyFactory.circleColor(Colori.Petrolio.toArgb()),
                        PropertyFactory.circleStrokeColor(android.graphics.Color.WHITE),
                        PropertyFactory.circleStrokeWidth(4f),
                    ),
                )
                s.addSource(GeoJsonSource(SORGENTE_LUOGO, FeatureCollection.fromFeatures(emptyList<Feature>())))
                s.addLayer(
                    SymbolLayer(STRATO_LUOGO, SORGENTE_LUOGO).withProperties(
                        PropertyFactory.iconImage(Expression.get("icona")),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                    ),
                )
                s.addSource(GeoJsonSource(SORGENTE_PREZZI, FeatureCollection.fromFeatures(emptyList<Feature>())))
                s.addLayer(
                    SymbolLayer(STRATO_PREZZI, SORGENTE_PREZZI).withProperties(
                        PropertyFactory.iconImage(Expression.get("icona")),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        PropertyFactory.iconAllowOverlap(false),
                        PropertyFactory.symbolSortKey(Expression.get("ordine")),
                    ),
                )
                stile = s
            }
            m.addOnCameraMoveStartedListener { motivo ->
                camera.gesto = motivo == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE
            }
            m.addOnCameraIdleListener {
                val posizione = m.cameraPosition
                val bersaglio = posizione.target ?: return@addOnCameraIdleListener
                vm.mappaFerma(Coordinate(bersaglio.latitude, bersaglio.longitude), posizione.zoom)
                val base = camera.base
                if (!camera.gesto || base == null) {
                    // movimento nostro (mirino, ricerca, adattamento della vista): i risultati sono di qui
                    camera.base = posizione
                    mostraCerca = false
                } else {
                    val raggio = if (vm.mappaColonnine.value) vm.vistaColonnineMappa.value?.zona?.raggioKm else vm.vistaMappa.value?.zona?.raggioUsatoKm
                    mostraCerca = spostataLontano(m, base, posizione, raggio)
                }
            }
            m.addOnMapClickListener { punto ->
                val schermo = m.projection.toScreenLocation(punto)
                val raggio = with(densita) { 14.dp.toPx() }
                val zona = RectF(schermo.x - raggio, schermo.y - raggio * 2, schermo.x + raggio, schermo.y + raggio / 2)
                val trovato = m.queryRenderedFeatures(zona, STRATO_PREZZI).firstOrNull()
                val colonnina = trovato?.getStringProperty("colonnina")
                val id = trovato?.getStringProperty("id")?.toLongOrNull()
                when {
                    colonnina != null -> {
                        selezionataEv = colonnina
                        apriFoglio()
                        true
                    }
                    id != null -> {
                        selezionato = id
                        apriFoglio()
                        true
                    }
                    else -> {
                        // un tocco a vuoto chiude il foglio
                        chiudiFoglio()
                        false
                    }
                }
            }
            mappa = m
        }
    }

    // il centro (posizione, luogo o comune) cambia, o si tocca il mirino: la mappa va sul segnaposto
    // e, appena arrivano i prezzi, si allarga quanto basta per vedere i distributori piu vicini
    val centro = dati.centro
    LaunchedEffect(mappa, centro, richiestaCentra) {
        val m = mappa ?: return@LaunchedEffect
        val c = centro ?: return@LaunchedEffect
        // tornando sulla mappa con un'area cercata restiamo li: la camera parte gia dall'area
        if (vm.areaMappa.value != null) return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(c.coordinate.lat, c.coordinate.lon), 13.0), 600)
        adattaVista(m, vistaMappa, vm, c.coordinate, densita, soloSeServe = false, fondoDp = 120)
    }
    LaunchedEffect(mappa, richiestaAdatta) {
        val m = mappa ?: return@LaunchedEffect
        if (richiestaAdatta == 0) return@LaunchedEffect
        val a = vm.areaMappa.value ?: return@LaunchedEffect
        adattaVista(m, vistaMappa, vm, a.centro, densita, soloSeServe = true, fondoDp = 330)
        if (vm.areaMappa.value != a) return@LaunchedEffect
        // la risposta a "Cerca in quest'area": il piu conveniente (o la colonnina piu vicina) si apre nel foglio
        if (vm.mappaColonnine.value) {
            val prima = vm.vistaColonnineMappa.value?.takeIf { it.centro.coordinate == a.centro }?.zona?.vicine?.firstOrNull()
            if (prima != null) {
                selezionataEv = prima.colonnina.id
                apriFoglio()
            }
        } else {
            val migliore = vm.vistaMappa.value?.takeIf { it.centro.coordinate == a.centro }?.let { Convenienza.consigliati(it.zona, 1).firstOrNull() }
            if (migliore != null) {
                selezionato = migliore.distributore.id
                apriFoglio()
            }
        }
    }
    // il segnaposto del centro: un puntino con l'alone per la posizione, un'icona per luoghi e comuni
    LaunchedEffect(stile, centro, utente.luoghi) {
        val s = stile ?: return@LaunchedEffect
        val c = centro
        val icona = c?.let { chiaveSegnaposto(it, utente.luoghi) }
        val punto = c?.let { Feature.fromGeometry(Point.fromLngLat(it.coordinate.lon, it.coordinate.lat)) }
        s.getSourceAs<GeoJsonSource>(SORGENTE_IO)?.setGeoJson(FeatureCollection.fromFeatures(listOfNotNull(punto.takeIf { icona == null })))
        if (icona != null && immaginiCaricate.add(icona)) {
            iconeLuogo[icona]?.let { s.addImage(icona, disegnaSegnaposto(it, densita)) }
        }
        val luogo = if (icona != null && punto != null) listOf(punto.apply { addStringProperty("icona", icona) }) else emptyList()
        s.getSourceAs<GeoJsonSource>(SORGENTE_LUOGO)?.setGeoJson(FeatureCollection.fromFeatures(luogo))
    }
    // prezzi (o colonnine) sulla mappa
    LaunchedEffect(stile, vista, selezionato, colonnine, vistaEv, selezionataEv) {
        val s = stile ?: return@LaunchedEffect
        if (colonnine) {
            // la colonnina aperta nel foglio ha il bordo scuro
            val elementi = vistaEv?.zona?.vicine.orEmpty().take(MASSIMO_PIN).map { cv ->
                val c = cv.colonnina
                val sel = c.id == selezionataEv
                // senza potenza nota il segnaposto mostra solo il fulmine
                val testo = c.kw?.let { Formati.kw(it) } ?: ""
                val aspetto = stileColonnina(c, sel)
                val chiave = "c-$testo-${aspetto.fondo}-${aspetto.bordo}-$sel"
                if (immaginiCaricate.add(chiave)) {
                    s.addImage(chiave, pin.disegna(testo, aspetto.fondo, sel, aspetto.testo, aspetto.bordo, fulmine = testo.isEmpty()))
                }
                Feature.fromGeometry(Point.fromLngLat(c.lon, c.lat)).apply {
                    addStringProperty("colonnina", c.id)
                    addStringProperty("icona", chiave)
                    addNumberProperty("ordine", if (sel) -1_000.0 else -(c.kw ?: 0.0))
                }
            }
            val sorgente = s.getSourceAs<GeoJsonSource>(SORGENTE_PREZZI)
            sorgente?.setGeoJson(FeatureCollection.fromFeatures(elementi))
            Log.i("Goccia", "mappa: ${elementi.size} colonnine, sorgente ${if (sorgente != null) "ok" else "assente"}")
            return@LaunchedEffect
        }
        val v = vista ?: return@LaunchedEffect
        val offerte = v.zona.tutte.sortedBy { it.distanzaKm }.take(MASSIMO_PIN)
        // il piu conveniente e piu grande degli altri; quello aperto nel foglio ha anche il bordo scuro
        val migliore = Convenienza.consigliati(v.zona, 1).firstOrNull()?.distributore?.id
        val elementi = offerte.map { o ->
            val sel = o.distributore.id == selezionato
            val grande = sel || o.distributore.id == migliore
            val prezzo = Formati.prezzo(o.prezzo.millesimi)
            val aspetto = stilePrezzo(o, sel)
            val nome = Bandiere.breve(o.distributore)
            val chiave = "p-$prezzo-$nome-${aspetto.fondo}-${aspetto.bordo}-$grande"
            if (immaginiCaricate.add(chiave)) {
                s.addImage(chiave, pin.disegna(prezzo, aspetto.fondo, grande, aspetto.testo, aspetto.bordo, etichetta = nome))
            }
            Feature.fromGeometry(Point.fromLngLat(o.distributore.lon, o.distributore.lat)).apply {
                addStringProperty("id", o.distributore.id.toString())
                addStringProperty("icona", chiave)
                addNumberProperty("ordine", if (sel) -2 else if (grande) -1 else o.prezzo.millesimi)
            }
        }
        val sorgente = s.getSourceAs<GeoJsonSource>(SORGENTE_PREZZI)
        sorgente?.setGeoJson(FeatureCollection.fromFeatures(elementi))
        Log.i("Goccia", "mappa: ${elementi.size} prezzi, sorgente ${if (sorgente != null) "ok" else "assente"}")
    }
    // il distributore aperto non c'e piu (altro carburante, altre prese): il foglio si chiude
    val sceltaSparita = if (colonnine) {
        selezionataEv != null && vistaEv != null && vistaEv.zona.vicine.none { it.colonnina.id == selezionataEv }
    } else {
        selezionato != null && vista != null && vista.zona.tutte.none { it.distributore.id == selezionato }
    }
    LaunchedEffect(sceltaSparita) { if (sceltaSparita) chiudiFoglio() }

    // "Cerca in quest'area": i risultati diventano quelli attorno al centro della mappa
    fun cercaQui() {
        val m = mappa ?: return
        val posizione = m.cameraPosition
        val bersaglio = posizione.target ?: return
        chiudiFoglio()
        mostraCerca = false
        val punto = Coordinate(bersaglio.latitude, bersaglio.longitude)
        if (posizione.zoom < ZOOM_PREZZI) {
            // da cosi lontano si vedono intere regioni: avviciniamo la mappa a una zona di qualche km
            m.animateCamera(CameraUpdateFactory.newLatLngZoom(bersaglio, ZOOM_AREA), 700)
            vm.cercaInArea(punto, ZOOM_AREA, 10)
        } else {
            camera.base = posizione
            vm.cercaInArea(punto, posizione.zoom, raggioVisibile(m))
        }
        Log.i("Goccia", "mappa: cerco in quest'area (zoom ${"%.1f".format(posizione.zoom)})")
        richiestaAdatta++
    }

    // il mirino riporta sempre al segnaposto; se e la posizione, la aggiorna
    fun tornaAlSegnaposto() {
        chiudiFoglio()
        vm.annullaArea()
        mostraCerca = false
        richiestaCentra++
        val c = dati.centro
        if (c == null || c.tipo == TipoCentro.POSIZIONE || c.tipo == TipoCentro.ULTIMA) vaiDoveSei()
        Log.i("Goccia", "mappa: torno al segnaposto (${c?.etichetta ?: "nessun centro"})")
    }

    // cosa dire sotto l'intestazione: il pulsante per cercare, un caricamento o un avviso
    val avviso: Pair<String, Boolean>? = when {
        cercoArea -> "Cerco in quest'area…" to true
        centro == null && area == null -> null
        colonnine && statoColonnine.nonDisponibili && statoColonnine.tutte.isEmpty() ->
            "Colonnine non disponibili: controlla la connessione" to false
        colonnine && (vistaEv == null || (vistaEv.zona.vicine.isEmpty() && statoColonnine.caricamento)) -> "Carico le colonnine…" to true
        colonnine && vistaEv != null && vistaEv.zona.vicine.isEmpty() -> "Nessuna colonnina compatibile: prova altre prese" to false
        !colonnine && (vista == null || dati.caricamento) -> "Carico i distributori…" to true
        !colonnine && vista != null && vista.zona.offerte.isEmpty() ->
            "Nessun distributore con ${vista.carburante.etichetta.lowercase()} qui" to false
        else -> null
    }
    val mostraBottone = mostraCerca && !cercoArea

    BottomSheetScaffold(
        sheetContent = {
            val toccaLinguetta: () -> Unit = {
                scope.launch { if (foglio.currentValue == SheetValue.Expanded) foglio.partialExpand() else foglio.expand() }
            }
            if (colonnine) {
                FoglioColonnina(
                    vm = vm,
                    vista = vistaEv,
                    selezionata = selezionataEv,
                    inArea = area != null,
                    espanso = espanso,
                    onLinguetta = toccaLinguetta,
                    onAltezzaPiccola = { altezzaPiccola = it },
                    onColonnina = onColonnina,
                )
            } else {
                FoglioDistributore(
                    vm = vm,
                    vista = vista,
                    selezionato = selezionato,
                    inArea = area != null,
                    espanso = espanso,
                    onLinguetta = toccaLinguetta,
                    onAltezzaPiccola = { altezzaPiccola = it },
                    onDistributore = onDistributore,
                    onLista = onLista,
                )
            }
        },
        scaffoldState = impalcatura,
        sheetPeekHeight = if (altezzaPiccola > 0) with(densita) { altezzaPiccola.toDp() } else 190.dp,
        sheetShape = Forme.Foglio,
        sheetContainerColor = Colori.Superficie,
        sheetContentColor = Colori.Inchiostro,
        // chiuso, il foglio sta sotto il bordo: senza ombra non lascia una riga in fondo alla mappa
        sheetShadowElevation = if (fogliochiuso) 0.dp else 10.dp,
        sheetDragHandle = null,
        containerColor = Colori.Sfondo,
        contentColor = Colori.Inchiostro,
    ) { _ ->
        Box(Modifier.fillMaxSize().background(Colori.Sfondo).onSizeChanged { altezzaContenuto = it.height }) {
            AndroidView(factory = { vistaMappa }, modifier = Modifier.fillMaxSize())

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                // intestazione con ricerca e carburanti
                Column(
                    Modifier
                        .fillMaxWidth()
                        .ombra(RoundedCornerShape(0.dp), 4.dp)
                        .background(Colori.Sfondo.copy(alpha = 0.94f))
                        .statusBarsPadding()
                        .padding(bottom = 12.dp),
                ) {
                    Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        CampoRicerca(testo, { testo = it }, Modifier.weight(1f), segnaposto = "Cerca un comune")
                        if (!colonnine) {
                            BottoneIcona(
                                Icone.Lista, "Vedi come lista", onLista,
                                colore = Color.White, sfondo = Colori.Inchiostro, dimensione = 52.dp, forma = RoundedCornerShape(16.dp),
                            )
                        }
                    }
                    if (testo.isNotBlank()) {
                        SuggerimentiComuni(
                            vm.cercaComuni(testo),
                            onScelto = {
                                testo = ""
                                chiudiFoglio()
                                vm.centraSu(it)
                            },
                            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                        )
                    }
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChipScelta(
                            "Colonnine",
                            colonnine,
                            onClick = {
                                // il foglio aperto e di un distributore (o di una colonnina): prima si chiude
                                val dopo = !colonnine
                                if (fogliochiuso) {
                                    vm.mostraColonnine(dopo)
                                } else {
                                    scope.launch {
                                        try {
                                            foglio.hide()
                                        } finally {
                                            vm.mostraColonnine(dopo)
                                        }
                                    }
                                }
                            },
                            icona = Icone.Fulmine,
                            altezza = 38.dp,
                        )
                        if (colonnine) {
                            val prese = utente.prese
                            Presa.filtrabili.forEach { p ->
                                ChipScelta(p.etichetta, p in prese, onClick = { vm.scegliPrese(if (p in prese) prese - p else prese + p) }, altezza = 38.dp)
                            }
                            val potenze = listOf(0, 50, 150)
                            val attuale = utente.impostazioni.potenzaMinima
                            ChipScelta(
                                if (attuale == 0) "Tutte le potenze" else "≥ $attuale kW",
                                attuale > 0,
                                onClick = { vm.scegliPotenzaMinima(potenze[(potenze.indexOf(attuale).coerceAtLeast(0) + 1) % potenze.size]) },
                                altezza = 38.dp,
                            )
                        } else {
                            Carburante.entries.forEach { c ->
                                ChipScelta(c.etichetta, c == utente.carburante, onClick = { vm.scegliCarburante(c) }, altezza = 38.dp)
                            }
                        }
                    }
                    Legenda(colonnine, Modifier.padding(start = 20.dp, end = 20.dp, top = 10.dp))
                }
                AnimatedVisibility(
                    visible = testo.isBlank() && (mostraBottone || avviso != null),
                    enter = fadeIn() + slideInVertically { -it / 2 },
                    exit = fadeOut() + slideOutVertically { -it / 2 },
                ) {
                    val a = avviso
                    when {
                        mostraBottone -> BottoneCercaQui(onClick = { cercaQui() }, modifier = Modifier.padding(top = 12.dp))
                        a != null -> AvvisoMappa(a.first, a.second, Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp))
                    }
                }
            }

            // da dove si cerca, attribuzione e mirino: stanno sempre sopra il foglio, anche mentre si muove
            Row(
                Modifier
                    .align(Alignment.BottomCenter)
                    .offset {
                        val cima = runCatching { foglio.requireOffset() }.getOrNull()
                        val su = if (cima == null || cima.isNaN() || altezzaContenuto == 0) 0
                        else (altezzaContenuto - cima).roundToInt().coerceIn(0, altezzaContenuto)
                        IntOffset(0, -su)
                    }
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    SceltaPartenza(
                        centro = centro,
                        luoghi = utente.luoghi,
                        onPosizione = {
                            vaiDoveSei()
                            if (centro?.tipo == TipoCentro.POSIZIONE) richiestaCentra++
                        },
                        onLuogo = { luogo ->
                            chiudiFoglio()
                            vm.centraSu(luogo)
                            // lo stesso luogo di prima: il centro non cambia, ma la mappa ci torna
                            if (centro?.luogoId == luogo.id) richiestaCentra++
                        },
                        onNuovoLuogo = onNuovoLuogo,
                    )
                    Text(
                        "© OpenStreetMap · OpenFreeMap" + if (colonnine && vm.colonnineDaPun) " · Colonnine: GSE – PUN" else "",
                        style = Testi.Minimo.copy(color = Colori.Testo2, fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                        modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.8f)).padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                Spacer(Modifier.width(12.dp))
                BottoneIcona(
                    Icone.Mirino, "Torna al segnaposto", { tornaAlSegnaposto() },
                    Modifier.ombra(CircleShape, 4.dp),
                    colore = Colori.Petrolio, sfondo = Colori.Superficie,
                )
            }
        }
    }
}

/** Il pulsante che compare sotto l'intestazione quando la mappa si sposta. */
@Composable
private fun BottoneCercaQui(onClick: () -> Unit, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(20.dp)
    Row(
        modifier
            .height(40.dp)
            .ombra(forma, 6.dp)
            .clip(forma)
            .background(Colori.Petrolio)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = 14.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icone.Cerca, null, tint = Color.White, modifier = Modifier.size(17.dp))
        Text("Cerca in quest'area", style = Testi.ChipAttivo.copy(color = Color.White))
    }
}

/**
 * Da dove cerchiamo: la posizione di adesso, un luogo salvato o il comune cercato. Il pulsante
 * dice qual e; il menu permette di cambiarlo o di salvare un luogo nuovo.
 */
@Composable
private fun SceltaPartenza(
    centro: Centro?,
    luoghi: List<Luogo>,
    onPosizione: () -> Unit,
    onLuogo: (Luogo) -> Unit,
    onNuovoLuogo: (tipo: String) -> Unit,
) {
    var aperto by remember { mutableStateOf(false) }
    val forma = RoundedCornerShape(20.dp)
    Box {
        Row(
            Modifier
                .height(40.dp)
                .ombra(forma, 4.dp)
                .clip(forma)
                .background(Colori.Superficie)
                .clickable(role = Role.Button, onClickLabel = "Cambia") { aperto = true }
                .padding(start = 12.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Icon(iconaCentro(centro, luoghi), null, tint = Colori.Petrolio, modifier = Modifier.size(17.dp))
            Text(
                centro?.etichetta ?: "Scegli da dove cercare",
                style = Testi.ChipAttivo,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 200.dp),
            )
            Icon(Icone.ChevronGiu, null, tint = Colori.Testo3, modifier = Modifier.size(16.dp))
        }
        DropdownMenu(expanded = aperto, onDismissRequest = { aperto = false }) {
            Text(
                "Cerca vicino a",
                style = Testi.Piccolo.copy(color = Colori.Testo3),
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 6.dp),
            )
            VocePartenza("Dove sei adesso", Icone.Mirino, scelta = centro?.tipo == TipoCentro.POSIZIONE) {
                aperto = false
                onPosizione()
            }
            luoghi.forEach { luogo ->
                VocePartenza(luogo.nome, iconaLuogo(luogo.tipo), scelta = centro?.luogoId == luogo.id) {
                    aperto = false
                    onLuogo(luogo)
                }
            }
            if (centro != null && centro.tipo == TipoCentro.COMUNE) {
                VocePartenza(centro.etichetta, Icone.Segnaposto, scelta = true) { aperto = false }
            }
            val (tipo, etichetta) = when {
                luoghi.none { it.tipo == TipoLuogo.CASA } -> "CASA" to "Aggiungi Casa"
                luoghi.none { it.tipo == TipoLuogo.LAVORO } -> "LAVORO" to "Aggiungi Lavoro"
                else -> "ALTRO" to "Aggiungi un luogo"
            }
            VocePartenza(etichetta, Icone.Piu, scelta = false) {
                aperto = false
                onNuovoLuogo(tipo)
            }
        }
    }
}

@Composable
private fun VocePartenza(testo: String, icona: ImageVector, scelta: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Text(
                testo,
                style = if (scelta) Testi.ChipAttivo.copy(color = Colori.PetrolioScuro) else Testi.Chip,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        onClick = onClick,
        leadingIcon = { Icon(icona, null, tint = Colori.Petrolio, modifier = Modifier.size(18.dp)) },
        trailingIcon = if (scelta) {
            { Icon(Icone.Spunta, null, tint = Colori.Petrolio, modifier = Modifier.size(18.dp)) }
        } else {
            null
        },
    )
}

private fun luogoDi(c: Centro, luoghi: List<Luogo>): Luogo? = c.luogoId?.let { id -> luoghi.firstOrNull { it.id == id } }

/** L'icona del segnaposto del centro; null per la posizione, che e un puntino con l'alone. */
private fun chiaveSegnaposto(c: Centro, luoghi: List<Luogo>): String? = when (c.tipo) {
    TipoCentro.POSIZIONE, TipoCentro.ULTIMA -> null
    TipoCentro.LUOGO -> when (luogoDi(c, luoghi)?.tipo) {
        TipoLuogo.CASA -> ICONA_CASA
        TipoLuogo.LAVORO -> ICONA_LAVORO
        else -> ICONA_LUOGO
    }
    else -> ICONA_LUOGO
}

private fun iconaCentro(c: Centro?, luoghi: List<Luogo>): ImageVector = when (c?.let { chiaveSegnaposto(it, luoghi) }) {
    ICONA_CASA -> Icone.Casa
    ICONA_LAVORO -> Icone.Lavoro
    ICONA_LUOGO -> Icone.Segnaposto
    else -> Icone.Mirino
}

private fun iconaLuogo(tipo: TipoLuogo): ImageVector = when (tipo) {
    TipoLuogo.CASA -> Icone.Casa
    TipoLuogo.LAVORO -> Icone.Lavoro
    TipoLuogo.ALTRO -> Icone.Segnaposto
}

/** Il segnaposto di un luogo o di un comune: un tondo petrolio con il bordo bianco e l'icona. */
private fun disegnaSegnaposto(icona: Painter, densita: Density): Bitmap {
    val lato = with(densita) { 34.dp.toPx() }
    val bordo = with(densita) { 3.dp.toPx() }
    val glifo = with(densita) { 18.dp.toPx() }
    val immagine = ImageBitmap(ceil(lato).toInt(), ceil(lato).toInt())
    CanvasDrawScope().draw(densita, LayoutDirection.Ltr, Canvas(immagine), Size(lato, lato)) {
        drawCircle(Colori.Inchiostro.copy(alpha = 0.18f), radius = lato / 2)
        drawCircle(Color.White, radius = lato / 2 - bordo / 3)
        drawCircle(Colori.Petrolio, radius = lato / 2 - bordo)
        translate((lato - glifo) / 2, (lato - glifo) / 2) {
            with(icona) { draw(Size(glifo, glifo), colorFilter = ColorFilter.tint(Color.White)) }
        }
    }
    return immagine.asAndroidBitmap()
}

/** Larghezza della mappa visibile, in km. */
private fun larghezzaVisibileKm(m: MapLibreMap): Double {
    val r = m.projection.visibleRegion
    val sinistra = r.nearLeft ?: return 0.0
    val destra = r.nearRight ?: return 0.0
    return Geo.distanzaKm(sinistra.latitude, sinistra.longitude, destra.latitude, destra.longitude)
}

/** Il raggio di "Cerca in quest'area": quello che si vede, tra 2 e 30 km. */
private fun raggioVisibile(m: MapLibreMap): Int = (larghezzaVisibileKm(m) / 2).roundToInt().coerceIn(2, RAGGIO_MASSIMO_KM)

/**
 * L'utente ha spostato la mappa abbastanza da meritare una nuova ricerca: lontano da dove
 * erano i risultati, oppure si vede molto piu in largo del raggio cercato.
 */
private fun spostataLontano(m: MapLibreMap, base: CameraPosition, adesso: CameraPosition, raggioKm: Int?): Boolean {
    val da = base.target ?: return true
    val a = adesso.target ?: return false
    val larghezza = larghezzaVisibileKm(m)
    val spostamento = Geo.distanzaKm(da.latitude, da.longitude, a.latitude, a.longitude)
    return spostamento > maxOf(0.5, larghezza * 0.25) || (raggioKm != null && larghezza / 2 > raggioKm * 1.8)
}

/**
 * Quando arrivano i risultati attorno a [punto] allarga la vista quanto basta per vedere i piu
 * vicini e il piu conveniente (o le colonnine piu vicine). Con [soloSeServe] la mappa si muove
 * solo se il piu conveniente resterebbe fuori, o sotto l'intestazione o quello che c'e in basso
 * ([fondoDp]: il mirino, e il foglio se si aprira).
 */
private suspend fun adattaVista(
    m: MapLibreMap,
    vistaMappa: MapView,
    vm: GocciaViewModel,
    punto: Coordinate,
    densita: Density,
    soloSeServe: Boolean,
    fondoDp: Int,
) {
    val vicini: List<LatLng>
    val principale: LatLng?
    withTimeoutOrNull(20_000) { vm.cercoArea.first { !it } }
    // una zona cercata sulla mappa e gia caricata: se resta vuota non aspettiamo oltre
    val attesa = if (soloSeServe) 6_000L else 20_000L
    if (vm.mappaColonnine.value) {
        val attorno = withTimeoutOrNull(attesa) {
            vm.vistaColonnineMappa.first { it != null && it.centro.coordinate == punto && it.zona.vicine.isNotEmpty() }
        }
        vicini = attorno?.zona?.vicine.orEmpty().take(4).map { LatLng(it.colonnina.lat, it.colonnina.lon) }
        principale = vicini.firstOrNull()
    } else {
        val attorno = withTimeoutOrNull(attesa) {
            vm.vistaMappa.first { it != null && it.centro.coordinate == punto && it.zona.offerte.isNotEmpty() }
        }
        // i piu vicini e il piu conveniente, che il foglio in basso descrive: deve stare nella vista
        val zona = attorno?.zona
        val consigliato = zona?.let { Convenienza.consigliati(it, 1) }.orEmpty().map { LatLng(it.distributore.lat, it.distributore.lon) }
        vicini = zona?.offerte.orEmpty().sortedBy { it.distanzaKm }.take(4).map { LatLng(it.distributore.lat, it.distributore.lon) } + consigliato
        principale = consigliato.firstOrNull() ?: vicini.firstOrNull()
    }
    Log.i("Goccia", "mappa: adatto la vista a ${vicini.size} punti vicini")
    if (vicini.isEmpty()) return
    fun px(valore: Int) = with(densita) { valore.dp.roundToPx() }
    if (soloSeServe && principale != null) {
        val schermo = m.projection.toScreenLocation(principale)
        val visibile = schermo.x >= px(24) && schermo.x <= vistaMappa.width - px(24) &&
            schermo.y >= px(210) && schermo.y <= vistaMappa.height - px(fondoDp)
        if (visibile) return
    }
    val limiti = LatLngBounds.Builder()
        .include(LatLng(punto.lat + 0.004, punto.lon + 0.005))
        .include(LatLng(punto.lat - 0.004, punto.lon - 0.005))
    vicini.forEach { limiti.include(it) }
    m.animateCamera(CameraUpdateFactory.newLatLngBounds(limiti.build(), px(48), px(210), px(48), px(fondoDp)), 700)
}

/** Colori di un segnaposto: riempimento, testo e bordo (ARGB). */
private data class StilePin(val fondo: Int, val testo: Int, val bordo: Int)

private const val BIANCO = android.graphics.Color.WHITE

/**
 * Verde i convenienti (almeno 3 centesimi sotto la media della zona), blu scuro quelli nella media
 * o sopra, bianco con il bordo grigio i prezzi vecchi; il scelto ha il bordo scuro.
 */
private fun stilePrezzo(o: Offerta, scelto: Boolean): StilePin {
    val base = when {
        o.vecchio -> StilePin(BIANCO, Colori.Testo3.toArgb(), Colori.Linea.toArgb())
        o.tono == Tono.CONVENIENTE -> StilePin(Colori.VerdeTesto.toArgb(), BIANCO, BIANCO)
        else -> StilePin(Colori.PinAltri.toArgb(), BIANCO, BIANCO)
    }
    return if (scelto) base.copy(bordo = Colori.Inchiostro.toArgb()) else base
}

/** Petrolio pieno le colonnine veloci (in continua), bianche con il bordo petrolio le lente. */
private fun stileColonnina(c: Colonnina, scelta: Boolean): StilePin {
    val base = if (c.continua) StilePin(Colori.Petrolio.toArgb(), BIANCO, BIANCO)
    else StilePin(BIANCO, Colori.PetrolioScuro.toArgb(), Colori.Petrolio.toArgb())
    return if (scelta) base.copy(bordo = Colori.Inchiostro.toArgb()) else base
}

/** Cosa vogliono dire i colori dei segnaposto, sempre visibile sotto i filtri (va a capo se non ci sta). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Legenda(colonnine: Boolean, modifier: Modifier = Modifier) {
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (colonnine) {
            VoceLegenda(Colori.Petrolio, null, "Veloci (in continua)")
            VoceLegenda(Color.White, Colori.Petrolio, "Lente (in alternata)")
        } else {
            VoceLegenda(Colori.VerdeTesto, null, "Convenienti")
            VoceLegenda(Colori.PinAltri, null, "Nella media o sopra")
            VoceLegenda(Color.White, Colori.Linea, "Prezzo vecchio")
        }
    }
}

@Composable
private fun VoceLegenda(fondo: Color, bordo: Color?, testo: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val forma = RoundedCornerShape(6.dp)
        Box(
            Modifier
                .size(width = 18.dp, height = 12.dp)
                .clip(forma)
                .background(fondo)
                .then(if (bordo != null) Modifier.border(1.5.dp, bordo, forma) else Modifier),
        )
        Text(testo, style = Testi.Piccolo.copy(color = Colori.Testo2, fontWeight = FontWeight.SemiBold))
    }
}

/** La linguetta in cima al foglio: si trascina, o si tocca per espandere e ridurre. */
@Composable
private fun Linguetta(espanso: Boolean, onClick: () -> Unit) {
    val descrizione = if (espanso) "Riduci i dettagli" else "Espandi i dettagli"
    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClickLabel = descrizione, onClick = onClick)
            .semantics { contentDescription = descrizione },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Colori.InterruttoreSpento))
    }
}

/** Sotto l'intestazione: un caricamento in corso o un avviso sulla zona. */
@Composable
private fun AvvisoMappa(testo: String, caricamento: Boolean, modifier: Modifier = Modifier) {
    val forma = RoundedCornerShape(20.dp)
    Row(
        modifier
            .heightIn(min = 40.dp)
            .ombra(forma, 4.dp)
            .clip(forma)
            .background(Colori.Superficie)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (caricamento) {
            CircularProgressIndicator(color = Colori.Petrolio, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
        } else {
            Icon(Icone.Info, null, tint = Colori.Testo3, modifier = Modifier.size(17.dp))
        }
        Text(testo, style = Testi.Chip.copy(color = Colori.TestoChip))
    }
}

/**
 * Il foglio di un distributore. Piccolo: chi e, quanto costa, Naviga e Dettagli. Espanso: tutti
 * i suoi prezzi e quanti distributori ci sono nell'area. [onAltezzaPiccola] riceve l'altezza
 * della parte piccola, che e quella mostrata quando il foglio si apre.
 */
@Composable
private fun FoglioDistributore(
    vm: GocciaViewModel,
    vista: VistaZona?,
    selezionato: Long?,
    inArea: Boolean,
    espanso: Boolean,
    onLinguetta: () -> Unit,
    onAltezzaPiccola: (Int) -> Unit,
    onDistributore: (Distributore) -> Unit,
    onLista: () -> Unit,
) {
    val context = LocalContext.current
    val utente by vm.utente.collectAsStateWithLifecycle()
    val offerta = vista?.zona?.tutte?.firstOrNull { it.distributore.id == selezionato }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
        Column(Modifier.onSizeChanged { onAltezzaPiccola(it.height) }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Linguetta(espanso, onLinguetta)
            if (vista != null && offerta != null) {
                val d = offerta.distributore
                if (d.id == Convenienza.consigliati(vista.zona, 1).firstOrNull()?.distributore?.id) {
                    Text(
                        if (inArea) "Il più conveniente in quest'area" else "Il più conveniente qui vicino",
                        style = Testi.Piccolo.copy(color = Colori.Petrolio, fontWeight = FontWeight.Bold),
                    )
                }
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { onDistributore(d) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    LogoBandiera(d.bandiera, d.pompaBianca, dimensione = 48.dp, angolo = 14.dp, testo = 15.sp)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(d.titolo, style = Testi.Sottosezione, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(metaOfferta(offerta, vista.adessoMillis), style = Testi.Didascalia.copy(color = Colori.Testo3), maxLines = 1)
                        BadgeConvenienza(offerta.differenzaCent, offerta.tono)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(Formati.prezzo(offerta.prezzo.millesimi), style = Testi.PrezzoMappa)
                        Text(vista.carburante.unitaCon(offerta.self), style = Testi.Piccolo.copy(color = Colori.Testo3))
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BottonePrimario("Naviga", { naviga(context, d, utente.impostazioni.navigazione) }, Modifier.weight(1f), icona = Icone.Naviga, altezza = 46.dp)
                    BottoneSecondario("Dettagli", { onDistributore(d) }, Modifier.weight(1f), altezza = 46.dp)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        if (vista != null && offerta != null) {
            // quello che si vede tirando su la linguetta
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Colori.Divisore))
                Text("Prezzi di questo distributore", style = Testi.DidascaliaForte.copy(color = Colori.Testo3))
                PrezziDistributore(offerta.distributore, vista.carburante)
                Box(Modifier.fillMaxWidth().height(1.dp).background(Colori.Divisore))
                val vicine = vista.zona.offerte
                val minimo = vicine.filter { !it.vecchio }.minOfOrNull { it.prezzo.millesimi }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${vicine.size} distributori nell'area" + (minimo?.let { " · da ${Formati.prezzo(it)} ${vista.carburante.unita}" } ?: ""),
                        style = Testi.Didascalia.copy(color = Colori.Testo3, fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Vedi lista",
                        style = Testi.DidascaliaForte.copy(color = Colori.Petrolio),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onLista).padding(6.dp),
                    )
                }
            }
        }
    }
}

/** Tutti i prezzi di un distributore, self e servito; in grassetto il carburante scelto. */
@Composable
private fun PrezziDistributore(d: Distributore, attivo: Carburante) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            Box(Modifier.weight(1f))
            Text("Self", style = Testi.Piccolo.copy(color = Colori.Testo3), textAlign = TextAlign.End, modifier = Modifier.width(86.dp))
            Text("Servito", style = Testi.Piccolo.copy(color = Colori.Testo3), textAlign = TextAlign.End, modifier = Modifier.width(86.dp))
        }
        Carburante.entries.forEach { c ->
            val p = d.prezzi[c]
            if (p == null || (p.self == null && p.servito == null)) return@forEach
            val stile = if (c == attivo) Testi.CorpoForte else Testi.Corpo.copy(color = Colori.Testo2)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(c.etichetta, style = stile, modifier = Modifier.weight(1f))
                Text(p.self?.let { Formati.prezzo(it.millesimi) } ?: "—", style = stile, textAlign = TextAlign.End, modifier = Modifier.width(86.dp))
                Text(p.servito?.let { Formati.prezzo(it.millesimi) } ?: "—", style = stile, textAlign = TextAlign.End, modifier = Modifier.width(86.dp))
            }
        }
    }
}

/**
 * Il foglio di una colonnina. Piccolo: chi e, potenza e tariffa, punti liberi adesso, Naviga e
 * Dettagli. Espanso: quanto costa una ricarica con la tua auto e quante colonnine ci sono attorno.
 */
@Composable
private fun FoglioColonnina(
    vm: GocciaViewModel,
    vista: VistaColonnine?,
    selezionata: String?,
    inArea: Boolean,
    espanso: Boolean,
    onLinguetta: () -> Unit,
    onAltezzaPiccola: (Int) -> Unit,
    onColonnina: (Colonnina) -> Unit,
) {
    val context = LocalContext.current
    val utente by vm.utente.collectAsStateWithLifecycle()
    val vicine = vista?.zona?.vicine.orEmpty()
    val scelta = vicine.firstOrNull { it.colonnina.id == selezionata }
    Column(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 20.dp)) {
        Column(Modifier.onSizeChanged { onAltezzaPiccola(it.height) }, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Linguetta(espanso, onLinguetta)
            if (scelta != null) {
                val c = scelta.colonnina
                val usata = vm.tariffaUsata(c)
                val statoPunti by produceState<StatoColonnina?>(null, c.id) { value = vm.statoColonnina(c) }
                if (c.id == vicine.firstOrNull()?.colonnina?.id) {
                    Text(
                        if (inArea) "La più vicina al centro dell'area" else "La più vicina",
                        style = Testi.Piccolo.copy(color = Colori.Petrolio, fontWeight = FontWeight.Bold),
                    )
                }
                Row(
                    Modifier.clip(RoundedCornerShape(12.dp)).clickable { onColonnina(c) },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    IconaColonnina(c, 48)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(titoloColonnina(c), style = Testi.Sottosezione, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(metaColonnina(c, scelta.distanzaKm), style = Testi.Didascalia.copy(color = Colori.Testo3), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(potenzaColonnina(c), style = Testi.PrezzoMedio)
                        Text(testoTariffa(c, usata), style = Testi.Piccolo.copy(color = Colori.Testo3))
                    }
                }
                RigaStatoPunti(statoPunti)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BottonePrimario("Naviga", { naviga(context, c.lat, c.lon, c.titolo, utente.impostazioni.navigazione) }, Modifier.weight(1f), icona = Icone.Naviga, altezza = 46.dp)
                    BottoneSecondario("Dettagli e costi", { onColonnina(c) }, Modifier.weight(1f), altezza = 46.dp)
                }
                Spacer(Modifier.height(6.dp))
            }
        }
        if (vista != null && scelta != null) {
            // quello che si vede tirando su la linguetta
            val c = scelta.colonnina
            Column(Modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(Colori.Divisore))
                val auto = utente.autoCorrente?.takeIf { it.alimentazione.elettrica }
                if (auto != null) {
                    val tariffa = vm.tariffaUsata(c).euroKwh
                    val kwh = 0.6 * auto.capienza
                    val erogazione = Elettrico.erogazione(c, utente.prese, auto.acKw, auto.dcKw)
                    Text(
                        buildString {
                            append("Dal 20% all'80% (${Formati.numero(kwh, 0)} kWh): circa ${Formati.euro(if (c.gratuita) 0.0 else kwh * tariffa)}")
                            if (erogazione != null) {
                                append(" e ${Formati.durata(Elettrico.minutiRicarica(auto.capienza, 0.2, 0.8, erogazione))}")
                                if (!erogazione.continua) append(" (${Formati.kw(erogazione.kw)} di bordo)")
                            }
                            append(".")
                        },
                        style = Testi.Didascalia.copy(color = Colori.TestoChip),
                    )
                }
                val massima = vicine.mapNotNull { it.colonnina.kw }.maxOrNull()
                Text(
                    "${vicine.size} ${if (vicine.size == 1) "colonnina" else "colonnine"} entro ${vista.zona.raggioKm} km" +
                        (massima?.let { " · fino a ${Formati.kw(it)}" } ?: ""),
                    style = Testi.Didascalia.copy(color = Colori.Testo3, fontWeight = FontWeight.SemiBold),
                )
            }
        }
    }
}
