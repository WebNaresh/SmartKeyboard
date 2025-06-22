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
        moods = newMoods
        notifyDataSetChanged()
    }

    fun updateSelectedMood(moodId: String) {
        val oldSelectedIndex = moods.indexOfFirst { it.id == selectedMoodId }
        val newSelectedIndex = moods.indexOfFirst { it.id == moodId }
        
        selectedMoodId = moodId
        
        // Update only the affected items
        if (oldSelectedIndex != -1) {
            notifyItemChanged(oldSelectedIndex)
        }
        if (newSelectedIndex != -1) {
            notifyItemChanged(newSelectedIndex)
        }
    }
}
