package it.goccia.app.ui.mappa

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import org.maplibre.android.maps.MapLibreMapOptions
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer

/** Stile di OpenFreeMap: chiaro, gratuito, senza chiavi. */
const val STILE_MAPPA = "https://tiles.openfreemap.org/styles/positron"

fun nuovaVistaMappa(context: Context): MapView =
    MapView(context, MapLibreMapOptions.createFromAttributes(context).textureMode(true))

/**
 * Lo stile mostra i nomi in inglese ("Florence", "Tuscany"): le etichette dei luoghi passano
 * al nome italiano, se c'e, altrimenti a quello locale. Sigle delle strade e numeri civici
 * restano come sono.
 */
fun etichetteInItaliano(stile: Style) {
    for (strato in stile.layers) {
        if (strato !is SymbolLayer) continue
        val campo = try {
            strato.textField.expression?.toString() ?: strato.textField.value?.toString()
        } catch (e: Exception) {
            null
        } ?: continue
        if (!campo.contains("name")) continue
        try {
            strato.setProperties(PropertyFactory.textField(Expression.coalesce(Expression.get("name:it"), Expression.get("name"))))
        } catch (e: Exception) {
            // uno strato che non accetta il cambio resta com'era
        }
    }
}

/**
 * Una MapView legata al ciclo di vita della schermata: viene creata quando la schermata
 * compare e distrutta quando se ne va. [alCreare] riceve la vista appena creata.
 */
@Composable
fun rememberVistaMappa(alCreare: (MapView) -> Unit = {}): MapView {
    val context = LocalContext.current
    val ciclo = LocalLifecycleOwner.current.lifecycle
    val vista = remember { nuovaVistaMappa(context) }
    DisposableEffect(ciclo, vista) {
        vista.onCreate(null)
        alCreare(vista)
        val osservatore = LifecycleEventObserver { _, evento ->
            when (evento) {
                Lifecycle.Event.ON_START -> vista.onStart()
                Lifecycle.Event.ON_RESUME -> vista.onResume()
                Lifecycle.Event.ON_PAUSE -> vista.onPause()
                Lifecycle.Event.ON_STOP -> vista.onStop()
                else -> {}
            }
        }
        ciclo.addObserver(osservatore)
        onDispose {
            ciclo.removeObserver(osservatore)
            val stato = ciclo.currentState
            if (stato.isAtLeast(Lifecycle.State.RESUMED)) vista.onPause()
            if (stato.isAtLeast(Lifecycle.State.STARTED)) vista.onStop()
            vista.onDestroy()
        }
    }
    return vista
}
