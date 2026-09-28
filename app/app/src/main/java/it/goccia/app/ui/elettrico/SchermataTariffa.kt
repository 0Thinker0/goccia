package it.goccia.app.ui.elettrico

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Abitudine
import it.goccia.app.dati.ClasseRicarica
import it.goccia.app.dati.ModoTariffa
import it.goccia.app.dati.TariffaCasa
import it.goccia.app.dati.TariffeColonnine
import it.goccia.app.logica.Elettrico
import it.goccia.app.logica.FonteTariffa
import it.goccia.app.logica.Formati
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.componenti.BarraTitolo
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.BottoneTesto
import it.goccia.app.ui.componenti.CampoTesto
import it.goccia.app.ui.componenti.Opzione
import it.goccia.app.ui.componenti.Riquadro
import it.goccia.app.ui.componenti.Scheda
import it.goccia.app.ui.componenti.Segmentato
import it.goccia.app.ui.componenti.Separatore
import it.goccia.app.ui.componenti.Stepper
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi
import kotlin.math.abs
import kotlin.math.roundToInt

private fun Double.a(passo: Double): Double = (this / passo).roundToInt() * passo

/** Tariffa di casa (prezzo, bolletta o stima, fasce, pannelli, perdite) e tariffe alle colonnine. */
@Composable
fun SchermataTariffa(vm: GocciaViewModel, onIndietro: () -> Unit) {
    val utente by vm.utente.collectAsStateWithLifecycle()
    val stime = vm.stime
    var t by remember { mutableStateOf(utente.tariffaCasa) }
    var colonnine by remember { mutableStateOf(utente.tariffeColonnine) }
    var euroBolletta by remember { mutableStateOf(t.bollettaEuro.takeIf { it > 0 }?.let { Formati.numero(it, 2) } ?: "") }
    var kwhBolletta by remember { mutableStateOf(t.bollettaKwh.takeIf { it > 0 }?.let { Formati.numero(it, 0) } ?: "") }
    val auto = utente.autoCorrente?.takeIf { it.alimentazione.ricaricabile }
    val consumo = auto?.takeIf { it.alimentazione.elettrica }?.consumo ?: 16.0
    val capacita = auto?.takeIf { it.alimentazione.elettrica }?.capienza ?: 60.0

    fun conBolletta(): TariffaCasa = t.copy(
        bollettaEuro = Formati.leggiNumero(euroBolletta)?.takeIf { it > 0 } ?: 0.0,
        bollettaKwh = Formati.leggiNumero(kwhBolletta)?.takeIf { it > 0 } ?: 0.0,
    )

    Column(Modifier.fillMaxSize().background(Colori.Sfondo).statusBarsPadding().imePadding()) {
        BarraTitolo("Tariffa di casa", onIndietro)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "Serve per calcolare quanto ti costa ricaricare l'auto a casa e confrontarlo con colonnine e carburante.",
                style = Testi.Corpo.copy(color = Colori.Testo2),
            )

            // prezzo dell'energia
            Scheda(Modifier.fillMaxWidth()) {
                Text("Quanto paghi un kWh?", style = Testi.Voce)
                Segmentato(
                    ModoTariffa.entries.map { it.etichetta },
                    ModoTariffa.entries.indexOf(t.modo),
                    { t = t.copy(modo = ModoTariffa.entries[it]) },
                    Modifier.fillMaxWidth(),
                )
                when (t.modo) {
                    ModoTariffa.PREZZO -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Opzione("Monoraria", !t.bioraria, { t = t.copy(bioraria = false) }, Modifier.weight(1f))
                            Opzione("Bioraria F1 · F23", t.bioraria, { t = t.copy(bioraria = true) }, Modifier.weight(1f))
                        }
                        if (t.bioraria) {
                            RigaPrezzo("Fascia F1", "Lun–ven, 8–19", t.f1) { t = t.copy(f1 = it) }
                            RigaPrezzo("Fascia F23", "Sere, notti, weekend e festivi", t.f23) { t = t.copy(f23 = it) }
                        } else {
                            RigaPrezzo("Prezzo al kWh", "Tutto compreso", t.prezzo) { t = t.copy(prezzo = it) }
                        }
                        Text(
                            "Energia, trasporto, oneri e IVA inclusi. In bolletta lo trovi come costo medio del kWh.",
                            style = Testi.Didascalia.copy(color = Colori.Testo2),
                        )
                    }
                    ModoTariffa.BOLLETTA -> {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            CampoTesto(euroBolletta, { euroBolletta = it }, Modifier.weight(1f), etichetta = "Totale senza quote fisse", suffisso = "€", tastiera = KeyboardType.Decimal, segnaposto = "84,20")
                            CampoTesto(kwhBolletta, { kwhBolletta = it }, Modifier.weight(1f), etichetta = "kWh consumati", suffisso = "kWh", tastiera = KeyboardType.Number, segnaposto = "320")
                        }
                        val medio = Elettrico.daBolletta(conBolletta())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Il tuo prezzo medio", style = Testi.CorpoForte, modifier = Modifier.weight(1f))
                            Text(medio?.let { Formati.euroKwh(it, 3) } ?: "—", style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"))
                        }
                        Text(
                            "Togli quota fissa e quota potenza: le paghi comunque, anche senza ricaricare l'auto.",
                            style = Testi.Didascalia.copy(color = Colori.Testo2),
                        )
                    }
                    ModoTariffa.STIMA -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Stima", style = Testi.CorpoForte, modifier = Modifier.weight(1f))
                            Text(Formati.euroKwh(stime.casa), style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"))
                        }
                        Text(
                            "${stime.notaCasa.replaceFirstChar { it.uppercase() }}. Per un calcolo preciso inserisci il tuo prezzo.",
                            style = Testi.Didascalia.copy(color = Colori.Testo2),
                        )
                    }
                }
            }

            // abitudini
            Scheda(Modifier.fillMaxWidth()) {
                Text("Quando ricarichi di solito?", style = Testi.Voce)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Abitudine.entries.forEach { ab ->
                        Opzione(ab.etichetta, t.abitudine == ab, { t = t.copy(abitudine = ab) })
                    }
                }
                if (t.abitudine == Abitudine.FOTOVOLTAICO) {
                    RigaPrezzo("Valore dell'energia dei pannelli", "Se la vendi in rete, quanto ti viene pagata", t.fotovoltaico, passo = 0.01) {
                        t = t.copy(fotovoltaico = it)
                    }
                }
                val suggerimento = when {
                    t.abitudine == Abitudine.FOTOVOLTAICO ->
                        "Con i pannelli contiamo solo quanto rinunci a guadagnare vendendo l'energia. Se ricarichi anche la sera, conta la tariffa di rete."
                    t.modo == ModoTariffa.PREZZO && t.bioraria -> {
                        val cent = abs(t.f1 - t.f23) * 100
                        if (t.f1 < t.f23) "Con la tua tariffa, di giorno (F1) paghi ${Formati.numero(cent, 1)} cent in meno al kWh: se puoi, ricarica in F1."
                        else "Con la tua tariffa, di sera e di notte (F23) paghi ${Formati.numero(cent, 1)} cent in meno al kWh."
                    }
                    else -> "Con una tariffa monoraria il prezzo è lo stesso a ogni ora: ricarica quando ti è comodo."
                }
                Text(suggerimento, style = Testi.Didascalia.copy(color = Colori.Testo2))
                Separatore()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Perdite di ricarica", style = Testi.CorpoForte)
                    Text("Di solito intorno al 10%", style = Testi.Piccolo.copy(color = Colori.Testo3))
                }
                Stepper(
                    "${t.perdite}%",
                    onMeno = { t = t.copy(perdite = (t.perdite - 5).coerceAtLeast(5)) },
                    onPiu = { t = t.copy(perdite = (t.perdite + 5).coerceAtMost(20)) },
                    descrizioneMeno = "Diminuisci le perdite",
                    descrizionePiu = "Aumenta le perdite",
                )
                Text(
                    "Parte dell'energia si disperde in calore: per mettere 20 kWh in batteria ne prelevi circa " +
                        "${Formati.numero(Elettrico.kwhDallaRete(t, 20.0), 1)} dalla rete.",
                    style = Testi.Didascalia.copy(color = Colori.Testo2),
                )
            }

            // risultato
            val provvisoria = conBolletta()
            val prezzo = Elettrico.prezzoCasa(provvisoria, stime.casa)
            val kwh = Elettrico.costoKwhCasa(provvisoria, stime.casa)
            val veloce = colonnine.dc ?: stime.dc
            Scheda(Modifier.fillMaxWidth(), sfondo = Colori.PetrolioTenue, conOmbra = false) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Costo di un kWh in batteria", style = Testi.CorpoForte)
                        Text(
                            when (prezzo.fonte) {
                                FonteTariffa.STIMA -> "Stima"
                                else -> prezzo.fonte.etichetta
                            },
                            style = Testi.Piccolo.copy(color = Colori.PetrolioScuro),
                        )
                    }
                    Text(Formati.numero(kwh, 3) + " €", style = Testi.Numero)
                }
                RigaRisultato("Dal 20% all'80% (${Formati.numero(0.6 * capacita, 0)} kWh)", Formati.euro(0.6 * capacita * kwh))
                RigaRisultato("Ogni 100 km (${Formati.numero(consumo, 0)} kWh)", Formati.euro(consumo * kwh))
                val confronto = ((kwh / veloce - 1) * 100).roundToInt()
                RigaRisultato(
                    "Rispetto a una colonnina veloce (${Formati.euroKwh(veloce)})",
                    (if (confronto <= 0) "−${-confronto}" else "+$confronto") + "%",
                )
            }

            // colonnine
            Scheda(Modifier.fillMaxWidth()) {
                Text("Tariffe alle colonnine", style = Testi.Voce)
                Text(
                    "Quelle del tuo abbonamento o della tua app di ricarica. Finché non le cambi usiamo i ${stime.notaColonnine}.",
                    style = Testi.Didascalia.copy(color = Colori.Testo2),
                )
                ClasseRicarica.entries.forEach { classe ->
                    val valore = Elettrico.tariffaColonnina(classe, colonnine, stime)
                    val mia = Elettrico.tariffaPersonale(classe, colonnine)
                    RigaPrezzo(
                        when (classe) {
                            ClasseRicarica.AC -> "Lenta (AC)"
                            ClasseRicarica.DC -> "Veloce (DC)"
                            ClasseRicarica.HPC -> "Ultraveloce (HPC)"
                        },
                        if (mia) "La tua tariffa" else "Stima",
                        valore,
                        passo = 0.01,
                    ) { colonnine = Elettrico.conTariffa(colonnine, classe, it) }
                }
                if (colonnine != TariffeColonnine()) {
                    BottoneTesto("Torna alle stime", { colonnine = TariffeColonnine() })
                }
            }
            Riquadro(Modifier.fillMaxWidth()) {
                Text(
                    "Tutto resta sul telefono. Le stime si aggiornano da sole; i valori che inserisci tu restano finché non li cambi.",
                    style = Testi.Didascalia.copy(color = Color(0xFF1F3A4A)),
                )
            }
        }
        BottonePrimario(
            "Salva tariffa",
            onClick = {
                vm.salvaTariffaCasa(conBolletta())
                vm.salvaTariffeColonnine(colonnine)
                onIndietro()
            },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            altezza = 54.dp,
            forma = RoundedCornerShape(16.dp),
        )
    }
}

@Composable
private fun RigaPrezzo(titolo: String, sottotitolo: String, valore: Double, passo: Double = 0.005, onValore: (Double) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titolo, style = Testi.CorpoForte)
            Text(sottotitolo, style = Testi.Piccolo.copy(color = Colori.Testo3))
        }
        Stepper(
            Formati.euroKwh(valore, if (passo < 0.01) 3 else 2),
            onMeno = { onValore((valore - passo).coerceAtLeast(0.0).a(passo)) },
            onPiu = { onValore((valore + passo).a(passo)) },
            modifier = Modifier.weight(1.2f),
            altezza = 46.dp,
            stileValore = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
            descrizioneMeno = "Diminuisci $titolo",
            descrizionePiu = "Aumenta $titolo",
        )
    }
}

@Composable
private fun RigaRisultato(etichetta: String, valore: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(etichetta, style = Testi.Didascalia.copy(color = Colori.TestoChip, fontWeight = FontWeight.SemiBold), modifier = Modifier.weight(1f))
        Text(valore, style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"))
    }
}
