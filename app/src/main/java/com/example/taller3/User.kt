package com.example.taller3

import android.provider.ContactsContract.CommonDataKinds.Email

data class User(
    var userId: String = "",
    var nombre: String = "",
    var apellido: String = "",
    var identificacion: String = "",
    val status: String,
    var email: String="",
    var latitude: Double,
    var longitude: Double,
    val imageUrl: String

)