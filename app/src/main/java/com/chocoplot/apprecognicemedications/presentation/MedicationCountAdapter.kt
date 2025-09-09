package com.chocoplot.apprecognicemedications.presentation

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.chocoplot.apprecognicemedications.R

class MedicationCountAdapter : RecyclerView.Adapter<MedicationCountAdapter.ViewHolder>() {
    
    private var medicationCounts = listOf<MedicationCount>()
    
    fun updateCounts(newCounts: List<MedicationCount>) {
        medicationCounts = newCounts
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_medication_result, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val medicationCount = medicationCounts[position]
        holder.bind(medicationCount)
    }
    
    override fun getItemCount(): Int = medicationCounts.size
    
    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val medicationName: TextView = itemView.findViewById(R.id.medicationName)
        private val countText: TextView = itemView.findViewById(R.id.countText)
        private val medicationInfo: TextView = itemView.findViewById(R.id.medicationInfo)
        
        fun bind(medicationCount: MedicationCount) {
            medicationName.text = medicationCount.name
            countText.text = medicationCount.count.toString()
            
            // Ocultar info adicional en modo tiempo real
            medicationInfo.visibility = View.GONE
        }
    }
}