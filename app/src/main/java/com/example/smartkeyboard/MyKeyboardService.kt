package com.example.smartkeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.util.Log
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.content.Context
import com.example.smartkeyboard.ai.AITextProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MyKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private var keyboardView: KeyboardView? = null
    private var keyboard: Keyboard? = null

    // Mood selection state
    private var moodButtonsContainer: LinearLayout? = null
    private var btnRespectful: LinearLayout? = null
    private var btnFunny: LinearLayout? = null
    private var btnAngry: LinearLayout? = null
    private var moodSelectorPill: LinearLayout? = null
    private var moodEmoji: TextView? = null
    private var btnEnhanceText: ImageButton? = null

    // AI integration
    private var aiTextProcessor: AITextProcessor? = null
    private var isAIEnabled: Boolean = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Keyboard state
    private var moodButtonsVisible = false
    private var currentMood = MoodType.NORMAL
    private var isShifted = false

    // Haptic feedback
    private var vibrator: Vibrator? = null

    enum class MoodType {
        NORMAL, RESPECTFUL, FUNNY, ANGRY
    }
    
    override fun onCreateInputView(): View {
        val inputView = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardView = inputView.findViewById(R.id.keyboard_view)
        keyboard = Keyboard(this, R.xml.qwerty)
        keyboardView?.keyboard = keyboard
        keyboardView?.setOnKeyboardActionListener(this)

        // Initialize vibrator for haptic feedback
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Initialize mood buttons
        setupMoodButtons(inputView)

        return inputView
    }
    
    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // Initialize keyboard based on input type if needed
        initializeAIFromSettings()
    }
    
    override fun onStartInputView(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        keyboardView?.keyboard = keyboard
        keyboardView?.closing()
    }
    
    // KeyboardView.OnKeyboardActionListener methods
    override fun onPress(primaryCode: Int) {
        // Called when a key is pressed - provide haptic feedback
        performHapticFeedback()
    }
    
    override fun onRelease(primaryCode: Int) {
        // Called when a key is released
    }
    
    override fun onKey(primaryCode: Int, keyCodes: IntArray?) {
        when (primaryCode) {
            Keyboard.KEYCODE_DELETE -> {
                // Handle backspace
                val inputConnection = currentInputConnection
                inputConnection?.deleteSurroundingText(1, 0)
            }
            Keyboard.KEYCODE_DONE -> {
                // Handle enter/done
                val inputConnection = currentInputConnection
                inputConnection?.sendKeyEvent(
                    android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_DOWN,
                        android.view.KeyEvent.KEYCODE_ENTER
                    )
                )
                inputConnection?.sendKeyEvent(
                    android.view.KeyEvent(
                        android.view.KeyEvent.ACTION_UP,
                        android.view.KeyEvent.KEYCODE_ENTER
                    )
                )
            }
            Keyboard.KEYCODE_SHIFT -> {
                // Handle shift key
                handleShift()
            }
            else -> {
                // Handle regular character input
                if (primaryCode > 0) {
                    var character = primaryCode.toChar()

                    // Apply shift/uppercase if needed
                    if (isShifted && character.isLetter()) {
                        character = character.uppercaseChar()
                        // Auto-disable shift after typing one character (like normal keyboards)
                        isShifted = false
                        keyboard?.isShifted = false
                        keyboardView?.invalidateAllKeys()
                    }

                    // For single characters, just commit directly (no mood transformation)
                    // Mood transformation only applies when using the enhance button
                    val inputConnection = currentInputConnection
                    inputConnection?.commitText(character.toString(), 1)
                }
            }
        }
    }
    
    override fun onText(text: CharSequence?) {
        text?.let { handleTextInput(it.toString()) }
    }
    
    override fun swipeLeft() {
        // Handle swipe left gesture
    }
    
    override fun swipeRight() {
        // Handle swipe right gesture
    }
    
    override fun swipeDown() {
        // Handle swipe down gesture
    }
    
    override fun swipeUp() {
        // Handle swipe up gesture
    }
    
    private fun handleShift() {
        if (keyboard != null) {
            isShifted = !keyboard!!.isShifted
            keyboard!!.isShifted = isShifted
            keyboardView?.invalidateAllKeys()
        }
    }

    private fun setupMoodButtons(inputView: View) {
        moodButtonsContainer = inputView.findViewById(R.id.mood_buttons_container)
        moodSelectorPill = inputView.findViewById(R.id.mood_selector_pill)
        moodEmoji = inputView.findViewById(R.id.mood_emoji)
        btnRespectful = inputView.findViewById(R.id.btn_respectful)
        btnFunny = inputView.findViewById(R.id.btn_funny)
        btnAngry = inputView.findViewById(R.id.btn_angry)
        btnEnhanceText = inputView.findViewById(R.id.btn_enhance_text)

        // Set click listeners with haptic feedback
        btnRespectful?.setOnClickListener {
            performHapticFeedback()
            selectMood(MoodType.RESPECTFUL)
        }
        btnFunny?.setOnClickListener {
            performHapticFeedback()
            selectMood(MoodType.FUNNY)
        }
        btnAngry?.setOnClickListener {
            performHapticFeedback()
            selectMood(MoodType.ANGRY)
        }
        moodSelectorPill?.setOnClickListener {
            performHapticFeedback()
            toggleMoodButtonsVisibility()
        }
        btnEnhanceText?.setOnClickListener {
            performHapticFeedback()
            enhanceCurrentText()
        }

        // Initially hide mood buttons
        moodButtonsContainer?.visibility = View.GONE

        // Initialize AI by default
        initializeAIFromSettings()
        isAIEnabled = true

        updateMoodButtonsUI()
        updateMoodPillAppearance()
    }

    private fun selectMood(mood: MoodType) {
        currentMood = mood
        updateMoodButtonsUI()
        updateMoodPillAppearance()
    }

    private fun toggleMoodButtonsVisibility() {
        moodButtonsVisible = !moodButtonsVisible
        moodButtonsContainer?.visibility = if (moodButtonsVisible) View.VISIBLE else View.GONE

        // Update mood pill appearance
        updateMoodPillAppearance()
    }

    private fun updateMoodButtonsUI() {
        // Reset all buttons to normal state
        btnRespectful?.setBackgroundResource(R.drawable.mood_item_bg)
        btnFunny?.setBackgroundResource(R.drawable.mood_item_bg)
        btnAngry?.setBackgroundResource(R.drawable.mood_item_bg)

        // Highlight selected mood
        when (currentMood) {
            MoodType.RESPECTFUL -> btnRespectful?.setBackgroundResource(R.drawable.google_key_bg_selected)
            MoodType.FUNNY -> btnFunny?.setBackgroundResource(R.drawable.google_key_bg_selected)
            MoodType.ANGRY -> btnAngry?.setBackgroundResource(R.drawable.google_key_bg_selected)
            MoodType.NORMAL -> { /* No button highlighted */ }
        }
    }

    private fun applyMoodTransformation(text: String): String {
        return when (currentMood) {
            MoodType.RESPECTFUL -> transformToRespectful(text)
            MoodType.FUNNY -> transformToFunny(text)
            MoodType.ANGRY -> transformToAngry(text)
            MoodType.NORMAL -> text
        }
    }

    private fun transformToRespectful(text: String): String {
        // Simple respectful transformations
        return when (text.lowercase()) {
            "no" -> "I respectfully disagree"
            "yes" -> "I would be honored to"
            "ok" -> "Certainly"
            "thanks" -> "Thank you very much"
            "hi" -> "Good day"
            "bye" -> "Have a wonderful day"
            else -> {
                // Add respectful modifiers to sentences
                if (text.endsWith(".") || text.endsWith("!")) {
                    text.dropLast(1) + ", if I may say so."
                } else {
                    text
                }
            }
        }
    }

    private fun transformToFunny(text: String): String {
        // Simple funny transformations
        return when (text.lowercase()) {
            "hello" -> "Howdy partner! 🤠"
            "yes" -> "Absolutely-positively! 😄"
            "no" -> "Nope-a-dope! 😅"
            "ok" -> "Okey-dokey! 👍"
            "thanks" -> "Thanks a bunch! 🙌"
            "bye" -> "See ya later, alligator! 🐊"
            else -> {
                // Add funny emojis or expressions
                if (text.endsWith(".")) {
                    text.dropLast(1) + "! 😂"
                } else if (text.endsWith("!")) {
                    text + " 🎉"
                } else {
                    text
                }
            }
        }
    }

    private fun transformToAngry(text: String): String {
        // Simple angry transformations (keeping it appropriate)
        return when (text.lowercase()) {
            "hello" -> "WHAT?!"
            "yes" -> "FINE!"
            "no" -> "ABSOLUTELY NOT!"
            "ok" -> "WHATEVER!"
            "thanks" -> "About time!"
            "bye" -> "GOOD RIDDANCE!"
            else -> {
                // Convert to uppercase for emphasis
                if (text.length > 1) {
                    text.uppercase() + "!"
                } else {
                    text.uppercase()
                }
            }
        }
    }

    private fun updateMoodPillAppearance() {
        // Update mood emoji in the pill
        val moodEmojiText = when (currentMood) {
            MoodType.RESPECTFUL -> "❤️"
            MoodType.FUNNY -> "😊"
            MoodType.ANGRY -> "😠"
            MoodType.NORMAL -> "❤️"
        }
        moodEmoji?.text = moodEmojiText

        // Highlight pill if a mood is selected
        if (currentMood != MoodType.NORMAL) {
            moodSelectorPill?.setBackgroundResource(R.drawable.google_key_bg_selected)
        } else {
            moodSelectorPill?.setBackgroundResource(R.drawable.mood_pill_bg)
        }
    }

    /**
     * Initialize AI from saved settings
     */
    private fun initializeAIFromSettings() {
        val apiKey = SettingsActivity.getApiKey(this)
        if (!apiKey.isNullOrBlank()) {
            initializeAI(apiKey)
        }
    }



    /**
     * Initialize AI processor with API key
     */
    fun initializeAI(apiKey: String) {
        try {
            aiTextProcessor = AITextProcessor(apiKey)
            isAIEnabled = true
            Log.d(TAG, "AI processor initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize AI processor", e)
            isAIEnabled = false
        }
    }

    /**
     * Handle text input with optional AI enhancement
     */
    private fun handleTextInput(text: String) {
        if (isAIEnabled && aiTextProcessor != null && text.length > 1) {
            // Use AI for longer text inputs
            enhanceTextWithAI(text)
        } else {
            // Use local mood transformation for single characters or when AI is disabled
            val transformedText = applyMoodTransformation(text)
            commitText(transformedText)
        }
    }

    /**
     * Enhance text using AI
     */
    private fun enhanceTextWithAI(text: String) {
        serviceScope.launch {
            try {
                val aiMood = when (currentMood) {
                    MoodType.RESPECTFUL -> AITextProcessor.MoodType.RESPECTFUL
                    MoodType.FUNNY -> AITextProcessor.MoodType.FUNNY
                    MoodType.ANGRY -> AITextProcessor.MoodType.ANGRY
                    MoodType.NORMAL -> AITextProcessor.MoodType.NORMAL
                }

                val result = aiTextProcessor?.enhanceText(text, aiMood)
                result?.fold(
                    onSuccess = { enhancedText ->
                        // Now replace the text with AI response
                        replaceAllTextWithEnhanced(enhancedText)
                    },
                    onFailure = { error ->
                        Log.w(TAG, "AI enhancement failed, using local transformation", error)
                        val fallbackText = applyMoodTransformation(text)
                        replaceAllTextWithEnhanced(fallbackText)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in AI enhancement", e)
                val fallbackText = applyMoodTransformation(text)
                replaceAllTextWithEnhanced(fallbackText)
            }
        }
    }

    /**
     * Replace all text in input field with enhanced text
     */
    private fun replaceAllTextWithEnhanced(enhancedText: String) {
        val inputConnection = currentInputConnection ?: return
        try {
            // Select all text and replace with enhanced version
            inputConnection.performContextMenuAction(android.R.id.selectAll)
            inputConnection.commitText(enhancedText, 1)
        } catch (e: Exception) {
            Log.e(TAG, "Error replacing text", e)
        }
    }

    /**
     * Enhance the current text in the input field
     */
    private fun enhanceCurrentText() {
        val inputConnection = currentInputConnection ?: return

        try {
            // Get all text before cursor
            val textBeforeCursor = inputConnection.getTextBeforeCursor(1000, 0)?.toString() ?: ""
            // Get all text after cursor
            val textAfterCursor = inputConnection.getTextAfterCursor(1000, 0)?.toString() ?: ""

            val currentText = textBeforeCursor + textAfterCursor

            if (currentText.isBlank()) {
                Log.d(TAG, "No text to enhance")
                return
            }

            Log.d(TAG, "Enhancing text: '$currentText'")

            // DON'T delete text immediately - keep it visible until response arrives
            // Enhance the text based on current settings
            if (isAIEnabled && aiTextProcessor != null) {
                // Use AI enhancement - text will be replaced when response arrives
                enhanceTextWithAI(currentText)
            } else {
                // Use local mood transformation - replace immediately since it's instant
                inputConnection.performContextMenuAction(android.R.id.selectAll)
                val enhancedText = enhanceTextLocally(currentText)
                inputConnection.commitText(enhancedText, 1)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error enhancing current text", e)
        }
    }

    /**
     * Enhance text using local mood transformations for longer text
     */
    private fun enhanceTextLocally(text: String): String {
        return when (currentMood) {
            MoodType.RESPECTFUL -> enhanceRespectfully(text)
            MoodType.FUNNY -> enhanceFunnily(text)
            MoodType.ANGRY -> enhanceAngrily(text)
            MoodType.NORMAL -> enhanceNormally(text)
        }
    }

    private fun enhanceRespectfully(text: String): String {
        val lowerText = text.lowercase().trim()
        return when {
            lowerText.contains("want") && lowerText.contains("number") ->
                "I would be honored to have your contact number, if you don't mind."
            lowerText.startsWith("hi") || lowerText.startsWith("hello") ->
                "Good day, ${text.substringAfter(" ").ifBlank { "sir/madam" }}. ${text.substringAfter("hi").substringAfter("hello").trim()}"
            lowerText.contains("help") ->
                "I would greatly appreciate your assistance with this matter."
            lowerText.contains("thanks") || lowerText.contains("thank") ->
                "I am deeply grateful for your time and consideration."
            lowerText.contains("sorry") ->
                "I sincerely apologize for any inconvenience caused."
            else -> "I hope you don't mind, but $text"
        }
    }

    private fun enhanceFunnily(text: String): String {
        val lowerText = text.lowercase().trim()
        return when {
            lowerText.contains("want") && lowerText.contains("number") ->
                "Hey there! Could I snag your digits? 📱😄"
            lowerText.startsWith("hi") || lowerText.startsWith("hello") ->
                "Howdy partner! 🤠 What's cooking?"
            lowerText.contains("help") ->
                "SOS! I need a superhero! 🦸‍♂️ Can you save the day?"
            lowerText.contains("thanks") || lowerText.contains("thank") ->
                "You're absolutely amazing! Thanks a million! 🙌✨"
            lowerText.contains("sorry") ->
                "Oopsie daisy! My bad! 😅🤷‍♂️"
            else -> "$text (but make it fun! 🎉)"
        }
    }

    private fun enhanceAngrily(text: String): String {
        val lowerText = text.lowercase().trim()
        return when {
            lowerText.contains("want") && lowerText.contains("number") ->
                "I NEED YOUR NUMBER RIGHT NOW!"
            lowerText.startsWith("hi") || lowerText.startsWith("hello") ->
                "WHAT'S UP?! ${text.substringAfter(" ").uppercase()}"
            lowerText.contains("help") ->
                "I REQUIRE IMMEDIATE ASSISTANCE!"
            lowerText.contains("thanks") || lowerText.contains("thank") ->
                "FINALLY! About time!"
            lowerText.contains("sorry") ->
                "YOU BETTER BE SORRY!"
            else -> text.uppercase() + "!"
        }
    }

    private fun enhanceNormally(text: String): String {
        // Just clean up the text - capitalize first letter, add period if needed
        val cleaned = text.trim()
        return if (cleaned.isNotEmpty()) {
            val capitalized = cleaned.first().uppercase() + cleaned.drop(1)
            if (capitalized.endsWith(".") || capitalized.endsWith("!") || capitalized.endsWith("?")) {
                capitalized
            } else {
                "$capitalized."
            }
        } else {
            text
        }
    }

    /**
     * Commit text to input connection
     */
    private fun commitText(text: String) {
        val inputConnection = currentInputConnection
        inputConnection?.commitText(text, 1)
    }

    /**
     * Provide haptic feedback for key presses
     */
    private fun performHapticFeedback() {
        try {
            vibrator?.let { vib ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    // Use VibrationEffect for API 26+
                    val effect = VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
                    vib.vibrate(effect)
                } else {
                    // Fallback for older versions
                    @Suppress("DEPRECATION")
                    vib.vibrate(25)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error providing haptic feedback", e)
        }
    }

    companion object {
        private const val TAG = "MyKeyboardService"
    }
}
