package com.vigil.security.service.overlay

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.PorterDuff
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.*
import android.view.animation.LinearInterpolator
import android.widget.*
import androidx.core.app.NotificationCompat
import com.vigil.security.MainActivity
import com.vigil.security.R
import com.vigil.security.core.model.RiskLevel
import com.vigil.security.service.accessibility.VigilAccessibilityService
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.cos
import kotlin.math.sin

/**
 * Hardware-accelerated custom circular spinner that draws a spinning arc with rounded caps
 * over a subtle track ring. Guaranteed to animate smoothly at 60/120fps inside WindowManager overlays.
 */
class CircularLoadingSpinnerView(context: Context) : View(context) {

    private var animAngle = 0f
    private var animator: ValueAnimator? = null

    private val strokeWidthPx = 2.4f * resources.displayMetrics.density

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        color = Color.parseColor("#4DFFFFFF") // 30% translucent white track ring
    }

    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }

    private val oval = RectF()

    init {
        startSpinning()
    }

    fun startSpinning() {
        if (animator?.isRunning == true) return
        animator = ValueAnimator.ofFloat(0f, 360f).apply {
            duration = 900L
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener {
                animAngle = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    fun stopSpinning() {
        animator?.cancel()
        animator = null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (visibility == VISIBLE) {
            startSpinning()
        }
    }

    override fun onDetachedFromWindow() {
        stopSpinning()
        super.onDetachedFromWindow()
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility == VISIBLE) {
            startSpinning()
        } else {
            stopSpinning()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val pad = strokeWidthPx / 2f + 1f
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        oval.set(pad, pad, w - pad, h - pad)

        // Subtle guide ring
        canvas.drawOval(oval, trackPaint)

        // Crisp spinning loading arc (260 degrees)
        canvas.drawArc(oval, animAngle, 260f, false, arcPaint)
    }
}

class VigilOverlayService : Service() {

    private var windowManager: WindowManager? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private class OverlayEntry(
        val key: String,
        val url: String,
        var bounds: Rect,
        val badgeRootView: LinearLayout,
        val badgeShield: LinearLayout,
        val badgeSpinner: CircularLoadingSpinnerView,
        val badgeIcon: TextView,
        val infoCard: LinearLayout,
        val badgeParams: WindowManager.LayoutParams,
        var isExpanded: Boolean = false,
        var reportId: String? = null
    )

    private val activeOverlays = ConcurrentHashMap<String, OverlayEntry>()

    companion object {
        @Volatile
        var instance: VigilOverlayService? = null
            private set

        const val ACTION_SHOW_SCANNING = "com.vigil.security.ACTION_SHOW_SCANNING"
        const val ACTION_SHOW_INDICATOR = "com.vigil.security.ACTION_SHOW_INDICATOR"
        const val ACTION_DISMISS = "com.vigil.security.ACTION_DISMISS"
        const val ACTION_SYNC_VISIBLE = "com.vigil.security.ACTION_SYNC_VISIBLE"

        const val EXTRA_URL_KEY = "extra_url_key"
        const val EXTRA_REPORT_ID = "extra_report_id"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_DOMAIN = "extra_domain"
        const val EXTRA_RISK = "extra_risk"
        const val EXTRA_SUMMARY = "extra_summary"
        const val EXTRA_ACTION = "extra_action"
        const val EXTRA_TRUST_SCORE = "extra_trust_score"
        const val EXTRA_CONFIDENCE = "extra_confidence"
        const val EXTRA_CONFIDENCE_SCORE = "extra_confidence_score"
        const val EXTRA_VISIBLE_KEYS = "extra_visible_keys"
        const val EXTRA_VISIBLE_URLS = "extra_visible_urls"

        const val EXTRA_BOUNDS_LEFT = "extra_bounds_left"
        const val EXTRA_BOUNDS_TOP = "extra_bounds_top"
        const val EXTRA_BOUNDS_RIGHT = "extra_bounds_right"
        const val EXTRA_BOUNDS_BOTTOM = "extra_bounds_bottom"

        private const val NOTIFICATION_CHANNEL_ID = "vigil_overlay_channel"
        private const val NOTIFICATION_ID = 2001
    }

    private val density: Float
        get() = resources.displayMetrics.density

    private fun dp(dpVal: Int): Int = (dpVal * density + 0.5f).toInt()

    private fun getSafeScreenBounds(): Pair<Int, Int> {
        val dm = resources.displayMetrics
        val sw = dm.widthPixels.coerceAtLeast(480)
        val sh = dm.heightPixels.coerceAtLeast(800)
        return Pair(sw, sh)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForegroundNotification()
    }

    private fun startForegroundNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Vigil Link Intelligence",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Beside-the-bubble link safety indicators and reports"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Vigil")
            .setContentText("Beside-Bubble Link Protection Active")
            .setSmallIcon(R.drawable.ic_qs_tile)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent == null) return START_NOT_STICKY

        when (intent.action) {
            ACTION_SHOW_SCANNING -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val key = intent.getStringExtra(EXTRA_URL_KEY) ?: url
                val bounds = extractBounds(intent)
                val domain = extractSimpleDomain(url)
                if (Settings.canDrawOverlays(this)) {
                    renderScanningOverlay(key, url, domain, bounds)
                }
            }
            ACTION_SHOW_INDICATOR -> {
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val key = intent.getStringExtra(EXTRA_URL_KEY) ?: url
                val bounds = extractBounds(intent)
                val reportId = intent.getStringExtra(EXTRA_REPORT_ID) ?: ""
                val domain = intent.getStringExtra(EXTRA_DOMAIN) ?: extractSimpleDomain(url)
                val riskString = intent.getStringExtra(EXTRA_RISK) ?: "UNKNOWN"
                val summary = intent.getStringExtra(EXTRA_SUMMARY) ?: ""
                val action = intent.getStringExtra(EXTRA_ACTION) ?: ""
                val trustScore = intent.getIntExtra(EXTRA_TRUST_SCORE, 50)
                val confidence = intent.getStringExtra(EXTRA_CONFIDENCE) ?: "High"
                val confidenceScore = intent.getIntExtra(EXTRA_CONFIDENCE_SCORE, 85)

                val risk = try { RiskLevel.valueOf(riskString) } catch (_: Exception) { RiskLevel.UNKNOWN }

                if (Settings.canDrawOverlays(this)) {
                    renderVerdictOverlay(
                        key = key,
                        reportId = reportId,
                        url = url,
                        domain = domain,
                        risk = risk,
                        summary = summary,
                        action = action,
                        trustScore = trustScore,
                        confidence = confidence,
                        confidenceScore = confidenceScore,
                        bounds = bounds
                    )
                }
            }
            ACTION_SYNC_VISIBLE -> {
                val visibleKeys = (intent.getStringArrayListExtra(EXTRA_VISIBLE_KEYS)
                    ?: intent.getStringArrayListExtra(EXTRA_VISIBLE_URLS))?.toSet() ?: emptySet()
                val wm = windowManager ?: return START_NOT_STICKY
                val iterator = activeOverlays.entries.iterator()
                while (iterator.hasNext()) {
                    val (key, entry) = iterator.next()
                    val keyDomain = extractSimpleDomain(key)
                    val isVisible = visibleKeys.contains(key) || visibleKeys.any { vk ->
                        val vkNorm = vk.removePrefix("https://").removePrefix("http://").trimEnd('/')
                        val keyNorm = key.removePrefix("https://").removePrefix("http://").trimEnd('/')
                        vkNorm == keyNorm || extractSimpleDomain(vk) == keyDomain
                    }
                    if (!isVisible) {
                        try { wm.removeView(entry.badgeRootView) } catch (_: Exception) {}
                        iterator.remove()
                    }
                }
            }
            ACTION_DISMISS -> {
                val key = intent.getStringExtra(EXTRA_URL_KEY) ?: intent.getStringExtra(EXTRA_URL)
                if (key.isNullOrBlank()) {
                    removeAllOverlays()
                    stopSelf()
                } else {
                    dismissOverlay(key)
                    val domain = extractSimpleDomain(key)
                    val it = activeOverlays.entries.iterator()
                    val wm = windowManager
                    while (it.hasNext()) {
                        val (k, e) = it.next()
                        if (k == key || extractSimpleDomain(k) == domain) {
                            try { wm?.removeView(e.badgeRootView) } catch (_: Exception) {}
                            it.remove()
                        }
                    }
                    if (activeOverlays.isEmpty()) {
                        stopSelf()
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    private fun extractBounds(intent: Intent): Rect {
        val (sw, _) = getSafeScreenBounds()
        val left = intent.getIntExtra(EXTRA_BOUNDS_LEFT, dp(16))
        val top = intent.getIntExtra(EXTRA_BOUNDS_TOP, dp(120))
        val right = intent.getIntExtra(EXTRA_BOUNDS_RIGHT, sw - dp(16))
        val bottom = intent.getIntExtra(EXTRA_BOUNDS_BOTTOM, top + dp(40))
        return Rect(left, top, right, bottom)
    }

    private fun extractSimpleDomain(rawUrl: String): String {
        return try {
            val clean = if (rawUrl.startsWith("http://") || rawUrl.startsWith("https://")) rawUrl else "https://$rawUrl"
            val uri = java.net.URI(clean)
            val host = uri.host
            if (!host.isNullOrBlank()) {
                host.removePrefix("www.").lowercase()
            } else {
                clean.substringAfter("://").substringBefore("/").removePrefix("www.").lowercase()
            }
        } catch (_: Exception) {
            rawUrl.substringAfter("://").substringBefore("/").removePrefix("www.").lowercase()
        }
    }

    private fun safeCoerce(value: Int, min: Int, max: Int): Int {
        val actualMin = minOf(min, max)
        val actualMax = maxOf(min, max)
        return value.coerceIn(actualMin, actualMax)
    }

    // ------------------------------------------------------------------------
    // RENDER: SCANNING STATE (Beside-Bubble Animated Circular Spinner)
    // ------------------------------------------------------------------------
    @SuppressLint("ClickableViewAccessibility")
    fun renderScanningOverlay(key: String, url: String, domain: String, bounds: Rect) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { renderScanningOverlay(key, url, domain, bounds) }
            return
        }
        val wm = windowManager ?: return
        val scanColor = Color.parseColor("#2563EB") // Electric Azure

        val existing = activeOverlays[key]
        if (existing != null) {
            existing.bounds = bounds
            updateBadgeStyle(existing.badgeShield, existing.badgeSpinner, existing.badgeIcon, "", scanColor, isScanning = true)
            updatePositions(existing, bounds, wm)
            return
        }

        val badgeParams = createBadgeLayoutParams(bounds, isExpanded = false)

        val badgeRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        // Circular Security Shield Badge (34dp) sitting beside the bubble
        val badgeShield = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(scanColor)
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(8).toFloat()
        }

        // Animated custom circular loading spinner
        val badgeSpinner = CircularLoadingSpinnerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                gravity = Gravity.CENTER
            }
            visibility = View.VISIBLE
        }

        val badgeIcon = TextView(this).apply {
            text = "✓"
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = View.GONE
        }

        badgeShield.addView(badgeSpinner)
        badgeShield.addView(badgeIcon)

        val infoCard = buildExpandedCard(
            key, url, domain, "Analyzing link security in real-time...",
            "Verifying URL against threat engines.", 50, scanColor, "",
            confidence = "Calculating...", confidenceScore = 50, riskLevel = RiskLevel.UNKNOWN
        )

        badgeRoot.addView(badgeShield)
        badgeRoot.addView(infoCard)

        val entry = OverlayEntry(
            key = key,
            url = url,
            bounds = bounds,
            badgeRootView = badgeRoot,
            badgeShield = badgeShield,
            badgeSpinner = badgeSpinner,
            badgeIcon = badgeIcon,
            infoCard = infoCard,
            badgeParams = badgeParams
        )

        badgeShield.setOnClickListener {
            toggleExpanded(entry, wm)
        }

        attachDragListener(badgeShield, badgeRoot, badgeParams, wm)

        try {
            wm.addView(badgeRoot, badgeParams)
            activeOverlays[key] = entry
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------------
    // RENDER: VERDICT STATE (Beside-Bubble Verdict Shield Badge with Checkmark / Alert)
    // ------------------------------------------------------------------------
    @SuppressLint("ClickableViewAccessibility")
    fun renderVerdictOverlay(
        key: String,
        reportId: String,
        url: String,
        domain: String,
        risk: RiskLevel,
        summary: String,
        action: String,
        trustScore: Int,
        confidence: String,
        confidenceScore: Int,
        bounds: Rect
    ) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post {
                renderVerdictOverlay(
                    key, reportId, url, domain, risk, summary, action,
                    trustScore, confidence, confidenceScore, bounds
                )
            }
            return
        }
        val wm = windowManager ?: return
        val riskColor = getRiskColor(risk)
        val riskIconText = if (risk == RiskLevel.SAFE) "✓" else "!"

        // Exact key match first
        var existing = activeOverlays[key]

        // Fallback: if keys differed slightly (e.g. scheme or preview card vs full URL), find by domain
        if (existing == null) {
            val normalizedTarget = url.removePrefix("https://").removePrefix("http://").trimEnd('/')
            val match = activeOverlays.entries.firstOrNull { (_, entry) ->
                val entryNorm = entry.url.removePrefix("https://").removePrefix("http://").trimEnd('/')
                entryNorm == normalizedTarget || extractSimpleDomain(entry.url) == domain
            }
            if (match != null) {
                activeOverlays.remove(match.key)
                existing = match.value
                activeOverlays[key] = existing
            }
        }

        if (existing != null) {
            existing.bounds = bounds
            existing.reportId = reportId
            updateBadgeStyle(existing.badgeShield, existing.badgeSpinner, existing.badgeIcon, riskIconText, riskColor, isScanning = false)
            populateExpandedCard(existing.infoCard, domain, summary, action, trustScore, riskColor, reportId, url, key, confidence, confidenceScore, risk)
            updatePositions(existing, bounds, wm)
            return
        }

        val badgeParams = createBadgeLayoutParams(bounds, isExpanded = false)

        val badgeRoot = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
        }

        val badgeShield = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(dp(34), dp(34))
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(riskColor)
                setStroke(dp(2), Color.WHITE)
            }
            elevation = dp(8).toFloat()
        }

        val badgeSpinner = CircularLoadingSpinnerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply {
                gravity = Gravity.CENTER
            }
            visibility = View.GONE
        }

        val badgeIcon = TextView(this).apply {
            text = riskIconText
            textSize = 15f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            visibility = View.VISIBLE
        }

        badgeShield.addView(badgeSpinner)
        badgeShield.addView(badgeIcon)

        val infoCard = buildExpandedCard(key, url, domain, summary, action, trustScore, riskColor, reportId, confidence, confidenceScore, risk)

        badgeRoot.addView(badgeShield)
        badgeRoot.addView(infoCard)

        val entry = OverlayEntry(
            key = key,
            url = url,
            bounds = bounds,
            badgeRootView = badgeRoot,
            badgeShield = badgeShield,
            badgeSpinner = badgeSpinner,
            badgeIcon = badgeIcon,
            infoCard = infoCard,
            badgeParams = badgeParams,
            reportId = reportId
        )

        badgeShield.setOnClickListener {
            toggleExpanded(entry, wm)
        }

        attachDragListener(badgeShield, badgeRoot, badgeParams, wm)

        try {
            wm.addView(badgeRoot, badgeParams)
            activeOverlays[key] = entry
        } catch (_: Exception) {}
    }

    private fun toggleExpanded(entry: OverlayEntry, wm: WindowManager) {
        val (sw, sh) = getSafeScreenBounds()
        entry.isExpanded = !entry.isExpanded

        if (entry.isExpanded) {
            for ((url, other) in activeOverlays) {
                if (url != entry.url && other.isExpanded) {
                    other.isExpanded = false
                    other.infoCard.visibility = View.GONE
                    try {
                        applyPositionToParams(other.badgeParams, other.bounds, false, sw, sh)
                        wm.updateViewLayout(other.badgeRootView, other.badgeParams)
                    } catch (_: Exception) {}
                }
            }
        }

        if (entry.isExpanded) {
            entry.infoCard.visibility = View.VISIBLE
            applyPositionToParams(entry.badgeParams, entry.bounds, true, sw, sh)
        } else {
            entry.infoCard.visibility = View.GONE
            applyPositionToParams(entry.badgeParams, entry.bounds, false, sw, sh)
        }

        try {
            wm.updateViewLayout(entry.badgeRootView, entry.badgeParams)
        } catch (_: Exception) {}
    }

    // ------------------------------------------------------------------------
    // EXPANDED CARD BUILDER (Scrollable, Unclipped)
    // ------------------------------------------------------------------------
    private fun buildExpandedCard(
        key: String,
        url: String,
        domain: String,
        summary: String,
        action: String,
        trustScore: Int,
        accentColor: Int,
        reportId: String,
        confidence: String = "High",
        confidenceScore: Int = 85,
        riskLevel: RiskLevel = RiskLevel.SAFE
    ): LinearLayout {
        val (sw, _) = getSafeScreenBounds()
        val cardWidth = (sw - dp(32)).coerceAtMost(dp(320))

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(Color.parseColor("#080D1A"))
                setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            elevation = dp(14).toFloat()
            layoutParams = LinearLayout.LayoutParams(
                cardWidth,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(6)
            }
        }

        populateExpandedCard(card, domain, summary, action, trustScore, accentColor, reportId, url, key, confidence, confidenceScore, riskLevel)
        return card
    }

    private fun populateExpandedCard(
        card: LinearLayout,
        domain: String,
        summary: String,
        action: String,
        trustScore: Int,
        accentColor: Int,
        reportId: String,
        url: String,
        key: String,
        confidence: String = "High",
        confidenceScore: Int = 85,
        riskLevel: RiskLevel = RiskLevel.SAFE
    ) {
        card.removeAllViews()

        val scrollContainer = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            isFillViewport = true
        }

        val contentLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val domainHeader = TextView(this).apply {
            text = domain
            textSize = 13.5f
            setTextColor(Color.parseColor("#F8FAFC"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }

        // Dual Speedometer Meters: Trust Score & Confidence side-by-side with clear labels!
        val metersRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
                bottomMargin = dp(8)
            }
            gravity = Gravity.CENTER
        }

        // 1. Trust Score Meter Column
        val trustCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val trustLabel = TextView(this).apply {
            text = "TRUST SCORE"
            textSize = 10f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val trustSpeedometer = SpeedometerView(this, trustScore).apply {
            layoutParams = LinearLayout.LayoutParams(dp(112), dp(66)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(2)
            }
        }
        val trustVerdict = TextView(this).apply {
            text = riskLevel.name
            textSize = 10f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(accentColor)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(2), 0, 0)
        }
        trustCol.addView(trustLabel)
        trustCol.addView(trustSpeedometer)
        trustCol.addView(trustVerdict)

        // 2. Confidence Meter Column
        val confCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val confLabel = TextView(this).apply {
            text = "CONFIDENCE"
            textSize = 10f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#94A3B8"))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        val confSpeedometer = SpeedometerView(this, confidenceScore).apply {
            layoutParams = LinearLayout.LayoutParams(dp(112), dp(66)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                topMargin = dp(2)
            }
        }
        val confVerdict = TextView(this).apply {
            text = "${confidence.uppercase()}"
            textSize = 10f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(when {
                confidenceScore >= 67 -> Color.parseColor("#10B981")
                confidenceScore >= 34 -> Color.parseColor("#F59E0B")
                else -> Color.parseColor("#EF4444")
            })
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(2), 0, 0)
        }
        confCol.addView(confLabel)
        confCol.addView(confSpeedometer)
        confCol.addView(confVerdict)

        metersRow.addView(trustCol)
        metersRow.addView(confCol)

        val summaryView = TextView(this).apply {
            text = summary
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            maxLines = 3
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(4))
        }

        val actionView = TextView(this).apply {
            text = action
            textSize = 10.5f
            setTextColor(if (trustScore < 50) accentColor else Color.parseColor("#38BDF8"))
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
            setPadding(0, 0, 0, dp(8))
        }

        val buttonsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }

        val dismissBtn = TextView(this).apply {
            text = "Dismiss"
            textSize = 11f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(dp(10), dp(6), dp(10), dp(6))
            setOnClickListener {
                if (key.isNotEmpty()) {
                    dismissOverlay(key)
                } else if (url.isNotEmpty()) {
                    dismissOverlay(url)
                } else {
                    card.visibility = View.GONE
                }
            }
        }

        val verifyBtn = TextView(this).apply {
            text = "Inspect Full Report"
            textSize = 11f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(Color.parseColor("#050811"))
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#38BDF8"))
                cornerRadius = dp(6).toFloat()
            }
            setOnClickListener {
                if (key.isNotEmpty()) {
                    dismissOverlay(key)
                } else if (url.isNotEmpty()) {
                    dismissOverlay(url)
                } else {
                    card.visibility = View.GONE
                }
                if (reportId.isNotEmpty()) {
                    val mainIntent = Intent(this@VigilOverlayService, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                        putExtra("NAVIGATE_TO_REPORT_ID", reportId)
                    }
                    startActivity(mainIntent)
                }
            }
        }

        val disclaimerView = TextView(this).apply {
            text = "Vigil can make mistakes. Verify critical links."
            textSize = 9.5f
            setTextColor(Color.parseColor("#64748B"))
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(4), 0, dp(2))
        }

        buttonsRow.addView(dismissBtn)
        buttonsRow.addView(verifyBtn)

        contentLayout.addView(domainHeader)
        contentLayout.addView(metersRow)
        contentLayout.addView(summaryView)
        contentLayout.addView(actionView)
        contentLayout.addView(buttonsRow)
        contentLayout.addView(disclaimerView)

        scrollContainer.addView(contentLayout)
        card.addView(scrollContainer)
    }

    private class SpeedometerView(context: Context, private val score: Int) : View(context) {
        private val density = context.resources.displayMetrics.density
        private val strokeWidth = 11f * density

        private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = this@SpeedometerView.strokeWidth
            color = Color.parseColor("#EF4444")
        }
        private val yellowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = this@SpeedometerView.strokeWidth
            color = Color.parseColor("#F59E0B")
        }
        private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = this@SpeedometerView.strokeWidth
            color = Color.parseColor("#10B981")
        }
        private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.5f * density
            color = Color.parseColor("#334155")
        }
        private val needlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL_AND_STROKE
            color = Color.parseColor("#0F172A")
        }
        private val needleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2f
            color = Color.WHITE
        }
        private val hubPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.parseColor("#0F172A")
        }
        private val hubInnerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = Color.WHITE
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = when {
                score >= 67 -> Color.parseColor("#10B981")
                score >= 34 -> Color.parseColor("#F59E0B")
                else -> Color.parseColor("#EF4444")
            }
            textSize = 13f * density
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0 || h <= 0) return

            val sw = strokeWidth
            val pad = sw / 2f + 4 * density
            val oval = RectF(pad, pad, w - pad, (h - 18 * density) * 2f)

            // Outer border arc
            val bPad = 2 * density
            val bOval = RectF(bPad, bPad, w - bPad, (h - 18 * density) * 2f + sw - 2 * density)
            canvas.drawArc(bOval, 180f, 180f, false, borderPaint)

            // Reversed: Low (Red on left 180-240), Medium (Yellow 240-300), High (Green on right 300-360)
            canvas.drawArc(oval, 180f, 60f, false, redPaint)
            canvas.drawArc(oval, 240f, 60f, false, yellowPaint)
            canvas.drawArc(oval, 300f, 60f, false, greenPaint)

            // Pointer Needle
            val cx = w / 2f
            val cy = h - 18 * density
            val progress = (score.coerceIn(0, 100)) / 100f
            val angleRad = Math.toRadians((180f + 180f * progress).toDouble())
            val needleLen = (w / 2f) - sw - 2 * density

            val tipX = cx + (needleLen * cos(angleRad)).toFloat()
            val tipY = cy + (needleLen * sin(angleRad)).toFloat()

            val perpRad = angleRad + Math.PI / 2
            val baseR = 4.5f * density
            val bx1 = cx + (baseR * cos(perpRad)).toFloat()
            val by1 = cy + (baseR * sin(perpRad)).toFloat()
            val bx2 = cx - (baseR * cos(perpRad)).toFloat()
            val by2 = cy - (baseR * sin(perpRad)).toFloat()

            val path = Path().apply {
                moveTo(bx1, by1)
                lineTo(tipX, tipY)
                lineTo(bx2, by2)
                close()
            }
            canvas.drawPath(path, needlePaint)
            canvas.drawPath(path, needleBorderPaint)

            // Needle Hub (matching reference)
            canvas.drawCircle(cx, cy, 8.5f * density, hubPaint)
            canvas.drawCircle(cx, cy, 8.5f * density, needleBorderPaint)
            canvas.drawCircle(cx, cy, 3.5f * density, hubInnerPaint)

            // Digital readout
            canvas.drawText("$score%", cx, h - 2 * density, textPaint)
        }
    }

    // ------------------------------------------------------------------------
    // HELPER METHODS & BESIDE-BUBBLE GEOMETRY
    // ------------------------------------------------------------------------
    private fun updateBadgeStyle(
        badgeShield: LinearLayout,
        badgeSpinner: CircularLoadingSpinnerView,
        badgeIcon: TextView,
        iconText: String,
        color: Int,
        isScanning: Boolean
    ) {
        badgeShield.background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
            setStroke(dp(2), Color.WHITE)
        }
        if (isScanning) {
            badgeSpinner.visibility = View.VISIBLE
            badgeSpinner.startSpinning()
            badgeIcon.visibility = View.GONE
        } else {
            badgeSpinner.stopSpinning()
            badgeSpinner.visibility = View.GONE
            badgeIcon.visibility = View.VISIBLE
            badgeIcon.text = iconText
        }
        badgeShield.invalidate()
        badgeIcon.invalidate()
    }

    private fun updatePositions(entry: OverlayEntry, bounds: Rect, wm: WindowManager) {
        val (sw, sh) = getSafeScreenBounds()

        // If completely off-screen, hide views so they don't linger
        if (bounds.bottom <= 0 || bounds.top >= sh) {
            entry.badgeRootView.visibility = View.GONE
            return
        }

        entry.badgeRootView.visibility = View.VISIBLE
        applyPositionToParams(entry.badgeParams, bounds, entry.isExpanded, sw, sh)

        try {
            wm.updateViewLayout(entry.badgeRootView, entry.badgeParams)
        } catch (_: Exception) {}
    }

    private fun applyPositionToParams(
        params: WindowManager.LayoutParams,
        bounds: Rect,
        isExpanded: Boolean,
        sw: Int,
        sh: Int
    ) {
        val shieldSize = dp(34)

        if (!isExpanded) {
            val spaceLeft = bounds.left
            val spaceRight = sw - bounds.right
            val minOuterMargin = dp(36)

            // Strict check: Only treat as browser bar if at the very top and spans wide across screen
            val isBrowserBar = bounds.top < dp(100) && bounds.width() > (sw * 0.45f) && bounds.height() < dp(65)

            val badgeX = when {
                isBrowserBar -> {
                    // Sit neatly on the right inner side of the address bar
                    safeCoerce(bounds.right - shieldSize - dp(8), dp(8), sw - shieldSize - dp(8))
                }
                // Gap 1: Large left margin (sent chat bubble on right)
                spaceLeft >= minOuterMargin && spaceLeft >= spaceRight -> {
                    safeCoerce(bounds.left - shieldSize - dp(6), dp(6), sw - shieldSize - dp(6))
                }
                // Gap 2: Large right margin (received chat bubble on left)
                spaceRight >= minOuterMargin -> {
                    safeCoerce(bounds.right + dp(6), dp(6), sw - shieldSize - dp(6))
                }
                // Gap 3: Wide bubble (SMS messages, emails, notes): anchor at right edge of bubble line
                else -> {
                    safeCoerce(bounds.right - shieldSize - dp(8), dp(8), sw - shieldSize - dp(8))
                }
            }

            // Vertically center neatly with the link line
            val badgeY = if (isBrowserBar) {
                safeCoerce(bounds.top + (bounds.height() - shieldSize) / 2, dp(24), sh - shieldSize - dp(10))
            } else {
                safeCoerce(bounds.top + (bounds.height() - shieldSize) / 2, dp(36), sh - shieldSize - dp(10))
            }

            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.x = badgeX
            params.y = badgeY
        } else {
            // Expanded report card: center horizontally with 16dp margins, expand above/below bubble
            val cardWidth = (sw - dp(32)).coerceAtMost(dp(320))
            val cardEstHeight = dp(200)

            val idealX = (sw - cardWidth) / 2
            val spaceBelow = sh - bounds.bottom - dp(80)
            val shouldExpandAbove = spaceBelow < cardEstHeight + dp(20)

            val idealY = if (shouldExpandAbove) {
                safeCoerce(bounds.top - cardEstHeight - dp(10), dp(44), sh - cardEstHeight - dp(80))
            } else {
                safeCoerce(bounds.bottom + dp(10), dp(44), sh - cardEstHeight - dp(80))
            }

            params.width = WindowManager.LayoutParams.WRAP_CONTENT
            params.height = WindowManager.LayoutParams.WRAP_CONTENT
            params.x = idealX
            params.y = idealY
        }
    }

    private fun createBadgeLayoutParams(bounds: Rect, isExpanded: Boolean): WindowManager.LayoutParams {
        val (sw, sh) = getSafeScreenBounds()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }

        applyPositionToParams(params, bounds, isExpanded, sw, sh)
        return params
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun attachDragListener(
        badge: View,
        root: View,
        params: WindowManager.LayoutParams,
        wm: WindowManager
    ) {
        var initialX = 0
        var initialY = 0
        var touchStartX = 0f
        var touchStartY = 0f
        var isDragging = false

        badge.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    touchStartX = event.rawX
                    touchStartY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchStartX).toInt()
                    val dy = (event.rawY - touchStartY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) {
                        isDragging = true
                        params.x = initialX + dx
                        params.y = initialY + dy
                        try {
                            wm.updateViewLayout(root, params)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        badge.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    fun dismissOverlay(url: String) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { dismissOverlay(url) }
            return
        }
        val entry = activeOverlays.remove(url) ?: return
        VigilAccessibilityService.dismissedUrls.add(url)
        val wm = windowManager ?: return
        try {
            wm.removeView(entry.badgeRootView)
        } catch (_: Exception) {}
    }

    fun removeAllOverlays() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { removeAllOverlays() }
            return
        }
        val wm = windowManager ?: return
        for ((_, entry) in activeOverlays) {
            try {
                wm.removeView(entry.badgeRootView)
            } catch (_: Exception) {}
        }
        activeOverlays.clear()
    }

    fun syncVisibleKeys(visibleKeys: Set<String>) {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { syncVisibleKeys(visibleKeys) }
            return
        }
        val wm = windowManager ?: return
        val iterator = activeOverlays.entries.iterator()
        while (iterator.hasNext()) {
            val (key, entry) = iterator.next()
            val keyDomain = extractSimpleDomain(key)
            val isVisible = visibleKeys.contains(key) || visibleKeys.any { vk ->
                val vkNorm = vk.removePrefix("https://").removePrefix("http://").trimEnd('/')
                val keyNorm = key.removePrefix("https://").removePrefix("http://").trimEnd('/')
                vkNorm == keyNorm || extractSimpleDomain(vk) == keyDomain
            }
            if (!isVisible) {
                try { wm.removeView(entry.badgeRootView) } catch (_: Exception) {}
                iterator.remove()
            }
        }
    }

    private fun getRiskColor(risk: RiskLevel): Int = when (risk) {
        RiskLevel.SAFE -> Color.parseColor("#059669") // Fresh Emerald Green
        RiskLevel.CAUTION -> Color.parseColor("#D97706") // Luminous Amber
        RiskLevel.DANGEROUS -> Color.parseColor("#DC2626") // Security Red
        RiskLevel.UNKNOWN -> Color.parseColor("#64748B") // Slate
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        removeAllOverlays()
        super.onDestroy()
    }
}
