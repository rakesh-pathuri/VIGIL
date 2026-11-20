package com.vigil.security.service.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.View
import android.view.animation.LinearInterpolator
import com.vigil.security.core.model.RiskLevel
import kotlin.math.PI
import kotlin.math.sin

/**
 * Lightweight, hardware-accelerated View that renders the signature Vigil Dotted Cyber Shield
 * inside the floating overlay system.
 */
@SuppressLint("ViewConstructor")
class CyberDottedShieldView(context: Context) : View(context) {

    enum class BadgeState {
        SCANNING,
        SAFE,
        CAUTION,
        DANGEROUS,
        UNKNOWN
    }

    var badgeState: BadgeState = BadgeState.SCANNING
        set(value) {
            field = value
            updateState()
        }

    private var animPhase = 0f
    private var animator: ValueAnimator? = null

    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private class DotPoint(val nx: Float, val ny: Float, val defaultColor: Int)

    private val perimeterDots = listOf(
        DotPoint(0.50f, 0.11f, Color.parseColor("#FBBF24")),
        DotPoint(0.56f, 0.13f, Color.parseColor("#F59E0B")),
        DotPoint(0.62f, 0.16f, Color.parseColor("#F97316")),
        DotPoint(0.68f, 0.20f, Color.parseColor("#EF4444")),
        DotPoint(0.74f, 0.25f, Color.parseColor("#F43F5E")),
        DotPoint(0.74f, 0.32f, Color.parseColor("#E11D48")),
        DotPoint(0.74f, 0.39f, Color.parseColor("#D946EF")),
        DotPoint(0.74f, 0.46f, Color.parseColor("#A855F7")),
        DotPoint(0.74f, 0.53f, Color.parseColor("#8B5CF6")),
        DotPoint(0.70f, 0.61f, Color.parseColor("#6366F1")),
        DotPoint(0.65f, 0.68f, Color.parseColor("#4F46E5")),
        DotPoint(0.60f, 0.75f, Color.parseColor("#3B82F6")),
        DotPoint(0.55f, 0.81f, Color.parseColor("#2563EB")),
        DotPoint(0.50f, 0.87f, Color.parseColor("#0284C7")),
        DotPoint(0.45f, 0.81f, Color.parseColor("#0EA5E9")),
        DotPoint(0.40f, 0.75f, Color.parseColor("#06B6D4")),
        DotPoint(0.35f, 0.68f, Color.parseColor("#00D2FF")),
        DotPoint(0.30f, 0.61f, Color.parseColor("#00E5FF")),
        DotPoint(0.26f, 0.53f, Color.parseColor("#00F5D4")),
        DotPoint(0.26f, 0.46f, Color.parseColor("#10B981")),
        DotPoint(0.26f, 0.39f, Color.parseColor("#34D399")),
        DotPoint(0.26f, 0.32f, Color.parseColor("#6EE7B7")),
        DotPoint(0.26f, 0.25f, Color.parseColor("#6EE7B7")),
        DotPoint(0.32f, 0.20f, Color.parseColor("#A7F3D0")),
        DotPoint(0.38f, 0.16f, Color.parseColor("#FDE68A")),
        DotPoint(0.44f, 0.13f, Color.parseColor("#FCD34D"))
    )

    private val checkmarkDots = listOf(
        DotPoint(0.38f, 0.47f, Color.parseColor("#00E5FF")),
        DotPoint(0.44f, 0.53f, Color.parseColor("#38BDF8")),
        DotPoint(0.50f, 0.60f, Color.parseColor("#60A5FA")),
        DotPoint(0.56f, 0.51f, Color.parseColor("#BAE6FD")),
        DotPoint(0.62f, 0.42f, Color.parseColor("#E0F2FE")),
        DotPoint(0.66f, 0.35f, Color.WHITE)
    )

    private val alertDots = listOf(
        DotPoint(0.50f, 0.35f, Color.WHITE),
        DotPoint(0.50f, 0.44f, Color.WHITE),
        DotPoint(0.50f, 0.53f, Color.WHITE),
        DotPoint(0.50f, 0.66f, Color.WHITE)
    )

    init {
        isClickable = true
        isFocusable = true
        updateState()
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun setVerdict(risk: RiskLevel) {
        badgeState = when (risk) {
            RiskLevel.SAFE -> BadgeState.SAFE
            RiskLevel.CAUTION -> BadgeState.CAUTION
            RiskLevel.DANGEROUS -> BadgeState.DANGEROUS
            RiskLevel.UNKNOWN -> BadgeState.UNKNOWN
        }
    }

    private fun updateState() {
        if (badgeState == BadgeState.SCANNING) {
            startAnimation()
        } else {
            stopAnimation()
        }
        invalidate()
    }

    private fun startAnimation() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animPhase = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun stopAnimation() {
        animator?.cancel()
        animator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (badgeState == BadgeState.SCANNING) {
            startAnimation()
        }
    }

    override fun onDetachedFromWindow() {
        stopAnimation()
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        val baseRadius = w * 0.038f
        val totalDots = perimeterDots.size

        // Background dark obsidian circular shield backdrop with cyber border ring
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#080D1A")
        }
        canvas.drawCircle(w / 2f, h / 2f, w * 0.47f, bgPaint)

        val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w * 0.045f
            color = when (badgeState) {
                BadgeState.SCANNING -> Color.parseColor("#0284C7")
                BadgeState.SAFE -> Color.parseColor("#10B981")
                BadgeState.CAUTION -> Color.parseColor("#F59E0B")
                BadgeState.DANGEROUS -> Color.parseColor("#EF4444")
                BadgeState.UNKNOWN -> Color.parseColor("#475569")
            }
        }
        canvas.drawCircle(w / 2f, h / 2f, w * 0.46f, ringPaint)

        // Draw Perimeter dots
        perimeterDots.forEachIndexed { index, dot ->
            val cx = dot.nx * w
            val cy = dot.ny * h

            val dotColor = when (badgeState) {
                BadgeState.SCANNING -> {
                    // Scanning: cyber neon blue/cyan traveling pulse
                    Color.parseColor("#00D2FF")
                }
                BadgeState.SAFE -> Color.parseColor("#059669") // Fresh Emerald
                BadgeState.CAUTION -> Color.parseColor("#D97706") // Cyber Amber
                BadgeState.DANGEROUS -> Color.parseColor("#DC2626") // Security Red
                BadgeState.UNKNOWN -> Color.parseColor("#64748B") // Slate
            }

            val pulse = if (badgeState == BadgeState.SCANNING) {
                val normIdx = index.toFloat() / totalDots
                val delta = (normIdx - animPhase + 1f) % 1f
                ((sin(delta * 2 * PI).toFloat() + 1f) / 2f).coerceIn(0.15f, 1f)
            } else 1f

            val r = baseRadius * (0.8f + 0.35f * pulse)

            // Outer glow
            glowPaint.color = dotColor
            glowPaint.alpha = (if (badgeState == BadgeState.SCANNING) 70 * pulse else 55f).toInt()
            canvas.drawCircle(cx, cy, r * 2.2f, glowPaint)

            // Crisp dot core
            dotPaint.color = dotColor
            dotPaint.alpha = (if (badgeState == BadgeState.SCANNING) 180 + (75 * pulse) else 255f).toInt()
            canvas.drawCircle(cx, cy, r, dotPaint)
        }

        // Draw Center symbol
        when (badgeState) {
            BadgeState.SAFE -> {
                // Illuminated checkmark dots
                dotPaint.color = Color.WHITE
                dotPaint.alpha = 255
                glowPaint.color = Color.parseColor("#34D399")
                glowPaint.alpha = 110

                checkmarkDots.forEach { dot ->
                    val cx = dot.nx * w
                    val cy = dot.ny * h
                    canvas.drawCircle(cx, cy, baseRadius * 2.0f, glowPaint)
                    canvas.drawCircle(cx, cy, baseRadius * 1.05f, dotPaint)
                }
            }
            BadgeState.CAUTION, BadgeState.DANGEROUS, BadgeState.UNKNOWN -> {
                // Illuminated exclamation mark dots
                val alertColor = if (badgeState == BadgeState.DANGEROUS) Color.parseColor("#F87171") else Color.parseColor("#FBBF24")
                dotPaint.color = Color.WHITE
                dotPaint.alpha = 255
                glowPaint.color = alertColor
                glowPaint.alpha = 110

                alertDots.forEach { dot ->
                    val cx = dot.nx * w
                    val cy = dot.ny * h
                    canvas.drawCircle(cx, cy, baseRadius * 2.0f, glowPaint)
                    canvas.drawCircle(cx, cy, baseRadius * 1.05f, dotPaint)
                }
            }
            BadgeState.SCANNING -> {
                // In scanning state, draw a subtle luminous center core pulse
                val centerPulse = ((sin(animPhase * 2 * PI).toFloat() + 1f) / 2f)
                glowPaint.color = Color.parseColor("#00E5FF")
                glowPaint.alpha = (50 + 60 * centerPulse).toInt()
                canvas.drawCircle(w / 2f, h / 2f, baseRadius * 3.5f * centerPulse, glowPaint)
            }
        }
    }
}
