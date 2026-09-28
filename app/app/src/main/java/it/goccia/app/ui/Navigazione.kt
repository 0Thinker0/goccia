package it.goccia.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import it.goccia.app.ui.dettaglio.SchermataDettaglio
import it.goccia.app.ui.elettrico.SchermataColonnina
import it.goccia.app.ui.elettrico.SchermataTariffa
import it.goccia.app.ui.home.SchermataHome
import it.goccia.app.ui.icone.Icone
import it.goccia.app.ui.intro.SchermataIntro
import it.goccia.app.ui.lista.SchermataLista
import it.goccia.app.ui.mappa.SchermataMappa
import it.goccia.app.ui.preferiti.SchermataAvviso
import it.goccia.app.ui.preferiti.SchermataPreferiti
import it.goccia.app.ui.profilo.SchermataAuto
import it.goccia.app.ui.profilo.SchermataImpostazioni
import it.goccia.app.ui.profilo.SchermataLuogo
import it.goccia.app.ui.profilo.SchermataProfilo
import it.goccia.app.ui.profilo.SchermataRegistro
import it.goccia.app.ui.profilo.SchermataSostieni
import it.goccia.app.ui.profilo.SchermataStatistiche
import it.goccia.app.ui.rifornimento.SchermataRifornimento
import it.goccia.app.ui.tema.Colori
import it.goccia.app.ui.tema.Testi
import it.goccia.app.guida.GuidaInCorso
import it.goccia.app.ui.viaggio.SchermataAutostrada
import it.goccia.app.ui.viaggio.SchermataViaggio

object Rotte {
    const val INTRO = "intro"
    const val HOME = "home"
    const val MAPPA = "mappa"
    const val LISTA = "lista"
    const val VIAGGIO = "viaggio"
    const val PREFERITI = "preferiti"
    const val PROFILO = "profilo"
    const val DETTAGLIO = "dettaglio/{prov}/{id}"
    const val RIFORNIMENTO = "rifornimento?prov={prov}&id={id}"
    const val AVVISO = "avviso?id={id}&prov={prov}&distributore={distributore}"
    const val AUTO = "auto?id={id}"
    const val IMPOSTAZIONI = "impostazioni"
    const val LUOGO = "luogo?id={id}&tipo={tipo}"
    const val REGISTRO = "registro"
    const val STATISTICHE = "statistiche"
    const val SOSTIENI = "sostieni"
    const val COLONNINA = "colonnina/{id}"
    const val TARIFFA = "tariffa"
    const val AUTOSTRADA = "autostrada"

    fun dettaglio(provincia: String, id: Long) = "dettaglio/$provincia/$id"
    fun rifornimento(provincia: String? = null, id: Long? = null) = "rifornimento?prov=${provincia ?: ""}&id=${id ?: -1}"
    fun avviso(id: String? = null) = "avviso?id=${id ?: ""}&prov=&distributore=-1"
    fun auto(id: String? = null) = "auto?id=${id ?: ""}"
    fun luogo(id: String? = null, tipo: String? = null) = "luogo?id=${id ?: ""}&tipo=${tipo ?: ""}"
    fun colonnina(id: String) = "colonnina/${Uri.encode(id)}"
}

private data class Scheda(val rotta: String, val etichetta: String, val icona: ImageVector)

private val schede = listOf(
    Scheda(Rotte.HOME, "Home", Icone.Casa),
    Scheda(Rotte.MAPPA, "Mappa", Icone.Mappa),
    Scheda(Rotte.VIAGGIO, "Viaggio", Icone.Percorso),
    Scheda(Rotte.PREFERITI, "Preferiti", Icone.Stella),
    Scheda(Rotte.PROFILO, "Profilo", Icone.Persona),
)

/** Cambio di scheda nella barra in basso: la Home resta sempre alla base della pila. */
private fun NavHostController.vaiAScheda(rotta: String) {
    navigate(rotta) {
        popUpTo(Rotte.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun GocciaRadice(
    vm: GocciaViewModel,
    distributoreDaAprire: Pair<String, Long>?,
    onAperto: () -> Unit,
    apriGuida: Boolean = false,
    onGuidaAperta: () -> Unit = {},
) {
    val nav = rememberNavController()
    val utente by vm.utente.collectAsStateWithLifecycle()
    val inizio = remember { if (utente.introduzioneVista) Rotte.HOME else Rotte.INTRO }
    var schedaPreferiti by rememberSaveable { mutableIntStateOf(0) }

    LaunchedEffect(distributoreDaAprire) {
        val (provincia, id) = distributoreDaAprire ?: return@LaunchedEffect
        if (utente.introduzioneVista) nav.navigate(Rotte.dettaglio(provincia, id))
        onAperto()
    }
    // tocco sulla notifica della modalita autostrada
    LaunchedEffect(apriGuida) {
        if (!apriGuida) return@LaunchedEffect
        if (GuidaInCorso.attiva && nav.currentDestination?.route != Rotte.AUTOSTRADA) {
            nav.navigate(Rotte.AUTOSTRADA) { launchSingleTop = true }
        }
        onGuidaAperta()
    }

    val voce by nav.currentBackStackEntryAsState()
    val rotta = voce?.destination?.route
    val schedaCorrente = when (rotta) {
        Rotte.LISTA -> Rotte.MAPPA
        else -> rotta
    }
    val conBarra = schedaCorrente in schede.map { it.rotta }

    Column(Modifier.fillMaxSize().background(Colori.Sfondo)) {
        NavHost(nav, startDestination = inizio, modifier = Modifier.weight(1f)) {
            composable(Rotte.INTRO) {
                SchermataIntro(vm) {
                    vm.completaIntroduzione()
                    nav.navigate(Rotte.HOME) { popUpTo(Rotte.INTRO) { inclusive = true } }
                }
            }
            composable(Rotte.HOME) {
                SchermataHome(
                    vm = vm,
                    onDistributore = { nav.navigate(Rotte.dettaglio(it.provincia, it.id)) },
                    onVediTutti = { nav.navigate(Rotte.LISTA) },
                    onRifornimento = { nav.navigate(Rotte.rifornimento()) },
                    onViaggio = { nav.vaiAScheda(Rotte.VIAGGIO) },
                    onAvvisi = {
                        schedaPreferiti = 1
                        nav.vaiAScheda(Rotte.PREFERITI)
                    },
                    onProfilo = { nav.vaiAScheda(Rotte.PROFILO) },
                    onStatistiche = { nav.navigate(Rotte.STATISTICHE) },
                    onSostieni = { nav.navigate(Rotte.SOSTIENI) },
                    onNuovaAuto = { nav.navigate(Rotte.auto()) },
                    onLuogo = { nav.navigate(Rotte.luogo(tipo = "CASA")) },
                    onColonnina = { nav.navigate(Rotte.colonnina(it.id)) },
                    onMappaColonnine = {
                        vm.mostraColonnine(true)
                        nav.vaiAScheda(Rotte.MAPPA)
                        // se nella scheda Mappa era rimasta aperta la lista, torniamo alla mappa
                        nav.popBackStack(Rotte.MAPPA, inclusive = false)
                    },
                    onTariffa = { nav.navigate(Rotte.TARIFFA) },
                )
            }
            composable(Rotte.MAPPA) {
                SchermataMappa(
                    vm = vm,
                    onDistributore = { nav.navigate(Rotte.dettaglio(it.provincia, it.id)) },
                    onLista = { nav.navigate(Rotte.LISTA) { launchSingleTop = true } },
                    onColonnina = { nav.navigate(Rotte.colonnina(it.id)) },
                )
            }
            composable(Rotte.LISTA) {
                SchermataLista(
                    vm = vm,
                    onDistributore = { nav.navigate(Rotte.dettaglio(it.provincia, it.id)) },
                    onMappa = { if (!nav.popBackStack(Rotte.MAPPA, inclusive = false)) nav.vaiAScheda(Rotte.MAPPA) },
                )
            }
            composable(Rotte.VIAGGIO) {
                SchermataViaggio(
                    vm = vm,
                    onDistributore = { nav.navigate(Rotte.dettaglio(it.provincia, it.id)) },
                    onAuto = { nav.navigate(Rotte.auto(vm.utente.value.autoCorrente?.id)) },
                    onColonnina = { nav.navigate(Rotte.colonnina(it.id)) },
                    onAutostrada = { nav.navigate(Rotte.AUTOSTRADA) { launchSingleTop = true } },
                )
            }
            composable(Rotte.PREFERITI) {
                SchermataPreferiti(
                    vm = vm,
                    scheda = schedaPreferiti,
                    onScheda = { schedaPreferiti = it },
                    onDistributore = { provincia, id -> nav.navigate(Rotte.dettaglio(provincia, id)) },
                    onNuovoAvviso = { nav.navigate(Rotte.avviso()) },
                    onAvviso = { nav.navigate(Rotte.avviso(it)) },
                    onLuogo = { id, tipo -> nav.navigate(Rotte.luogo(id, tipo)) },
                    onCerca = { nav.navigate(Rotte.LISTA) },
                )
            }
            composable(Rotte.PROFILO) {
                SchermataProfilo(
                    vm = vm,
                    onImpostazioni = { nav.navigate(Rotte.IMPOSTAZIONI) },
                    onAuto = { nav.navigate(Rotte.auto(it)) },
                    onRegistro = { nav.navigate(Rotte.REGISTRO) },
                    onStatistiche = { nav.navigate(Rotte.STATISTICHE) },
                    onSostieni = { nav.navigate(Rotte.SOSTIENI) },
                )
            }
            composable(
                Rotte.DETTAGLIO,
                arguments = listOf(
                    navArgument("prov") { type = NavType.StringType },
                    navArgument("id") { type = NavType.LongType },
                ),
            ) { e ->
                val provincia = e.arguments?.getString("prov").orEmpty()
                val id = e.arguments?.getLong("id") ?: -1L
                SchermataDettaglio(
                    vm = vm,
                    provincia = provincia,
                    id = id,
                    onIndietro = { nav.popBackStack() },
                    onRifornimento = { nav.navigate(Rotte.rifornimento(provincia, id)) },
                )
            }
            composable(
                Rotte.RIFORNIMENTO,
                arguments = listOf(
                    navArgument("prov") { type = NavType.StringType; defaultValue = "" },
                    navArgument("id") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { e ->
                SchermataRifornimento(
                    vm = vm,
                    provincia = e.arguments?.getString("prov")?.takeIf { it.isNotBlank() },
                    id = e.arguments?.getLong("id")?.takeIf { it > 0 },
                    onChiudi = { nav.popBackStack() },
                )
            }
            composable(
                Rotte.AVVISO,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("prov") { type = NavType.StringType; defaultValue = "" },
                    navArgument("distributore") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { e ->
                SchermataAvviso(
                    vm = vm,
                    id = e.arguments?.getString("id")?.takeIf { it.isNotBlank() },
                    onChiudi = { nav.popBackStack() },
                    onNuovoLuogo = { nav.navigate(Rotte.luogo(tipo = "ALTRO")) },
                )
            }
            composable(Rotte.AUTO, arguments = listOf(navArgument("id") { type = NavType.StringType; defaultValue = "" })) { e ->
                SchermataAuto(vm = vm, id = e.arguments?.getString("id")?.takeIf { it.isNotBlank() }, onChiudi = { nav.popBackStack() })
            }
            composable(Rotte.IMPOSTAZIONI) {
                SchermataImpostazioni(
                    vm = vm,
                    onIndietro = { nav.popBackStack() },
                    onLuogo = { id, tipo -> nav.navigate(Rotte.luogo(id, tipo)) },
                    onSostieni = { nav.navigate(Rotte.SOSTIENI) },
                    onTariffa = { nav.navigate(Rotte.TARIFFA) },
                )
            }
            composable(
                Rotte.LUOGO,
                arguments = listOf(
                    navArgument("id") { type = NavType.StringType; defaultValue = "" },
                    navArgument("tipo") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { e ->
                SchermataLuogo(
                    vm = vm,
                    id = e.arguments?.getString("id")?.takeIf { it.isNotBlank() },
                    tipo = e.arguments?.getString("tipo")?.takeIf { it.isNotBlank() },
                    onChiudi = { nav.popBackStack() },
                )
            }
            composable(Rotte.REGISTRO) { SchermataRegistro(vm = vm, onIndietro = { nav.popBackStack() }) }
            composable(Rotte.STATISTICHE) { SchermataStatistiche(vm = vm, onIndietro = { nav.popBackStack() }) }
            composable(Rotte.SOSTIENI) { SchermataSostieni(onIndietro = { nav.popBackStack() }) }
            composable(Rotte.COLONNINA, arguments = listOf(navArgument("id") { type = NavType.StringType })) { e ->
                SchermataColonnina(
                    vm = vm,
                    id = e.arguments?.getString("id").orEmpty(),
                    onIndietro = { nav.popBackStack() },
                    onTariffa = { nav.navigate(Rotte.TARIFFA) },
                )
            }
            composable(Rotte.TARIFFA) { SchermataTariffa(vm = vm, onIndietro = { nav.popBackStack() }) }
            composable(Rotte.AUTOSTRADA) { SchermataAutostrada(vm = vm, onIndietro = { nav.popBackStack() }) }
        }
        if (conBarra) {
            BarraNavigazione(schedaCorrente) { nav.vaiAScheda(it) }
        }
    }
}

@Composable
private fun BarraNavigazione(corrente: String?, onScheda: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Colori.Superficie)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Colori.Bordo))
        Row(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .height(76.dp)
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            schede.forEach { scheda ->
                val attiva = scheda.rotta == corrente
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable(role = Role.Tab) { if (!attiva) onScheda(scheda.rotta) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    Box(
                        Modifier
                            .size(width = 56.dp, height = 32.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (attiva) Colori.PetrolioChiaro else Colori.Superficie),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(scheda.icona, null, tint = if (attiva) Colori.Petrolio else Colori.Testo3, modifier = Modifier.size(22.dp))
                    }
                    Text(
                        scheda.etichetta,
                        style = Testi.Piccolo.copy(
                            color = if (attiva) Colori.Petrolio else Colori.Testo3,
                            fontWeight = if (attiva) FontWeight.ExtraBold else FontWeight.SemiBold,
                        ),
                    )
                }
            }
        }
    }
}
