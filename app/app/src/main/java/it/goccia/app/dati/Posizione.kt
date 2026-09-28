package it.goccia.app.dati

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Posizione usata solo mentre l'app e aperta, mai in background. */
class ServizioPosizione(private val context: Context) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }
    private val gestore: LocationManager? get() = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun haPermesso(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** La localizzazione del telefono e accesa? (con il GPS spento non arriva nessuna posizione) */
    fun localizzazioneAttiva(): Boolean {
        val g = gestore ?: return false
        return try {
            g.isProviderEnabled(LocationManager.GPS_PROVIDER) || g.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * La posizione di adesso, o null se non arriva entro qualche secondo.
     *
     * Una posizione di pochi minuti fa va benissimo; altrimenti chiediamo insieme ai servizi
     * Google (rete e GPS) e al sistema, e teniamo la prima che risponde. Cosi funziona anche
     * al chiuso, sui telefoni senza servizi Google e quando la rete non localizza.
     */
    suspend fun attuale(): Coordinate? {
        if (!haPermesso()) return null
        val nota = ultimaNota()
        if (nota != null && eta(nota) <= RECENTE_MS) {
            Log.i(TAG, "posizione recente, di ${eta(nota) / 1000} s fa")
            return nota.coordinata()
        }
        val nuova = withTimeoutOrNull(ATTESA_MS) {
            primaDisponibile(
                Fonte("google, rete") { daGoogle(Priority.PRIORITY_BALANCED_POWER_ACCURACY) },
                Fonte("google, gps") { daGoogle(Priority.PRIORITY_HIGH_ACCURACY) },
                Fonte("sistema, rete") { dalSistema(LocationManager.NETWORK_PROVIDER) },
                Fonte("sistema, gps") { dalSistema(LocationManager.GPS_PROVIDER) },
            )
        }
        if (nuova != null) return nuova.coordinata()
        val ripiego = (nota ?: ultimaNota())?.takeIf { eta(it) <= ACCETTABILE_MS }
        Log.i(TAG, if (ripiego != null) "nessuna posizione nuova, uso quella di ${eta(ripiego) / 60_000} min fa" else "nessuna posizione")
        return ripiego?.coordinata()
    }

    private class Fonte(val nome: String, val cerca: suspend () -> Location?)

    private fun Location.coordinata() = Coordinate(latitude, longitude)

    private fun eta(l: Location): Long = (SystemClock.elapsedRealtimeNanos() - l.elapsedRealtimeNanos) / 1_000_000

    /** L'ultima posizione conosciuta dal telefono, la piu recente fra Google e sistema. */
    @SuppressLint("MissingPermission")
    private suspend fun ultimaNota(): Location? {
        val daGoogle = try {
            client.lastLocation.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        val g = gestore
        val dalSistema: List<Location> = if (g == null) emptyList() else listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { fornitore ->
                try {
                    g.getLastKnownLocation(fornitore)
                } catch (e: Exception) {
                    null
                }
            }
        return (listOfNotNull(daGoogle) + dalSistema).maxByOrNull { it.elapsedRealtimeNanos }
    }

    @SuppressLint("MissingPermission")
    private suspend fun daGoogle(priorita: Int): Location? {
        val annulla = CancellationTokenSource()
        return try {
            val richiesta = CurrentLocationRequest.Builder()
                .setPriority(priorita)
                .setMaxUpdateAgeMillis(RECENTE_MS)
                .setDurationMillis(ATTESA_MS)
                .build()
            client.getCurrentLocation(richiesta, annulla.token).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // telefoni senza servizi Google: ci pensa il sistema
            null
        } finally {
            annulla.cancel()
        }
    }

    /** Una posizione nuova direttamente dal sistema (rete o GPS), senza servizi Google. */
    @SuppressLint("MissingPermission")
    private suspend fun dalSistema(fornitore: String): Location? {
        val g = gestore ?: return null
        val acceso = try {
            g.isProviderEnabled(fornitore)
        } catch (e: Exception) {
            false
        }
        if (!acceso) return null
        return suspendCancellableCoroutine { cont ->
            if (Build.VERSION.SDK_INT >= 30) {
                val segnale = CancellationSignal()
                cont.invokeOnCancellation { segnale.cancel() }
                try {
                    g.getCurrentLocation(fornitore, segnale, ContextCompat.getMainExecutor(context)) { posizione ->
                        if (cont.isActive) cont.resume(posizione)
                    }
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            } else {
                // prima di Android 11 tutti i metodi dell'ascoltatore vanno implementati
                val ascoltatore = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        g.removeUpdates(this)
                        if (cont.isActive) cont.resume(location)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

                    override fun onProviderEnabled(provider: String) = Unit

                    override fun onProviderDisabled(provider: String) = Unit
                }
                cont.invokeOnCancellation { g.removeUpdates(ascoltatore) }
                try {
                    g.requestLocationUpdates(fornitore, 0L, 0f, ascoltatore, Looper.getMainLooper())
                } catch (e: Exception) {
                    if (cont.isActive) cont.resume(null)
                }
            }
        }
    }

    /** Avvia tutte le richieste insieme e restituisce la prima posizione arrivata (o null se nessuna). */
    private suspend fun primaDisponibile(vararg fonti: Fonte): Location? = coroutineScope {
        val inizio = SystemClock.elapsedRealtime()
        val esito = CompletableDeferred<Location?>()
        val lavori = fonti.map { fonte ->
            launch {
                val trovata = try {
                    fonte.cerca()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    null
                }
                if (trovata != null && esito.complete(trovata)) {
                    Log.i(TAG, "posizione da ${fonte.nome} in ${SystemClock.elapsedRealtime() - inizio} ms")
                }
            }
        }
        launch {
            lavori.joinAll()
            esito.complete(null)
        }
        val trovata = esito.await()
        coroutineContext.cancelChildren()
        trovata
    }

    private companion object {
        const val TAG = "Goccia"

        /** una posizione piu giovane di cosi la usiamo senza chiederne un'altra */
        const val RECENTE_MS = 2 * 60_000L

        /** quanto aspettiamo al massimo una posizione nuova */
        const val ATTESA_MS = 15_000L

        /** se non arriva niente, va bene anche una posizione di mezz'ora fa */
        const val ACCETTABILE_MS = 30 * 60_000L
    }
}
