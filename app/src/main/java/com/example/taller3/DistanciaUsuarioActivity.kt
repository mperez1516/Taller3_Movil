package com.example.taller3

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
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
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.gestures.RotationGestureOverlay

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName
        setContentView(R.layout.activity_distancia_usuario) // Usa el layout adecuado con <org.osmdroid.views.MapView>

        tvDistancia = findViewById(R.id.tvDistancia)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        userId = intent.getStringExtra("userId") ?: run {
            finish()
            return
        }

        map = findViewById(R.id.osmMap)
        map.setMultiTouchControls(true)
        map.controller.setZoom(15.0)
        map.setTileSource(org.osmdroid.tileprovider.tilesource.TileSourceFactory.MAPNIK)

        requestLocationPermission()
        startTrackingUser()
    }

    private fun requestLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                1
            )
        } else {
            startLocationUpdates()
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
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun startTrackingUser() {
        val userRef = database.getReference("users").child(userId).child("location")

        userRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("latitude").getValue(Double::class.java) ?: 0.0
                val lng = snapshot.child("longitude").getValue(Double::class.java) ?: 0.0
                trackedUserLocation = GeoPoint(lat, lng)
                updateTrackedUserMarker()
                updateDistance()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@DistanciaUsuarioActivity, "Error al rastrear usuario", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun updateCurrentUserMarker() {
        currentLocation?.let { location ->
            if (currentUserMarker == null) {
                currentUserMarker = Marker(map).apply {
                    position = location
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "Mi ubicación"
                    icon = ContextCompat.getDrawable(this@DistanciaUsuarioActivity, R.drawable.ic_blue_marker)
                }
                map.overlays.add(currentUserMarker)
            } else {
                currentUserMarker?.position = location
            }
            map.controller.setCenter(location)
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
                    icon = ContextCompat.getDrawable(this@DistanciaUsuarioActivity, R.drawable.ic_red_marker)
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
        requestCode: Int, permissions: Array<String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1 && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startLocationUpdates()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}
