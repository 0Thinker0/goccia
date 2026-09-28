package it.goccia.app

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.compose.runtime.getValue
import it.goccia.app.avvisi.Notifiche
import it.goccia.app.ui.GocciaRadice
import it.goccia.app.ui.GocciaViewModel
import it.goccia.app.ui.tema.GocciaTema
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val vm: GocciaViewModel by viewModels {
        viewModelFactory { initializer { GocciaViewModel(applicationContext.contenitore) } }
    }

    /** Distributore da aprire perche l'utente ha toccato una notifica. */
    private val daAprire = MutableStateFlow<Pair<String, Long>?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.WHITE, Color.WHITE),
        )
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) leggi(intent)
        setContent {
            GocciaTema {
                val apri by daAprire.collectAsStateWithLifecycle()
                GocciaRadice(vm = vm, distributoreDaAprire = apri, onAperto = { daAprire.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        leggi(intent)
    }

    override fun onStart() {
        super.onStart()
        vm.alRitorno()
    }

    private fun leggi(intent: Intent?) {
        val provincia = intent?.getStringExtra(Notifiche.EXTRA_PROVINCIA) ?: return
        val id = intent.getLongExtra(Notifiche.EXTRA_DISTRIBUTORE, -1L)
        if (id > 0) daAprire.value = provincia to id
    }
}
