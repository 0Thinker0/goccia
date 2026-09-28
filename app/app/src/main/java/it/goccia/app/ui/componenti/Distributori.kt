package it.goccia.app.ui.componenti

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import it.goccia.app.dati.Carburante
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Offerta
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Forme
import it.goccia.app.ui.tema.Testi
import it.goccia.app.ui.tema.ombra

/** "1,2 km · aggiornato oggi" */
fun metaOfferta(o: Offerta, adessoMillis: Long): String =
    Formati.km(o.distanzaKm) + " · " + if (o.distributore.autostradale) {
        "area di servizio autostradale"
    } else {
        Formati.aggiornamento(o.prezzo.comunicato, adessoMillis)
    }

/** La card di un distributore nelle liste: logo, nome, distanza, badge di convenienza e prezzo. */
@Composable
fun CardOfferta(
    offerta: Offerta,
    carburante: Carburante,
    meta: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mostraBadge: Boolean = true,
) {
    val d = offerta.distributore
    Row(
        modifier = modifier
            .fillMaxWidth()
            .ombra(Forme.Card)
            .clip(Forme.Card)
            .background(Colori.Superficie)
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LogoBandiera(d.bandiera, d.pompaBianca)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(d.titolo, style = Testi.CorpoForte, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (offerta.daVerificare) Icon(Icone.Attenzione, "Prezzo da verificare", tint = Colori.AmbraScuro, modifier = Modifier.size(13.dp))
                Text(
                    meta,
                    style = Testi.Didascalia.copy(color = if (offerta.daVerificare) Colori.AmbraTesto else Colori.Testo3),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (mostraBadge) BadgeConvenienza(offerta.differenzaCent, offerta.tono)
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(Formati.prezzo(offerta.prezzo.millesimi), style = Testi.Prezzo)
            Text(carburante.unitaCon(offerta.self), style = Testi.Piccolo.copy(color = Colori.Testo3))
        }
    }
}

/** Segnaposto grigi mentre si caricano i dati (stato "caricamento" del design). */
@Composable
fun CardScheletro(modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Forme.Card)
            .background(Colori.Superficie)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Colori.GrigioBadge))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.fillMaxWidth(0.7f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(Colori.GrigioBadge))
            Box(Modifier.fillMaxWidth(0.45f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(Colori.Divisore))
        }
        Box(Modifier.size(width = 64.dp, height = 24.dp).clip(RoundedCornerShape(8.dp)).background(Colori.GrigioBadge))
    }
}
