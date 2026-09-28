package it.goccia.app.ui.stati

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Comune
import it.goccia.app.dati.TipoLuogo
import it.goccia.app.logica.Formati
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.ProblemaPosizione
import it.goccia.app.ui.StatoDati
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.CampoRicerca
import it.goccia.app.ui.componenti.ChipScelta
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra

/** "Sei offline: ti mostriamo i prezzi salvati..." */
@Composable
fun BannerOffline(scaricatoIl: Long?, onRiprova: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Forme.Card)
            .background(Colori.AmbraChiaro)
            .border(1.dp, Colori.AmbraBordo, Forme.Card)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Colori.Superficie), contentAlignment = Alignment.Center) {
            Icon(Icone.Offline, null, tint = Colori.AmbraScuro, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Sei offline", style = Testi.CorpoForte.copy(color = Color(0xFF5B3A07)))
            val quando = scaricatoIl?.let { "salvati ${Formati.quandoMillis(it, System.currentTimeMillis())}" } ?: "salvati sul telefono"
            Text("Ti mostriamo i prezzi $quando.", style = Testi.Didascalia.copy(color = Color(0xFF6B4A12)))
        }
        Text(
            "Riprova",
            style = Testi.ChipAttivo.copy(color = Color(0xFF5B3A07)),
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Colori.Superficie)
                .clickable(onClick = onRiprova)
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

/** Primo avvio senza connessione: non abbiamo ancora nessun prezzo. */
@Composable
fun SenzaDati(onRiprova: () -> Unit, modifier: Modifier = Modifier) {
    StatoVuoto(
        icona = Icone.Offline,
        titolo = "Serve la connessione",
        testo = "Per il primo avvio Goccia deve scaricare i prezzi di oggi. Poi funziona anche offline con gli ultimi dati.",
        azione = "Riprova",
        onAzione = onRiprova,
        modifier = modifier,
    )
}

@Composable
fun StatoVuoto(
    icona: ImageVector,
    titolo: String,
    testo: String,
    modifier: Modifier = Modifier,
    azione: String? = null,
    onAzione: () -> Unit = {},
    coloreIcona: Color = Colori.Petrolio,
    sfondoIcona: Color = Colori.PetrolioChiaro,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(120.dp).clip(CircleShape).background(sfondoIcona), contentAlignment = Alignment.Center) {
            Icon(icona, null, tint = coloreIcona, modifier = Modifier.size(56.dp))
        }
        Text(titolo, style = Testi.Titolo, textAlign = TextAlign.Center)
        Text(testo, style = Testi.Corpo.copy(color = Colori.Testo2), textAlign = TextAlign.Center)
        if (azione != null) BottonePrimario(azione, onAzione, Modifier.fillMaxWidth(), altezza = 54.dp)
    }
}

/** Suggerimenti di comuni sotto un campo di ricerca. */
@Composable
fun SuggerimentiComuni(risultati: List<Comune>, onScelto: (Comune) -> Unit, modifier: Modifier = Modifier) {
    if (risultati.isEmpty()) return
    Column(
        modifier
            .fillMaxWidth()
            .ombra(Forme.Card, 4.dp)
            .clip(Forme.Card)
            .background(Colori.Superficie),
    ) {
        risultati.forEachIndexed { i, comune ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onScelto(comune) }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icone.Segnaposto, null, tint = Colori.Petrolio, modifier = Modifier.size(18.dp))
                Text(comune.etichetta, style = Testi.CorpoForte, modifier = Modifier.weight(1f))
                Text(
                    "${comune.distributori} ${if (comune.distributori == 1) "distributore" else "distributori"}",
                    style = Testi.Piccolo.copy(color = Colori.Testo3),
                )
            }
            if (i < risultati.lastIndex) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Colori.Divisore))
        }
    }
}

/**
 * Quando non sappiamo dove sei: attiva la posizione, oppure cerca un comune
 * o parti da un luogo salvato.
 */
@Composable
fun PannelloPosizione(vm: GocciaViewModel, dati: StatoDati, onLuogo: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val utente by vm.utente.collectAsStateWithLifecycle()
    var negato by remember { mutableStateOf(false) }
    val richiesta = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { esito ->
        if (esito.values.any { it }) vm.usaPosizione() else negato = true
    }
    var testo by remember { mutableStateOf("") }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(140.dp).clip(CircleShape).background(Colori.PetrolioChiaro), contentAlignment = Alignment.Center) {
            Icon(Icone.PosizioneSpenta, null, tint = Colori.Petrolio, modifier = Modifier.size(64.dp))
        }
        Text(
            "Non sappiamo dove sei",
            style = Testi.Titolo.copy(fontSize = Testi.TitoloSchermata.fontSize),
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
        val spiegazione = when {
            dati.problemaPosizione == ProblemaPosizione.GPS_SPENTO -> "La localizzazione del telefono è spenta. Accendila per vedere i distributori vicini."
            negato -> "Senza il permesso puoi cercare un comune qui sotto, oppure concederlo dalle impostazioni del telefono."
            else -> "Attiva la posizione per vedere i distributori vicini. La usiamo solo mentre l'app è aperta."
        }
        Text(spiegazione, style = Testi.Corpo.copy(color = Colori.Testo2), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        BottonePrimario(
            when {
                dati.problemaPosizione == ProblemaPosizione.GPS_SPENTO -> "Accendi la localizzazione"
                negato -> "Apri le impostazioni"
                else -> "Attiva la posizione"
            },
            onClick = {
                when {
                    dati.problemaPosizione == ProblemaPosizione.GPS_SPENTO ->
                        context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    negato -> context.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                    )
                    dati.problemaPosizione == ProblemaPosizione.NON_TROVATA -> vm.usaPosizione()
                    else -> richiesta.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            icona = Icone.Segnaposto,
            altezza = 54.dp,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f).height(1.dp).background(Colori.Bordo))
            Text("oppure", style = Testi.DidascaliaForte.copy(color = Colori.Testo3))
            Box(Modifier.weight(1f).height(1.dp).background(Colori.Bordo))
        }
        CampoRicerca(testo, { testo = it }, Modifier.fillMaxWidth(), segnaposto = "Cerca un comune")
        SuggerimentiComuni(vm.cercaComuni(testo), onScelto = {
            testo = ""
            vm.centraSu(it)
        })
        Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            utente.luoghi.take(3).forEach { luogo ->
                ChipScelta(
                    "Vicino a ${luogo.nome}",
                    attivo = false,
                    onClick = { vm.centraSu(luogo) },
                    icona = if (luogo.tipo == TipoLuogo.LAVORO) Icone.Lavoro else Icone.Casa,
                    altezza = 40.dp,
                )
            }
            if (utente.luoghi.none { it.tipo == TipoLuogo.CASA }) {
                ChipScelta("Salva Casa", attivo = false, onClick = onLuogo, icona = Icone.Casa, altezza = 40.dp)
            }
        }
    }
}
