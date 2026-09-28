package it.goccia.app.ui.componenti

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.goccia.app.logica.Formati
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi

/**
 * Andamento di un prezzo nel tempo, con la linea tratteggiata della media.
 * I valori null (giorni senza dato) vengono saltati.
 */
@Composable
fun GraficoLinea(
    valori: List<Int?>,
    inizio: String,
    fine: String,
    modifier: Modifier = Modifier,
    media: Int? = null,
    etichettaMedia: String? = null,
    mostraScala: Boolean = true,
    altezza: Dp = 160.dp,
    descrizione: String = "Grafico dei prezzi",
) {
    val misuratore = rememberTextMeasurer()
    val stile = Testi.Piccolo.copy(fontSize = 11.sp, color = Colori.Testo3)
    val stileForte = stile.copy(color = Colori.Inchiostro, fontWeight = FontWeight.Bold)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(altezza)
            .semantics { contentDescription = descrizione },
    ) {
        val punti = valori.mapIndexedNotNull { i, v -> v?.let { i to it } }
        if (punti.isEmpty()) return@Canvas
        val tutti = punti.map { it.second } + listOfNotNull(media)
        var basso = tutti.min() - 4
        var alto = tutti.max() + 4
        if (alto - basso < 10) {
            val centro = (alto + basso) / 2
            basso = centro - 5
            alto = centro + 5
        }
        val sinistra = if (mostraScala) 44.dp.toPx() else 6.dp.toPx()
        val destra = size.width - 8.dp.toPx()
        val cima = 16.dp.toPx()
        val fondo = size.height - 34.dp.toPx()
        fun x(i: Int): Float = if (valori.size <= 1) destra else sinistra + (destra - sinistra) * i / (valori.size - 1)
        fun y(v: Int): Float = cima + (fondo - cima) * (alto - v) / (alto - basso).toFloat()

        drawLine(Colori.Divisore, Offset(sinistra - 4.dp.toPx(), cima), Offset(size.width, cima), 1.dp.toPx())
        drawLine(Colori.Divisore, Offset(sinistra - 4.dp.toPx(), fondo), Offset(size.width, fondo), 1.dp.toPx())
        if (mostraScala) {
            drawText(misuratore, Formati.prezzo(alto), Offset(0f, cima - 8.dp.toPx()), stile)
            drawText(misuratore, Formati.prezzo(basso), Offset(0f, fondo - 8.dp.toPx()), stile)
        }

        if (media != null) {
            val ym = y(media)
            drawLine(
                Colori.Linea,
                Offset(sinistra, ym),
                Offset(size.width, ym),
                1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            if (etichettaMedia != null) {
                val testo = misuratore.measure(etichettaMedia, stile)
                val yTesto = if (ym - cima < 24.dp.toPx()) ym + 4.dp.toPx() else ym - testo.size.height - 2.dp.toPx()
                drawText(testo, topLeft = Offset(size.width - testo.size.width, yTesto))
            }
        }

        val percorso = Path()
        punti.forEachIndexed { k, (i, v) ->
            if (k == 0) percorso.moveTo(x(i), y(v)) else percorso.lineTo(x(i), y(v))
        }
        drawPath(percorso, Colori.Petrolio, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        val (ultimoI, ultimoV) = punti.last()
        drawCircle(Color.White, 7.5.dp.toPx(), Offset(x(ultimoI), y(ultimoV)))
        drawCircle(Colori.Petrolio, 5.5.dp.toPx(), Offset(x(ultimoI), y(ultimoV)))

        val yEtichette = size.height - 16.dp.toPx()
        drawText(misuratore, inizio, Offset(sinistra, yEtichette), stile)
        val testoFine = misuratore.measure(fine, stileForte)
        drawText(testoFine, topLeft = Offset(size.width - testoFine.size.width, yEtichette))
    }
}

/** Barre verticali (risparmio o spesa per mese), l'ultima evidenziata. */
@Composable
fun GraficoBarre(
    valori: List<Double>,
    etichette: List<String>,
    modifier: Modifier = Modifier,
    altezza: Dp = 96.dp,
    colore: Color = Colori.PetrolioBarre,
    coloreUltimo: Color = Colori.Petrolio,
    descrizione: String = "Grafico a barre",
) {
    val massimo = (valori.maxOrNull() ?: 0.0).coerceAtLeast(0.01)
    val altezzaBarre = altezza - 24.dp
    Row(
        modifier
            .fillMaxWidth()
            .height(altezza)
            .semantics { contentDescription = descrizione },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Bottom,
    ) {
        valori.forEachIndexed { i, v ->
            val ultimo = i == valori.lastIndex
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val h = altezzaBarre * (v.coerceAtLeast(0.0) / massimo).toFloat()
                Box(
                    Modifier
                        .width(26.dp)
                        .height(if (v > 0) h.coerceAtLeast(4.dp) else 3.dp)
                        .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 2.dp, bottomEnd = 2.dp))
                        .background(if (v > 0) (if (ultimo) coloreUltimo else colore) else Colori.Divisore),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    etichette.getOrElse(i) { "" },
                    style = Testi.Piccolo.copy(
                        color = if (ultimo) Colori.Inchiostro else Colori.Testo3,
                        fontWeight = if (ultimo) FontWeight.ExtraBold else FontWeight.SemiBold,
                    ),
                )
            }
        }
    }
}
