package com.vigil.security.engine

import com.vigil.security.core.model.Evidence
import com.vigil.security.core.model.RiskLevel
import kotlin.math.log2

object SubdomainAnalyzer {

    private val SUSPICIOUS_SUBDOMAIN_KEYWORDS = setOf(
        "login", "signin", "auth", "verify", "secure", "security",
        "update", "account", "banking", "wallet", "support",
        "service", "portal", "confirm", "recovery", "alert"
    )

    fun analyze(parsedHost: ParsedHost): List<Evidence> {
        val findings = mutableListOf<Evidence>()
        val subdomain = parsedHost.subdomain.lowercase()

        if (subdomain.isEmpty()) {
            return findings
        }

        val parts = subdomain.split('.')

        // 1. Excessive subdomain depth
        if (parts.size >= 4) {
            findings.add(
                Evidence(
                    ruleId = "SUBDOMAIN_EXCESSIVE_DEPTH",
                    title = "Excessive Subdomain Nesting",
                    description = "The host contains ${parts.size} levels of subdomains. Complex subdomain chains are commonly used to disguise the authentic registered domain (${parsedHost.registeredDomain}).",
                    severity = RiskLevel.CAUTION,
                    indicatorType = "Subdomain Hierarchy",
                    technicalDetail = "Subdomains: $subdomain"
                )
            )
        }

        // 2. High Shannon entropy (randomness) in subdomain
        for (part in parts) {
            if (part.length >= 10) {
                val entropy = calculateShannonEntropy(part)
                if (entropy > 3.8) {
                    findings.add(
                        Evidence(
                            ruleId = "SUBDOMAIN_HIGH_ENTROPY",
                            title = "High Randomness Subdomain",
                            description = "Subdomain '$part' exhibits high character randomness (entropy: ${"%.2f".format(entropy)}), characteristic of automated or disposable phishing infrastructure.",
                            severity = RiskLevel.CAUTION,
                            indicatorType = "Algorithmic Generation",
                            technicalDetail = "Entropy score: $entropy"
                        )
                    )
                    break
                }
            }
        }

        // 3. Security keyword in subdomain pointing to unrelated registered domain
        val matchingKeywords = parts.filter { part ->
            SUSPICIOUS_SUBDOMAIN_KEYWORDS.any { part.contains(it) }
        }
        if (matchingKeywords.isNotEmpty()) {
            findings.add(
                Evidence(
                    ruleId = "SUBDOMAIN_DECEPTIVE_KEYWORD",
                    title = "Sensitive Keyword in Subdomain",
                    description = "Subdomain contains sensitive credential keywords (${matchingKeywords.joinToString(", ")}), but the parent domain is '${parsedHost.registeredDomain}'.",
                    severity = RiskLevel.CAUTION,
                    indicatorType = "Deceptive Framing",
                    technicalDetail = "Subdomain keywords: $matchingKeywords"
                )
            )
        }

        return findings
    }

    private fun calculateShannonEntropy(input: String): Double {
        if (input.isEmpty()) return 0.0
        val frequencyMap = mutableMapOf<Char, Int>()
        for (c in input) {
            frequencyMap[c] = (frequencyMap[c] ?: 0) + 1
        }
        val len = input.length.toDouble()
        var entropy = 0.0
        for ((_, count) in frequencyMap) {
            val p = count / len
            entropy -= p * (log2(p))
        }
        return entropy
    }
}
