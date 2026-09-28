package it.goccia.app

import android.app.Application
import android.content.Context
import it.goccia.app.avvisi.Notifiche
import it.goccia.app.avvisi.Sorveglianza
import it.goccia.app.dati.ArchivioUtente
import it.goccia.app.dati.DatiRepository
import it.goccia.app.dati.Instradamento
import it.goccia.app.dati.ServizioPosizione
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre

/** Gli oggetti condivisi dall'app e dal controllo degli avvisi in background. */
class Contenitore(context: Context) {
    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { catena ->
            catena.proceed(
                catena.request().newBuilder()
                    .header("User-Agent", "Goccia/${BuildConfig.VERSION_NAME} (Android; +https://github.com/${BuildConfig.REPO})")
                    .build(),
            )
        }
        .build()

    val repository = DatiRepository(File(context.filesDir, "dati"), http, BuildConfig.DATI_URL)
    val archivio = ArchivioUtente(File(context.filesDir, "utente.json"))
    val posizione = ServizioPosizione(context.applicationContext)
    val instradamento = Instradamento(http)
}

class GocciaApp : Application() {
    lateinit var contenitore: Contenitore
        private set

    override fun onCreate() {
        super.onCreate()
        contenitore = Contenitore(this)
        MapLibre.getInstance(this)
        Notifiche.creaCanale(this)
        Sorveglianza.pianifica(this)
    }
}

val Context.contenitore: Contenitore get() = (applicationContext as GocciaApp).contenitore
