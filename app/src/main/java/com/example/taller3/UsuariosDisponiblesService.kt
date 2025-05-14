package com.example.taller3

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.firebase.database.*

class UsuariosDisponiblesService : Service() {

    private lateinit var database: FirebaseDatabase
    private lateinit var usuariosRef: DatabaseReference
    private var usuariosListener: ValueEventListener? = null
    private val notifiedUsers = mutableSetOf<String>()
    private val CHANNEL_ID = "UsuariosDisponiblesChannel"

    override fun onCreate() {
        super.onCreate()
        database = FirebaseDatabase.getInstance()
        usuariosRef = database.getReference("users")
        createNotificationChannel()
        startForegroundService()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Notificaciones de Usuarios Disponibles",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundService() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Monitor de Usuarios")
            .setContentText("Escuchando cambios en disponibilidad")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(1, notification)
        }

        startListeningForUserChanges()
    }

    private fun startListeningForUserChanges() {
        usuariosListener = usuariosRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (userSnapshot in snapshot.children) {
                    val userId = userSnapshot.key ?: continue
                    val estado = userSnapshot.child("available").getValue(Boolean::class.java) ?: false
                    val nombre = userSnapshot.child("name").getValue(String::class.java) ?: "Usuario desconocido"

                    if (estado && !notifiedUsers.contains(userId)) {
                        showUserAvailableToast(nombre)
                        notifiedUsers.add(userId)
                    } else if (!estado) {
                        notifiedUsers.remove(userId)
                    }
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@UsuariosDisponiblesService,
                    "Error al escuchar cambios de usuarios",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    private fun showUserAvailableToast(userName: String) {
        Handler(mainLooper).post {
            Toast.makeText(
                applicationContext,
                "$userName está ahora disponible",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        usuariosListener?.let {
            usuariosRef.removeEventListener(it)
        }
    }

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, UsuariosDisponiblesService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, UsuariosDisponiblesService::class.java)
            context.stopService(intent)
        }
    }
}