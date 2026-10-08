package it.goccia.app.ui.profilo

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.BuildConfig
import it.goccia.app.avvisi.Notifiche
import it.goccia.app.dati.AppNavigazione
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.TariffeColonnine
import it.goccia.app.dati.TipoLuogo
import it.goccia.app.logica.Elettrico
import it.goccia.app.logica.FonteTariffa
import it.goccia.app.logica.Formati
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.apriLink
import it.goccia.app.ui.componenti.BarraTitolo
import it.goccia.app.ui.componenti.CREDITI
import it.goccia.app.ui.componenti.Gruppo
import it.goccia.app.ui.componenti.RigaInterruttore
import it.goccia.app.ui.componenti.RigaVoce
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi
import it.goccia.app.widget.Widget
import it.goccia.app.widget.WidgetAutoReceiver
import it.goccia.app.widget.WidgetPrezziReceiver
import kotlinx.coroutines.launch

private enum class Dialogo { CARBURANTE, MODALITA, RAGGIO, NAVIGAZIONE, RIPRISTINO }

@Composable
fun SchermataImpostazioni(
    vm: GocciaViewModel,
    onIndietro: () -> Unit,
    onLuogo: (String?, String?) -> Unit,
    onSostieni: () -> Unit,
    onTariffa: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val utente by vm.utente.collectAsStateWithLifecycle()
    val imp = utente.impostazioni
    var dialogo by remember { mutableStateOf<Dialogo?>(null) }
    var notifiche by remember { mutableStateOf(Notifiche.permesso(context)) }
    val richiestaNotifiche = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { notifiche = Notifiche.permesso(context) }
    val esportaCsv = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scriviFile(context, uri, csvRifornimenti(utente))
    }
    val salvaBackup = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scriviFile(context, uri, vm.esportaBackup())
    }
    val apriBackup = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val testo = leggiFile(context, uri)
            scope.launch {
                val riuscito = testo != null && vm.importaBackup(testo)
                Toast.makeText(context, if (riuscito) "Backup ripristinato" else "Questo file non è un backup di Goccia", Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        BarraTitolo("Impostazioni", onIndietro)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Gruppo("Rifornimento") {
                RigaVoce(
                    "Carburante predefinito",
                    valore = imp.carburante?.let { Carburante.daCodice(it)?.etichetta } ?: "Dell'auto",
                    onClick = { dialogo = Dialogo.CARBURANTE },
                )
                RigaVoce("Modalità di servizio", valore = if (imp.preferisciSelf) "Self" else "Servito", onClick = { dialogo = Dialogo.MODALITA })
                RigaVoce("Raggio di ricerca", valore = "${imp.raggioKm} km", onClick = { dialogo = Dialogo.RAGGIO })
                RigaInterruttore(
                    "Nascondi distributori autostradali",
                    imp.escludiAutostrade,
                    { v -> vm.impostazioni { it.copy(escludiAutostrade = v) } },
                    divisore = false,
                )
            }

            Gruppo("Auto elettrica") {
                val stime = vm.stime
                val prezzo = Elettrico.prezzoCasa(utente.tariffaCasa, stime.casa)
                RigaVoce(
                    "Tariffa di casa",
                    sottotitolo = "Prezzo al kWh, fasce orarie, fotovoltaico",
                    valore = Formati.numero(prezzo.euroKwh, if (prezzo.fonte == FonteTariffa.TUA) 3 else 2) +
                        if (prezzo.fonte == FonteTariffa.STIMA) " · stima" else "",
                    altezza = 58.dp,
                    onClick = onTariffa,
                )
                RigaVoce(
                    "Tariffe alle colonnine",
                    sottotitolo = "Lenta, veloce e ultraveloce",
                    valore = if (utente.tariffeColonnine == TariffeColonnine()) "stime" else "le tue",
                    altezza = 58.dp,
                    divisore = false,
                    onClick = onTariffa,
                )
            }

            Gruppo("Luoghi salvati") {
                utente.luoghi.forEach { l ->
                    RigaVoce(
                        l.nome,
                        sottotitolo = l.descrizione.ifBlank { "Posizione salvata" },
                        icona = if (l.tipo == TipoLuogo.LAVORO) Icone.Lavoro else Icone.Casa,
                        coloreIcona = if (l.tipo == TipoLuogo.LAVORO) Colori.Lavoro else Colori.Petrolio,
                        altezza = 58.dp,
                        onClick = { onLuogo(l.id, null) },
                    )
                }
                RigaVoce(
                    "Aggiungi un luogo",
                    icona = Icone.Piu,
                    divisore = false,
                    onClick = { onLuogo(null, if (utente.casa == null) "CASA" else "ALTRO") },
                ) {}
            }

            Gruppo("Notifiche") {
                if (!notifiche && Build.VERSION.SDK_INT >= 33) {
                    RigaVoce(
                        "Consenti le notifiche",
                        sottotitolo = "Senza il permesso gli avvisi non possono arrivarti",
                        icona = Icone.Attenzione,
                        coloreIcona = Colori.AmbraScuro,
                        altezza = 62.dp,
                        onClick = { richiestaNotifiche.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    )
                }
                RigaInterruttore(
                    "Avvisi di prezzo",
                    imp.avvisiPrezzo,
                    { v -> vm.impostazioni { it.copy(avvisiPrezzo = v) } },
                    sottotitolo = "Quando in zona si scende sotto le tue soglie",
                )
                RigaInterruttore(
                    "Calo dei preferiti",
                    imp.caloPreferiti,
                    { v -> vm.impostazioni { it.copy(caloPreferiti = v) } },
                    sottotitolo = "Quando un preferito abbassa il prezzo",
                )
                RigaInterruttore(
                    "Orario silenzioso",
                    imp.orarioSilenzioso,
                    { v -> vm.impostazioni { it.copy(orarioSilenzioso = v) } },
                    sottotitolo = "Nessuna notifica dalle 21:00 alle 07:00",
                    divisore = false,
                )
            }

            Gruppo("Navigazione") {
                RigaVoce("App di navigazione", valore = imp.navigazione.etichetta, divisore = false, onClick = { dialogo = Dialogo.NAVIGAZIONE })
            }

            if (remember { Widget.puoAggiungere(context) }) {
                Gruppo("Widget per la schermata Home") {
                    fun aggiungi(ricevitore: Class<out GlanceAppWidgetReceiver>) {
                        if (!Widget.aggiungi(context, ricevitore)) {
                            Toast.makeText(context, "Tieni premuto sulla schermata Home e scegli Widget, poi Goccia", Toast.LENGTH_LONG).show()
                        }
                    }
                    RigaVoce(
                        "Prezzi vicino a te",
                        sottotitolo = "Il più conveniente e altri due in zona",
                        icona = Icone.Pompa,
                        altezza = 58.dp,
                        onClick = { aggiungi(WidgetPrezziReceiver::class.java) },
                    ) { Icon(Icone.Piu, null, tint = Colori.Petrolio, modifier = Modifier.size(18.dp)) }
                    RigaVoce(
                        "La tua auto",
                        sottotitolo = "Serbatoio o batteria stimati",
                        icona = Icone.Auto,
                        altezza = 58.dp,
                        divisore = false,
                        onClick = { aggiungi(WidgetAutoReceiver::class.java) },
                    ) { Icon(Icone.Piu, null, tint = Colori.Petrolio, modifier = Modifier.size(18.dp)) }
                }
            }

            Gruppo("Dati e backup") {
                RigaVoce(
                    "Esporta rifornimenti",
                    sottotitolo = "CSV per fogli di calcolo e note spese",
                    icona = Icone.Scarica,
                    altezza = 62.dp,
                    onClick = { esportaCsv.launch("goccia-rifornimenti.csv") },
                )
                RigaVoce(
                    "Salva un backup",
                    sottotitolo = "Auto, rifornimenti, preferiti, avvisi e luoghi",
                    icona = Icone.Nuvola,
                    altezza = 62.dp,
                    onClick = { salvaBackup.launch("goccia-backup.json") },
                )
                RigaVoce(
                    "Ripristina da un backup",
                    sottotitolo = "Sostituisce i dati attuali",
                    icona = Icone.Carica,
                    altezza = 62.dp,
                    divisore = false,
                    onClick = { dialogo = Dialogo.RIPRISTINO },
                )
            }

            Gruppo("Privacy e fonti dei dati") {
                Riga(Icone.Scudo, Colori.Petrolio) {
                    Text(
                        "Nessun account, nessuna pubblicità, nessun tracciamento. I tuoi dati restano sul telefono (e nel backup di Android, se attivo).",
                        style = Testi.Didascalia.copy(color = Colori.TestoChip),
                    )
                }
                Riga(Icone.Database, Colori.Testo3) {
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("Prezzi: ") }
                            append("Ministero delle Imprese e del Made in Italy – Osservaprezzi carburanti: file di ogni mattina (licenza IODL 2.0) e prezzi in vigore letti in tempo reale. ")
                            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("Colonnine: ") }
                            append("GSE – Piattaforma Unica Nazionale (PUN), licenza CC BY 4.0; in sua assenza © OpenStreetMap contributors, licenza ODbL. ")
                            withStyle(SpanStyle(fontWeight = FontWeight.ExtraBold)) { append("Mappe: ") }
                            append("© OpenStreetMap, OpenFreeMap, OpenMapTiles.")
                        },
                        style = Testi.Didascalia.copy(color = Colori.TestoChip),
                    )
                }
                RigaVoce(
                    "Informativa sulla privacy",
                    divisore = false,
                    onClick = { apriLink(context, BuildConfig.PRIVACY_URL) },
                )
            }

            Gruppo("Info") {
                RigaVoce("Sostieni il progetto", icona = Icone.Cuore, coloreIcona = Colori.AmbraScuro, onClick = onSostieni)
                RigaVoce("Codice sorgente", valore = "GitHub", onClick = { apriLink(context, "https://github.com/${BuildConfig.REPO}") })
                RigaVoce("Versione", valore = BuildConfig.VERSION_NAME) {}
                RigaVoce("Crediti", sottotitolo = CREDITI, divisore = false, altezza = 68.dp) {}
            }
        }
    }

    when (dialogo) {
        Dialogo.CARBURANTE -> {
            val opzioni = listOf<Carburante?>(null) + Carburante.entries
            DialogoScelta(
                "Carburante predefinito",
                opzioni.map { it?.etichetta ?: "Quello dell'auto attiva" },
                opzioni.indexOf(imp.carburante?.let { Carburante.daCodice(it) }),
                onScelta = { i -> vm.impostazioni { it.copy(carburante = opzioni[i]?.codice) } },
                onChiudi = { dialogo = null },
            )
        }
        Dialogo.MODALITA -> DialogoScelta(
            "Modalità di servizio",
            listOf("Self", "Servito"),
            if (imp.preferisciSelf) 0 else 1,
            onScelta = { i -> vm.impostazioni { it.copy(preferisciSelf = i == 0) } },
            onChiudi = { dialogo = null },
        )
        Dialogo.RAGGIO -> {
            val raggi = listOf(3, 5, 10, 20)
            DialogoScelta(
                "Raggio di ricerca",
                raggi.map { "$it km" },
                raggi.indexOf(imp.raggioKm),
                onScelta = { i -> vm.impostazioni { it.copy(raggioKm = raggi[i]) } },
                onChiudi = { dialogo = null },
                nota = "Se nel raggio ci sono meno di 5 distributori lo allarghiamo da soli.",
            )
        }
        Dialogo.NAVIGAZIONE -> DialogoScelta(
            "App di navigazione",
            AppNavigazione.entries.map { it.etichetta },
            AppNavigazione.entries.indexOf(imp.navigazione),
            onScelta = { i -> vm.impostazioni { it.copy(navigazione = AppNavigazione.entries[i]) } },
            onChiudi = { dialogo = null },
        )
        Dialogo.RIPRISTINO -> AlertDialog(
            onDismissRequest = { dialogo = null },
            containerColor = Colori.Superficie,
            title = { Text("Ripristinare un backup?", style = Testi.Sottosezione) },
            text = { Text("Auto, rifornimenti, preferiti, avvisi e luoghi di adesso verranno sostituiti da quelli del file.", style = Testi.Didascalia.copy(color = Colori.Testo2)) },
            confirmButton = {
                TextButton(onClick = {
                    dialogo = null
                    apriBackup.launch(arrayOf("application/json", "application/octet-stream", "text/plain"))
                }) { Text("Scegli il file", style = Testi.Pulsante.copy(color = Colori.Petrolio)) }
            },
            dismissButton = { TextButton(onClick = { dialogo = null }) { Text("Annulla", style = Testi.Pulsante.copy(color = Colori.Testo2)) } },
        )
        null -> {}
    }
}

@Composable
private fun Riga(icona: ImageVector, colore: Color, testo: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Icon(icona, null, tint = colore, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Column(Modifier.weight(1f)) { testo() }
    }
}

@Composable
fun DialogoScelta(
    titolo: String,
    opzioni: List<String>,
    scelta: Int,
    onScelta: (Int) -> Unit,
    onChiudi: () -> Unit,
    nota: String? = null,
) {
    AlertDialog(
        onDismissRequest = onChiudi,
        containerColor = Colori.Superficie,
        title = { Text(titolo, style = Testi.Sottosezione) },
        text = {
            Column {
                opzioni.forEachIndexed { i, testo ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                onScelta(i)
                                onChiudi()
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = i == scelta,
                            onClick = {
                                onScelta(i)
                                onChiudi()
                            },
                            colors = RadioButtonDefaults.colors(selectedColor = Colori.Petrolio),
                        )
                        Text(testo, style = Testi.Corpo.copy(fontWeight = FontWeight.SemiBold))
                    }
                }
                if (nota != null) {
                    Text(nota, style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium), modifier = Modifier.padding(top = 8.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onChiudi) { Text("Chiudi", style = Testi.Pulsante.copy(color = Colori.Petrolio)) } },
    )
}
