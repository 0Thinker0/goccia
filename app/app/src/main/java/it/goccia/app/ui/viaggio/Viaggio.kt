package it.goccia.app.ui.viaggio

import android.content.Context
import android.util.Log
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Colonnina
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.DatiUtente
import it.goccia.app.dati.Distributore
import it.goccia.app.dati.TipoLuogo
import it.goccia.app.logica.Consiglio
import it.goccia.app.logica.Formati
import it.goccia.app.logica.LungoIlPercorso
import it.goccia.app.logica.PuntoPercorso
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.StatoViaggio
import it.goccia.app.ui.Tappa
import it.goccia.app.ui.TipoCentro
import it.goccia.app.ui.apriLink
import it.goccia.app.ui.componenti.Badge
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.BottoneSecondario
import it.goccia.app.ui.componenti.CampoRicerca
import it.goccia.app.ui.componenti.ChipScelta
import it.goccia.app.ui.componenti.LogoBandiera
import it.goccia.app.ui.componenti.Opzione
import it.goccia.app.ui.componenti.Riquadro
import it.goccia.app.ui.componenti.Scheda
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.mappa.PinPrezzo
import it.goccia.app.ui.mappa.STILE_MAPPA
import it.goccia.app.ui.mappa.rememberVistaMappa
import it.goccia.app.ui.stati.SuggerimentiComuni
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra
import kotlin.math.abs
import kotlin.math.roundToInt
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point

private val LIVELLI = listOf("Riserva" to 0.1, "1/4" to 0.25, "1/2" to 0.5, "3/4" to 0.75, "Pieno" to 1.0)
private val DEVIAZIONI = listOf(3, 5, 10)
private val LIVELLI_EV = listOf(0.2, 0.4, 0.6, 0.8, 1.0)
private val ARRIVI_EV = listOf(0.1, 0.2, 0.3)

private enum class Campo { PARTENZA, ARRIVO }

@Composable
fun SchermataViaggio(
    vm: GocciaViewModel,
    onDistributore: (Distributore) -> Unit,
    onAuto: () -> Unit,
    onColonnina: (Colonnina) -> Unit,
    onAutostrada: () -> Unit,
) {
    LaunchedEffect(Unit) {
        vm.avvia()
        vm.caricaComuni()
    }
    val stato by vm.viaggio.collectAsStateWithLifecycle()
    when (val s = stato) {
        is StatoViaggio.Pronto -> {
            BackHandler { vm.chiudiViaggio() }
            Risultato(vm, s, onDistributore, onAutostrada)
        }
        is StatoViaggio.ProntoElettrico -> {
            BackHandler { vm.chiudiViaggio() }
            RisultatoElettrico(vm, s, onColonnina, onAutostrada)
        }
        else -> Pianifica(vm, s, onAuto)
    }
}

// ------------------------------------------------------------------ pianificazione

@Composable
private fun Pianifica(vm: GocciaViewModel, stato: StatoViaggio, onAuto: () -> Unit) {
    val utente by vm.utente.collectAsStateWithLifecycle()
    val dati by vm.dati.collectAsStateWithLifecycle()

    val posizione = dati.centro?.takeIf { it.tipo == TipoCentro.POSIZIONE }?.let { Tappa("La tua posizione", it.coordinate, posizioneAttuale = true) }
    val precedente = vm.ultimaRichiesta
    var partenza by remember { mutableStateOf(precedente?.partenza) }
    var arrivo by remember { mutableStateOf(precedente?.arrivo) }
    val partenzaEffettiva = partenza ?: posizione ?: utente.casa?.let { Tappa(it.nome, it.coordinate) }
    var cercaPer by remember { mutableStateOf<Campo?>(null) }

    val auto = utente.autoCorrente?.takeIf { !it.alimentazione.elettrica }
    val stimato = vm.serbatoio(utente)?.livello
    var livello by remember {
        val dalPrecedente = precedente?.takeIf { !it.elettrica }?.let { r -> LIVELLI.indexOfFirst { abs(it.second - r.livello) < 0.01 } } ?: -1
        val dallaStima = LIVELLI.indexOfFirst { stimato != null && it.second >= stimato - 0.125 }
        mutableIntStateOf(if (dalPrecedente >= 0) dalPrecedente else if (dallaStima >= 0) dallaStima else 1)
    }
    var deviazione by remember { mutableIntStateOf(precedente?.let { DEVIAZIONI.indexOf(it.deviazioneMin) }?.takeIf { it >= 0 } ?: 1) }
    var pienoCompleto by remember { mutableStateOf(precedente?.pienoCompleto ?: true) }

    // auto elettrica: batteria in partenza e livello minimo all'arrivo
    val elettrica = utente.autoCorrente?.takeIf { it.alimentazione.elettrica }
    // si riparte dall'ultimo viaggio elettrico, altrimenti dal livello stimato in Home
    val precedenteEv = precedente?.takeIf { it.elettrica }
    var batteria by remember {
        val dalPrecedente = precedenteEv?.let { r -> LIVELLI_EV.indexOfFirst { abs(it - r.livello) < 0.01 } } ?: -1
        val dallaStima = stimato?.let { st -> LIVELLI_EV.indices.minByOrNull { abs(LIVELLI_EV[it] - st - 0.001) } } ?: -1
        mutableIntStateOf(
            when {
                dalPrecedente >= 0 -> dalPrecedente
                dallaStima >= 0 -> dallaStima
                else -> LIVELLI_EV.indexOf(0.8)
            },
        )
    }
    var arrivoMinimo by remember {
        mutableIntStateOf(precedenteEv?.let { r -> ARRIVI_EV.indexOfFirst { abs(it - r.arrivoMinimo) < 0.01 } }?.takeIf { it >= 0 } ?: 1)
    }

    if (cercaPer != null) {
        BackHandler { cercaPer = null }
        Scelta(vm, utente, cercaPer == Campo.PARTENZA, posizione, onScelta = { t ->
            if (cercaPer == Campo.PARTENZA) partenza = t else arrivo = t
            cercaPer = null
        }, onChiudi = { cercaPer = null })
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Viaggio", style = Testi.TitoloSchermata)
            Text(
                if (elettrica != null) "Ti diciamo dove e quanto ricaricare lungo il percorso."
                else "Ti diciamo dove e quando fare il pieno lungo il percorso.",
                style = Testi.Testo14.copy(color = Colori.Testo2),
            )
        }

        // partenza e arrivo
        Row(
            Modifier.fillMaxWidth().ombra(Forme.CardGrande).clip(Forme.CardGrande).background(Colori.Superficie).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                RigaTappa("Partenza", partenzaEffettiva?.nome ?: "Scegli da dove parti", Colori.Petrolio, pieno = false) { cercaPer = Campo.PARTENZA }
                Box(Modifier.padding(start = 5.dp).width(2.dp).height(14.dp).background(Colori.Bordo))
                RigaTappa("Destinazione", arrivo?.nome ?: "Dove vai?", Colori.Notifica, pieno = true) { cercaPer = Campo.ARRIVO }
            }
            BottoneIcona(Icone.Ordina, "Inverti partenza e destinazione", {
                val vecchia = partenzaEffettiva
                partenza = arrivo
                arrivo = vecchia
            }, sfondo = Colori.Grigio, dimensione = 44.dp, dimensioneIcona = 20.dp)
        }

        // auto e livello
        if (elettrica != null) {
            Scheda(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Colori.PetrolioChiaro), contentAlignment = Alignment.Center) {
                        Icon(Icone.Fulmine, null, tint = Colori.Petrolio, modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(elettrica.nome, style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold))
                        Text(
                            "Elettrica · ${Formati.numero(elettrica.capienza, 0)} kWh · ${Formati.numero(elettrica.consumo, 1)} kWh/100 km",
                            style = Testi.Piccolo.copy(color = Colori.Testo2),
                        )
                    }
                    Text("Cambia", style = Testi.DidascaliaForte.copy(color = Colori.Petrolio), modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAuto).padding(8.dp))
                }
                Text("Batteria in partenza", style = Testi.DidascaliaForte.copy(color = Colori.TestoChip))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LIVELLI_EV.forEachIndexed { i, v ->
                        Opzione(Formati.percento(v), batteria == i, { batteria = i }, Modifier.weight(1f), altezza = 38.dp, margine = 2.dp)
                    }
                }
                Text("Arrivo con almeno", style = Testi.DidascaliaForte.copy(color = Colori.TestoChip))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ARRIVI_EV.forEachIndexed { i, v ->
                        Opzione(Formati.percento(v), arrivoMinimo == i, { arrivoMinimo = i }, Modifier.weight(1f), altezza = 38.dp)
                    }
                }
                val kwh = elettrica.capienza * LIVELLI_EV[batteria]
                val autonomia = ((kwh / elettrica.consumo * 100) / 10).roundToInt() * 10
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("Circa ${Formati.numero(kwh, 0)} kWh · autonomia ~$autonomia km.") }
                        if (stimato != null) append(" Dalla Home stimiamo il ${Formati.percento(stimato)}.")
                    },
                    style = Testi.Didascalia.copy(color = Colori.TestoChip),
                )
            }
        } else Scheda(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Colori.PetrolioChiaro), contentAlignment = Alignment.Center) {
                    Icon(Icone.Auto, null, tint = Colori.Petrolio, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(auto?.nome ?: "Auto tipo", style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold))
                    Text(
                        auto?.let { "${utente.carburante.etichetta} · ${Formati.numero(it.capienza, 0)} ${it.alimentazione.unitaCapienza} · ${Formati.numero(it.consumo, 1)} ${it.alimentazione.unitaConsumo}" }
                            ?: "${utente.carburante.etichetta} · 50 l · 6 l/100 km (aggiungi la tua auto per calcoli precisi)",
                        style = Testi.Piccolo.copy(color = Colori.Testo2),
                    )
                }
                Text("Cambia", style = Testi.DidascaliaForte.copy(color = Colori.Petrolio), modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAuto).padding(8.dp))
            }
            Text("Quanto carburante hai?", style = Testi.DidascaliaForte.copy(color = Colori.TestoChip))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                LIVELLI.forEachIndexed { i, (etichetta, _) ->
                    Opzione(etichetta, livello == i, { livello = i }, Modifier.weight(1f), altezza = 38.dp)
                }
            }
            val capienza = auto?.capienza ?: 50.0
            val consumo = auto?.consumo ?: 6.0
            val litri = capienza * LIVELLI[livello].second
            val autonomia = (litri / consumo * 100 / 10).roundToInt() * 10
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("Circa ${Formati.numero(litri, 0)} ${auto?.alimentazione?.unitaCapienza ?: "l"} · autonomia ~$autonomia km.") }
                    if (stimato != null) append(" Dalla Home stimiamo ${Consiglio.quarti(stimato)}.")
                },
                style = Testi.Didascalia.copy(color = Colori.TestoChip),
            )
        }

        // opzioni
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ChipScelta("Deviazione max ${DEVIAZIONI[deviazione]} min", false, onClick = { deviazione = (deviazione + 1) % DEVIAZIONI.size }, icona = Icone.Orologio)
            if (elettrica == null) {
                ChipScelta(if (pienoCompleto) "Pieno completo" else "Solo quanto basta", false, onClick = { pienoCompleto = !pienoCompleto }, icona = Icone.Pompa)
            }
        }

        if (stato is StatoViaggio.Errore) {
            Riquadro(Modifier.fillMaxWidth(), sfondo = Colori.RossoChiaro, icona = Icone.Attenzione, coloreIcona = Colori.Rosso) {
                Text(stato.messaggio, style = Testi.Didascalia.copy(color = Colori.Rosso))
            }
        }
        if (stato is StatoViaggio.Calcolo) {
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = Colori.Petrolio, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                Text(stato.fase, style = Testi.CorpoForte)
            }
        } else {
            val pronto = partenzaEffettiva != null && arrivo != null
            BottonePrimario(
                "Calcola soste",
                onClick = {
                    val da = partenzaEffettiva ?: return@BottonePrimario
                    val a = arrivo ?: return@BottonePrimario
                    if (elettrica != null) {
                        vm.calcolaViaggio(da, a, LIVELLI_EV[batteria], DEVIAZIONI[deviazione], pienoCompleto, ARRIVI_EV[arrivoMinimo])
                    } else {
                        vm.calcolaViaggio(da, a, LIVELLI[livello].second, DEVIAZIONI[deviazione], pienoCompleto)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icona = Icone.Percorso,
                abilitato = pronto,
                altezza = 56.dp,
                forma = RoundedCornerShape(16.dp),
            )
        }

        if (utente.viaggiRecenti.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Recenti", style = Testi.DidascaliaForte.copy(color = Colori.Testo3))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    utente.viaggiRecenti.forEach { r ->
                        ChipScelta("${r.partenza} → ${r.arrivo}", false, onClick = {
                            partenza = Tappa(r.partenza, Coordinate(r.partenzaLat, r.partenzaLon), posizioneAttuale = r.partenza == "La tua posizione")
                            arrivo = Tappa(r.arrivo, Coordinate(r.arrivoLat, r.arrivoLon))
                        }, icona = Icone.Percorso, altezza = 40.dp)
                    }
                }
            }
        }
        Text(
            if (elettrica != null) "Percorsi calcolati con OSRM sul servizio di FOSSGIS; percorso e colonnine da OpenStreetMap (ODbL)."
            else "Percorsi calcolati con OSRM sul servizio di FOSSGIS, con i dati di OpenStreetMap. I prezzi sono quelli comunicati al Ministero.",
            style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun RigaTappa(etichetta: String, valore: String, colore: Color, pieno: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(if (pieno) colore else Colori.Superficie)
                .border(3.dp, colore, CircleShape),
        )
        Column {
            Text(etichetta, style = Testi.Piccolo.copy(color = Colori.Testo3))
            Text(valore, style = Testi.CorpoForte, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Scelta di partenza o arrivo: posizione, luoghi salvati o un comune. */
@Composable
private fun Scelta(
    vm: GocciaViewModel,
    utente: DatiUtente,
    perPartenza: Boolean,
    posizione: Tappa?,
    onScelta: (Tappa) -> Unit,
    onChiudi: () -> Unit,
) {
    var testo by remember { mutableStateOf("") }
    // si apre gia pronto per scrivere
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            BottoneIcona(Icone.Indietro, "Indietro", onChiudi)
            Text(if (perPartenza) "Da dove parti?" else "Dove vai?", style = Testi.Titolo)
        }
        CampoRicerca(testo, { testo = it }, Modifier.fillMaxWidth(), segnaposto = "Cerca un comune", focus = focus)
        if (testo.isBlank()) {
            if (perPartenza && posizione != null) {
                ChipScelta("La tua posizione", false, onClick = { onScelta(posizione) }, icona = Icone.Mirino, altezza = 44.dp)
            }
            utente.luoghi.forEach { l ->
                ChipScelta(
                    l.nome,
                    false,
                    onClick = { onScelta(Tappa(l.nome, l.coordinate)) },
                    icona = if (l.tipo == TipoLuogo.LAVORO) Icone.Lavoro else Icone.Casa,
                    altezza = 44.dp,
                )
            }
            Text("Scrivi il nome di un comune: basta anche solo l'inizio.", style = Testi.Didascalia.copy(color = Colori.Testo3))
        } else {
            SuggerimentiComuni(vm.cercaComuni(testo), onScelto = { onScelta(Tappa(it.nome, it.coordinate)) })
        }
    }
}

// ------------------------------------------------------------------ risultato

private fun durata(minuti: Int): String = if (minuti < 60) "$minuti min" else "${minuti / 60} h ${minuti % 60} min"

@Composable
private fun Risultato(vm: GocciaViewModel, s: StatoViaggio.Pronto, onDistributore: (Distributore) -> Unit, onAutostrada: () -> Unit) {
    val context = LocalContext.current
    val avviaGuida = rememberAvvioGuida(vm, onAutostrada)
    val vista by vm.vista.collectAsStateWithLifecycle()
    val piano = s.piano
    val carburante = s.carburante
    val autostrada = s.lungo.any { it.distributore.autostradale }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(0.42f)) {
            MappaViaggio(s.campioni, pinCarburante(s))
            BottoneIcona(
                Icone.Indietro, "Indietro", { vm.chiudiViaggio() },
                Modifier.statusBarsPadding().padding(12.dp).ombra(CircleShape, 4.dp),
                sfondo = Colori.Superficie,
            )
        }
        Column(
            Modifier
                .weight(0.58f)
                .fillMaxWidth()
                .ombra(Forme.Foglio, 8.dp)
                .clip(Forme.Foglio)
                .background(Colori.Superficie)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${s.partenza.nome} → ${s.arrivo.nome}", style = Testi.Titolo, maxLines = 2)
                    Text("${Formati.numero(s.percorso.distanzaKm, 0)} km · ${durata(s.percorso.durataMin)}", style = Testi.Didascalia.copy(color = Colori.Testo2, fontWeight = FontWeight.SemiBold))
                }
                if (autostrada) {
                    // come nel design: la targhetta porta alla modalita autostrada
                    Badge(
                        "Autostrada",
                        Colori.GrigioBadge,
                        Colori.TestoChip,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable(onClick = avviaGuida),
                        icona = Icone.Autostrada,
                    )
                }
            }

            if (piano.senzaSoste) {
                Riquadro(Modifier.fillMaxWidth(), sfondo = Colori.VerdeChiaro, icona = Icone.Spunta, coloreIcona = Colori.VerdeTesto) {
                    Text(
                        "Arrivi senza fermarti: all'arrivo ti restano circa ${piano.margineArrivoKm} km di autonomia.",
                        style = Testi.Testo14.copy(color = Colori.VerdeScuro, fontWeight = FontWeight.SemiBold),
                    )
                }
                piano.migliore?.let { m ->
                    Text("Se vuoi fare il pieno lungo la strada", style = Testi.DidascaliaForte.copy(color = Colori.Testo3))
                    CardSosta(m, carburante, null, piano.mediaAutostrada, onDistributore)
                }
            } else {
                piano.soste.forEachIndexed { i, sosta ->
                    Text(
                        if (piano.soste.size == 1) "Sosta consigliata" else "${i + 1}ª sosta",
                        style = Testi.DidascaliaForte.copy(color = Colori.Petrolio, fontWeight = FontWeight.ExtraBold),
                    )
                    CardSosta(sosta.punto, carburante, sosta.quantita, piano.mediaAutostrada, onDistributore)
                }
                val livello = LIVELLI.firstOrNull { abs(it.second - s.livello) < 0.01 }?.first ?: Consiglio.quarti(s.livello)
                val frasi = mutableListOf("Con ${if (livello == "Pieno") "il pieno" else if (livello == "Riserva") "la riserva" else "$livello di serbatoio"} arrivi fino al km ${piano.kmLimite.coerceAtLeast(0.0).roundToInt()}.")
                val prima = piano.soste.first().punto
                val qui = vista?.zona?.media
                if (qui != null) {
                    val diff = ((qui - prima.prezzo.millesimi) / 10.0).roundToInt()
                    frasi += when {
                        diff >= 2 -> "Puoi aspettare: qui il ${carburante.etichetta.lowercase()} costa in media $diff cent in più della sosta consigliata."
                        diff <= -2 -> "Qui il ${carburante.etichetta.lowercase()} costa ${-diff} cent in meno: se puoi, fai il pieno prima di partire."
                        else -> "Qui il prezzo è simile a quello della sosta consigliata."
                    }
                }
                Riquadro(Modifier.fillMaxWidth()) { Text(frasi.joinToString(" "), style = Testi.Didascalia.copy(color = Color(0xFF1F3A4A))) }
            }

            if (piano.alternative.isNotEmpty()) {
                Text("Alternative sul percorso", style = Testi.DidascaliaForte.copy(color = Colori.Testo3))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Colori.Bordo, RoundedCornerShape(16.dp))) {
                    val riferimento = piano.soste.firstOrNull()?.punto?.prezzo?.millesimi ?: piano.migliore?.prezzo?.millesimi
                    piano.alternative.forEachIndexed { i, a ->
                        RigaAlternativa(a, riferimento) { onDistributore(a.distributore) }
                        if (i < piano.alternative.lastIndex) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Colori.Divisore))
                    }
                }
            }

            if (s.lungo.isEmpty()) {
                Text("Non abbiamo trovato distributori con prezzi recenti lungo questo percorso.", style = Testi.Didascalia.copy(color = Colori.Testo2))
            }

            CardModalitaAutostrada(elettrica = false, onAvvia = avviaGuida)

            val tappe = piano.soste.map { it.punto.distributore }.ifEmpty { listOfNotNull(piano.migliore?.distributore) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                BottonePrimario(
                    "Avvia con Google Maps",
                    { avviaGoogleMaps(context, s.partenza.coordinate, s.arrivo.coordinate, if (piano.senzaSoste) emptyList() else tappe) },
                    Modifier.weight(1f),
                    icona = Icone.Naviga,
                    altezza = 52.dp,
                )
                val primaTappa = tappe.firstOrNull()
                if (primaTappa != null && !piano.senzaSoste) {
                    BottoneSecondario("Waze", { apriLink(context, "https://waze.com/ul?ll=${primaTappa.lat},${primaTappa.lon}&navigate=yes") }, altezza = 52.dp)
                }
            }
            Text(
                "Il percorso e le soste sono una stima: controlla sempre il livello reale del serbatoio.",
                style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
            )
        }
    }
}

private fun avviaGoogleMaps(context: Context, da: Coordinate, a: Coordinate, tappe: List<Distributore>) {
    val url = StringBuilder("https://www.google.com/maps/dir/?api=1&travelmode=driving")
        .append("&origin=${da.lat},${da.lon}")
        .append("&destination=${a.lat},${a.lon}")
    if (tappe.isNotEmpty()) url.append("&waypoints=").append(Uri.encode(tappe.joinToString("|") { "${it.lat},${it.lon}" }))
    apriLink(context, url.toString())
}

@Composable
private fun CardSosta(
    p: LungoIlPercorso,
    carburante: Carburante,
    quantita: Double?,
    mediaAutostrada: Int?,
    onDistributore: (Distributore) -> Unit,
) {
    val d = p.distributore
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Colori.PetrolioTenue)
            .border(1.5.dp, Colori.Petrolio, RoundedCornerShape(18.dp))
            .clickable { onDistributore(d) }
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            LogoBandiera(d.bandiera, d.pompaBianca)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(d.titolo, style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                val dove = when {
                    d.autostradale -> "area di servizio"
                    p.deviazioneMin == 0 -> "sulla strada"
                    else -> "deviazione +${p.deviazioneMin} min"
                }
                Text("al km ${p.km.roundToInt()} · $dove · ${d.comune}", style = Testi.Didascalia.copy(color = Colori.Testo2), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(Formati.prezzo(p.prezzo.millesimi), style = Testi.Prezzo)
                Text(carburante.unitaCon(p.self), style = Testi.Piccolo.copy(color = Colori.Testo3))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (mediaAutostrada != null && !d.autostradale) {
                val cent = ((mediaAutostrada - p.prezzo.millesimi) / 10.0).roundToInt()
                if (cent >= 2) Badge("−$cent cent vs autostrada", Colori.VerdeChiaro, Colori.VerdeTesto)
            }
            if (quantita != null) {
                val risparmio = mediaAutostrada?.takeIf { !d.autostradale }?.let { (it - p.prezzo.millesimi) / 1000.0 * quantita }
                Badge(
                    if (risparmio != null && risparmio >= 1) "Risparmi ~${Formati.euro(risparmio)}"
                    else "~${Formati.numero(quantita, 0)} ${if (carburante == Carburante.METANO) "kg" else "l"} · ${Formati.euro(quantita * p.prezzo.euro)}",
                    Colori.Superficie,
                    Colori.TestoChip,
                )
            }
        }
    }
}

@Composable
private fun RigaAlternativa(a: LungoIlPercorso, riferimento: Int?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(a.distributore.titolo, style = Testi.Chip.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            val extra = when {
                a.distributore.autostradale -> "area di servizio"
                a.deviazioneMin == 0 -> "sulla strada"
                else -> "+${a.deviazioneMin} min"
            }
            Text("km ${a.km.roundToInt()} · $extra", style = Testi.Piccolo.copy(color = Colori.Testo3))
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(Formati.prezzo(a.prezzo.millesimi), style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"))
            if (riferimento != null) {
                val cent = ((a.prezzo.millesimi - riferimento) / 10.0).roundToInt()
                if (cent != 0) {
                    Text(
                        if (cent > 0) "+$cent cent" else "−${-cent} cent",
                        style = Testi.Minimo.copy(color = if (cent > 0) Colori.Rosso else Colori.VerdeTesto),
                    )
                }
            }
        }
    }
}

/**
 * Un segnaposto sulla mappa del viaggio: prezzo di un distributore o potenza di una colonnina
 * (testo vuoto: solo il fulmine). [ordine] piu basso = piu importante, disegnato sopra gli altri.
 */
data class PinViaggio(
    val lat: Double,
    val lon: Double,
    val testo: String,
    val colore: Int,
    val scelto: Boolean,
    val ordine: Double,
    val coloreTesto: Int = android.graphics.Color.WHITE,
    val coloreBordo: Int = android.graphics.Color.WHITE,
) {
    companion object {
        /** l'ordine delle soste scelte: sopra a tutto */
        const val SCELTO = -1_000_000.0
    }
}

private fun pinCarburante(s: StatoViaggio.Pronto): List<PinViaggio> {
    val sosteId = s.piano.soste.map { it.punto.distributore.id }.toSet()
    val mostrati = (s.piano.soste.map { it.punto } + s.piano.alternative + listOfNotNull(s.piano.migliore)).distinctBy { it.distributore.id }
    return mostrati.map { p ->
        val scelto = p.distributore.id in sosteId || (s.piano.senzaSoste && p == s.piano.migliore)
        val colore = if (scelto) Colori.Inchiostro else if (p.distributore.autostradale) Colori.Rosso else Colori.VerdeTesto
        PinViaggio(p.distributore.lat, p.distributore.lon, Formati.prezzo(p.prezzo.millesimi), colore.toArgb(), scelto, if (scelto) PinViaggio.SCELTO else p.prezzo.millesimi.toDouble())
    }
}

@Composable
internal fun MappaViaggio(campioni: List<PuntoPercorso>, pin: List<PinViaggio>) {
    val context = LocalContext.current
    val densita = LocalDensity.current
    val disegnatore = remember { PinPrezzo(context) }
    val vistaMappa = rememberVistaMappa { v ->
        v.getMapAsync { m ->
            m.uiSettings.isRotateGesturesEnabled = false
            m.uiSettings.isTiltGesturesEnabled = false
            m.uiSettings.isCompassEnabled = false
            m.uiSettings.isLogoEnabled = false
            m.setStyle(Style.Builder().fromUri(STILE_MAPPA)) { stile ->
                val linea = LineString.fromLngLats(campioni.map { Point.fromLngLat(it.lon, it.lat) })
                stile.addSource(GeoJsonSource("goccia-percorso", FeatureCollection.fromFeatures(listOf(Feature.fromGeometry(linea)))))
                stile.addLayer(
                    LineLayer("goccia-percorso", "goccia-percorso").withProperties(
                        PropertyFactory.lineColor(Colori.Petrolio.toArgb()),
                        PropertyFactory.lineWidth(5f),
                        PropertyFactory.lineCap(Property.LINE_CAP_ROUND),
                        PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
                    ),
                )
                val elementi = pin.map { p ->
                    val chiave = "v-${p.testo}-${p.colore}-${p.coloreTesto}-${p.scelto}"
                    stile.addImage(chiave, disegnatore.disegna(p.testo, p.colore, p.scelto, p.coloreTesto, p.coloreBordo, fulmine = p.testo.isEmpty()))
                    Feature.fromGeometry(Point.fromLngLat(p.lon, p.lat)).apply {
                        addStringProperty("icona", chiave)
                        addNumberProperty("ordine", p.ordine)
                    }
                }
                stile.addSource(GeoJsonSource("goccia-soste", FeatureCollection.fromFeatures(elementi)))
                Log.i("Goccia", "viaggio: ${elementi.size} segnaposto sulla mappa")
                stile.addLayer(
                    SymbolLayer("goccia-soste", "goccia-soste").withProperties(
                        PropertyFactory.iconImage(Expression.get("icona")),
                        PropertyFactory.iconAnchor(Property.ICON_ANCHOR_BOTTOM),
                        PropertyFactory.iconAllowOverlap(true),
                        // qui si vedono tutti: chi viene disegnato dopo sta sopra, quindi la sosta scelta
                        // (ordine piu basso) deve avere la chiave piu alta
                        PropertyFactory.symbolSortKey(Expression.product(Expression.literal(-1), Expression.get("ordine"))),
                    ),
                )
                val lat = campioni.map { it.lat }
                val lon = campioni.map { it.lon }
                if (lat.isNotEmpty()) {
                    try {
                        val bordi = LatLngBounds.from(lat.max(), lon.max(), lat.min(), lon.min())
                        fun px(valore: Int) = with(densita) { valore.dp.roundToPx() }
                        // in alto c'e la barra di stato con il pulsante indietro
                        m.moveCamera(CameraUpdateFactory.newLatLngBounds(bordi, px(48), px(96), px(48), px(28)))
                    } catch (e: Exception) {
                        // mappa non ancora misurata: resta sulla vista iniziale
                    }
                }
            }
        }
    }
    AndroidView(factory = { vistaMappa }, modifier = Modifier.fillMaxSize())
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Text(
            "© OpenStreetMap · OpenFreeMap",
            style = Testi.Minimo.copy(color = Colori.Testo2, fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
            modifier = Modifier.padding(8.dp).clip(RoundedCornerShape(6.dp)).background(Color.White.copy(alpha = 0.8f)).padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
