package com.example.smartkeyboard.data

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Data class representing a custom mood created by the user
 */
data class CustomMood(
    val id: String,
    val title: String,
    val instructions: String,
    val emoji: String = "🎭", // Default emoji for custom moods
    val createdAt: Long = System.currentTimeMillis()
) {
    companion object {
        /**
         * Convert list of CustomMood to JSON string for storage
         */
        fun toJson(moods: List<CustomMood>): String {
            return Gson().toJson(moods)
        }
        
        /**
         * Convert JSON string to list of CustomMood
         */
        fun fromJson(json: String): List<CustomMood> {
            return try {
                val type = object : TypeToken<List<CustomMood>>() {}.type
                Gson().fromJson(json, type) ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
        }
        
        /**
         * Generate a unique ID for a new custom mood
         */
        fun generateId(): String {
            return "custom_${System.currentTimeMillis()}_${(1000..9999).random()}"
        }
    }
}
