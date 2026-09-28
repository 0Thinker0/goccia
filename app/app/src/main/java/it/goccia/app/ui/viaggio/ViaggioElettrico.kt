package it.goccia.app.ui.viaggio

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Colonnina
import it.goccia.app.dati.Coordinate
import it.goccia.app.logica.ColonninaSulPercorso
import it.goccia.app.logica.Elettrico
import it.goccia.app.logica.Formati
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.StatoViaggio
import it.goccia.app.ui.apriLink
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.Riquadro
import it.goccia.app.ui.elettrico.potenzaColonnina
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra
import kotlin.math.roundToInt

private fun pinElettrici(s: StatoViaggio.ProntoElettrico): List<PinViaggio> {
    val scelte = s.piano.soste.map { it.punto.colonnina.id }.toSet()
    val mostrate = (s.piano.soste.map { it.punto } + s.piano.alternative).distinctBy { it.colonnina.id }
    return mostrate.map { p ->
        val scelta = p.colonnina.id in scelte
        // senza potenza nota il segnaposto mostra solo il fulmine
        val testo = p.colonnina.kw?.let { Formati.kw(it) } ?: ""
        if (scelta) {
            PinViaggio(p.colonnina.lat, p.colonnina.lon, testo, Colori.Petrolio.toArgb(), true, PinViaggio.SCELTO)
        } else {
            PinViaggio(
                p.colonnina.lat, p.colonnina.lon, testo, android.graphics.Color.WHITE, false, -(p.colonnina.kw ?: 0.0),
                coloreTesto = Colori.PetrolioScuro.toArgb(), coloreBordo = Colori.Petrolio.toArgb(),
            )
        }
    }
}

@Composable
internal fun RisultatoElettrico(vm: GocciaViewModel, s: StatoViaggio.ProntoElettrico, onColonnina: (Colonnina) -> Unit) {
    val context = LocalContext.current
    val utente by vm.utente.collectAsStateWithLifecycle()
    val piano = s.piano
    val auto = s.auto
    val kmh = auto.capienza

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxWidth().weight(0.36f)) {
            MappaViaggio(s.campioni, pinElettrici(s))
            BottoneIcona(
                Icone.Indietro, "Indietro", { vm.chiudiViaggio() },
                Modifier.statusBarsPadding().padding(12.dp).ombra(CircleShape, 4.dp),
                sfondo = Colori.Superficie,
            )
        }
        Column(
            Modifier
                .weight(0.64f)
                .fillMaxWidth()
                .ombra(Forme.Foglio, 8.dp)
                .clip(Forme.Foglio)
                .background(Colori.Superficie)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("${s.partenza.nome} → ${s.arrivo.nome}", style = Testi.Titolo, maxLines = 2)
                Text(
                    "${Formati.numero(s.percorso.distanzaKm, 0)} km · ${auto.nome} · ${Formati.numero(kmh, 0)} kWh",
                    style = Testi.Didascalia.copy(color = Colori.Testo2, fontWeight = FontWeight.SemiBold),
                )
            }

            // riassunto
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Riepilogo(
                    "Soste di ricarica",
                    if (piano.soste.isEmpty()) "Nessuna" else "${piano.soste.size} · ${Formati.durata(piano.minutiSoste)}",
                    Modifier.weight(1f),
                )
                Riepilogo("Tempo totale", Formati.durata(s.percorso.durataMin + piano.minutiSoste), Modifier.weight(1f))
                Riepilogo(
                    "All'arrivo",
                    if (piano.arrivo >= 0) Formati.percento(piano.arrivo) else "—",
                    Modifier.weight(0.8f),
                )
            }

            if (!piano.completo) {
                Riquadro(Modifier.fillMaxWidth(), sfondo = Colori.RossoChiaro, icona = Icone.Attenzione, coloreIcona = Colori.Rosso) {
                    Text(
                        buildString {
                            append("Con il ${Formati.percento(piano.partenza)} non troviamo colonnine veloci compatibili entro l'autonomia")
                            piano.kmCritico?.let { append(": resteresti senza carica verso il km ${it.roundToInt()}") }
                            append(". Parti più carico, accetta una deviazione maggiore o controlla le prese nei filtri della mappa.")
                        },
                        style = Testi.Didascalia.copy(color = Colori.Rosso),
                    )
                }
            } else if (piano.soste.isEmpty()) {
                Riquadro(Modifier.fillMaxWidth(), sfondo = Colori.VerdeChiaro, icona = Icone.Spunta, coloreIcona = Colori.VerdeTesto) {
                    Text(
                        "Arrivi senza ricaricare: a destinazione ti resta il ${Formati.percento(piano.arrivo)} " +
                            "(${Formati.numero(piano.arrivo * kmh, 1)} kWh).",
                        style = Testi.Testo14.copy(color = Colori.VerdeScuro, fontWeight = FontWeight.SemiBold),
                    )
                }
            }

            // tappe
            Column(Modifier.fillMaxWidth()) {
                PassoViaggio(
                    titolo = "Partenza · ${s.partenza.nome}",
                    dettaglio = "${Formati.numero(piano.partenza * kmh, 0)} kWh disponibili",
                    livello = Formati.percento(piano.partenza),
                    colore = Colori.Petrolio,
                    ultima = false,
                )
                piano.soste.forEach { sosta ->
                    val c = sosta.punto.colonnina
                    val mia = Elettrico.tariffaPersonale(c.classe, utente.tariffeColonnine)
                    PassoViaggio(
                        titolo = "km ${sosta.punto.km.roundToInt()} · ${c.titolo}",
                        dettaglio = listOfNotNull(
                            potenzaColonnina(c),
                            c.descrizionePrese,
                            if (sosta.punto.deviazioneMin == 0) "sulla strada" else "${Formati.km(sosta.punto.distanzaKm)} dal percorso",
                        ).joinToString(" · "),
                        livello = null,
                        colore = Colori.Petrolio,
                        ultima = false,
                        extra = "Arrivi al ${Formati.percento(sosta.arrivo)} e ricarichi al ${Formati.percento(sosta.ripartenza)}: " +
                            "${Formati.durata(sosta.minuti)} · ~${Formati.euro(if (c.gratuita) 0.0 else sosta.costo)}" +
                            if (c.gratuita) " (gratuita)" else " (${if (mia) "tua tariffa" else "stima"} ${Formati.euroKwh(sosta.tariffa)})",
                        onClick = { onColonnina(c) },
                    )
                }
                PassoViaggio(
                    titolo = "Arrivo · ${s.arrivo.nome}",
                    dettaglio = "km ${s.percorso.distanzaKm.roundToInt()} · " +
                        if (piano.arrivo >= 0) "${Formati.numero(piano.arrivo * kmh, 1)} kWh residui" else "batteria insufficiente",
                    livello = if (piano.arrivo >= 0) Formati.percento(piano.arrivo) else "—",
                    colore = Colori.Notifica,
                    ultima = true,
                )
            }

            // costi: tutta l'energia del viaggio, sia quella caricata a casa prima di partire sia quella
            // delle soste, cosi il confronto con il carburante e alla pari
            val kwhCasa = Elettrico.costoKwhCasa(utente.tariffaCasa, vm.stime.casa)
            val costi = Elettrico.costoViaggio(s.percorso.distanzaKm, auto.consumo, piano, kwhCasa)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Costo(
                    "Costo del viaggio",
                    "~${Formati.euro(costi.totale)}",
                    if (piano.soste.isEmpty()) "energia caricata a casa"
                    else "${Formati.euro(costi.soste)} in viaggio + ${Formati.euro(costi.casa)} a casa",
                    Modifier.weight(1f),
                    evidenziato = true,
                )
                s.confronto?.let { c ->
                    Costo(
                        "A carburante",
                        "~${Formati.euro(c.costo)}",
                        "${c.etichetta} · ${Formati.numero(c.litri, 1)} ${c.unita}",
                        Modifier.weight(1f),
                    )
                }
            }
            Text(
                buildString {
                    append("Il viaggio consuma circa ${Formati.numero(costi.kwhViaggio, 0)} kWh: ")
                    append("${Formati.numero(costi.kwhCasa, 0)} caricati a casa a ${Formati.euroKwh(kwhCasa)}")
                    if (piano.soste.isNotEmpty()) {
                        val tariffe = piano.soste.map { it.tariffa }.distinct()
                        append(" e ${Formati.numero(costi.kwhSoste, 0)} alle colonnine")
                        if (tariffe.size == 1) append(" a ${Formati.euroKwh(tariffe.single())}")
                    }
                    append(".")
                    s.confronto?.let { c ->
                        val risparmio = c.costo - costi.totale
                        if (risparmio > 0.5) append(" Rispetto al carburante risparmi circa ${Formati.euro(risparmio)}.")
                    }
                },
                style = Testi.Didascalia.copy(color = Colori.Testo2),
            )

            if (piano.alternative.isNotEmpty()) {
                Text("Altre colonnine veloci sul percorso", style = Testi.DidascaliaForte.copy(color = Colori.Testo3))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).border(1.dp, Colori.Bordo, RoundedCornerShape(16.dp))) {
                    piano.alternative.forEachIndexed { i, a ->
                        RigaColonnina(a) { onColonnina(a.colonnina) }
                        if (i < piano.alternative.lastIndex) Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp).height(1.dp).background(Colori.Divisore))
                    }
                }
            } else if (s.lungo.isEmpty()) {
                Text(
                    "Non abbiamo trovato colonnine lungo questo percorso: OpenStreetMap potrebbe non averle ancora mappate.",
                    style = Testi.Didascalia.copy(color = Colori.Testo2),
                )
            }

            BottonePrimario(
                "Avvia con Google Maps",
                { avviaConSoste(context, s.partenza.coordinate, s.arrivo.coordinate, piano.soste.map { it.punto.colonnina.coordinate }) },
                Modifier.fillMaxWidth(),
                icona = Icone.Naviga,
                altezza = 52.dp,
            )
            Text(
                "Stima con un consumo costante di ${Formati.numero(auto.consumo, 1)} kWh/100 km: in autostrada, con il freddo o con il clima acceso " +
                    "si consuma di più. Controlla la disponibilità delle colonnine nell'app con cui paghi.",
                style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
            )
        }
    }
}

private fun avviaConSoste(context: Context, da: Coordinate, a: Coordinate, soste: List<Coordinate>) {
    val url = StringBuilder("https://www.google.com/maps/dir/?api=1&travelmode=driving")
        .append("&origin=${da.lat},${da.lon}")
        .append("&destination=${a.lat},${a.lon}")
    if (soste.isNotEmpty()) url.append("&waypoints=").append(Uri.encode(soste.joinToString("|") { "${it.lat},${it.lon}" }))
    apriLink(context, url.toString())
}

@Composable
private fun Riepilogo(titolo: String, valore: String, modifier: Modifier = Modifier) {
    Column(
        modifier.clip(RoundedCornerShape(14.dp)).background(Colori.Grigio).padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(titolo, style = Testi.Piccolo.copy(color = Colori.Testo3), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(valore, style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1)
    }
}

/** Una tappa della linea del tempo: pallino, linea verso la tappa successiva, testi. */
@Composable
private fun PassoViaggio(
    titolo: String,
    dettaglio: String,
    livello: String?,
    colore: Color,
    ultima: Boolean,
    extra: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.width(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.padding(top = 4.dp).size(14.dp).clip(CircleShape).background(colore))
            if (!ultima) Box(Modifier.width(2.dp).height(if (extra != null) 64.dp else 34.dp).background(Colori.Bordo))
        }
        Column(Modifier.weight(1f).padding(bottom = if (ultima) 0.dp else 10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titolo, style = Testi.CorpoForte, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dettaglio, style = Testi.Didascalia.copy(color = Colori.Testo3), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (extra != null) {
                Text(
                    extra,
                    style = Testi.Didascalia.copy(color = Colori.PetrolioScuro, fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.padding(top = 4.dp).clip(RoundedCornerShape(10.dp)).background(Colori.PetrolioTenue).padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
        }
        if (livello != null) Text(livello, style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold))
    }
}

@Composable
private fun Costo(titolo: String, valore: String, nota: String, modifier: Modifier = Modifier, evidenziato: Boolean = false) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (evidenziato) Colori.PetrolioTenue else Colori.Grigio)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(titolo, style = Testi.Piccolo.copy(color = Colori.Testo3), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(valore, style = Testi.Numero)
        Text(nota, style = Testi.Piccolo.copy(color = Colori.Testo2, fontWeight = FontWeight.Medium), maxLines = 2)
    }
}

@Composable
private fun RigaColonnina(a: ColonninaSulPercorso, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(a.colonnina.titolo, style = Testi.Chip.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "km ${a.km.roundToInt()} · " + if (a.deviazioneMin == 0) "sulla strada" else "+${a.deviazioneMin} min",
                style = Testi.Piccolo.copy(color = Colori.Testo3),
            )
        }
        Text(potenzaColonnina(a.colonnina), style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"))
    }
}
