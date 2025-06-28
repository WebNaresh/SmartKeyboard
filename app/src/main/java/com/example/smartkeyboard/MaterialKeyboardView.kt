package com.example.smartkeyboard

import android.content.Context
import android.graphics.*
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.MotionEvent
import android.os.Handler
import android.os.Looper
import android.os.Message
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



    @Deprecated("Using deprecated KeyboardView API")
    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // drawCustomSpaceKey(canvas) // Temporarily disabled to fix black rectangle
        // drawCustomActionButton(canvas) // Disabled to remove green enter icon
        // drawNumberIndicators(canvas) // Removed - no longer needed with dedicated number row
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



    /**
     * Override to safely handle keyboard operations and prevent StringIndexOutOfBoundsException
     */
    override fun setKeyboard(keyboard: Keyboard?) {
        // Ensure all keys have non-empty labels to prevent crashes in adjustCase()
        keyboard?.let { kb ->
            try {
                kb.keys.forEach { key ->
                    // Check if key label is null or empty and fix it
                    if (key.label.isNullOrEmpty()) {
                        // Use a single space for empty labels to prevent charAt(0) crashes
                        key.label = " "
                    }
                }
            } catch (e: Exception) {
                // If we can't modify the keys, log the error but continue
                android.util.Log.w("MaterialKeyboardView", "Could not fix empty key labels", e)
            }
        }
        super.setKeyboard(keyboard)
    }

    /**
     * Custom Handler that blocks showKey messages to prevent crashes
     */
    private val safeHandler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            // Block all messages that could trigger showKey
            // Don't call super.handleMessage to prevent the crash
            android.util.Log.d("MaterialKeyboardView", "Blocked Handler message: ${msg.what}")
        }
    }

    /**
     * Initialize with preview disabled to prevent crashes
     */
    init {
        try {
            // Disable preview functionality completely
            isPreviewEnabled = false

            // Try to replace the internal handler with our safe handler
            val handlerField = javaClass.superclass?.getDeclaredField("mHandler")
            handlerField?.isAccessible = true
            handlerField?.set(this, safeHandler)

            // Try to set preview layout to null via reflection
            val previewLayoutField = javaClass.superclass?.getDeclaredField("mPreviewText")
            previewLayoutField?.isAccessible = true
            previewLayoutField?.set(this, null)

            // Try alternative field names
            try {
                val previewPopupField = javaClass.superclass?.getDeclaredField("mPreviewPopup")
                previewPopupField?.isAccessible = true
                previewPopupField?.set(this, null)
            } catch (e: Exception) {
                // Ignore
            }

        } catch (e: Exception) {
            android.util.Log.w("MaterialKeyboardView", "Could not disable preview via reflection", e)
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

                    // Find which key was pressed for spacer key handling
                    val pressedKey = keyboard.keys.find { key ->
                        x >= key.x && x <= key.x + key.width &&
                        y >= key.y && y <= key.y + key.height
                    }

                    pressedKey?.let { key ->
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
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Re-enable preview if it was disabled for space key
                    if (isSpaceKeyPressed) {
                        try {
                            val setPreviewEnabledMethod = javaClass.superclass?.getDeclaredMethod("setPreviewEnabled", Boolean::class.java)
                            setPreviewEnabledMethod?.isAccessible = true
                            setPreviewEnabledMethod?.invoke(this, true)
                        } catch (e: Exception) {
                            // Ignore if method not available
                        }
                        isSpaceKeyPressed = false
                    }
                }


            }
        }

        // Call super with safety wrapper
        return try {
            super.onTouchEvent(me)
        } catch (e: StringIndexOutOfBoundsException) {
            // Catch and log StringIndexOutOfBoundsException from KeyboardView.adjustCase()
            android.util.Log.w("MaterialKeyboardView", "Caught StringIndexOutOfBoundsException in onTouchEvent", e)
            // Return true to indicate the event was handled
            true
        } catch (e: Exception) {
            // Catch any other exceptions that might occur
            android.util.Log.w("MaterialKeyboardView", "Caught exception in onTouchEvent", e)
            true
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
