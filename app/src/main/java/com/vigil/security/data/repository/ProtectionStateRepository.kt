package com.vigil.security.data.repository

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import com.vigil.security.core.model.ProtectionState
import com.vigil.security.core.security.KeyStoreManager
import com.vigil.security.service.accessibility.VigilAccessibilityService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

class ProtectionStateRepository(
    private val context: Context,
    private val keyStoreManager: KeyStoreManager,
    private val scanRepository: ScanRepository
) {

    private val _refreshTrigger = MutableStateFlow(System.currentTimeMillis())

    fun refresh() {
        _refreshTrigger.value = System.currentTimeMillis()
    }

    fun isAccessibilityEnabled(): Boolean {
        // 1. Check live in-memory running flag from service
        if (VigilAccessibilityService.isServiceRunning) {
            return true
        }

        // 2. Check Android Settings.Secure for persistent permission state
        try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            val expectedPackage = context.packageName
            if (enabledServices.contains(expectedPackage) && enabledServices.contains("VigilAccessibilityService")) {
                return true
            }
        } catch (_: Exception) {}

        // 3. Fallback to system AccessibilityManager list with ALL feedback types
        return try {
            val am = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager ?: return false
            val allServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
            val genericServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
            (allServices + genericServices).any {
                it.resolveInfo?.serviceInfo?.packageName == context.packageName
            }
        } catch (_: Exception) {
            false
        }
    }

    fun isOverlayGranted(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    var isMonitoringEnabled: Boolean
        get() = keyStoreManager.isMonitoringEnabled
        set(value) {
            keyStoreManager.isMonitoringEnabled = value
            refresh()
        }

    fun getProtectionStateFlow(): Flow<ProtectionState> {
        return combine(
            _refreshTrigger,
            scanRepository.getTotalScansCount(),
            scanRepository.getSuspiciousScansCount()
        ) { _, totalScans, suspiciousScans ->
            val accessibility = isAccessibilityEnabled()
            val overlay = isOverlayGranted()
            val monitoring = keyStoreManager.isMonitoringEnabled

            val isActive = monitoring && accessibility

            ProtectionState(
                isProtectionActive = isActive,
                isAccessibilityEnabled = accessibility,
                isOverlayPermissionGranted = overlay,
                linksAnalyzedCount = totalScans,
                suspiciousDetectedCount = suspiciousScans,
                lastActiveTimestamp = System.currentTimeMillis()
            )
        }
    }
}
