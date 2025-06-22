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
                centerKeysWithMargins(secondRowKeys)
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
     * Center the given keys by setting them to 10% width and adding margins
     */
    private fun centerKeysWithMargins(keysToCenter: List<Key>) {
        if (keysToCenter.isEmpty()) return

        // Get keyboard dimensions
        val totalKeyboardWidth = minWidth

        // Force key width to be exactly 10% of keyboard width (same as first row)
        val keyWidth = (totalKeyboardWidth * 0.10).toInt()

        // Calculate total width needed for 9 keys at 10% each = 90%
        val totalKeysWidth = keyWidth * 9

        // Calculate left margin to center the row (5% of keyboard width)
        val leftMargin = (totalKeyboardWidth - totalKeysWidth) / 2

        android.util.Log.d("CenteredKeyboard", "Centering: keyboard width=$totalKeyboardWidth, forced key width=$keyWidth (10%), total keys width=$totalKeysWidth, left margin=$leftMargin")

        // Set both width and X positions for all keys in the row
        keysToCenter.forEachIndexed { index, key ->
            // Force width to 10% of keyboard width
            key.width = keyWidth

            // Position with left margin + index offset
            val newX = leftMargin + (index * keyWidth)
            android.util.Log.d("CenteredKeyboard", "Key ${key.codes[0].toChar()}: setting width=${key.width}, moving x from ${key.x} to $newX")
            key.x = newX
        }

        android.util.Log.d("CenteredKeyboard", "Second row centered with ${leftMargin}px margin on each side")
    }
}
