package com.example.taller3

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import java.util.Random
import java.io.File

class DistanciaUsuarioActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private lateinit var database: FirebaseDatabase
    private lateinit var auth: FirebaseAuth
    private lateinit var tvDistancia: TextView
    private lateinit var tvNombreUsuario: TextView

    private var currentLocation: GeoPoint? = null
    private var trackedUserLocation: GeoPoint? = null
    private var trackedUserMarker: Marker? = null
    private var currentUserMarker: Marker? = null
    private lateinit var userIdToTrack: String
    private var nombreUsuarioSeguido: String = ""
    private val random = Random()

    private val locationPermissionRequest = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        when {
            permissions.getOrDefault(Manifest.permission.ACCESS_FINE_LOCATION, false) -> {
                if (isLocationEnabled()) {
                    startLocationUpdates()
                    startTrackingUser()
                } else {
                    showLocationSettingsPrompt()
                }
            }
            permissions.getOrDefault(Manifest.permission.ACCESS_COARSE_LOCATION, false) -> {
                if (isLocationEnabled()) {
                    startLocationUpdates()
                    startTrackingUser()
                } else {
                    showLocationSettingsPrompt()
                }
            }
            else -> {
                Toast.makeText(this, "Permisos de ubicación requeridos", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    companion object {
        private const val TAG = "DistanciaUsuarioAct"
        private const val DEFAULT_ZOOM_LEVEL = 15.0
        private const val MARKER_ICON_SIZE = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Configuración OSMDroid
        Configuration.getInstance().apply {
            userAgentValue = packageName
            osmdroidBasePath = File(getExternalFilesDir(null), "osmdroid")
            osmdroidTileCache = File(osmdroidBasePath, "tiles")
            load(this@DistanciaUsuarioActivity, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        }

        setContentView(R.layout.activity_distancia_usuario)

        // Inicializar vistas
        tvDistancia = findViewById(R.id.tvDistancia)
        tvNombreUsuario = findViewById(R.id.tvNombreUsuario)
        map = findViewById(R.id.osmMap)

        // Inicializar Firebase
        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Obtener ID del usuario a rastrear
        userIdToTrack = intent.getStringExtra("userId") ?: run {
            Toast.makeText(this, "ID de usuario no válido", Toast.LENGTH_LONG).show()
            finish()
            return
        }

        initializeMap()
        checkLocationPermission()
        loadUserInfo()
    }

    private fun initializeMap() {
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)
        map.controller.setZoom(DEFAULT_ZOOM_LEVEL)

        // Configurar overlay de escala
        map.overlays.add(ScaleBarOverlay(map).apply {
            setCentred(true)
            setScaleBarOffset(resources.displayMetrics.widthPixels / 2, 10)
        })
    }

    private fun checkLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                if (isLocationEnabled()) {
                    startLocationUpdates()
                    startTrackingUser()
                } else {
                    showLocationSettingsPrompt()
                }
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                showPermissionRationale()
            }
            else -> {
                requestLocationPermission()
            }
        }
    }

    private fun showPermissionRationale() {
        AlertDialog.Builder(this)
            .setTitle("Permiso de ubicación necesario")
            .setMessage("Esta aplicación necesita acceso a tu ubicación para mostrar la distancia entre usuarios.")
            .setPositiveButton("OK") { _, _ ->
                requestLocationPermission()
            }
            .setNegativeButton("Cancelar") { _, _ ->
                Toast.makeText(this, "La funcionalidad de ubicación estará limitada", Toast.LENGTH_SHORT).show()
            }
            .create()
            .show()
    }

    private fun requestLocationPermission() {
        locationPermissionRequest.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    private fun showLocationSettingsPrompt() {
        AlertDialog.Builder(this)
            .setTitle("Servicios de ubicación desactivados")
            .setMessage("Por favor active los servicios de ubicación para usar esta función")
            .setPositiveButton("Configuración") { _, _ ->
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
            .setNegativeButton("Cancelar") { _, _ ->
                Toast.makeText(this, "No se puede mostrar la ubicación sin los servicios activados", Toast.LENGTH_LONG).show()
            }
            .create()
            .show()
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 5000
            fastestInterval = 3000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        // Obtener última ubicación conocida primero
        fusedLocationClient.lastLocation.addOnSuccessListener { lastLocation ->
            lastLocation?.let {
                currentLocation = GeoPoint(it.latitude, it.longitude)
                updateCurrentUserMarker()
                saveCurrentLocationToDatabase(it.latitude, it.longitude)
                updateDistanceAndCenterMap()
            } ?: run {
                Log.w(TAG, "No se pudo obtener la última ubicación conocida")
            }
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    currentLocation = GeoPoint(location.latitude, location.longitude)
                    updateCurrentUserMarker()
                    saveCurrentLocationToDatabase(location.latitude, location.longitude)
                    updateDistanceAndCenterMap()
                } ?: run {
                    Log.w(TAG, "Location result is null")
                }
            }

            override fun onLocationAvailability(availability: LocationAvailability) {
                if (!availability.isLocationAvailable) {
                    Log.w(TAG, "Location services are not available")
                    runOnUiThread {
                        Toast.makeText(
                            this@DistanciaUsuarioActivity,
                            "Ubicación no disponible. Active GPS o conexión de red",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }
        }

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error al solicitar actualizaciones de ubicación: ${e.message}")
        }
    }

    private fun saveCurrentLocationToDatabase(latitude: Double, longitude: Double) {
        auth.currentUser?.uid?.let { currentUserId ->
            database.getReference("users").child(currentUserId).child("location").setValue(
                mapOf(
                    "latitude" to latitude,
                    "longitude" to longitude
                )
            ).addOnFailureListener { e ->
                Log.e(TAG, "Error al guardar ubicación: ${e.message}")
            }
        }
    }

    private fun startTrackingUser() {
        database.getReference("users").child(userIdToTrack).child("location")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    try {
                        val latitude = snapshot.child("latitude").getValue(Double::class.java)
                        val longitude = snapshot.child("longitude").getValue(Double::class.java)

                        if (latitude == null || longitude == null) {
                            showLocationError("El usuario no ha compartido su ubicación")
                            return
                        }

                        trackedUserLocation = GeoPoint(latitude, longitude)
                        updateTrackedUserMarker()
                        updateDistanceAndCenterMap()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error al procesar ubicación: ${e.message}")
                        showLocationError("Error al leer ubicación")
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, "Error al rastrear usuario: ${error.message}")
                    showLocationError("Error de conexión con Firebase")
                }
            })
    }

    private fun showLocationError(message: String) {
        runOnUiThread {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            tvDistancia.text = message
            trackedUserLocation = null
            updateTrackedUserMarker()
        }
    }

    private fun updateDistanceAndCenterMap() {
        if (currentLocation != null && trackedUserLocation != null) {
            updateDistance()
            centerMapBetweenLocations()
        } else if (currentLocation != null) {
            map.controller.setCenter(currentLocation)
        }
    }

    private fun centerMapBetweenLocations() {
        currentLocation?.let { current ->
            trackedUserLocation?.let { tracked ->
                val centerLat = (current.latitude + tracked.latitude) / 2
                val centerLon = (current.longitude + tracked.longitude) / 2
                map.controller.setCenter(GeoPoint(centerLat, centerLon))

                val distance = calculateDistance(current, tracked)
                val zoomLevel = when {
                    distance > 5000 -> 12.0
                    distance > 1000 -> 14.0
                    else -> 16.0
                }
                map.controller.setZoom(zoomLevel)
            }
        }
    }

    private fun updateCurrentUserMarker() {
        currentLocation?.let { location ->
            if (currentUserMarker == null) {
                currentUserMarker = addMarkerToMap(
                    location.latitude,
                    location.longitude,
                    "Mi ubicación",
                    R.drawable.ic_blue_marker
                ).apply {
                    setOnMarkerClickListener { marker, _ ->
                        showRandomDistanceToast(marker)
                        true
                    }
                }
            } else {
                currentUserMarker?.position = location
                map.invalidate()
            }
        }
    }

    private fun updateTrackedUserMarker() {
        trackedUserLocation?.let { location ->
            if (trackedUserMarker == null) {
                trackedUserMarker = addMarkerToMap(
                    location.latitude,
                    location.longitude,
                    nombreUsuarioSeguido,
                    R.drawable.ic_red_marker
                ).apply {
                    setOnMarkerClickListener { marker, _ ->
                        showRandomDistanceToast(marker)
                        true
                    }
                }
            } else {
                trackedUserMarker?.position = location
                trackedUserMarker?.title = nombreUsuarioSeguido
                map.invalidate()
            }
        } ?: run {
            trackedUserMarker?.let {
                map.overlays.remove(it)
                trackedUserMarker = null
                map.invalidate()
            }
        }
    }

    private fun showRandomDistanceToast(marker: Marker) {
        val randomDistance = 1 + random.nextInt(1000) // Número aleatorio entre 1 y 1000 metros
        val message = when {
            marker == currentUserMarker -> "Estás a $randomDistance metros de este punto"
            marker == trackedUserMarker -> "$nombreUsuarioSeguido está a $randomDistance metros de ti"
            else -> "Distancia: $randomDistance metros"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun addMarkerToMap(latitude: Double, longitude: Double, title: String, iconResId: Int): Marker {
        val marker = Marker(map).apply {
            position = GeoPoint(latitude, longitude)
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

            // Escalar el icono del marcador
            ContextCompat.getDrawable(this@DistanciaUsuarioActivity, iconResId)?.let { drawable ->
                val bitmap = drawable.toBitmap(MARKER_ICON_SIZE, MARKER_ICON_SIZE, Bitmap.Config.ARGB_8888)
                icon = BitmapDrawable(resources, bitmap)
            }
        }
        map.overlays.add(marker)
        map.invalidate()
        return marker
    }

    private fun Drawable.toBitmap(width: Int, height: Int, config: Bitmap.Config): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, config)
        val canvas = Canvas(bitmap)
        setBounds(0, 0, canvas.width, canvas.height)
        draw(canvas)
        return bitmap
    }

    private fun updateDistance() {
        if (currentLocation != null && trackedUserLocation != null) {
            val distance = calculateDistance(currentLocation!!, trackedUserLocation!!)
            tvDistancia.text = "Distancia: %.2f metros".format(distance)
        } else {
            tvDistancia.text = "Calculando distancia..."
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

    private fun loadUserInfo() {
        database.getReference("users").child(userIdToTrack)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    nombreUsuarioSeguido = snapshot.child("name").getValue(String::class.java) ?: "Usuario"
                    tvNombreUsuario.text = "Siguiendo a: $nombreUsuarioSeguido"
                    trackedUserMarker?.title = nombreUsuarioSeguido
                    map.invalidate()
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.w(TAG, "Error al cargar información del usuario", error.toException())
                    tvNombreUsuario.text = "Siguiendo a: Usuario desconocido"
                }
            })
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        Configuration.getInstance().load(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))

        // Verificar si los permisos y servicios de ubicación están activos al volver
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED && isLocationEnabled()
        ) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        Configuration.getInstance().save(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            Log.e(TAG, "Error al eliminar actualizaciones de ubicación: ${e.message}")
        }
    }
}