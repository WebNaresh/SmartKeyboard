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

    public override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // drawCustomSpaceKey(canvas) // Temporarily disabled to fix black rectangle
        // drawCustomActionButton(canvas) // Disabled to remove green enter icon
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

    override fun onTouchEvent(me: MotionEvent?): Boolean {
        me?.let { event ->
            val keyboard = keyboard ?: return super.onTouchEvent(me)
            val x = event.x.toInt()
            val y = event.y.toInt()

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Find which key was pressed
                    val pressedKey = keyboard.keys.find { key ->
                        x >= key.x && x <= key.x + key.width &&
                        y >= key.y && y <= key.y + key.height
                    }

                    pressedKey?.let { key ->
                        downKey = key
                        isLongPressTriggered = false

                        // Check if this is a top row key that supports long press for numbers
                        if (numberMap.containsKey(key.codes[0])) {
                            // Start long press timer
                            longPressRunnable = Runnable {
                                handleLongPress(key)
                            }
                            longPressHandler.postDelayed(longPressRunnable!!, longPressDelay)

                            // Let the parent handle the initial touch for normal behavior
                            return super.onTouchEvent(me)
                        }
                    }
                }

                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    // Cancel long press timer
                    longPressRunnable?.let { runnable ->
                        longPressHandler.removeCallbacks(runnable)
                        longPressRunnable = null
                    }

                    // If long press was triggered, dismiss any preview and consume the event
                    if (isLongPressTriggered) {
                        isLongPressTriggered = false
                        downKey = null

                        // Dismiss the key preview popup
                        dismissKeyPreview()

                        return true // Consume the event to prevent normal key processing
                    }

                    downKey = null
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

        return super.onTouchEvent(me)
    }

    private fun handleLongPress(key: Keyboard.Key) {
        val primaryCode = key.codes[0]
        val numberCode = numberMap[primaryCode]

        if (numberCode != null) {
            isLongPressTriggered = true

            // Dismiss the key preview popup immediately
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

    private fun dismissKeyPreview() {
        try {
            // Force dismiss any key preview popup
            val previewPopup = javaClass.superclass?.getDeclaredField("mPreviewPopup")
            previewPopup?.isAccessible = true
            val popup = previewPopup?.get(this)

            popup?.let {
                val dismissMethod = it.javaClass.getMethod("dismiss")
                dismissMethod.invoke(it)
            }
        } catch (e: Exception) {
            // Fallback: try to invalidate the view to clear any stuck previews
            invalidate()
        }
    }
}
