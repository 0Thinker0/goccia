package it.goccia.app.ui.mappa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import it.goccia.app.R
import kotlin.math.ceil

/** Disegna i segnaposto con il prezzo (pillola colorata con bordo bianco e punta), come nel design. */
class PinPrezzo(context: Context) {
    private val densita = context.resources.displayMetrics.density
    private val carattere: Typeface = try {
        ResourcesCompat.getFont(context, R.font.plus_jakarta_sans_800extrabold) ?: Typeface.DEFAULT_BOLD
    } catch (e: Exception) {
        Typeface.DEFAULT_BOLD
    }

    /**
     * [colore] riempie la pillola; [coloreTesto] e [coloreBordo] di serie sono bianchi
     * (per le colonnine si usa il contrario: pillola bianca, testo e bordo petrolio).
     * Con [fulmine] al posto del testo c'e un fulmine (colonnine di cui non si sa la potenza).
     */
    fun disegna(
        testo: String,
        colore: Int,
        grande: Boolean,
        coloreTesto: Int = android.graphics.Color.WHITE,
        coloreBordo: Int = android.graphics.Color.WHITE,
        fulmine: Boolean = false,
    ): Bitmap {
        val scala = if (grande) 1.12f else 1f
        val d = densita * scala
        val pennello = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = carattere
            textSize = 14f * d
            color = coloreTesto
        }
        val larghezzaTesto = if (fulmine) 10f * d else pennello.measureText(testo)
        val bordo = 2f * d
        val altezza = 32f * d
        val punta = 6f * d
        val larghezza = larghezzaTesto + 20f * d + 2 * bordo
        val bitmap = Bitmap.createBitmap(ceil(larghezza).toInt(), ceil(altezza + punta).toInt(), Bitmap.Config.ARGB_8888)
        val tela = Canvas(bitmap)
        val bianco = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = coloreBordo }
        val pieno = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colore }

        val esterno = RectF(0f, 0f, larghezza, altezza)
        tela.drawRoundRect(esterno, altezza / 2, altezza / 2, bianco)
        val puntaEsterna = Path().apply {
            moveTo(larghezza / 2 - punta - bordo, altezza - bordo * 2)
            lineTo(larghezza / 2, altezza + punta)
            lineTo(larghezza / 2 + punta + bordo, altezza - bordo * 2)
            close()
        }
        tela.drawPath(puntaEsterna, bianco)

        val interno = RectF(bordo, bordo, larghezza - bordo, altezza - bordo)
        tela.drawRoundRect(interno, altezza / 2 - bordo, altezza / 2 - bordo, pieno)
        val puntaInterna = Path().apply {
            moveTo(larghezza / 2 - punta, altezza - bordo * 1.5f)
            lineTo(larghezza / 2, altezza + punta - bordo * 1.8f)
            lineTo(larghezza / 2 + punta, altezza - bordo * 1.5f)
            close()
        }
        tela.drawPath(puntaInterna, pieno)

        if (fulmine) {
            // fulmine di 10 x 14, centrato nella pillola
            val x0 = larghezza / 2 - 5f * d
            val y0 = altezza / 2 - 7f * d
            val saetta = Path().apply {
                moveTo(x0 + 6.5f * d, y0)
                lineTo(x0 + 0.5f * d, y0 + 8f * d)
                lineTo(x0 + 4.8f * d, y0 + 8f * d)
                lineTo(x0 + 3.5f * d, y0 + 14f * d)
                lineTo(x0 + 9.5f * d, y0 + 5.8f * d)
                lineTo(x0 + 5.3f * d, y0 + 5.8f * d)
                lineTo(x0 + 7f * d, y0)
                close()
            }
            tela.drawPath(saetta, pennello)
        } else {
            val y = altezza / 2 - (pennello.descent() + pennello.ascent()) / 2
            tela.drawText(testo, larghezza / 2 - larghezzaTesto / 2, y, pennello)
        }
        return bitmap
    }
}
