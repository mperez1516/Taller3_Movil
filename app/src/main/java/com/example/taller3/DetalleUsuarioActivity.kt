package com.example.taller3

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

import java.net.URL
import kotlin.concurrent.thread

class DetalleUsuarioActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var userId: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalle_usuario)

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance()

        userId = intent.getStringExtra("userId") ?: run {
            finish()
            return
        }

        val btnVerPosicion: Button = findViewById(R.id.btnVerPosicion)
        btnVerPosicion.setOnClickListener {
            val intent = Intent(this, DistanciaUsuarioActivity::class.java).apply {
                putExtra("userId", userId)
            }
            startActivity(intent)
        }

        cargarDetallesUsuario()
    }

    private fun cargarDetallesUsuario() {
        val userRef = database.getReference("users").child(userId)

        userRef.addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val nombre = snapshot.child("name").getValue(String::class.java) ?: "Sin nombre"
                val email = snapshot.child("email").getValue(String::class.java) ?: ""
                val imageUrl = snapshot.child("imageUrl").getValue(String::class.java) ?: ""

                findViewById<TextView>(R.id.tvNombre).text = nombre
                findViewById<TextView>(R.id.tvEmail).text = email
                val imageView = findViewById<ImageView>(R.id.ivUsuario)

                if (imageUrl.isNotEmpty()) {
                    thread {
                        try {
                            val input = URL(imageUrl).openStream()
                            val bitmap = BitmapFactory.decodeStream(input)
                            runOnUiThread {
                                imageView.setImageBitmap(bitmap)
                            }
                        } catch (e: Exception) {
                            runOnUiThread {
                                Toast.makeText(this@DetalleUsuarioActivity, "Error al cargar imagen", Toast.LENGTH_SHORT).show()
                                imageView.setImageResource(R.drawable.ic_default_user)
                            }
                        }
                    }
                } else {
                    imageView.setImageResource(R.drawable.ic_default_user)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@DetalleUsuarioActivity, "Error al cargar datos", Toast.LENGTH_SHORT).show()
            }
        })
    }
}
