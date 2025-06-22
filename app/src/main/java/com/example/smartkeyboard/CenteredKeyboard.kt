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
            // Find the second row keys by their character codes (A-S-D-F-G-H-J-K-L)
            val secondRowCodes = setOf(97, 115, 100, 102, 103, 104, 106, 107, 108) // a,s,d,f,g,h,j,k,l
            val secondRowKeys = keys.filter { key ->
                key.codes.isNotEmpty() && secondRowCodes.contains(key.codes[0])
            }.sortedBy { it.x } // Sort by X position to maintain order

            if (secondRowKeys.size == 9) { // Should be 9 keys: A-S-D-F-G-H-J-K-L
                centerKeys(secondRowKeys)
                android.util.Log.d("CenteredKeyboard", "Successfully centered second row with ${secondRowKeys.size} keys")
            } else {
                android.util.Log.w("CenteredKeyboard", "Expected 9 keys in second row, found ${secondRowKeys.size}")
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

        // Get keyboard dimensions
        val totalKeyboardWidth = minWidth
        val keyWidth = keysToCenter.first().width
        val horizontalGap = 8 // 8dp gap between keys (from XML)

        // Calculate total width needed for 9 keys with gaps
        val totalKeysWidth = (keysToCenter.size * keyWidth) + ((keysToCenter.size - 1) * horizontalGap)

        // Calculate the left margin to center the row
        val leftMargin = (totalKeyboardWidth - totalKeysWidth) / 2

        android.util.Log.d("CenteredKeyboard", "Centering: keyboard width=$totalKeyboardWidth, keys width=$totalKeysWidth, left margin=$leftMargin")

        // Adjust X positions of all keys in the row
        keysToCenter.forEachIndexed { index, key ->
            val newX = leftMargin + (index * (keyWidth + horizontalGap))
            android.util.Log.d("CenteredKeyboard", "Key ${key.codes[0].toChar()}: moving from ${key.x} to $newX")
            key.x = newX
        }
    }
}
