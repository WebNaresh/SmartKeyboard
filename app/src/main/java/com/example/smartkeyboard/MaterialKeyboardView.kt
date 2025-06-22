package com.example.smartkeyboard

import android.content.Context
import android.graphics.*
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

@Suppress("DEPRECATION")
class MaterialKeyboardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : KeyboardView(context, attrs, defStyleAttr) {

    private val whatsappBlue = Color.parseColor("#128C7E")
    private val whatsappBlueDark = Color.parseColor("#0F7A6E")
    private val actionButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = whatsappBlue
        style = Paint.Style.FILL
    }
    private val actionButtonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1A9C8E")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
        strokeWidth = 3f
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    // Space key flat design paints
    private val spaceKeyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#1C1C1C")
        style = Paint.Style.FILL
    }
    private val spaceKeyStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A2A")
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    private val spaceKeyPressedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2A2A2A")
        style = Paint.Style.FILL
    }

    private var actionButtonPressed = false
    private var actionButtonScale = 1.0f
    private var spaceKeyPressed = false

    // Long press functionality
    private val longPressHandler = Handler(Looper.getMainLooper())
    private var longPressRunnable: Runnable? = null
    private var isLongPressTriggered = false
    private var downKey: Keyboard.Key? = null
    private val longPressDelay = 500L // 500ms for long press
    private var isLongPressKey = false

    // Number mapping for QWERTYUIOP -> 1234567890
    private val numberMap = mapOf(
        113 to 49,  // q -> 1
        119 to 50,  // w -> 2
        101 to 51,  // e -> 3
        114 to 52,  // r -> 4
        116 to 53,  // t -> 5
        121 to 54,  // y -> 6
        117 to 55,  // u -> 7
        105 to 56,  // i -> 8
        111 to 57,  // o -> 9
        112 to 48   // p -> 0
    )

    @Deprecated("Using deprecated KeyboardView API")
    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // drawCustomSpaceKey(canvas) // Temporarily disabled to fix black rectangle
        // drawCustomActionButton(canvas) // Disabled to remove green enter icon
        drawNumberIndicators(canvas)
    }

    private fun drawCustomSpaceKey(canvas: Canvas) {
        val keyboard = keyboard ?: return
        val keys = keyboard.keys

        // Find the space key (code 32)
        val spaceKey = keys.find { it.codes[0] == 32 } ?: return

        // Calculate flat rectangle bounds with padding
        val padding = 8f
        val left = spaceKey.x + padding
        val top = spaceKey.y + padding
        val right = spaceKey.x + spaceKey.width - padding
        val bottom = spaceKey.y + spaceKey.height - padding
        val cornerRadius = 8f

        // Create rounded rectangle path
        val path = Path().apply {
            addRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, Path.Direction.CW)
        }

        // Draw flat background
        val fillPaint = if (spaceKeyPressed) spaceKeyPressedPaint else spaceKeyPaint
        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, spaceKeyStrokePaint)
    }

    private fun drawCustomActionButton(canvas: Canvas) {
        val keyboard = keyboard ?: return
        val keys = keyboard.keys

        // Find the enter/action key (code -4)
        val actionKey = keys.find { it.codes[0] == -4 } ?: return

        // Calculate button position and size
        val centerX = actionKey.x + actionKey.width / 2f
        val centerY = actionKey.y + actionKey.height / 2f
        val radius = (minOf(actionKey.width, actionKey.height) * 0.35f * actionButtonScale)

        // Draw the circular background
        actionButtonPaint.color = if (actionButtonPressed) whatsappBlueDark else whatsappBlue
        canvas.drawCircle(centerX, centerY, radius, actionButtonPaint)
        canvas.drawCircle(centerX, centerY, radius, actionButtonStrokePaint)

        // Draw the enter arrow icon
        drawEnterIcon(canvas, centerX, centerY, radius * 0.5f)
    }

    private fun drawEnterIcon(canvas: Canvas, centerX: Float, centerY: Float, size: Float) {
        val path = Path().apply {
            // Draw a curved enter arrow
            moveTo(centerX - size * 0.3f, centerY - size * 0.2f)
            lineTo(centerX + size * 0.3f, centerY - size * 0.2f)
            lineTo(centerX + size * 0.1f, centerY - size * 0.4f)
            moveTo(centerX + size * 0.3f, centerY - size * 0.2f)
            lineTo(centerX + size * 0.1f, centerY)
            moveTo(centerX + size * 0.3f, centerY - size * 0.2f)
            lineTo(centerX + size * 0.3f, centerY + size * 0.3f)
            lineTo(centerX - size * 0.3f, centerY + size * 0.3f)
        }

        iconPaint.style = Paint.Style.STROKE
        canvas.drawPath(path, iconPaint)
    }

    private fun drawNumberIndicators(canvas: Canvas) {
        val keyboard = keyboard ?: return
        val keys = keyboard.keys

        // Paint for number indicators
        val numberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#AAAAAA") // Lighter gray for better visibility
            textSize = 20f // Smaller text size
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        // Number mapping for display
        val numberLabels = mapOf(
            113 to "1", // q -> 1
            119 to "2", // w -> 2
            101 to "3", // e -> 3
            114 to "4", // r -> 4
            116 to "5", // t -> 5
            121 to "6", // y -> 6
            117 to "7", // u -> 7
            105 to "8", // i -> 8
            111 to "9", // o -> 9
            112 to "0"  // p -> 0
        )

        // Draw number indicators on top row keys
        keys.forEach { key ->
            val primaryCode = key.codes[0]
            val numberLabel = numberLabels[primaryCode]

            if (numberLabel != null) {
                // Position the number in the bottom-right area of the key
                val numberX = key.x + key.width - 16f
                val numberY = key.y + key.height - 8f

                canvas.drawText(numberLabel, numberX, numberY, numberPaint)
            }
        }
    }

    @Deprecated("Using deprecated KeyboardView API")
    override fun onTouchEvent(me: MotionEvent?): Boolean {
        me?.let { event ->
            val keyboard = keyboard ?: return super.onTouchEvent(me)
            val x = event.x.toInt()
            val y = event.y.toInt()

            // Check if this is the space key
            isSpaceKeyPressed = isSpaceKey(x, y)

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // If this is the space key, handle it specially to disable preview
                    if (isSpaceKeyPressed) {
                        // Temporarily disable preview for space key and spacer keys
                        try {
                            val setPreviewEnabledMethod = javaClass.superclass?.getDeclaredMethod("setPreviewEnabled", Boolean::class.java)
                            setPreviewEnabledMethod?.isAccessible = true
                            setPreviewEnabledMethod?.invoke(this, false)
                        } catch (e: Exception) {
                            // Ignore if method not available
                        }
                    }

                    // Find which key was pressed
                    val pressedKey = keyboard.keys.find { key ->
                        x >= key.x && x <= key.x + key.width &&
                        y >= key.y && y <= key.y + key.height
                    }

                    pressedKey?.let { key ->
                        downKey = key
                        isLongPressTriggered = false
                        isLongPressKey = numberMap.containsKey(key.codes[0])

                        // Check if this is a spacer key and disable preview
                        val isSpacerKey = key.codes.isNotEmpty() && key.codes[0] == -1000
                        if (isSpacerKey) {
                            try {
                                val setPreviewEnabledMethod = javaClass.superclass?.getDeclaredMethod("setPreviewEnabled", Boolean::class.java)
                                setPreviewEnabledMethod?.isAccessible = true
                                setPreviewEnabledMethod?.invoke(this, false)
                            } catch (e: Exception) {
                                // Ignore if method not available
                            }
                        }

                        // Check if this is a top row key that supports long press for numbers
                        if (isLongPressKey) {
                            // Start long press timer
                            longPressRunnable = Runnable {
                                handleLongPress(key)
                            }
                            longPressHandler.postDelayed(longPressRunnable!!, longPressDelay)
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Check if this was a spacer key
                    val wasSpacerKey = downKey?.codes?.isNotEmpty() == true && downKey!!.codes[0] == -1000

                    // Re-enable preview if it was disabled for space key or spacer key
                    if (isSpaceKeyPressed || wasSpacerKey) {
                        try {
                            val setPreviewEnabledMethod = javaClass.superclass?.getDeclaredMethod("setPreviewEnabled", Boolean::class.java)
                            setPreviewEnabledMethod?.isAccessible = true
                            setPreviewEnabledMethod?.invoke(this, true)
                        } catch (e: Exception) {
                            // Ignore if method not available
                        }
                        isSpaceKeyPressed = false
                    }

                    // Cancel long press timer
                    longPressRunnable?.let { runnable ->
                        longPressHandler.removeCallbacks(runnable)
                        longPressRunnable = null
                    }

                    // If long press was triggered, consume the event
                    if (isLongPressTriggered) {
                        isLongPressTriggered = false
                        downKey = null
                        isLongPressKey = false

                        // Aggressively dismiss the preview popup
                        dismissKeyPreview()

                        return true // Consume the event to prevent normal key processing
                    }

                    // If this was a long press key but long press wasn't triggered, send normal key event
                    if (isLongPressKey) {
                        downKey?.let { key ->
                            // Send normal key press event
                            val keyboardService = context as? MyKeyboardService
                            keyboardService?.let { service ->
                                service.onKey(key.codes[0], key.codes)
                            }
                        }
                        downKey = null
                        isLongPressKey = false
                        return true
                    }

                    downKey = null
                    isLongPressKey = false
                }

                MotionEvent.ACTION_MOVE -> {
                    // Check if finger moved outside the key bounds
                    downKey?.let { key ->
                        if (x < key.x || x > key.x + key.width ||
                            y < key.y || y > key.y + key.height) {
                            // Cancel long press if finger moved outside
                            longPressRunnable?.let { runnable ->
                                longPressHandler.removeCallbacks(runnable)
                                longPressRunnable = null
                            }
                        }
                    }
                }
            }
        }

        // Only call super for non-long-press keys
        return if (isLongPressKey) true else super.onTouchEvent(me)
    }

    private fun handleLongPress(key: Keyboard.Key) {
        val primaryCode = key.codes[0]
        val numberCode = numberMap[primaryCode]

        if (numberCode != null) {
            isLongPressTriggered = true

            // Immediately dismiss the key preview popup
            dismissKeyPreview()

            // Get the keyboard service to input the number
            val keyboardService = context as? MyKeyboardService
            keyboardService?.let { service ->
                val inputConnection = service.currentInputConnection
                inputConnection?.commitText(numberCode.toChar().toString(), 1)

                // Provide haptic feedback
                service.performHapticFeedback()
            }
        }
    }

    /**
     * Override to disable key preview for space key specifically
     */
    private var isSpaceKeyPressed = false

    private fun isSpaceKey(x: Int, y: Int): Boolean {
        val keyboard = keyboard ?: return false
        val spaceKey = keyboard.keys.find { it.codes.isNotEmpty() && it.codes[0] == 32 }
        return spaceKey?.let { key ->
            x >= key.x && x <= key.x + key.width &&
            y >= key.y && y <= key.y + key.height
        } ?: false
    }

    private fun dismissKeyPreview() {
        // Method 1: Immediate multiple invalidations
        invalidate()

        // Method 2: Try to access and dismiss the preview popup through reflection
        try {
            val previewPopupField = javaClass.superclass?.getDeclaredField("mPreviewPopup")
            previewPopupField?.isAccessible = true
            val previewPopup = previewPopupField?.get(this)

            previewPopup?.let { popup ->
                val dismissMethod = popup.javaClass.getMethod("dismiss")
                dismissMethod.invoke(popup)
            }
        } catch (e: Exception) {
            // Try alternative field name
            try {
                val previewPopupField = javaClass.superclass?.getDeclaredField("mPopupPreview")
                previewPopupField?.isAccessible = true
                val previewPopup = previewPopupField?.get(this)

                previewPopup?.let { popup ->
                    val dismissMethod = popup.javaClass.getMethod("dismiss")
                    dismissMethod.invoke(popup)
                }
            } catch (e2: Exception) {
                // Ignore
            }
        }

        // Method 3: Try to call dismissPreview method directly
        try {
            val dismissMethod = javaClass.superclass?.getDeclaredMethod("dismissPreview")
            dismissMethod?.isAccessible = true
            dismissMethod?.invoke(this)
        } catch (e: Exception) {
            // Ignore
        }

        // Method 4: Aggressive invalidation with delays
        post {
            invalidate()
            postDelayed({
                invalidate()
                postDelayed({
                    invalidate()
                }, 50)
            }, 10)
        }

        // Method 5: Try to reset preview state
        try {
            val setPreviewEnabledMethod = javaClass.superclass?.getDeclaredMethod("setPreviewEnabled", Boolean::class.java)
            setPreviewEnabledMethod?.isAccessible = true
            setPreviewEnabledMethod?.invoke(this, false)

            postDelayed({
                try {
                    setPreviewEnabledMethod?.invoke(this, true)
                } catch (e: Exception) {
                    // Ignore
                }
            }, 100)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
