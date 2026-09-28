package it.goccia.app.guida

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import it.goccia.app.MainActivity
import it.goccia.app.R
import it.goccia.app.avvisi.Notifiche
import it.goccia.app.logica.Formati
import it.goccia.app.logica.Guida
import it.goccia.app.logica.SituazioneGuida
import it.goccia.app.logica.VoceGuida

/**
 * Servizio in primo piano della modalita autostrada: segue il GPS mentre guidi (anche con un
 * navigatore aperto sopra), aggiorna la notifica fissa e avvisa prima della sosta consigliata.
 * Si ferma a destinazione, con "Termina" o se manca il permesso di posizione.
 */
class ServizioGuida : Service() {
    private var avviato = false
    private var gestore: LocationManager? = null
    private val principale = Handler(Looper.getMainLooper())

    private val ascoltatore = object : LocationListener {
        override fun onLocationChanged(posizione: Location) = nuovaPosizione(posizione)

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        override fun onProviderEnabled(provider: String) = Unit
        override fun onProviderDisabled(provider: String) = Unit
    }

    private val controllo = object : Runnable {
        override fun run() {
            GuidaInCorso.controllaSegnale(System.currentTimeMillis())
            aggiornaNotifica()
            principale.postDelayed(this, 30_000)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            AZIONE_TERMINA -> {
                termina()
                return START_NOT_STICKY
            }
            AZIONE_PIENO -> {
                GuidaInCorso.rifornito(true)
                NotificationManagerCompat.from(this).cancel(ID_DOMANDA)
            }
            AZIONE_NESSUN_RIFORNIMENTO -> {
                GuidaInCorso.rifornito(false)
                NotificationManagerCompat.from(this).cancel(ID_DOMANDA)
            }
        }
        if (GuidaInCorso.percorso == null) {
            // processo ricreato dal sistema senza il viaggio in memoria: non c'e niente da seguire
            termina()
            return START_NOT_STICKY
        }
        if (!avviato) {
            if (!permessoPosizione(this)) {
                termina()
                return START_NOT_STICKY
            }
            creaCanali(this)
            try {
                ServiceCompat.startForeground(
                    this,
                    ID_NOTIFICA,
                    notifica(),
                    if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0,
                )
            } catch (e: Exception) {
                Log.w("Goccia", "modalita autostrada: servizio non avviato", e)
                termina()
                return START_NOT_STICKY
            }
            avviato = true
            ascolta()
            principale.postDelayed(controllo, 30_000)
        } else {
            aggiornaNotifica()
        }
        return START_NOT_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun ascolta() {
        val lm = getSystemService(LocationManager::class.java) ?: return
        gestore = lm
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val fornitori = lm.getProviders(true)
        try {
            if (fine && LocationManager.GPS_PROVIDER in fornitori) {
                lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3_000L, 0f, ascoltatore, Looper.getMainLooper())
            }
            // la rete aiuta in galleria e quando il GPS e lento a partire
            if (LocationManager.NETWORK_PROVIDER in fornitori) {
                lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 10_000L, 0f, ascoltatore, Looper.getMainLooper())
            }
            val ultima = fornitori.mapNotNull { lm.getLastKnownLocation(it) }.maxByOrNull { it.time }
            if (ultima != null && System.currentTimeMillis() - ultima.time < 60_000) nuovaPosizione(ultima)
        } catch (e: SecurityException) {
            termina()
        } catch (e: IllegalArgumentException) {
            Log.w("Goccia", "modalita autostrada: fornitore di posizione non disponibile", e)
        }
    }

    private var ultimaPrecisa = 0L

    private fun nuovaPosizione(posizione: Location) {
        val adesso = System.currentTimeMillis()
        // la posizione di rete e grossolana: la usiamo solo se il GPS tace
        if (posizione.provider == LocationManager.NETWORK_PROVIDER && adesso - ultimaPrecisa < 20_000) return
        if (posizione.provider == LocationManager.GPS_PROVIDER) ultimaPrecisa = adesso
        val esito = GuidaInCorso.posizione(
            posizione.latitude,
            posizione.longitude,
            if (posizione.hasSpeed()) posizione.speed else null,
            adesso,
        ) ?: return
        Log.i("Goccia", "modalita autostrada: km ${Formati.numero(esito.situazione.km, 1)}, autonomia ${esito.situazione.autonomiaKm.toInt()} km")
        aggiornaNotifica()
        esito.preavviso?.let { avvisaSosta(it) }
        esito.domanda?.let { chiediRifornimento(it) }
        if (esito.situazione.arrivato) {
            avvisaArrivo()
            termina()
        }
    }

    private fun termina() {
        principale.removeCallbacks(controllo)
        try {
            gestore?.removeUpdates(ascoltatore)
        } catch (e: Exception) {
            // gia fermo
        }
        gestore = null
        GuidaInCorso.termina()
        NotificationManagerCompat.from(this).cancel(ID_DOMANDA)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        principale.removeCallbacks(controllo)
        try {
            gestore?.removeUpdates(ascoltatore)
        } catch (e: Exception) {
            // gia fermo
        }
        super.onDestroy()
    }

    // ------------------------------------------------------------------ notifiche

    private fun aggiornaNotifica() {
        if (!avviato || GuidaInCorso.percorso == null) return
        try {
            NotificationManagerCompat.from(this).notify(ID_NOTIFICA, notifica())
        } catch (e: SecurityException) {
            // senza permesso di notifica il servizio continua lo stesso
        }
    }

    private fun notifica(): Notification {
        val stato = GuidaInCorso.stato.value
        val s = stato?.situazione
        val titolo = "Modalità autostrada" + (stato?.destinazione?.let { " · verso $it" } ?: "")
        val testo = when {
            s == null -> "In attesa della posizione…"
            else -> riassunto(s)
        }
        val costruttore = NotificationCompat.Builder(this, CANALE)
            .setSmallIcon(R.drawable.ic_notifica)
            .setColor(0xFF0B7A75.toInt())
            .setContentTitle(titolo)
            .setContentText(testo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(listOfNotNull(testo, s?.messaggio).joinToString("\n")))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(apriSchermata(this))
        s?.consigliata?.let { costruttore.addAction(0, "Portami alla sosta", portami(it, 1)) }
        costruttore.addAction(0, "Termina", azione(AZIONE_TERMINA, 2))
        return costruttore.build()
    }

    private fun riassunto(s: SituazioneGuida): String = buildList {
        add("km ${s.km.toInt()}")
        add("autonomia ${Guida.autonomiaTesto(s.autonomiaKm)}")
        s.consigliata?.let { add("sosta tra ${Formati.km(it.traKm)}") }
    }.joinToString(" · ")

    @SuppressLint("MissingPermission")
    private fun avvisaSosta(v: VoceGuida) {
        if (!Notifiche.permesso(this)) return
        val stato = GuidaInCorso.stato.value
        val testo = "${v.nome}: ${v.valore}${if (stato?.elettrica == true) "" else " €"} · ${v.dettaglio}"
        val n = NotificationCompat.Builder(this, CANALE_AVVISI)
            .setSmallIcon(R.drawable.ic_notifica)
            .setColor(0xFF0B7A75.toInt())
            .setContentTitle("Sosta consigliata tra ${Formati.km(v.traKm)}")
            .setContentText(testo)
            .setStyle(NotificationCompat.BigTextStyle().bigText(testo))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .setAutoCancel(true)
            .setContentIntent(apriSchermata(this))
            .addAction(0, "Portami", portami(v, 3))
            .build()
        try {
            NotificationManagerCompat.from(this).notify(ID_AVVISO, n)
        } catch (e: SecurityException) {
            // permesso revocato
        }
    }

    @SuppressLint("MissingPermission")
    private fun chiediRifornimento(stazione: Pair<String, String>) {
        if (!Notifiche.permesso(this)) return
        val elettrica = GuidaInCorso.stato.value?.elettrica == true
        val n = NotificationCompat.Builder(this, CANALE_AVVISI)
            .setSmallIcon(R.drawable.ic_notifica)
            .setColor(0xFF0B7A75.toInt())
            .setContentTitle(if (elettrica) "Hai ricaricato a ${stazione.second}?" else "Hai fatto il pieno a ${stazione.second}?")
            .setContentText("Così l'autonomia riparte giusta.")
            .setAutoCancel(true)
            .setContentIntent(apriSchermata(this))
            .addAction(0, if (elettrica) "Sì, ho ricaricato" else "Sì, il pieno", azione(AZIONE_PIENO, 4))
            .addAction(0, "No", azione(AZIONE_NESSUN_RIFORNIMENTO, 5))
            .build()
        try {
            NotificationManagerCompat.from(this).notify(ID_DOMANDA, n)
        } catch (e: SecurityException) {
            // permesso revocato
        }
    }

    @SuppressLint("MissingPermission")
    private fun avvisaArrivo() {
        if (!Notifiche.permesso(this)) return
        val n = NotificationCompat.Builder(this, CANALE)
            .setSmallIcon(R.drawable.ic_notifica)
            .setColor(0xFF0B7A75.toInt())
            .setContentTitle("Sei arrivato")
            .setContentText("La modalità autostrada si è chiusa da sola. Buon arrivo!")
            .setAutoCancel(true)
            .setTimeoutAfter(10 * 60_000L)
            .build()
        try {
            NotificationManagerCompat.from(this).notify(ID_AVVISO, n)
        } catch (e: SecurityException) {
            // permesso revocato
        }
    }

    private fun azione(azione: String, codice: Int): PendingIntent =
        PendingIntent.getService(
            this,
            codice,
            Intent(this, ServizioGuida::class.java).setAction(azione),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun portami(v: VoceGuida, codice: Int): PendingIntent {
        val intent = intentNavigazione(this, v.lat, v.lon, v.nome).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(this, codice, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    companion object {
        const val AZIONE_TERMINA = "it.goccia.app.guida.TERMINA"
        const val AZIONE_PIENO = "it.goccia.app.guida.PIENO"
        const val AZIONE_NESSUN_RIFORNIMENTO = "it.goccia.app.guida.NESSUN_RIFORNIMENTO"
        const val EXTRA_APRI = "apri"
        const val APRI_AUTOSTRADA = "autostrada"
        private const val CANALE = "guida"
        private const val CANALE_AVVISI = "guida-avvisi"
        private const val ID_NOTIFICA = 7_001
        private const val ID_AVVISO = 7_002
        private const val ID_DOMANDA = 7_003

        fun permessoPosizione(context: Context): Boolean =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /** Avvia il servizio: il percorso deve essere gia in [GuidaInCorso]. Falso se non si puo. */
        fun avvia(context: Context): Boolean {
            if (!permessoPosizione(context) || !GuidaInCorso.attiva) return false
            return try {
                ContextCompat.startForegroundService(context, Intent(context, ServizioGuida::class.java))
                true
            } catch (e: Exception) {
                Log.w("Goccia", "modalita autostrada: avvio non riuscito", e)
                false
            }
        }

        fun termina(context: Context) {
            if (!GuidaInCorso.attiva) return
            try {
                context.startService(Intent(context, ServizioGuida::class.java).setAction(AZIONE_TERMINA))
            } catch (e: Exception) {
                GuidaInCorso.termina()
            }
        }

        /** Segna (o nega) un rifornimento dalla schermata: passa dal servizio, che chiude la domanda. */
        fun rispondi(context: Context, pieno: Boolean) {
            try {
                context.startService(
                    Intent(context, ServizioGuida::class.java).setAction(if (pieno) AZIONE_PIENO else AZIONE_NESSUN_RIFORNIMENTO),
                )
            } catch (e: Exception) {
                GuidaInCorso.rifornito(pieno)
            }
        }

        fun creaCanali(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java) ?: return
            nm.createNotificationChannel(
                NotificationChannel(CANALE, "Modalità autostrada", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "La notifica fissa mentre la modalità autostrada segue il viaggio"
                    setShowBadge(false)
                },
            )
            nm.createNotificationChannel(
                NotificationChannel(CANALE_AVVISI, "Soste in viaggio", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "L'avviso prima della sosta consigliata e la domanda sul rifornimento"
                },
            )
        }

        fun apriSchermata(context: Context): PendingIntent {
            val apri = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_APRI, APRI_AUTOSTRADA)
            }
            return PendingIntent.getActivity(context, 7_000, apri, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        }

        /**
         * Il navigatore verso un punto, come intent da mettere in una notifica: Google Maps se c'e,
         * altrimenti qualunque app che apre le coordinate.
         */
        fun intentNavigazione(context: Context, lat: Double, lon: Double, nome: String): Intent {
            val maps = Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=$lat,$lon")).setPackage("com.google.android.apps.maps")
            if (maps.resolveActivity(context.packageManager) != null) return maps
            return Intent(Intent.ACTION_VIEW, Uri.parse("geo:$lat,$lon?q=$lat,$lon(${Uri.encode(nome)})"))
        }
    }
}
