package com.example.smartkeyboard.data

/**
 * Represents a mood that can be either default or custom
 */
sealed class MoodData {
    abstract val id: String
    abstract val title: String
    abstract val emoji: String
    abstract val instructions: String?

    // Removed default moods - system is now completely dynamic with custom moods only
    
    /**
     * Custom user-created mood (now the only type of mood)
     */
    data class CustomMoodData(
        override val id: String,
        override val title: String,
        override val emoji: String,
        override val instructions: String,
        val customMood: CustomMood
    ) : MoodData()

    companion object {
        /**
         * Get all available moods (custom moods only)
         */
        fun getAllMoods(customMoods: List<CustomMood>): List<MoodData> {
            return customMoods.map { customMood ->
                CustomMoodData(
                    id = customMood.id,
                    title = customMood.title,
                    emoji = customMood.emoji,
                    instructions = customMood.instructions,
                    customMood = customMood
                )
            }
        }

        /**
         * Find mood by ID (custom moods only)
         */
        fun findMoodById(id: String, customMoods: List<CustomMood>): MoodData? {
            return customMoods.find { it.id == id }?.let { customMood ->
                CustomMoodData(
                    id = customMood.id,
                    title = customMood.title,
                    emoji = customMood.emoji,
                    instructions = customMood.instructions,
                    customMood = customMood
                )
            }
        }
    }
}
