package it.goccia.app.ui.viaggio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.goccia.app.logica.Convenienza
import it.goccia.app.logica.Formati
import it.goccia.app.logica.GIORNI_VECCHIO
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.componenti.BottonePrimario
import it.goccia.app.ui.componenti.Scheda
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi
import kotlin.math.roundToInt

/**
 * La pianificazione dei viaggi arriva nel prossimo aggiornamento: intanto mostriamo
 * autonomia e quanto costa fare il pieno in autostrada rispetto alla strada.
 */
@Composable
fun SchermataViaggio(vm: GocciaViewModel, onLista: () -> Unit) {
    LaunchedEffect(Unit) { vm.avvia() }
    val utente by vm.utente.collectAsStateWithLifecycle()
    val dati by vm.dati.collectAsStateWithLifecycle()
    val carburante = utente.carburante
    val self = utente.impostazioni.preferisciSelf || !carburante.haSelf
    val adesso = System.currentTimeMillis() / 1000

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Viaggio", style = Testi.TitoloSchermata)
        Scheda(Modifier.fillMaxWidth()) {
            Box(Modifier.size(56.dp).clip(CircleShape).background(Colori.PetrolioChiaro), contentAlignment = Alignment.Center) {
                Icon(Icone.Percorso, null, tint = Colori.Petrolio, modifier = Modifier.size(28.dp))
            }
            Text("Il navigatore dei rifornimenti è in arrivo", style = Testi.Sottosezione)
            Text(
                "Scegli la destinazione e Goccia ti dirà dove fermarti lungo il percorso, in base all'autonomia della tua auto e ai prezzi, " +
                    "evitando le aree di servizio più care. Arriva con il prossimo aggiornamento.",
                style = Testi.Didascalia.copy(color = Colori.Testo2),
            )
        }

        utente.autoCorrente?.takeIf { !it.alimentazione.elettrica }?.let { auto ->
            val serbatoio = vm.serbatoio(utente)
            if (serbatoio != null) {
                Scheda(Modifier.fillMaxWidth()) {
                    Text("${auto.nome} · autonomia", style = Testi.DidascaliaForte.copy(color = Colori.Testo3, fontWeight = FontWeight.SemiBold))
                    Text("~${serbatoio.autonomiaKm} km con quello che hai", style = Testi.Titolo)
                    val pieno = if (auto.consumo > 0) (auto.capienza / auto.consumo * 100).roundToInt() else 0
                    Text("Con il pieno fai circa $pieno km.", style = Testi.Didascalia.copy(color = Colori.Testo2))
                }
            }
        }

        // quanto costa l'autostrada, dai distributori caricati attorno a te
        val recenti = dati.distributori.mapNotNull { d ->
            Convenienza.prezzoEsatto(d, carburante, self)?.takeIf { Convenienza.giorniDa(it.comunicato, adesso) <= GIORNI_VECCHIO }?.let { d.autostradale to it.millesimi }
        }
        val autostrada = recenti.filter { it.first }.map { it.second }
        val strada = recenti.filter { !it.first }.map { it.second }
        if (autostrada.size >= 3 && strada.size >= 10) {
            val differenza = ((autostrada.average() - strada.average()) / 10).roundToInt()
            Scheda(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icone.Autostrada, null, tint = Colori.AmbraScuro, modifier = Modifier.size(20.dp))
                    Text("In autostrada costa di più", style = Testi.Sottosezione)
                }
                Text(
                    "Qui attorno il ${carburante.etichetta.lowercase()} in autostrada costa in media ${Formati.prezzo(autostrada.average().roundToInt())} ${carburante.unita}, " +
                        "circa $differenza cent in più che sulla strada normale (${Formati.prezzo(strada.average().roundToInt())}). " +
                        "Su un pieno da 40 litri sono ${Formati.euro(differenza * 0.4)}.",
                    style = Testi.Didascalia.copy(color = Colori.Testo2),
                )
            }
        }

        BottonePrimario("Distributori convenienti vicino a te", onLista, Modifier.fillMaxWidth(), icona = Icone.Lista)
    }
}
