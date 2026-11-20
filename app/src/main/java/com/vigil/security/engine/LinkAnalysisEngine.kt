package com.vigil.security.engine

import com.vigil.security.core.model.*
import com.vigil.security.threatintel.GoogleSafeBrowsingProvider
import com.vigil.security.threatintel.LocalSignatureProvider
import com.vigil.security.threatintel.ThreatIntelligenceProvider
import com.vigil.security.threatintel.VirusTotalProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class LinkAnalysisEngine(
    private val localSignatureProvider: LocalSignatureProvider,
    private val googleSafeBrowsingProvider: GoogleSafeBrowsingProvider,
    private val virusTotalProvider: VirusTotalProvider
) {

    suspend fun analyzeUrl(
        rawUrl: String,
        sourcePackage: String? = null,
        sourceAppLabel: String? = null
    ): SecurityReport = withContext(Dispatchers.Default) {
        val findings = mutableListOf<Evidence>()
        val checksCompleted = mutableListOf<AnalysisCheck>()
        val checksUnavailable = mutableListOf<String>()

        // Stage 1: URL extraction and normalization
        val startNorm = System.currentTimeMillis()
        val normResult = UrlNormalizer.normalize(rawUrl)
        if (!normResult.isValid) {
            checksCompleted.add(
                AnalysisCheck(
                    stage = "Stage 1",
                    name = "URL Normalization",
                    status = CheckStatus.FAILED,
                    explanation = normResult.error ?: "Malformed URL",
                    latencyMs = System.currentTimeMillis() - startNorm
                )
            )
            return@withContext EvidenceRiskAssessor.assess(
                originalUrl = rawUrl,
                normalizedResult = normResult,
                parsedHost = ParsedHost(normResult.host, "", normResult.host, ""),
                findings = listOf(
                    Evidence(
                        ruleId = "URL_MALFORMED",
                        title = "Malformed URL Structure",
                        description = "The provided link does not comply with standard URI syntax and cannot be parsed safely.",
                        severity = RiskLevel.CAUTION,
                        indicatorType = "Syntax Validation"
                    )
                ),
                checksCompleted = checksCompleted,
                checksUnavailable = listOf("Hostname Analysis", "Threat Intelligence"),
                sourcePackage = sourcePackage,
                sourceAppLabel = sourceAppLabel
            )
        }
        checksCompleted.add(
            AnalysisCheck(
                stage = "Stage 1",
                name = "URL Normalization",
                status = CheckStatus.PASSED,
                explanation = "Syntactically valid ${normResult.scheme.uppercase()} URI",
                latencyMs = System.currentTimeMillis() - startNorm
            )
        )

        // Stage 2: URL parsing and hostname identification
        val startParse = System.currentTimeMillis()
        val parsedHost = HostnameParser.parse(normResult.host)
        checksCompleted.add(
            AnalysisCheck(
                stage = "Stage 2",
                name = "Hostname Parsing",
                status = CheckStatus.PASSED,
                explanation = "Resolved domain: ${parsedHost.registeredDomain}",
                latencyMs = System.currentTimeMillis() - startParse
            )
        )

        // Stage 3: Punycode and Unicode homograph analysis
        val startHomo = System.currentTimeMillis()
        val homoFindings = HomographDetector.analyze(parsedHost.fullHost)
        findings.addAll(homoFindings)
        checksCompleted.add(
            AnalysisCheck(
                stage = "Stage 3",
                name = "Homograph & Punycode Analysis",
                status = if (homoFindings.isEmpty()) CheckStatus.PASSED else CheckStatus.WARNING,
                explanation = if (homoFindings.isEmpty()) "Standard Latin character set verified" else "${homoFindings.size} confusable script anomalies detected",
                latencyMs = System.currentTimeMillis() - startHomo
            )
        )

        // Stage 4: Lookalike & brand impersonation detection
        val startImper = System.currentTimeMillis()
        val imperFindings = ImpersonationDetector.analyze(parsedHost)
        findings.addAll(imperFindings)
        checksCompleted.add(
            AnalysisCheck(
                stage = "Stage 4",
                name = "Brand Impersonation Detection",
                status = if (imperFindings.isEmpty()) CheckStatus.PASSED else CheckStatus.WARNING,
                explanation = if (imperFindings.isEmpty()) "No brand typosquatting detected" else "${imperFindings.size} brand similarity matches found",
                latencyMs = System.currentTimeMillis() - startImper
            )
        )

        // Stage 5: IP-address URL detection
        val startIp = System.currentTimeMillis()
        val ipFindings = IpAddressDetector.analyze(parsedHost.fullHost)
        findings.addAll(ipFindings)
        checksCompleted.add(
            AnalysisCheck(
                stage = "Stage 5",
                name = "IP Host Analysis",
                status = if (ipFindings.isEmpty()) CheckStatus.PASSED else CheckStatus.WARNING,
                explanation = if (ipFindings.isEmpty()) "Hostname uses domain registration" else "Raw or obfuscated IP address host",
                latencyMs = System.currentTimeMillis() - startIp
            )
        )

        // Stage 6: Suspicious subdomain analysis
        val startSub = System.currentTimeMillis()
        val subFindings = SubdomainAnalyzer.analyze(parsedHost)
        findings.addAll(subFindings)
        checksCompleted.add(
            AnalysisCheck(
                stage = "Stage 6",
                name = "Subdomain Hierarchy & Entropy",
                status = if (subFindings.isEmpty()) CheckStatus.PASSED else CheckStatus.WARNING,
                explanation = if (subFindings.isEmpty()) "Normal subdomain depth and entropy" else "${subFindings.size} subdomain irregularities detected",
                latencyMs = System.currentTimeMillis() - startSub
            )
        )

        // Stage 7 & 8: URL shortener identification & redirect analysis
        val startShort = System.currentTimeMillis()
        val shortenerResult = UrlShortenerDetector.inspectShortener(
            url = normResult.normalizedUrl,
            registeredDomain = parsedHost.registeredDomain
        )
        findings.addAll(shortenerResult.findings)
        checksCompleted.add(
            AnalysisCheck(
                stage = "Stage 7 & 8",
                name = "Shortener & Redirect Tracing",
                status = if (!shortenerResult.isShortener) CheckStatus.PASSED else CheckStatus.WARNING,
                explanation = if (!shortenerResult.isShortener) "Direct domain (no shortener detected)" else "Shortener service resolved: ${shortenerResult.expandedUrl ?: "Undetermined"}",
                latencyMs = System.currentTimeMillis() - startShort
            )
        )

        // Stage 9: Known malicious URL reputation checks
        // 9a. Local Threat Intelligence (Always available)
        val startLocal = System.currentTimeMillis()
        val localResult = localSignatureProvider.checkUrl(normResult.normalizedUrl, parsedHost.registeredDomain)
        findings.addAll(localResult.evidence)
        checksCompleted.add(
            AnalysisCheck(
                stage = "Stage 9a",
                name = "Local Threat Intelligence",
                status = if (localResult.threatDetected) CheckStatus.FAILED else CheckStatus.PASSED,
                explanation = if (localResult.threatDetected) "Signature match: ${localResult.threatType}" else "No matching signature in offline database",
                latencyMs = System.currentTimeMillis() - startLocal
            )
        )

        // 9b. Google Safe Browsing (if configured)
        if (googleSafeBrowsingProvider.isConfigured) {
            val startGsb = System.currentTimeMillis()
            val gsbResult = googleSafeBrowsingProvider.checkUrl(normResult.normalizedUrl, parsedHost.registeredDomain)
            if (gsbResult.isEvaluated) {
                findings.addAll(gsbResult.evidence)
                checksCompleted.add(
                    AnalysisCheck(
                        stage = "Stage 9b",
                        name = "Google Safe Browsing",
                        status = if (gsbResult.threatDetected) CheckStatus.FAILED else CheckStatus.PASSED,
                        explanation = if (gsbResult.threatDetected) "Flagged: ${gsbResult.threatType}" else "Clean verdict returned by Google Safe Browsing",
                        latencyMs = System.currentTimeMillis() - startGsb
                    )
                )
            } else {
                checksUnavailable.add("Google Safe Browsing (${gsbResult.errorMessage})")
            }
        } else {
            checksUnavailable.add("Google Safe Browsing (API key unconfigured)")
        }

        // 9c. VirusTotal (if configured)
        if (virusTotalProvider.isConfigured) {
            val startVt = System.currentTimeMillis()
            val vtResult = virusTotalProvider.checkUrl(normResult.normalizedUrl, parsedHost.registeredDomain)
            if (vtResult.isEvaluated) {
                findings.addAll(vtResult.evidence)
                checksCompleted.add(
                    AnalysisCheck(
                        stage = "Stage 9c",
                        name = "VirusTotal Multi-Engine",
                        status = if (vtResult.threatDetected) CheckStatus.FAILED else CheckStatus.PASSED,
                        explanation = if (vtResult.threatDetected) "Multi-vendor alert: ${vtResult.threatType}" else "No vendor detections",
                        latencyMs = System.currentTimeMillis() - startVt
                    )
                )
            } else {
                checksUnavailable.add("VirusTotal (${vtResult.errorMessage})")
            }
        } else {
            checksUnavailable.add("VirusTotal (API key unconfigured)")
        }

        // Stage 10: Phishing and impersonation heuristics
        val startPhish = System.currentTimeMillis()
        val phishFindings = PhishingPatternDetector.analyze(normResult, parsedHost)
        findings.addAll(phishFindings)
        checksCompleted.add(
            AnalysisCheck(
                stage = "Stage 10",
                name = "Phishing & Credential Heuristics",
                status = if (phishFindings.isEmpty()) CheckStatus.PASSED else CheckStatus.WARNING,
                explanation = if (phishFindings.isEmpty()) "Standard URI path and legitimate TLD profile" else "${phishFindings.size} heuristic risk indicators matched",
                latencyMs = System.currentTimeMillis() - startPhish
            )
        )

        // Stage 11: Evidence-based risk assessment
        EvidenceRiskAssessor.assess(
            originalUrl = rawUrl,
            normalizedResult = normResult,
            parsedHost = parsedHost,
            findings = findings,
            checksCompleted = checksCompleted,
            checksUnavailable = checksUnavailable,
            sourcePackage = sourcePackage,
            sourceAppLabel = sourceAppLabel
        )
    }
}
