package com.example.smartkeyboard

import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.LinearLayout
import android.util.Log
import com.example.smartkeyboard.ai.AITextProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MyKeyboardService : InputMethodService(), KeyboardView.OnKeyboardActionListener {

    private var keyboardView: KeyboardView? = null
    private var keyboard: Keyboard? = null

    // Mood selection state
    private var currentMood: MoodType = MoodType.NORMAL
    private var moodButtonsContainer: LinearLayout? = null
    private var btnRespectful: Button? = null
    private var btnFunny: Button? = null
    private var btnAngry: Button? = null
    private var btnToggleMood: Button? = null
    private var btnAI: Button? = null
    private var moodButtonsVisible: Boolean = false

    // AI integration
    private var aiTextProcessor: AITextProcessor? = null
    private var isAIEnabled: Boolean = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    enum class MoodType {
        NORMAL, RESPECTFUL, FUNNY, ANGRY
    }
    
    override fun onCreateInputView(): View {
        val inputView = layoutInflater.inflate(R.layout.keyboard_view, null)
        keyboardView = inputView.findViewById(R.id.keyboard_view)
        keyboard = Keyboard(this, R.xml.qwerty)
        keyboardView?.keyboard = keyboard
        keyboardView?.setOnKeyboardActionListener(this)

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
        // Called when a key is pressed
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
                    val character = primaryCode.toChar().toString()
                    handleTextInput(character)
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
            val shifted = keyboard!!.isShifted
            keyboard!!.isShifted = !shifted
            keyboardView?.invalidateAllKeys()
        }
    }

    private fun setupMoodButtons(inputView: View) {
        moodButtonsContainer = inputView.findViewById(R.id.mood_buttons_container)
        btnRespectful = inputView.findViewById(R.id.btn_respectful)
        btnFunny = inputView.findViewById(R.id.btn_funny)
        btnAngry = inputView.findViewById(R.id.btn_angry)
        btnToggleMood = inputView.findViewById(R.id.btn_toggle_mood)
        btnAI = inputView.findViewById(R.id.btn_ai_toggle)

        // Set click listeners
        btnRespectful?.setOnClickListener { selectMood(MoodType.RESPECTFUL) }
        btnFunny?.setOnClickListener { selectMood(MoodType.FUNNY) }
        btnAngry?.setOnClickListener { selectMood(MoodType.ANGRY) }
        btnToggleMood?.setOnClickListener { toggleMoodButtonsVisibility() }
        btnAI?.setOnClickListener { toggleAI() }

        // Initially hide mood buttons
        moodButtonsContainer?.visibility = View.GONE
        moodButtonsVisible = false

        updateMoodButtonsUI()
        updateToggleButtonAppearance()
        updateAIButtonAppearance()
    }

    private fun selectMood(mood: MoodType) {
        currentMood = mood
        updateMoodButtonsUI()
        updateToggleButtonAppearance()
    }

    private fun toggleMoodButtonsVisibility() {
        moodButtonsVisible = !moodButtonsVisible
        moodButtonsContainer?.visibility = if (moodButtonsVisible) View.VISIBLE else View.GONE

        // Update toggle button appearance
        updateToggleButtonAppearance()
    }

    private fun updateMoodButtonsUI() {
        // Reset all buttons to normal state
        btnRespectful?.setBackgroundResource(R.drawable.key_bg)
        btnFunny?.setBackgroundResource(R.drawable.key_bg)
        btnAngry?.setBackgroundResource(R.drawable.key_bg)

        // Highlight selected mood
        when (currentMood) {
            MoodType.RESPECTFUL -> btnRespectful?.setBackgroundResource(R.drawable.key_bg_selected)
            MoodType.FUNNY -> btnFunny?.setBackgroundResource(R.drawable.key_bg_selected)
            MoodType.ANGRY -> btnAngry?.setBackgroundResource(R.drawable.key_bg_selected)
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

    private fun updateToggleButtonAppearance() {
        // Update toggle button based on current mood and visibility
        val moodEmoji = when (currentMood) {
            MoodType.RESPECTFUL -> "🙏"
            MoodType.FUNNY -> "😄"
            MoodType.ANGRY -> "😠"
            MoodType.NORMAL -> "🎭"
        }

        btnToggleMood?.text = moodEmoji

        // Highlight toggle button if a mood is selected
        if (currentMood != MoodType.NORMAL) {
            btnToggleMood?.setBackgroundResource(R.drawable.key_bg_selected)
        } else {
            btnToggleMood?.setBackgroundResource(R.drawable.key_bg)
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
     * Toggle AI functionality
     */
    private fun toggleAI() {
        if (aiTextProcessor == null) {
            // AI not initialized, try to load from settings
            initializeAIFromSettings()
            if (aiTextProcessor == null) {
                Log.w(TAG, "AI not initialized. Please set API key in settings first.")
                return
            }
        }

        isAIEnabled = !isAIEnabled
        Log.d(TAG, "AI ${if (isAIEnabled) "enabled" else "disabled"}")
        updateAIButtonAppearance()
    }

    /**
     * Update AI button appearance
     */
    private fun updateAIButtonAppearance() {
        if (isAIEnabled && aiTextProcessor != null) {
            btnAI?.setBackgroundResource(R.drawable.key_bg_selected)
            btnAI?.text = "🤖✨"
        } else {
            btnAI?.setBackgroundResource(R.drawable.key_bg)
            btnAI?.text = "🤖"
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
                        commitText(enhancedText)
                    },
                    onFailure = { error ->
                        Log.w(TAG, "AI enhancement failed, using local transformation", error)
                        val fallbackText = applyMoodTransformation(text)
                        commitText(fallbackText)
                    }
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error in AI enhancement", e)
                val fallbackText = applyMoodTransformation(text)
                commitText(fallbackText)
            }
        }
    }

    /**
     * Commit text to input connection
     */
    private fun commitText(text: String) {
        val inputConnection = currentInputConnection
        inputConnection?.commitText(text, 1)
    }

    companion object {
        private const val TAG = "MyKeyboardService"
    }
}
