package it.goccia.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.DatiUtente
import it.goccia.app.dati.Distributore
import it.goccia.app.logica.Consiglio
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Statistiche
import it.goccia.app.logica.Tendenza
import it.goccia.app.logica.Urgenza
import it.goccia.app.logica.meseDi
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.StatoDati
import it.goccia.app.ui.TipoCentro
import it.goccia.app.ui.VistaZona
import it.goccia.app.ui.componenti.Badge
import it.goccia.app.ui.componenti.BarraLivello
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.CardOfferta
import it.goccia.app.ui.componenti.CardScheletro
import it.goccia.app.ui.componenti.ChipScelta
import it.goccia.app.ui.componenti.GraficoBarre
import it.goccia.app.ui.componenti.GraficoLinea
import it.goccia.app.ui.componenti.IntestazioneSezione
import it.goccia.app.ui.componenti.Scheda
import it.goccia.app.ui.componenti.metaOfferta
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.stati.BannerOffline
import it.goccia.app.ui.stati.PannelloPosizione
import it.goccia.app.ui.stati.SenzaDati
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataHome(
    vm: GocciaViewModel,
    onDistributore: (Distributore) -> Unit,
    onVediTutti: () -> Unit,
    onRifornimento: () -> Unit,
    onViaggio: () -> Unit,
    onAvvisi: () -> Unit,
    onProfilo: () -> Unit,
    onStatistiche: () -> Unit,
    onSostieni: () -> Unit,
    onNuovaAuto: () -> Unit,
    onLuogo: () -> Unit,
) {
    LaunchedEffect(Unit) { vm.avvia() }
    val dati by vm.dati.collectAsStateWithLifecycle()
    val utente by vm.utente.collectAsStateWithLifecycle()
    val statoVista by vm.vista.collectAsStateWithLifecycle()
    val vista = statoVista

    PullToRefreshBox(isRefreshing = dati.aggiornando, onRefresh = { vm.aggiorna() }, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item(key = "intestazione") { Intestazione(utente, onAvvisi) }
            item(key = "scelte") { Scelte(vm, utente, onProfilo, onNuovaAuto) }
            if (dati.offline && !dati.senzaDati) {
                item(key = "offline") {
                    BannerOffline(dati.scaricatoIl, onRiprova = { vm.aggiorna() }, modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp))
                }
            }
            when {
                dati.senzaDati -> item(key = "senza-dati") { SenzaDati(onRiprova = { vm.aggiorna() }, modifier = Modifier.padding(20.dp)) }
                dati.centro == null && !dati.caricamento && !dati.cercoPosizione -> item(key = "posizione") {
                    PannelloPosizione(vm, dati, onLuogo = onLuogo, modifier = Modifier.padding(20.dp))
                }
                else -> {
                    item(key = "auto") { CardAuto(vm, utente, vista, onRifornimento, onViaggio, onNuovaAuto) }
                    item(key = "vicini") { TitoloVicini(vista, dati, onVediTutti) }
                    val consigliati = vista?.let { Convenienza.consigliati(it.zona) }
                    if (vista == null || consigliati == null || dati.caricamento || dati.cercoPosizione) {
                        items(3, key = { "scheletro$it" }) { CardScheletro(Modifier.padding(horizontal = 20.dp, vertical = 5.dp)) }
                    } else if (consigliati.isEmpty()) {
                        item(key = "nessuno") { NessunoVicino(vista.carburante, onVediTutti) }
                    } else {
                        items(consigliati, key = { "o" + it.distributore.id }) { o ->
                            CardOfferta(
                                offerta = o,
                                carburante = vista.carburante,
                                meta = metaOfferta(o, vista.adessoMillis),
                                onClick = { onDistributore(o.distributore) },
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
            item(key = "risparmio") { CardRisparmio(utente, onStatistiche) }
            item(key = "consumi") { Consumi(vm, utente, onStatistiche) }
            item(key = "media") { CardMediaZona(dati, vista) }
            item(key = "sostieni") { InvitoSostegno(vm, utente, onSostieni) }
        }
    }
}

@Composable
private fun Intestazione(utente: DatiUtente, onAvvisi: () -> Unit) {
    val ora = LocalTime.now().hour
    val saluto = when {
        ora < 12 -> "Buongiorno"
        ora < 18 -> "Buon pomeriggio"
        else -> "Buonasera"
    }
    val novita = utente.avvisi.any { a -> a.ultimaNotificaIl?.let { System.currentTimeMillis() - it < 24 * 3_600_000L } == true }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(saluto, style = Testi.Chip.copy(color = Colori.Testo2))
            Text("Dove fai il pieno oggi?", style = Testi.TitoloSchermata)
        }
        Box {
            BottoneIcona(
                Icone.Campanella,
                if (novita) "Avvisi prezzo, novità" else "Avvisi prezzo",
                onAvvisi,
                sfondo = Colori.Superficie,
                bordo = Colori.Bordo,
                forma = RoundedCornerShape(16.dp),
            )
            if (novita) {
                Box(
                    Modifier
                        .padding(top = 11.dp, start = 27.dp)
                        .size(11.dp)
                        .clip(CircleShape)
                        .background(Colori.Superficie)
                        .padding(2.dp)
                        .clip(CircleShape)
                        .background(Colori.Notifica),
                )
            }
        }
    }
}

@Composable
private fun Scelte(vm: GocciaViewModel, utente: DatiUtente, onProfilo: () -> Unit, onNuovaAuto: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.padding(start = 20.dp, top = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val auto = utente.autoCorrente
        Box {
            Row(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(Colori.Superficie)
                    .border(1.dp, Colori.Bordo, RoundedCornerShape(22.dp))
                    .clickable { if (auto == null) onNuovaAuto() else menu = true }
                    .padding(start = 8.dp, end = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(30.dp).clip(CircleShape).background(Colori.PetrolioChiaro), contentAlignment = Alignment.Center) {
                    Icon(if (auto?.alimentazione?.elettrica == true) Icone.Fulmine else Icone.Auto, null, tint = Colori.Petrolio, modifier = Modifier.size(17.dp))
                }
                if (auto == null) {
                    Text("Aggiungi la tua auto", style = Testi.ChipAttivo)
                } else {
                    Text(auto.nome, style = Testi.ChipAttivo)
                    Text("· cambia auto", style = Testi.Chip.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium))
                    Icon(Icone.ChevronGiu, null, tint = Colori.Testo3, modifier = Modifier.size(16.dp))
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                utente.auto.forEach { a ->
                    DropdownMenuItem(
                        text = { Text(a.nome + " · " + a.alimentazione.etichetta, style = Testi.Chip) },
                        onClick = {
                            menu = false
                            vm.scegliAuto(a.id)
                        },
                        leadingIcon = {
                            if (a.id == auto?.id) Icon(Icone.Spunta, null, tint = Colori.Petrolio, modifier = Modifier.size(18.dp))
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Aggiungi un'auto", style = Testi.Chip) },
                    onClick = {
                        menu = false
                        onNuovaAuto()
                    },
                    leadingIcon = { Icon(Icone.Piu, null, tint = Colori.Petrolio, modifier = Modifier.size(18.dp)) },
                )
                DropdownMenuItem(
                    text = { Text("Il tuo garage", style = Testi.Chip) },
                    onClick = {
                        menu = false
                        onProfilo()
                    },
                    leadingIcon = { Icon(Icone.Persona, null, tint = Colori.Petrolio, modifier = Modifier.size(18.dp)) },
                )
            }
        }
        ChipCarburanti(vm, utente, Modifier.fillMaxWidth())
    }
}

/** I chip dei carburanti: toccando quello attivo si passa da self a servito. */
@Composable
fun ChipCarburanti(vm: GocciaViewModel, utente: DatiUtente, modifier: Modifier = Modifier) {
    val attivo = utente.carburante
    val self = utente.impostazioni.preferisciSelf
    Row(
        modifier.horizontalScroll(rememberScrollState()).padding(end = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        val ordine = listOf(attivo) + Carburante.entries.filter { it != attivo }
        ordine.forEach { c ->
            val etichetta = if (c == attivo && c.haSelf) "${c.etichetta} · ${if (self) "Self" else "Servito"}" else c.etichetta
            ChipScelta(etichetta, c == attivo, conSpunta = true, onClick = {
                if (c == attivo) {
                    if (c.haSelf) vm.impostazioni { it.copy(preferisciSelf = !it.preferisciSelf) }
                } else {
                    vm.scegliCarburante(c)
                }
            })
        }
    }
}

@Composable
private fun CardAuto(
    vm: GocciaViewModel,
    utente: DatiUtente,
    vista: VistaZona?,
    onRifornimento: () -> Unit,
    onViaggio: () -> Unit,
    onNuovaAuto: () -> Unit,
) {
    val auto = utente.autoCorrente
    val modificatore = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp)
    if (auto == null) {
        Scheda(modificatore.fillMaxWidth()) {
            Text("Aggiungi la tua auto", style = Testi.Sottosezione)
            Text(
                "Con serbatoio e consumo ti diciamo quando fare il pieno, quanta autonomia ti resta e quanto spendi davvero.",
                style = Testi.Didascalia.copy(color = Colori.Testo2),
            )
            BottonePrimario("Aggiungi auto", onNuovaAuto, Modifier.fillMaxWidth(), icona = Icone.Piu)
        }
        return
    }
    if (auto.alimentazione.elettrica) {
        Scheda(modificatore.fillMaxWidth()) {
            Text("${auto.nome} · elettrica · ${Formati.numero(auto.capienza, 0)} kWh", style = Testi.DidascaliaForte.copy(color = Colori.Testo3))
            Text("Ricarica e colonnine in arrivo", style = Testi.Titolo)
            Text(
                "Stiamo preparando la mappa delle colonnine e il costo della ricarica di casa. Intanto puoi usare Goccia per i prezzi dei carburanti.",
                style = Testi.Didascalia.copy(color = Colori.Testo2),
            )
        }
        return
    }
    val serbatoio = vm.serbatoio(utente) ?: return
    var dialogo by remember { mutableStateOf(false) }
    val riserva = serbatoio.livello <= Consiglio.RISERVA
    val (sfondoBadge, testoBadge) = when {
        serbatoio.livello < 0.3 -> Colori.AmbraChiaro to Colori.AmbraTesto
        else -> Colori.VerdeChiaro to Colori.VerdeTesto
    }
    Scheda(modificatore.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${auto.nome} · ${auto.alimentazione.etichetta} · ${Formati.numero(auto.capienza, 0)} ${auto.alimentazione.unitaCapienza}",
                    style = Testi.DidascaliaForte.copy(color = Colori.Testo3, fontWeight = FontWeight.SemiBold),
                )
                Text("Autonomia ~${serbatoio.autonomiaKm} km", style = Testi.Titolo)
            }
            Badge(
                "Serbatoio ${Consiglio.quarti(serbatoio.livello)}",
                if (riserva) Colori.RossoChiaro else sfondoBadge,
                if (riserva) Colori.Rosso else testoBadge,
                Modifier.clip(Forme.Pillola).clickable { dialogo = true },
                padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Box(Modifier.clickable { dialogo = true }.semantics { contentDescription = "Livello stimato, tocca per correggerlo" }) {
            BarraLivello(serbatoio.livello, if (serbatoio.livello < 0.3) Colori.Ambra else Colori.Petrolio)
        }
        val migliore = vista?.let { Convenienza.consigliati(it.zona, 1).firstOrNull() }
        val consiglio = Consiglio.consiglio(serbatoio, migliore, tendenzaZona(vm, vista), LocalDate.now())
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(
                if (consiglio.urgenza == Urgenza.RISERVA) Colori.RossoChiaro else Colori.PetrolioTenue,
            ).padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(if (consiglio.urgenza == Urgenza.RISERVA) Colori.Rosso else Colori.Petrolio),
                contentAlignment = Alignment.Center,
            ) { Icon(Icone.Pompa, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(consiglio.titolo, style = Testi.CorpoForte)
                if (consiglio.testo.isNotBlank()) Text(consiglio.testo, style = Testi.Didascalia.copy(color = Colori.TestoChip))
                Text(
                    if (serbatoio.kmDaiDati) "Stima dai tuoi rifornimenti: ~${Formati.numero(serbatoio.kmGiornalieri, 0)} km al giorno."
                    else "Livello stimato con ~35 km al giorno: toccalo per correggerlo.",
                    style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BottonePrimario("Registra rifornimento", onRifornimento, Modifier.weight(1f), icona = Icone.Piu)
            BottoneIcona(Icone.Percorso, "Pianifica un viaggio", onViaggio, bordo = Colori.BordoControllo, sfondo = Colori.Superficie, forma = Forme.Pulsante)
        }
    }
    if (dialogo) DialogoLivello(serbatoio.livello, onAnnulla = { dialogo = false }) {
        vm.impostaLivello(auto.id, it)
        dialogo = false
    }
}

private fun tendenzaZona(vm: GocciaViewModel, vista: VistaZona?): Tendenza? {
    val v = vista ?: return null
    val storico = vm.dati.value.storicoZona ?: return null
    val serie = storico.medie[v.carburante] ?: return null
    return Consiglio.tendenza(if (v.self) serie.self else serie.servito)?.first
}

@Composable
private fun DialogoLivello(iniziale: Double, onAnnulla: () -> Unit, onConferma: (Double) -> Unit) {
    var valore by remember { mutableFloatStateOf(iniziale.toFloat()) }
    AlertDialog(
        onDismissRequest = onAnnulla,
        containerColor = Colori.Superficie,
        title = { Text("Quanto carburante hai?", style = Testi.Sottosezione) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Basta una stima a occhio: guarda la lancetta del serbatoio.",
                    style = Testi.Didascalia.copy(color = Colori.Testo2),
                )
                Text(Consiglio.quarti(valore.toDouble()), style = Testi.Titolo)
                Slider(
                    value = valore,
                    onValueChange = { valore = it },
                    valueRange = 0f..1f,
                    steps = 7,
                    colors = SliderDefaults.colors(
                        thumbColor = Colori.Petrolio,
                        activeTrackColor = Colori.Petrolio,
                        inactiveTrackColor = Colori.GrigioBadge,
                        activeTickColor = Colori.PetrolioChiaro,
                        inactiveTickColor = Colori.InterruttoreSpento,
                    ),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("R", "1/4", "1/2", "3/4", "Pieno").forEach { Text(it, style = Testi.Piccolo.copy(color = Colori.Testo3)) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConferma(valore.toDouble()) }) { Text("Salva", style = Testi.Pulsante.copy(color = Colori.Petrolio)) } },
        dismissButton = { TextButton(onClick = onAnnulla) { Text("Annulla", style = Testi.Pulsante.copy(color = Colori.Testo2)) } },
    )
}

@Composable
private fun TitoloVicini(vista: VistaZona?, dati: StatoDati, onVediTutti: () -> Unit) {
    val titolo = when (dati.centro?.tipo) {
        TipoCentro.COMUNE -> "Più convenienti a ${dati.centro.etichetta}"
        TipoCentro.LUOGO -> "Più convenienti ${dati.centro.etichetta.replaceFirstChar { it.lowercase() }}"
        else -> "Più convenienti vicino a te"
    }
    val sottotitolo = vista?.let { v ->
        val modo = if (v.carburante.haSelf) (if (v.self) " self" else " servito") else ""
        val media = v.zona.media?.let { " · media ${Formati.prezzo(it)} ${v.carburante.unita}" } ?: ""
        "${v.carburante.etichetta}$modo · entro ${v.zona.raggioUsatoKm} km$media"
    } ?: if (dati.cercoPosizione) "Sto cercando la tua posizione…" else "Carico i prezzi…"
    IntestazioneSezione(
        titolo,
        Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 5.dp),
        sottotitolo = sottotitolo,
        azione = "Vedi tutti",
        onAzione = onVediTutti,
    )
}

@Composable
private fun NessunoVicino(carburante: Carburante, onVediTutti: () -> Unit) {
    Scheda(Modifier.padding(horizontal = 20.dp, vertical = 5.dp).fillMaxWidth(), onClick = onVediTutti) {
        Text("Nessun distributore con ${carburante.etichetta.lowercase()} nel raggio di 30 km", style = Testi.CorpoForte)
        Text("Prova un altro carburante o cerca un comune dalla lista.", style = Testi.Didascalia.copy(color = Colori.Testo2))
    }
}

@Composable
private fun CardRisparmio(utente: DatiUtente, onStatistiche: () -> Unit) {
    val adesso = System.currentTimeMillis()
    val r = Statistiche.risparmio(utente.rifornimenti, adesso)
    Scheda(Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp).fillMaxWidth(), onClick = onStatistiche) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Hai risparmiato questo mese", style = Testi.DidascaliaForte.copy(color = Colori.Testo3, fontWeight = FontWeight.SemiBold))
                Text(Formati.euro(r.meseCorrente), style = Testi.Display)
            }
            if (r.totale != 0.0) {
                Badge(
                    "Totale ${Formati.euro(r.totale)}",
                    Colori.VerdeChiaro,
                    Colori.VerdeTesto,
                    padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }
        GraficoBarre(
            valori = r.perMese.map { it.second },
            etichette = r.perMese.map { Formati.meseBreve(it.first) },
            descrizione = "Risparmio negli ultimi sei mesi",
        )
        Text(
            if (utente.rifornimenti.isEmpty()) "Registra i tuoi rifornimenti: calcoliamo quanto risparmi rispetto alla media della zona."
            else "Calcolato su ogni rifornimento: prezzo pagato rispetto alla media della zona quel giorno.",
            style = Testi.Didascalia.copy(color = Colori.Testo2),
        )
    }
}

@Composable
private fun Consumi(vm: GocciaViewModel, utente: DatiUtente, onStatistiche: () -> Unit) {
    val auto = utente.autoCorrente ?: return
    if (auto.alimentazione.elettrica) return
    val adesso = System.currentTimeMillis()
    val suoi = utente.rifornimenti.filter { it.autoId == null || it.autoId == auto.id }
    val r = Statistiche.consumi(suoi, adesso, auto.consumo)
    val mese = Formati.meseLungo(meseDi(adesso))
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        IntestazioneSezione("Consumi · ${auto.nome}", azione = "Statistiche", onAzione = onStatistiche)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Riquadrino(
                "Consumo",
                Formati.numero(r.consumo ?: auto.consumo, 1),
                if (r.consumo != null) auto.alimentazione.unitaConsumo else "${auto.alimentazione.unitaConsumo} · dichiarato",
                Modifier.weight(1f),
            )
            Riquadrino("Costo per km", r.costoKm?.let { Formati.numero(it, 3) } ?: "—", "€/km", Modifier.weight(1f))
            Riquadrino(
                "Spesa ${mese.take(3)}.",
                Formati.numero(r.spesaMese, 2),
                "€ · ${r.rifornimentiMese} ${if (r.rifornimentiMese == 1) "pieno" else "pieni"}",
                Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun Riquadrino(titolo: String, valore: String, unita: String, modifier: Modifier = Modifier) {
    Column(
        modifier
            .ombra(RoundedCornerShape(16.dp), 1.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Colori.Superficie)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(titolo, style = Testi.Piccolo.copy(color = Colori.Testo3), maxLines = 1)
        Text(valore, style = Testi.Numero, maxLines = 1)
        Text(unita, style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium), maxLines = 2)
    }
}

@Composable
private fun CardMediaZona(dati: StatoDati, vista: VistaZona?) {
    val v = vista ?: return
    val provincia = v.provincia ?: return
    val serieCompleta = dati.storicoZona?.medie?.get(v.carburante)?.let { if (v.self) it.self else it.servito } ?: emptyList()
    val serie = serieCompleta.takeLast(7)
    val ultimo = serie.lastOrNull { it != null } ?: v.mediaProvincia ?: return
    val tendenza = Consiglio.tendenza(serieCompleta)
    val modo = if (v.carburante.haSelf) (if (v.self) " self" else " servito") else ""
    Scheda(Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp).fillMaxWidth(), spazio = 12.dp) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Prezzo medio ${v.carburante.etichetta.lowercase()}$modo · provincia di ${provincia.nome}",
                    style = Testi.DidascaliaForte.copy(color = Colori.Testo3, fontWeight = FontWeight.SemiBold),
                )
                Text(
                    buildAnnotatedString {
                        append(Formati.prezzo(ultimo))
                        withStyle(SpanStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Colori.Testo3)) { append(" ${v.carburante.unita}") }
                    },
                    style = Testi.PrezzoGrande.copy(fontSize = 26.sp),
                )
            }
            if (tendenza != null) {
                val stile = when (tendenza.first) {
                    Tendenza.IN_CALO -> StileTendenza(Colori.VerdeChiaro, Colori.VerdeTesto, "In calo", Icone.InCalo)
                    Tendenza.IN_SALITA -> StileTendenza(Colori.RossoChiaro, Colori.Rosso, "In aumento", Icone.InSalita)
                    Tendenza.STABILE -> StileTendenza(Colori.GrigioBadge, Colori.TestoChip, "Stabile", null)
                }
                Badge(stile.testo, stile.sfondo, stile.colore, icona = stile.icona, padding = PaddingValues(horizontal = 10.dp, vertical = 5.dp))
            }
        }
        if (serie.count { it != null } >= 2) {
            GraficoLinea(
                valori = serie,
                inizio = "${serie.size - 1} giorni fa",
                fine = "Oggi",
                media = v.mediaNazionale,
                etichettaMedia = v.mediaNazionale?.let { "Media nazionale ${Formati.prezzo(it)}" },
                mostraScala = false,
                altezza = 96.dp,
                descrizione = "Andamento della media provinciale negli ultimi giorni",
            )
        }
        val frasi = mutableListOf<String>()
        if (tendenza != null && tendenza.second != 0) frasi += "${Formati.variazioneCent(tendenza.second)} in 7 giorni"
        v.mediaNazionale?.let { naz ->
            val diff = ultimo - naz
            frasi += when {
                abs(diff) < 5 -> "in linea con la media nazionale"
                diff < 0 -> "${Formati.numero(abs(diff) / 10.0, 1)} cent sotto la media nazionale"
                else -> "${Formati.numero(diff / 10.0, 1)} cent sopra la media nazionale"
            }
        }
        if (frasi.isNotEmpty()) {
            Text(frasi.joinToString(", ").replaceFirstChar { it.uppercase() } + ".", style = Testi.Didascalia.copy(color = Colori.Testo2))
        }
    }
}

private data class StileTendenza(val sfondo: Color, val colore: Color, val testo: String, val icona: ImageVector?)

@Composable
private fun InvitoSostegno(vm: GocciaViewModel, utente: DatiUtente, onSostieni: () -> Unit) {
    val risparmio = utente.rifornimenti.sumOf { it.risparmio ?: 0.0 }
    val chiusoDaPoco = System.currentTimeMillis() - utente.donazioneChiusaIl < 90L * 24 * 3_600_000
    if (risparmio < 25 || chiusoDaPoco) return
    Box(Modifier.padding(start = 20.dp, end = 20.dp, top = 26.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(Forme.CardGrande)
                .background(Colori.Crema)
                .border(1.dp, Colori.CremaBordo, Forme.CardGrande)
                .padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.size(44.dp).clip(CircleShape).background(Colori.AmbraChiaro), contentAlignment = Alignment.Center) {
                Icon(Icone.Caffe, null, tint = Colori.AmbraScuro, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f).padding(end = 28.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Hai superato ${Formati.numero(risparmio, 0)} € di risparmio", style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold))
                Text(
                    "Goccia è gratis e senza pubblicità. Se ti è utile, puoi dare una mano a farla crescere.",
                    style = Testi.Didascalia.copy(color = Colori.CremaTesto),
                )
                Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BottonePrimario("Scopri come", onSostieni, altezza = 40.dp, sfondo = Colori.Inchiostro, forma = RoundedCornerShape(12.dp))
                    Text(
                        "Non ora",
                        style = Testi.Pulsante.copy(color = Colori.CremaTesto, fontSize = 14.sp),
                        modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { vm.chiudiDonazione() }.padding(horizontal = 12.dp, vertical = 11.dp),
                    )
                }
            }
        }
        BottoneIcona(
            Icone.Chiudi, "Chiudi", { vm.chiudiDonazione() },
            Modifier.align(Alignment.TopEnd).padding(8.dp),
            colore = Colori.CremaIcona, dimensione = 40.dp, dimensioneIcona = 18.dp,
        )
    }
}
