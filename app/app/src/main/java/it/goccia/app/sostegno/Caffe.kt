package it.goccia.app.sostegno

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClient.BillingResponseCode
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.ConsumeParams
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Un caffe da offrire: prodotto in-app consumabile di Google Play. */
data class OffertaCaffe(val id: String, val nome: String, val prezzo: String, val dettagli: ProductDetails)

sealed interface StatoCaffe {
    data object Caricamento : StatoCaffe

    /** Google Play non c'e, non risponde o i prodotti non sono (ancora) pubblicati. */
    data object NonDisponibile : StatoCaffe

    data class Pronto(val offerte: List<OffertaCaffe>, val avviso: String? = null) : StatoCaffe

    /** Pagamento avviato ma non ancora completato (per esempio in contanti in un negozio). */
    data class InAttesa(val offerte: List<OffertaCaffe>) : StatoCaffe

    data class Grazie(val offerte: List<OffertaCaffe>) : StatoCaffe
}

/**
 * I caffe per sostenere Goccia, solo nella versione per Google Play: tre prodotti in-app da 1, 3
 * e 5 euro, consumabili (si possono offrire piu volte). Non sbloccano niente: ogni funzione e di
 * tutti. I prodotti vanno creati in Play Console con questi identificativi ([PRODOTTI]).
 */
class Caffe(context: Context) : PurchasesUpdatedListener {
    private val client: BillingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    private val _stato = MutableStateFlow<StatoCaffe>(StatoCaffe.Caricamento)
    val stato: StateFlow<StatoCaffe> = _stato.asStateFlow()

    private var offerte: List<OffertaCaffe> = emptyList()

    fun avvia() {
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(risultato: BillingResult) {
                if (risultato.responseCode == BillingResponseCode.OK) {
                    cercaOfferte()
                    consumaRimasti()
                } else {
                    Log.w(TAG, "Google Play non disponibile: ${risultato.responseCode} ${risultato.debugMessage}")
                    _stato.value = StatoCaffe.NonDisponibile
                }
            }

            // con la riconnessione automatica la libreria riprova da sola
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    fun chiudi() {
        client.endConnection()
    }

    private fun cercaOfferte() {
        val prodotti = PRODOTTI.keys.map { id ->
            QueryProductDetailsParams.Product.newBuilder()
                .setProductId(id)
                .setProductType(BillingClient.ProductType.INAPP)
                .build()
        }
        val parametri = QueryProductDetailsParams.newBuilder().setProductList(prodotti).build()
        client.queryProductDetailsAsync(parametri) { risultato, trovati ->
            if (risultato.responseCode != BillingResponseCode.OK) {
                Log.w(TAG, "prodotti non letti: ${risultato.responseCode} ${risultato.debugMessage}")
                _stato.value = StatoCaffe.NonDisponibile
                return@queryProductDetailsAsync
            }
            offerte = trovati.productDetailsList.mapNotNull { d ->
                val id = d.productId ?: return@mapNotNull null
                val prezzo = (d.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: d.oneTimePurchaseOfferDetails)
                    ?.formattedPrice ?: return@mapNotNull null
                OffertaCaffe(id, PRODOTTI[id] ?: d.name.orEmpty(), prezzo, d)
            }.sortedBy { PRODOTTI.keys.indexOf(it.id) }
            _stato.value = if (offerte.isEmpty()) StatoCaffe.NonDisponibile else StatoCaffe.Pronto(offerte)
        }
    }

    /** Apre il pagamento di Google Play per il caffe scelto. */
    fun offri(attivita: Activity, offerta: OffertaCaffe) {
        val dettagli = offerta.dettagli
        val parametriProdotto = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(dettagli)
        (dettagli.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: dettagli.oneTimePurchaseOfferDetails)
            ?.offerToken?.takeIf { it.isNotEmpty() }
            ?.let { parametriProdotto.setOfferToken(it) }
        val parametri = BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(parametriProdotto.build())).build()
        val risultato = client.launchBillingFlow(attivita, parametri)
        if (risultato.responseCode != BillingResponseCode.OK) {
            Log.w(TAG, "pagamento non avviato: ${risultato.responseCode} ${risultato.debugMessage}")
            _stato.value = StatoCaffe.Pronto(offerte, "Google Play non ha aperto il pagamento: riprova tra poco.")
        }
    }

    override fun onPurchasesUpdated(risultato: BillingResult, acquisti: MutableList<Purchase>?) {
        when (risultato.responseCode) {
            BillingResponseCode.OK -> acquisti.orEmpty().forEach { gestisci(it, nuovo = true) }
            BillingResponseCode.USER_CANCELED -> _stato.value = StatoCaffe.Pronto(offerte)
            BillingResponseCode.ITEM_ALREADY_OWNED -> {
                // un caffe precedente non ancora consumato: lo consumiamo e si puo riprovare
                consumaRimasti()
                _stato.value = StatoCaffe.Pronto(offerte, "Il pagamento precedente era ancora in sospeso: riprova.")
            }
            else -> {
                Log.w(TAG, "pagamento non riuscito: ${risultato.responseCode} ${risultato.debugMessage}")
                _stato.value = StatoCaffe.Pronto(offerte, "Il pagamento non è andato a buon fine. Non ti è stato addebitato nulla.")
            }
        }
    }

    private fun gestisci(acquisto: Purchase, nuovo: Boolean) {
        if (acquisto.products.none { it in PRODOTTI }) return
        when (acquisto.purchaseState) {
            Purchase.PurchaseState.PURCHASED -> {
                consuma(acquisto)
                if (nuovo) _stato.value = StatoCaffe.Grazie(offerte)
            }
            Purchase.PurchaseState.PENDING -> if (nuovo) _stato.value = StatoCaffe.InAttesa(offerte)
            else -> Unit
        }
    }

    /** Consumato, il caffe si puo offrire di nuovo (e Google Play lo considera confermato). */
    private fun consuma(acquisto: Purchase) {
        val parametri = ConsumeParams.newBuilder().setPurchaseToken(acquisto.purchaseToken).build()
        client.consumeAsync(parametri) { risultato, _ ->
            if (risultato.responseCode != BillingResponseCode.OK) {
                Log.w(TAG, "caffe non consumato: ${risultato.responseCode} ${risultato.debugMessage}")
            }
        }
    }

    /** I pagamenti completati mentre l'app era chiusa (per esempio quelli in attesa) vanno consumati. */
    private fun consumaRimasti() {
        val parametri = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        client.queryPurchasesAsync(parametri) { risultato, acquisti ->
            if (risultato.responseCode == BillingResponseCode.OK) acquisti.forEach { gestisci(it, nuovo = false) }
        }
    }

    companion object {
        private const val TAG = "Goccia"

        /** Identificativi dei prodotti in Play Console, con il nome mostrato nell'app. */
        val PRODOTTI = linkedMapOf(
            "caffe_1" to "Un caffè",
            "caffe_3" to "Caffè e cornetto",
            "caffe_5" to "Una colazione",
        )
    }
}

/** L'Activity dietro un Context di Compose (serve per aprire il pagamento di Google Play). */
tailrec fun Context.attivita(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.attivita()
    else -> null
}
