package it.goccia.app.ui.mappa

import android.graphics.RectF
import android.util.Log
import android.view.Gravity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Colonnina
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.Distributore
import it.goccia.app.dati.Presa
import it.goccia.app.dati.StatoColonnina
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.Elettrico
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Bandiere
import it.goccia.app.logica.Offerta
import it.goccia.app.logica.Tono
import it.goccia.app.ui.GocciaViewModel
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
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
private const val MASSIMO_PIN = 1500

@Composable
fun SchermataMappa(
    vm: GocciaViewModel,
    onDistributore: (Distributore) -> Unit,
    onLista: () -> Unit,
    onColonnina: (Colonnina) -> Unit,
) {
    LaunchedEffect(Unit) { vm.avvia() }
    val context = LocalContext.current
    val densita = LocalDensity.current
    val dati by vm.dati.collectAsStateWithLifecycle()
    val utente by vm.utente.collectAsStateWithLifecycle()
    val statoVista by vm.vistaMappa.collectAsStateWithLifecycle()
    val vista = statoVista
    // colonnine al posto dei distributori (di serie per le auto elettriche)
    val colonnine by vm.mappaColonnine.collectAsStateWithLifecycle()
    val statoVistaEv by vm.vistaColonnineMappa.collectAsStateWithLifecycle()
    val vistaEv = statoVistaEv
    var selezionataEv by rememberSaveable { mutableStateOf<String?>(null) }

    var selezionato by rememberSaveable { mutableStateOf<Long?>(null) }
    var testo by rememberSaveable { mutableStateOf("") }
    var mappa by remember { mutableStateOf<MapLibreMap?>(null) }
    var stile by remember { mutableStateOf<Style?>(null) }
    // da lontano (tutta Italia) non carichiamo i prezzi: chiediamo di avvicinarsi
    var zoom by remember { mutableDoubleStateOf(if (vm.dati.value.centro != null) 13.0 else 5.0) }
    val pin = remember { PinPrezzo(context) }
    val immaginiCaricate = remember { HashSet<String>() }

    val vistaMappa = rememberVistaMappa { v ->
        v.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.attributionGravity = Gravity.TOP or Gravity.END
            val margine = with(densita) { 12.dp.roundToPx() }
            m.uiSettings.setAttributionMargins(0, with(densita) { 170.dp.roundToPx() }, margine, 0)
            val iniziale = vm.dati.value.centro?.coordinate
            if (iniziale != null) {
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(iniziale.lat, iniziale.lon), 13.0))
            } else {
                m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(42.5, 12.5), 5.0))
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
            m.addOnCameraIdleListener {
                val bersaglio = m.cameraPosition.target ?: return@addOnCameraIdleListener
                zoom = m.cameraPosition.zoom
                vm.mappaSpostata(Coordinate(bersaglio.latitude, bersaglio.longitude), m.cameraPosition.zoom)
            }
            m.addOnMapClickListener { punto ->
                val schermo = m.projection.toScreenLocation(punto)
                val raggio = with(densita) { 14.dp.toPx() }
                val area = RectF(schermo.x - raggio, schermo.y - raggio * 2, schermo.x + raggio, schermo.y + raggio / 2)
                val trovato = m.queryRenderedFeatures(area, STRATO_PREZZI).firstOrNull()
                val colonnina = trovato?.getStringProperty("colonnina")
                val id = trovato?.getStringProperty("id")?.toLongOrNull()
                when {
                    colonnina != null -> {
                        selezionataEv = colonnina
                        true
                    }
                    id != null -> {
                        selezionato = id
                        true
                    }
                    else -> false
                }
            }
            mappa = m
        }
    }

    // la posizione (o il comune cercato) cambia: spostiamo la mappa e, appena arrivano i prezzi,
    // allarghiamo la vista quanto basta per vedere i distributori piu vicini
    val centro = dati.centro
    LaunchedEffect(mappa, centro) {
        val m = mappa ?: return@LaunchedEffect
        val c = centro ?: return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(c.coordinate.lat, c.coordinate.lon), 13.0), 600)
        val vicini: List<LatLng> = if (vm.mappaColonnine.value) {
            val attorno = withTimeoutOrNull(20_000) { vm.vistaColonnine.first { it != null && it.centro == c && it.zona.vicine.isNotEmpty() } }
            attorno?.zona?.vicine.orEmpty().take(4).map { LatLng(it.colonnina.lat, it.colonnina.lon) }
        } else {
            val attorno = withTimeoutOrNull(20_000) { vm.vista.first { it != null && it.centro == c } }
            // i piu vicini e il piu conveniente, che il foglio in basso descrive: deve stare nella vista
            val zona = attorno?.zona
            val consigliato = zona?.let { Convenienza.consigliati(it, 1) }.orEmpty()
            (zona?.offerte.orEmpty().sortedBy { it.distanzaKm }.take(4) + consigliato)
                .map { LatLng(it.distributore.lat, it.distributore.lon) }
        }
        Log.i("Goccia", "mappa: adatto la vista a ${vicini.size} punti vicini")
        if (vicini.isEmpty()) return@LaunchedEffect
        val limiti = LatLngBounds.Builder()
            .include(LatLng(c.coordinate.lat + 0.004, c.coordinate.lon + 0.005))
            .include(LatLng(c.coordinate.lat - 0.004, c.coordinate.lon - 0.005))
        vicini.forEach { limiti.include(it) }
        fun px(valore: Int) = with(densita) { valore.dp.roundToPx() }
        m.animateCamera(CameraUpdateFactory.newLatLngBounds(limiti.build(), px(48), px(190), px(48), px(260)), 700)
    }
    LaunchedEffect(stile, centro) {
        val s = stile ?: return@LaunchedEffect
        val c = centro?.coordinate
        val punti = if (c != null) listOf(Feature.fromGeometry(Point.fromLngLat(c.lon, c.lat))) else emptyList()
        s.getSourceAs<GeoJsonSource>(SORGENTE_IO)?.setGeoJson(FeatureCollection.fromFeatures(punti))
    }
    // prezzi (o colonnine) sulla mappa
    LaunchedEffect(stile, vista, selezionato, colonnine, vistaEv, selezionataEv) {
        val s = stile ?: return@LaunchedEffect
        if (colonnine) {
            // la colonnina descritta nel foglio in basso (scelta, o la piu vicina) e evidenziata
            val evidenziata = selezionataEv ?: vistaEv?.zona?.vicine?.firstOrNull()?.colonnina?.id
            val elementi = vistaEv?.zona?.vicine.orEmpty().take(MASSIMO_PIN).map { cv ->
                val c = cv.colonnina
                val sel = c.id == evidenziata
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
        // il distributore descritto nel foglio in basso (scelto, o il piu conveniente) e evidenziato
        val evidenziato = selezionato ?: Convenienza.consigliati(v.zona, 1).firstOrNull()?.distributore?.id
        val elementi = offerte.map { o ->
            val sel = o.distributore.id == evidenziato
            val prezzo = Formati.prezzo(o.prezzo.millesimi)
            val aspetto = stilePrezzo(o, sel)
            val nome = Bandiere.breve(o.distributore)
            val chiave = "p-$prezzo-$nome-${aspetto.fondo}-${aspetto.bordo}-$sel"
            if (immaginiCaricate.add(chiave)) {
                s.addImage(chiave, pin.disegna(prezzo, aspetto.fondo, sel, aspetto.testo, aspetto.bordo, etichetta = nome))
            }
            Feature.fromGeometry(Point.fromLngLat(o.distributore.lon, o.distributore.lat)).apply {
                addStringProperty("id", o.distributore.id.toString())
                addStringProperty("icona", chiave)
                addNumberProperty("ordine", if (sel) -1 else o.prezzo.millesimi)
            }
        }
        val sorgente = s.getSourceAs<GeoJsonSource>(SORGENTE_PREZZI)
        sorgente?.setGeoJson(FeatureCollection.fromFeatures(elementi))
        Log.i("Goccia", "mappa: ${elementi.size} prezzi, sorgente ${if (sorgente != null) "ok" else "assente"}")
    }

    Box(Modifier.fillMaxSize().background(Colori.Sfondo)) {
        AndroidView(factory = { vistaMappa }, modifier = Modifier.fillMaxSize())

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
                        selezionato = null
                        selezionataEv = null
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
                        selezionataEv = null
                        vm.mostraColonnine(!colonnine)
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

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    "© OpenStreetMap · OpenFreeMap" + if (colonnine && vm.colonnineDaPun) " · Colonnine: GSE – PUN" else "",
                    style = Testi.Minimo.copy(color = Colori.Testo2, fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.8f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Box(Modifier.weight(1f))
                BottoneIcona(
                    Icone.Mirino, "Centra sulla mia posizione", {
                        selezionato = null
                        selezionataEv = null
                        vm.usaPosizione()
                    },
                    Modifier.ombra(CircleShape, 4.dp),
                    colore = Colori.Petrolio, sfondo = Colori.Superficie,
                )
            }
            if (colonnine) {
                SchedaColonnina(vm, vistaEv, selezionataEv, zoom < ZOOM_PREZZI, onColonnina)
            } else {
                SchedaSelezione(vm, vista, selezionato, zoom < ZOOM_PREZZI, onDistributore, onLista)
            }
        }
    }
}

/** Sotto questo zoom la mappa mostra troppa Italia per caricare i prezzi (vedi GocciaViewModel.mappaSpostata). */
private const val ZOOM_PREZZI = 9.0

/** Colori di un segnaposto: riempimento, testo e bordo (ARGB). */
private data class StilePin(val fondo: Int, val testo: Int, val bordo: Int)

private const val BIANCO = android.graphics.Color.WHITE

/** Verde i convenienti, blu scuro gli altri, bianco con il bordo grigio i prezzi vecchi; il scelto ha il bordo scuro. */
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

/** Cosa vogliono dire i colori dei segnaposto, sempre visibile sotto i filtri. */
@Composable
private fun Legenda(colonnine: Boolean, modifier: Modifier = Modifier) {
    Row(
        modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (colonnine) {
            VoceLegenda(Colori.Petrolio, null, "Veloci (in continua)")
            VoceLegenda(Color.White, Colori.Petrolio, "Lente (in alternata)")
        } else {
            VoceLegenda(Colori.VerdeTesto, null, "Convenienti")
            VoceLegenda(Colori.PinAltri, null, "Altri prezzi")
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

@Composable
private fun SchedaSelezione(
    vm: GocciaViewModel,
    vista: VistaZona?,
    selezionato: Long?,
    lontano: Boolean,
    onDistributore: (Distributore) -> Unit,
    onLista: () -> Unit,
) {
    val context = LocalContext.current
    val utente by vm.utente.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxWidth()
            .ombra(Forme.Foglio, 8.dp)
            .clip(Forme.Foglio)
            .background(Colori.Superficie)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Colori.InterruttoreSpento))
        if (lontano && selezionato == null) {
            Text("Avvicina la mappa per vedere i prezzi", style = Testi.CorpoForte)
            Text("Oppure cerca un comune qui sopra, o tocca il mirino per andare dove sei.", style = Testi.Didascalia.copy(color = Colori.Testo3))
            return@Column
        }
        if (vista == null) {
            Text("Carico i distributori…", style = Testi.Didascalia.copy(color = Colori.Testo3))
            return@Column
        }
        val vicine = vista.zona.offerte
        val offerta: Offerta? = vista.zona.tutte.firstOrNull { it.distributore.id == selezionato }
            ?: Convenienza.consigliati(vista.zona, 1).firstOrNull()
        Row(verticalAlignment = Alignment.CenterVertically) {
            val minimo = vicine.filter { !it.vecchio }.minOfOrNull { it.prezzo.millesimi }
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
        if (offerta == null) {
            Text("Nessun distributore con ${vista.carburante.etichetta.lowercase()} in quest'area.", style = Testi.Corpo)
            return@Column
        }
        if (selezionato == null) {
            Text(
                "Il più conveniente qui vicino · evidenziato sulla mappa",
                style = Testi.Piccolo.copy(color = Colori.Petrolio, fontWeight = FontWeight.Bold),
            )
        }
        val d = offerta.distributore
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
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BottonePrimario("Naviga", { naviga(context, d, utente.impostazioni.navigazione) }, Modifier.weight(1f), icona = Icone.Naviga)
            BottoneSecondario("Dettagli", { onDistributore(d) }, Modifier.weight(1f))
        }
    }
}

/** Foglio in basso con la colonnina scelta (o la piu vicina), come nel design "Colonnine". */
@Composable
private fun SchedaColonnina(
    vm: GocciaViewModel,
    vista: VistaColonnine?,
    selezionata: String?,
    lontano: Boolean,
    onColonnina: (Colonnina) -> Unit,
) {
    val context = LocalContext.current
    val utente by vm.utente.collectAsStateWithLifecycle()
    val stato by vm.colonnine.collectAsStateWithLifecycle()
    Column(
        Modifier
            .fillMaxWidth()
            .ombra(Forme.Foglio, 8.dp)
            .clip(Forme.Foglio)
            .background(Colori.Superficie)
            .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 40.dp, height = 4.dp).clip(RoundedCornerShape(2.dp)).background(Colori.InterruttoreSpento))
        if (lontano && selezionata == null) {
            Text("Avvicina la mappa per vedere le colonnine", style = Testi.CorpoForte)
            Text("Oppure cerca un comune qui sopra, o tocca il mirino per andare dove sei.", style = Testi.Didascalia.copy(color = Colori.Testo3))
            return@Column
        }
        if (stato.nonDisponibili && stato.tutte.isEmpty()) {
            Text("Colonnine non disponibili", style = Testi.CorpoForte)
            Text("Non riusciamo a scaricare l'elenco: controlla la connessione e riprova.", style = Testi.Didascalia.copy(color = Colori.Testo3))
            return@Column
        }
        val vicine = vista?.zona?.vicine.orEmpty()
        if (vista == null || (vicine.isEmpty() && stato.caricamento)) {
            Text("Carico le colonnine…", style = Testi.Didascalia.copy(color = Colori.Testo3))
            return@Column
        }
        val massima = vicine.mapNotNull { it.colonnina.kw }.maxOrNull()
        Text(
            "${vicine.size} ${if (vicine.size == 1) "colonnina" else "colonnine"} entro ${vista.zona.raggioKm} km" +
                (massima?.let { " · fino a ${Formati.kw(it)}" } ?: ""),
            style = Testi.Didascalia.copy(color = Colori.Testo3, fontWeight = FontWeight.SemiBold),
        )
        val scelta = vicine.firstOrNull { it.colonnina.id == selezionata } ?: vicine.firstOrNull()
        if (scelta == null) {
            Text("Nessuna colonnina compatibile in quest'area: prova a cambiare prese o potenza qui sopra.", style = Testi.Corpo)
            return@Column
        }
        val c = scelta.colonnina
        val usata = vm.tariffaUsata(c)
        val tariffa = usata.euroKwh
        val statoPunti by produceState<StatoColonnina?>(null, c.id) { value = vm.statoColonnina(c) }
        if (selezionata == null) {
            Text(
                "La più vicina · evidenziata sulla mappa",
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
        val auto = utente.autoCorrente?.takeIf { it.alimentazione.elettrica }
        if (auto != null) {
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
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BottonePrimario("Naviga", { naviga(context, c.lat, c.lon, c.titolo, utente.impostazioni.navigazione) }, Modifier.weight(1f), icona = Icone.Naviga)
            BottoneSecondario("Dettagli e costi", { onColonnina(c) }, Modifier.weight(1f))
        }
    }
}
