package com.example.smartkeyboard.manager

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import com.example.smartkeyboard.data.CustomMood
import com.example.smartkeyboard.utils.MoodBroadcastConstants

/**
 * Manager class for handling custom mood operations and storage
 */
class CustomMoodManager(private val context: Context) {

    private val sharedPreferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    /**
     * Save a new custom mood
     */
    fun saveCustomMood(mood: CustomMood): Boolean {
        return try {
            val currentMoods = getCustomMoods().toMutableList()
            
            // Check if mood with same title already exists
            if (currentMoods.any { it.title.equals(mood.title, ignoreCase = true) }) {
                return false // Mood with same title already exists
            }
            
            currentMoods.add(mood)
            val json = CustomMood.toJson(currentMoods)
            
            sharedPreferences.edit()
                .putString(CUSTOM_MOODS_KEY, json)
                .apply()

            // Send broadcast to notify keyboard of new mood
            sendMoodBroadcast(MoodBroadcastConstants.ACTION_MOOD_CREATED, mood.id, mood.title)

            true
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get all custom moods
     */
    fun getCustomMoods(): List<CustomMood> {
        val json = sharedPreferences.getString(CUSTOM_MOODS_KEY, null)
        return if (json != null) {
            CustomMood.fromJson(json)
        } else {
            emptyList()
        }
    }
    
    /**
     * Update an existing custom mood
     */
    fun updateCustomMood(oldMood: CustomMood, newMood: CustomMood): Boolean {
        return try {
            val currentMoods = getCustomMoods().toMutableList()
            val index = currentMoods.indexOfFirst { it.id == oldMood.id }
            
            if (index != -1) {
                // Check if new title conflicts with other moods (excluding current one)
                val titleConflict = currentMoods.any { 
                    it.id != oldMood.id && it.title.equals(newMood.title, ignoreCase = true) 
                }
                
                if (titleConflict) {
                    return false
                }
                
                currentMoods[index] = newMood
                val json = CustomMood.toJson(currentMoods)
                
                sharedPreferences.edit()
                    .putString(CUSTOM_MOODS_KEY, json)
                    .apply()

                // Send broadcast to notify keyboard of mood update
                sendMoodBroadcast(
                    MoodBroadcastConstants.ACTION_MOOD_UPDATED,
                    newMood.id,
                    newMood.title,
                    oldMood.id
                )

                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Delete a custom mood
     */
    fun deleteCustomMood(mood: CustomMood): Boolean {
        return try {
            val currentMoods = getCustomMoods().toMutableList()
            val removed = currentMoods.removeIf { it.id == mood.id }
            
            if (removed) {
                val json = CustomMood.toJson(currentMoods)
                sharedPreferences.edit()
                    .putString(CUSTOM_MOODS_KEY, json)
                    .apply()

                // Send broadcast to notify keyboard of mood deletion
                sendMoodBroadcast(MoodBroadcastConstants.ACTION_MOOD_DELETED, mood.id, mood.title)
            }

            removed
        } catch (e: Exception) {
            false
        }
    }
    
    /**
     * Get a custom mood by ID
     */
    fun getCustomMoodById(id: String): CustomMood? {
        return getCustomMoods().find { it.id == id }
    }
    
    /**
     * Check if a mood title already exists
     */
    fun isTitleExists(title: String, excludeId: String? = null): Boolean {
        return getCustomMoods().any { 
            it.title.equals(title, ignoreCase = true) && it.id != excludeId 
        }
    }
    
    /**
     * Send broadcast to notify keyboard of mood changes
     */
    private fun sendMoodBroadcast(
        action: String,
        moodId: String,
        moodTitle: String,
        oldMoodId: String? = null
    ) {
        try {
            val intent = Intent(action).apply {
                // Set explicit package to ensure broadcast reaches our app
                setPackage(context.packageName)
                putExtra(MoodBroadcastConstants.EXTRA_MOOD_ID, moodId)
                putExtra(MoodBroadcastConstants.EXTRA_MOOD_TITLE, moodTitle)
                oldMoodId?.let {
                    putExtra(MoodBroadcastConstants.EXTRA_OLD_MOOD_ID, it)
                }
                // Add flags to ensure delivery
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }

            android.util.Log.d("CustomMoodManager", "Sending broadcast: $action for mood: $moodTitle ($moodId)")
            context.sendBroadcast(intent)

            // Also send a general refresh broadcast as backup
            val refreshIntent = Intent(MoodBroadcastConstants.ACTION_MOODS_REFRESHED).apply {
                setPackage(context.packageName)
                addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
            }
            context.sendBroadcast(refreshIntent)
            android.util.Log.d("CustomMoodManager", "Sent backup refresh broadcast")

        } catch (e: Exception) {
            android.util.Log.e("CustomMoodManager", "Error sending mood broadcast", e)
        }
    }

    companion object {
        private const val PREFS_NAME = "SmartKeyboardPrefs"
        private const val CUSTOM_MOODS_KEY = "custom_moods"
    }
}
