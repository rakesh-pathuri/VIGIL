package com.vigil.security.engine

import com.vigil.security.core.model.Evidence
import com.vigil.security.core.model.RiskLevel
import kotlin.math.min

object ImpersonationDetector {

    data class BrandTarget(
        val brandName: String,
        val officialDomains: Set<String>,
        val highRiskKeywords: List<String> = listOf("login", "verify", "security", "support", "account", "update", "auth")
    )

    private val TARGET_BRANDS = listOf(
        BrandTarget("Google", setOf(
            "google.com", "google.co.uk", "google.co.in", "google.de", "google.fr", "google.it", "google.es",
            "google.ca", "google.com.au", "google.co.jp", "google.com.br", "youtube.com", "youtu.be",
            "gmail.com", "google.dev", "web.dev", "android.com", "chromium.org", "g.co", "goo.gl",
            "googleusercontent.com", "gstatic.com", "share.google"
        )),
        BrandTarget("Apple", setOf("apple.com", "icloud.com", "apple.co", "itunes.com")),
        BrandTarget("Microsoft", setOf("microsoft.com", "live.com", "office.com", "outlook.com", "msn.com", "bing.com", "github.com", "azure.com", "windows.com", "msft.it", "linkedin.com")),
        BrandTarget("PayPal", setOf("paypal.com", "paypal.me")),
        BrandTarget("Amazon", setOf("amazon.com", "amazon.co.uk", "amazon.in", "amazon.de", "amzn.to", "aws.amazon.com", "primevideo.com")),
        BrandTarget("Netflix", setOf("netflix.com")),
        BrandTarget("WhatsApp", setOf("whatsapp.com", "wa.me")),
        BrandTarget("Telegram", setOf("telegram.org", "t.me")),
        BrandTarget("Instagram", setOf("instagram.com")),
        BrandTarget("Facebook", setOf("facebook.com", "fb.com", "messenger.com")),
        BrandTarget("Chase", setOf("chase.com")),
        BrandTarget("Bank of America", setOf("bankofamerica.com")),
        BrandTarget("Wells Fargo", setOf("wellsfargo.com")),
        BrandTarget("Binance", setOf("binance.com")),
        BrandTarget("Coinbase", setOf("coinbase.com"))
    )

    private val COMMON_DICTIONARY_WORDS = setOf(
        "share", "store", "state", "score", "space", "scale", "smart", "sharp",
        "speed", "start", "show", "short", "sheet", "shift", "shape", "shake",
        "shall", "stage", "stand", "still", "study", "style", "sound", "south"
    )

    fun analyze(parsedHost: ParsedHost): List<Evidence> {
        val findings = mutableListOf<Evidence>()
        val regDomain = parsedHost.registeredDomain.lowercase()
        val fullHost = parsedHost.fullHost.lowercase()
        val domainWithoutTld = regDomain.substringBefore('.')

        for (target in TARGET_BRANDS) {
            val isOfficial = target.officialDomains.any { official ->
                regDomain == official || regDomain.endsWith(".$official") ||
                fullHost == official || fullHost.endsWith(".$official")
            } || (target.brandName.equals("Google", ignoreCase = true) && (parsedHost.tld.equals("google", ignoreCase = true) || regDomain.endsWith(".google") || fullHost.endsWith(".google")))
            if (isOfficial) {
                continue // Official brand domain, no impersonation
            }

            val targetKeyword = target.brandName.lowercase().replace(" ", "")

            // 1. Typosquatting check (Levenshtein distance: <= 1 for short brand <= 5 chars, <= 2 for longer)
            val maxAllowedDistance = if (targetKeyword.length <= 5) 1 else 2
            val isCommonWord = COMMON_DICTIONARY_WORDS.contains(domainWithoutTld)
            val distance = levenshteinDistance(domainWithoutTld, targetKeyword)
            if (!isCommonWord && distance in 1..maxAllowedDistance && domainWithoutTld.length >= 4) {
                findings.add(
                    Evidence(
                        ruleId = "IMPERSONATION_TYPOSQUAT",
                        title = "Potential Typosquatting (${target.brandName})",
                        description = "The domain name '$domainWithoutTld' closely resembles the official brand '${target.brandName}' (edit distance: $distance). This is commonly used in deceptive links.",
                        severity = RiskLevel.DANGEROUS,
                        indicatorType = "Typosquatting",
                        technicalDetail = "Registered domain: $regDomain vs Official: ${target.officialDomains.first()}"
                    )
                )
            }

            // 2. Combosquatting (Target brand keyword embedded with security words)
            if (fullHost.contains(targetKeyword)) {
                val hasSecurityKeyword = target.highRiskKeywords.any { fullHost.contains(it) }
                if (hasSecurityKeyword) {
                    findings.add(
                        Evidence(
                            ruleId = "IMPERSONATION_COMBOSQUAT",
                            title = "Brand Impersonation (${target.brandName})",
                            description = "This domain combines the brand name '${target.brandName}' with security or verification keywords, but does not lead to the official domain (${target.officialDomains.first()}).",
                            severity = RiskLevel.DANGEROUS,
                            indicatorType = "Combosquatting",
                            technicalDetail = "Detected in host: $fullHost"
                        )
                    )
                } else {
                    findings.add(
                        Evidence(
                            ruleId = "IMPERSONATION_BRAND_MENTION",
                            title = "Unverified Reference to ${target.brandName}",
                            description = "The URL includes the brand name '${target.brandName}', yet the destination is registered under '$regDomain', not the official brand domain.",
                            severity = RiskLevel.CAUTION,
                            indicatorType = "Brand Mention",
                            technicalDetail = "Official domains: ${target.officialDomains.joinToString(", ")}"
                        )
                    )
                }
            }
        }

        return findings
    }

    private fun levenshteinDistance(s1: String, s2: String): Int {
        val dp = Array(s1.length + 1) { IntArray(s2.length + 1) }
        for (i in 0..s1.length) dp[i][0] = i
        for (j in 0..s2.length) dp[0][j] = j

        for (i in 1..s1.length) {
            for (j in 1..s2.length) {
                val cost = if (s1[i - 1] == s2[j - 1]) 0 else 1
                dp[i][j] = min(
                    dp[i - 1][j] + 1,
                    min(dp[i][j - 1] + 1, dp[i - 1][j - 1] + cost)
                )
            }
        }
        return dp[s1.length][s2.length]
    }
}
