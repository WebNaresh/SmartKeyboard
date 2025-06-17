package com.example.smartkeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.FrameLayout
import android.util.Log
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.content.Context
import android.graphics.Color
import android.widget.HorizontalScrollView
import androidx.core.content.ContextCompat
import com.example.smartkeyboard.ai.AITextProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MyKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private var keyboardView: MaterialKeyboardView? = null
    private var keyboard: Keyboard? = null

    // Mood selection state
    private var moodButtonsContainer: LinearLayout? = null
    private var moodOverlay: View? = null
    private var btnRespectful: LinearLayout? = null
    private var btnFunny: LinearLayout? = null
    private var btnAngry: LinearLayout? = null
    private var moodSelectorPill: FrameLayout? = null
    private var moodEmoji: TextView? = null
    private var btnEnhanceText: FrameLayout? = null

    // Auto suggestion components
    private var suggestionScrollView: HorizontalScrollView? = null
    private var suggestionContainer: LinearLayout? = null
    private var currentTypedText = StringBuilder()
    private var suggestionJob: Job? = null

    // AI integration
    private var aiTextProcessor: AITextProcessor? = null
    private var isAIEnabled: Boolean = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Keyboard state
    private var moodButtonsVisible = false
    private var currentMood = MoodType.RESPECTFUL
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

        // Initialize suggestion bar
        setupSuggestionBar(inputView)

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

                // Remove last character from typed text and update suggestions
                if (currentTypedText.isNotEmpty()) {
                    currentTypedText.deleteCharAt(currentTypedText.length - 1)
                    triggerAutoSuggestion()
                }
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

                    // Handle space and other characters for auto suggestions
                    if (character == ' ') {
                        // Space ends current word, trigger final suggestion and reset
                        triggerAutoSuggestion()
                        currentTypedText.clear()
                    } else {
                        // Add character to current typed text for auto suggestions
                        currentTypedText.append(character)
                        triggerAutoSuggestion()
                    }
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
        moodOverlay = inputView.findViewById(R.id.mood_overlay)
        moodSelectorPill = inputView.findViewById<FrameLayout>(R.id.mood_selector_pill)
        moodEmoji = inputView.findViewById<TextView>(R.id.mood_emoji)
        btnRespectful = inputView.findViewById<LinearLayout>(R.id.btn_respectful)
        btnFunny = inputView.findViewById<LinearLayout>(R.id.btn_funny)
        btnAngry = inputView.findViewById<LinearLayout>(R.id.btn_angry)
        btnEnhanceText = inputView.findViewById<FrameLayout>(R.id.btn_enhance_text)

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

        // Click outside to close mood dialog
        moodOverlay?.setOnClickListener {
            if (moodButtonsVisible) {
                toggleMoodButtonsVisibility()
            }
        }

        // Initially hide mood buttons and overlay
        moodButtonsContainer?.visibility = View.GONE
        moodOverlay?.visibility = View.GONE

        // Initialize AI by default
        initializeAIFromSettings()
        isAIEnabled = true

        updateMoodButtonsUI()
        updateMoodPillAppearance()
    }

    private fun selectMood(mood: MoodType) {
        currentMood = mood

        // Add scale animation to selected mood button
        val selectedButton = when (mood) {
            MoodType.RESPECTFUL -> btnRespectful
            MoodType.FUNNY -> btnFunny
            MoodType.ANGRY -> btnAngry
            MoodType.NORMAL -> null
        }

        selectedButton?.let { button ->
            val scaleAnimation = AnimationUtils.loadAnimation(this, R.anim.mood_item_scale)
            button.startAnimation(scaleAnimation)
        }

        updateMoodButtonsUI()
        updateMoodPillAppearance()

        // Hide mood menu after selection with animation
        if (moodButtonsVisible) {
            toggleMoodButtonsVisibility()
        }
    }

    private fun toggleMoodButtonsVisibility() {
        moodButtonsVisible = !moodButtonsVisible

        if (moodButtonsVisible) {
            // Show with animation
            moodOverlay?.visibility = View.VISIBLE
            moodButtonsContainer?.visibility = View.VISIBLE
            val scaleInAnimation = AnimationUtils.loadAnimation(this, R.anim.mood_menu_scale_in)
            moodButtonsContainer?.startAnimation(scaleInAnimation)
        } else {
            // Hide with animation
            val scaleOutAnimation = AnimationUtils.loadAnimation(this, R.anim.mood_menu_scale_out)
            scaleOutAnimation.setAnimationListener(object : android.view.animation.Animation.AnimationListener {
                override fun onAnimationStart(animation: android.view.animation.Animation?) {}
                override fun onAnimationRepeat(animation: android.view.animation.Animation?) {}
                override fun onAnimationEnd(animation: android.view.animation.Animation?) {
                    moodButtonsContainer?.visibility = View.GONE
                    moodOverlay?.visibility = View.GONE
                }
            })
            moodButtonsContainer?.startAnimation(scaleOutAnimation)
        }

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

        // Highlight pill if a mood is selected with glowing outline (Respectful is default, so always highlighted)
        moodSelectorPill?.isSelected = true
    }

    /**
     * Initialize AI from saved settings
     */
    private fun initializeAIFromSettings() {
        val apiKey = SettingsActivity.getApiKey(this)
        if (!apiKey.isNullOrBlank()) {
            initializeAI(apiKey)
        } else {
            // Enable mock AI for demo purposes when no API key is configured
            initializeMockAI()
        }
    }

    /**
     * Initialize mock AI for demo purposes (no API key required)
     */
    private fun initializeMockAI() {
        try {
            // Create a mock AI processor that doesn't require API key
            aiTextProcessor = AITextProcessor("mock-key-for-demo")
            isAIEnabled = true
            Log.d(TAG, "Mock AI processor initialized for demo")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize mock AI processor", e)
            isAIEnabled = false
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
            // Always try to use AI enhancement first
            if (isAIEnabled && aiTextProcessor != null) {
                // Use AI enhancement - text will be replaced when response arrives
                enhanceTextWithAI(currentText)
            } else {
                // If AI is not available, use simple enhancement
                inputConnection.performContextMenuAction(android.R.id.selectAll)
                val enhancedText = enhanceTextLocally(currentText)
                inputConnection.commitText(enhancedText, 1)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error enhancing current text", e)
        }
    }

    /**
     * Enhance text using AI (replaces old local mood transformations)
     */
    private fun enhanceTextLocally(text: String): String {
        // This function is now deprecated - we use AI for all enhancements
        // Only used as fallback when AI completely fails
        return when (currentMood) {
            MoodType.RESPECTFUL -> "Please $text"
            MoodType.FUNNY -> "$text 😄"
            MoodType.ANGRY -> text.uppercase() + "!"
            MoodType.NORMAL -> text.replaceFirstChar { it.uppercase() }
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
     * Setup suggestion bar components
     */
    private fun setupSuggestionBar(inputView: View) {
        suggestionScrollView = inputView.findViewById(R.id.suggestion_scroll_view)
        suggestionContainer = inputView.findViewById(R.id.suggestion_container)
    }

    /**
     * Trigger automatic suggestion generation
     */
    private fun triggerAutoSuggestion() {
        // Cancel previous suggestion job
        suggestionJob?.cancel()

        val currentText = currentTypedText.toString().trim()

        // Only show suggestions for words with 2+ characters
        if (currentText.length >= 2) {
            suggestionJob = serviceScope.launch {
                delay(300) // Debounce typing
                generateAutoSuggestions(currentText)
            }
        } else {
            // Hide suggestions for short text
            hideSuggestions()
        }
    }

    /**
     * Generate automatic suggestions based on current mood
     */
    private suspend fun generateAutoSuggestions(text: String) {
        try {
            if (!isAIEnabled || aiTextProcessor == null) {
                // Use local suggestions if AI is not available
                generateLocalSuggestions(text)
                return
            }

            val aiMood = when (currentMood) {
                MoodType.RESPECTFUL -> AITextProcessor.MoodType.RESPECTFUL
                MoodType.FUNNY -> AITextProcessor.MoodType.FUNNY
                MoodType.ANGRY -> AITextProcessor.MoodType.ANGRY
                MoodType.NORMAL -> AITextProcessor.MoodType.NORMAL
            }

            // Generate AI suggestions
            val result = aiTextProcessor?.enhanceText(text, aiMood)
            result?.fold(
                onSuccess = { enhancedText ->
                    // Show AI suggestion
                    showSuggestions(listOf(enhancedText, text)) // Original text as fallback
                },
                onFailure = {
                    // Fallback to local suggestions
                    generateLocalSuggestions(text)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error generating auto suggestions", e)
            generateLocalSuggestions(text)
        }
    }

    /**
     * Generate local suggestions based on mood (AI-powered when possible)
     */
    private fun generateLocalSuggestions(text: String) {
        serviceScope.launch {
            try {
                if (isAIEnabled && aiTextProcessor != null) {
                    // Try AI first even for local suggestions
                    val aiMood = when (currentMood) {
                        MoodType.RESPECTFUL -> AITextProcessor.MoodType.RESPECTFUL
                        MoodType.FUNNY -> AITextProcessor.MoodType.FUNNY
                        MoodType.ANGRY -> AITextProcessor.MoodType.ANGRY
                        MoodType.NORMAL -> AITextProcessor.MoodType.NORMAL
                    }

                    val result = aiTextProcessor?.enhanceText(text, aiMood)
                    result?.fold(
                        onSuccess = { enhancedText ->
                            showSuggestions(listOf(enhancedText, text))
                        },
                        onFailure = {
                            // Only use string manipulation as last resort
                            showFallbackSuggestions(text)
                        }
                    )
                } else {
                    // AI not available, use fallback
                    showFallbackSuggestions(text)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in local suggestions", e)
                showFallbackSuggestions(text)
            }
        }
    }

    /**
     * Show fallback suggestions when AI is not available
     */
    private fun showFallbackSuggestions(text: String) {
        val suggestions = mutableListOf<String>()

        // Add original text
        suggestions.add(text)

        // Add simple mood-based suggestion only as fallback
        val moodSuggestion = when (currentMood) {
            MoodType.RESPECTFUL -> "Please $text"
            MoodType.FUNNY -> "$text 😄"
            MoodType.ANGRY -> text.uppercase() + "!"
            MoodType.NORMAL -> text.replaceFirstChar { it.uppercase() }
        }

        if (moodSuggestion != text) {
            suggestions.add(0, moodSuggestion)
        }

        showSuggestions(suggestions)
    }

    /**
     * Show suggestions in the suggestion bar
     */
    private fun showSuggestions(suggestions: List<String>) {
        runOnUiThread {
            suggestionContainer?.removeAllViews()

            suggestions.take(3).forEach { suggestion -> // Limit to 3 suggestions
                val chipView = createSuggestionChip(suggestion)
                suggestionContainer?.addView(chipView)
            }

            suggestionScrollView?.visibility = View.VISIBLE
        }
    }

    /**
     * Hide suggestions bar
     */
    private fun hideSuggestions() {
        runOnUiThread {
            suggestionScrollView?.visibility = View.GONE
        }
    }

    /**
     * Create a suggestion chip view
     */
    private fun createSuggestionChip(suggestion: String): View {
        val chipView = TextView(this).apply {
            text = suggestion
            textSize = 14f
            setTextColor(Color.WHITE)
            background = ContextCompat.getDrawable(this@MyKeyboardService, R.drawable.suggestion_chip_bg)
            setPadding(24, 12, 24, 12)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            maxWidth = 200 // Limit width

            setOnClickListener {
                applySuggestion(suggestion)
            }
        }

        // Add margin between chips
        val layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = 12
        }
        chipView.layoutParams = layoutParams

        return chipView
    }

    /**
     * Apply selected suggestion
     */
    private fun applySuggestion(suggestion: String) {
        val inputConnection = currentInputConnection ?: return

        try {
            // Delete the current typed text
            inputConnection.deleteSurroundingText(currentTypedText.length, 0)

            // Insert the suggestion
            inputConnection.commitText(suggestion, 1)

            // Clear current typed text and hide suggestions
            currentTypedText.clear()
            hideSuggestions()

            performHapticFeedback()
        } catch (e: Exception) {
            Log.e(TAG, "Error applying suggestion", e)
        }
    }

    /**
     * Run code on UI thread
     */
    private fun runOnUiThread(action: () -> Unit) {
        serviceScope.launch(Dispatchers.Main) {
            action()
        }
    }

    /**
     * Provide haptic feedback for key presses
     */
    fun performHapticFeedback() {
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
