package it.goccia.app.ui.elettrico

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.goccia.app.dati.Colonnina
import it.goccia.app.dati.Presa
import it.goccia.app.logica.Formati
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra

// Pezzi condivisi dalle schermate delle auto elettriche.

/** "Voltaria Hub · Viale Europa 120": nome (o operatore) e indirizzo, se c'e. */
fun titoloColonnina(c: Colonnina): String = listOfNotNull(c.titolo, c.indirizzo?.substringBefore(",")).distinct().joinToString(" · ")

/** "150 kW", "~22 kW" se la potenza e dedotta dalle prese, "kW non indicati" se manca. */
fun potenzaColonnina(c: Colonnina): String = when {
    c.kw == null -> "kW non indicati"
    c.potenzaStimata -> "~" + Formati.kw(c.kw)
    else -> Formati.kw(c.kw)
}

/** "2,1 km · CCS2 ×4 · Type 2 ×2" */
fun metaColonnina(c: Colonnina, distanzaKm: Double?): String =
    listOfNotNull(distanzaKm?.let { Formati.km(it) }, c.descrizionePrese).joinToString(" · ")

/** "CCS2 e Type 2", "tutte le prese" */
fun descrizioneFiltroPrese(prese: Set<Presa>): String {
    val nomi = Presa.filtrabili.filter { it in prese }.map { it.etichetta }
    return when (nomi.size) {
        0 -> "Tutte le prese"
        1 -> "Solo ${nomi.first()}"
        else -> nomi.dropLast(1).joinToString(", ") + " e " + nomi.last()
    }
}

/** Una colonnina nelle liste: fulmine (pieno se veloce), nome, distanza e prese, potenza e tariffa. */
@Composable
fun CardColonnina(
    c: Colonnina,
    distanzaKm: Double?,
    tariffa: Double,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .ombra(Forme.Card, 1.dp)
            .clip(Forme.Card)
            .background(Colori.Superficie)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IconaColonnina(c)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(titoloColonnina(c), style = Testi.CorpoForte, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(metaColonnina(c, distanzaKm), style = Testi.Didascalia.copy(color = Colori.Testo3), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (c.soloClienti || c.gratuita) {
                Text(
                    listOfNotNull("solo clienti".takeIf { c.soloClienti }, "gratuita".takeIf { c.gratuita }).joinToString(" · ").replaceFirstChar { it.uppercase() },
                    style = Testi.Piccolo.copy(color = if (c.gratuita) Colori.VerdeTesto else Colori.AmbraTesto),
                )
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(potenzaColonnina(c), style = Testi.CorpoForte.copy(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"))
            Text(
                if (c.gratuita) "gratis" else Formati.euroKwh(tariffa),
                style = Testi.Piccolo.copy(color = Colori.Testo3),
            )
        }
    }
}

@Composable
fun IconaColonnina(c: Colonnina, dimensione: Int = 40) {
    val veloce = c.continua
    Box(
        Modifier.size(dimensione.dp).clip(RoundedCornerShape(12.dp)).background(if (veloce) Colori.Petrolio else Colori.PetrolioChiaro),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icone.Fulmine, null, tint = if (veloce) Color.White else Colori.Petrolio, modifier = Modifier.size((dimensione * 0.45f).dp))
    }
}

/** Una riga del confronto "costo per 100 km": etichetta, barra proporzionale e importo. */
@Composable
fun RigaConfronto(etichetta: String, valore: Double, massimo: Double, colore: Color = Colori.Petrolio) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(etichetta, style = Testi.Didascalia.copy(color = Colori.TestoChip, fontWeight = FontWeight.SemiBold), modifier = Modifier.width(128.dp), maxLines = 2)
        Box(Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(4.dp)).background(Colori.GrigioBadge)) {
            val quota = if (massimo > 0) (valore / massimo).toFloat().coerceIn(0.02f, 1f) else 0f
            Box(Modifier.fillMaxWidth(quota).height(10.dp).clip(RoundedCornerShape(4.dp)).background(colore))
        }
        Text(
            Formati.euro(valore),
            style = Testi.Chip.copy(fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
            textAlign = TextAlign.End,
            modifier = Modifier.width(62.dp),
        )
    }
}

/** Il colore grigio delle barre del carburante nel confronto. */
val GrigioCarburante = Color(0xFF8492A3)
