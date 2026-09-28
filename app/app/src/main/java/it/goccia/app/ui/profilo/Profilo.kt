package it.goccia.app.ui.profilo

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Auto
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Statistiche
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.RigaVoce
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra

@Composable
fun SchermataProfilo(
    vm: GocciaViewModel,
    onImpostazioni: () -> Unit,
    onAuto: (String?) -> Unit,
    onRegistro: () -> Unit,
    onStatistiche: () -> Unit,
    onSostieni: () -> Unit,
) {
    val context = LocalContext.current
    val utente by vm.utente.collectAsStateWithLifecycle()
    val esporta = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scriviFile(context, uri, csvRifornimenti(utente))
    }
    val adesso = System.currentTimeMillis()
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 20.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Profilo", style = Testi.TitoloSchermata, modifier = Modifier.weight(1f))
            BottoneIcona(Icone.Ingranaggio, "Impostazioni", onImpostazioni)
        }

        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp), verticalAlignment = Alignment.Bottom) {
            Text("Il tuo garage", style = Testi.Sottosezione, modifier = Modifier.weight(1f))
            if (utente.auto.size > 1) Text("Tocca per scegliere l'auto attiva", style = Testi.Didascalia.copy(color = Colori.Testo3, fontWeight = FontWeight.SemiBold))
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val attiva = utente.autoCorrente?.id
            utente.auto.forEach { auto ->
                CardAuto(auto, auto.id == attiva, onScegli = { vm.scegliAuto(auto.id) }, onModifica = { onAuto(auto.id) })
            }
            val forma = RoundedCornerShape(18.dp)
            Box(
                Modifier
                    .width(if (utente.auto.isEmpty()) 150.dp else 64.dp)
                    .height(112.dp)
                    .clip(forma)
                    .border(1.5.dp, Colori.Tratteggio, forma)
                    .clickable(role = Role.Button) { onAuto(null) },
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icone.Piu, "Aggiungi auto", tint = Colori.Petrolio, modifier = Modifier.size(22.dp))
                    if (utente.auto.isEmpty()) Text("Aggiungi auto", style = Testi.ChipAttivo.copy(color = Colori.Petrolio))
                }
            }
        }

        val risparmio = utente.rifornimenti.sumOf { it.risparmio ?: 0.0 }
        val consumo = utente.autoCorrente?.let { a -> Statistiche.consumo(utente.rifornimenti.filter { it.autoId == null || it.autoId == a.id }) }
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Numero(Formati.euro(risparmio), "risparmiati", Modifier.weight(1f))
            Numero(utente.rifornimenti.size.toString(), "rifornimenti", Modifier.weight(1f))
            Numero(
                consumo?.let { Formati.numero(it, 1) } ?: "—",
                utente.autoCorrente?.alimentazione?.unitaConsumo?.let { "$it medi" } ?: "consumo medio",
                Modifier.weight(1f),
            )
        }

        Column(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 16.dp)
                .fillMaxWidth()
                .ombra(Forme.CardGrande, 2.dp)
                .clip(Forme.CardGrande)
                .background(Colori.Superficie),
        ) {
            val ultimo = utente.rifornimenti.maxByOrNull { it.quando }
            RigaVoce(
                "Registro rifornimenti",
                sottotitolo = ultimo?.let { "Ultimo ${Formati.quandoMillis(it.quando, adesso).substringBefore(" alle")} · ${it.distributore}, ${Formati.euro(it.importo)}" }
                    ?: "Ancora nessun rifornimento registrato",
                icona = Icone.Registro,
                conSfondoIcona = true,
                altezza = 62.dp,
                onClick = onRegistro,
            )
            RigaVoce("Statistiche", sottotitolo = "Spesa, consumi e risparmio", icona = Icone.Grafico, conSfondoIcona = true, altezza = 62.dp, onClick = onStatistiche)
            RigaVoce(
                "Esporta rifornimenti",
                sottotitolo = "File CSV, utile per rimborsi e note spese",
                icona = Icone.Scarica,
                conSfondoIcona = true,
                altezza = 62.dp,
                onClick = { esporta.launch("goccia-rifornimenti.csv") },
            )
            RigaVoce(
                "Backup e impostazioni",
                sottotitolo = "I dati restano sul telefono e nel backup di Android",
                icona = Icone.Nuvola,
                conSfondoIcona = true,
                altezza = 62.dp,
                divisore = false,
                onClick = onImpostazioni,
            )
        }

        Row(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 12.dp)
                .fillMaxWidth()
                .clip(Forme.CardGrande)
                .background(Colori.Crema)
                .border(1.dp, Colori.CremaBordo, Forme.CardGrande)
                .clickable(onClick = onSostieni)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(Colori.AmbraChiaro), contentAlignment = Alignment.Center) {
                Icon(Icone.Cuore, null, tint = Colori.AmbraScuro, modifier = Modifier.size(20.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text("Sostieni il progetto", style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold))
                Text("Goccia è gratis e senza pubblicità", style = Testi.Piccolo.copy(color = Colori.CremaTesto, fontWeight = FontWeight.Medium))
            }
            Icon(Icone.ChevronDestra, null, tint = Colori.CremaIcona, modifier = Modifier.size(18.dp))
        }
        Text(
            "Nessun account e nessun tracciamento: rifornimenti, auto e luoghi restano sul tuo telefono.",
            style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp),
        )
    }
}

@Composable
private fun CardAuto(auto: Auto, attiva: Boolean, onScegli: () -> Unit, onModifica: () -> Unit) {
    val forma = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .width(150.dp)
            .height(112.dp)
            .clip(forma)
            .background(if (attiva) Colori.PetrolioTenue else Colori.Superficie)
            .border(1.5.dp, if (attiva) Colori.Petrolio else Colori.Bordo, forma)
            .clickable(role = Role.RadioButton) { if (attiva) onModifica() else onScegli() }
            .padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(if (attiva) Colori.Petrolio else Colori.Grigio),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (auto.alimentazione.elettrica) Icone.Fulmine else Icone.Auto,
                    null,
                    tint = if (attiva) Color.White else Colori.Petrolio,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(Modifier.weight(1f))
            if (attiva) {
                Text(
                    "Attiva",
                    style = Testi.Minimo.copy(color = Color.White, fontWeight = FontWeight.ExtraBold),
                    modifier = Modifier.clip(Forme.Pillola).background(Colori.Petrolio).padding(horizontal = 8.dp, vertical = 3.dp),
                )
            } else {
                BottoneIcona(Icone.Matita, "Modifica ${auto.nome}", onModifica, dimensione = 32.dp, dimensioneIcona = 16.dp, colore = Colori.Testo3)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(auto.nome, style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
            Text(
                "${auto.alimentazione.etichetta} · ${Formati.numero(auto.capienza, 0)} ${auto.alimentazione.unitaCapienza}",
                style = Testi.Piccolo.copy(color = Colori.Testo2),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Numero(valore: String, etichetta: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .ombra(RoundedCornerShape(16.dp), 1.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Colori.Superficie)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(valore, style = Testi.Sottosezione.copy(fontSize = Testi.Sezione.fontSize, fontFeatureSettings = "tnum"), maxLines = 1)
        Text(etichetta, style = Testi.Piccolo.copy(color = Colori.Testo3), maxLines = 2)
    }
}
