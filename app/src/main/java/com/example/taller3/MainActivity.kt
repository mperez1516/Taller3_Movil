package com.example.taller3

import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.Firebase
import com.google.firebase.database.database
import com.google.firebase.firestore.firestore
import com.google.firebase.firestore.ktx.firestoreSettings

class MainActivity : AppCompatActivity() {

    private val database by lazy { Firebase.database }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sendTestMessage()
    }

    private fun sendTestMessage() {
        val reference = database.getReference("connection_test")

        val testData = mapOf(
            "status" to "attempt",
            "timestamp" to System.currentTimeMillis()
        )

        reference.setValue(testData)
            .addOnSuccessListener {
                Log.d("RealtimeDB", "✅ Datos enviados correctamente")
            }
            .addOnFailureListener { e ->
                Log.e("RealtimeDB", "❌ Error al enviar datos", e)
            }
    }
}