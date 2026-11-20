package com.vigil.security.core.model

enum class RiskLevel(val displayName: String) {
    SAFE("Safe"),
    CAUTION("Caution"),
    DANGEROUS("Dangerous"),
    UNKNOWN("Unknown")
}

enum class ConfidenceLevel(val displayName: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    VERIFIED("Verified")
}

enum class CheckStatus {
    PASSED,
    WARNING,
    FAILED,
    UNAVAILABLE
}

data class AnalysisCheck(
    val stage: String,
    val name: String,
    val status: CheckStatus,
    val explanation: String,
    val latencyMs: Long = 0L
)

data class Evidence(
    val ruleId: String,
    val title: String,
    val description: String,
    val severity: RiskLevel,
    val indicatorType: String,
    val technicalDetail: String? = null
)

data class SecurityReport(
    val reportId: String,
    val originalUrl: String,
    val normalizedUrl: String,
    val destinationDomain: String,
    val sourcePackage: String? = null,
    val sourceAppLabel: String? = null,
    val riskLevel: RiskLevel,
    val confidence: ConfidenceLevel,
    val summary: String,
    val findings: List<Evidence>,
    val checksCompleted: List<AnalysisCheck>,
    val checksUnavailable: List<String> = emptyList(),
    val recommendedAction: String,
    val timestamp: Long = System.currentTimeMillis(),
    val trustScore: Int = when (riskLevel) {
        RiskLevel.SAFE -> 96
        RiskLevel.CAUTION -> 48
        RiskLevel.DANGEROUS -> 8
        RiskLevel.UNKNOWN -> 50
    },
    val confidenceScore: Int = when (confidence) {
        ConfidenceLevel.VERIFIED -> 98
        ConfidenceLevel.HIGH -> 88
        ConfidenceLevel.MEDIUM -> 65
        ConfidenceLevel.LOW -> 35
    }
)

data class ThreatDefinitionInfo(
    val version: String = "2026.11.19",
    val releaseDate: String = "19 Nov 2026",
    val signatureCount: Int = 14280,
    val lastCheckedTimestamp: Long = System.currentTimeMillis(),
    val updateAvailable: Boolean = false,
    val latestVersion: String? = null,
    val updateDescription: String? = null
)

data class ProtectionState(
    val isProtectionActive: Boolean = false,
    val isAccessibilityEnabled: Boolean = false,
    val isOverlayPermissionGranted: Boolean = false,
    val linksAnalyzedCount: Int = 0,
    val suspiciousDetectedCount: Int = 0,
    val lastActiveTimestamp: Long = 0L
)
