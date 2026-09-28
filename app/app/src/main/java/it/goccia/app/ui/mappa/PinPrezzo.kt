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

    fun disegna(testo: String, colore: Int, grande: Boolean): Bitmap {
        val scala = if (grande) 1.12f else 1f
        val d = densita * scala
        val pennello = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = carattere
            textSize = 14f * d
            color = android.graphics.Color.WHITE
        }
        val larghezzaTesto = pennello.measureText(testo)
        val bordo = 2f * d
        val altezza = 32f * d
        val punta = 6f * d
        val larghezza = larghezzaTesto + 20f * d + 2 * bordo
        val bitmap = Bitmap.createBitmap(ceil(larghezza).toInt(), ceil(altezza + punta).toInt(), Bitmap.Config.ARGB_8888)
        val tela = Canvas(bitmap)
        val bianco = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE }
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

        val y = altezza / 2 - (pennello.descent() + pennello.ascent()) / 2
        tela.drawText(testo, larghezza / 2 - larghezzaTesto / 2, y, pennello)
        return bitmap
    }
}
