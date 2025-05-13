package com.example.taller3

import android.provider.ContactsContract.CommonDataKinds.Email

data class User(
    var userId: String = "",
    var nombre: String = "",
    var apellido: String = "",
    var identificacion: String = "",
    val status: String,
    var latitud: Double = 0.0,
    var longitud: Double = 0.0

)