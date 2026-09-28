package it.goccia.app.ui.profilo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Alimentazione
import it.goccia.app.dati.Auto
import it.goccia.app.logica.Formati
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.BottoneTesto
import it.goccia.app.ui.componenti.CampoTesto
import it.goccia.app.ui.componenti.IntestazioneFoglio
import it.goccia.app.ui.componenti.Opzione
import it.goccia.app.ui.componenti.Stepper
import it.goccia.app.ui.componenti.Suggerimento
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi

/** Passo del selettore di capienza per unita. */
private fun passo(a: Alimentazione): Double = if (a.unitaCapienza == "kg") 1.0 else 5.0

private fun limiti(a: Alimentazione): ClosedFloatingPointRange<Double> = when (a.unitaCapienza) {
    "kg" -> 5.0..40.0
    "kWh" -> 15.0..150.0
    else -> 15.0..120.0
}

/** I campi dell'auto, usati sia nell'introduzione sia nel profilo. */
@Composable
fun ModuloAuto(
    nome: String,
    onNome: (String) -> Unit,
    alimentazione: Alimentazione,
    onAlimentazione: (Alimentazione) -> Unit,
    capienza: Double,
    onCapienza: (Double) -> Unit,
    consumo: String,
    onConsumo: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        CampoTesto(nome, onNome, etichetta = "Come la chiami?", segnaposto = "Es. La Tipo")
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Alimentazione", style = Testi.DidascaliaForte.copy(color = Colori.TestoChip))
            Alimentazione.entries.chunked(2).forEach { coppia ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    coppia.forEach { a ->
                        Opzione(
                            a.etichetta,
                            a == alimentazione,
                            { onAlimentazione(a) },
                            Modifier.weight(1f),
                            altezza = 44.dp,
                            conSpunta = true,
                            allineamento = androidx.compose.ui.Alignment.Start,
                        )
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (alimentazione.elettrica) "Batteria" else "Serbatoio", style = Testi.DidascaliaForte.copy(color = Colori.TestoChip))
                val intervallo = limiti(alimentazione)
                Stepper(
                    "${Formati.numero(capienza, 0)} ${alimentazione.unitaCapienza}",
                    onMeno = { onCapienza((capienza - passo(alimentazione)).coerceIn(intervallo)) },
                    onPiu = { onCapienza((capienza + passo(alimentazione)).coerceIn(intervallo)) },
                )
            }
            CampoTesto(
                consumo,
                onConsumo,
                Modifier.weight(1f),
                etichetta = "Consumo medio",
                suffisso = alimentazione.unitaConsumo.substringBefore("/100") + "/100",
                tastiera = KeyboardType.Decimal,
                stile = Testi.Sottosezione.copy(fontSize = 17.sp, fontFeatureSettings = "tnum"),
            )
        }
        Suggerimento("Non sai il consumo? Nessun problema: lo calcoliamo noi dai rifornimenti che registri con il pieno e i chilometri.")
    }
}

@Composable
fun SchermataAuto(vm: GocciaViewModel, id: String?, onChiudi: () -> Unit) {
    val utente by vm.utente.collectAsStateWithLifecycle()
    val esistente = remember(id) { id?.let { i -> utente.auto.firstOrNull { it.id == i } } }
    var nome by rememberSaveable { mutableStateOf(esistente?.nome ?: "") }
    var alimentazione by rememberSaveable { mutableStateOf(esistente?.alimentazione ?: Alimentazione.BENZINA) }
    var capienza by rememberSaveable { mutableDoubleStateOf(esistente?.capienza ?: Alimentazione.BENZINA.capienzaTipica) }
    var consumo by rememberSaveable { mutableStateOf(Formati.numero(esistente?.consumo ?: Alimentazione.BENZINA.consumoTipico, 1)) }
    var conferma by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Colori.Superficie).statusBarsPadding().imePadding()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            IntestazioneFoglio(if (esistente == null) "Nuova auto" else "Modifica auto", onChiudi)
            ModuloAuto(
                nome = nome,
                onNome = { nome = it.take(24) },
                alimentazione = alimentazione,
                onAlimentazione = {
                    if (it != alimentazione) {
                        alimentazione = it
                        capienza = it.capienzaTipica
                        consumo = Formati.numero(it.consumoTipico, 1)
                    }
                },
                capienza = capienza,
                onCapienza = { capienza = it },
                consumo = consumo,
                onConsumo = { consumo = it },
            )
            if (esistente != null) {
                BottoneTesto("Elimina auto", { conferma = true }, colore = Colori.Rosso)
            }
        }
        BottonePrimario(
            "Salva",
            onClick = {
                val valore = Formati.leggiNumero(consumo)?.takeIf { it > 0 && it < 100 } ?: alimentazione.consumoTipico
                val auto = (esistente ?: Auto(id = vm.nuovoId(), nome = "", alimentazione = alimentazione, capienza = capienza, consumo = valore))
                    .copy(nome = nome.trim().ifBlank { "La mia auto" }, alimentazione = alimentazione, capienza = capienza, consumo = valore)
                vm.salvaAuto(auto, attiva = esistente == null)
                onChiudi()
            },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            altezza = 54.dp,
            forma = RoundedCornerShape(16.dp),
        )
    }
    if (conferma && esistente != null) {
        AlertDialog(
            onDismissRequest = { conferma = false },
            containerColor = Colori.Superficie,
            title = { Text("Eliminare ${esistente.nome}?", style = Testi.Sottosezione) },
            text = { Text("I rifornimenti già registrati restano nel registro.", style = Testi.Didascalia.copy(color = Colori.Testo2)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.eliminaAuto(esistente.id)
                    conferma = false
                    onChiudi()
                }) { Text("Elimina", style = Testi.Pulsante.copy(color = Colori.Rosso)) }
            },
            dismissButton = {
                TextButton(onClick = { conferma = false }) { Text("Annulla", style = Testi.Pulsante.copy(color = Colori.Testo2, fontWeight = FontWeight.Bold)) }
            },
        )
    }
}
