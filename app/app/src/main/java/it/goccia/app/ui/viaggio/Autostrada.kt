package it.goccia.app.ui.viaggio

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.avvisi.Notifiche
import it.goccia.app.guida.GuidaInCorso
import it.goccia.app.guida.ServizioGuida
import it.goccia.app.guida.StatoGuida
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Guida
import it.goccia.app.logica.SituazioneGuida
import it.goccia.app.logica.VoceGuida
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.componenti.Badge
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.BottoneSecondario
import it.goccia.app.ui.componenti.Interruttore
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.naviga
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra

/**
 * Avvio della modalita autostrada dal risultato del viaggio: chiede i permessi che mancano
 * (notifiche per gli avvisi, posizione per seguirti), poi apre la schermata.
 */
@Composable
fun rememberAvvioGuida(vm: GocciaViewModel, onAperta: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val avvia = {
        if (GuidaInCorso.attiva || vm.avviaGuida(context)) {
            onAperta()
        } else {
            Toast.makeText(context, "Per la modalità autostrada serve il permesso di posizione", Toast.LENGTH_LONG).show()
        }
    }
    val permessoPosizione = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { avvia() }
    val chiediPosizione = {
        permessoPosizione.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }
    val permessoNotifiche = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        if (ServizioGuida.permessoPosizione(context)) avvia() else chiediPosizione()
    }
    return {
        when {
            GuidaInCorso.attiva -> onAperta()
            Build.VERSION.SDK_INT >= 33 && !Notifiche.permesso(context) -> permessoNotifiche.launch(Manifest.permission.POST_NOTIFICATIONS)
            !ServizioGuida.permessoPosizione(context) -> chiediPosizione()
            else -> avvia()
        }
    }
}

/** Invito alla modalita autostrada in fondo al risultato del viaggio. */
@Composable
fun CardModalitaAutostrada(elettrica: Boolean, onAvvia: () -> Unit, modifier: Modifier = Modifier) {
    val attiva by GuidaInCorso.stato.collectAsStateWithLifecycle()
    val forma = RoundedCornerShape(18.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(forma)
            .background(Colori.Inchiostro)
            .clickable(onClick = onAvvia)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(FondoBarra), contentAlignment = Alignment.Center) {
            Icon(Icone.Autostrada, null, tint = SiglaFondo, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("Modalità autostrada", style = Testi.CorpoForte.copy(color = Color.White, fontWeight = FontWeight.ExtraBold))
            Text(
                if (attiva != null) "È attiva: tocca per vedere dove sei e la prossima sosta."
                else if (elettrica) "Mentre guidi: km, batteria stimata e colonnina consigliata sempre aggiornati."
                else "Mentre guidi: km, autonomia e sosta consigliata sempre aggiornati, con l'avviso 10 km prima.",
                style = Testi.Piccolo.copy(color = TestoTenue, fontWeight = FontWeight.Medium),
            )
        }
        Text(
            if (attiva != null) "Apri" else "Avvia",
            style = Testi.DidascaliaForte.copy(color = Colori.Inchiostro, fontWeight = FontWeight.ExtraBold),
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(SiglaFondo).padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

// colori della scheda scura, come nel design
private val TestoTenue = Color(0xFFC9D6E2)
private val TestoMessaggio = Color(0xFFDCE5EE)
private val FondoBarra = Color(0xFF33475B)
private val SiglaFondo = Color(0xFFFBE3A5)
private val SiglaTesto = Color(0xFF6B4E0E)

/** Modalita autostrada: dove sei, quanta autonomia hai e dove conviene fermarsi, aggiornati mentre guidi. */
@Composable
fun SchermataAutostrada(vm: GocciaViewModel, onIndietro: () -> Unit) {
    val context = LocalContext.current
    val utente by vm.utente.collectAsStateWithLifecycle()
    val statoGuida by GuidaInCorso.stato.collectAsStateWithLifecycle()
    val stato = statoGuida

    // lo schermo resta acceso finche la schermata e aperta (telefono sul supporto)
    val vista = LocalView.current
    DisposableEffect(vista) {
        vista.keepScreenOn = true
        onDispose { vista.keepScreenOn = false }
    }

    if (stato == null) {
        // modalita terminata (arrivo, "Termina" dalla notifica): torniamo al viaggio
        LaunchedEffect(Unit) { onIndietro() }
        return
    }
    val s = stato.situazione

    Column(Modifier.fillMaxSize().background(Colori.Sfondo).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BottoneIcona(Icone.Indietro, "Indietro", onIndietro)
            Text("Modalità autostrada", style = Testi.Titolo.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold), modifier = Modifier.weight(1f))
        }
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SchedaPosizione(stato, s, onRifornito = { ServizioGuida.rispondi(context, true) })

            stato.domanda?.let { (_, nome) ->
                DomandaRifornimento(
                    nome,
                    stato.elettrica,
                    onSi = { ServizioGuida.rispondi(context, true) },
                    onNo = { ServizioGuida.rispondi(context, false) },
                )
            }
            if (stato.rifornimentoAnnullabile && stato.domanda == null) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        if (stato.elettrica) "Ricarica segnata: batteria all'80%." else "Pieno segnato: autonomia ripartita.",
                        style = Testi.Didascalia.copy(color = Colori.Testo2),
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Annulla",
                        style = Testi.DidascaliaForte.copy(color = Colori.Petrolio),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { GuidaInCorso.annullaRifornimento() }.padding(8.dp),
                    )
                }
            }

            if (s != null && !s.arrivato) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Bottom) {
                    Text(
                        if (stato.elettrica) "Prossime colonnine" else "Prossimi rifornimenti",
                        style = Testi.Sezione.copy(fontSize = 17.sp),
                        modifier = Modifier.weight(1f),
                    )
                    Text(stato.etichetta, style = Testi.Didascalia.copy(color = Colori.Testo3, fontWeight = FontWeight.SemiBold))
                }
                if (s.prossime.isEmpty()) {
                    Text(
                        if (stato.elettrica) "Nessuna colonnina veloce compatibile nei prossimi chilometri del percorso."
                        else "Nessun distributore con prezzi recenti nei prossimi chilometri del percorso.",
                        style = Testi.Didascalia.copy(color = Colori.Testo2),
                    )
                }
                s.prossime.forEach { v -> RigaProssima(v) }
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Colori.Superficie)
                    .border(1.dp, Colori.Bordo, RoundedCornerShape(16.dp))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icone.Campanella, null, tint = Colori.Petrolio, modifier = Modifier.size(20.dp))
                Text(
                    "Avvisami ${Guida.PREAVVISO_KM.toInt()} km prima della sosta consigliata",
                    style = Testi.Testo14.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f),
                )
                Interruttore(stato.avviso, { GuidaInCorso.impostaAvviso(it) }, descrizione = "Avvisami prima della sosta")
            }
            Text(
                "La modalità resta attiva anche con il navigatore aperto: la trovi nelle notifiche. " +
                    "L'autonomia è una stima dai consumi dell'auto; quando fai rifornimento te lo chiediamo.",
                style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
            )
        }

        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, bottom = 12.dp, top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val consigliata = s?.consigliata
            if (consigliata != null) {
                BottonePrimario(
                    if (stato.elettrica) "Portami alla colonnina" else "Portami alla sosta",
                    { naviga(context, consigliata.lat, consigliata.lon, consigliata.nome, utente.impostazioni.navigazione) },
                    Modifier.fillMaxWidth(),
                    icona = Icone.Naviga,
                    altezza = 52.dp,
                )
            }
            BottoneSecondario(
                "Termina la modalità autostrada",
                { ServizioGuida.termina(context) },
                Modifier.fillMaxWidth(),
                icona = Icone.Chiudi,
                altezza = 48.dp,
            )
        }
    }
}

@Composable
private fun SchedaPosizione(stato: StatoGuida, s: SituazioneGuida?, onRifornito: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Colori.Inchiostro)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    s?.sigla?.let { sigla ->
                        Text(
                            sigla,
                            style = Testi.DidascaliaForte.copy(color = SiglaTesto, fontWeight = FontWeight.ExtraBold),
                            modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(SiglaFondo).padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                    Text(
                        "direzione ${stato.destinazione}",
                        style = Testi.Testo14.copy(color = TestoTenue, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    when {
                        s == null -> "Cerco la posizione…"
                        s.arrivato -> "Sei arrivato"
                        else -> "Sei al km ${s.km.toInt()}"
                    },
                    style = Testi.Titolo.copy(color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold),
                )
            }
            if (s != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Autonomia", style = Testi.Piccolo.copy(color = TestoTenue, fontWeight = FontWeight.SemiBold))
                    Text(
                        Guida.autonomiaTesto(s.autonomiaKm),
                        style = Testi.Titolo.copy(color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
                    )
                }
            }
        }
        if (s != null) {
            val livello = s.livello.toFloat()
            val colore = when {
                livello < 0.15f -> Color(0xFFEF4444)
                livello < 0.3f -> Colori.Ambra
                else -> Color(0xFF2DD4BF)
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(FondoBarra)
                    .semantics { contentDescription = "${if (stato.elettrica) "Batteria" else "Serbatoio"} al ${Formati.percento(s.livello)}" },
            ) {
                Box(Modifier.fillMaxWidth(livello.coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(4.dp)).background(colore))
            }
        }
        Text(
            when {
                stato.senzaSegnale -> "Nessun segnale GPS da più di un minuto (galleria?): i dati restano quelli dell'ultima posizione."
                s == null -> "Appena il GPS trova la posizione ti diciamo dove sei lungo il percorso."
                else -> s.messaggio
            },
            style = Testi.Didascalia.copy(color = TestoMessaggio, fontSize = 13.sp),
        )
        if (s != null && !s.arrivato) {
            Text(
                if (stato.elettrica) "Ho ricaricato" else "Ho fatto il pieno",
                style = Testi.DidascaliaForte.copy(color = Color.White),
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, FondoBarra, RoundedCornerShape(10.dp))
                    .clickable(onClick = onRifornito)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun DomandaRifornimento(nome: String, elettrica: Boolean, onSi: () -> Unit, onNo: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Colori.AmbraChiaro)
            .border(1.dp, Colori.AmbraBordo, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            if (elettrica) "Ti sei fermato a $nome: hai ricaricato?" else "Ti sei fermato a $nome: hai fatto il pieno?",
            style = Testi.CorpoForte.copy(color = Colori.AmbraTesto),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BottonePrimario(if (elettrica) "Sì, all'80%" else "Sì, il pieno", onSi, Modifier.weight(1f), altezza = 42.dp)
            BottoneSecondario("No", onNo, Modifier.weight(1f), altezza = 42.dp)
        }
    }
}

@Composable
private fun RigaProssima(v: VoceGuida) {
    val forma = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .then(
                if (v.consigliata) Modifier.clip(forma).background(Colori.PetrolioTenue).border(1.5.dp, Colori.Petrolio, forma)
                else Modifier.ombra(forma, 1.dp).clip(forma).background(Colori.Superficie),
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Column(Modifier.width(58.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (v.traKm < 10) Formati.numero(v.traKm, 1) else v.traKm.toInt().toString(),
                style = Testi.Titolo.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
            )
            Text("km", style = Testi.Piccolo.copy(color = if (v.consigliata) Colori.TestoChip else Colori.Testo3, fontWeight = FontWeight.SemiBold))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                v.nome,
                style = Testi.CorpoForte.copy(fontWeight = if (v.consigliata) FontWeight.ExtraBold else FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(v.dettaglio, style = Testi.Piccolo.copy(color = if (v.consigliata) Colori.TestoChip else Colori.Testo3, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
            when {
                v.consigliata -> Badge("Consigliato", Colori.VerdeTesto, Color.White, icona = Icone.Spunta)
                v.confronto != null -> Badge(
                    v.confronto,
                    if (v.piuCara) Colori.RossoChiaro else Colori.VerdeChiaro,
                    if (v.piuCara) Colori.Rosso else Colori.VerdeTesto,
                )
            }
        }
        Text(v.valore, style = Testi.Titolo.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"))
    }
}
