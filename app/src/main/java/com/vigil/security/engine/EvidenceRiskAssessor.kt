package com.vigil.security.engine

import com.vigil.security.core.model.*

object EvidenceRiskAssessor {

    fun assess(
        originalUrl: String,
        normalizedResult: NormalizedUrlResult,
        parsedHost: ParsedHost,
        findings: List<Evidence>,
        checksCompleted: List<AnalysisCheck>,
        checksUnavailable: List<String>,
        sourcePackage: String? = null,
        sourceAppLabel: String? = null
    ): SecurityReport {
        val dangerousFindings = findings.filter { it.severity == RiskLevel.DANGEROUS }
        val cautionFindings = findings.filter { it.severity == RiskLevel.CAUTION }

        val riskLevel: RiskLevel
        val confidence: ConfidenceLevel
        val summary: String
        val recommendedAction: String

        when {
            // Strong evidence of phishing, malware, or brand spoofing
            dangerousFindings.isNotEmpty() -> {
                riskLevel = RiskLevel.DANGEROUS
                confidence = if (dangerousFindings.size >= 2 || checksCompleted.size >= 7) {
                    ConfidenceLevel.HIGH
                } else {
                    ConfidenceLevel.MEDIUM
                }
                val primaryThreat = dangerousFindings.first().title
                summary = "Potential security threat detected: $primaryThreat. Multiple risk factors indicate deceptive or malicious intent."
                recommendedAction = "Avoid interacting with this link. Do not enter credentials, financial information, or download attachments."
            }

            // Suspicious signals detected
            cautionFindings.isNotEmpty() -> {
                riskLevel = RiskLevel.CAUTION
                confidence = if (cautionFindings.size >= 2) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM
                val primarySignal = cautionFindings.first().title
                summary = "Caution advised: $primarySignal identified. While not definitively malicious, abnormal link characteristics were observed."
                recommendedAction = "Proceed with caution. Verify the sender's identity and confirm the authentic destination domain before proceeding."
            }

            // No threat detected by completed checks
            checksCompleted.isNotEmpty() && checksCompleted.any { it.status == CheckStatus.PASSED } -> {
                riskLevel = RiskLevel.SAFE
                confidence = if (checksCompleted.size >= 6) ConfidenceLevel.HIGH else ConfidenceLevel.MEDIUM
                summary = "No known threat detected by the completed security checks. Destination appears to be consistent with standard domain configurations."
                recommendedAction = "Link passed local heuristic checks. Always remain vigilant regarding sensitive personal information."
            }

            // Incomplete checks or insufficient data
            else -> {
                riskLevel = RiskLevel.UNKNOWN
                confidence = ConfidenceLevel.LOW
                summary = "Insufficient telemetry available to determine link safety. Analysis incomplete."
                recommendedAction = "Open with caution or verify the destination URL through trusted channels."
            }
        }

        val reportId = "rep_" + System.currentTimeMillis().toString() + "_" + (1000..9999).random()

        return SecurityReport(
            reportId = reportId,
            originalUrl = originalUrl,
            normalizedUrl = normalizedResult.normalizedUrl,
            destinationDomain = parsedHost.registeredDomain.ifEmpty { normalizedResult.host },
            sourcePackage = sourcePackage,
            sourceAppLabel = sourceAppLabel,
            riskLevel = riskLevel,
            confidence = confidence,
            summary = summary,
            findings = findings,
            checksCompleted = checksCompleted,
            checksUnavailable = checksUnavailable,
            recommendedAction = recommendedAction,
            timestamp = System.currentTimeMillis()
        )
    }
}
