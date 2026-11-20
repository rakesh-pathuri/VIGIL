package com.vigil.security.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.SystemClock
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.vigil.security.VigilApplication
import com.vigil.security.core.model.RiskLevel
import com.vigil.security.core.model.SecurityReport
import com.vigil.security.service.overlay.VigilOverlayService
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

data class DetectedLinkNode(
    val url: String,
    val bounds: Rect
)

class VigilAccessibilityService : AccessibilityService() {

    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        android.util.Log.e("VigilAccess", "Unhandled coroutine error", throwable)
    }
    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob() + coroutineExceptionHandler)
    private var lastEventTime: Long = 0L
    private var isReceiverRegistered = false

    private val systemEventsReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            try {
                when (intent?.action) {
                    Intent.ACTION_SCREEN_OFF -> dismissAllOverlays()
                    Intent.ACTION_CLOSE_SYSTEM_DIALOGS -> {
                        val reason = intent.getStringExtra("reason")
                        if (reason == "homekey" || reason == "recentapps") {
                            dismissAllOverlays()
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    companion object {
        private const val EVENT_THROTTLE_MS = 35L

        // Robust regex matching full URLs, www prefixes, and domains with modern TLDs
        private val URL_REGEX = Pattern.compile(
            "\\b((https?|ftp)://[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_]|www\\.[-a-zA-Z0-9+&@#/%?=~_|!:,.;]*[-a-zA-Z0-9+&@#/%=~_]|[a-zA-Z0-9.-]+\\.(com|org|net|xyz|top|cc|ru|cn|live|site|online|club|vip|icu|fun|info|biz|link|click|app|dev|me|io|co|in|us|uk|gov|edu|ai|tech|store|shop|space|pro|mobi|security|bank|finance|agency|digital|cloud|work|world|zone|today|page|news)(/[a-zA-Z0-9+&@#/%?=~_|!:,.;]*)?)",
            Pattern.CASE_INSENSITIVE
        )

        @Volatile
        var isServiceRunning: Boolean = false
            private set

        val dismissedUrls = ConcurrentHashMap.newKeySet<String>()
        private val currentlyAnalyzingUrls = ConcurrentHashMap.newKeySet<String>()
        private val displayedUrls = ConcurrentHashMap.newKeySet<String>()
        private val lastKnownBounds = ConcurrentHashMap<String, Rect>()
        private var lastActivePackage: String = ""
    }

    private val density: Float
        get() = resources.displayMetrics.density

    private fun dp(dpVal: Int): Int = (dpVal * density + 0.5f).toInt()

    override fun onServiceConnected() {
        super.onServiceConnected()
        isServiceRunning = true

        // Ensure VigilOverlayService is running in foreground so Android / Vivo does not revoke accessibility
        try {
            val overlayIntent = Intent(this, VigilOverlayService::class.java)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(overlayIntent)
            } else {
                startService(overlayIntent)
            }
        } catch (_: Exception) {}

        try {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                @Suppress("DEPRECATION")
                addAction(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
            }
            registerReceiver(systemEventsReceiver, filter)
            isReceiverRegistered = true
        } catch (_: Exception) {}
    }

    override fun onDestroy() {
        isServiceRunning = false
        if (isReceiverRegistered) {
            try {
                unregisterReceiver(systemEventsReceiver)
            } catch (_: Exception) {}
            isReceiverRegistered = false
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        isServiceRunning = true

        try {
            val app = application as? VigilApplication ?: return
            val keyStoreManager = app.container.keyStoreManager
            if (!keyStoreManager.isMonitoringEnabled) {
                return
            }

            val sourcePackage = event.packageName?.toString() ?: ""
            // Crucial: Never process events originating from Vigil itself (prevents feedback loop and scanning inside our own app)
            if (sourcePackage.isEmpty() || sourcePackage == packageName) {
                return
            }

            val eventType = event.eventType
            val isWindowState = eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED

            // Rapid non-starving throttle: limit content churn to max 35ms, without dropping events when idle
            val now = SystemClock.uptimeMillis()
            if (!isWindowState && (now - lastEventTime < EVENT_THROTTLE_MS)) {
                return
            }
            lastEventTime = now

            // Check if user returned to the home launcher (closing or exiting monitored app)
            if (isLauncherPackage(sourcePackage)) {
                dismissAllOverlays()
                lastActivePackage = sourcePackage
                lastKnownBounds.clear()
                displayedUrls.clear()
                return
            }

            // Real app switch: clear overlays from the prior app (ignoring IME / keyboards)
            if (!isIgnoredPackage(sourcePackage) && sourcePackage != lastActivePackage) {
                dismissAllOverlays()
                lastActivePackage = sourcePackage
                dismissedUrls.clear()
                lastKnownBounds.clear()
                displayedUrls.clear()
            }

            // Inspect node hierarchy synchronously on the accessibility thread
            val rootNode = rootInActiveWindow ?: event.source ?: return
            val activeWindowPackage = rootNode.packageName?.toString() ?: ""
            if (activeWindowPackage == packageName) {
                return
            }
            val rawDetectedLinks = mutableListOf<DetectedLinkNode>()
            try {
                inspectNodeRecursive(rootNode, rawDetectedLinks, depth = 0)
            } catch (_: Exception) {
                return
            }

            // Deduplicate links in current view (removes duplicate hero preview cards vs message body text)
            val detectedLinks = deduplicateLinks(rawDetectedLinks)

            // Prune off-screen overlays so scrolled-out links never leave floating ghosts
            val visibleKeys = ArrayList(detectedLinks.map { it.url.trim() })
            if (visibleKeys.isNotEmpty()) {
                syncVisibleOverlays(visibleKeys)
            }

            if (detectedLinks.isEmpty()) {
                return
            }

            val appLabel = getAppLabel(if (!isIgnoredPackage(sourcePackage)) sourcePackage else lastActivePackage)

            for (link in detectedLinks) {
                val urlKey = link.url.trim()
                if (dismissedUrls.contains(urlKey)) {
                    continue
                }

                // Check if bounds have shifted (scrolling or keyboard movement)
                val last = lastKnownBounds[urlKey]
                val hasMoved = last == null || Math.abs(last.left - link.bounds.left) > 2 || Math.abs(last.top - link.bounds.top) > 2

                // If already displayed and has NOT moved: do NOT re-render (stops blinking!)
                if (!hasMoved && displayedUrls.contains(urlKey)) {
                    continue
                }

                lastKnownBounds[urlKey] = Rect(link.bounds)

                // If it moved but was already displayed: smoothly update position without re-scanning
                if (hasMoved && displayedUrls.contains(urlKey)) {
                    if (currentlyAnalyzingUrls.contains(urlKey)) {
                        // Keep tracking link position during the scanning animation
                        showFloatingScanning(urlKey, link.bounds)
                        continue
                    }
                    serviceScope.launch {
                        val currentApp = application as? VigilApplication ?: return@launch
                        val scanRepo = currentApp.container.scanRepository
                        val cached = scanRepo.getScanByUrl(urlKey) ?: scanRepo.getScanByUrl(
                            if (urlKey.startsWith("http://", ignoreCase = true) || urlKey.startsWith("https://", ignoreCase = true)) {
                                urlKey
                            } else {
                                "https://$urlKey"
                            }
                        )
                        if (cached != null && android.provider.Settings.canDrawOverlays(this@VigilAccessibilityService)) {
                            withContext(Dispatchers.Main) {
                                showFloatingInfoBadge(urlKey, cached, link.bounds)
                            }
                        }
                    }
                    continue
                }

                // NEW LINK: mark as displayed and immediately show the blue circle in 0ms!
                displayedUrls.add(urlKey)
                showFloatingScanning(urlKey, link.bounds)

                serviceScope.launch {
                    val isNew = currentlyAnalyzingUrls.add(urlKey)
                    if (isNew) {
                        try {
                            analyzeAndReport(urlKey, link, sourcePackage, appLabel)
                        } finally {
                            currentlyAnalyzingUrls.remove(urlKey)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            android.util.Log.e("VigilAccess", "Error processing accessibility event", t)
        }
    }

    private fun inspectNodeRecursive(
        node: AccessibilityNodeInfo?,
        detectedLinks: MutableList<DetectedLinkNode>,
        depth: Int
    ) {
        if (node == null || depth > 40) return

        try {
            if (node.isPassword) {
                return
            }

            val nodePkg = node.packageName?.toString() ?: ""
            if (nodePkg == packageName) {
                return
            }

            val viewId = node.viewIdResourceName?.lowercase() ?: ""
            val isBrowserUrlBar = viewId.contains("url_bar") ||
                    viewId.contains("search_box") ||
                    viewId.contains("location_bar") ||
                    viewId.contains("address_bar") ||
                    viewId.contains("url_field")

            val text = node.text?.toString()
            if (!text.isNullOrBlank()) {
                if (isBrowserUrlBar) {
                    extractBrowserUrl(text, node, detectedLinks)
                } else {
                    extractLinksFromText(text, node, detectedLinks)
                }
            }

            val contentDesc = node.contentDescription?.toString()
            if (!contentDesc.isNullOrBlank() && contentDesc != text) {
                extractLinksFromText(contentDesc, node, detectedLinks)
            }

            val childCount = node.childCount
            for (i in 0 until childCount) {
                val child = node.getChild(i)
                if (child != null) {
                    inspectNodeRecursive(child, detectedLinks, depth + 1)
                }
            }
        } catch (_: Exception) {}
    }

    private fun extractBrowserUrl(
        text: String,
        node: AccessibilityNodeInfo,
        detectedLinks: MutableList<DetectedLinkNode>
    ) {
        val trimmed = text.trim()
        if (trimmed.length < 3 || trimmed.contains(" ")) return

        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty || bounds.width() <= 10 || bounds.height() <= 10) return

        if (trimmed.contains(".") && !trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            val fullUrl = "https://$trimmed"
            detectedLinks.add(DetectedLinkNode(fullUrl, bounds))
        } else {
            extractLinksFromText(trimmed, node, detectedLinks)
        }
    }

    private fun extractLinksFromText(
        text: String,
        node: AccessibilityNodeInfo,
        detectedLinks: MutableList<DetectedLinkNode>
    ) {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        if (bounds.isEmpty || bounds.width() <= 10 || bounds.height() <= 10) {
            return
        }

        val matcher = URL_REGEX.matcher(text)
        val textLength = text.length.coerceAtLeast(1)

        // Layout Brain: Estimate text line height and individual link line coordinates
        val explicitLines = text.count { it == '\n' } + 1
        val charsPerLine = ((bounds.width() / dp(8)).coerceIn(20, 60))
        val wrappedLines = (textLength / charsPerLine) + 1
        val estimatedTotalLines = Math.max(explicitLines, wrappedLines).coerceAtLeast(1)
        val estimatedLineHeight = (bounds.height() / estimatedTotalLines).coerceIn(dp(22), dp(55))

        while (matcher.find()) {
            val url = matcher.group()
            if (url.length >= 5 && !url.contains(" ")) {
                val startIndex = matcher.start()

                // Calculate the line number where this specific link begins
                val textBefore = text.substring(0, startIndex)
                val lineBreaksBefore = textBefore.count { it == '\n' }
                val lastLineLength = startIndex - (textBefore.lastIndexOf('\n') + 1)
                val wrapsBefore = (lastLineLength / charsPerLine)
                val lineIndex = (lineBreaksBefore + wrapsBefore).coerceIn(0, estimatedTotalLines - 1)

                val linkTop = (bounds.top + (lineIndex * estimatedLineHeight)).coerceIn(bounds.top, (bounds.bottom - dp(30)).coerceAtLeast(bounds.top))
                val linkBottom = (linkTop + estimatedLineHeight).coerceAtMost(bounds.bottom)

                val linkBounds = Rect(bounds.left, linkTop, bounds.right, linkBottom)
                detectedLinks.add(DetectedLinkNode(url, linkBounds))
            }
        }
    }

    private fun extractDomain(rawUrl: String): String {
        val clean = if (rawUrl.startsWith("http://", ignoreCase = true) || rawUrl.startsWith("https://", ignoreCase = true)) {
            rawUrl
        } else {
            "https://$rawUrl"
        }
        return try {
            val uri = java.net.URI(clean)
            val host = uri.host ?: clean.substringAfter("://").substringBefore("/")
            host.removePrefix("www.").lowercase()
        } catch (_: Exception) {
            clean.substringAfter("://").substringBefore("/").removePrefix("www.").lowercase()
        }
    }

    private fun deduplicateLinks(rawList: List<DetectedLinkNode>): List<DetectedLinkNode> {
        if (rawList.size <= 1) return rawList

        val result = mutableListOf<DetectedLinkNode>()

        for (candidate in rawList) {
            val candidateDomain = extractDomain(candidate.url)
            val candidateIsFullUrl = candidate.url.startsWith("http://", ignoreCase = true) ||
                    candidate.url.startsWith("https://", ignoreCase = true) ||
                    candidate.url.contains("/")

            var duplicateIndex = -1
            for (i in result.indices) {
                val existing = result[i]
                val existingDomain = extractDomain(existing.url)

                val sameDomain = candidateDomain.isNotEmpty() && candidateDomain == existingDomain
                val sameUrl = candidate.url.trim().equals(existing.url.trim(), ignoreCase = true)

                // Spatial proximity: are they part of the same chat bubble / hero preview card?
                val verticalDist = Math.abs(candidate.bounds.top - existing.bounds.top)
                val horizontalDist = Math.abs(candidate.bounds.left - existing.bounds.left)
                val inSameBubble = horizontalDist < dp(100) && verticalDist < dp(280)

                // Exact same URL in close proximity
                if (sameUrl && (inSameBubble || verticalDist < dp(50))) {
                    duplicateIndex = i
                    break
                }

                // Hero preview card vs message body text sharing the same domain
                if (sameDomain && inSameBubble) {
                    duplicateIndex = i
                    break
                }
            }

            if (duplicateIndex >= 0) {
                val existing = result[duplicateIndex]
                val existingIsFullUrl = existing.url.startsWith("http://", ignoreCase = true) ||
                        existing.url.startsWith("https://", ignoreCase = true) ||
                        existing.url.contains("/")

                // Prefer the full URL or the message body text over the bare hero preview domain
                if ((candidateIsFullUrl && !existingIsFullUrl) ||
                    (candidate.url.length > existing.url.length && candidate.bounds.top >= existing.bounds.top)) {
                    result[duplicateIndex] = candidate
                }
            } else {
                result.add(candidate)
            }
        }

        return result
    }

    private suspend fun analyzeAndReport(
        urlKey: String,
        link: DetectedLinkNode,
        sourcePackage: String,
        appLabel: String
    ) {
        val app = application as? VigilApplication ?: return
        val analysisEngine = app.container.analysisEngine
        val scanRepository = app.container.scanRepository
        val keyStoreManager = app.container.keyStoreManager

        // Normalize bare URLs (e.g. iodb.fjzxu.xyz -> https://iodb.fjzxu.xyz)
        val normalizedUrl = if (urlKey.startsWith("http://", ignoreCase = true) || urlKey.startsWith("https://", ignoreCase = true)) {
            urlKey
        } else {
            "https://$urlKey"
        }

        // 1. Perform deterministic on-device analysis
        val report = analysisEngine.analyzeUrl(
            rawUrl = normalizedUrl,
            sourcePackage = sourcePackage,
            sourceAppLabel = appLabel
        )

        // Ensure blue progression is visible for a perceptible duration (650ms)
        delay(650)

        // 2. Save scan locally
        scanRepository.saveScan(report)

        // 3. Update shield beside the bubble to the final verdict
        val shouldShowOverlay = when (report.riskLevel) {
            RiskLevel.DANGEROUS, RiskLevel.CAUTION -> true
            RiskLevel.SAFE -> keyStoreManager.showOverlayOnSafe
            RiskLevel.UNKNOWN -> true
        }

        if (android.provider.Settings.canDrawOverlays(this)) {
            withContext(Dispatchers.Main) {
                val currentBounds = lastKnownBounds[urlKey] ?: link.bounds
                if (shouldShowOverlay) {
                    showFloatingInfoBadge(urlKey, report, currentBounds)
                } else {
                    dismissFloatingOverlay(urlKey)
                }
            }
        }
    }

    private fun showFloatingScanning(urlKey: String, bounds: Rect) {
        try {
            val overlay = VigilOverlayService.instance
            if (overlay != null && android.provider.Settings.canDrawOverlays(this)) {
                val domain = extractDomain(urlKey)
                overlay.renderScanningOverlay(urlKey, urlKey, domain, bounds)
                return
            }
            val intent = Intent(this, VigilOverlayService::class.java).apply {
                action = VigilOverlayService.ACTION_SHOW_SCANNING
                putExtra(VigilOverlayService.EXTRA_URL_KEY, urlKey)
                putExtra(VigilOverlayService.EXTRA_URL, urlKey)
                putExtra(VigilOverlayService.EXTRA_BOUNDS_LEFT, bounds.left)
                putExtra(VigilOverlayService.EXTRA_BOUNDS_TOP, bounds.top)
                putExtra(VigilOverlayService.EXTRA_BOUNDS_RIGHT, bounds.right)
                putExtra(VigilOverlayService.EXTRA_BOUNDS_BOTTOM, bounds.bottom)
            }
            startService(intent)
        } catch (_: Exception) {}
    }

    private fun dismissFloatingOverlay(urlKey: String) {
        try {
            val overlay = VigilOverlayService.instance
            if (overlay != null) {
                overlay.dismissOverlay(urlKey)
                return
            }
            val intent = Intent(this, VigilOverlayService::class.java).apply {
                action = VigilOverlayService.ACTION_DISMISS
                putExtra(VigilOverlayService.EXTRA_URL_KEY, urlKey)
                putExtra(VigilOverlayService.EXTRA_URL, urlKey)
            }
            startService(intent)
        } catch (_: Exception) {}
    }

    private fun showFloatingInfoBadge(urlKey: String, report: SecurityReport, bounds: Rect) {
        try {
            val overlay = VigilOverlayService.instance
            if (overlay != null && android.provider.Settings.canDrawOverlays(this)) {
                overlay.renderVerdictOverlay(
                    key = urlKey,
                    reportId = report.reportId,
                    url = urlKey,
                    domain = report.destinationDomain,
                    risk = report.riskLevel,
                    summary = report.summary,
                    action = report.recommendedAction,
                    trustScore = report.trustScore,
                    confidence = report.confidence.displayName,
                    confidenceScore = report.confidenceScore,
                    bounds = bounds
                )
                return
            }
            val intent = Intent(this, VigilOverlayService::class.java).apply {
                action = VigilOverlayService.ACTION_SHOW_INDICATOR
                putExtra(VigilOverlayService.EXTRA_URL_KEY, urlKey)
                putExtra(VigilOverlayService.EXTRA_REPORT_ID, report.reportId)
                putExtra(VigilOverlayService.EXTRA_URL, urlKey)
                putExtra(VigilOverlayService.EXTRA_DOMAIN, report.destinationDomain)
                putExtra(VigilOverlayService.EXTRA_RISK, report.riskLevel.name)
                putExtra(VigilOverlayService.EXTRA_SUMMARY, report.summary)
                putExtra(VigilOverlayService.EXTRA_ACTION, report.recommendedAction)
                putExtra(VigilOverlayService.EXTRA_TRUST_SCORE, report.trustScore)
                putExtra(VigilOverlayService.EXTRA_CONFIDENCE, report.confidence.displayName)
                putExtra(VigilOverlayService.EXTRA_CONFIDENCE_SCORE, report.confidenceScore)
                putExtra(VigilOverlayService.EXTRA_BOUNDS_LEFT, bounds.left)
                putExtra(VigilOverlayService.EXTRA_BOUNDS_TOP, bounds.top)
                putExtra(VigilOverlayService.EXTRA_BOUNDS_RIGHT, bounds.right)
                putExtra(VigilOverlayService.EXTRA_BOUNDS_BOTTOM, bounds.bottom)
            }
            startService(intent)
        } catch (_: Exception) {}
    }

    private fun syncVisibleOverlays(visibleKeys: ArrayList<String>) {
        try {
            val overlay = VigilOverlayService.instance
            if (overlay != null) {
                overlay.syncVisibleKeys(visibleKeys.toSet())
                return
            }
            val intent = Intent(this, VigilOverlayService::class.java).apply {
                action = VigilOverlayService.ACTION_SYNC_VISIBLE
                putStringArrayListExtra(VigilOverlayService.EXTRA_VISIBLE_KEYS, visibleKeys)
            }
            startService(intent)
        } catch (_: Exception) {}
    }

    private fun getAppLabel(packageName: String): String {
        return try {
            val pm = packageManager
            val info = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(info).toString()
        } catch (_: Exception) {
            packageName
        }
    }

    private fun dismissAllOverlays() {
        try {
            val overlay = VigilOverlayService.instance
            if (overlay != null) {
                overlay.removeAllOverlays()
                return
            }
            val intent = Intent(this, VigilOverlayService::class.java).apply {
                action = VigilOverlayService.ACTION_DISMISS
            }
            startService(intent)
        } catch (_: Exception) {}
    }

    private fun isIgnoredPackage(pkg: String): Boolean {
        if (pkg.isEmpty() || pkg == packageName) return true
        if (pkg == "com.android.systemui" || pkg == "android") return true
        val lower = pkg.lowercase()
        return lower.contains("inputmethod") || lower.contains("ime") || lower.contains("keyboard")
    }

    private fun isLauncherPackage(pkg: String): Boolean {
        val lower = pkg.lowercase()
        if (lower.contains("launcher") || lower.contains("upslide") || lower == "com.android.launcher3" || lower == "com.bbk.launcher2") {
            return true
        }
        return try {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            val resolveInfo = packageManager.resolveActivity(intent, 0)
            resolveInfo?.activityInfo?.packageName == pkg
        } catch (_: Exception) {
            false
        }
    }

    override fun onInterrupt() {}
}
