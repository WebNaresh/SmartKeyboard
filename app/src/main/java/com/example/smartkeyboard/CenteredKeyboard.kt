package com.example.smartkeyboard

import android.content.Context
import android.inputmethodservice.Keyboard

/**
 * Custom Keyboard class that centers the second row (ASDFGHJKL)
 * by adjusting key positions after the keyboard is loaded
 */
class CenteredKeyboard(context: Context, xmlLayoutResId: Int) : Keyboard(context, xmlLayoutResId) {
    
    init {
        centerSecondRow()
    }
    
    /**
     * Center the second row (ASDFGHJKL) by adjusting key positions
     */
    private fun centerSecondRow() {
        try {
            // Find the second row (index 1, containing ASDFGHJKL)
            if (keys.size > 1) {
                val secondRowKeys = keys.filter { key ->
                    // Check if this key is in the second row by its Y position
                    // Second row should have Y position between first and third rows
                    val firstRowY = keys.firstOrNull()?.y ?: 0
                    val keyY = key.y
                    keyY > firstRowY && keyY < (firstRowY + height / 2)
                }
                
                if (secondRowKeys.size == 9) { // Should be 9 keys: A-S-D-F-G-H-J-K-L
                    centerKeys(secondRowKeys)
                }
            }
        } catch (e: Exception) {
            // If centering fails, fall back to default layout
            android.util.Log.w("CenteredKeyboard", "Failed to center second row", e)
        }
    }
    
    /**
     * Center the given keys by adjusting their X positions
     */
    private fun centerKeys(keysToCenter: List<Key>) {
        if (keysToCenter.isEmpty()) return
        
        // Calculate total width needed for the keys
        val keyWidth = keysToCenter.first().width
        val totalKeysWidth = keysToCenter.size * keyWidth
        
        // Calculate the offset needed to center the keys
        val totalKeyboardWidth = minWidth
        val leftMargin = (totalKeyboardWidth - totalKeysWidth) / 2
        
        // Adjust X positions of all keys in the row
        keysToCenter.forEachIndexed { index, key ->
            key.x = leftMargin + (index * keyWidth)
        }
    }
}
