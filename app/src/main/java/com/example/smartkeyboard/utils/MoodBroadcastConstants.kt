package com.example.smartkeyboard.utils

/**
 * Constants for mood-related broadcasts between settings and keyboard
 */
object MoodBroadcastConstants {
    const val ACTION_MOOD_CREATED = "com.example.smartkeyboard.MOOD_CREATED"
    const val ACTION_MOOD_UPDATED = "com.example.smartkeyboard.MOOD_UPDATED"
    const val ACTION_MOOD_DELETED = "com.example.smartkeyboard.MOOD_DELETED"
    const val ACTION_MOODS_REFRESHED = "com.example.smartkeyboard.MOODS_REFRESHED"
    
    const val EXTRA_MOOD_ID = "mood_id"
    const val EXTRA_MOOD_TITLE = "mood_title"
    const val EXTRA_OLD_MOOD_ID = "old_mood_id"
    const val EXTRA_NEW_MOOD_ID = "new_mood_id"
}
