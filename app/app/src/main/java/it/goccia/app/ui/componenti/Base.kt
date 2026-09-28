package it.goccia.app.ui.componenti

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Tono
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra

// ---------- contenitori ----------

@Composable
fun Scheda(
    modifier: Modifier = Modifier,
    forma: Shape = Forme.CardGrande,
    padding: PaddingValues = PaddingValues(18.dp),
    spazio: Dp = 14.dp,
    sfondo: Color = Colori.Superficie,
    conOmbra: Boolean = true,
    onClick: (() -> Unit)? = null,
    contenuto: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .then(if (conOmbra) Modifier.ombra(forma) else Modifier)
            .clip(forma)
            .background(sfondo)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spazio),
        content = contenuto,
    )
}

@Composable
fun Riquadro(
    modifier: Modifier = Modifier,
    sfondo: Color = Colori.PetrolioTenue,
    icona: ImageVector? = Icone.Info,
    coloreIcona: Color = Colori.Petrolio,
    contenuto: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(sfondo)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (icona != null) Icon(icona, null, tint = coloreIcona, modifier = Modifier.padding(top = 1.dp).size(18.dp))
        Box(Modifier.weight(1f)) { contenuto() }
    }
}

@Composable
fun Suggerimento(testo: String, modifier: Modifier = Modifier, icona: ImageVector? = Icone.Info, sfondo: Color = Colori.PetrolioTenue) {
    Riquadro(modifier, sfondo, icona) {
        Text(testo, style = Testi.Didascalia.copy(color = Color(0xFF1F3A4A)))
    }
}

// ---------- testi ----------

@Composable
fun IntestazioneSezione(
    titolo: String,
    modifier: Modifier = Modifier,
    sottotitolo: String? = null,
    azione: String? = null,
    onAzione: () -> Unit = {},
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titolo, style = Testi.Sezione)
            if (sottotitolo != null) Text(sottotitolo, style = Testi.Didascalia.copy(color = Colori.Testo3))
        }
        if (azione != null) {
            Text(
                azione,
                style = Testi.ChipAttivo.copy(color = Colori.Petrolio),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAzione)
                    .padding(start = 8.dp, top = 10.dp, bottom = 2.dp, end = 2.dp),
            )
        }
    }
}

@Composable
fun TitoloGruppo(testo: String, modifier: Modifier = Modifier) {
    Text(testo.uppercase(), style = Testi.TitoloGruppo.copy(color = Colori.Testo3), modifier = modifier.padding(start = 4.dp))
}

// ---------- badge ----------

@Composable
fun Badge(
    testo: String,
    sfondo: Color,
    colore: Color,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
    stile: TextStyle = Testi.Etichetta,
    padding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
) {
    Row(
        modifier = modifier.clip(Forme.Pillola).background(sfondo).padding(padding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icona != null) Icon(icona, null, tint = colore, modifier = Modifier.size(13.dp))
        Text(testo, style = stile.copy(color = colore), maxLines = 1)
    }
}

fun coloriTono(tono: Tono): Pair<Color, Color> = when (tono) {
    Tono.CONVENIENTE -> Colori.VerdeChiaro to Colori.VerdeTesto
    Tono.MEDIA -> Colori.AmbraChiaro to Colori.AmbraTesto
    Tono.CARO -> Colori.RossoChiaro to Colori.Rosso
}

/** Colore del segnaposto sulla mappa */
fun colorePin(tono: Tono): Color = when (tono) {
    Tono.CONVENIENTE -> Colori.VerdeTesto
    Tono.MEDIA -> Colori.AmbraScuro
    Tono.CARO -> Colori.Rosso
}

@Composable
fun BadgeConvenienza(cent: Int?, tono: Tono, modifier: Modifier = Modifier, piccolo: Boolean = false, sfondo: Color? = null) {
    if (cent == null) return
    val (bg, fg) = coloriTono(tono)
    Badge(
        Formati.differenza(cent),
        sfondo ?: bg,
        fg,
        modifier,
        stile = if (piccolo) Testi.Minimo else Testi.Etichetta,
        padding = if (piccolo) PaddingValues(horizontal = 7.dp, vertical = 2.dp) else PaddingValues(horizontal = 8.dp, vertical = 3.dp),
    )
}

// ---------- logo della bandiera ----------

private val tavolozza = listOf(
    0xFFC2410C, 0xFF1D4ED8, 0xFF0E7490, 0xFFA16207, 0xFF6D28D9,
    0xFF33475B, 0xFFBE185D, 0xFF15803D, 0xFF4338CA, 0xFF9A3412,
).map { Color(it) }

/** Colore "di fantasia" della bandiera: sempre lo stesso per lo stesso nome, senza imitare i marchi. */
fun coloreBandiera(bandiera: String): Color = tavolozza[(bandiera.lowercase().hashCode() and 0x7fffffff) % tavolozza.size]

fun iniziali(bandiera: String): String {
    val parole = bandiera.split(Regex("[\\s\\-·.]+")).filter { it.isNotBlank() }
    return when {
        parole.isEmpty() -> "?"
        parole[0].length <= 3 && parole[0].all { it.isUpperCase() || it.isDigit() } -> parole[0]
        parole.size >= 2 -> "${parole[0][0]}${parole[1][0]}".uppercase()
        else -> parole[0].take(2).uppercase()
    }
}

@Composable
fun LogoBandiera(
    bandiera: String,
    pompaBianca: Boolean,
    modifier: Modifier = Modifier,
    dimensione: Dp = 44.dp,
    angolo: Dp = 12.dp,
    testo: TextUnit = 14.sp,
) {
    val forma = RoundedCornerShape(angolo)
    if (pompaBianca || bandiera.isBlank()) {
        Box(
            modifier
                .size(dimensione)
                .clip(forma)
                .background(Colori.Superficie)
                .border(1.5.dp, Colori.BordoControllo, forma)
                .semantics { contentDescription = "Pompa bianca" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icone.Pompa, null, tint = Colori.Testo3, modifier = Modifier.size(dimensione * 0.45f))
        }
    } else {
        Box(
            modifier.size(dimensione).clip(forma).background(coloreBandiera(bandiera)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                iniziali(bandiera),
                style = Testi.Etichetta.copy(color = Color.White, fontSize = testo, fontWeight = FontWeight.ExtraBold),
                maxLines = 1,
            )
        }
    }
}

@Composable
fun LogoApp(dimensione: Dp, modifier: Modifier = Modifier, angolo: Dp = dimensione * 0.3f) {
    Box(
        modifier.size(dimensione).clip(RoundedCornerShape(angolo)).background(Colori.Petrolio),
        contentAlignment = Alignment.Center,
    ) {
        Image(Icone.Goccia, contentDescription = null, modifier = Modifier.size(dimensione * 0.58f))
    }
}

// ---------- pulsanti ----------

@Composable
fun BottonePrimario(
    testo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
    abilitato: Boolean = true,
    altezza: Dp = 48.dp,
    forma: Shape = Forme.Pulsante,
    sfondo: Color = Colori.Petrolio,
    iconaDopo: Boolean = false,
) {
    Row(
        modifier = modifier
            .height(altezza)
            .clip(forma)
            .background(if (abilitato) sfondo else Colori.InterruttoreSpento)
            .clickable(enabled = abilitato, role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icona != null && !iconaDopo) Icon(icona, null, tint = Color.White, modifier = Modifier.size(18.dp))
        Text(testo, style = Testi.Pulsante.copy(color = Color.White, fontSize = if (altezza >= 54.dp) 16.sp else 15.sp), maxLines = 1)
        if (icona != null && iconaDopo) Icon(icona, null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun BottoneSecondario(
    testo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
    coloreIcona: Color = Colori.Inchiostro,
    altezza: Dp = 48.dp,
    forma: Shape = Forme.Pulsante,
    abilitato: Boolean = true,
) {
    Row(
        modifier = modifier
            .height(altezza)
            .clip(forma)
            .background(Colori.Superficie)
            .border(1.dp, Colori.BordoControllo, forma)
            .clickable(enabled = abilitato, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icona != null) Icon(icona, null, tint = coloreIcona, modifier = Modifier.size(18.dp))
        Text(
            testo,
            style = Testi.Pulsante.copy(color = if (abilitato) Colori.Inchiostro else Colori.Testo3, fontSize = 14.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun BottoneTesto(testo: String, onClick: () -> Unit, modifier: Modifier = Modifier, colore: Color = Colori.Petrolio) {
    Text(
        testo,
        style = Testi.Pulsante.copy(color = colore),
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
fun BottoneIcona(
    icona: ImageVector,
    descrizione: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    colore: Color = Colori.Inchiostro,
    sfondo: Color = Color.Transparent,
    bordo: Color? = null,
    dimensione: Dp = 48.dp,
    dimensioneIcona: Dp = 22.dp,
    forma: Shape = CircleShape,
) {
    Box(
        modifier = modifier
            .size(dimensione)
            .clip(forma)
            .background(sfondo)
            .then(if (bordo != null) Modifier.border(1.dp, bordo, forma) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = descrizione },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icona, null, tint = colore, modifier = Modifier.size(dimensioneIcona))
    }
}

// ---------- scelte ----------

@Composable
fun ChipScelta(
    testo: String,
    attivo: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icona: ImageVector? = null,
    conSpunta: Boolean = false,
    altezza: Dp = 36.dp,
) {
    val forma = RoundedCornerShape(altezza / 2)
    Row(
        modifier = modifier
            .height(altezza)
            .clip(forma)
            .background(if (attivo) Colori.Petrolio else Colori.Superficie)
            .then(if (attivo) Modifier else Modifier.border(1.dp, Colori.BordoControllo, forma))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(start = if (conSpunta && attivo || icona != null) 10.dp else 14.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val colore = if (attivo) Color.White else Colori.TestoChip
        when {
            conSpunta && attivo -> Icon(Icone.Spunta, null, tint = colore, modifier = Modifier.size(16.dp))
            icona != null -> Icon(icona, null, tint = colore, modifier = Modifier.size(15.dp))
        }
        Text(testo, style = (if (attivo) Testi.ChipAttivo else Testi.Chip).copy(color = colore), maxLines = 1)
    }
}

/** Opzione "radio" su sfondo chiaro, come le scelte di raggio e carburante nel foglio degli avvisi. */
@Composable
fun Opzione(
    testo: String,
    scelta: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    altezza: Dp = 38.dp,
    forma: Shape = RoundedCornerShape(12.dp),
    conSpunta: Boolean = false,
    allineamento: Alignment.Horizontal = Alignment.CenterHorizontally,
) {
    Row(
        modifier = modifier
            .height(altezza)
            .clip(forma)
            .background(if (scelta) Colori.PetrolioChiaro else Colori.Superficie)
            .border(1.5.dp, if (scelta) Colori.Petrolio else Colori.BordoControllo, forma)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (allineamento == Alignment.Start) Arrangement.SpaceBetween else Arrangement.Center,
    ) {
        Text(
            testo,
            style = Testi.Chip.copy(
                color = if (scelta) Colori.PetrolioScuro else Colori.TestoChip,
                fontWeight = if (scelta) FontWeight.ExtraBold else FontWeight.SemiBold,
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (conSpunta && scelta) Icon(Icone.Spunta, null, tint = Colori.PetrolioScuro, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun Segmentato(
    opzioni: List<String>,
    scelto: Int,
    onScelta: (Int) -> Unit,
    modifier: Modifier = Modifier,
    altezza: Dp = 38.dp,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(Colori.Segmentato)
            .padding(4.dp),
    ) {
        opzioni.forEachIndexed { i, testo ->
            val attivo = i == scelto
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(altezza)
                    .then(if (attivo) Modifier.ombra(RoundedCornerShape(11.dp), 1.dp) else Modifier)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (attivo) Colori.Superficie else Color.Transparent)
                    .clickable(role = Role.Tab) { onScelta(i) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    testo,
                    style = Testi.Chip.copy(
                        fontSize = 13.sp,
                        color = if (attivo) Colori.Inchiostro else Colori.Testo2,
                        fontWeight = if (attivo) FontWeight.ExtraBold else FontWeight.SemiBold,
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
fun Interruttore(acceso: Boolean, onCambia: (Boolean) -> Unit, modifier: Modifier = Modifier, descrizione: String? = null) {
    val sfondo by animateColorAsState(if (acceso) Colori.Petrolio else Colori.InterruttoreSpento, label = "interruttore")
    val spostamento by animateDpAsState(if (acceso) 24.dp else 4.dp, label = "pomello")
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(sfondo)
            .clickable(role = Role.Switch) { onCambia(!acceso) }
            .then(if (descrizione != null) Modifier.semantics { contentDescription = descrizione } else Modifier),
    ) {
        Box(
            Modifier
                .offset(x = spostamento, y = 4.dp)
                .size(24.dp)
                .ombra(CircleShape, 1.dp)
                .clip(CircleShape)
                .background(Colori.Superficie),
        )
    }
}

@Composable
fun Stepper(
    valore: String,
    onMeno: () -> Unit,
    onPiu: () -> Unit,
    modifier: Modifier = Modifier,
    altezza: Dp = 52.dp,
    stileValore: TextStyle = Testi.Sottosezione.copy(fontFeatureSettings = "tnum"),
    sfondoPulsanti: Color = Color.Transparent,
    descrizioneMeno: String = "Diminuisci",
    descrizionePiu: String = "Aumenta",
) {
    val forma = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .height(altezza)
            .clip(forma)
            .background(Colori.Superficie)
            .border(1.5.dp, Colori.BordoControllo, forma)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottoneIcona(
            Icone.Meno, descrizioneMeno, onMeno,
            colore = Colori.Petrolio, sfondo = sfondoPulsanti, dimensione = altezza - 8.dp, dimensioneIcona = 18.dp,
            forma = RoundedCornerShape(12.dp),
        )
        Text(valore, style = stileValore, textAlign = TextAlign.Center, modifier = Modifier.weight(1f), maxLines = 1)
        BottoneIcona(
            Icone.Piu, descrizionePiu, onPiu,
            colore = Colori.Petrolio, sfondo = sfondoPulsanti, dimensione = altezza - 8.dp, dimensioneIcona = 18.dp,
            forma = RoundedCornerShape(12.dp),
        )
    }
}

// ---------- campi di testo ----------

/**
 * Il segnaposto sta dentro il campo (decorationBox): cosi lo leggono anche TalkBack e gli
 * strumenti di accessibilita, che altrimenti lo considerano coperto dal campo e lo saltano.
 */
@Composable
private fun ConSegnaposto(vuoto: Boolean, segnaposto: String, stile: TextStyle, campo: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        if (vuoto && segnaposto.isNotEmpty()) Text(segnaposto, style = stile, maxLines = 1)
        campo()
    }
}

@Composable
fun CampoRicerca(
    testo: String,
    onTesto: (String) -> Unit,
    modifier: Modifier = Modifier,
    segnaposto: String = "Cerca comune",
    onCerca: () -> Unit = {},
    focus: FocusRequester? = null,
) {
    val forma = Forme.Campo
    Row(
        modifier = modifier
            .height(52.dp)
            .clip(forma)
            .background(Colori.Superficie)
            .border(1.dp, Colori.Bordo, forma)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icone.Cerca, null, tint = Colori.Testo3, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = testo,
            onValueChange = onTesto,
            singleLine = true,
            textStyle = Testi.Corpo,
            cursorBrush = SolidColor(Colori.Petrolio),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onCerca() }),
            modifier = Modifier.weight(1f).then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
            decorationBox = { campo -> ConSegnaposto(testo.isEmpty(), segnaposto, Testi.Corpo.copy(color = Colori.Segnaposto), campo) },
        )
        if (testo.isNotEmpty()) {
            BottoneIcona(Icone.Chiudi, "Cancella", { onTesto("") }, dimensione = 32.dp, dimensioneIcona = 16.dp, colore = Colori.Testo3)
        }
    }
}

@Composable
fun CampoTesto(
    valore: String,
    onValore: (String) -> Unit,
    modifier: Modifier = Modifier,
    etichetta: String? = null,
    suffisso: String? = null,
    tastiera: KeyboardType = KeyboardType.Text,
    stile: TextStyle = Testi.CorpoGrande.copy(fontWeight = FontWeight.SemiBold),
    segnaposto: String = "",
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (etichetta != null) Text(etichetta, style = Testi.DidascaliaForte.copy(color = Colori.TestoChip))
        val forma = RoundedCornerShape(14.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(forma)
                .background(Colori.Superficie)
                .border(1.5.dp, Colori.BordoControllo, forma)
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicTextField(
                value = valore,
                onValueChange = onValore,
                singleLine = true,
                textStyle = stile,
                cursorBrush = SolidColor(Colori.Petrolio),
                keyboardOptions = KeyboardOptions(keyboardType = tastiera, imeAction = ImeAction.Done),
                modifier = Modifier.weight(1f),
                decorationBox = { campo -> ConSegnaposto(valore.isEmpty(), segnaposto, stile.copy(color = Colori.Segnaposto), campo) },
            )
            if (suffisso != null) Text(suffisso, style = Testi.Chip.copy(color = Colori.Testo3))
        }
    }
}

// ---------- righe di elenco ----------

@Composable
fun RigaVoce(
    titolo: String,
    modifier: Modifier = Modifier,
    sottotitolo: String? = null,
    valore: String? = null,
    icona: ImageVector? = null,
    coloreIcona: Color = Colori.Petrolio,
    conSfondoIcona: Boolean = false,
    divisore: Boolean = true,
    altezza: Dp = 54.dp,
    onClick: (() -> Unit)? = null,
    finale: @Composable RowScope.() -> Unit = {
        if (onClick != null) Icon(Icone.ChevronDestra, null, tint = Colori.Testo3, modifier = Modifier.size(16.dp))
    },
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(altezza)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icona != null) {
                if (conSfondoIcona) {
                    Box(
                        Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Colori.Grigio),
                        contentAlignment = Alignment.Center,
                    ) { Icon(icona, null, tint = coloreIcona, modifier = Modifier.size(18.dp)) }
                } else {
                    Icon(icona, null, tint = coloreIcona, modifier = Modifier.size(20.dp))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(titolo, style = Testi.Corpo.copy(fontWeight = if (sottotitolo != null) FontWeight.Bold else FontWeight.SemiBold), maxLines = 1)
                if (sottotitolo != null) {
                    Text(sottotitolo, style = Testi.Piccolo.copy(color = Colori.Testo3, fontWeight = FontWeight.Medium), maxLines = 2)
                }
            }
            if (valore != null) Text(valore, style = Testi.Chip.copy(color = Colori.Testo2, fontWeight = FontWeight.Bold), maxLines = 1)
            finale()
        }
        if (divisore) Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(1.dp).background(Colori.Divisore))
    }
}

@Composable
fun RigaInterruttore(
    titolo: String,
    acceso: Boolean,
    onCambia: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    sottotitolo: String? = null,
    divisore: Boolean = true,
) {
    RigaVoce(
        titolo = titolo,
        sottotitolo = sottotitolo,
        modifier = modifier,
        divisore = divisore,
        altezza = if (sottotitolo != null) 64.dp else 54.dp,
        onClick = { onCambia(!acceso) },
    ) { Interruttore(acceso, onCambia, descrizione = titolo) }
}

@Composable
fun Gruppo(titolo: String?, modifier: Modifier = Modifier, contenuto: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (titolo != null) TitoloGruppo(titolo)
        Column(
            Modifier
                .fillMaxWidth()
                .ombra(RoundedCornerShape(18.dp), 1.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Colori.Superficie),
            content = contenuto,
        )
    }
}

// ---------- barre ----------

@Composable
fun BarraTitolo(
    titolo: String?,
    onIndietro: (() -> Unit)?,
    modifier: Modifier = Modifier,
    azioni: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().height(56.dp).padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (onIndietro != null) BottoneIcona(Icone.Indietro, "Indietro", onIndietro)
        else Spacer(Modifier.width(12.dp))
        if (titolo != null) {
            Text(titolo, style = Testi.Titolo, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        } else {
            Spacer(Modifier.weight(1f))
        }
        azioni()
    }
}

/** Intestazione dei fogli (rifornimento, nuovo avviso): titolo e X per chiudere. */
@Composable
fun IntestazioneFoglio(titolo: String, onChiudi: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(titolo, style = Testi.Foglio, modifier = Modifier.weight(1f))
        BottoneIcona(Icone.Chiudi, "Chiudi", onChiudi, sfondo = Colori.Grigio, dimensione = 44.dp, dimensioneIcona = 18.dp)
    }
}

@Composable
fun IndicatorePassi(passo: Int, totale: Int, modifier: Modifier = Modifier) {
    Row(modifier.semantics { contentDescription = "Passo ${passo + 1} di $totale" }, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(totale) { i ->
            Box(
                Modifier
                    .size(width = if (i == passo) 24.dp else 8.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (i == passo) Colori.Petrolio else Colori.InterruttoreSpento),
            )
        }
    }
}

/** Barra del livello del serbatoio con le tacche. */
@Composable
fun BarraLivello(livello: Double, colore: Color, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Colori.GrigioBadge),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(livello.toFloat().coerceIn(0.02f, 1f))
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(colore),
            )
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("R", "1/4", "1/2", "3/4", "Pieno").forEach {
                Text(it, style = Testi.Piccolo.copy(color = Colori.Testo3))
            }
        }
    }
}

@Composable
fun Separatore(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(1.dp).background(Colori.Divisore))
}

@Composable
fun BordoTratteggiato(
    testo: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icona: ImageVector = Icone.Piu,
    altezza: Dp = 64.dp,
) {
    val forma = RoundedCornerShape(18.dp)
    Row(
        modifier
            .height(altezza)
            .clip(forma)
            .border(BorderStroke(1.5.dp, Colori.Tratteggio), forma)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icona, null, tint = Colori.Petrolio, modifier = Modifier.size(20.dp))
        Text(testo, style = Testi.Pulsante.copy(color = Colori.Petrolio))
    }
}
