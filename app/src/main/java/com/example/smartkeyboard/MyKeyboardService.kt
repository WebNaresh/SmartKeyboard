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
import android.widget.ImageView
import android.widget.EditText
import android.text.TextWatcher
import android.text.Editable
import android.util.Log
import android.os.Vibrator
import android.os.VibrationEffect
import android.os.Build
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.BroadcastReceiver
import android.graphics.Color
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.smartkeyboard.ai.AITextProcessor
import com.example.smartkeyboard.adapter.KeyboardMoodAdapter
import com.example.smartkeyboard.data.MoodData
import com.example.smartkeyboard.data.CustomMood
import com.example.smartkeyboard.manager.CustomMoodManager
import com.example.smartkeyboard.utils.MoodBroadcastConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import kotlinx.coroutines.withTimeout
import android.view.MotionEvent

@Suppress("DEPRECATION")
class MyKeyboardService : InputMethodService() {

    // Simple keyboard container
    private var keyboardContainer: LinearLayout? = null
    private var isShifted = false

    // Mood selection state
    private var moodButtonsContainer: LinearLayout? = null
    private var moodOverlay: View? = null
    private var rvMoodSelector: RecyclerView? = null
    private var moodSelectorPill: FrameLayout? = null
    private var moodIcon: ImageView? = null
    private var btnEnhanceText: FrameLayout? = null
    private var btnUndo: FrameLayout? = null

    // Modal search components
    private var etSearchMoods: EditText? = null
    private var btnCloseModal: ImageView? = null
    private var btnClearSearch: ImageView? = null

    // Dynamic mood system
    private lateinit var customMoodManager: CustomMoodManager
    private lateinit var keyboardMoodAdapter: KeyboardMoodAdapter
    private var allMoods: List<MoodData> = emptyList()
    private var currentSelectedMood: MoodData? = null

    // Broadcast receiver for real-time mood updates
    private var moodUpdateReceiver: BroadcastReceiver? = null

    // Text tracking for AI enhancement (keeping only for manual enhancement button)
    private var currentTypedText = StringBuilder() // Tracks the full sentence context

    // Undo functionality
    private var undoStack = mutableListOf<String>() // Stack to store previous text states
    private val maxUndoSteps = 10 // Maximum number of undo steps to remember

    // AI integration
    private var aiTextProcessor: AITextProcessor? = null
    private var isAIEnabled: Boolean = false
    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())

    // Keyboard state
    private var moodButtonsVisible = false

    // Long press backspace functionality
    private var backspaceLongPressJob: Job? = null
    private var isBackspaceLongPressed = false

    // Haptic feedback
    private var vibrator: Vibrator? = null

    // Legacy MoodType for backward compatibility with AI system
    enum class MoodType {
        NORMAL, RESPECTFUL, FUNNY, ANGRY
    }



    override fun onCreateInputView(): View {
        val inputView = layoutInflater.inflate(R.layout.keyboard_view, null)

        // Initialize vibrator for haptic feedback
        vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator

        // Initialize dynamic mood system
        customMoodManager = CustomMoodManager(this)

        // Initialize mood buttons
        setupMoodButtons(inputView)

        // Setup simple keyboard buttons
        setupSimpleKeyboard(inputView)

        // Load and setup dynamic moods
        loadAndSetupMoods()

        // Register broadcast receiver for real-time mood updates
        registerMoodUpdateReceiver()

        return inputView
    }

    override fun onDestroy() {
        super.onDestroy()
        // Stop any ongoing long press operations
        stopBackspaceLongPress()
        // Unregister broadcast receiver
        unregisterMoodUpdateReceiver()
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        // Initialize keyboard based on input type if needed
        initializeAIFromSettings()
    }
    
    override fun onStartInputView(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        // Simple keyboard doesn't need special initialization
    }
    


    private fun setupMoodButtons(inputView: View) {
        moodButtonsContainer = inputView.findViewById(R.id.mood_buttons_container)
        moodOverlay = inputView.findViewById(R.id.mood_overlay)
        moodSelectorPill = inputView.findViewById<FrameLayout>(R.id.mood_selector_pill)
        moodIcon = inputView.findViewById<ImageView>(R.id.mood_icon)
        rvMoodSelector = inputView.findViewById<RecyclerView>(R.id.rv_mood_selector)
        btnEnhanceText = inputView.findViewById<FrameLayout>(R.id.btn_enhance_text)
        btnUndo = inputView.findViewById<FrameLayout>(R.id.btn_undo)

        // Initialize search components
        etSearchMoods = inputView.findViewById<EditText>(R.id.et_search_moods)
        btnCloseModal = inputView.findViewById<ImageView>(R.id.btn_close_modal)
        btnClearSearch = inputView.findViewById<ImageView>(R.id.btn_clear_search)

        // Setup RecyclerView for dynamic moods
        rvMoodSelector?.layoutManager = LinearLayoutManager(this)

        // Set click listeners with haptic feedback
        moodSelectorPill?.setOnClickListener {
            performHapticFeedback()
            handleMoodSelectorClick()
        }
        btnEnhanceText?.setOnClickListener {
            performHapticFeedback()
            enhanceCurrentText()
        }
        btnUndo?.setOnClickListener {
            performHapticFeedback()
            performUndo()
        }

        // Setup search functionality
        setupSearchFunctionality()

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

        Log.d(TAG, "Keyboard initialized - AI enabled: $isAIEnabled, AI processor: ${aiTextProcessor != null}")

        updateMoodButtonsUI()
        updateMoodPillAppearance()
    }

    /**
     * Setup simple button-based keyboard to avoid KeyboardView crashes
     */
    private fun setupSimpleKeyboard(inputView: View) {
        keyboardContainer = inputView.findViewById(R.id.keyboard_container)

        // Setup all keyboard buttons
        val keys = mapOf(
            // Numbers
            R.id.key_1 to "1", R.id.key_2 to "2", R.id.key_3 to "3", R.id.key_4 to "4", R.id.key_5 to "5",
            R.id.key_6 to "6", R.id.key_7 to "7", R.id.key_8 to "8", R.id.key_9 to "9", R.id.key_0 to "0",
            // Letters
            R.id.key_q to "q", R.id.key_w to "w", R.id.key_e to "e", R.id.key_r to "r", R.id.key_t to "t",
            R.id.key_y to "y", R.id.key_u to "u", R.id.key_i to "i", R.id.key_o to "o", R.id.key_p to "p",
            R.id.key_a to "a", R.id.key_s to "s", R.id.key_d to "d", R.id.key_f to "f", R.id.key_g to "g",
            R.id.key_h to "h", R.id.key_j to "j", R.id.key_k to "k", R.id.key_l to "l",
            R.id.key_z to "z", R.id.key_x to "x", R.id.key_c to "c", R.id.key_v to "v", R.id.key_b to "b",
            R.id.key_n to "n", R.id.key_m to "m",
            // Symbols
            R.id.key_comma to ",", R.id.key_period to "."
        )

        // Setup character keys
        keys.forEach { (keyId, char) ->
            inputView.findViewById<Button>(keyId)?.setOnClickListener {
                performHapticFeedback()
                val text = if (isShifted && char.matches(Regex("[a-z]"))) char.uppercase() else char
                commitText(text)
            }
        }

        // Setup special keys
        inputView.findViewById<Button>(R.id.key_space)?.setOnClickListener {
            performHapticFeedback()
            commitText(" ")
        }

        // Setup backspace with long press functionality
        val backspaceButton = inputView.findViewById<Button>(R.id.key_backspace)
        backspaceButton?.setOnClickListener {
            if (!isBackspaceLongPressed) {
                performHapticFeedback()
                deleteText()
            }
        }

        // Add long press functionality to backspace
        backspaceButton?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    isBackspaceLongPressed = false
                    startBackspaceLongPress()
                    false // Don't consume the event, let click listener work
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    stopBackspaceLongPress()
                    false // Don't consume the event
                }
                else -> false
            }
        }

        inputView.findViewById<Button>(R.id.key_enter)?.setOnClickListener {
            performHapticFeedback()
            sendKeyChar('\n')
        }

        inputView.findViewById<Button>(R.id.key_shift)?.setOnClickListener {
            performHapticFeedback()
            toggleShift()
        }

        inputView.findViewById<Button>(R.id.key_symbols)?.setOnClickListener {
            performHapticFeedback()
            // TODO: Implement symbols keyboard
        }

        // Initialize keyboard case (start with lowercase)
        updateKeyboardCase()
    }

    private fun commitText(text: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(text, 1)

        // Auto-disable shift after typing a letter (like normal keyboards)
        if (isShifted && text.matches(Regex("[a-zA-Z]"))) {
            isShifted = false
            updateKeyboardCase()
        }

        // Track text for enhancement functionality
        if (text == " ") {
            currentTypedText.append(text)
            Log.d(TAG, "Space pressed, current text: '${currentTypedText}'")
        } else {
            currentTypedText.append(text)
            Log.d(TAG, "Character '$text' added, current text: '${currentTypedText}'")

            // Clear context on sentence-ending punctuation
            if (text == "." || text == "!" || text == "?") {
                Log.d(TAG, "Sentence ending detected, clearing context")
                serviceScope.launch {
                    delay(500)
                    currentTypedText.clear()
                }
            }
        }

        saveTextState()
    }

    private fun deleteText() {
        val ic = currentInputConnection ?: return
        ic.deleteSurroundingText(1, 0)

        // Remove last character from typed text tracking
        if (currentTypedText.isNotEmpty()) {
            currentTypedText.deleteCharAt(currentTypedText.length - 1)
        }

        saveTextState()
    }

    private fun toggleShift() {
        isShifted = !isShifted

        // Update shift key appearance
        keyboardContainer?.findViewById<Button>(R.id.key_shift)?.isSelected = isShifted

        // Update all letter keys to show uppercase/lowercase
        updateKeyboardCase()
    }

    /**
     * Update all letter keys to show uppercase or lowercase based on shift state
     */
    private fun updateKeyboardCase() {
        val letterKeys = mapOf(
            R.id.key_q to "q", R.id.key_w to "w", R.id.key_e to "e", R.id.key_r to "r", R.id.key_t to "t",
            R.id.key_y to "y", R.id.key_u to "u", R.id.key_i to "i", R.id.key_o to "o", R.id.key_p to "p",
            R.id.key_a to "a", R.id.key_s to "s", R.id.key_d to "d", R.id.key_f to "f", R.id.key_g to "g",
            R.id.key_h to "h", R.id.key_j to "j", R.id.key_k to "k", R.id.key_l to "l",
            R.id.key_z to "z", R.id.key_x to "x", R.id.key_c to "c", R.id.key_v to "v", R.id.key_b to "b",
            R.id.key_n to "n", R.id.key_m to "m"
        )

        letterKeys.forEach { (keyId, baseLetter) ->
            keyboardContainer?.findViewById<Button>(keyId)?.text =
                if (isShifted) baseLetter.uppercase() else baseLetter.lowercase()
        }
    }

    /**
     * Start long press backspace functionality with optimized timing
     */
    private fun startBackspaceLongPress() {
        // Cancel any existing long press job
        stopBackspaceLongPress()

        backspaceLongPressJob = serviceScope.launch {
            try {
                // Wait for optimized long press threshold (300ms)
                delay(300)

                // Mark as long pressed to prevent single tap action
                isBackspaceLongPressed = true

                // Save text state before starting continuous deletion
                saveTextState()

                // Start with optimized deletion intervals (50ms)
                var deletionInterval = 50L
                val startTime = System.currentTimeMillis()

                while (backspaceLongPressJob?.isActive == true && isBackspaceLongPressed) {
                    // Perform deletion with haptic feedback (25ms vibration)
                    performHapticFeedback()
                    deleteText()

                    // Progressive acceleration based on elapsed time
                    val elapsedTime = System.currentTimeMillis() - startTime
                    deletionInterval = when {
                        elapsedTime > 2000 -> 15L  // Maximum speed after 2 seconds
                        elapsedTime > 1000 -> 25L  // Faster after 1 second
                        else -> 50L                // Initial speed for first second
                    }

                    // Wait for next deletion
                    delay(deletionInterval)
                }
            } catch (e: Exception) {
                Log.d(TAG, "Long press backspace cancelled or interrupted")
            }
        }
    }

    /**
     * Stop long press backspace functionality
     */
    private fun stopBackspaceLongPress() {
        backspaceLongPressJob?.cancel()
        backspaceLongPressJob = null
        isBackspaceLongPressed = false
    }

    /**
     * Load and setup dynamic moods (custom moods only)
     */
    private fun loadAndSetupMoods() {
        Log.d(TAG, "Loading and setting up dynamic moods...")

        // Get custom moods from storage
        val customMoods = customMoodManager.getCustomMoods()
        Log.d(TAG, "Retrieved ${customMoods.size} custom moods from storage")

        // Get all moods (custom moods only)
        allMoods = MoodData.getAllMoods(customMoods)

        // Set first custom mood as selected, or null if no custom moods exist
        currentSelectedMood = allMoods.firstOrNull()
        Log.d(TAG, "Selected initial mood: ${currentSelectedMood?.title ?: "none"}")

        // Setup adapter
        keyboardMoodAdapter = KeyboardMoodAdapter(
            allMoods = allMoods,
            selectedMoodId = currentSelectedMood?.id ?: "",
            onMoodClick = { mood -> selectDynamicMood(mood) }
        )

        rvMoodSelector?.adapter = keyboardMoodAdapter
        Log.d(TAG, "Keyboard mood adapter initialized")

        // Update pill appearance
        updateMoodPillAppearance()

        Log.d(TAG, "Successfully loaded ${allMoods.size} custom moods")

        // Force a refresh from storage to ensure we have the latest data
        serviceScope.launch {
            delay(100) // Small delay to ensure everything is initialized
            reloadMoodsFromStorage()
        }
    }

    /**
     * Select a dynamic mood (default or custom)
     */
    private fun selectDynamicMood(mood: MoodData) {
        performHapticFeedback()
        currentSelectedMood = mood

        // Update adapter selection
        keyboardMoodAdapter.updateSelectedMood(mood.id)

        // Update pill appearance
        updateMoodPillAppearance()

        // Hide mood menu after selection
        if (moodButtonsVisible) {
            toggleMoodButtonsVisibility()
        }

        Log.d(TAG, "Selected mood: ${mood.title} (${mood.id})")
    }

    /**
     * Handle mood selector pill click with validation
     */
    private fun handleMoodSelectorClick() {
        // Check if there are any custom moods available
        if (allMoods.isEmpty()) {
            // Show toast message prompting user to create moods
            showCreateMoodPrompt()
        } else {
            // Show mood selector dropdown
            toggleMoodButtonsVisibility()
        }
    }

    /**
     * Show prompt to create first custom mood
     */
    private fun showCreateMoodPrompt() {
        try {
            // Show toast message
            android.widget.Toast.makeText(
                this,
                "Please create your first custom mood in Settings",
                android.widget.Toast.LENGTH_LONG
            ).show()

            // Optional: Try to open settings directly
            openKeyboardSettings()

            Log.d(TAG, "Prompted user to create custom moods - no moods available")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing create mood prompt", e)
        }
    }

    /**
     * Attempt to open keyboard settings
     */
    private fun openKeyboardSettings() {
        try {
            val intent = android.content.Intent(this, SettingsActivity::class.java).apply {
                flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(intent)
            Log.d(TAG, "Opened keyboard settings")
        } catch (e: Exception) {
            Log.w(TAG, "Could not open settings directly", e)
            // Fallback: Show additional guidance
            android.widget.Toast.makeText(
                this,
                "Open Smart Keyboard Settings to create custom moods",
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    }

    // Legacy method - kept for backward compatibility but not used
    private fun selectMood(mood: MoodType) {
        // This method is deprecated - use selectDynamicMood instead
        Log.d(TAG, "Legacy selectMood called with: $mood")
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

    // Legacy method - no longer needed with dynamic mood system
    private fun updateMoodButtonsUI() {
        // This method is deprecated - mood highlighting is now handled by the RecyclerView adapter
        Log.d(TAG, "Legacy updateMoodButtonsUI called")
    }

    private fun applyMoodTransformation(text: String): String {
        // Use dynamic mood transformation with safety check
        if (text.isEmpty()) return text

        val selectedMood = currentSelectedMood
        return if (selectedMood != null) {
            applyDynamicMoodTransformation(text, selectedMood)
        } else {
            try {
                text.replaceFirstChar { it.uppercase() }
            } catch (e: Exception) {
                Log.w(TAG, "Error applying mood transformation", e)
                text
            }
        }
    }

    // Removed old mood transformation methods - no longer needed with custom moods only

    /**
     * Apply mood transformation for custom moods (simple fallback)
     */
    private fun applyDynamicMoodTransformation(text: String, mood: MoodData): String {
        // For custom moods, just capitalize as a simple fallback
        // Real enhancement should use AI with custom instructions
        if (text.isEmpty()) return text

        return try {
            text.replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            Log.w(TAG, "Error applying dynamic mood transformation", e)
            text
        }
    }

    private fun updateMoodPillAppearance() {
        // Update mood icon in the pill based on selected custom mood or empty state
        val moodIconResource = when {
            allMoods.isEmpty() -> R.drawable.ic_mood // Default mood icon when no moods
            currentSelectedMood != null -> {
                // Map mood names to specific icons
                when (currentSelectedMood!!.title.lowercase()) {
                    "respectful" -> R.drawable.ic_mood_respectful
                    "funny" -> R.drawable.ic_mood_funny
                    "angry" -> R.drawable.ic_mood_angry
                    else -> R.drawable.ic_mood // Default mood icon for custom moods
                }
            }
            else -> R.drawable.ic_mood // Default when moods exist but none selected
        }
        moodIcon?.setImageResource(moodIconResource)

        // Highlight pill if a mood is selected, dim if no moods available
        moodSelectorPill?.isSelected = currentSelectedMood != null && allMoods.isNotEmpty()

        // Set alpha to indicate state
        moodSelectorPill?.alpha = if (allMoods.isEmpty()) 0.6f else 1.0f
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
     * Enhance text using AI with dynamic mood support
     */
    private fun enhanceTextWithAI(text: String) {
        serviceScope.launch {
            try {
                val selectedMood = currentSelectedMood

                if (selectedMood != null && selectedMood.instructions != null) {
                    // All moods are now custom moods with instructions
                    Log.d(TAG, "Enhancing text with custom mood: '${selectedMood.title}' - Instructions: '${selectedMood.instructions}'")
                    val result = aiTextProcessor?.enhanceTextWithCustomInstructions(text, selectedMood.instructions!!)
                    result?.fold(
                        onSuccess = { enhancedText ->
                            replaceAllTextWithEnhanced(enhancedText)
                        },
                        onFailure = { error ->
                            Log.w(TAG, "Custom mood AI enhancement failed, using fallback", error)
                            val fallbackText = if (text.isEmpty()) text else {
                                try {
                                    text.replaceFirstChar { it.uppercase() }
                                } catch (e: Exception) {
                                    Log.w(TAG, "Error in fallback text transformation", e)
                                    text
                                }
                            }
                            replaceAllTextWithEnhanced(fallbackText)
                        }
                    )
                } else {
                    // No mood selected or no instructions, use simple enhancement
                    Log.w(TAG, "No custom mood selected or no instructions, using simple fallback")
                    val fallbackText = if (text.isEmpty()) text else {
                        try {
                            text.replaceFirstChar { it.uppercase() }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error in simple enhancement fallback", e)
                            text
                        }
                    }
                    replaceAllTextWithEnhanced(fallbackText)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in AI enhancement", e)
                val fallbackText = if (text.isEmpty()) text else {
                    try {
                        text.replaceFirstChar { it.uppercase() }
                    } catch (e2: Exception) {
                        Log.w(TAG, "Error in exception fallback text transformation", e2)
                        text
                    }
                }
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

            // Save text state before enhancement (for undo functionality)
            saveTextState()

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
     * Enhance text using local transformations (fallback when AI fails)
     */
    private fun enhanceTextLocally(text: String): String {
        // Since all moods are now custom, just provide basic enhancement
        if (text.isEmpty()) return text

        return try {
            text.replaceFirstChar { it.uppercase() }
        } catch (e: Exception) {
            Log.w(TAG, "Error enhancing text locally", e)
            text
        }
    }



    /**
     * Save current text state for undo functionality
     */
    private fun saveTextState() {
        try {
            val inputConnection = currentInputConnection ?: return

            // Get current text from the input field
            val currentText = inputConnection.getTextBeforeCursor(1000, 0)?.toString() ?: ""

            // Add to undo stack if it's different from the last saved state
            if (undoStack.isEmpty() || undoStack.last() != currentText) {
                undoStack.add(currentText)

                // Limit the undo stack size
                if (undoStack.size > maxUndoSteps) {
                    undoStack.removeAt(0)
                }

                Log.d(TAG, "Saved text state for undo: '${currentText.take(50)}...' (${undoStack.size} states)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving text state for undo", e)
        }
    }

    /**
     * Perform undo operation
     */
    private fun performUndo() {
        try {
            val inputConnection = currentInputConnection ?: return

            if (undoStack.isEmpty()) {
                Log.d(TAG, "No undo states available")
                return
            }

            // Get the previous text state
            val previousText = undoStack.removeLastOrNull() ?: return

            // Get current text to compare
            val currentText = inputConnection.getTextBeforeCursor(1000, 0)?.toString() ?: ""

            // If current text is the same as previous, try the next one
            val textToRestore = if (currentText == previousText && undoStack.isNotEmpty()) {
                undoStack.removeLastOrNull() ?: previousText
            } else {
                previousText
            }

            // Clear current text and insert the previous state
            inputConnection.deleteSurroundingText(currentText.length, 0)
            inputConnection.commitText(textToRestore, 1)

            // Update current typed text for AI enhancement
            currentTypedText.clear()
            currentTypedText.append(textToRestore)

            Log.d(TAG, "Undo performed: restored '${textToRestore.take(50)}...' (${undoStack.size} states remaining)")

        } catch (e: Exception) {
            Log.e(TAG, "Error performing undo", e)
        }
    }

    /**
     * Setup search functionality for mood modal
     */
    private fun setupSearchFunctionality() {
        // Close modal button
        btnCloseModal?.setOnClickListener {
            performHapticFeedback()
            if (moodButtonsVisible) {
                toggleMoodButtonsVisibility()
            }
        }

        // Clear search button
        btnClearSearch?.setOnClickListener {
            performHapticFeedback()
            etSearchMoods?.setText("")
        }

        // Search text watcher
        etSearchMoods?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString() ?: ""

                // Show/hide clear button based on text
                btnClearSearch?.visibility = if (query.isEmpty()) View.GONE else View.VISIBLE

                // Filter moods
                if (::keyboardMoodAdapter.isInitialized) {
                    keyboardMoodAdapter.filter(query)
                }

                Log.d(TAG, "Search query: '$query'")
            }

            override fun afterTextChanged(s: Editable?) {}
        })
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

    /**
     * Register broadcast receiver for real-time mood updates
     */
    private fun registerMoodUpdateReceiver() {
        if (moodUpdateReceiver == null) {
            Log.d(TAG, "Registering mood update broadcast receiver...")

            moodUpdateReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    Log.d(TAG, "Broadcast received: ${intent?.action}")

                    when (intent?.action) {
                        MoodBroadcastConstants.ACTION_MOOD_CREATED -> {
                            val moodId = intent.getStringExtra(MoodBroadcastConstants.EXTRA_MOOD_ID)
                            val moodTitle = intent.getStringExtra(MoodBroadcastConstants.EXTRA_MOOD_TITLE)
                            Log.d(TAG, "Received mood created broadcast: $moodTitle ($moodId)")
                            handleMoodCreated(moodId, moodTitle)
                        }
                        MoodBroadcastConstants.ACTION_MOOD_UPDATED -> {
                            val newMoodId = intent.getStringExtra(MoodBroadcastConstants.EXTRA_MOOD_ID)
                            val oldMoodId = intent.getStringExtra(MoodBroadcastConstants.EXTRA_OLD_MOOD_ID)
                            val moodTitle = intent.getStringExtra(MoodBroadcastConstants.EXTRA_MOOD_TITLE)
                            Log.d(TAG, "Received mood updated broadcast: $moodTitle ($oldMoodId -> $newMoodId)")
                            handleMoodUpdated(oldMoodId, newMoodId, moodTitle)
                        }
                        MoodBroadcastConstants.ACTION_MOOD_DELETED -> {
                            val moodId = intent.getStringExtra(MoodBroadcastConstants.EXTRA_MOOD_ID)
                            val moodTitle = intent.getStringExtra(MoodBroadcastConstants.EXTRA_MOOD_TITLE)
                            Log.d(TAG, "Received mood deleted broadcast: $moodTitle ($moodId)")
                            handleMoodDeleted(moodId, moodTitle)
                        }
                        MoodBroadcastConstants.ACTION_MOODS_REFRESHED -> {
                            Log.d(TAG, "Received moods refresh broadcast")
                            reloadMoodsFromStorage()
                        }
                        else -> {
                            Log.w(TAG, "Received unknown broadcast action: ${intent?.action}")
                        }
                    }
                }
            }

            val intentFilter = IntentFilter().apply {
                addAction(MoodBroadcastConstants.ACTION_MOOD_CREATED)
                addAction(MoodBroadcastConstants.ACTION_MOOD_UPDATED)
                addAction(MoodBroadcastConstants.ACTION_MOOD_DELETED)
                addAction(MoodBroadcastConstants.ACTION_MOODS_REFRESHED)
            }

            // Use RECEIVER_NOT_EXPORTED for internal app communication (Android 14+ requirement)
            ContextCompat.registerReceiver(
                this@MyKeyboardService,
                moodUpdateReceiver,
                intentFilter,
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
            Log.d(TAG, "Mood update receiver registered")
        }
    }

    /**
     * Unregister broadcast receiver
     */
    private fun unregisterMoodUpdateReceiver() {
        moodUpdateReceiver?.let {
            try {
                unregisterReceiver(it)
                Log.d(TAG, "Mood update receiver unregistered")
            } catch (e: Exception) {
                Log.w(TAG, "Error unregistering mood update receiver", e)
            }
            moodUpdateReceiver = null
        }
    }

    /**
     * Handle mood created event
     */
    private fun handleMoodCreated(moodId: String?, moodTitle: String?) {
        Log.d(TAG, "Handling mood created event: $moodTitle ($moodId)")

        if (moodId != null && moodTitle != null) {
            // Reload moods to include the new one
            reloadMoodsFromStorage()

            // If this is the first mood and no mood is currently selected, select it
            if (currentSelectedMood == null && allMoods.isNotEmpty()) {
                val newMood = allMoods.find { it.id == moodId }
                if (newMood != null) {
                    currentSelectedMood = newMood
                    Log.d(TAG, "Auto-selected first mood: $moodTitle")

                    // Update adapter selection
                    if (::keyboardMoodAdapter.isInitialized) {
                        keyboardMoodAdapter.updateSelectedMood(moodId)
                    }

                    // Update pill appearance
                    updateMoodPillAppearance()
                }
            }

            Log.d(TAG, "Successfully handled mood created: $moodTitle")
        } else {
            Log.w(TAG, "Invalid mood created event - missing ID or title")
        }
    }

    /**
     * Handle mood updated event
     */
    private fun handleMoodUpdated(oldMoodId: String?, newMoodId: String?, moodTitle: String?) {
        if (oldMoodId != null && newMoodId != null && moodTitle != null) {
            // Check if the currently selected mood was updated
            if (currentSelectedMood?.id == oldMoodId) {
                // Update the current selection to the new mood ID
                val updatedMood = allMoods.find { it.id == newMoodId }
                currentSelectedMood = updatedMood
                updateMoodPillAppearance()
                Log.d(TAG, "Updated currently selected mood: $moodTitle")
            }

            // Reload moods to reflect the changes
            reloadMoodsFromStorage()
            Log.d(TAG, "Mood updated: $moodTitle")
        }
    }

    /**
     * Handle mood deleted event
     */
    private fun handleMoodDeleted(moodId: String?, moodTitle: String?) {
        if (moodId != null && moodTitle != null) {
            // Check if the currently selected mood was deleted
            if (currentSelectedMood?.id == moodId) {
                // Switch to the first available mood or null if none exist
                val remainingMoods = allMoods.filter { it.id != moodId }
                currentSelectedMood = remainingMoods.firstOrNull()
                updateMoodPillAppearance()
                Log.d(TAG, "Deleted currently selected mood, switched to: ${currentSelectedMood?.title ?: "none"}")
            }

            // Reload moods to remove the deleted one
            reloadMoodsFromStorage()
            Log.d(TAG, "Mood deleted: $moodTitle")
        }
    }

    /**
     * Reload moods from storage and update UI
     */
    private fun reloadMoodsFromStorage() {
        try {
            Log.d(TAG, "Starting mood reload from storage...")

            // Get updated custom moods from storage
            val customMoods = customMoodManager.getCustomMoods()
            Log.d(TAG, "Retrieved ${customMoods.size} custom moods from storage")

            // Update all moods list
            val oldMoodCount = allMoods.size
            allMoods = MoodData.getAllMoods(customMoods)
            Log.d(TAG, "Updated allMoods: $oldMoodCount -> ${allMoods.size} moods")

            // Ensure UI updates happen on main thread
            serviceScope.launch(Dispatchers.Main) {
                try {
                    // Update adapter
                    if (::keyboardMoodAdapter.isInitialized) {
                        Log.d(TAG, "Updating keyboard mood adapter...")
                        keyboardMoodAdapter.updateMoods(allMoods)

                        // Validate current selection still exists
                        val currentMoodStillExists = currentSelectedMood?.let { mood ->
                            allMoods.any { it.id == mood.id }
                        } ?: false

                        if (!currentMoodStillExists && allMoods.isNotEmpty()) {
                            // Select first mood if current selection is invalid
                            currentSelectedMood = allMoods.first()
                            Log.d(TAG, "Current mood no longer exists, selected first available: ${currentSelectedMood?.title}")
                        }

                        keyboardMoodAdapter.updateSelectedMood(currentSelectedMood?.id ?: "")
                        Log.d(TAG, "Adapter updated successfully")
                    } else {
                        Log.w(TAG, "KeyboardMoodAdapter not initialized yet")
                    }

                    // Update pill appearance
                    updateMoodPillAppearance()
                    Log.d(TAG, "Mood pill appearance updated")

                } catch (e: Exception) {
                    Log.e(TAG, "Error updating UI during mood reload", e)
                }
            }

            Log.d(TAG, "Successfully reloaded ${allMoods.size} moods from storage")
        } catch (e: Exception) {
            Log.e(TAG, "Error reloading moods from storage", e)
        }
    }

    /**
     * Public method to test if the keyboard service is receiving broadcasts
     * This can be called from external components to verify connectivity
     */
    fun testBroadcastReceiver() {
        Log.d(TAG, "Broadcast receiver test - Service is active and listening")
        reloadMoodsFromStorage()
    }

    companion object {
        private const val TAG = "MyKeyboardService"
    }
}
