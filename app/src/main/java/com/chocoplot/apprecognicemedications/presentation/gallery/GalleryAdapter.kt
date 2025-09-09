// app/src/main/java/com/chocoplot/apprecognicemedications/presentation/gallery/GalleryAdapter.kt
package com.chocoplot.apprecognicemedications.presentation.gallery

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.chocoplot.apprecognicemedications.databinding.ItemGalleryPhotoBinding
import java.text.SimpleDateFormat
import java.util.*

data class GalleryPhoto(
    val uri: Uri,
    val dateAdded: Long,
    val displayName: String
)

class GalleryAdapter(
    private val onPhotoClick: (GalleryPhoto) -> Unit
) : ListAdapter<GalleryPhoto, GalleryAdapter.GalleryPhotoViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryPhotoViewHolder {
        val binding = ItemGalleryPhotoBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GalleryPhotoViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GalleryPhotoViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class GalleryPhotoViewHolder(
        private val binding: ItemGalleryPhotoBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(photo: GalleryPhoto) {
            // Load image with Glide
            Glide.with(binding.root.context)
                .load(photo.uri)
                .centerCrop()
                .transition(DrawableTransitionOptions.withCrossFade())
                .into(binding.photoImageView)

            // Format and display date
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.dateTextView.text = dateFormat.format(Date(photo.dateAdded * 1000))

            // Set click listener
            binding.root.setOnClickListener {
                onPhotoClick(photo)
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<GalleryPhoto>() {
        override fun areItemsTheSame(oldItem: GalleryPhoto, newItem: GalleryPhoto): Boolean {
            return oldItem.uri == newItem.uri
        }

        override fun areContentsTheSame(oldItem: GalleryPhoto, newItem: GalleryPhoto): Boolean {
            return oldItem == newItem
        }
    }
}