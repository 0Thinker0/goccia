package it.goccia.app.ui.profilo

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.Luogo
import it.goccia.app.dati.TipoLuogo
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.BottoneSecondario
import it.goccia.app.ui.componenti.BottoneTesto
import it.goccia.app.ui.componenti.CampoRicerca
import it.goccia.app.ui.componenti.CampoTesto
import it.goccia.app.ui.componenti.IntestazioneFoglio
import it.goccia.app.ui.componenti.Opzione
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.mappa.STILE_MAPPA
import it.goccia.app.ui.mappa.rememberVistaMappa
import it.goccia.app.ui.stati.SuggerimentiComuni
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style

@Composable
fun SchermataLuogo(vm: GocciaViewModel, id: String?, tipo: String?, onChiudi: () -> Unit) {
    LaunchedEffect(Unit) { vm.caricaComuni() }
    val scope = rememberCoroutineScope()
    val utente by vm.utente.collectAsStateWithLifecycle()
    val dati by vm.dati.collectAsStateWithLifecycle()
    val esistente = remember(id) { id?.let { i -> utente.luoghi.firstOrNull { it.id == i } } }
    val tipoIniziale = esistente?.tipo ?: TipoLuogo.entries.firstOrNull { it.name == tipo } ?: TipoLuogo.ALTRO

    var nome by rememberSaveable { mutableStateOf(esistente?.nome ?: if (tipoIniziale == TipoLuogo.ALTRO) "" else tipoIniziale.etichetta) }
    var tipoLuogo by rememberSaveable { mutableStateOf(tipoIniziale) }
    val partenza = esistente?.coordinate ?: dati.centro?.coordinate ?: utente.ultimaPosizione?.coordinate
    var lat by rememberSaveable { mutableDoubleStateOf(partenza?.lat ?: 42.5) }
    var lon by rememberSaveable { mutableDoubleStateOf(partenza?.lon ?: 12.5) }
    var scelto by rememberSaveable { mutableStateOf(partenza != null) }
    var testo by rememberSaveable { mutableStateOf("") }
    var mappa by remember { mutableStateOf<MapLibreMap?>(null) }
    var cerco by remember { mutableStateOf(false) }

    fun sposta(c: Coordinate, zoom: Double = 15.0) {
        lat = c.lat
        lon = c.lon
        scelto = true
        mappa?.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(c.lat, c.lon), zoom), 500)
    }

    val usaPosizione: () -> Unit = {
        scope.launch {
            cerco = true
            vm.posizioneAttuale()?.let { sposta(it, 16.0) }
            cerco = false
        }
    }
    val richiesta = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { esito ->
        if (esito.values.any { it }) usaPosizione()
    }

    val vistaMappa = rememberVistaMappa { v ->
        v.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lon), if (scelto) 15.0 else 5.0))
            m.setStyle(Style.Builder().fromUri(STILE_MAPPA))
            m.addOnCameraIdleListener {
                val t = m.cameraPosition.target ?: return@addOnCameraIdleListener
                if (m.cameraPosition.zoom >= 11.0) {
                    lat = t.latitude
                    lon = t.longitude
                    scelto = true
                }
            }
            mappa = m
        }
    }

    val punto = Coordinate(lat, lon)
    val vicino = if (scelto) vm.comunePiuVicino(punto) else null
    val descrizione = vicino?.let { "vicino a ${it.etichetta}" }.orEmpty()

    Column(Modifier.fillMaxSize().background(Colori.Superficie).statusBarsPadding().imePadding()) {
        Column(
            Modifier.weight(1f).padding(start = 20.dp, end = 20.dp, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IntestazioneFoglio(if (esistente == null) "Nuovo luogo" else "Modifica luogo", onChiudi)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TipoLuogo.entries.forEach { t ->
                    Opzione(t.etichetta, tipoLuogo == t, {
                        if (nome.isBlank() || nome == tipoLuogo.etichetta) nome = if (t == TipoLuogo.ALTRO) "" else t.etichetta
                        tipoLuogo = t
                    }, Modifier.weight(1f))
                }
            }
            CampoTesto(nome, { nome = it.take(24) }, etichetta = "Nome", segnaposto = "Es. Palestra, Nonna")
            Box(Modifier.weight(1f).fillMaxWidth()) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CampoRicerca(testo, { testo = it }, Modifier.fillMaxWidth(), segnaposto = "Cerca un comune")
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Colori.Grigio),
                    ) {
                        AndroidView(factory = { vistaMappa }, modifier = Modifier.fillMaxSize())
                        Icon(
                            Icone.Segnaposto,
                            "Punto scelto",
                            tint = Colori.Petrolio,
                            modifier = Modifier.align(Alignment.Center).offset(y = (-16).dp).size(36.dp),
                        )
                        BottoneSecondario(
                            if (cerco) "Cerco…" else "La mia posizione",
                            {
                                if (vm.haPermessoPosizione()) usaPosizione()
                                else richiesta.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            },
                            Modifier.align(Alignment.BottomEnd).padding(10.dp),
                            icona = Icone.Mirino,
                            coloreIcona = Colori.Petrolio,
                            altezza = 40.dp,
                        )
                    }
                    Text(
                        if (scelto) "Sposta la mappa per mettere il segnaposto sul punto giusto${if (descrizione.isNotBlank()) " · $descrizione" else ""}"
                        else "Cerca un comune o usa la tua posizione, poi sposta la mappa.",
                        style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
                    )
                }
                if (testo.isNotBlank()) {
                    SuggerimentiComuni(
                        vm.cercaComuni(testo),
                        onScelto = {
                            testo = ""
                            sposta(it.coordinate, 13.0)
                        },
                        modifier = Modifier.padding(top = 60.dp),
                    )
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (esistente != null) {
                BottoneTesto("Elimina", {
                    vm.eliminaLuogo(esistente.id)
                    onChiudi()
                }, colore = Colori.Rosso)
            }
            BottonePrimario(
                "Salva luogo",
                onClick = {
                    vm.salvaLuogo(
                        Luogo(
                            id = esistente?.id ?: vm.nuovoId(),
                            nome = nome.trim().ifBlank { tipoLuogo.etichetta },
                            tipo = tipoLuogo,
                            lat = lat,
                            lon = lon,
                            descrizione = descrizione,
                        ),
                    )
                    onChiudi()
                },
                modifier = Modifier.weight(1f),
                abilitato = scelto,
                altezza = 54.dp,
                forma = RoundedCornerShape(16.dp),
            )
        }
    }
}
