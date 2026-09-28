package it.goccia.app.avvisi

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import it.goccia.app.MainActivity
import it.goccia.app.R
import it.goccia.app.dati.Distributore

object Notifiche {
    const val CANALE = "avvisi-prezzo"
    const val EXTRA_PROVINCIA = "provincia"
    const val EXTRA_DISTRIBUTORE = "distributore"

    fun creaCanale(context: Context) {
        val canale = NotificationChannel(CANALE, "Avvisi di prezzo", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Quando un preferito abbassa il prezzo o in zona si scende sotto la tua soglia"
        }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(canale)
    }

    fun permesso(context: Context): Boolean {
        val concesso = Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        return concesso && NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    @SuppressLint("MissingPermission")
    fun mostra(context: Context, id: Int, titolo: String, testo: String, distributore: Distributore) {
        if (!permesso(context)) return
        val apri = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_PROVINCIA, distributore.provincia)
            putExtra(EXTRA_DISTRIBUTORE, distributore.id)
        }
        val flag = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val intentApri = PendingIntent.getActivity(context, id, apri, flag)
        val naviga = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("geo:0,0?q=${distributore.lat},${distributore.lon}(${Uri.encode(distributore.titolo)})"),
        )
        val intentNaviga = PendingIntent.getActivity(context, id xor 0x5A5A, naviga, flag)
        val notifica = NotificationCompat.Builder(context, CANALE)
            .setSmallIcon(R.drawable.ic_notifica)
            .setColor(0xFF0B7A75.toInt())
            .setContentTitle(titolo)
            .setContentText(testo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
            .setContentIntent(intentApri)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .addAction(0, "Naviga", intentNaviga)
            .addAction(0, "Vedi distributore", intentApri)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notifica)
        } catch (e: SecurityException) {
            // permesso revocato nel frattempo
        }
    }
}
