package com.example.smartkeyboard.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.smartkeyboard.R
import com.example.smartkeyboard.data.MoodData

class KeyboardMoodAdapter(
    private var allMoods: List<MoodData>,
    private var selectedMoodId: String,
    private val onMoodClick: (MoodData) -> Unit
) : RecyclerView.Adapter<KeyboardMoodAdapter.MoodViewHolder>() {

    private var filteredMoods: List<MoodData> = allMoods
    private var currentSearchQuery: String = ""

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
        val mood = filteredMoods[position]
        
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

    override fun getItemCount(): Int = filteredMoods.size

    fun updateMoods(newMoods: List<MoodData>) {
        android.util.Log.d("KeyboardMoodAdapter", "Updating moods: ${newMoods.size} moods")

        allMoods = newMoods
        applyFilter(currentSearchQuery)
    }

    fun filter(query: String) {
        currentSearchQuery = query
        applyFilter(query)
    }

    private fun applyFilter(query: String) {
        filteredMoods = if (query.isEmpty()) {
            allMoods
        } else {
            allMoods.filter { mood ->
                mood.title.contains(query, ignoreCase = true) ||
                mood.instructions?.contains(query, ignoreCase = true) == true
            }
        }
        notifyDataSetChanged()
        android.util.Log.d("KeyboardMoodAdapter", "Filtered moods: ${filteredMoods.size} of ${allMoods.size} total")
    }

    fun updateSelectedMood(moodId: String) {
        android.util.Log.d("KeyboardMoodAdapter", "Updating selected mood to: $moodId")

        val oldSelectedIndex = filteredMoods.indexOfFirst { it.id == selectedMoodId }
        val newSelectedIndex = filteredMoods.indexOfFirst { it.id == moodId }

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
