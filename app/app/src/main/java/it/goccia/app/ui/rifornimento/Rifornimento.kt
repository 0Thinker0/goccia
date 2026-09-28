package it.goccia.app.ui.rifornimento

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.dati.Carburante
import it.goccia.app.dati.DatiUtente
import it.goccia.app.dati.Distributore
import it.goccia.app.dati.Rifornimento
import it.goccia.app.logica.Consiglio
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Statistiche
import it.goccia.app.logica.meseDi
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.componenti.BottoneIcona
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.BottoneSecondario
import it.goccia.app.ui.componenti.CampoTesto
import it.goccia.app.ui.componenti.Interruttore
import it.goccia.app.ui.componenti.IntestazioneFoglio
import it.goccia.app.ui.componenti.LogoBandiera
import it.goccia.app.ui.componenti.Opzione
import it.goccia.app.ui.componenti.Segmentato
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra
import kotlin.math.abs

@Composable
fun SchermataRifornimento(vm: GocciaViewModel, provincia: String?, id: Long?, onChiudi: () -> Unit) {
    val utente by vm.utente.collectAsStateWithLifecycle()
    val statoVista by vm.vista.collectAsStateWithLifecycle()
    val iniziale by produceState<Distributore?>(id?.let { vm.distributoreCaricato(it) }, provincia, id) {
        if (provincia != null && id != null) value = vm.trovaDistributore(provincia, id)
    }
    var scelto by remember { mutableStateOf<Distributore?>(null) }
    val distributore = scelto ?: iniziale
    var salvato by remember { mutableStateOf<Rifornimento?>(null) }

    val auto = utente.autoCorrente?.takeIf { !it.alimentazione.elettrica }
    var carburante by rememberSaveable { mutableStateOf(utente.carburante) }
    var self by rememberSaveable { mutableStateOf(utente.impostazioni.preferisciSelf) }
    var testoPrezzo by rememberSaveable { mutableStateOf("") }
    var modo by rememberSaveable { mutableIntStateOf(0) }
    var testoQuantita by rememberSaveable { mutableStateOf("") }
    var testoKm by rememberSaveable { mutableStateOf("") }
    var pieno by rememberSaveable { mutableStateOf(true) }
    var nomeLibero by rememberSaveable { mutableStateOf("") }
    var menuCarburante by remember { mutableStateOf(false) }
    var menuDistributore by remember { mutableStateOf(false) }

    // prezzo del distributore come punto di partenza
    LaunchedEffect(distributore?.id, carburante, self) {
        val d = distributore ?: return@LaunchedEffect
        val p = Convenienza.prezzoPer(d, carburante, self)
        if (p != null) {
            testoPrezzo = Formati.prezzo(p.first.millesimi)
            if (carburante.haSelf) self = p.second
        }
    }

    salvato?.let { r ->
        Confermato(vm, r, onChiudi)
        return
    }

    val prezzo = Formati.leggiPrezzo(testoPrezzo)
    val quantita = Formati.leggiNumero(testoQuantita)
    val litri = when {
        quantita == null || prezzo == null -> null
        modo == 0 -> quantita / (prezzo / 1000.0)
        else -> quantita
    }
    val importo = when {
        quantita == null || prezzo == null -> null
        modo == 0 -> quantita
        else -> quantita * prezzo / 1000.0
    }
    val media = distributore?.let { vm.mediaAttorno(it, carburante, self) } ?: statoVista?.zona?.media
    val unitaQuantita = if (carburante == Carburante.METANO) "kg" else "l"

    Column(
        Modifier
            .fillMaxSize()
            .background(Colori.Superficie)
            .statusBarsPadding()
            .imePadding(),
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            IntestazioneFoglio("Registra rifornimento", onChiudi)

            // distributore
            Box {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Colori.Sfondo)
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (distributore != null) {
                        LogoBandiera(distributore.bandiera, distributore.pompaBianca)
                    } else {
                        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Colori.GrigioBadge), contentAlignment = Alignment.Center) {
                            Icon(Icone.Pompa, null, tint = Colori.Testo3, modifier = Modifier.size(20.dp))
                        }
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(distributore?.titolo ?: "Quale distributore?", style = Testi.CorpoForte.copy(fontWeight = FontWeight.ExtraBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "Oggi, ${Formati.ora(System.currentTimeMillis())}" + (auto?.let { " · ${it.nome}" } ?: ""),
                            style = Testi.Didascalia.copy(color = Colori.Testo2),
                        )
                    }
                    Text(
                        "Cambia",
                        style = Testi.DidascaliaForte.copy(color = Colori.Petrolio),
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { menuDistributore = true }.padding(8.dp),
                    )
                }
                DropdownMenu(expanded = menuDistributore, onDismissRequest = { menuDistributore = false }) {
                    val vicini = statoVista?.zona?.offerte?.sortedBy { it.distanzaKm }?.take(8).orEmpty()
                    vicini.forEach { o ->
                        DropdownMenuItem(
                            text = {
                                Text("${o.distributore.titolo} · ${Formati.km(o.distanzaKm)}", style = Testi.Chip, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            onClick = {
                                scelto = o.distributore
                                menuDistributore = false
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Un altro distributore", style = Testi.Chip) },
                        onClick = {
                            scelto = null
                            menuDistributore = false
                        },
                    )
                }
            }
            if (distributore == null) {
                CampoTesto(nomeLibero, { nomeLibero = it }, etichetta = "Nome del distributore (facoltativo)", segnaposto = "Es. Esso di via Roma")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Carburante", style = Testi.DidascaliaForte.copy(color = Colori.TestoChip))
                    Box {
                        val forma = RoundedCornerShape(14.dp)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .clip(forma)
                                .border(1.5.dp, Colori.BordoControllo, forma)
                                .clickable { menuCarburante = true }
                                .padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                carburante.etichetta + if (carburante.haSelf) (if (self) " self" else " servito") else "",
                                style = Testi.CorpoForte,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                            )
                            Icon(Icone.ChevronGiu, null, tint = Colori.Testo3, modifier = Modifier.size(16.dp))
                        }
                        DropdownMenu(expanded = menuCarburante, onDismissRequest = { menuCarburante = false }) {
                            Carburante.entries.forEach { c ->
                                val modi = if (c.haSelf) listOf(true, false) else listOf(true)
                                modi.forEach { s ->
                                    DropdownMenuItem(
                                        text = { Text(c.etichetta + if (c.haSelf) (if (s) " self" else " servito") else "", style = Testi.Chip) },
                                        onClick = {
                                            carburante = c
                                            self = s
                                            menuCarburante = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                CampoTesto(
                    testoPrezzo,
                    { testoPrezzo = it },
                    Modifier.weight(1f),
                    etichetta = "Prezzo pagato",
                    suffisso = carburante.unita,
                    tastiera = KeyboardType.Decimal,
                    stile = Testi.CorpoForte.copy(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
                )
            }

            Segmentato(listOf("Importo in €", if (unitaQuantita == "kg") "Chili" else "Litri"), modo, {
                // convertiamo il valore gia scritto
                if (it != modo && quantita != null && prezzo != null) {
                    testoQuantita = if (it == 1) Formati.numero(quantita / (prezzo / 1000.0), 2) else Formati.numero(quantita * prezzo / 1000.0, 2)
                }
                modo = it
            })

            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = testoQuantita,
                        onValueChange = { testoQuantita = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }.take(7) },
                        singleLine = true,
                        textStyle = Testi.Importo.copy(textAlign = TextAlign.End),
                        cursorBrush = SolidColor(Colori.Petrolio),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(170.dp),
                        decorationBox = { campo ->
                            Box(contentAlignment = Alignment.CenterEnd) {
                                if (testoQuantita.isEmpty()) Text("0", style = Testi.Importo.copy(color = Colori.InterruttoreSpento, textAlign = TextAlign.End), modifier = Modifier.fillMaxWidth())
                                campo()
                            }
                        },
                    )
                    Text(if (modo == 0) " €" else " $unitaQuantita", style = Testi.Importo)
                }
                val altro = if (modo == 0) litri?.let { "= ${Formati.numero(it, 2)} ${if (unitaQuantita == "kg") "kg" else "litri"}" }
                else importo?.let { "= ${Formati.euro(it)}" }
                Text(altro ?: "Scrivi l'importo o scegli qui sotto", style = Testi.Corpo.copy(color = Colori.Testo2, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(20.0, 30.0, 50.0).forEach { euro ->
                    val etichetta = if (modo == 0 || prezzo == null) "${Formati.numero(euro, 0)} €" else "${Formati.numero(euro / (prezzo / 1000.0), 1)} $unitaQuantita"
                    val attivo = importo != null && abs(importo - euro) < 0.01
                    Opzione(etichetta, attivo, onClick = {
                        testoQuantita = if (modo == 0 || prezzo == null) Formati.numero(euro, 2) else Formati.numero(euro / (prezzo / 1000.0), 2)
                        pieno = false
                    }, Modifier.weight(1f), altezza = 40.dp)
                }
                Opzione("Pieno", pieno && litri != null && auto != null && abs(litri - litriPerPieno(vm, utente)) < 0.05, onClick = {
                    val l = litriPerPieno(vm, utente)
                    testoQuantita = if (modo == 1 || prezzo == null) Formati.numero(l, 2) else Formati.numero(l * prezzo / 1000.0, 2)
                    pieno = true
                }, Modifier.weight(1f), altezza = 40.dp)
            }

            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CampoTesto(
                    testoKm,
                    { testoKm = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    Modifier.weight(1f),
                    etichetta = "Contachilometri (facoltativo)",
                    suffisso = "km",
                    tastiera = KeyboardType.Number,
                    stile = Testi.CorpoForte.copy(fontSize = 16.sp, fontFeatureSettings = "tnum"),
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Pieno", style = Testi.DidascaliaForte.copy(color = Colori.TestoChip))
                    Box(Modifier.height(52.dp), contentAlignment = Alignment.Center) { Interruttore(pieno, { pieno = it }, descrizione = "Pieno") }
                }
            }
            Text(
                if (pieno) "Con il pieno e i chilometri calcoliamo il consumo reale dall'ultimo pieno."
                else "Rifornimento parziale: conta per la spesa, e per il consumo quando rifarai il pieno.",
                style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium),
            )

            if (media != null && prezzo != null && litri != null) {
                val risparmio = (media - prezzo) / 1000.0 * litri
                val positivo = risparmio >= 0.005
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(if (positivo) Colori.VerdeChiaro else Colori.Grigio).padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier.size(36.dp).clip(CircleShape).background(if (positivo) Colori.VerdeTesto else Colori.Testo3),
                        contentAlignment = Alignment.Center,
                    ) { Icon(if (positivo) Icone.InCalo else Icone.Info, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
                    Text(
                        if (positivo) "Risparmi ${Formati.euro(risparmio)} rispetto alla media della zona (${Formati.prezzo(media)} ${carburante.unita})"
                        else "Paghi ${Formati.euro(-risparmio)} più della media della zona (${Formati.prezzo(media)} ${carburante.unita})",
                        style = Testi.Testo14.copy(color = if (positivo) Colori.VerdeScuro else Colori.TestoChip, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        val valido = prezzo != null && litri != null && litri > 0.2 && importo != null
        BottonePrimario(
            "Salva rifornimento",
            onClick = {
                if (!valido || prezzo == null || litri == null || importo == null) return@BottonePrimario
                val r = Rifornimento(
                    id = vm.nuovoId(),
                    quando = System.currentTimeMillis(),
                    autoId = auto?.id,
                    distributoreId = distributore?.id,
                    provincia = distributore?.provincia,
                    distributore = distributore?.titolo ?: nomeLibero.ifBlank { "Distributore" },
                    carburante = carburante.codice,
                    self = self || !carburante.haSelf,
                    prezzo = prezzo,
                    litri = litri,
                    importo = importo,
                    km = Formati.leggiIntero(testoKm),
                    pieno = pieno,
                    mediaZona = media,
                )
                vm.salvaRifornimento(r)
                salvato = r
            },
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 16.dp),
            abilitato = valido,
            altezza = 54.dp,
            forma = RoundedCornerShape(16.dp),
        )
    }
}

private fun litriPerPieno(vm: GocciaViewModel, utente: DatiUtente): Double {
    val auto = utente.autoCorrente ?: return 40.0
    val livello = vm.serbatoio(utente)?.livello ?: auto.livello
    return (auto.capienza * (1 - livello)).coerceIn(5.0, auto.capienza.coerceAtLeast(5.0))
}

@Composable
private fun Confermato(vm: GocciaViewModel, r: Rifornimento, onChiudi: () -> Unit) {
    val utente by vm.utente.collectAsStateWithLifecycle()
    val adesso = System.currentTimeMillis()
    val riepilogo = Statistiche.risparmio(utente.rifornimenti, adesso)
    val auto = utente.autoCorrente
    val consumo = auto?.let { a -> Statistiche.consumo(utente.rifornimenti.filter { it.autoId == null || it.autoId == a.id }) }
    Column(
        Modifier.fillMaxSize().background(Colori.Sfondo).statusBarsPadding().navigationBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            BottoneIcona(Icone.Chiudi, "Chiudi", onChiudi)
        }
        Box(Modifier.padding(top = 12.dp).size(180.dp).clip(CircleShape).background(Colori.PetrolioChiaro), contentAlignment = Alignment.Center) {
            Box(Modifier.size(120.dp).ombra(CircleShape, 8.dp).clip(CircleShape).background(Colori.Petrolio), contentAlignment = Alignment.Center) {
                Icon(Icone.Spunta, null, tint = Color.White, modifier = Modifier.size(60.dp))
            }
        }
        Text("Rifornimento salvato", style = Testi.Titolo.copy(fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Colori.Testo2), modifier = Modifier.padding(top = 20.dp))
        val risparmio = r.risparmio
        if (risparmio != null && risparmio > 0.005) {
            Text(Formati.euro(risparmio), style = Testi.Importo.copy(fontSize = 48.sp))
            Text("risparmiati rispetto alla media della zona", style = Testi.CorpoGrande.copy(color = Colori.TestoChip, fontWeight = FontWeight.SemiBold))
        } else {
            Text(Formati.euro(r.importo), style = Testi.Importo.copy(fontSize = 48.sp))
            Text("${Formati.numero(r.litri, 2)} ${if (r.carburante == "M") "kg" else "litri"} a ${Formati.prezzo(r.prezzo)}", style = Testi.CorpoGrande.copy(color = Colori.TestoChip, fontWeight = FontWeight.SemiBold))
        }
        Column(
            Modifier
                .padding(start = 20.dp, end = 20.dp, top = 26.dp)
                .fillMaxWidth()
                .ombra(RoundedCornerShape(20.dp))
                .clip(RoundedCornerShape(20.dp))
                .background(Colori.Superficie)
                .padding(horizontal = 18.dp, vertical = 6.dp),
        ) {
            Riga("Risparmio a ${Formati.meseLungo(meseDi(adesso))}", Formati.euro(riepilogo.meseCorrente))
            Riga("Totale con Goccia", Formati.euro(riepilogo.totale))
            if (consumo != null && auto != null) Riga("Consumo medio dai pieni", "${Formati.numero(consumo, 1)} ${auto.alimentazione.unitaConsumo}", ultima = true)
        }
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            BottonePrimario("Fatto", onChiudi, Modifier.fillMaxWidth(), altezza = 56.dp, forma = RoundedCornerShape(16.dp))
            if (auto != null) {
                val stato = Consiglio.serbatoio(auto, utente.rifornimenti, adesso)
                BottoneSecondario("Autonomia ora ~${stato.autonomiaKm} km", {}, Modifier.fillMaxWidth(), altezza = 52.dp, abilitato = false)
            }
        }
    }
}

@Composable
private fun Riga(titolo: String, valore: String, ultima: Boolean = false) {
    Column {
        Row(Modifier.fillMaxWidth().height(54.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(titolo, style = Testi.Chip.copy(color = Colori.TestoChip), modifier = Modifier.weight(1f))
            Text(valore, style = Testi.Sottosezione.copy(fontFeatureSettings = "tnum"))
        }
        if (!ultima) Box(Modifier.fillMaxWidth().height(1.dp).background(Colori.Divisore))
    }
}
