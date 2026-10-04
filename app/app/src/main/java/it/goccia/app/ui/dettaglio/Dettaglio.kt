package it.goccia.app.ui.dettaglio

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Distributore
import it.goccia.app.dati.Prezzo
import it.goccia.app.dati.ServizioImpianto
import it.goccia.app.dati.StoricoDistributore
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.Formati
import it.goccia.app.logica.GIORNI_DA_VERIFICARE
import it.goccia.app.logica.Geo
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.componenti.Badge
import it.goccia.app.ui.componenti.BadgeConvenienza
import it.goccia.app.ui.componenti.BarraTitolo
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.BottoneSecondario
import it.goccia.app.ui.componenti.GraficoLinea
import it.goccia.app.ui.componenti.LogoBandiera
import it.goccia.app.ui.componenti.Riquadro
import it.goccia.app.ui.componenti.Scheda
import it.goccia.app.ui.componenti.Segmentato
import it.goccia.app.ui.condividi
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.naviga
import it.goccia.app.ui.stati.StatoVuoto
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi

private data class Caricamento(val finito: Boolean, val distributore: Distributore?)

@Composable
fun SchermataDettaglio(vm: GocciaViewModel, provincia: String, id: Long, onIndietro: () -> Unit, onRifornimento: () -> Unit) {
    val stato by produceState(Caricamento(false, vm.distributoreCaricato(id)), provincia, id) {
        value = Caricamento(true, vm.trovaDistributore(provincia, id))
    }
    // i prezzi in tempo reale arrivano dopo: seguiamo la versione aggiornata
    val dati by vm.dati.collectAsStateWithLifecycle()
    val d = dati.distributori.firstOrNull { it.id == id } ?: stato.distributore
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        if (d == null) {
            BarraTitolo(null, onIndietro)
            Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                if (!stato.finito) {
                    CircularProgressIndicator(color = Colori.Petrolio)
                } else {
                    StatoVuoto(
                        Icone.Pompa,
                        "Distributore non trovato",
                        "Potrebbe aver chiuso o non comunicare più i prezzi al Ministero.",
                        azione = "Torna indietro",
                        onAzione = onIndietro,
                    )
                }
            }
        } else {
            ContenutoDettaglio(vm, d, onIndietro, onRifornimento)
        }
    }
}

@Composable
private fun ContenutoDettaglio(vm: GocciaViewModel, d: Distributore, onIndietro: () -> Unit, onRifornimento: () -> Unit) {
    val context = LocalContext.current
    val utente by vm.utente.collectAsStateWithLifecycle()
    val dati by vm.dati.collectAsStateWithLifecycle()
    val schede by vm.schede.collectAsStateWithLifecycle()
    val adesso = System.currentTimeMillis()
    val carburante = utente.carburante
    val self = utente.impostazioni.preferisciSelf
    val preferito = utente.preferito(d.id)
    LaunchedEffect(d.id) { vm.caricaScheda(d.id) }
    val scheda = schede[d.id]
    val lettiIl = dati.live[d.id]?.letto?.takeIf { adesso / 1000 - it < 30 * 60 }

    Column(Modifier.fillMaxSize()) {
        BarraTitolo(null, onIndietro) {
            BottoneIcona(Icone.Condividi, "Condividi", {
                val prezzo = Convenienza.prezzoPer(d, carburante, self)
                val riga = prezzo?.let { (p, s) ->
                    "${carburante.etichetta}${if (carburante.haSelf) (if (s) " self" else " servito") else ""} ${Formati.prezzo(p.millesimi)} ${carburante.unita} " +
                        "(${Formati.aggiornamento(p.comunicato, adesso)})"
                } ?: ""
                condividi(
                    context,
                    "${d.titolo}, ${d.comune}\n$riga\nhttps://www.google.com/maps/search/?api=1&query=${d.lat},${d.lon}\n\nVisto con Goccia",
                )
            }, dimensioneIcona = 21.dp)
            BottoneIcona(
                Icone.Campanella,
                if (preferito?.avvisaCalo == true) "Avviso di calo attivo" else "Avvisami se scende",
                {
                    if (preferito == null) vm.preferito(d, true) else vm.avvisaCalo(d.id, !preferito.avvisaCalo)
                },
                colore = if (preferito?.avvisaCalo == true) Colori.Petrolio else Colori.Inchiostro,
                dimensioneIcona = 21.dp,
            )
            BottoneIcona(
                if (preferito != null) Icone.StellaPiena else Icone.Stella,
                if (preferito != null) "Rimuovi dai preferiti" else "Aggiungi ai preferiti",
                { vm.preferito(d, preferito == null) },
                colore = if (preferito != null) Colori.Ambra else Colori.Inchiostro,
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 18.dp),
        ) {
            Intestazione(vm, d)
            TabellaPrezzi(vm, d, carburante, self, adesso)
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val allerta = preferito?.avvisaCalo == true
                BottoneSecondario(
                    if (allerta) "Avviso attivo" else "Attiva avviso",
                    {
                        if (preferito == null) vm.preferito(d, true) else vm.avvisaCalo(d.id, !allerta)
                    },
                    Modifier.weight(1f),
                    icona = Icone.Campanella,
                    coloreIcona = if (allerta) Colori.Petrolio else Colori.Inchiostro,
                )
                BottoneSecondario(
                    if (preferito != null) "Salvato" else "Salva",
                    { vm.preferito(d, preferito == null) },
                    Modifier.weight(1f),
                    icona = if (preferito != null) Icone.StellaPiena else Icone.Stella,
                    coloreIcona = if (preferito != null) Colori.Ambra else Colori.Inchiostro,
                )
            }
            val prezzo = Convenienza.prezzoPer(d, carburante, self)?.first
            if (prezzo != null && Convenienza.giorniDa(prezzo.comunicato, adesso / 1000) > GIORNI_DA_VERIFICARE) {
                Riquadro(
                    Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp).fillMaxWidth(),
                    sfondo = Colori.AmbraChiaro,
                    icona = Icone.Attenzione,
                    coloreIcona = Colori.AmbraScuro,
                ) {
                    Text(
                        "Il prezzo del ${carburante.etichetta.lowercase()} è stato comunicato ${Formati.aggiornamento(prezzo.comunicato, adesso)}: " +
                            "potrebbe essere cambiato. Lo escludiamo dai consigli finché il gestore non lo aggiorna.",
                        style = Testi.Didascalia.copy(color = Colori.AmbraTesto),
                    )
                }
            }
            if (scheda != null && scheda.servizi.isNotEmpty()) Servizi(scheda.servizi)
            Storico(vm, d, carburante, self)
            Text(
                "Fonte: prezzi comunicati dal gestore al Ministero delle Imprese e del Made in Italy – Osservaprezzi carburanti. " +
                    if (lettiIl != null) {
                        "Prezzi in vigore letti in tempo reale alle ${Formati.ora(lettiIl * 1000)}; storico dal file pubblicato ogni mattina."
                    } else {
                        "Dati aggiornati ogni mattina (estrazione del ${dati.indice?.estrazione?.let { Formati.dataIso(it) } ?: "—"})."
                    },
                style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 18.dp),
            )
        }

        Column(Modifier.fillMaxWidth().background(Colori.Superficie)) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Colori.Bordo))
            Row(
                Modifier.navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                BottonePrimario(
                    "Naviga",
                    { naviga(context, d, utente.impostazioni.navigazione) },
                    Modifier.weight(0.7f),
                    icona = Icone.Naviga,
                    altezza = 54.dp,
                    forma = RoundedCornerShape(16.dp),
                )
                BottoneSecondario(
                    "Ho fatto il pieno qui",
                    onRifornimento,
                    Modifier.weight(1.3f),
                    icona = Icone.Piu,
                    altezza = 54.dp,
                    forma = RoundedCornerShape(16.dp),
                )
            }
        }
    }
}

@Composable
private fun Intestazione(vm: GocciaViewModel, d: Distributore) {
    val dati by vm.dati.collectAsStateWithLifecycle()
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            LogoBandiera(d.bandiera, d.pompaBianca, dimensione = 60.dp, angolo = 18.dp, testo = 19.sp)
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(d.intestazione, style = Testi.TitoloSchermata)
                val indirizzo = listOf(d.indirizzo, "${d.comune} (${d.provincia})").filter { it.isNotBlank() }.joinToString(", ")
                Text(indirizzo, style = Testi.Testo14.copy(color = Colori.Testo2))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            dati.centro?.let { c ->
                val km = Geo.distanzaKm(c.coordinate, d.coordinate)
                Pillola(Formati.km(km), Icone.Segnaposto)
            }
            if (d.autostradale) Pillola("Autostradale", Icone.Autostrada) else Pillola("Stradale", null)
            if (d.pompaBianca && d.bandiera.isNotBlank()) Pillola("Pompa bianca", null)
            else if (!d.pompaBianca && d.nome.isNotBlank() && d.nome != d.bandiera) Pillola(d.nome, null)
        }
    }
}

/** I servizi dichiarati dal gestore al Ministero: bar, autolavaggio, bancomat... */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Servizi(servizi: List<ServizioImpianto>) {
    Scheda(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp).fillMaxWidth(), spazio = 12.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Servizi", style = Testi.Sottosezione)
            Text("Dichiarati dal gestore al Ministero", style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            servizi.forEach { s -> Pillola(s.etichetta, iconaServizio(s)) }
        }
    }
}

private fun iconaServizio(s: ServizioImpianto): ImageVector? = when (s) {
    ServizioImpianto.RICARICA -> Icone.Fulmine
    ServizioImpianto.SOSTA_CAMPER_TIR -> Icone.Autostrada
    else -> null
}

@Composable
private fun Pillola(testo: String, icona: ImageVector?) {
    Row(
        Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Colori.Superficie)
            .border(1.dp, Colori.Bordo, RoundedCornerShape(15.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icona != null) Icon(icona, null, tint = Colori.Inchiostro, modifier = Modifier.size(14.dp))
        Text(testo, style = Testi.DidascaliaForte, maxLines = 1)
    }
}

@Composable
private fun TabellaPrezzi(vm: GocciaViewModel, d: Distributore, attivo: Carburante, self: Boolean, adesso: Long) {
    val ultimo = d.prezzi.values.flatMap { listOfNotNull(it.self, it.servito) }.maxOfOrNull { it.comunicato }
    Scheda(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp).fillMaxWidth(), spazio = 0.dp, padding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp)) {
        Row(Modifier.padding(start = 4.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icone.Orologio, null, tint = Colori.TestoChip, modifier = Modifier.size(14.dp))
            Text(
                ultimo?.let { "Prezzi aggiornati ${Formati.quando(it, adesso)} dal gestore" } ?: "Prezzi comunicati dal gestore",
                style = Testi.Etichetta.copy(color = Colori.TestoChip),
            )
        }
        Row(Modifier.padding(horizontal = 8.dp).padding(bottom = 8.dp)) {
            Text("Carburante", style = Testi.Etichetta.copy(color = Colori.Testo3), modifier = Modifier.weight(1f))
            Text("Self", style = Testi.Etichetta.copy(color = Colori.Testo3), textAlign = TextAlign.End, modifier = Modifier.width(78.dp))
            Text("Servito", style = Testi.Etichetta.copy(color = Colori.Testo3), textAlign = TextAlign.End, modifier = Modifier.width(78.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Colori.Divisore))
        val ordine = listOf(attivo) + Carburante.entries.filter { it != attivo }
        val righe = ordine.filter { d.prezzi[it] != null }
        righe.forEachIndexed { i, c ->
            val p = d.prezzi[c] ?: return@forEachIndexed
            val evidenziata = c == attivo
            val selfRiga = if (c.haSelf) p.self != null && (self || p.servito == null) else true
            val riferimento: Prezzo? = if (c.haSelf) (if (selfRiga) p.self else p.servito) else (p.self ?: p.servito)
            val media = vm.mediaAttorno(d, c, selfRiga)
            val cent = riferimento?.let { Convenienza.differenzaCent(it.millesimi, media) }
            RigaPrezzo(
                nome = c.etichetta,
                self = if (c.haSelf) p.self else null,
                servito = if (c.haSelf) p.servito else (p.servito ?: p.self),
                evidenziata = evidenziata,
                badge = {
                    if (cent != null) {
                        BadgeConvenienza(cent, Convenienza.tono(cent), piccolo = true, sfondo = if (evidenziata) Colori.Superficie else null)
                    }
                },
            )
            if (!evidenziata && i < righe.lastIndex) Box(Modifier.fillMaxWidth().height(1.dp).background(Colori.Divisore))
        }
        d.speciali.forEach { s ->
            Box(Modifier.fillMaxWidth().height(1.dp).background(Colori.Divisore))
            RigaPrezzo(
                nome = s.nome,
                self = if (s.self) s.prezzo else null,
                servito = if (s.self) null else s.prezzo,
                evidenziata = false,
                badge = {
                    Text(
                        "Speciale della bandiera · ${Formati.aggiornamento(s.prezzo.comunicato, adesso)}",
                        style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
                    )
                },
            )
        }
    }
}

@Composable
private fun RigaPrezzo(nome: String, self: Prezzo?, servito: Prezzo?, evidenziata: Boolean, badge: @Composable () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = if (evidenziata) 6.dp else 0.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (evidenziata) Colori.PetrolioTenue else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(nome, style = Testi.CorpoForte.copy(fontWeight = if (evidenziata) FontWeight.ExtraBold else FontWeight.Bold))
            badge()
        }
        CellaPrezzo(self, grande = evidenziata, principale = true)
        CellaPrezzo(servito, grande = false, principale = self == null)
    }
}

@Composable
private fun CellaPrezzo(prezzo: Prezzo?, grande: Boolean, principale: Boolean) {
    Text(
        prezzo?.let { Formati.prezzo(it.millesimi) } ?: "—",
        style = when {
            prezzo == null -> Testi.Corpo.copy(color = Colori.Testo3, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            grande -> Testi.PrezzoMedio
            principale -> Testi.PrezzoMedio.copy(fontSize = 18.sp)
            else -> Testi.PrezzoMedio.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Colori.TestoChip)
        },
        textAlign = TextAlign.End,
        modifier = Modifier.width(78.dp),
    )
}

@Composable
private fun Storico(vm: GocciaViewModel, d: Distributore, carburante: Carburante, self: Boolean) {
    val storico by produceState<StoricoDistributore?>(null, d.id) { value = vm.storicoDistributore(d.provincia, d.id) }
    var scheda by rememberSaveable { mutableIntStateOf(0) }
    val s = storico ?: return
    val giornaliero = s.giornaliero[carburante] ?: return
    if (giornaliero.count { it != null } < 2 && (s.mensile[carburante]?.count { it != null } ?: 0) < 2) return
    val (valori, inizio, fine, parole) = when (scheda) {
        0 -> {
            val n = minOf(7, giornaliero.size)
            Quattro(giornaliero.takeLast(n), s.giorni.takeLast(n).firstOrNull()?.let { Formati.dataIso(it) } ?: "", "Oggi", "7 giorni")
        }
        1 -> {
            val n = minOf(30, giornaliero.size)
            Quattro(giornaliero.takeLast(n), s.giorni.takeLast(n).firstOrNull()?.let { Formati.dataIso(it) } ?: "", "Oggi", "${n} giorni")
        }
        else -> {
            val mensile = s.mensile[carburante].orEmpty()
            Quattro(mensile, s.mesi.firstOrNull()?.let { Formati.meseIso(it) } ?: "", s.mesi.lastOrNull()?.let { Formati.meseIso(it) } ?: "", "${mensile.size} mesi")
        }
    }
    val presenti = valori.filterNotNull()
    val media = vm.mediaAttorno(d, carburante, self)
    Scheda(Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp).fillMaxWidth(), spazio = 12.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Storico ${carburante.etichetta.lowercase()}", style = Testi.Sottosezione, modifier = Modifier.weight(1f))
            Segmentato(listOf("7 g", "30 g", "12 m"), scheda, { scheda = it }, Modifier.width(170.dp), altezza = 32.dp)
        }
        if (presenti.size >= 2) {
            val delta = presenti.last() - presenti.first()
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val (sfondo, colore) = when {
                    delta < 0 -> Colori.VerdeChiaro to Colori.VerdeTesto
                    delta > 0 -> Colori.RossoChiaro to Colori.Rosso
                    else -> Colori.GrigioBadge to Colori.TestoChip
                }
                Badge("${Formati.variazioneCent(delta)} in $parole", sfondo, colore, padding = PaddingValues(horizontal = 9.dp, vertical = 4.dp))
                Text(
                    "Min ${Formati.prezzo(presenti.min())} · Max ${Formati.prezzo(presenti.max())}",
                    style = Testi.Piccolo.copy(color = Colori.Testo3),
                )
            }
            GraficoLinea(
                valori = valori,
                inizio = inizio,
                fine = fine,
                media = media,
                etichettaMedia = media?.let { "Media zona ${Formati.prezzo(it)}" },
                descrizione = "Prezzo del ${carburante.etichetta.lowercase()} negli ultimi $parole",
            )
            Text(
                "Per ogni giorno il prezzo self, o il servito se il self non c'è.",
                style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
            )
        } else {
            Text("Servono almeno due giorni di dati per il grafico.", style = Testi.Didascalia.copy(color = Colori.Testo3))
        }
    }
}

private data class Quattro(val valori: List<Int?>, val inizio: String, val fine: String, val parole: String)
