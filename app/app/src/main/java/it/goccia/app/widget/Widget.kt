package it.goccia.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import it.goccia.app.MainActivity
import it.goccia.app.R
import it.goccia.app.avvisi.Notifiche
import it.goccia.app.logica.Formati
import kotlin.math.abs

// Colori del design (i widget non usano il tema Compose dell'app).
private val Bianco = Color.White
private val Inchiostro = Color(0xFF0F1B2A)
private val Petrolio = Color(0xFF0B7A75)
private val Testo2 = Color(0xFF4A5A6C)
private val Testo3 = Color(0xFF5F6E80)
private val TestoChip = Color(0xFF33475B)
private val Divisore = Color(0xFFEEF2F6)
private val Verde = Color(0xFF15803D)
private val VerdeChiaro = Color(0xFFE7F6EC)
private val Rosso = Color(0xFFB91C1C)
private val RossoChiaro = Color(0xFFFDECEC)
private val Ambra = Color(0xFFF59E0B)

private fun colore(c: Color) = ColorProvider(c)

private val CHIAVE_PROVINCIA = ActionParameters.Key<String>(Notifiche.EXTRA_PROVINCIA)
private val CHIAVE_DISTRIBUTORE = ActionParameters.Key<Long>(Notifiche.EXTRA_DISTRIBUTORE)

/** Aggiorna tutti i widget di Goccia sulla schermata Home (se ce ne sono). */
object Widget {
    suspend fun aggiornaTutti(context: Context) {
        try {
            WidgetPrezzi().updateAll(context)
            WidgetAuto().updateAll(context)
        } catch (e: Exception) {
            // un widget che non si aggiorna non deve bloccare l'app
        }
    }

    /** Se il launcher sa aggiungere un widget su richiesta dell'app (quasi tutti da Android 8). */
    fun puoAggiungere(context: Context): Boolean =
        try {
            AppWidgetManager.getInstance(context).isRequestPinAppWidgetSupported
        } catch (e: Exception) {
            false
        }

    /** Chiede al launcher di mettere il widget nella schermata Home: conferma l'utente. */
    fun aggiungi(context: Context, ricevitore: Class<out GlanceAppWidgetReceiver>): Boolean =
        try {
            AppWidgetManager.getInstance(context).requestPinAppWidget(ComponentName(context, ricevitore), null, null)
        } catch (e: Exception) {
            false
        }
}

// ------------------------------------------------------------------ prezzi

/**
 * Il piu conveniente vicino a te: piccolo (prezzo e distributore), largo (con il dettaglio) o
 * alto (con altri due, uno per riga).
 */
class WidgetPrezzi : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(PICCOLO, LARGO, ALTO))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val dati = DatiWidget.prezzi(context)
        provideContent {
            val dimensione = LocalSize.current
            val largo = dimensione.width >= LARGO.width
            val alto = largo && dimensione.height >= ALTO.height
            Box(
                GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(28.dp)
                    .background(Bianco)
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .clickable(apri(dati?.voci?.firstOrNull())),
            ) {
                when {
                    dati == null -> Vuoto("Apri Goccia per trovare i distributori vicino a te.")
                    dati.voci.isEmpty() -> Vuoto("Nessun distributore con prezzi recenti ${dati.dove}.")
                    largo -> PrezziLargo(dati, conAltri = alto)
                    else -> PrezziPiccolo(dati)
                }
            }
        }
    }

    companion object {
        val PICCOLO = DpSize(110.dp, 110.dp)
        val LARGO = DpSize(250.dp, 110.dp)
        val ALTO = DpSize(250.dp, 175.dp)
    }
}

class WidgetPrezziReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetPrezzi()
}

private fun apri(voce: DatiWidget.Voce?) =
    if (voce == null) actionStartActivity<MainActivity>()
    else actionStartActivity<MainActivity>(actionParametersOf(CHIAVE_PROVINCIA to voce.provincia, CHIAVE_DISTRIBUTORE to voce.id))

@Composable
private fun Intestazione(testo: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            GlanceModifier.size(22.dp).cornerRadius(7.dp).background(Petrolio),
            contentAlignment = Alignment.Center,
        ) {
            Image(ImageProvider(R.drawable.ic_notifica), contentDescription = null, modifier = GlanceModifier.size(13.dp))
        }
        Spacer(GlanceModifier.width(8.dp))
        Text(testo, style = TextStyle(color = colore(TestoChip), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun Vuoto(testo: String) {
    Column {
        Intestazione("Goccia")
        Spacer(GlanceModifier.height(10.dp))
        Text(testo, style = TextStyle(color = colore(Testo2), fontSize = 13.sp))
    }
}

@Composable
private fun Differenza(cent: Int?, breve: Boolean) {
    if (cent == null) return
    val (sfondo, testo) = when {
        cent <= -3 -> VerdeChiaro to Verde
        cent >= 3 -> RossoChiaro to Rosso
        else -> Divisore to TestoChip
    }
    val etichetta = when {
        abs(cent) < 3 -> "nella media"
        breve -> "${if (cent < 0) "−" else "+"}${abs(cent)} cent"
        else -> Formati.differenza(cent)
    }
    Box(GlanceModifier.cornerRadius(999.dp).background(sfondo).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text(etichetta, style = TextStyle(color = colore(testo), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
    }
}

@Composable
private fun PrezziLargo(dati: DatiWidget.Prezzi, conAltri: Boolean) {
    val primo = dati.voci.first()
    Column(GlanceModifier.fillMaxSize()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(GlanceModifier.defaultWeight()) { Intestazione(dati.carburante) }
            Text(
                dati.lettiIl?.let { "prezzi delle ${Formati.ora(it)}" } ?: "prezzi del ${Formati.dataIso(dati.estrazione)}",
                style = TextStyle(color = colore(Testo3), fontSize = 12.sp),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(GlanceModifier.defaultWeight()) {
                Text("Il più conveniente ${dati.dove}", style = TextStyle(color = colore(Testo3), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(primo.titolo, style = TextStyle(color = colore(Inchiostro), fontSize = 16.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Formati.km(primo.distanzaKm), style = TextStyle(color = colore(Testo2), fontSize = 13.sp, fontWeight = FontWeight.Medium))
                    Spacer(GlanceModifier.width(8.dp))
                    Differenza(primo.cent, breve = false)
                }
            }
            Spacer(GlanceModifier.width(10.dp))
            Text(Formati.prezzo(primo.prezzo), style = TextStyle(color = colore(Inchiostro), fontSize = 32.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        }
        val altri = dati.voci.drop(1)
        if (conAltri && altri.isNotEmpty()) {
            Spacer(GlanceModifier.height(8.dp))
            Box(GlanceModifier.fillMaxWidth().height(1.dp).background(Divisore)) {}
            // gli altri due, uno per riga: posizione, distributore con la via, distanza e prezzo
            altri.forEachIndexed { i, v ->
                Spacer(GlanceModifier.height(6.dp))
                Row(GlanceModifier.fillMaxWidth().clickable(apri(v)), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${i + 2}",
                        style = TextStyle(color = colore(Testo3), fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        modifier = GlanceModifier.width(16.dp),
                    )
                    Text(
                        v.titolo,
                        style = TextStyle(color = colore(TestoChip), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        modifier = GlanceModifier.defaultWeight(),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Text(Formati.km(v.distanzaKm), style = TextStyle(color = colore(Testo3), fontSize = 12.sp), maxLines = 1)
                    Spacer(GlanceModifier.width(10.dp))
                    Text(Formati.prezzo(v.prezzo), style = TextStyle(color = colore(Inchiostro), fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun PrezziPiccolo(dati: DatiWidget.Prezzi) {
    val primo = dati.voci.first()
    Column(GlanceModifier.fillMaxSize()) {
        Intestazione(dati.carburante.substringBefore(" "))
        Spacer(GlanceModifier.defaultWeight())
        Text(Formati.prezzo(primo.prezzo), style = TextStyle(color = colore(Inchiostro), fontSize = 34.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.defaultWeight())
        Text("${primo.nome} · ${Formati.km(primo.distanzaKm)}", style = TextStyle(color = colore(Inchiostro), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
        Spacer(GlanceModifier.height(4.dp))
        Differenza(primo.cent, breve = true)
    }
}

// ------------------------------------------------------------------ auto

/** La tua auto: autonomia, livello e quando fare il pieno (o la batteria). */
class WidgetAuto : GlanceAppWidget() {
    override val sizeMode = SizeMode.Single

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val auto = DatiWidget.auto(context)
        provideContent {
            Column(
                GlanceModifier
                    .fillMaxSize()
                    .cornerRadius(28.dp)
                    .background(Inchiostro)
                    .padding(18.dp)
                    .clickable(actionStartActivity<MainActivity>()),
            ) {
                if (auto == null) {
                    Text("Goccia", style = TextStyle(color = colore(Color(0xFFC9D6E2)), fontSize = 13.sp, fontWeight = FontWeight.Bold))
                    Spacer(GlanceModifier.defaultWeight())
                    Text("Aggiungi la tua auto nell'app per vedere l'autonomia.", style = TextStyle(color = colore(Color(0xFFDCE5EE)), fontSize = 12.sp))
                } else {
                    Text(auto.nome, style = TextStyle(color = colore(Color(0xFFC9D6E2)), fontSize = 13.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Spacer(GlanceModifier.defaultWeight())
                    Text(auto.valore, style = TextStyle(color = colore(Bianco), fontSize = 28.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Spacer(GlanceModifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = auto.livello.toFloat().coerceIn(0.02f, 1f),
                        modifier = GlanceModifier.fillMaxWidth().height(8.dp),
                        color = colore(if (auto.livello < 0.3) Ambra else Color(0xFF2FB5AE)),
                        backgroundColor = colore(Color(0xFF33475B)),
                    )
                    Spacer(GlanceModifier.defaultWeight())
                    Text(auto.testo, style = TextStyle(color = colore(Color(0xFFDCE5EE)), fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 2)
                }
            }
        }
    }
}

class WidgetAutoReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WidgetAuto()
}
