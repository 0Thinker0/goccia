package it.goccia.app.ui.intro

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.avvisi.Notifiche
import it.goccia.app.dati.Alimentazione
import it.goccia.app.dati.Auto
import it.goccia.app.dati.Luogo
import it.goccia.app.dati.TipoLuogo
import it.goccia.app.logica.Formati
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.BottoneSecondario
import it.goccia.app.ui.componenti.BottoneTesto
import it.goccia.app.ui.componenti.CampoRicerca
import it.goccia.app.ui.componenti.IndicatorePassi
import it.goccia.app.ui.componenti.LogoApp
import it.goccia.app.ui.componenti.Riquadro
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.profilo.ModuloAuto
import it.goccia.app.ui.stati.SuggerimentiComuni
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra
import kotlinx.coroutines.launch

private const val PASSI = 4

@Composable
fun SchermataIntro(vm: GocciaViewModel, onFine: () -> Unit) {
    var passo by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = passo > 0) { passo-- }

    // dati dell'auto, tenuti qui per non perderli tornando indietro
    var nome by rememberSaveable { mutableStateOf("") }
    var alimentazione by rememberSaveable { mutableStateOf(Alimentazione.BENZINA) }
    var capienza by rememberSaveable { mutableDoubleStateOf(Alimentazione.BENZINA.capienzaTipica) }
    var consumo by rememberSaveable { mutableStateOf(Formati.numero(Alimentazione.BENZINA.consumoTipico, 1)) }

    fun salvaAuto() {
        val valore = Formati.leggiNumero(consumo)?.takeIf { it > 0 && it < 100 } ?: alimentazione.consumoTipico
        vm.salvaAuto(
            Auto(
                id = vm.nuovoId(),
                nome = nome.trim().ifBlank { "La mia auto" },
                alimentazione = alimentazione,
                capienza = capienza,
                consumo = valore,
                livello = 0.5,
                livelloIl = System.currentTimeMillis(),
            ),
            attiva = true,
        )
    }

    Column(Modifier.fillMaxSize().background(Colori.Sfondo).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (passo == 0) {
                Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LogoApp(30.dp, angolo = 9.dp)
                    Text("Goccia", style = Testi.Sottosezione)
                }
            } else {
                BottoneIcona(Icone.Indietro, "Indietro", { passo-- })
            }
            Box(Modifier.weight(1f))
            BottoneTesto("Salta", onFine, colore = Colori.Testo2)
        }
        Crossfade(targetState = passo, label = "introduzione", modifier = Modifier.weight(1f)) { p ->
            when (p) {
                0 -> Benvenuto()
                1 -> Permessi()
                2 -> Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    Titolo("Com'è la tua auto?", "Serve per stimare autonomia, consumi e quanto ti costa ogni viaggio.", Modifier.padding(horizontal = 8.dp))
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
                }
                else -> Luoghi(vm)
            }
        }
        Column(Modifier.padding(start = 28.dp, end = 28.dp, bottom = 28.dp, top = 12.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
            IndicatorePassi(passo, PASSI)
            BottonePrimario(
                when (passo) {
                    0 -> "Iniziamo"
                    PASSI - 1 -> "Fatto, andiamo!"
                    else -> "Continua"
                },
                onClick = {
                    when (passo) {
                        2 -> {
                            salvaAuto()
                            passo++
                        }
                        PASSI - 1 -> onFine()
                        else -> passo++
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                icona = Icone.Avanti,
                iconaDopo = true,
                altezza = 56.dp,
                forma = RoundedCornerShape(16.dp),
            )
        }
    }
}

@Composable
private fun Titolo(titolo: String, testo: String, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(titolo, style = Testi.Onboarding)
        Text(testo, style = Testi.CorpoGrande.copy(color = Colori.Testo2))
    }
}

@Composable
private fun Benvenuto() {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(380.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(280.dp).clip(CircleShape).background(Colori.PetrolioChiaro))
            Box(Modifier.size(180.dp).clip(CircleShape).background(Color(0xFFC5E7E3)))
            LogoApp(100.dp, Modifier.ombra(RoundedCornerShape(30.dp), 12.dp), angolo = 30.dp)
            Nuvoletta(Modifier.align(Alignment.TopStart).offset(x = 20.dp, y = 30.dp).rotate(-4f)) {
                Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Colori.Notifica), contentAlignment = Alignment.Center) {
                    Icon(Icone.Pompa, null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        buildAnnotatedString {
                            append("1,689")
                            withStyle(SpanStyle(fontSize = 12.sp, color = Colori.Testo3, fontWeight = FontWeight.SemiBold)) { append(" €/l") }
                        },
                        style = Testi.Sottosezione.copy(fontSize = 18.sp, fontFeatureSettings = "tnum"),
                    )
                    Text(
                        "−5 cent vs media",
                        style = Testi.Minimo.copy(color = Colori.VerdeTesto),
                        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Colori.VerdeChiaro).padding(horizontal = 7.dp, vertical = 2.dp),
                    )
                }
            }
            Nuvoletta(Modifier.align(Alignment.BottomEnd).offset(x = (-16).dp, y = (-50).dp).rotate(3f)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(Colori.AmbraChiaro), contentAlignment = Alignment.Center) {
                    Icon(Icone.Campanella, null, tint = Colori.AmbraScuro, modifier = Modifier.size(18.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Gasolio sotto 1,700", style = Testi.DidascaliaForte.copy(fontWeight = FontWeight.ExtraBold))
                    Text("a 1,2 km da Casa", style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium))
                }
            }
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-44).dp, y = 36.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Colori.Inchiostro)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icone.Segnaposto, null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text("Distributori vicini", style = Testi.DidascaliaForte.copy(color = Color.White))
            }
        }
        Titolo(
            "Trova il pieno più conveniente, gratis e senza pubblicità",
            "Prezzi ufficiali comunicati dai distributori al Ministero, aggiornati ogni giorno. Niente account, niente abbonamenti.",
            Modifier.padding(horizontal = 28.dp),
        )
    }
}

@Composable
private fun Nuvoletta(modifier: Modifier, contenuto: @Composable () -> Unit) {
    Row(
        modifier
            .width(210.dp)
            .ombra(RoundedCornerShape(16.dp), 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Colori.Superficie)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) { contenuto() }
}

@Composable
private fun Permessi() {
    val context = LocalContext.current
    var posizione by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var notifiche by remember { mutableStateOf(Notifiche.permesso(context)) }
    val chiediPosizione = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { esito ->
        posizione = esito.values.any { it }
    }
    val chiediNotifiche = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifiche = Notifiche.permesso(context) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Titolo("Due permessi, e poi si parte", "Ti spieghiamo a cosa servono. Puoi usare l'app anche senza, cercando per comune.", Modifier.padding(start = 8.dp, end = 8.dp, bottom = 14.dp))
        CartaPermesso(
            Icone.Segnaposto, Colori.PetrolioChiaro, Colori.Petrolio,
            "Posizione",
            "Per mostrarti i distributori vicini e calcolare le deviazioni. La usiamo solo mentre l'app è aperta.",
            concesso = posizione,
            etichettaConcesso = "Posizione consentita",
            pulsante = "Consenti posizione",
            primario = true,
        ) { chiediPosizione.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)) }
        CartaPermesso(
            Icone.Campanella, Colori.AmbraChiaro, Colori.AmbraScuro,
            "Notifiche",
            "Per avvisarti quando un preferito cala o quando in zona il prezzo scende sotto la tua soglia.",
            concesso = notifiche,
            etichettaConcesso = "Notifiche consentite",
            pulsante = "Consenti notifiche",
            primario = false,
        ) {
            if (Build.VERSION.SDK_INT >= 33) chiediNotifiche.launch(Manifest.permission.POST_NOTIFICATIONS)
            else notifiche = Notifiche.permesso(context)
        }
        Row(Modifier.padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icone.Scudo, null, tint = Colori.Testo3, modifier = Modifier.size(18.dp))
            Text("Nessun account e nessun tracciamento. Puoi cambiare idea quando vuoi dalle Impostazioni.", style = Testi.Didascalia.copy(color = Colori.Testo2))
        }
    }
}

@Composable
private fun CartaPermesso(
    icona: ImageVector,
    sfondoIcona: Color,
    coloreIcona: Color,
    titolo: String,
    testo: String,
    concesso: Boolean,
    etichettaConcesso: String,
    pulsante: String,
    primario: Boolean,
    onChiedi: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .ombra(RoundedCornerShape(20.dp))
            .clip(RoundedCornerShape(20.dp))
            .background(Colori.Superficie)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(sfondoIcona), contentAlignment = Alignment.Center) {
                Icon(icona, null, tint = coloreIcona, modifier = Modifier.size(24.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(titolo, style = Testi.Sottosezione)
                Text(testo, style = Testi.Testo14.copy(color = Colori.Testo2))
            }
        }
        if (concesso) {
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Colori.VerdeChiaro),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icone.Spunta, null, tint = Colori.VerdeTesto, modifier = Modifier.size(18.dp))
                Text(etichettaConcesso, style = Testi.Pulsante.copy(color = Colori.VerdeTesto))
            }
        } else if (primario) {
            BottonePrimario(pulsante, onChiedi, Modifier.fillMaxWidth())
        } else {
            BottoneSecondario(pulsante, onChiedi, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun Luoghi(vm: GocciaViewModel) {
    val utente by vm.utente.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var cerco by remember { mutableStateOf(false) }
    var errore by remember { mutableStateOf(false) }
    var testoComune by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { vm.caricaComuni() }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Titolo(
            "Dove vai più spesso?",
            "Salva la tua casa: la useremo per gli avvisi di prezzo e quando la posizione non è disponibile.",
            Modifier.padding(start = 8.dp, end = 8.dp, bottom = 14.dp),
        )
        utente.luoghi.forEach { l ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .ombra(RoundedCornerShape(18.dp))
                    .clip(RoundedCornerShape(18.dp))
                    .background(Colori.Superficie)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(Colori.PetrolioChiaro), contentAlignment = Alignment.Center) {
                    Icon(if (l.tipo == TipoLuogo.LAVORO) Icone.Lavoro else Icone.Casa, null, tint = Colori.Petrolio, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(l.nome, style = Testi.Voce)
                    Text(l.descrizione.ifBlank { "Posizione salvata" }, style = Testi.Testo14.copy(color = Colori.Testo2))
                }
                Icon(Icone.Spunta, null, tint = Colori.VerdeTesto, modifier = Modifier.size(20.dp))
            }
        }
        if (utente.casa == null) {
            BottoneSecondario(
                if (cerco) "Cerco la posizione…" else "Salva la posizione attuale come Casa",
                {
                    if (!cerco) scope.launch {
                        cerco = true
                        val qui = if (vm.haPermessoPosizione()) vm.posizioneAttuale() else null
                        cerco = false
                        if (qui == null) {
                            errore = true
                        } else {
                            val vicino = vm.comunePiuVicino(qui)
                            vm.salvaLuogo(Luogo(vm.nuovoId(), "Casa", TipoLuogo.CASA, qui.lat, qui.lon, vicino?.let { "vicino a ${it.etichetta}" }.orEmpty()))
                        }
                    }
                },
                Modifier.fillMaxWidth(),
                icona = Icone.Casa,
                coloreIcona = Colori.Petrolio,
                altezza = 56.dp,
            )
        }
        if (errore && utente.casa == null) {
            Riquadro(Modifier.fillMaxWidth(), sfondo = Colori.AmbraChiaro, icona = Icone.Info, coloreIcona = Colori.AmbraScuro) {
                Text(
                    "Non riesco a trovare la posizione adesso. Scrivi il tuo comune e salviamo Casa lì: potrai spostarla quando vuoi da Impostazioni › Luoghi salvati.",
                    style = Testi.Didascalia.copy(color = Colori.AmbraTesto),
                )
            }
            CampoRicerca(testoComune, { testoComune = it }, Modifier.fillMaxWidth(), segnaposto = "Il tuo comune")
            if (testoComune.isNotBlank()) {
                SuggerimentiComuni(vm.cercaComuni(testoComune), onScelto = { comune ->
                    testoComune = ""
                    vm.salvaLuogo(Luogo(vm.nuovoId(), "Casa", TipoLuogo.CASA, comune.lat, comune.lon, "centro di ${comune.etichetta}"))
                })
            }
        }
        Riquadro(Modifier.fillMaxWidth(), icona = Icone.Scudo) {
            Text(
                buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("Nessun tracciamento continuo. ") }
                    append("Gli avvisi controllano i prezzi attorno ai luoghi salvati, non seguono i tuoi spostamenti.")
                },
                style = Testi.Didascalia.copy(color = Color(0xFF1F3A4A)),
            )
        }
    }
}
