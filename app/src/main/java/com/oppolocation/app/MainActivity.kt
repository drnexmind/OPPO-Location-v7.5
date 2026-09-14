package com.oppolocation.app

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.oppolocation.app.databinding.ActivityMainBinding
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private lateinit var mapView: WebView

    private val store by lazy { PlaceStore(this) }
    private val prefs by lazy { AppPrefs(this) }
    private val search = SearchClient()
    private val fused by lazy { LocationServices.getFusedLocationProviderClient(this) }

    private var selected = SavedPlace(
        name = "El Oued",
        latitude = 33.3683,
        longitude = 6.8674
    )

    private var pendingRealLocationRequest = false
    private var pendingStartMock = false
    private var mapReady = false
    private var currentMapStyle = "streets"

    private val notif =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    private val locationPermission =
        registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { result ->
            val granted =
                result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                result[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (granted && pendingRealLocationRequest) {
                pendingRealLocationRequest = false
                loadRealLocation()
            } else if (granted && pendingStartMock) {
                pendingStartMock = false
                startMockLocation()
            } else if (!granted) {
                pendingRealLocationRequest = false
                pendingStartMock = false
                toast("Location permission is required.")
            }
        }

    private val exportLauncher =
        registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/json")
        ) { u: Uri? ->
            if (u != null) {
                contentResolver.openOutputStream(u)?.use {
                    it.write(store.exportJson().toByteArray())
                }
            }
        }

    private val importLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { u: Uri? ->
            if (u != null) {
                try {
                    val raw =
                        contentResolver.openInputStream(u)
                            ?.bufferedReader()
                            ?.use { it.readText() }
                            ?: return@registerForActivityResult

                    val n = store.importJson(raw)
                    toast("Imported $n places")
                } catch (_: Exception) {
                    toast("Import failed")
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)

        selected = SavedPlace(
            name = prefs.lastName,
            latitude = prefs.lastLat,
            longitude = prefs.lastLon
        )

        mapView = b.mapView
        setupMapWebView()
        setupActions()
        requestNotif()
        updateUi()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupMapWebView() {
        mapView.settings.javaScriptEnabled = true
        mapView.settings.domStorageEnabled = true
        mapView.settings.allowFileAccess = false
        mapView.settings.allowContentAccess = false
        mapView.webChromeClient = WebChromeClient()
        mapView.webViewClient = WebViewClient()
        mapView.addJavascriptInterface(MapBridge(), "Android")

        val key = JSONObject.quote(BuildConfig.MAPTILER_API_KEY)
        val lat = selected.latitude
        val lon = selected.longitude

        val html = """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <meta name="viewport" content="width=device-width,initial-scale=1,user-scalable=no">
              <script src="https://cdn.maptiler.com/maptiler-sdk-js/v4.1.0/maptiler-sdk.umd.min.js"></script>
              <link href="https://cdn.maptiler.com/maptiler-sdk-js/v4.1.0/maptiler-sdk.css" rel="stylesheet">
              <style>
                html,body,#map { margin:0; width:100%; height:100%; overflow:hidden; }
                .pin {
                  width:22px; height:22px; border-radius:50% 50% 50% 0;
                  background:#e53935; transform:rotate(-45deg);
                  border:3px solid white; box-shadow:0 1px 5px rgba(0,0,0,.5);
                }
                .crosshair {
                  position:absolute; z-index:5; left:50%; top:50%;
                  width:18px; height:18px; margin:-9px 0 0 -9px;
                  pointer-events:none;
                }
                .crosshair:before,.crosshair:after {
                  content:""; position:absolute; background:#111;
                }
                .crosshair:before { left:8px; top:0; width:2px; height:18px; }
                .crosshair:after { top:8px; left:0; height:2px; width:18px; }
              </style>
            </head>
            <body>
              <div id="map"></div>
              <div class="crosshair"></div>
              <script>
                maptilersdk.config.apiKey = $key;
                let target = [$lon, $lat];
                let styleName = 'streets';

                const map = new maptilersdk.Map({
                  container: 'map',
                  style: maptilersdk.MapStyle.STREETS,
                  center: target,
                  zoom: 16
                });

                let marker = new maptilersdk.Marker({color:'#e53935'})
                  .setLngLat(target)
                  .addTo(map);

                map.on('load', () => {
                  Android.onMapReady();
                });

                map.on('click', (e) => {
                  const p = e.lngLat;
                  target = [p.lng, p.lat];
                  marker.setLngLat(target);
                  Android.onMapSelected(p.lat, p.lng);
                });

                function setTarget(lat, lon, zoom) {
                  target = [lon, lat];
                  marker.setLngLat(target);
                  map.easeTo({center: target, zoom: zoom || 16});
                }

                function useCenter() {
                  const c = map.getCenter();
                  target = [c.lng, c.lat];
                  marker.setLngLat(target);
                  Android.onMapSelected(c.lat, c.lng);
                }

                function setMapMode(mode) {
                  styleName = mode;
                  if (mode === 'hybrid') {
                    map.setStyle(maptilersdk.MapStyle.HYBRID);
                  } else {
                    map.setStyle(maptilersdk.MapStyle.STREETS);
                  }
                  map.once('styledata', () => {
                    marker.setLngLat(target);
                  });
                }
              </script>
            </body>
            </html>
        """.trimIndent()

        mapView.loadDataWithBaseURL(
            "https://appassets.androidplatform.net/",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    private inner class MapBridge {
        @JavascriptInterface
        fun onMapReady() {
            runOnUiThread {
                mapReady = true
                b.tvStatus.text = if (prefs.running) "ACTIVE • VERIFIED" else "MAP READY"
            }
        }

        @JavascriptInterface
        fun onMapSelected(lat: Double, lon: Double) {
            runOnUiThread {
                selected = SavedPlace(
                    name = "Pinned location",
                    latitude = lat,
                    longitude = lon
                )
                saveLast()
                updateUi()
            }
        }
    }

    private fun setupActions() {
        b.btnMapNormal.setOnClickListener {
            currentMapStyle = "streets"
            evalJs("setMapMode('streets')")
        }

        b.btnMapSatellite.setOnClickListener {
            currentMapStyle = "hybrid"
            evalJs("setMapMode('hybrid')")
        }

        b.btnSearch.setOnClickListener {
            val q = b.etSearch.text.toString().trim()
            if (q.isBlank()) return@setOnClickListener

            b.progress.visibility = View.VISIBLE
            search.search(q) { r ->
                b.progress.visibility = View.GONE

                r.onSuccess {
                    selected = it
                    saveLast()
                    showSelectedOnMap(17.0)
                    updateUi()
                }.onFailure {
                    toast("Location not found")
                }
            }
        }

        b.btnMyLocation.setOnClickListener { requestRealLocation() }
        b.btnStart.setOnClickListener { requestStartMock() }

        b.btnStop.setOnClickListener {
            startService(serviceIntent(MockLocationService.ACTION_STOP))
            prefs.running = false
            updateUi()
        }

        b.btnFavorite.setOnClickListener {
            store.addFavorite(selected)
            toast("Saved to favorites")
        }

        b.btnFavorites.setOnClickListener {
            showPlaces("Favorites", store.favorites())
        }

        b.btnHistory.setOnClickListener {
            showPlaces("History", store.history())
        }

        b.btnSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        b.btnDeveloperOptions.setOnClickListener {
            startActivity(Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS))
        }

        b.btnExport.setOnClickListener {
            exportLauncher.launch("oppo-location-places.json")
        }

        b.btnImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/*"))
        }
    }

    private fun showSelectedOnMap(zoom: Double = 16.0) {
        evalJs(
            "setTarget(${selected.latitude}, ${selected.longitude}, $zoom)"
        )
    }

    private fun evalJs(script: String) {
        if (mapReady) {
            mapView.evaluateJavascript(script, null)
        }
    }

    private fun requestStartMock() {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) {
            pendingStartMock = true
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        startMockLocation()
    }

    private fun startMockLocation() {
        store.addHistory(selected)
        saveLast()
        b.tvStatus.text = "VERIFYING MOCK…"

        ContextCompat.startForegroundService(
            this,
            serviceIntent(MockLocationService.ACTION_START)
        )
    }

    private fun requestRealLocation() {
        if (prefs.running) {
            toast("Stop Mock Location first.")
            return
        }

        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fine && !coarse) {
            pendingRealLocationRequest = true
            locationPermission.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }

        loadRealLocation()
    }

    @Suppress("MissingPermission")
    private fun loadRealLocation() {
        b.progress.visibility = View.VISIBLE
        b.tvStatus.text = "READING REAL LOCATION…"

        val token = CancellationTokenSource()

        fused.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            token.token
        ).addOnSuccessListener { location: Location? ->
            b.progress.visibility = View.GONE

            if (location == null) {
                b.tvStatus.text = "REAL LOCATION UNAVAILABLE"
                return@addOnSuccessListener
            }

            selected = SavedPlace(
                name = "Current real location",
                latitude = location.latitude,
                longitude = location.longitude
            )

            saveLast()
            showSelectedOnMap(17.0)
            updateUi()

            b.tvStatus.text =
                "REAL • accuracy ${location.accuracy.toInt()} m"
        }.addOnFailureListener {
            b.progress.visibility = View.GONE
            b.tvStatus.text = "REAL LOCATION ERROR"
        }
    }

    private fun serviceIntent(action: String) =
        Intent(this, MockLocationService::class.java)
            .setAction(action)
            .putExtra(MockLocationService.EXTRA_LAT, selected.latitude)
            .putExtra(MockLocationService.EXTRA_LON, selected.longitude)
            .putExtra(MockLocationService.EXTRA_NAME, selected.name)

    private fun saveLast() {
        prefs.lastLat = selected.latitude
        prefs.lastLon = selected.longitude
        prefs.lastName = selected.name
        LocationWidgetProvider.refreshAll(this)
    }

    private fun updateUi() {
        b.tvPlace.text = selected.name
        b.tvCoordinates.text =
            "%.6f, %.6f".format(selected.latitude, selected.longitude)

        if (!b.tvStatus.text.toString().startsWith("REAL") &&
            !b.tvStatus.text.toString().startsWith("VERIFYING")) {
            b.tvStatus.text =
                if (prefs.running) "ACTIVE • VERIFIED"
                else "STOPPED"
        }
    }

    private fun showPlaces(title: String, places: List<SavedPlace>) {
        showList(
            title,
            places.map {
                "${it.name}\n${it.latitude}, ${it.longitude}"
            }
        ) { idx ->
            selected = places[idx]
            saveLast()
            showSelectedOnMap()
            updateUi()
        }
    }

    private fun showList(
        title: String,
        labels: List<String>,
        click: ((Int) -> Unit)?
    ) {
        val d = BottomSheetDialog(this)
        val v = layoutInflater.inflate(
            R.layout.sheet_places,
            null
        ) as android.widget.LinearLayout

        v.findViewById<android.widget.TextView>(
            R.id.sheetTitle
        ).text = title

        val l =
            v.findViewById<android.widget.ListView>(
                R.id.placeList
            )

        l.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_list_item_1,
            labels
        )

        if (click != null) {
            l.setOnItemClickListener { _, _, p, _ ->
                click(p)
                d.dismiss()
            }
        }

        d.setContentView(v)
        d.show()
    }

    private fun requestNotif() {
        if (
            Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notif.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun toast(s: String) =
        Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        mapView.removeJavascriptInterface("Android")
        mapView.destroy()
        super.onDestroy()
    }
}
