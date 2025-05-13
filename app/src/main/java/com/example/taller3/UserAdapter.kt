package com.example.taller3

import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.net.URL
import kotlin.concurrent.thread

class UserAdapter(
    private val users: List<User>,
    private val onItemClick: (User) -> Unit
) : RecyclerView.Adapter<UserAdapter.UserViewHolder>() {

    inner class UserViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val nameTextView: TextView = itemView.findViewById(R.id.tvUserName)
        val emailTextView: TextView = itemView.findViewById(R.id.tvUserEmail)
        val imageView: ImageView = itemView.findViewById(R.id.ivUserImage)
        val btnVerPosicion: Button = itemView.findViewById(R.id.btnVerPosicion)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): UserViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_user, parent, false)
        return UserViewHolder(view)
    }

    override fun onBindViewHolder(holder: UserViewHolder, position: Int) {
        val user = users[position]

        holder.nameTextView.text = user.nombre
        holder.emailTextView.text = user.email

        // Imagen por defecto primero
        holder.imageView.setImageResource(R.drawable.ic_default_user)

        // Si hay URL, intenta cargar la imagen
        if (!user.imageUrl.isNullOrEmpty()) {
            thread {
                try {
                    val input = URL(user.imageUrl).openStream()
                    val bitmap = BitmapFactory.decodeStream(input)
                    holder.imageView.post {
                        holder.imageView.setImageBitmap(bitmap)
                    }
                } catch (e: Exception) {
                    // Si falla, mantiene la imagen por defecto
                    e.printStackTrace()
                }
            }
        }

        holder.btnVerPosicion.setOnClickListener {
            onItemClick(user)
        }
    }

    override fun getItemCount() = users.size
}
