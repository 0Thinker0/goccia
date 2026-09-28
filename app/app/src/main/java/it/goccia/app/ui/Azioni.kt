package it.goccia.app.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import it.goccia.app.dati.AppNavigazione
import it.goccia.app.dati.Distributore

private fun avvia(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    true
} catch (e: ActivityNotFoundException) {
    false
} catch (e: SecurityException) {
    false
}

/** Apre il navigatore scelto nelle impostazioni verso il distributore. */
fun naviga(context: Context, d: Distributore, app: AppNavigazione) {
    val lat = d.lat
    val lon = d.lon
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$lat,$lon"))
    val riuscito = when (app) {
        AppNavigazione.GOOGLE_MAPS -> avvia(
            context,
            Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon")).setPackage("com.google.android.apps.maps"),
        )
        AppNavigazione.WAZE -> avvia(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://waze.com/ul?ll=$lat,$lon&navigate=yes")))
        AppNavigazione.CHIEDI -> {
            val geo = Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(d.titolo)})"))
            avvia(context, Intent.createChooser(geo, "Naviga con"))
        }
    }
    if (!riuscito) avvia(context, web)
}

fun condividi(context: Context, testo: String, titolo: String = "Condividi") {
    val invio = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, testo)
    }
    avvia(context, Intent.createChooser(invio, titolo))
}

fun apriLink(context: Context, url: String) {
    avvia(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}
