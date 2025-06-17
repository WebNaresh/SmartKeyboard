package com.example.smartkeyboard

import android.content.Context
import android.graphics.*
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.util.AttributeSet
import android.view.MotionEvent
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
        val result = super.onTouchEvent(me)

        me?.let { event ->
            val keyboard = keyboard ?: return result
            // val actionKey = keyboard.keys.find { it.codes[0] == -4 } // Disabled
            val spaceKey = keyboard.keys.find { it.codes[0] == 32 }

            val x = event.x.toInt()
            val y = event.y.toInt()

            // Action button touch handling disabled
            /*
            actionKey?.let { key ->
                if (x >= key.x && x <= key.x + key.width &&
                    y >= key.y && y <= key.y + key.height) {

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            actionButtonPressed = true
                            actionButtonScale = 0.95f
                            invalidate()
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            actionButtonPressed = false
                            actionButtonScale = 1.0f
                            invalidate()
                        }
                    }
                }
            }
            */

            // Check if touch is within space key bounds
            spaceKey?.let { key ->
                if (x >= key.x && x <= key.x + key.width &&
                    y >= key.y && y <= key.y + key.height) {

                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            spaceKeyPressed = true
                            invalidate()
                        }
                        MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                            spaceKeyPressed = false
                            invalidate()
                        }
                    }
                }
            }
        }

        return result
    }
}
