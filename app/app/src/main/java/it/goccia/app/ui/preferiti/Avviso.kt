package it.goccia.app.ui.preferiti

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.avvisi.Notifiche
import it.goccia.app.avvisi.Sorveglianza
import it.goccia.app.dati.Avviso
import it.goccia.app.dati.Carburante
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.Formati
import it.goccia.app.logica.ValutaAvvisi
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.BottoneTesto
import it.goccia.app.ui.componenti.IntestazioneFoglio
import it.goccia.app.ui.componenti.LogoApp
import it.goccia.app.ui.componenti.Opzione
import it.goccia.app.ui.componenti.Stepper
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra
import kotlin.math.roundToInt

private val RAGGI = listOf(3, 5, 10, 20)

@Composable
fun SchermataAvviso(vm: GocciaViewModel, id: String?, onChiudi: () -> Unit, onNuovoLuogo: () -> Unit) {
    val context = LocalContext.current
    val utente by vm.utente.collectAsStateWithLifecycle()
    val dati by vm.dati.collectAsStateWithLifecycle()
    val esistente = remember(id) { id?.let { i -> utente.avvisi.firstOrNull { it.id == i } } }

    // luogo: id di un luogo salvato, oppure "" per la posizione attuale
    var luogoId by rememberSaveable { mutableStateOf(esistente?.luogoId ?: utente.casa?.id ?: utente.luoghi.firstOrNull()?.id ?: "") }
    var raggio by rememberSaveable { mutableIntStateOf(esistente?.raggioKm ?: 5) }
    var carburante by rememberSaveable { mutableStateOf(esistente?.carburante?.let { Carburante.daCodice(it) } ?: utente.carburante) }
    var soglia by rememberSaveable { mutableIntStateOf(esistente?.soglia ?: -1) }

    val luogo = utente.luogo(luogoId.ifBlank { null })
    val centro = luogo?.coordinate ?: dati.centro?.coordinate ?: utente.ultimaPosizione?.coordinate
    LaunchedEffect(centro, raggio) { centro?.let { vm.caricaAttorno(it, raggio + 5.0) } }

    val adessoSecondi = System.currentTimeMillis() / 1000
    val self = utente.impostazioni.preferisciSelf
    val migliore = centro?.let {
        ValutaAvvisi.migliore(dati.distributori, carburante, self, it, raggio, utente.impostazioni.escludiAutostrade, adessoSecondi)
    }
    val media = centro?.let { Convenienza.mediaLocale(dati.distributori, carburante, self || !carburante.haSelf, it, maxOf(raggio, 5).toDouble(), adessoSecondi) }

    // soglia iniziale: un centesimo sotto la media della zona, arrotondata a mezzo centesimo
    var toccata by rememberSaveable { mutableStateOf(esistente != null) }
    LaunchedEffect(media, carburante, toccata) {
        if (!toccata) {
            val base = media ?: dati.indice?.medieNazionali?.get(carburante)?.let { it.self ?: it.servito }
            if (base != null) soglia = ((base - 10) / 5.0).roundToInt() * 5
        }
    }

    var permessoChiesto by remember { mutableStateOf(false) }
    val richiesta = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { permessoChiesto = true }

    Column(Modifier.fillMaxSize().background(Colori.Superficie).statusBarsPadding()) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            IntestazioneFoglio(if (esistente == null) "Nuovo avviso prezzo" else "Modifica avviso", onChiudi)

            if (soglia > 0) Anteprima(carburante, migliore?.prezzo?.millesimi ?: soglia, soglia, luogo?.nome, migliore?.distributore?.titolo)

            Sezione("Zona") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    utente.luoghi.forEach { l ->
                        Opzione(l.nome, luogoId == l.id, { luogoId = l.id }, forma = RoundedCornerShape(19.dp))
                    }
                    Opzione("Vicino a me", luogoId.isBlank(), { luogoId = "" }, forma = RoundedCornerShape(19.dp))
                    val forma = RoundedCornerShape(19.dp)
                    Box(
                        Modifier
                            .height(38.dp)
                            .clip(forma)
                            .border(1.5.dp, Colori.Tratteggio, forma)
                            .clickable(onClick = onNuovoLuogo)
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text("+ Altro luogo", style = Testi.ChipAttivo.copy(color = Colori.Petrolio)) }
                }
                if (luogoId.isBlank()) {
                    Text(
                        "Usiamo l'ultima posizione registrata quando hai aperto l'app: non ti seguiamo in background.",
                        style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
                    )
                }
            }

            Sezione("Raggio") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RAGGI.forEach { r -> Opzione("$r km", raggio == r, { raggio = r }, Modifier.weight(1f)) }
                }
            }

            Sezione("Carburante") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Carburante.entries.forEach { c ->
                        Opzione(c.etichetta, carburante == c, {
                            if (c != carburante) toccata = false
                            carburante = c
                        }, Modifier.weight(1f))
                    }
                }
            }

            Sezione("Avvisami sotto") {
                Stepper(
                    valore = if (soglia > 0) "${Formati.prezzo(soglia)} ${carburante.unita}" else "—",
                    onMeno = {
                        if (soglia > 0) soglia = (soglia - 5).coerceAtLeast(100)
                        toccata = true
                    },
                    onPiu = {
                        if (soglia > 0) soglia += 5
                        toccata = true
                    },
                    altezza = 60.dp,
                    stileValore = Testi.PrezzoGrande.copy(fontSize = 26.sp),
                    sfondoPulsanti = Colori.Grigio,
                    descrizioneMeno = "Abbassa la soglia di mezzo centesimo",
                    descrizionePiu = "Alza la soglia di mezzo centesimo",
                )
                val aiuto = when {
                    centro == null -> "Salva un luogo o attiva la posizione per controllare i prezzi."
                    migliore == null -> "Carico i prezzi della zona…"
                    else -> {
                        val scatta = soglia > 0 && migliore.prezzo.millesimi <= soglia
                        "Oggi entro $raggio km: minimo ${Formati.prezzo(migliore.prezzo.millesimi)}" +
                            (media?.let { " · media ${Formati.prezzo(it)}" } ?: "") +
                            if (scatta) " · scatterebbe subito" else ""
                    }
                }
                Text(aiuto, style = Testi.Piccolo.copy(color = Colori.Testo2))
            }

            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icone.Luna, null, tint = Colori.Testo2, modifier = Modifier.padding(top = 1.dp).size(14.dp))
                Text(
                    "Controlliamo i prezzi ogni mattina, appena il Ministero li pubblica" +
                        if (utente.impostazioni.orarioSilenzioso) ". Niente notifiche tra le 21:00 e le 07:00." else ".",
                    style = Testi.Piccolo.copy(color = Colori.Testo2),
                )
            }
            if (esistente != null) {
                BottoneTesto("Elimina avviso", {
                    vm.eliminaAvviso(esistente.id)
                    onChiudi()
                }, colore = Colori.Rosso)
            }
        }
        BottonePrimario(
            if (esistente == null) "Crea avviso" else "Salva avviso",
            onClick = {
                if (soglia <= 0) return@BottonePrimario
                val avviso = (esistente ?: Avviso(id = vm.nuovoId(), carburante = carburante.codice, soglia = soglia, creatoIl = System.currentTimeMillis())).copy(
                    carburante = carburante.codice,
                    soglia = soglia,
                    luogoId = luogoId.ifBlank { null },
                    raggioKm = raggio,
                    attivo = true,
                    ultimoMinimo = null,
                    ultimaEstrazione = null,
                )
                vm.salvaAvviso(avviso)
                if (!utente.impostazioni.avvisiPrezzo) vm.impostazioni { it.copy(avvisiPrezzo = true) }
                if (Build.VERSION.SDK_INT >= 33 && !Notifiche.permesso(context) && !permessoChiesto) {
                    richiesta.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Sorveglianza.controllaOra(context)
                onChiudi()
            },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 16.dp),
            abilitato = soglia > 0 && centro != null,
            altezza = 54.dp,
            forma = RoundedCornerShape(16.dp),
        )
    }
}

@Composable
private fun Sezione(titolo: String, contenuto: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titolo, style = Testi.DidascaliaForte.copy(color = Colori.TestoChip))
        contenuto()
    }
}

/** Come apparirebbe la notifica. */
@Composable
private fun Anteprima(carburante: Carburante, prezzo: Int, soglia: Int, luogo: String?, distributore: String?) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(Colori.Inchiostro).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("ANTEPRIMA NOTIFICA", style = Testi.TitoloGruppo.copy(color = Colori.Bordo, letterSpacing = 0.6.sp), modifier = Modifier.padding(start = 6.dp))
        Column(
            Modifier.fillMaxWidth().ombra(RoundedCornerShape(18.dp), 6.dp).clip(RoundedCornerShape(18.dp)).background(Colori.Superficie).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LogoApp(20.dp, angolo = 6.dp)
                Text("Goccia · ora", style = Testi.Piccolo.copy(color = Colori.Testo2))
            }
            val dove = luogo?.let { "vicino a $it" } ?: "vicino a te"
            val mostrato = minOf(prezzo, soglia)
            Text("${carburante.etichetta} a ${Formati.prezzo(mostrato)} ${carburante.unita} $dove", style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold))
            Text(
                "${distributore ?: "Il distributore più conveniente"} · sotto la tua soglia di ${Formati.prezzo(soglia)} ${carburante.unita}",
                style = Testi.Didascalia.copy(color = Colori.TestoChip),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp)) {
                Text("Naviga", style = Testi.DidascaliaForte.copy(color = Colori.Petrolio, fontWeight = FontWeight.ExtraBold))
                Text("Vedi distributore", style = Testi.DidascaliaForte.copy(color = Colori.Petrolio, fontWeight = FontWeight.ExtraBold))
            }
        }
    }
}
