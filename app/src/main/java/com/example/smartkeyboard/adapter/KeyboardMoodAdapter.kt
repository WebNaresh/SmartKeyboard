package com.example.smartkeyboard.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartkeyboard.R
import com.example.smartkeyboard.data.MoodData

class KeyboardMoodAdapter(
    private var moods: List<MoodData>,
    private var selectedMoodId: String,
    private val onMoodClick: (MoodData) -> Unit
) : RecyclerView.Adapter<KeyboardMoodAdapter.MoodViewHolder>() {

    class MoodViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvMoodEmoji: TextView = itemView.findViewById(R.id.tv_mood_emoji)
        val tvMoodTitle: TextView = itemView.findViewById(R.id.tv_mood_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MoodViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_keyboard_mood, parent, false)
        return MoodViewHolder(view)
    }

    override fun onBindViewHolder(holder: MoodViewHolder, position: Int) {
        val mood = moods[position]
        
        holder.tvMoodEmoji.text = mood.emoji
        holder.tvMoodTitle.text = mood.title
        
        // Highlight selected mood
        if (mood.id == selectedMoodId) {
            holder.itemView.setBackgroundResource(R.drawable.google_key_bg_selected)
        } else {
            holder.itemView.setBackgroundResource(R.drawable.mood_item_bg)
        }
        
        holder.itemView.setOnClickListener {
            onMoodClick(mood)
        }
    }

    override fun getItemCount(): Int = moods.size

    fun updateMoods(newMoods: List<MoodData>) {
        android.util.Log.d("KeyboardMoodAdapter", "Updating moods: ${newMoods.size} moods")

        // Check if the data actually changed to avoid unnecessary updates
        val oldSize = moods.size
        val newSize = newMoods.size

        moods = newMoods

        // Use more efficient notification methods when possible
        when {
            oldSize == 0 && newSize > 0 -> {
                // First time loading moods
                notifyDataSetChanged()
                android.util.Log.d("KeyboardMoodAdapter", "Initial mood load: $newSize moods")
            }
            oldSize < newSize -> {
                // New moods added
                notifyItemRangeInserted(oldSize, newSize - oldSize)
                android.util.Log.d("KeyboardMoodAdapter", "Added ${newSize - oldSize} new moods")
            }
            oldSize > newSize -> {
                // Moods removed
                notifyItemRangeRemoved(newSize, oldSize - newSize)
                android.util.Log.d("KeyboardMoodAdapter", "Removed ${oldSize - newSize} moods")
            }
            else -> {
                // Same count, might be updates
                notifyDataSetChanged()
                android.util.Log.d("KeyboardMoodAdapter", "Updated existing moods")
            }
        }
    }

    fun updateSelectedMood(moodId: String) {
        android.util.Log.d("KeyboardMoodAdapter", "Updating selected mood to: $moodId")

        val oldSelectedIndex = moods.indexOfFirst { it.id == selectedMoodId }
        val newSelectedIndex = moods.indexOfFirst { it.id == moodId }

        selectedMoodId = moodId

        // Update only the affected items
        if (oldSelectedIndex != -1) {
            notifyItemChanged(oldSelectedIndex)
            android.util.Log.d("KeyboardMoodAdapter", "Deselected mood at index: $oldSelectedIndex")
        }
        if (newSelectedIndex != -1) {
            notifyItemChanged(newSelectedIndex)
            android.util.Log.d("KeyboardMoodAdapter", "Selected mood at index: $newSelectedIndex")
        }

        if (newSelectedIndex == -1 && moodId.isNotEmpty()) {
            android.util.Log.w("KeyboardMoodAdapter", "Could not find mood with ID: $moodId")
        }
    }
}
