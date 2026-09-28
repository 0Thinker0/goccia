package it.goccia.app.ui.preferiti

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.avvisi.Notifiche
import it.goccia.app.dati.Avviso
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Coordinate
import it.goccia.app.dati.DatiUtente
import it.goccia.app.dati.Preferito
import it.goccia.app.dati.TipoLuogo
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Geo
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.componenti.Badge
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.Interruttore
import it.goccia.app.ui.componenti.LogoBandiera
import it.goccia.app.ui.componenti.Riquadro
import it.goccia.app.ui.componenti.Segmentato
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.stati.StatoVuoto
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra

@Composable
fun SchermataPreferiti(
    vm: GocciaViewModel,
    scheda: Int,
    onScheda: (Int) -> Unit,
    onDistributore: (String, Long) -> Unit,
    onNuovoAvviso: () -> Unit,
    onAvviso: (String) -> Unit,
    onLuogo: (String?, String?) -> Unit,
    onCerca: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.avvia() }
    val utente by vm.utente.collectAsStateWithLifecycle()
    LaunchedEffect(utente.preferiti.map { it.provincia }.distinct()) {
        vm.caricaProvince(utente.preferiti.map { it.provincia }.distinct())
    }
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Text("Preferiti", style = Testi.TitoloSchermata, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp))
            Segmentato(
                listOf("Distributori · ${utente.preferiti.size}", "Avvisi · ${utente.avvisi.size}"),
                scheda,
                onScheda,
                Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
                altezza = 40.dp,
            )
            if (scheda == 0) ListaPreferiti(vm, utente, onDistributore, onCerca) else ListaAvvisi(vm, utente, onAvviso, onLuogo, onNuovoAvviso)
        }
        if (scheda == 1) {
            BottonePrimario(
                "Nuovo avviso",
                onNuovoAvviso,
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(20.dp)
                    .ombra(RoundedCornerShape(18.dp), 8.dp),
                icona = Icone.Piu,
                altezza = 56.dp,
                forma = RoundedCornerShape(18.dp),
            )
        }
    }
}

@Composable
private fun AvvisoNotificheSpente(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var concesso by remember { mutableStateOf(Notifiche.permesso(context)) }
    val richiesta = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concesso = Notifiche.permesso(context) }
    if (concesso) return
    Riquadro(modifier.fillMaxWidth(), sfondo = Colori.AmbraChiaro, icona = Icone.Campanella, coloreIcona = Colori.AmbraScuro) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Le notifiche di Goccia sono spente: gli avvisi non potranno raggiungerti.", style = Testi.Didascalia.copy(color = Colori.AmbraTesto))
            if (Build.VERSION.SDK_INT >= 33) {
                Text(
                    "Attiva le notifiche",
                    style = Testi.DidascaliaForte.copy(color = Colori.AmbraTesto),
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { richiesta.launch(Manifest.permission.POST_NOTIFICATIONS) }.padding(vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ListaPreferiti(vm: GocciaViewModel, utente: DatiUtente, onDistributore: (String, Long) -> Unit, onCerca: () -> Unit) {
    if (utente.preferiti.isEmpty()) {
        StatoVuoto(
            Icone.Stella,
            "Nessun preferito, per ora",
            "Tocca la stella nel dettaglio di un distributore: qui vedrai il suo prezzo e potrai farti avvisare quando scende.",
            modifier = Modifier.padding(20.dp),
            azione = "Cerca distributori",
            onAzione = onCerca,
            coloreIcona = Colori.AmbraScuro,
            sfondoIcona = Colori.AmbraChiaro,
        )
        return
    }
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "info") {
            Riquadro(Modifier.fillMaxWidth(), icona = Icone.Campanella) {
                Text(
                    buildAnnotatedString {
                        append("Attiva ")
                        withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("Avvisami se scende") }
                        append(" e ricevi una notifica appena il prezzo di un preferito cala.")
                    },
                    style = Testi.Didascalia.copy(color = Color(0xFF1F3A4A)),
                )
            }
        }
        if (utente.preferiti.any { it.avvisaCalo }) item(key = "notifiche") { AvvisoNotificheSpente() }
        items(utente.preferiti, key = { it.id }) { p -> CardPreferito(vm, utente, p, onDistributore) }
    }
}

@Composable
private fun CardPreferito(vm: GocciaViewModel, utente: DatiUtente, p: Preferito, onDistributore: (String, Long) -> Unit) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    val d = dati.distributori.firstOrNull { it.id == p.id }
    val carburante = utente.carburante
    val self = utente.impostazioni.preferisciSelf
    val prezzo = d?.let { Convenienza.prezzoPer(it, carburante, self) }
    val variazione by produceState<Int?>(null, p.id, carburante, dati.indice?.estrazione) {
        val storico = vm.storicoDistributore(p.provincia, p.id)
        val serie = storico?.giornaliero?.get(carburante)?.filterNotNull().orEmpty()
        value = if (serie.size >= 2) serie[serie.lastIndex] - serie[serie.lastIndex - 1] else null
    }
    Column(
        Modifier
            .fillMaxWidth()
            .ombra(Forme.Card)
            .clip(Forme.Card)
            .background(Colori.Superficie),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable { onDistributore(p.provincia, p.id) }.padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            LogoBandiera(d?.bandiera ?: "", d?.pompaBianca ?: false)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(d?.titolo ?: p.titolo, style = Testi.CorpoForte, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val modo = if (carburante.haSelf) (if (prezzo?.second == false) " servito" else " self") else ""
                val vicino = utente.luoghi.firstOrNull { Geo.distanzaKm(it.lat, it.lon, p.lat, p.lon) < 2.0 }
                val dove = vicino?.let { l -> if (l.tipo == TipoLuogo.CASA) "vicino a Casa" else "vicino a ${l.nome}" }
                    ?: vm.distanzaDalCentro(Coordinate(p.lat, p.lon))?.let { Formati.km(it) }
                    ?: d?.comune.orEmpty()
                Text(
                    "${carburante.etichetta}$modo · $dove",
                    style = Testi.Didascalia.copy(color = Colori.Testo3),
                    maxLines = 1,
                )
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(prezzo?.let { Formati.prezzo(it.first.millesimi) } ?: "—", style = Testi.Prezzo.copy(fontSize = Testi.Prezzo.fontSize * 0.96f))
                val v = variazione
                when {
                    prezzo == null && d != null -> Badge("non venduto", Colori.GrigioBadge, Colori.TestoChip, stile = Testi.Etichetta)
                    v == null -> {}
                    v < 0 -> Badge("↓ ${Formati.numero(-v / 10.0, if (v % 10 == 0) 0 else 1)} cent da ieri", Colori.VerdeChiaro, Colori.VerdeTesto)
                    v > 0 -> Badge("↑ ${Formati.numero(v / 10.0, if (v % 10 == 0) 0 else 1)} cent da ieri", Colori.RossoChiaro, Colori.Rosso)
                    else -> Badge("= invariato", Colori.GrigioBadge, Colori.TestoChip)
                }
            }
        }
        Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(Colori.Divisore))
        Row(
            Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Avvisami se scende", style = Testi.Chip.copy(color = Colori.TestoChip), modifier = Modifier.weight(1f))
            Interruttore(p.avvisaCalo, { vm.avvisaCalo(p.id, it) }, descrizione = "Avvisami se scende: ${p.titolo}")
        }
    }
}

/** "Gasolio sotto 1,700 €/l" */
fun titoloAvviso(a: Avviso): String {
    val c = Carburante.daCodice(a.carburante) ?: Carburante.BENZINA
    return "${c.etichetta} sotto ${Formati.prezzo(a.soglia)} ${c.unita}"
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ListaAvvisi(
    vm: GocciaViewModel,
    utente: DatiUtente,
    onAvviso: (String) -> Unit,
    onLuogo: (String?, String?) -> Unit,
    onNuovoAvviso: () -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (!utente.impostazioni.avvisiPrezzo) {
            item(key = "spenti") {
                Riquadro(Modifier.fillMaxWidth(), sfondo = Colori.AmbraChiaro, icona = Icone.Info, coloreIcona = Colori.AmbraScuro) {
                    Text("Gli avvisi di prezzo sono spenti nelle impostazioni.", style = Testi.Didascalia.copy(color = Colori.AmbraTesto))
                }
            }
        }
        if (utente.avvisi.isNotEmpty()) item(key = "notifiche") { AvvisoNotificheSpente() }
        if (utente.avvisi.isEmpty()) {
            item(key = "vuoto") {
                StatoVuoto(
                    Icone.Campanella,
                    "Nessun avviso",
                    "Scegli una soglia di prezzo: quando un distributore vicino a casa (o dove vuoi tu) scende sotto, ti mandiamo una notifica.",
                    azione = "Crea il primo avviso",
                    onAzione = onNuovoAvviso,
                )
            }
        }
        items(utente.avvisi, key = { it.id }) { a -> CardAvviso(vm, utente, a, onAvviso) }
        item(key = "luoghi") {
            Column(Modifier.padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Luoghi salvati", style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    utente.luoghi.forEach { l ->
                        ChipLuogo(l.nome, if (l.tipo == TipoLuogo.LAVORO) Icone.Lavoro else Icone.Casa, if (l.tipo == TipoLuogo.LAVORO) Colori.Lavoro else Colori.Petrolio) {
                            onLuogo(l.id, null)
                        }
                    }
                    val forma = RoundedCornerShape(20.dp)
                    Row(
                        Modifier
                            .height(40.dp)
                            .clip(forma)
                            .border(1.5.dp, Colori.Tratteggio, forma)
                            .clickable { onLuogo(null, if (utente.casa == null) "CASA" else "ALTRO") }
                            .padding(start = 10.dp, end = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icone.Piu, null, tint = Colori.Inchiostro, modifier = Modifier.size(16.dp))
                        Text(if (utente.casa == null) "Aggiungi Casa" else "Aggiungi", style = Testi.ChipAttivo)
                    }
                }
                Text(
                    "Gli avvisi controllano i prezzi attorno ai luoghi salvati: la tua posizione non viene seguita.",
                    style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
                )
            }
        }
    }
}

@Composable
private fun ChipLuogo(nome: String, icona: ImageVector, colore: Color, onClick: () -> Unit) {
    val forma = RoundedCornerShape(20.dp)
    Row(
        Modifier
            .height(40.dp)
            .clip(forma)
            .background(Colori.Superficie)
            .border(1.dp, Colori.Bordo, forma)
            .clickable(onClick = onClick)
            .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icona, null, tint = colore, modifier = Modifier.size(16.dp))
        Text(nome, style = Testi.ChipAttivo)
    }
}

@Composable
private fun CardAvviso(vm: GocciaViewModel, utente: DatiUtente, a: Avviso, onAvviso: (String) -> Unit) {
    val adesso = System.currentTimeMillis()
    val luogo = utente.luogo(a.luogoId)
    val dove = (luogo?.nome ?: "Vicino a te") + " · entro ${a.raggioKm} km"
    val stato = when {
        !a.attivo -> "In pausa"
        a.ultimaNotificaIl != null && a.ultimoEsito != null && a.ultimoMinimo != null ->
            "Ultima notifica ${Formati.quandoMillis(a.ultimaNotificaIl, adesso)} · ${a.ultimoEsito}"
        a.minimoVisto != null && a.minimoVisto > a.soglia ->
            "Oggi il prezzo più basso è ${Formati.prezzo(a.minimoVisto)}: sopra la soglia"
        a.ultimoControlloIl != null && a.minimoVisto == null -> "Nessun prezzo recente nella zona"
        a.ultimoControlloIl != null -> "Controllato ${Formati.quandoMillis(a.ultimoControlloIl, adesso)}"
        else -> "Attivo · lo controlliamo ogni mattina"
    }
    Column(
        Modifier
            .fillMaxWidth()
            .ombra(Forme.Card)
            .clip(Forme.Card)
            .background(Colori.Superficie)
            .clickable { onAvviso(a.id) }
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (a.attivo) Colori.PetrolioChiaro else Colori.GrigioBadge),
                contentAlignment = Alignment.Center,
            ) { Icon(Icone.Campanella, null, tint = if (a.attivo) Colori.Petrolio else Colori.Testo3, modifier = Modifier.size(20.dp)) }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(titoloAvviso(a), style = Testi.Voce)
                Text(dove, style = Testi.Didascalia.copy(color = Colori.Testo2))
            }
            Interruttore(a.attivo, { vm.attivaAvviso(a.id, it) }, descrizione = "Avviso ${titoloAvviso(a)}")
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(Colori.Grigio).padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(Icone.Orologio, null, tint = Colori.TestoChip, modifier = Modifier.size(14.dp))
            Text(stato, style = Testi.Piccolo.copy(color = Colori.TestoChip), maxLines = 2)
        }
    }
}
