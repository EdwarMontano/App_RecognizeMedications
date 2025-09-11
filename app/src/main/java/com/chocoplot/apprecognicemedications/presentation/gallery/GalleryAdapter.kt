// app/src/main/java/com/chocoplot/apprecognicemedications/presentation/gallery/GalleryAdapter.kt
package com.chocoplot.apprecognicemedications.presentation.gallery

import android.net.Uri
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.chocoplot.apprecognicemedications.R
import com.chocoplot.apprecognicemedications.databinding.ItemGalleryPhotoBinding
import java.text.SimpleDateFormat
import java.util.*

class GalleryAdapter(
    private val onPhotoClick: (GalleryPhoto) -> Unit,
    private val onPhotoSelectionToggle: ((GalleryPhoto) -> Unit)? = null
) : ListAdapter<GalleryPhoto, GalleryAdapter.GalleryPhotoViewHolder>(DiffCallback()) {

    private var isSelectionMode = false

    fun setSelectionMode(enabled: Boolean) {
        if (isSelectionMode != enabled) {
            isSelectionMode = enabled
            notifyDataSetChanged()
        }
    }

    fun getSelectionMode(): Boolean = isSelectionMode

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

            // Set photo ID number based on position
            binding.photoIdTextView.text = "#${adapterPosition + 1}"

            // Format and display date
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.dateTextView.text = dateFormat.format(Date(photo.dateAdded * 1000))

            // Always reset checkbox state first
            binding.selectionCheckBox.setOnClickListener(null)
            binding.root.setOnClickListener(null)
            
            // Handle selection mode UI
            if (isSelectionMode) {
                // Show selection UI
                binding.selectionCheckBox.visibility = android.view.View.VISIBLE
                binding.selectionOverlay.visibility = if (photo.isSelected) android.view.View.VISIBLE else android.view.View.GONE
                binding.selectionCheckBox.isChecked = photo.isSelected
                
                // Update card appearance for selection
                if (photo.isSelected) {
                    binding.cardView.strokeWidth = 4
                    binding.cardView.strokeColor = ContextCompat.getColor(
                        binding.root.context,
                        R.color.menu_button_tint
                    )
                    binding.cardView.cardElevation = 8f
                } else {
                    binding.cardView.strokeWidth = 0
                    binding.cardView.cardElevation = 2f
                }
                
                // Set click listeners for selection mode
                binding.root.setOnClickListener {
                    onPhotoSelectionToggle?.invoke(photo)
                }
                
                binding.selectionCheckBox.setOnClickListener {
                    onPhotoSelectionToggle?.invoke(photo)
                }
                
                // Update content description for accessibility
                binding.root.contentDescription = if (photo.isSelected) {
                    binding.root.context.getString(R.string.content_desc_selected_photo)
                } else {
                    binding.root.context.getString(R.string.content_desc_unselected_photo)
                }
            } else {
                // Hide selection UI - CRITICAL: Always hide checkboxes when not in selection mode
                binding.selectionCheckBox.visibility = android.view.View.GONE
                binding.selectionOverlay.visibility = android.view.View.GONE
                binding.cardView.strokeWidth = 0
                binding.cardView.cardElevation = 2f
                binding.selectionCheckBox.isChecked = false
                
                // Set click listener for normal photo view
                binding.root.setOnClickListener {
                    onPhotoClick(photo)
                }
                
                // Reset content description
                binding.root.contentDescription = "Foto de medicamento ${dateFormat.format(Date(photo.dateAdded * 1000))}"
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