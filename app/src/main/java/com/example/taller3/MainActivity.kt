package com.example.taller3

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.ktx.firestoreSettings

class MainActivity : AppCompatActivity() {

    private val db by lazy {
        Firebase.firestore.apply {
            firestoreSettings = firestoreSettings {
                isPersistenceEnabled = false // Para pruebas iniciales
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sendTestMessage()
    }

    private fun sendTestMessage() {
        try {
            db.collection("connection_test")
                .document("test_doc")
                .set(mapOf(
                    "status" to "attempt",
                    "timestamp" to System.currentTimeMillis()
                ))
                .addOnSuccessListener {
                    Log.d("FirestoreTest", "✅ Comprobación exitosa")
                }
                .addOnFailureListener { e ->
                    Log.e("FirestoreTest", "❌ Error de conexión", e)
                }
        } catch (e: Exception) {
            Log.e("FirestoreTest", "🔥 Error inesperado", e)
        }
    }
}