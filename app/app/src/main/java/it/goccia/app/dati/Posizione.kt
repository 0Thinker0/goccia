package it.goccia.app.dati

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

/** Posizione usata solo mentre l'app e aperta, mai in background. */
class ServizioPosizione(private val context: Context) {
    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    fun haPermesso(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** La localizzazione del telefono e accesa? (con il GPS spento non arriva nessuna posizione) */
    fun localizzazioneAttiva(): Boolean {
        val gestore = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return try {
            gestore.isProviderEnabled(LocationManager.GPS_PROVIDER) || gestore.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun attuale(): Coordinate? {
        if (!haPermesso()) return null
        val daGoogle = try {
            val annulla = CancellationTokenSource()
            try {
                withTimeoutOrNull(10_000) {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, annulla.token).await()
                } ?: client.lastLocation.await()
            } finally {
                annulla.cancel()
            }
        } catch (e: Exception) {
            // telefoni senza servizi Google: proviamo con il sistema
            null
        }
        val posizione = daGoogle ?: ultimaDalSistema()
        return posizione?.let { Coordinate(it.latitude, it.longitude) }
    }

    @SuppressLint("MissingPermission")
    private fun ultimaDalSistema(): Location? {
        val gestore = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER, LocationManager.PASSIVE_PROVIDER)
            .mapNotNull { fornitore ->
                try {
                    gestore.getLastKnownLocation(fornitore)
                } catch (e: Exception) {
                    null
                }
            }
            .maxByOrNull { it.time }
    }
}
