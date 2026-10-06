package it.goccia.app.ui.mappa

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import it.goccia.app.dati.Coordinate
import it.goccia.app.logica.Formati
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

/** Sotto questo zoom il puntatore copre troppo: si chiede di avvicinare la mappa. */
private const val ZOOM_MINIMO = 10.0

/**
 * Scelta di un punto qualsiasi sulla mappa: il puntatore resta al centro e si sposta la mappa
 * sotto di lui. In basso le coordinate del punto, il comune piu vicino e la conferma.
 * [iniziale] e dove parte la mappa (null: tutta Italia).
 */
@Composable
fun ScegliSullaMappa(
    vm: GocciaViewModel,
    titolo: String,
    iniziale: Coordinate?,
    onConferma: (Coordinate) -> Unit,
    onChiudi: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lat by remember { mutableDoubleStateOf(iniziale?.lat ?: 42.5) }
    var lon by remember { mutableDoubleStateOf(iniziale?.lon ?: 12.5) }
    var zoom by remember { mutableDoubleStateOf(if (iniziale != null) 15.0 else 5.5) }
    // il comune piu vicino si cerca quando la mappa si ferma, non a ogni fotogramma
    var vicino by remember { mutableStateOf(iniziale?.let { vm.comunePiuVicino(it)?.etichetta }) }
    var mappa by remember { mutableStateOf<MapLibreMap?>(null) }
    var cerco by remember { mutableStateOf(false) }

    fun vaiAllaPosizione() {
        scope.launch {
            cerco = true
            val qui = vm.posizioneAttuale()
            cerco = false
            if (qui == null) {
                Toast.makeText(context, "Non riusciamo a trovare la tua posizione. Riprova tra poco.", Toast.LENGTH_LONG).show()
            } else {
                mappa?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(qui.lat, qui.lon), 16.0), 600)
            }
        }
    }
    val richiesta = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { esito ->
        if (esito.values.any { it }) vaiAllaPosizione()
    }

    val vistaMappa = rememberVistaMappa { v ->
        v.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.uiSettings.isAttributionEnabled = false
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), zoom))
            m.setStyle(Style.Builder().fromUri(STILE_MAPPA)) { etichetteInItaliano(it) }
            m.addOnCameraMoveListener {
                val t = m.cameraPosition.target ?: return@addOnCameraMoveListener
                lat = t.latitude
                lon = t.longitude
                zoom = m.cameraPosition.zoom
            }
            m.addOnCameraIdleListener {
                val t = m.cameraPosition.target ?: return@addOnCameraIdleListener
                lat = t.latitude
                lon = t.longitude
                zoom = m.cameraPosition.zoom
                vicino = vm.comunePiuVicino(Coordinate(t.latitude, t.longitude))?.etichetta
            }
            mappa = m
        }
    }

    val pronto = zoom >= ZOOM_MINIMO
    Box(Modifier.fillMaxSize().background(Colori.Sfondo)) {
        AndroidView(factory = { vistaMappa }, modifier = Modifier.fillMaxSize())

        // il puntatore: la punta del segnaposto sta esattamente al centro della mappa
        Box(Modifier.align(Alignment.Center).size(10.dp).clip(CircleShape).background(Colori.Inchiostro.copy(alpha = 0.30f)))
        Icon(
            Icone.Segnaposto,
            "Punto scelto",
            tint = Colori.Petrolio,
            modifier = Modifier.align(Alignment.Center).offset(y = (-21).dp).size(46.dp),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .ombra(RoundedCornerShape(0.dp), 4.dp)
                .background(Colori.Sfondo.copy(alpha = 0.96f))
                .statusBarsPadding()
                .padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BottoneIcona(Icone.Indietro, "Indietro", onChiudi)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(titolo, style = Testi.Titolo)
                Text("Sposta la mappa per mettere il segnaposto sul punto", style = Testi.Didascalia.copy(color = Colori.Testo2))
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.Bottom) {
                Text(
                    "© OpenStreetMap · OpenFreeMap",
                    style = Testi.Minimo.copy(color = Colori.Testo2, fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Colori.Superficie.copy(alpha = 0.8f)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Box(Modifier.weight(1f))
                BottoneIcona(
                    Icone.Mirino,
                    if (cerco) "Cerco la tua posizione" else "Vai alla tua posizione",
                    {
                        if (vm.haPermessoPosizione()) vaiAllaPosizione()
                        else richiesta.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    },
                    Modifier.ombra(CircleShape, 4.dp),
                    colore = Colori.Petrolio,
                    sfondo = Colori.Superficie,
                )
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .ombra(Forme.Foglio, 8.dp)
                    .clip(Forme.Foglio)
                    .background(Colori.Superficie)
                    .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(if (pronto) Formati.coordinate(lat, lon) else "Avvicina la mappa", style = Testi.Sottosezione)
                Text(
                    if (pronto) vicino?.let { "Vicino a $it" } ?: "Coordinate del punto scelto" else "Da qui il segnaposto copre una zona troppo grande.",
                    style = Testi.Didascalia.copy(color = Colori.Testo3),
                )
                BottonePrimario(
                    "Conferma il punto",
                    onClick = { onConferma(Coordinate(lat, lon)) },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                    icona = Icone.Spunta,
                    abilitato = pronto,
                    altezza = 54.dp,
                    forma = RoundedCornerShape(16.dp),
                )
            }
        }
    }
}
