package com.vigil.security.threatintel

import com.vigil.security.core.model.Evidence
import com.vigil.security.core.model.RiskLevel

data class ThreatIntelResult(
    val providerName: String,
    val isEvaluated: Boolean,
    val threatDetected: Boolean,
    val riskLevel: RiskLevel = RiskLevel.SAFE,
    val threatType: String? = null,
    val evidence: List<Evidence> = emptyList(),
    val errorMessage: String? = null
)

interface ThreatIntelligenceProvider {
    val providerName: String
    val isConfigured: Boolean
    suspend fun checkUrl(normalizedUrl: String, domain: String): ThreatIntelResult
}
