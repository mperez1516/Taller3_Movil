package com.example.taller3

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.ktx.Firebase

class ListaUsuariosActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var usersListView: ListView
    private val database = FirebaseDatabase.getInstance()
    private val usersList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lista_usuarios)

        auth = Firebase.auth

        // Configurar Toolbar
        val toolbar: Toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.title = "Usuarios activos"

        // Inicializar ListView
        usersListView = findViewById(R.id.usersList)

        // Cargar usuarios desde Firebase
        loadActiveUsers()
    }

    private fun loadActiveUsers() {
        val usersRef = database.getReference("users")

        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                usersList.clear()
                for (userSnapshot in snapshot.children) {
                    val email = userSnapshot.child("email").getValue(String::class.java)
                    val status = userSnapshot.child("status").getValue(String::class.java)

                    // Solo mostrar usuarios activos que no sean el usuario actual
                    if (status == "available" && email != auth.currentUser?.email) {
                        email?.let { usersList.add(it) }
                    }
                }

                // Configurar el adaptador para el ListView
                val adapter = ArrayAdapter(
                    this@ListaUsuariosActivity,
                    android.R.layout.simple_list_item_1,
                    usersList
                )
                usersListView.adapter = adapter

                // Configurar click listener para los items de la lista
                usersListView.setOnItemClickListener { _, _, position, _ ->
                    val selectedUserEmail = usersList[position]
                    // Aquí puedes implementar la lógica para mostrar la ubicación del usuario seleccionado
                    Toast.makeText(
                        this@ListaUsuariosActivity,
                        "Mostrar ubicación de $selectedUserEmail",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Ejemplo: iniciar actividad de mapa con el usuario seleccionado
                    val intent = Intent(this@ListaUsuariosActivity, OSMMapsActivity::class.java).apply {
                        putExtra("selected_user_email", selectedUserEmail)
                    }
                    startActivity(intent)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(
                    this@ListaUsuariosActivity,
                    "Error al cargar usuarios: ${error.message}",
                    Toast.LENGTH_SHORT
                ).show()
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.baseline_menu_24, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_logout -> {
                auth.signOut()
                val intent = Intent(this, MainActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                startActivity(intent)
                finish()
                true
            }
            R.id.menu_usuarios -> {
                // Ya estamos en esta actividad, no necesitamos hacer nada
                true
            }
            R.id.menu_available -> {
                setAvailable()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setAvailable() {
        val currentUser = auth.currentUser
        currentUser?.let { user ->
            val userRef = database.getReference("users").child(user.uid)
            userRef.child("status").setValue("available")
                .addOnSuccessListener {
                    Toast.makeText(
                        this,
                        "Estado cambiado a disponible",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(
                        this,
                        "Error al cambiar estado: ${e.message}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
}