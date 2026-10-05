package it.goccia.app.ui.lista

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.Distributore
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Ordinamento
import it.goccia.app.ui.Centro
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.TipoCentro
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.CampoRicerca
import it.goccia.app.ui.componenti.CardOfferta
import it.goccia.app.ui.componenti.CardScheletro
import it.goccia.app.ui.componenti.ChipScelta
import it.goccia.app.ui.componenti.RigaTempoReale
import it.goccia.app.ui.componenti.Segmentato
import it.goccia.app.ui.componenti.metaOfferta
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.stati.BannerOffline
import it.goccia.app.ui.stati.PannelloPosizione
import it.goccia.app.ui.stati.StatoVuoto
import it.goccia.app.ui.stati.SuggerimentiComuni
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SchermataLista(vm: GocciaViewModel, onDistributore: (Distributore) -> Unit, onMappa: () -> Unit) {
    LaunchedEffect(Unit) { vm.avvia() }
    val dati by vm.dati.collectAsStateWithLifecycle()
    val utente by vm.utente.collectAsStateWithLifecycle()
    // la lista e l'altra faccia della mappa: mostra la stessa ricerca (anche "Cerca in quest'area")
    val statoVista by vm.vistaMappa.collectAsStateWithLifecycle()
    val vista = statoVista
    val area by vm.areaMappa.collectAsStateWithLifecycle()

    var testo by rememberSaveable { mutableStateOf("") }
    var ordinamento by rememberSaveable { mutableStateOf(Ordinamento.PREZZO) }
    var bandiera by rememberSaveable { mutableStateOf<String?>(null) }
    var menuBandiere by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Box(Modifier.zIndex(2f)) {
            Column {
                Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CampoRicerca(testo, { testo = it }, Modifier.weight(1f), segnaposto = "Cerca un comune")
                    BottoneIcona(
                        Icone.Mappa, "Vedi sulla mappa", onMappa,
                        colore = Color.White, sfondo = Colori.Inchiostro, dimensione = 52.dp, forma = RoundedCornerShape(16.dp),
                    )
                }
                val centro = dati.centro
                if ((centro != null || area != null) && testo.isEmpty()) {
                    Row(
                        Modifier.padding(start = 22.dp, end = 20.dp, top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icone.Segnaposto, null, tint = Colori.Petrolio, modifier = Modifier.size(15.dp))
                        if (area != null) {
                            // zona cercata sulla mappa: si torna al centro (posizione, luogo o comune)
                            Text("Area cercata sulla mappa", style = Testi.DidascaliaForte, modifier = Modifier.weight(1f, fill = false))
                            Text(
                                "· " + (centro?.let { tornaA(it) } ?: "annulla"),
                                style = Testi.DidascaliaForte.copy(color = Colori.Petrolio),
                                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.annullaArea() }.padding(4.dp),
                            )
                        } else if (centro != null) {
                            Text(centro.etichetta, style = Testi.DidascaliaForte, modifier = Modifier.weight(1f, fill = false))
                            if (centro.tipo != TipoCentro.POSIZIONE) {
                                Text(
                                    "· vicino a me",
                                    style = Testi.DidascaliaForte.copy(color = Colori.Petrolio),
                                    modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { vm.usaPosizione() }.padding(4.dp),
                                )
                            }
                        }
                    }
                }
            }
            if (testo.isNotBlank()) {
                SuggerimentiComuni(
                    vm.cercaComuni(testo),
                    onScelto = {
                        testo = ""
                        vm.centraSu(it)
                    },
                    modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 64.dp),
                )
            }
        }

        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Carburante.entries.forEach { c ->
                ChipScelta(c.etichetta, c == utente.carburante, onClick = { vm.scegliCarburante(c) }, altezza = 38.dp)
            }
        }

        val carburante = utente.carburante
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val etichettaModo = when {
                !carburante.haSelf -> "Solo servito"
                utente.impostazioni.preferisciSelf -> "Self"
                else -> "Servito"
            }
            ChipScelta(etichettaModo, false, onClick = {
                if (carburante.haSelf) vm.impostazioni { it.copy(preferisciSelf = !it.preferisciSelf) }
            }, icona = Icone.Ordina)
            ChipScelta(
                "Escludi autostrade",
                utente.impostazioni.escludiAutostrade,
                onClick = { vm.impostazioni { it.copy(escludiAutostrade = !it.escludiAutostrade) } },
                icona = Icone.Autostrada,
            )
            Box {
                ChipScelta(bandiera ?: "Bandiera", bandiera != null, onClick = { menuBandiere = true }, icona = Icone.Bandiera)
                val bandiere = vista?.zona?.offerte?.map { it.distributore.bandiera.ifBlank { "Pompa bianca" } }
                    ?.groupingBy { it }?.eachCount()?.entries?.sortedByDescending { it.value }?.map { it.key }.orEmpty()
                DropdownMenu(expanded = menuBandiere, onDismissRequest = { menuBandiere = false }) {
                    DropdownMenuItem(text = { Text("Tutte le bandiere", style = Testi.Chip) }, onClick = {
                        bandiera = null
                        menuBandiere = false
                    })
                    bandiere.forEach { b ->
                        DropdownMenuItem(
                            text = { Text(b, style = Testi.Chip) },
                            onClick = {
                                bandiera = b
                                menuBandiere = false
                            },
                            leadingIcon = { if (b == bandiera) Icon(Icone.Spunta, null, tint = Colori.Petrolio, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }
        }

        Segmentato(
            Ordinamento.entries.map { it.etichetta },
            Ordinamento.entries.indexOf(ordinamento),
            { ordinamento = Ordinamento.entries[it] },
            Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp),
        )

        val offerte = vista?.let { v ->
            Convenienza.ordina(
                v.zona.offerte.filter { bandiera == null || it.distributore.bandiera.ifBlank { "Pompa bianca" } == bandiera },
                ordinamento,
            )
        }
        if (vista != null) {
            Row(
                Modifier.padding(start = 22.dp, end = 20.dp, top = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icone.Info, null, tint = Colori.Testo3, modifier = Modifier.size(15.dp))
                val riga = if (ordinamento == Ordinamento.CONVENIENZA) {
                    "Conta anche il carburante per arrivarci, andata e ritorno"
                } else {
                    val media = vista.zona.media?.let { " · media ${Formati.prezzo(it)} ${carburante.unita}" } ?: ""
                    "${offerte?.size ?: 0} distributori entro ${vista.zona.raggioUsatoKm} km$media"
                }
                Text(riga, style = Testi.Didascalia.copy(color = Colori.Testo2, fontWeight = FontWeight.Medium), maxLines = 1)
            }
            RigaTempoReale(
                vm.inTempoReale(dati, vista.centro.coordinate),
                dati.tempoRealeIl,
                dati.tempoRealeErrore,
                Modifier.padding(start = 26.dp, end = 20.dp, top = 6.dp),
            )
        }

        PullToRefreshBox(isRefreshing = dati.aggiornando, onRefresh = { vm.aggiorna() }, modifier = Modifier.weight(1f)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (dati.offline && !dati.senzaDati) {
                    item(key = "offline") { BannerOffline(dati.scaricatoIl, onRiprova = { vm.aggiorna() }) }
                }
                when {
                    dati.centro == null && area == null && !dati.caricamento && !dati.cercoPosizione -> item(key = "posizione") {
                        PannelloPosizione(vm, dati, onLuogo = {})
                    }
                    vista == null || offerte == null || dati.caricamento -> items(6, key = { "s$it" }) { CardScheletro() }
                    offerte.isEmpty() -> item(key = "vuoto") {
                        StatoVuoto(
                            icona = Icone.Filtro,
                            titolo = "Nessun distributore con questi filtri",
                            testo = "Prova a togliere un filtro o a cambiare carburante.",
                            azione = if (bandiera != null || utente.impostazioni.escludiAutostrade) "Rimuovi i filtri" else null,
                            onAzione = {
                                bandiera = null
                                vm.impostazioni { it.copy(escludiAutostrade = false) }
                            },
                        )
                    }
                    else -> items(offerte, key = { it.distributore.id }) { o ->
                        val meta = if (ordinamento == Ordinamento.CONVENIENZA) {
                            val litri = vm.litriPieno(utente, carburante, vista.adessoMillis)
                            "${Formati.km(o.distanzaKm)} · ${Formati.numero(litri, 0)} ${if (carburante == Carburante.METANO) "kg" else "l"} + tragitto: ${Formati.euro(o.costoReale)}"
                        } else {
                            metaOfferta(o, vista.adessoMillis)
                        }
                        CardOfferta(o, carburante, meta, onClick = { onDistributore(o.distributore) })
                    }
                }
                val nascosti = vista?.zona?.nascosti ?: 0
                if (nascosti > 0 && !offerte.isNullOrEmpty() && !dati.caricamento) {
                    item(key = "nascosti") {
                        Text(
                            if (nascosti == 1) "1 distributore non è in elenco: non comunica i prezzi da oltre un mese, forse ha chiuso."
                            else "$nascosti distributori non sono in elenco: non comunicano i prezzi da oltre un mese, forse hanno chiuso.",
                            style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Il link per tornare dalla zona cercata sulla mappa al centro: "vicino a me", "vicino a Casa", "Bologna (BO)". */
private fun tornaA(centro: Centro): String = when (centro.tipo) {
    TipoCentro.POSIZIONE -> "vicino a me"
    else -> if (centro.etichetta.startsWith("Vicino")) centro.etichetta.replaceFirstChar { it.lowercase() } else centro.etichetta
}
