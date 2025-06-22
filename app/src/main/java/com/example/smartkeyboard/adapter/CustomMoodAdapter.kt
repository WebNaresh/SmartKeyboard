package com.example.smartkeyboard.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartkeyboard.R
import com.example.smartkeyboard.data.CustomMood

class CustomMoodAdapter(
    private var moods: MutableList<CustomMood>,
    private val onEditClick: (CustomMood) -> Unit,
    private val onDeleteClick: (CustomMood) -> Unit
) : RecyclerView.Adapter<CustomMoodAdapter.CustomMoodViewHolder>() {

    class CustomMoodViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMoodEmoji: TextView = itemView.findViewById(R.id.tv_mood_emoji)
        val tvMoodTitle: TextView = itemView.findViewById(R.id.tv_mood_title)
        val tvMoodInstructions: TextView = itemView.findViewById(R.id.tv_mood_instructions)
        val btnEditMood: ImageButton = itemView.findViewById(R.id.btn_edit_mood)
        val btnDeleteMood: ImageButton = itemView.findViewById(R.id.btn_delete_mood)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomMoodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_custom_mood, parent, false)
        return CustomMoodViewHolder(view)
    }

    override fun onBindViewHolder(holder: CustomMoodViewHolder, position: Int) {
        val mood = moods[position]
        
        holder.tvMoodEmoji.text = mood.emoji
        holder.tvMoodTitle.text = mood.title
        holder.tvMoodInstructions.text = mood.instructions
        
        holder.btnEditMood.setOnClickListener {
            onEditClick(mood)
        }
        
        holder.btnDeleteMood.setOnClickListener {
            onDeleteClick(mood)
        }
    }

    override fun getItemCount(): Int = moods.size

    fun updateMoods(newMoods: List<CustomMood>) {
        val oldSize = moods.size
        moods.clear()
        moods.addAll(newMoods)

        // Use more efficient notifications
        if (oldSize == 0 && newMoods.isNotEmpty()) {
            notifyItemRangeInserted(0, newMoods.size)
        } else if (oldSize > 0 && newMoods.isEmpty()) {
            notifyItemRangeRemoved(0, oldSize)
        } else {
            notifyDataSetChanged()
        }
    }

    fun addMood(mood: CustomMood) {
        moods.add(mood)
        notifyItemInserted(moods.size - 1)
    }

    fun removeMood(mood: CustomMood) {
        val position = moods.indexOf(mood)
        if (position != -1) {
            moods.removeAt(position)
            notifyItemRemoved(position)
        }
    }

    fun updateMood(oldMood: CustomMood, newMood: CustomMood) {
        val position = moods.indexOf(oldMood)
        if (position != -1) {
            moods[position] = newMood
            notifyItemChanged(position)
        }
    }
}
