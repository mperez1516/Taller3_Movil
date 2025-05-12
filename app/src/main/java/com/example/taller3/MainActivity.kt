package com.example.taller3

import android.content.Intent
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.taller3.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.ktx.Firebase

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private val TAG = "MainActivity"
    private val database = FirebaseDatabase.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase Auth
        auth = Firebase.auth

        sendTestMessage()

        binding.loginBtn.setOnClickListener {
            val email = binding.emailField.text.toString().trim()
            val password = binding.passwordField.text.toString().trim()

            if (validateForm(email, password)) {
                signInUser(email, password)
            }
        }

        binding.registerBtn.setOnClickListener {
            // Navegación a la pantalla de registro
            val intent = Intent(this, RegisterActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onStart() {
        super.onStart()
        // Verificar si el usuario ya está autenticado
        val currentUser = auth.currentUser
        if (currentUser != null) {
            updateUI(currentUser)
        }
    }

    private fun validateForm(email: String, password: String): Boolean {
        var valid = true

        when {
            TextUtils.isEmpty(email) -> {
                binding.emailField.error = "Ingresa tu correo"
                valid = false
            }
            !isEmailValid(email) -> {
                binding.emailField.error = "Correo no válido"
                valid = false
            }
            else -> {
                binding.emailField.error = null
            }
        }

        when {
            TextUtils.isEmpty(password) -> {
                binding.passwordField.error = "Ingresa tu contraseña"
                valid = false
            }
            password.length < 6 -> {
                binding.passwordField.error = "Mínimo 6 caracteres"
                valid = false
            }
            else -> {
                binding.passwordField.error = null
            }
        }

        return valid
    }

    private fun isEmailValid(email: String): Boolean {
        return android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }

    private fun signInUser(email: String, password: String) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    // Autenticación exitosa
                    Log.d(TAG, "signInWithEmail:success")
                    val user = auth.currentUser
                    updateUI(user)
                } else {
                    // Autenticación fallida
                    Log.w(TAG, "signInWithEmail:failure", task.exception)
                    Toast.makeText(
                        this, "Error: Credenciales incorrectas",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateUI(null)
                }
            }
    }

    private fun updateUI(user: Any?) {
        if (user != null) {
            // Redirigir a OSMMapsActivity para cualquier usuario autenticado
            val intent = Intent(this, OSMMapsActivity::class.java)
            intent.putExtra("user_email", (user as com.google.firebase.auth.FirebaseUser).email)
            startActivity(intent)
            finish()
        } else {
            // Limpiar campo de contraseña si la autenticación falla
            binding.passwordField.setText("")
        }
    }

    private fun sendTestMessage() {
        val reference = database.getReference("connection_test")

        val testData = mapOf(
            "status" to "connected",
            "timestamp" to System.currentTimeMillis()
        )

        reference.setValue(testData)
            .addOnSuccessListener {
                Log.d(TAG, "Datos enviados correctamente a Firebase")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error al enviar datos a Firebase", e)
            }
    }
}