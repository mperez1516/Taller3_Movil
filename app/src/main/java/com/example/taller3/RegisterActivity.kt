package com.example.taller3

import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.example.taller3.databinding.ActivityRegisterBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.ktx.userProfileChangeRequest
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

class RegisterActivity : AppCompatActivity() {

    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private var currentLocation: Location? = null

    companion object {
        private const val TAG = "RegisterActivity"
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth and Database
        auth = Firebase.auth
        database = Firebase.database.reference

        // Initialize location services
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configurar listeners
        setupListeners()
        setupLocationUpdates()
    }

    private fun setupListeners() {
        binding.photoButton.setOnClickListener {
            // TODO: Implementar cámara/galería en el futuro
            Toast.makeText(this, "Función de cámara por implementar", Toast.LENGTH_SHORT).show()
        }

        binding.registerButton.setOnClickListener {
            registerUser()
        }
    }

    private fun setupLocationUpdates() {
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                currentLocation = locationResult.lastLocation
                Log.d(TAG, "Location updated: ${currentLocation?.latitude}, ${currentLocation?.longitude}")
            }
        }

        if (checkLocationPermissions()) {
            startLocationUpdates()
        } else {
            requestLocationPermissions()
        }
    }

    private fun checkLocationPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
            this,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                android.Manifest.permission.ACCESS_FINE_LOCATION,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        if (checkLocationPermissions()) {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper()
            )
        }
    }

    private fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
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
                } else {
                    Toast.makeText(
                        this,
                        "Los permisos de ubicación son necesarios para el registro",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    private fun registerUser() {
        val name = binding.nameField.text.toString().trim()
        val lastName = binding.lastNameField.text.toString().trim()
        val email = binding.emailField.text.toString().trim()
        val password = binding.passwordField.text.toString().trim()
        val idNumber = binding.idNumberField.text.toString().trim()

        if (validateForm(name, lastName, email, password, idNumber)) {
            if (currentLocation == null) {
                Toast.makeText(
                    this,
                    "Obteniendo ubicación, por favor espere...",
                    Toast.LENGTH_SHORT
                ).show()
                return
            }

            // Crear usuario en Firebase Auth
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        // Registro exitoso
                        Log.d(TAG, "createUserWithEmail:success")
                        val user = auth.currentUser

                        // Actualizar perfil con nombre completo
                        val profileUpdates = userProfileChangeRequest {
                            displayName = "$name $lastName"
                            // photoUri se establecerá más adelante cuando implementemos las fotos
                        }

                        user?.updateProfile(profileUpdates)?.addOnCompleteListener { profileTask ->
                            if (profileTask.isSuccessful) {
                                Log.d(TAG, "User profile updated.")
                            }
                        }

                        // Guardar información adicional en Realtime Database
                        saveUserToDatabase(user?.uid, name, lastName, email, idNumber)

                        // Redirigir a OSMMapsActivity
                        val intent = Intent(this, OSMMapsActivity::class.java).apply {
                            putExtra("user_email", email)
                            putExtra("user_name", "$name $lastName")
                            putExtra("latitude", currentLocation?.latitude)
                            putExtra("longitude", currentLocation?.longitude)
                        }
                        startActivity(intent)
                        finish()
                    } else {
                        // Error en registro
                        Log.w(TAG, "createUserWithEmail:failure", task.exception)
                        Toast.makeText(
                            baseContext, "Error en el registro: ${task.exception?.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
        }
    }

    private fun saveUserToDatabase(
        userId: String?,
        name: String,
        lastName: String,
        email: String,
        idNumber: String
    ) {
        if (userId == null) return

        val user = hashMapOf(
            "name" to name,
            "lastName" to lastName,
            "email" to email,
            "idNumber" to idNumber,
            "latitude" to currentLocation?.latitude,
            "longitude" to currentLocation?.longitude,
            "profileImageUrl" to "" // Se actualizará cuando implementemos Storage
        )

        database.child("users").child(userId).setValue(user)
            .addOnSuccessListener {
                Log.d(TAG, "User data saved to database")
            }
            .addOnFailureListener { e ->
                Log.w(TAG, "Error saving user data", e)
            }
    }

    private fun validateForm(
        name: String,
        lastName: String,
        email: String,
        password: String,
        idNumber: String
    ): Boolean {
        var valid = true

        if (name.isEmpty()) {
            binding.nameField.error = "Ingrese su nombre"
            valid = false
        } else {
            binding.nameField.error = null
        }

        if (lastName.isEmpty()) {
            binding.lastNameField.error = "Ingrese su apellido"
            valid = false
        } else {
            binding.lastNameField.error = null
        }

        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            binding.emailField.error = "Ingrese un email válido"
            valid = false
        } else {
            binding.emailField.error = null
        }

        if (password.isEmpty() || password.length < 6) {
            binding.passwordField.error = "La contraseña debe tener al menos 6 caracteres"
            valid = false
        } else {
            binding.passwordField.error = null
        }

        if (idNumber.isEmpty()) {
            binding.idNumberField.error = "Ingrese su número de identificación"
            valid = false
        } else {
            binding.idNumberField.error = null
        }

        return valid
    }

    override fun onPause() {
        super.onPause()
        stopLocationUpdates()
    }

    override fun onResume() {
        super.onResume()
        if (checkLocationPermissions()) {
            startLocationUpdates()
        }
    }
}