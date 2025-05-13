package com.example.taller3

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.*
import com.google.firebase.ktx.Firebase

class ListaUsuariosActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var database: FirebaseDatabase
    private lateinit var listView: ListView
    private lateinit var adapter: UserAdapter
    private val usersList = mutableListOf<User>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_lista_usuarios)

        auth = Firebase.auth
        database = FirebaseDatabase.getInstance()
        listView = findViewById(R.id.usersList)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        adapter = UserAdapter()
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedUser = usersList[position]
            val intent = Intent(this, DetalleUsuarioActivity::class.java)
            intent.putExtra("userId", selectedUser.userId)
            startActivity(intent)
        }

        loadUsers()
        setupUserStatusListener()
    }

    private fun loadUsers() {
        val usersRef = database.getReference("users")

        usersRef.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                usersList.clear()

                for (userSnapshot in snapshot.children) {
                    if (userSnapshot.key != auth.currentUser?.uid) {
                        val user = User(
                            userId = userSnapshot.key ?: "",
                            nombre = userSnapshot.child("name").getValue(String::class.java) ?: "",
                            email = userSnapshot.child("email").getValue(String::class.java) ?: "",
                            imageUrl = userSnapshot.child("imageUrl").getValue(String::class.java) ?: "",
                            status = userSnapshot.child("status").getValue(String::class.java) ?: "offline"
                        )
                        if (user.status == "available") {
                            usersList.add(user)
                        }
                    }
                }
                adapter.notifyDataSetChanged()
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@ListaUsuariosActivity, "Error al cargar usuarios", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun setupUserStatusListener() {
        val usersRef = database.getReference("users")

        usersRef.addChildEventListener(object : ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onChildRemoved(snapshot: DataSnapshot) {}
            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}
            override fun onCancelled(error: DatabaseError) {}

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {
                val userId = snapshot.key
                val newStatus = snapshot.child("status").getValue(String::class.java)
                val userName = snapshot.child("name").getValue(String::class.java)

                if (userId != auth.currentUser?.uid && newStatus == "available") {
                    userName?.let {
                        Toast.makeText(this@ListaUsuariosActivity, "$it acaba de conectarse", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        })
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.baseline_menu_24, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return super.onOptionsItemSelected(item)
    }

    // Adaptador personalizado para ListView
    inner class UserAdapter : BaseAdapter() {
        override fun getCount(): Int = usersList.size
        override fun getItem(position: Int): Any = usersList[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: layoutInflater.inflate(R.layout.item_user, parent, false)
            val user = usersList[position]

            val nameTextView = view.findViewById<TextView>(R.id.tvUserName)
            val emailTextView = view.findViewById<TextView>(R.id.tvUserEmail)
            val imageView = view.findViewById<ImageView>(R.id.ivUserImage)

            nameTextView.text = user.nombre
            emailTextView.text = user.email
            imageView.setImageResource(R.drawable.ic_default_user) 

            return view
        }
    }
}
