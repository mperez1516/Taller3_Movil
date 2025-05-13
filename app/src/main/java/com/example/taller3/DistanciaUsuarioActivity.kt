package com.example.taller3

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.location.Location
import android.os.Bundle
import android.os.Environment
import android.os.Looper
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.ScaleBarOverlay
import org.osmdroid.views.overlay.compass.CompassOverlay
import java.io.File

class DistanciaUsuarioActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var tvDistancia: TextView

    private var currentLocation: GeoPoint? = null
    private var trackedUserLocation: GeoPoint? = null
    private var trackedUserMarker: Marker? = null
    private var currentUserMarker: Marker? = null
    private lateinit var userId: String

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración crítica de OSMDroid ANTES de setContentView
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
            load(this@DistanciaUsuarioActivity, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        }

        setContentView(R.layout.activity_distancia_usuario)

        // Inicializar vistas
        tvDistancia = findViewById(R.id.tvDistancia)
        map = findViewById(R.id.osmMap)

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Obtener userId del intent
        userId = intent.getStringExtra("userId") ?: run {
            Toast.makeText(this, "Usuario no válido", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        initializeMap()
        requestLocationPermission()
    }

    private fun initializeMap() {
        // Configuración básica del mapa
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.minZoomLevel = 3.0
        map.maxZoomLevel = 19.0
        map.controller.setZoom(15.0)

        // Overlays útiles
        val compassOverlay = CompassOverlay(this, map).apply {
            enableCompass()
        }
        map.overlays.add(compassOverlay)

        val scaleBarOverlay = ScaleBarOverlay(map).apply {
            setCentred(true)
            setScaleBarOffset(resources.displayMetrics.widthPixels / 2, 10)
        }
        map.overlays.add(scaleBarOverlay)
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST_CODE
            )
        } else {
            startLocationUpdates()
            startTrackingUser()
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 3000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    currentLocation = GeoPoint(location.latitude, location.longitude)
                    updateCurrentUserMarker()
                    updateDistance()
                    saveCurrentLocationToDatabase(location.latitude, location.longitude)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun saveCurrentLocationToDatabase(latitude: Double, longitude: Double) {
        auth.currentUser?.uid?.let { currentUserId ->
            database.getReference("users").child(currentUserId).child("location").setValue(
                mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude
                )
            )
        }
    }

    private fun startTrackingUser() {
        database.getReference("users").child(userId).child("location")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val lat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                    val lng = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0

                    if (lat != 0.0 && lng != 0.0) {
                        trackedUserLocation = GeoPoint(lat, lng)
                        updateTrackedUserMarker()
                        updateDistance()
                        centerMapBetweenLocations()
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Toast.makeText(this@DistanciaUsuarioActivity,
                        "Error al rastrear usuario: ${error.message}",
                        Toast.LENGTH_SHORT).show()
                }
            })
    }

    private fun centerMapBetweenLocations() {
        currentLocation?.let { current ->
            trackedUserLocation?.let { tracked ->
                val centerLat = (current.latitude + tracked.latitude) / 2
                val centerLon = (current.longitude + tracked.longitude) / 2
                map.controller.setCenter(GeoPoint(centerLat, centerLon))

                // Ajustar zoom para que ambos marcadores sean visibles
                val zoomLevel = calculateZoomLevel(current, tracked)
                map.controller.setZoom(zoomLevel)
            }
        }
    }

    private fun calculateZoomLevel(point1: GeoPoint, point2: GeoPoint): Double {
        val distance = calculateDistance(point1, point2)
        return when {
            distance > 10000 -> 10.0
            distance > 5000 -> 11.0
            distance > 2000 -> 12.0
            distance > 1000 -> 13.0
            distance > 500 -> 14.0
            distance > 200 -> 15.0
            distance > 100 -> 16.0
            else -> 17.0
        }
    }

    private fun updateCurrentUserMarker() {
        currentLocation?.let { location ->
            if (currentUserMarker == null) {
                currentUserMarker = Marker(map).apply {
                    position = location
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Mi ubicación"

                    // Configurar icono con tamaño adecuado
                    ContextCompat.getDrawable(this@DistanciaUsuarioActivity, R.drawable.ic_blue_marker)?.let { icon ->
                        icon.setBounds(0, 0, 80, 80)
                        setIcon(icon)
                    }
                }
                map.overlays.add(currentUserMarker)
            } else {
                currentUserMarker?.position = location
            }
            map.invalidate()
        }
    }

    private fun updateTrackedUserMarker() {
        trackedUserLocation?.let { location ->
            if (trackedUserMarker == null) {
                trackedUserMarker = Marker(map).apply {
                    position = location
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Usuario seguido"

                    // Configurar icono con tamaño adecuado
                    ContextCompat.getDrawable(this@DistanciaUsuarioActivity, R.drawable.ic_red_marker)?.let { icon ->
                        icon.setBounds(0, 0, 80, 80)
                        setIcon(icon)
                    }
                }
                map.overlays.add(trackedUserMarker)
            } else {
                trackedUserMarker?.position = location
            }
            map.invalidate()
        }
    }

    private fun updateDistance() {
        if (currentLocation != null && trackedUserLocation != null) {
            val distance = calculateDistance(currentLocation!!, trackedUserLocation!!)
            tvDistancia.text = "Distancia: %.2f metros".format(distance)
        }
    }

    private fun calculateDistance(point1: GeoPoint, point2: GeoPoint): Float {
        val results = FloatArray(1)
        Location.distanceBetween(
            point1.latitude, point1.longitude,
            point2.latitude, point2.longitude,
            results
        )
        return results[0]
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    startLocationUpdates()
                    startTrackingUser()
                } else {
                    Toast.makeText(this, "Se requieren permisos de ubicación", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        Configuration.getInstance().load(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        Configuration.getInstance().save(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}