package it.goccia.app.ui.elettrico

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import it.goccia.app.dati.Colonnina
import it.goccia.app.dati.DatiUtente
import it.goccia.app.logica.ColonninaVicina
import it.goccia.app.logica.Elettrico
import it.goccia.app.logica.FonteTariffa
import it.goccia.app.logica.Formati
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.StatoColonnine
import it.goccia.app.ui.TipoCentro
import it.goccia.app.ui.VistaColonnine
import it.goccia.app.ui.componenti.BarraLivello
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.CardScheletro
import it.goccia.app.ui.componenti.IntestazioneSezione
import it.goccia.app.ui.componenti.Scheda
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import kotlin.math.roundToInt

/** Fino a che livello conviene caricare a casa ogni giorno (oltre si stressa la batteria). */
private const val OBIETTIVO_CASA = 0.8

/** Le sezioni della Home per chi guida un'auto elettrica. */
fun LazyListScope.sezioniElettriche(
    vm: GocciaViewModel,
    utente: DatiUtente,
    vista: VistaColonnine?,
    colonnine: StatoColonnine,
    caricamento: Boolean,
    onColonnina: (Colonnina) -> Unit,
    onMappa: () -> Unit,
    onTariffa: () -> Unit,
    onViaggio: () -> Unit,
) {
    item(key = "batteria") { CardBatteria(vm, utente, onTariffa, onViaggio) }
    item(key = "titolo-colonnine") {
        val sottotitolo = when {
            vista == null -> if (caricamento) "Sto cercando la tua posizione…" else "Carico le colonnine…"
            else -> buildString {
                append(descrizioneFiltroPrese(utente.prese))
                if (utente.impostazioni.potenzaMinima > 0) append(" · da ${utente.impostazioni.potenzaMinima} kW")
                append(" · entro ${vista.zona.raggioKm} km")
            }
        }
        val titolo = when (vista?.centro?.tipo) {
            TipoCentro.COMUNE -> "Colonnine a ${vista.centro.etichetta}"
            TipoCentro.LUOGO -> "Colonnine ${vista.centro.etichetta.replaceFirstChar { it.lowercase() }}"
            else -> "Colonnine vicino a te"
        }
        IntestazioneSezione(
            titolo,
            Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 5.dp),
            sottotitolo = sottotitolo,
            azione = "Mappa",
            onAzione = onMappa,
        )
    }
    val vicine = vista?.zona?.vicine.orEmpty()
    when {
        colonnine.nonDisponibili && colonnine.tutte.isEmpty() -> item(key = "colonnine-assenti") {
            Scheda(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth()) {
                Text("Colonnine non disponibili", style = Testi.CorpoForte)
                Text(
                    "Non riusciamo a scaricare l'elenco delle colonnine: controlla la connessione. I costi di ricarica restano qui sotto.",
                    style = Testi.Didascalia.copy(color = Colori.Testo2),
                )
            }
        }
        vista == null || (vicine.isEmpty() && colonnine.caricamento) -> items(3, key = { "colonnina-scheletro$it" }) {
            CardScheletro(Modifier.padding(horizontal = 20.dp, vertical = 5.dp))
        }
        vicine.isEmpty() -> item(key = "colonnine-nessuna") {
            Scheda(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth(), onClick = onMappa) {
                Text("Nessuna colonnina compatibile entro 30 km", style = Testi.CorpoForte)
                Text(
                    "Prova a cambiare le prese o la potenza nei filtri della mappa: le colonnine arrivano da OpenStreetMap e alcune zone sono ancora poco mappate.",
                    style = Testi.Didascalia.copy(color = Colori.Testo2),
                )
            }
        }
        else -> items(scelteHome(vicine), key = { "colonnina-" + it.colonnina.id }) { v ->
            CardColonnina(
                v.colonnina,
                v.distanzaKm,
                vm.tariffaDi(v.colonnina),
                onClick = { onColonnina(v.colonnina) },
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
            )
        }
    }
    item(key = "costo-100") { CardCosto100(vm, utente, onTariffa) }
}

@Composable
private fun CardBatteria(vm: GocciaViewModel, utente: DatiUtente, onTariffa: () -> Unit, onViaggio: () -> Unit) {
    val auto = utente.autoCorrente ?: return
    val stato = vm.serbatoio(utente) ?: return
    var dialogo by remember { mutableStateOf(false) }
    val stime = vm.stime
    val prezzo = Elettrico.prezzoCasa(utente.tariffaCasa, stime.casa)
    val kwhCasa = Elettrico.costoKwhCasa(utente.tariffaCasa, stime.casa)
    val livello = stato.livello
    Scheda(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp).fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Batteria ${Formati.numero(auto.capienza, 0)} kWh", style = Testi.DidascaliaForte.copy(color = Colori.Testo3, fontWeight = FontWeight.SemiBold))
                Text("${Formati.percento(livello)} · ~${stato.autonomiaKm} km", style = Testi.Titolo)
            }
            Text("${Formati.numero(auto.consumo, 1)} kWh/100 km", style = Testi.Piccolo.copy(color = Colori.Testo3))
        }
        Box(Modifier.clickable { dialogo = true }.semantics { contentDescription = "Batteria al ${(livello * 100).roundToInt()} per cento, tocca per correggerla" }) {
            BarraLivello(livello, if (livello < 0.2) Colori.Ambra else Colori.Petrolio, tacche = null)
        }
        val fonte = when (prezzo.fonte) {
            FonteTariffa.STIMA -> "Tariffa stimata ${Formati.euroKwh(prezzo.euroKwh)} · imposta la tua"
            FonteTariffa.FOTOVOLTAICO -> "Con i pannelli: ${Formati.euroKwh(prezzo.euroKwh)} · cambia"
            else -> "La tua tariffa ${Formati.euroKwh(prezzo.euroKwh, 3)} · cambia"
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Colori.PetrolioTenue).clickable(onClick = onTariffa).padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icone.Casa, null, tint = Colori.Petrolio, modifier = Modifier.size(18.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    buildAnnotatedString {
                        if (livello < OBIETTIVO_CASA - 0.02) {
                            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("A casa, dal ${Formati.percento(livello)} all'80%:") }
                            append(" circa ${Formati.euro((OBIETTIVO_CASA - livello) * auto.capienza * kwhCasa)}")
                        } else {
                            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("Batteria carica.") }
                            append(" Un pieno a casa (dal 20% all'80%) costa circa ${Formati.euro(0.6 * auto.capienza * kwhCasa)}")
                        }
                    },
                    style = Testi.Didascalia.copy(color = Color(0xFF1F3A4A)),
                )
                Text(fonte, style = Testi.Piccolo.copy(color = Colori.TestoChip, fontWeight = FontWeight.Medium))
            }
            Icon(Icone.ChevronDestra, null, tint = Colori.Petrolio, modifier = Modifier.size(16.dp))
        }
        Text(
            if (stato.kmDaiDati) "Livello stimato dai tuoi tragitti: toccalo per correggerlo."
            else "Livello stimato con ~35 km al giorno: toccalo per correggerlo.",
            style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BottonePrimario("Pianifica un viaggio", onViaggio, Modifier.weight(1f), icona = Icone.Percorso)
            BottoneIcona(Icone.Matita, "Correggi il livello della batteria", { dialogo = true }, bordo = Colori.BordoControllo, sfondo = Colori.Superficie, forma = Forme.Pulsante)
        }
    }
    if (dialogo) {
        DialogoBatteria(livello, onAnnulla = { dialogo = false }) {
            vm.impostaLivello(auto.id, it)
            dialogo = false
        }
    }
}

@Composable
fun DialogoBatteria(iniziale: Double, onAnnulla: () -> Unit, onConferma: (Double) -> Unit) {
    var valore by remember { mutableFloatStateOf(((iniziale * 20).roundToInt() / 20.0).toFloat()) }
    AlertDialog(
        onDismissRequest = onAnnulla,
        containerColor = Colori.Superficie,
        title = { Text("Quanta batteria hai?", style = Testi.Sottosezione) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Guarda la percentuale sul cruscotto o nell'app dell'auto.", style = Testi.Didascalia.copy(color = Colori.Testo2))
                Text(Formati.percento(valore.toDouble()), style = Testi.Titolo)
                Slider(
                    value = valore,
                    onValueChange = { valore = ((it * 20).roundToInt() / 20f) },
                    valueRange = 0f..1f,
                    colors = SliderDefaults.colors(
                        thumbColor = Colori.Petrolio,
                        activeTrackColor = Colori.Petrolio,
                        inactiveTrackColor = Colori.GrigioBadge,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("0%", "25%", "50%", "75%", "100%").forEach { Text(it, style = Testi.Piccolo.copy(color = Colori.Testo3)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConferma(valore.toDouble()) }) { Text("Salva", style = Testi.Pulsante.copy(color = Colori.Petrolio)) } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla", style = Testi.Pulsante.copy(color = Colori.Testo2)) } },
    )
}

@Composable
private fun CardCosto100(vm: GocciaViewModel, utente: DatiUtente, onTariffa: () -> Unit) {
    val auto = utente.autoCorrente ?: return
    val stime = vm.stime
    val casa = Elettrico.costoPer100(auto.consumo, Elettrico.costoKwhCasa(utente.tariffaCasa, stime.casa))
    val tariffaDc = utente.tariffeColonnine.dc ?: stime.dc
    val colonnina = Elettrico.costoPer100(auto.consumo, tariffaDc)
    val carburante = vm.confrontoCarburante(utente, 100.0)
    val massimo = listOfNotNull(casa, colonnina, carburante?.costo).maxOrNull() ?: 1.0
    Scheda(Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp).fillMaxWidth(), onClick = onTariffa) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("Costo per 100 km", style = Testi.Voce, modifier = Modifier.weight(1f))
            Text("perdite di ricarica incluse", style = Testi.Piccolo.copy(color = Colori.Testo3))
        }
        RigaConfronto("Ricarica a casa", casa, massimo)
        RigaConfronto("Colonnina veloce", colonnina, massimo)
        if (carburante != null) RigaConfronto(carburante.etichetta, carburante.costo, massimo, GrigioCarburante)
        val risparmio = carburante?.let { it.costo - casa }
        Text(
            buildString {
                append("Con ${Formati.numero(auto.consumo, 1)} kWh ogni 100 km")
                if (utente.tariffeColonnine.dc == null) append(", colonnina a ${Formati.euroKwh(tariffaDc)} (media a consumo)")
                if (risparmio != null && risparmio > 0) append(". Ricaricando a casa risparmi circa ${Formati.euro(risparmio)} ogni 100 km")
                append(".")
            },
            style = Testi.Didascalia.copy(color = Colori.Testo2),
        )
    }
}

/** Le due colonnine piu vicine e, se non c'e gia, la veloce (in continua) piu vicina. */
private fun scelteHome(vicine: List<ColonninaVicina>): List<ColonninaVicina> {
    val prime = vicine.take(2)
    val veloce = vicine.firstOrNull { it.colonnina.continua }
    val scelte = if (veloce != null && veloce !in prime) prime + veloce else vicine.take(3)
    return scelte.sortedBy { it.distanzaKm }
}
