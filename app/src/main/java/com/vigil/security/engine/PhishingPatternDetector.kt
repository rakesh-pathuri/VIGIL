package com.vigil.security.engine

import com.vigil.security.core.model.Evidence
import com.vigil.security.core.model.RiskLevel
import java.net.URI

object PhishingPatternDetector {

    private val HIGH_RISK_TLDS = setOf(
        "top", "xyz", "buzz", "rest", "surf", "click", "work",
        "loan", "zip", "mov", "cam", "fit", "gq", "cf", "tk", "ml", "ga"
    )

    private val SUSPICIOUS_PATH_KEYWORDS = listOf(
        "login", "signin", "sign-in", "log-in", "verify", "verification",
        "account-update", "account-suspended", "confirm-identity",
        "update-billing", "wallet-connect", "security-alert", "recover",
        "passcode", "banking", "online-banking", "kyc-verification"
    )

    fun analyze(normalizedResult: NormalizedUrlResult, parsedHost: ParsedHost): List<Evidence> {
        val findings = mutableListOf<Evidence>()
        val tld = parsedHost.tld.lowercase()
        val domain = parsedHost.registeredDomain.lowercase()
        val path = normalizedResult.path.lowercase()
        val fullUrl = normalizedResult.normalizedUrl.lowercase()

        // 1. High-risk TLD check
        if (HIGH_RISK_TLDS.contains(tld)) {
            findings.add(
                Evidence(
                    ruleId = "PHISHING_TLD_HIGH_RISK",
                    title = "High-Risk Top-Level Domain (.$tld)",
                    description = "The top-level domain '.$tld' has a disproportionate prevalence in disposable phishing and malicious distribution infrastructure.",
                    severity = RiskLevel.CAUTION,
                    indicatorType = "TLD Reputation",
                    technicalDetail = "TLD: .$tld"
                )
            )
        }

        // 2. Excessive hyphens in domain name
        val hyphenCount = domain.count { it == '-' }
        if (hyphenCount >= 3) {
            findings.add(
                Evidence(
                    ruleId = "PHISHING_EXCESSIVE_HYPHENS",
                    title = "Excessive Hyphenation in Domain",
                    description = "Domain name contains $hyphenCount hyphens ($domain). Phishing campaigns frequently chain hyphens to simulate complex enterprise hostnames.",
                    severity = RiskLevel.CAUTION,
                    indicatorType = "Structural Anomaly",
                    technicalDetail = "Hyphen count: $hyphenCount"
                )
            )
        }

        // 3. Userinfo in URL (e.g., http://google.com@evil.com)
        try {
            val uri = URI(normalizedResult.normalizedUrl)
            if (uri.userInfo != null) {
                findings.add(
                    Evidence(
                        ruleId = "PHISHING_USERINFO_DECEPTION",
                        title = "Embedded Credentials / Deceptive Userinfo",
                        description = "URL contains embedded userinfo ('${uri.userInfo}@'). This technique is often used to deceive users about the true destination of the link.",
                        severity = RiskLevel.DANGEROUS,
                        indicatorType = "Deceptive Structure",
                        technicalDetail = "UserInfo: ${uri.userInfo}"
                    )
                )
            }
        } catch (_: Exception) {}

        // 4. Sensitive credential collection path
        val matchedKeywords = SUSPICIOUS_PATH_KEYWORDS.filter { path.contains(it) }
        if (matchedKeywords.isNotEmpty()) {
            val isKnownFinancialOrBrand = domain.contains("bank") || domain.contains("paypal")
            findings.add(
                Evidence(
                    ruleId = "PHISHING_CREDENTIAL_PATH",
                    title = "Credential Harvesting Path Patterns",
                    description = "The URL path indicates an authentication or identity verification form ($matchedKeywords). Verify that you trust the domain ($domain) before entering any credentials.",
                    severity = if (isKnownFinancialOrBrand) RiskLevel.DANGEROUS else RiskLevel.CAUTION,
                    indicatorType = "Path Heuristics",
                    technicalDetail = "Matched patterns: $matchedKeywords"
                )
            )
        }

        // 5. Insecure HTTP protocol with sensitive path
        if (normalizedResult.scheme == "http" && matchedKeywords.isNotEmpty()) {
            findings.add(
                Evidence(
                    ruleId = "PHISHING_INSECURE_LOGIN",
                    title = "Unencrypted Login/Verification Form",
                    description = "This form uses unencrypted HTTP. Any passwords or credentials transmitted through this link can be intercepted by intermediaries on the network.",
                    severity = RiskLevel.DANGEROUS,
                    indicatorType = "Insecure Transport"
                )
            )
        }

        return findings
    }
}
