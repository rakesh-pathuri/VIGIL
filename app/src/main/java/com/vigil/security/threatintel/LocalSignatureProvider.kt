package com.vigil.security.threatintel

import com.vigil.security.core.model.Evidence
import com.vigil.security.core.model.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LocalSignatureProvider : ThreatIntelligenceProvider {

    override val providerName: String = "Vigil Local Threat Intelligence"
    override val isConfigured: Boolean = true

    // Offline database of verified malicious signatures, known malware hosts, and standard security test domains
    private val knownMaliciousDomains = java.util.concurrent.ConcurrentHashMap.newKeySet<String>().apply {
        addAll(listOf(
            "testsafebrowsing.appspot.com",
            "malware.wicar.org",
            "phishing.test",
            "malware.test",
            "evil-phishing-test.com",
            "secure-login-apple-support-verify.com",
            "paypal-account-security-update.top",
            "metamask-wallet-recovery.xyz",
            "whatsapp-free-gift-airdrop.xyz",
            "telegram-premium-free-promo.top",
            "instagram-verify-badge-claim.xyz",
            "chase-online-account-locked.click",
            "binance-kyc-reactivate.rest"
        ))
    }

    private val suspiciousDomainPatterns = java.util.concurrent.CopyOnWriteArrayList<String>().apply {
        addAll(listOf(
            "gift-card-claim",
            "airdrop-crypto",
            "account-verification-required",
            "security-update-action",
            "claim-bonus-now",
            "free-vbucks",
            "free-nitro"
        ))
    }

    fun addSignatures(domains: Set<String>, patterns: List<String>) {
        knownMaliciousDomains.addAll(domains)
        suspiciousDomainPatterns.addAll(patterns)
    }

    override suspend fun checkUrl(normalizedUrl: String, domain: String): ThreatIntelResult =
        withContext(Dispatchers.Default) {
            val lowerDomain = domain.lowercase()
            val lowerUrl = normalizedUrl.lowercase()

            // 1. Exact or suffix match in local malicious database
            val isKnownMalicious = knownMaliciousDomains.any {
                lowerDomain == it || lowerDomain.endsWith(".$it")
            }

            if (isKnownMalicious) {
                return@withContext ThreatIntelResult(
                    providerName = providerName,
                    isEvaluated = true,
                    threatDetected = true,
                    riskLevel = RiskLevel.DANGEROUS,
                    threatType = "Known Malicious / Phishing Domain",
                    evidence = listOf(
                        Evidence(
                            ruleId = "LOCAL_INTEL_MALICIOUS_HOST",
                            title = "Known Threat Signature Match",
                            description = "This destination matches known malicious command-and-control, malware, or phishing signatures in the local threat intelligence database.",
                            severity = RiskLevel.DANGEROUS,
                            indicatorType = "Threat Database",
                            technicalDetail = "Matched domain: $domain"
                        )
                    )
                )
            }

            // 2. Suspicious threat campaign patterns
            val matchedPattern = suspiciousDomainPatterns.firstOrNull { lowerUrl.contains(it) }
            if (matchedPattern != null) {
                return@withContext ThreatIntelResult(
                    providerName = providerName,
                    isEvaluated = true,
                    threatDetected = true,
                    riskLevel = RiskLevel.CAUTION,
                    threatType = "Suspicious Campaign Pattern",
                    evidence = listOf(
                        Evidence(
                            ruleId = "LOCAL_INTEL_SUSPICIOUS_PATTERN",
                            title = "High-Risk Threat Campaign Pattern",
                            description = "The URL matches active social-engineering campaign patterns ('$matchedPattern') cataloged by local threat intelligence.",
                            severity = RiskLevel.CAUTION,
                            indicatorType = "Threat Signature",
                            technicalDetail = "Pattern: $matchedPattern"
                        )
                    )
                )
            }

            ThreatIntelResult(
                providerName = providerName,
                isEvaluated = true,
                threatDetected = false,
                riskLevel = RiskLevel.SAFE
            )
        }
}
