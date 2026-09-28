package it.goccia.app.ui.mappa

import android.graphics.RectF
import android.view.Gravity
import androidx.compose.foundation.background
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
import androidx.compose.runtime.mutableStateOf
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
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.Distributore
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Offerta
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.VistaZona
import it.goccia.app.ui.componenti.BadgeConvenienza
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.BottoneSecondario
import it.goccia.app.ui.componenti.CampoRicerca
import it.goccia.app.ui.componenti.ChipScelta
import it.goccia.app.ui.componenti.LogoBandiera
import it.goccia.app.ui.componenti.colorePin
import it.goccia.app.ui.componenti.metaOfferta
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.naviga
import it.goccia.app.ui.stati.SuggerimentiComuni
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
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
fun SchermataMappa(vm: GocciaViewModel, onDistributore: (Distributore) -> Unit, onLista: () -> Unit) {
    LaunchedEffect(Unit) { vm.avvia() }
    val context = LocalContext.current
    val densita = LocalDensity.current
    val dati by vm.dati.collectAsStateWithLifecycle()
    val utente by vm.utente.collectAsStateWithLifecycle()
    val statoVista by vm.vistaMappa.collectAsStateWithLifecycle()
    val vista = statoVista

    var selezionato by rememberSaveable { mutableStateOf<Long?>(null) }
    var testo by rememberSaveable { mutableStateOf("") }
    var mappa by remember { mutableStateOf<MapLibreMap?>(null) }
    var stile by remember { mutableStateOf<Style?>(null) }
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
                vm.mappaSpostata(Coordinate(bersaglio.latitude, bersaglio.longitude), m.cameraPosition.zoom)
            }
            m.addOnMapClickListener { punto ->
                val schermo = m.projection.toScreenLocation(punto)
                val raggio = with(densita) { 14.dp.toPx() }
                val area = RectF(schermo.x - raggio, schermo.y - raggio * 2, schermo.x + raggio, schermo.y + raggio / 2)
                val trovato = m.queryRenderedFeatures(area, STRATO_PREZZI).firstOrNull()
                val id = trovato?.getStringProperty("id")?.toLongOrNull()
                if (id != null) {
                    selezionato = id
                    true
                } else {
                    false
                }
            }
            mappa = m
        }
    }

    // la posizione (o il comune cercato) cambia: spostiamo la mappa
    val centro = dati.centro
    LaunchedEffect(mappa, centro) {
        val m = mappa ?: return@LaunchedEffect
        val c = centro ?: return@LaunchedEffect
        m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(c.coordinate.lat, c.coordinate.lon), 13.0), 600)
    }
    LaunchedEffect(stile, centro) {
        val s = stile ?: return@LaunchedEffect
        val c = centro?.coordinate
        val punti = if (c != null) listOf(Feature.fromGeometry(Point.fromLngLat(c.lon, c.lat))) else emptyList()
        s.getSourceAs<GeoJsonSource>(SORGENTE_IO)?.setGeoJson(FeatureCollection.fromFeatures(punti))
    }
    // prezzi sulla mappa
    LaunchedEffect(stile, vista, selezionato) {
        val s = stile ?: return@LaunchedEffect
        val v = vista ?: return@LaunchedEffect
        val offerte = v.zona.tutte.sortedBy { it.distanzaKm }.take(MASSIMO_PIN)
        val elementi = offerte.map { o ->
            val sel = o.distributore.id == selezionato
            val prezzo = Formati.prezzo(o.prezzo.millesimi)
            val colore = if (sel) Colori.Inchiostro else if (o.vecchio) Colori.Linea else colorePin(o.tono)
            val chiave = "p-$prezzo-${colore.toArgb()}-$sel"
            if (immaginiCaricate.add(chiave)) s.addImage(chiave, pin.disegna(prezzo, colore.toArgb(), sel))
            Feature.fromGeometry(Point.fromLngLat(o.distributore.lon, o.distributore.lat)).apply {
                addStringProperty("id", o.distributore.id.toString())
                addStringProperty("icona", chiave)
                addNumberProperty("ordine", if (sel) -1 else o.prezzo.millesimi)
            }
        }
        s.getSourceAs<GeoJsonSource>(SORGENTE_PREZZI)?.setGeoJson(FeatureCollection.fromFeatures(elementi))
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
                BottoneIcona(
                    Icone.Lista, "Vedi come lista", onLista,
                    colore = Color.White, sfondo = Colori.Inchiostro, dimensione = 52.dp, forma = RoundedCornerShape(16.dp),
                )
            }
            if (testo.isNotBlank()) {
                SuggerimentiComuni(
                    vm.cercaComuni(testo),
                    onScelto = {
                        testo = ""
                        selezionato = null
                        vm.centraSu(it)
                    },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp),
                )
            }
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Carburante.entries.forEach { c ->
                    ChipScelta(c.etichetta, c == utente.carburante, onClick = { vm.scegliCarburante(c) }, altezza = 38.dp)
                }
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    "© OpenStreetMap · OpenFreeMap",
                    style = Testi.Minimo.copy(color = Colori.Testo2, fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.8f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Box(Modifier.weight(1f))
                BottoneIcona(
                    Icone.Mirino, "Centra sulla mia posizione", {
                        selezionato = null
                        vm.usaPosizione()
                    },
                    Modifier.ombra(CircleShape, 4.dp),
                    colore = Colori.Petrolio, sfondo = Colori.Superficie,
                )
            }
            SchedaSelezione(vm, vista, selezionato, onDistributore, onLista)
        }
    }
}

@Composable
private fun SchedaSelezione(
    vm: GocciaViewModel,
    vista: VistaZona?,
    selezionato: Long?,
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
