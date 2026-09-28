package it.goccia.app.ui.profilo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.BuildConfig
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Rifornimento
import it.goccia.app.logica.Consiglio
import it.goccia.app.logica.FUSO_ITALIA
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Statistiche
import it.goccia.app.logica.meseDi
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.apriLink
import it.goccia.app.ui.componenti.Badge
import it.goccia.app.ui.componenti.BarraTitolo
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.GraficoBarre
import it.goccia.app.ui.componenti.Gruppo
import it.goccia.app.ui.componenti.RigaVoce
import it.goccia.app.ui.componenti.Scheda
import it.goccia.app.ui.condividi
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.stati.StatoVuoto
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra
import java.time.Instant

@Composable
fun SchermataRegistro(vm: GocciaViewModel, onIndietro: () -> Unit) {
    val utente by vm.utente.collectAsStateWithLifecycle()
    var daEliminare by remember { mutableStateOf<Rifornimento?>(null) }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BarraTitolo("Registro rifornimenti", onIndietro)
        if (utente.rifornimenti.isEmpty()) {
            StatoVuoto(
                Icone.Registro,
                "Ancora nessun rifornimento",
                "Registralo dalla Home o dal dettaglio di un distributore: calcoliamo spesa, consumi e quanto risparmi.",
                modifier = Modifier.padding(20.dp),
            )
            return@Column
        }
        val perMese = utente.rifornimenti.sortedByDescending { it.quando }.groupBy { meseDi(it.quando) }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            perMese.forEach { (mese, lista) ->
                item(key = "m$mese") {
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Bottom) {
                        Text(
                            "${Formati.meseLungo(mese).replaceFirstChar { it.uppercase() }} ${mese.year}",
                            style = Testi.Sottosezione,
                            modifier = Modifier.weight(1f),
                        )
                        Text(Formati.euro(lista.sumOf { it.importo }), style = Testi.CorpoForte.copy(fontFeatureSettings = "tnum"))
                    }
                }
                items(lista, key = { it.id }) { r -> RigaRifornimento(r, utente.auto.firstOrNull { it.id == r.autoId }?.nome) { daEliminare = r } }
            }
        }
    }
    daEliminare?.let { r ->
        AlertDialog(
            onDismissRequest = { daEliminare = null },
            containerColor = Colori.Superficie,
            title = { Text("Eliminare questo rifornimento?", style = Testi.Sottosezione) },
            text = { Text("${r.distributore} · ${Formati.euro(r.importo)}", style = Testi.Didascalia.copy(color = Colori.Testo2)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.eliminaRifornimento(r.id)
                    daEliminare = null
                }) { Text("Elimina", style = Testi.Pulsante.copy(color = Colori.Rosso)) }
            },
            dismissButton = { TextButton(onClick = { daEliminare = null }) { Text("Annulla", style = Testi.Pulsante.copy(color = Colori.Testo2)) } },
        )
    }
}

@Composable
private fun RigaRifornimento(r: Rifornimento, auto: String?, onElimina: () -> Unit) {
    val z = Instant.ofEpochMilli(r.quando).atZone(FUSO_ITALIA)
    val c = Carburante.daCodice(r.carburante)
    Row(
        Modifier
            .fillMaxWidth()
            .ombra(Forme.Card, 1.dp)
            .clip(Forme.Card)
            .background(Colori.Superficie)
            .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(44.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(z.dayOfMonth.toString(), style = Testi.Titolo.copy(fontFeatureSettings = "tnum"))
            Text(Formati.meseBreve(meseDi(r.quando)), style = Testi.Piccolo.copy(color = Colori.Testo3))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(r.distributore, style = Testi.CorpoForte, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val modo = if (c?.haSelf == true) (if (r.self) " self" else " servito") else ""
            val unita = if (c == Carburante.METANO) "kg" else "l"
            Text(
                "${c?.etichetta ?: r.carburante}$modo · ${Formati.numero(r.litri, 1)} $unita a ${Formati.prezzo(r.prezzo)}" +
                    (r.km?.let { " · ${Formati.numero(it.toDouble(), 0)} km" } ?: "") + (auto?.let { " · $it" } ?: ""),
                style = Testi.Didascalia.copy(color = Colori.Testo3),
                maxLines = 2,
            )
            val risparmio = r.risparmio
            if (risparmio != null && kotlin.math.abs(risparmio) >= 0.01) {
                if (risparmio > 0) Badge("Risparmiati ${Formati.euro(risparmio)}", Colori.VerdeChiaro, Colori.VerdeTesto)
                else Badge("${Formati.euro(-risparmio)} sopra la media", Colori.GrigioBadge, Colori.TestoChip)
            }
        }
        Text(Formati.euro(r.importo), style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"))
        BottoneIcona(Icone.Cestino, "Elimina rifornimento", onElimina, colore = Colori.Testo3, dimensione = 40.dp, dimensioneIcona = 18.dp)
    }
}

@Composable
fun SchermataStatistiche(vm: GocciaViewModel, onIndietro: () -> Unit) {
    val utente by vm.utente.collectAsStateWithLifecycle()
    val adesso = System.currentTimeMillis()
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BarraTitolo("Statistiche", onIndietro)
        if (utente.rifornimenti.isEmpty()) {
            StatoVuoto(
                Icone.Grafico,
                "Qui vedrai spesa e consumi",
                "Registra i tuoi rifornimenti (meglio se con il pieno e i chilometri): ti mostriamo spesa mensile, consumo reale e risparmio.",
                modifier = Modifier.padding(20.dp),
            )
            return@Column
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val mese = meseDi(adesso)
            val delMese = utente.rifornimenti.filter { meseDi(it.quando) == mese }
            val risparmio = Statistiche.risparmio(utente.rifornimenti, adesso)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Spesa di ${Formati.meseLungo(mese)}", Formati.euro(delMese.sumOf { it.importo }), Modifier.weight(1f))
                Kpi("Risparmio totale", Formati.euro(risparmio.totale), Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Kpi("Litri nel mese", Formati.numero(delMese.sumOf { it.litri }, 1), Modifier.weight(1f))
                Kpi("Rifornimenti", utente.rifornimenti.size.toString(), Modifier.weight(1f))
            }
            val spesa = Statistiche.spesaPerMese(utente.rifornimenti, adesso)
            Scheda(Modifier.fillMaxWidth()) {
                Text("Spesa per mese", style = Testi.Sottosezione)
                GraficoBarre(spesa.map { it.second }, spesa.map { Formati.meseBreve(it.first) }, altezza = 120.dp, descrizione = "Spesa negli ultimi sei mesi")
                Text("Media ${Formati.euro(spesa.map { it.second }.average())} al mese negli ultimi sei mesi.", style = Testi.Didascalia.copy(color = Colori.Testo2))
            }
            Scheda(Modifier.fillMaxWidth()) {
                Text("Risparmio per mese", style = Testi.Sottosezione)
                GraficoBarre(
                    risparmio.perMese.map { it.second },
                    risparmio.perMese.map { Formati.meseBreve(it.first) },
                    altezza = 120.dp,
                    colore = Colori.VerdeChiaro,
                    coloreUltimo = Colori.Verde,
                    descrizione = "Risparmio negli ultimi sei mesi",
                )
                Text("Prezzo pagato rispetto alla media della zona nel giorno del rifornimento.", style = Testi.Didascalia.copy(color = Colori.Testo2))
            }
            utente.auto.filter { !it.alimentazione.elettrica }.forEach { auto ->
                val suoi = utente.rifornimenti.filter { it.autoId == auto.id || (it.autoId == null && auto.id == utente.autoCorrente?.id) }
                if (suoi.isEmpty()) return@forEach
                val consumo = Statistiche.consumo(suoi)
                val km = Consiglio.kmGiornalieri(suoi, auto.id)
                Gruppo(auto.nome) {
                    RigaVoce(
                        "Consumo reale",
                        valore = consumo?.let { "${Formati.numero(it, 1)} ${auto.alimentazione.unitaConsumo}" } ?: "servono 2 pieni con i km",
                    )
                    RigaVoce("Consumo dichiarato", valore = "${Formati.numero(auto.consumo, 1)} ${auto.alimentazione.unitaConsumo}")
                    RigaVoce("Km al giorno", valore = km?.let { "~${Formati.numero(it, 0)} km" } ?: "servono 2 settimane di dati")
                    RigaVoce("Speso in totale", valore = Formati.euro(suoi.sumOf { it.importo }), divisore = false)
                }
            }
        }
    }
}

@Composable
private fun Kpi(titolo: String, valore: String, modifier: Modifier = Modifier) {
    Column(
        modifier.ombra(Forme.Card, 1.dp).clip(Forme.Card).background(Colori.Superficie).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(titolo, style = Testi.Piccolo.copy(color = Colori.Testo3), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(valore, style = Testi.Titolo.copy(fontSize = Testi.Sezione.fontSize, fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

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
            Text(
                "Quando Goccia sarà su Google Play potrai offrire un caffè direttamente da qui. Tutte le funzioni restano gratuite per tutti.",
                style = Testi.Didascalia.copy(color = Colori.Testo2),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}
