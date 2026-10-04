package it.goccia.app

import android.app.Application
import android.content.Context
import it.goccia.app.avvisi.Notifiche
import it.goccia.app.avvisi.Sorveglianza
import it.goccia.app.dati.ArchivioUtente
import it.goccia.app.dati.DatiRepository
import it.goccia.app.dati.Instradamento
import it.goccia.app.dati.Osservaprezzi
import it.goccia.app.dati.Pun
import it.goccia.app.dati.ServizioPosizione
import it.goccia.app.widget.Widget
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre

/** Gli oggetti condivisi dall'app, dai widget e dal controllo degli avvisi in background. */
class Contenitore(context: Context) {
    private val appContext = context.applicationContext
    private val ambito = CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
    val osservaprezzi = Osservaprezzi(http)
    val pun = Pun(http, File(context.filesDir, "dati"))

    /** Ridisegna i widget con i dati appena scaricati. */
    fun aggiornaWidget() {
        ambito.launch { Widget.aggiornaTutti(appContext) }
    }

    /** Quando cambiano auto, carburante o livello, i widget si aggiornano da soli (con calma). */
    @OptIn(FlowPreview::class)
    fun osservaUtente() {
        ambito.launch {
            archivio.dati.drop(1).debounce(3_000).collect { Widget.aggiornaTutti(appContext) }
        }
    }
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
        contenitore.osservaUtente()
    }
}

val Context.contenitore: Contenitore get() = (applicationContext as GocciaApp).contenitore
