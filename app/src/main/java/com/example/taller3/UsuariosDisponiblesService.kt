package com.example.taller3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import java.util.concurrent.Executors

class UsuariosDisponiblesService : Service() {

    private lateinit var usuariosRef: DatabaseReference
    private var usuariosListener: ValueEventListener? = null
    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate() {
        super.onCreate()
        try {
            val database = FirebaseDatabase.getInstance()
            database.setPersistenceEnabled(true)
            usuariosRef = database.getReference("users")

            createNotificationChannel()
            startForegroundService()
            setupUserListener()

            Log.d("UsuariosService", "Servicio creado correctamente")
        } catch (e: Exception) {
            Log.e("UsuariosService", "Error en onCreate: ${e.message}")
            stopSelf()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "usuarios_channel",
                "Usuarios Disponibles",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifica usuarios disponibles"
            }

            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, "usuarios_channel")
            .setContentTitle("Monitor de Usuarios")
            .setContentText("Escuchando cambios")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        startForeground(1, notification)
    }

    private fun setupUserListener() {
        usuariosListener = usuariosRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                executor.execute {
                    try {
                        snapshot.children.forEach { userSnapshot ->
                            val disponible = userSnapshot.child("available").getValue(Boolean::class.java) ?: false
                            val nombre = userSnapshot.child("name").getValue(String::class.java)

                            if (disponible && nombre != null) {
                                showNotification(nombre)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("UsuariosService", "Error en onDataChange: ${e.message}")
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("UsuariosService", "Error en Firebase: ${error.message}")
            }
        })
    }

    private fun showNotification(userName: String) {
        Handler(Looper.getMainLooper()).post {
            try {
                Toast.makeText(
                    applicationContext,
                    "$userName está disponible",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e("UsuariosService", "Error al mostrar Toast: ${e.message}")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        try {
            usuariosListener?.let { usuariosRef.removeEventListener(it) }
            executor.shutdown()
            Log.d("UsuariosService", "Servicio destruido correctamente")
        } catch (e: Exception) {
            Log.e("UsuariosService", "Error en onDestroy: ${e.message}")
        }
    }

    companion object {
        fun start(context: Context) {
            try {
                val intent = Intent(context, UsuariosDisponiblesService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("UsuariosService", "Error al iniciar servicio: ${e.message}")
            }
        }
    }
}