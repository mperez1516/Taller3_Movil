package com.example.taller3

import android.app.Application
import android.util.Log
import com.google.firebase.database.FirebaseDatabase

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        try {
            // Inicialización segura de Firebase
            FirebaseDatabase.getInstance().setPersistenceEnabled(true)
            Log.d("MyApp", "Firebase inicializado correctamente")

            // No iniciamos el servicio directamente aquí para evitar problemas
        } catch (e: Exception) {
            Log.e("MyApp", "Error al inicializar Firebase: ${e.message}")
        }
    }
}