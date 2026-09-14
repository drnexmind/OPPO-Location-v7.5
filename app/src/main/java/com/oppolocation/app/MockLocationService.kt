package com.oppolocation.app

import android.app.*
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.os.*
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import kotlin.math.*

class MockLocationService : Service() {

    private lateinit var lm: LocationManager
    private lateinit var fused: FusedLocationProviderClient
    private lateinit var prefs: AppPrefs

    private val scheduler = Executors.newSingleThreadScheduledExecutor()
    private var loop: ScheduledFuture<*>? = null

    private var targetLat = 0.0
    private var targetLon = 0.0
    @Volatile private var fusedVerified = false
    @Volatile private var gpsFallback = false

    override fun onCreate() {
        super.onCreate()
        prefs = AppPrefs(this)
        lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        fused = LocationServices.getFusedLocationProviderClient(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopMocking()
            ACTION_START, ACTION_UPDATE -> {
                targetLat = intent.getDoubleExtra(EXTRA_LAT, prefs.lastLat)
                targetLon = intent.getDoubleExtra(EXTRA_LON, prefs.lastLon)

                startForeground(
                    NOTIFICATION_ID,
                    notification("Verifying mock location…")
                )

                startVerifiedMock()
            }
        }
        return START_NOT_STICKY
    }

    private fun startVerifiedMock() {
        loop?.cancel(true)
        fusedVerified = false
        gpsFallback = false
        prefs.running = false

        val first = buildLocation(LocationManager.GPS_PROVIDER)

        try {
            fused.setMockMode(true)
                .addOnSuccessListener {
                    fused.setMockLocation(first)
                        .addOnSuccessListener {
                            verifyFusedResult()
                        }
                        .addOnFailureListener {
                            tryGpsFallback()
                        }
                }
                .addOnFailureListener {
                    tryGpsFallback()
                }
        } catch (_: Exception) {
            tryGpsFallback()
        }
    }

    @Suppress("MissingPermission")
    private fun verifyFusedResult() {
        fused.lastLocation
            .addOnSuccessListener { result ->
                if (result != null && isNearTarget(result)) {
                    fusedVerified = true
                    markVerifiedAndLoop("FUSED VERIFIED")
                } else {
                    // Give Google Play services one more immediate update, then verify again.
                    val second = buildLocation(LocationManager.GPS_PROVIDER)
                    fused.setMockLocation(second)
                        .addOnSuccessListener {
                            scheduler.schedule({
                                fused.lastLocation
                                    .addOnSuccessListener { secondResult ->
                                        if (secondResult != null && isNearTarget(secondResult)) {
                                            fusedVerified = true
                                            markVerifiedAndLoop("FUSED VERIFIED")
                                        } else {
                                            tryGpsFallback()
                                        }
                                    }
                                    .addOnFailureListener { tryGpsFallback() }
                            }, 350, TimeUnit.MILLISECONDS)
                        }
                        .addOnFailureListener { tryGpsFallback() }
                }
            }
            .addOnFailureListener {
                tryGpsFallback()
            }
    }

    @Suppress("DEPRECATION")
    private fun tryGpsFallback() {
        gpsFallback = try {
            try {
                lm.addTestProvider(
                    LocationManager.GPS_PROVIDER,
                    false, false, false, false,
                    true, true, true,
                    0, 5
                )
            } catch (_: IllegalArgumentException) { }

            lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
            lm.setTestProviderLocation(
                LocationManager.GPS_PROVIDER,
                buildLocation(LocationManager.GPS_PROVIDER)
            )

            val check = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            check != null && isNearTarget(check)
        } catch (_: Exception) {
            false
        }

        if (gpsFallback) {
            markVerifiedAndLoop("GPS VERIFIED")
        } else {
            fail("Android did not accept the selected mock coordinates.")
        }
    }

    private fun markVerifiedAndLoop(engine: String) {
        prefs.running = true
        notifyState(engine)

        loop?.cancel(true)
        val interval = prefs.updateIntervalMs.coerceIn(500L, 5000L)

        loop = scheduler.scheduleAtFixedRate({
            val location = buildLocation(LocationManager.GPS_PROVIDER)

            if (fusedVerified) {
                fused.setMockLocation(location)
                    .addOnFailureListener {
                        fusedVerified = false
                    }
            }

            if (gpsFallback) {
                try {
                    lm.setTestProviderLocation(
                        LocationManager.GPS_PROVIDER,
                        location
                    )
                } catch (_: Exception) {
                    gpsFallback = false
                }
            }

            if (!fusedVerified && !gpsFallback) {
                fail("Mock location stopped.")
            }
        }, interval, interval, TimeUnit.MILLISECONDS)
    }

    private fun buildLocation(provider: String): Location =
        Location(provider).apply {
            latitude = targetLat
            longitude = targetLon
            altitude = prefs.altitude
            accuracy = prefs.accuracy.coerceAtLeast(1f)
            speed = prefs.speedMps.coerceAtLeast(0f)
            bearing = ((prefs.bearing % 360f) + 360f) % 360f
            time = System.currentTimeMillis()
            elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()

            if (Build.VERSION.SDK_INT >= 26) {
                verticalAccuracyMeters = max(accuracy * 1.5f, 2f)
                speedAccuracyMetersPerSecond = 0.2f
                bearingAccuracyDegrees = 1f
            }
        }

    private fun isNearTarget(location: Location): Boolean {
        val result = FloatArray(1)
        Location.distanceBetween(
            targetLat, targetLon,
            location.latitude, location.longitude,
            result
        )
        return result[0] <= max(25f, prefs.accuracy * 4f)
    }

    private fun notifyState(engine: String) {
        LocationWidgetProvider.refreshAll(this)

        getSystemService(NotificationManager::class.java)
            .notify(
                NOTIFICATION_ID,
                notification(
                    "$engine • ${"%.5f".format(targetLat)}, ${"%.5f".format(targetLon)}"
                )
            )
    }

    private fun fail(message: String) {
        prefs.running = false
        LocationWidgetProvider.refreshAll(this)

        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(message))

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    @Suppress("DEPRECATION")
    private fun stopMocking() {
        loop?.cancel(true)
        loop = null

        try { fused.setMockMode(false) } catch (_: Exception) { }

        try {
            lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false)
        } catch (_: Exception) { }

        try {
            lm.removeTestProvider(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) { }

        fusedVerified = false
        gpsFallback = false
        prefs.running = false
        LocationWidgetProvider.refreshAll(this)

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        loop?.cancel(true)
        scheduler.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun notification(text: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_location_pin)
            .setContentTitle("OPPO Location")
            .setContentText(text)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        "Location service",
                        NotificationManager.IMPORTANCE_LOW
                    )
                )
        }
    }

    companion object {
        const val ACTION_START = "com.oppolocation.app.START"
        const val ACTION_UPDATE = "com.oppolocation.app.UPDATE"
        const val ACTION_STOP = "com.oppolocation.app.STOP"

        const val EXTRA_LAT = "lat"
        const val EXTRA_LON = "lon"
        const val EXTRA_NAME = "name"

        private const val CHANNEL_ID = "oppo_location_service"
        private const val NOTIFICATION_ID = 1101
    }
}
