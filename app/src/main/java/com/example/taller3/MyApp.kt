package com.example.taller3

import android.app.Application
import android.util.Log

class MyApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d("MyApp", "Aplicación inicializada")

        // Iniciar el servicio de monitoreo de usuarios disponibles
        UsuariosDisponiblesService.startService(this)
    }

    override fun onTerminate() {
        // Opcional: Detener el servicio cuando la aplicación termina
        UsuariosDisponiblesService.stopService(this)
        super.onTerminate()
    }
}