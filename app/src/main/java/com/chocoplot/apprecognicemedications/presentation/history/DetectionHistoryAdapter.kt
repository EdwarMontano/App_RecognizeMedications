package com.chocoplot.apprecognicemedications.presentation.history

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.chocoplot.apprecognicemedications.data.DetectionDatabase
import com.chocoplot.apprecognicemedications.data.DetectionSession
import com.chocoplot.apprecognicemedications.databinding.ItemDetectionHistoryBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*

class DetectionHistoryAdapter(
    private val onItemClick: (DetectionSession) -> Unit
) : ListAdapter<DetectionSession, DetectionHistoryAdapter.ViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemDetectionHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val session = getItem(position)
        holder.bind(session)
    }

    inner class ViewHolder(
        private val binding: ItemDetectionHistoryBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                val position = adapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(getItem(position))
                }
            }
        }

        fun bind(session: DetectionSession) {
            // Format date for display
            val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
            binding.textDate.text = dateFormat.format(Date(session.timestamp))
            
            // Show total detections
            binding.textSummary.text = "${session.totalItems} medicamentos detectados"
            
            // Load thumbnail image
            Glide.with(binding.root.context)
                .load(session.photoUri)
                .centerCrop()
                .into(binding.imagePhoto)
            
            // Load medications summary in background
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val db = DetectionDatabase(binding.root.context)
                    val summary = db.getDetectionSummary(session.id)
                    
                    // Format medications summary
                    val topMedications = summary.entries
                        .sortedByDescending { it.value }
                        .take(3)
                        .joinToString(", ") { "${it.key} (${it.value})" }
                    
                    val medicationText = if (summary.size > 3) {
                        "$topMedications..."
                    } else {
                        topMedications
                    }
                    
                    withContext(Dispatchers.Main) {
                        binding.textMedications.text = medicationText
                    }
                } catch (e: Exception) {
                    // Silently fail but show something
                    withContext(Dispatchers.Main) {
                        binding.textMedications.text = "Ver detalles"
                    }
                }
            }
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<DetectionSession>() {
        override fun areItemsTheSame(oldItem: DetectionSession, newItem: DetectionSession): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DetectionSession, newItem: DetectionSession): Boolean {
            return oldItem == newItem
        }
    }
}