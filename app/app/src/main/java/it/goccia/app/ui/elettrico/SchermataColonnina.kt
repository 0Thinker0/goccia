package it.goccia.app.ui.elettrico

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Colonnina
import it.goccia.app.dati.TariffeColonnine
import it.goccia.app.logica.Elettrico
import it.goccia.app.logica.Erogazione
import it.goccia.app.logica.FonteTariffa
import it.goccia.app.logica.Formati
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.componenti.Badge
import it.goccia.app.ui.componenti.BarraTitolo
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.BottoneTesto
import it.goccia.app.ui.componenti.Opzione
import it.goccia.app.ui.componenti.Riquadro
import it.goccia.app.ui.componenti.Scheda
import it.goccia.app.ui.componenti.Separatore
import it.goccia.app.ui.componenti.Stepper
import it.goccia.app.ui.condividi
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.naviga
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi
import kotlin.math.roundToInt

private val OBIETTIVI = listOf(0.6, 0.8, 1.0)
private const val DA = 0.2

@Composable
fun SchermataColonnina(vm: GocciaViewModel, id: String, onIndietro: () -> Unit, onTariffa: () -> Unit) {
    val context = LocalContext.current
    val stato by vm.colonnine.collectAsStateWithLifecycle()
    val utente by vm.utente.collectAsStateWithLifecycle()
    val c = stato.tutte.firstOrNull { it.id == id }
    if (c == null) {
        Column(Modifier.fillMaxSize().background(Colori.Sfondo).statusBarsPadding()) {
            BarraTitolo(null, onIndietro)
            Text(
                "Questa colonnina non è più nell'elenco scaricato. Torna indietro e riprova dalla mappa.",
                style = Testi.Corpo.copy(color = Colori.Testo2),
                modifier = Modifier.padding(20.dp),
            )
        }
        return
    }
    val auto = utente.autoCorrente?.takeIf { it.alimentazione.elettrica }
    val stime = vm.stime
    val tariffa = vm.tariffaDi(c)
    val personale = Elettrico.tariffaPersonale(c.classe, utente.tariffeColonnine)
    val distanza = vm.distanzaDalCentro(c.coordinate)
    var obiettivo by rememberSaveable { mutableIntStateOf(1) }

    Column(Modifier.fillMaxSize().background(Colori.Sfondo)) {
        BarraTitolo(null, onIndietro, Modifier.statusBarsPadding()) {
            BottoneIcona(Icone.Condividi, "Condividi", {
                condividi(
                    context,
                    "${titoloColonnina(c)}\n${potenzaColonnina(c)} · ${c.descrizionePrese}\n" +
                        "https://www.google.com/maps/search/?api=1&query=${c.lat},${c.lon}\n\nVisto con Goccia",
                )
            }, dimensioneIcona = 21.dp)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // intestazione
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                IconaColonnina(c, 56)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(c.titolo, style = Testi.Titolo)
                    val dove = listOfNotNull(c.indirizzo, distanza?.let { Formati.km(it) }).joinToString(" · ")
                    if (dove.isNotBlank()) Text(dove, style = Testi.Didascalia.copy(color = Colori.Testo2))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Badge(
                    c.kw?.let { (if (c.potenzaStimata) "Fino a ~" else "Fino a ") + Formati.kw(it) } ?: "Potenza non indicata",
                    Colori.PetrolioChiaro, Colori.PetrolioScuro, icona = Icone.Fulmine,
                    padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                )
                if (c.h24) Badge("Aperta 24h", Colori.GrigioBadge, Colori.TestoChip, padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp))
                if (c.gratuita) Badge("Gratuita", Colori.VerdeChiaro, Colori.VerdeTesto, padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp))
                if (c.soloClienti) Badge("Solo clienti", Colori.AmbraChiaro, Colori.AmbraTesto, padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp))
            }
            if (c.operatore != null && c.operatore != c.nome) {
                Text("Operatore: ${c.operatore}", style = Testi.Didascalia.copy(color = Colori.Testo2, fontWeight = FontWeight.SemiBold))
            }
            c.orari?.let { Text("Orari indicati: $it", style = Testi.Didascalia.copy(color = Colori.Testo2)) }

            // prese
            Scheda(Modifier.fillMaxWidth(), spazio = 0.dp, padding = PaddingValues(horizontal = 18.dp, vertical = 6.dp)) {
                if (c.connettori.isEmpty()) {
                    Text(
                        "Su OpenStreetMap non sono ancora indicate le prese di questa colonnina.",
                        style = Testi.Didascalia.copy(color = Colori.Testo2),
                        modifier = Modifier.padding(vertical = 10.dp),
                    )
                }
                c.connettori.forEachIndexed { i, con ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(con.presa.etichetta, style = Testi.CorpoForte)
                            Text(con.presa.corrente, style = Testi.Piccolo.copy(color = Colori.Testo3))
                        }
                        Text(
                            (if (c.potenzaStimata) "~" else "") + Formati.kw(con.kw) + " × ${con.numero}",
                            style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
                        )
                    }
                    if (i < c.connettori.lastIndex) Separatore()
                }
            }
            Riquadro(Modifier.fillMaxWidth(), icona = Icone.Info) {
                Text(
                    "La disponibilità in tempo reale non è pubblica: controllala nell'app con cui paghi." +
                        if (c.potenzaStimata) " La potenza è dedotta dal tipo di presa." else "",
                    style = Testi.Didascalia.copy(color = Color(0xFF1F3A4A)),
                )
            }

            // tariffa
            Scheda(Modifier.fillMaxWidth()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("La tua tariffa · ricarica ${c.classe.sigla}", style = Testi.Voce)
                    Text(
                        if (personale) "Quella del tuo abbonamento o della tua app di ricarica."
                        else "Stima: ${stime.notaColonnine}. Metti quella del tuo abbonamento o della tua app.",
                        style = Testi.Didascalia.copy(color = Colori.Testo2),
                    )
                }
                Stepper(
                    Formati.euroKwh(tariffa),
                    onMeno = { vm.salvaTariffeColonnine(Elettrico.conTariffa(utente.tariffeColonnine, c.classe, (tariffa - 0.01).coerceAtLeast(0.0).arrotonda())) },
                    onPiu = { vm.salvaTariffeColonnine(Elettrico.conTariffa(utente.tariffeColonnine, c.classe, (tariffa + 0.01).arrotonda())) },
                    descrizioneMeno = "Abbassa la tariffa di un centesimo",
                    descrizionePiu = "Alza la tariffa di un centesimo",
                )
                if (personale) {
                    BottoneTesto("Torna alla stima (${Formati.euroKwh(Elettrico.tariffaColonnina(c.classe, TariffeColonnine(), stime))})", {
                        vm.salvaTariffeColonnine(Elettrico.conTariffa(utente.tariffeColonnine, c.classe, null))
                    })
                }
            }

            // quanto costa qui
            if (auto != null) {
                val erogazione = Elettrico.erogazione(c, utente.prese, auto.acKw, auto.dcKw)
                CalcoloRicarica(vm, c, auto.capienza, auto.consumo, erogazione, tariffa, obiettivo, { obiettivo = it }, onTariffa)
            } else {
                Riquadro(Modifier.fillMaxWidth()) {
                    Text(
                        "Aggiungi un'auto elettrica nel profilo: ti diremo quanto costa e quanto dura una ricarica qui.",
                        style = Testi.Didascalia.copy(color = Color(0xFF1F3A4A)),
                    )
                }
            }
            Text(
                "Colonnine: © OpenStreetMap contributors, licenza ODbL. Tariffe: quelle che inserisci tu, altrimenti ${stime.notaColonnine}.",
                style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
        Column(Modifier.fillMaxWidth().background(Colori.Superficie)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Colori.Bordo))
            BottonePrimario(
                "Naviga",
                { naviga(context, c.lat, c.lon, c.titolo, utente.impostazioni.navigazione) },
                Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
                icona = Icone.Naviga,
                altezza = 54.dp,
                forma = RoundedCornerShape(16.dp),
            )
        }
    }
}

private fun Double.arrotonda(): Double = (this * 100).roundToInt() / 100.0

@Composable
private fun CalcoloRicarica(
    vm: GocciaViewModel,
    c: Colonnina,
    capacita: Double,
    consumo: Double,
    erogazione: Erogazione?,
    tariffa: Double,
    obiettivo: Int,
    onObiettivo: (Int) -> Unit,
    onTariffa: () -> Unit,
) {
    val utente = vm.utente.value
    val stime = vm.stime
    val a = OBIETTIVI[obiettivo]
    val kwh = (a - DA) * capacita
    val costoQui = if (c.gratuita) 0.0 else kwh * tariffa
    val kwhCasa = Elettrico.costoKwhCasa(utente.tariffaCasa, stime.casa)
    val prezzoCasa = Elettrico.prezzoCasa(utente.tariffaCasa, stime.casa)
    Scheda(Modifier.fillMaxWidth()) {
        Text("Quanto costa ricaricare qui", style = Testi.Voce)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Dal 20% fino al", style = Testi.Chip.copy(color = Colori.TestoChip))
            OBIETTIVI.forEachIndexed { i, o ->
                Opzione(Formati.percento(o), i == obiettivo, { onObiettivo(i) }, Modifier.weight(1f))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Riquadrino("Costo qui", Formati.euro(costoQui), Modifier.weight(1f))
            Riquadrino("Tempo", erogazione?.let { Formati.durata(Elettrico.minutiRicarica(capacita, DA, a, it)) } ?: "—", Modifier.weight(1f))
            Riquadrino("A casa", Formati.euro(kwh * kwhCasa), Modifier.weight(1f))
        }
        Text(
            buildString {
                append("${Formati.numero(kwh, 0)} kWh in batteria")
                if (a > Elettrico.SOGLIA_LENTA) append(", oltre l'80% la ricarica rallenta")
                append(". ")
                when {
                    erogazione != null -> append(
                        if (erogazione.continua) "In corrente continua a ${Formati.kw(erogazione.kw)} con la tua auto. "
                        else "In alternata a ${Formati.kw(erogazione.kw)} (caricatore di bordo). ",
                    )
                    c.kw == null && c.connettori.isEmpty() -> append("Potenza e prese non sono indicate, quindi il tempo non si può stimare. ")
                    else -> append("Nessuna presa compatibile con i filtri scelti. ")
                }
                append(
                    when (prezzoCasa.fonte) {
                        FonteTariffa.STIMA -> "A casa: tariffa stimata ${Formati.euroKwh(prezzoCasa.euroKwh)}"
                        FonteTariffa.FOTOVOLTAICO -> "A casa: energia dei pannelli a ${Formati.euroKwh(prezzoCasa.euroKwh)}"
                        else -> "A casa: la tua tariffa ${Formati.euroKwh(prezzoCasa.euroKwh, 3)}"
                    },
                )
                append(", perdite di ricarica incluse.")
            },
            style = Testi.Didascalia.copy(color = Colori.Testo2),
        )
        BottoneTesto("Imposta la tariffa di casa", onTariffa, Modifier.padding(start = 0.dp))
        Separatore()
        Text("Costo per 100 km", style = Testi.CorpoForte)
        val casa100 = Elettrico.costoPer100(consumo, kwhCasa)
        val qui100 = Elettrico.costoPer100(consumo, if (c.gratuita) 0.0 else tariffa)
        val carburante = vm.confrontoCarburante(utente, 100.0)
        val massimo = listOfNotNull(casa100, qui100, carburante?.costo).maxOrNull() ?: 1.0
        RigaConfronto("Ricarica a casa", casa100, massimo)
        RigaConfronto("Questa colonnina", qui100, massimo)
        if (carburante != null) RigaConfronto(carburante.etichetta, carburante.costo, massimo, GrigioCarburante)
    }
}

@Composable
private fun Riquadrino(titolo: String, valore: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Colori.Grigio).padding(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(titolo, style = Testi.Piccolo.copy(color = Colori.Testo3), maxLines = 1)
        Text(valore, style = Testi.Numero.copy(fontSize = 19.sp), maxLines = 1)
    }
}
