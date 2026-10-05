package it.goccia.app.ui.profilo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.BuildConfig
import it.goccia.app.sostegno.Caffe
import it.goccia.app.sostegno.OffertaCaffe
import it.goccia.app.sostegno.StatoCaffe
import it.goccia.app.sostegno.attivita
import it.goccia.app.ui.apriLink
import it.goccia.app.ui.componenti.BarraTitolo
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.Gruppo
import it.goccia.app.ui.componenti.RigaVoce
import it.goccia.app.ui.componenti.Scheda
import it.goccia.app.ui.componenti.TestoCrediti
import it.goccia.app.ui.condividi
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.stati.StatoVuoto
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi

@Composable
fun SchermataSostieni(onIndietro: () -> Unit) {
    val context = LocalContext.current
    val repo = "https://github.com/${BuildConfig.REPO}"
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BarraTitolo("Sostieni Goccia", onIndietro)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            StatoVuoto(
                Icone.Cuore,
                "Gratis, senza pubblicità e senza abbonamenti",
                "Goccia è un progetto indipendente: i prezzi arrivano dai dati aperti del Ministero e l'app non raccoglie nulla su di te.",
                coloreIcona = Colori.AmbraScuro,
                sfondoIcona = Colori.AmbraChiaro,
            )
            val donazioni = BuildConfig.DONAZIONI
            when {
                // versione del Play Store: i caffe passano da Google Play
                BuildConfig.PLAY -> CaffeDaGooglePlay()
                // versione di GitHub: il link esterno, se c'e
                donazioni.isNotBlank() -> {
                    BottonePrimario(
                        "Offri un caffè",
                        { apriLink(context, donazioni) },
                        Modifier.fillMaxWidth(),
                        icona = Icone.Caffe,
                        altezza = 52.dp,
                        sfondo = Colori.AmbraScuro,
                    )
                    Text(
                        "Una donazione una tantum, dell'importo che vuoi: copre server e mappe e non sblocca nulla, perché ogni funzione è già di tutti.",
                        style = Testi.Didascalia.copy(color = Colori.Testo2),
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            Gruppo("Come puoi aiutare") {
                RigaVoce(
                    "Fallo conoscere",
                    sottotitolo = "Condividi Goccia con chi fa spesso benzina",
                    icona = Icone.Condividi,
                    altezza = 62.dp,
                    onClick = {
                        condividi(
                            context,
                            "Uso Goccia per trovare il distributore più conveniente: gratis, senza pubblicità. $repo",
                            "Condividi Goccia",
                        )
                    },
                )
                RigaVoce(
                    "Segnala un problema o un'idea",
                    sottotitolo = "Un prezzo strano, un errore, una funzione che manca",
                    icona = Icone.Bandiera,
                    altezza = 62.dp,
                    onClick = { apriLink(context, "$repo/issues") },
                )
                RigaVoce(
                    "Metti una stella al progetto",
                    sottotitolo = "Il codice è aperto, su GitHub",
                    icona = Icone.Stella,
                    altezza = 62.dp,
                    divisore = false,
                    onClick = { apriLink(context, repo) },
                )
            }
            TestoCrediti(Modifier.fillMaxWidth().padding(top = 4.dp))
        }
    }
}

/** Tre caffè da 1, 3 e 5 euro, pagati con Google Play (prezzi e valuta li dà Google Play). */
@Composable
private fun CaffeDaGooglePlay() {
    val context = LocalContext.current
    val caffe = remember { Caffe(context) }
    DisposableEffect(caffe) {
        caffe.avvia()
        onDispose { caffe.chiudi() }
    }
    val stato by caffe.stato.collectAsStateWithLifecycle()
    Scheda(Modifier.fillMaxWidth(), spazio = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(Colori.AmbraChiaro), contentAlignment = Alignment.Center) {
                Icon(Icone.Caffe, null, tint = Colori.AmbraScuro, modifier = Modifier.size(19.dp))
            }
            Text("Offrimi un caffè", style = Testi.Sottosezione)
        }
        Text(
            "Una donazione una tantum con Google Play. Non sblocca nulla, perché ogni funzione è già di tutti: aiuta a tenere Goccia gratis e senza pubblicità.",
            style = Testi.Didascalia.copy(color = Colori.Testo2),
        )
        when (val s = stato) {
            StatoCaffe.Caricamento -> Text("Carico i prezzi da Google Play…", style = Testi.Didascalia.copy(color = Colori.Testo3))
            StatoCaffe.NonDisponibile -> Text(
                "Adesso non si può offrire un caffè da questo telefono: serve Google Play con un account. Riprova più tardi.",
                style = Testi.Didascalia.copy(color = Colori.Testo3),
            )
            is StatoCaffe.Pronto -> {
                TastiCaffe(s.offerte) { o -> context.attivita()?.let { caffe.offri(it, o) } }
                s.avviso?.let { Text(it, style = Testi.Didascalia.copy(color = Colori.AmbraTesto, fontWeight = FontWeight.SemiBold)) }
            }
            is StatoCaffe.InAttesa -> {
                TastiCaffe(s.offerte) { o -> context.attivita()?.let { caffe.offri(it, o) } }
                Text(
                    "Pagamento in attesa: il caffè arriva appena Google Play lo conferma. Grazie!",
                    style = Testi.Didascalia.copy(color = Colori.AmbraTesto, fontWeight = FontWeight.SemiBold),
                )
            }
            is StatoCaffe.Grazie -> {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Colori.VerdeChiaro).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text("Grazie di cuore!", style = Testi.CorpoForte.copy(color = Colori.VerdeScuro))
                    Text("Il tuo caffè è arrivato: aiuta davvero a tenere Goccia gratuita per tutti.", style = Testi.Didascalia.copy(color = Colori.VerdeScuro))
                }
                TastiCaffe(s.offerte) { o -> context.attivita()?.let { caffe.offri(it, o) } }
            }
        }
    }
}

@Composable
private fun TastiCaffe(offerte: List<OffertaCaffe>, onScelta: (OffertaCaffe) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        offerte.forEach { o ->
            val forma = RoundedCornerShape(14.dp)
            Column(
                Modifier
                    .weight(1f)
                    .clip(forma)
                    .background(Colori.AmbraChiaro)
                    .border(1.dp, Colori.AmbraBordo, forma)
                    .clickable(role = Role.Button, onClickLabel = "Offri ${o.nome.lowercase()} da ${o.prezzo}") { onScelta(o) }
                    .padding(horizontal = 6.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(o.prezzo, style = Testi.CorpoForte.copy(color = Colori.AmbraTesto, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), maxLines = 1)
                Text(o.nome, style = Testi.Piccolo.copy(color = Colori.AmbraTesto), textAlign = TextAlign.Center, maxLines = 2)
            }
        }
    }
}
