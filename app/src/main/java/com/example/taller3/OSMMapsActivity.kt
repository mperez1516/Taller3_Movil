package com.example.taller3

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.IOException

class OSMMapsActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        private const val DEFAULT_ZOOM_LEVEL = 15.0
        private const val HOSPITAL_LAT = 4.628308  // Coordenadas del Hospital San Ignacio
        private const val HOSPITAL_LON = -74.064929
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_osmmaps)

        // Configuración inicial de OSMDroid
        Configuration.getInstance().load(this, getSharedPreferences("osm_prefs", MODE_PRIVATE))
        Configuration.getInstance().userAgentValue = packageName

        map = findViewById(R.id.osmMap)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        // Inicializar servicios de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Cargar puntos de interés desde JSON
        loadPointsOfInterest()

        // Mostrar ubicación del Hospital San Ignacio
        showHospitalLocation()

        // Solicitar permisos de ubicación
        requestLocationPermission()
    }

    private fun requestLocationPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permiso ya concedido, iniciar actualizaciones de ubicación
                startLocationUpdates()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) -> {
                // Explicar al usuario por qué necesitamos el permiso
                Toast.makeText(
                    this,
                    "Los permisos de ubicación son necesarios para mostrar tu posición",
                    Toast.LENGTH_LONG
                ).show()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
            else -> {
                // Solicitar permiso directamente
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                    LOCATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                // Aquí puedes manejar las actualizaciones de ubicación si lo necesitas
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            LOCATION_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permiso concedido, iniciar actualizaciones de ubicación
                    startLocationUpdates()
                } else {
                    // Permiso denegado, mostrar mensaje
                    Toast.makeText(
                        this,
                        "La funcionalidad de ubicación está limitada sin permisos",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun loadPointsOfInterest() {
        try {
            val jsonString = assets.open("locations.json").bufferedReader().use { it.readText() }
            val jsonObject = JSONObject(jsonString)
            val locationsArray = jsonObject.getJSONArray("locationsArray")

            for (i in 0 until locationsArray.length()) {
                val location = locationsArray.getJSONObject(i)
                val lat = location.getDouble("latitude")
                val lon = location.getDouble("longitude")
                val name = location.getString("name")

                addMarkerToMap(lat, lon, name, R.drawable.ic_red_marker)
            }
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this, "Error cargando los puntos de interés", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Error procesando los datos", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showHospitalLocation() {
        // Añadir marcador del hospital
        addMarkerToMap(
            HOSPITAL_LAT,
            HOSPITAL_LON,
            "Hospital San Ignacio (Mi ubicación)",
            R.drawable.ic_blue_marker
        )

        // Centrar mapa en el hospital
        map.controller.setCenter(GeoPoint(HOSPITAL_LAT, HOSPITAL_LON))
        map.controller.setZoom(DEFAULT_ZOOM_LEVEL)
        map.invalidate()
    }

    private fun addMarkerToMap(latitude: Double, longitude: Double, title: String, iconResId: Int) {
        val marker = Marker(map)
        marker.position = GeoPoint(latitude, longitude)
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)

        // Convertir el recurso drawable a Bitmap y escalarlo
        val bitmap = BitmapFactory.decodeResource(resources, iconResId)
        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, 100, 100, false)
        marker.icon = BitmapDrawable(resources, scaledBitmap)

        marker.title = title
        map.overlays.add(marker)
    }

    override fun onResume() {
        super.onResume()
        map.onResume()
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            startLocationUpdates()
        }
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
        try {
            fusedLocationClient.removeLocationUpdates(locationCallback)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}