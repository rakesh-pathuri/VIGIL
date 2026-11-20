package com.vigil.security

import com.vigil.security.core.model.CheckStatus
import com.vigil.security.core.model.RiskLevel
import com.vigil.security.core.security.UrlSanitizer
import com.vigil.security.engine.*
import org.junit.Assert.*
import org.junit.Test

class LinkAnalysisEngineTest {

    @Test
    fun testUrlNormalizer_addsHttpsSchemeAndTrims() {
        val result = UrlNormalizer.normalize("  example.com/login  ")
        assertTrue(result.isValid)
        assertEquals("https://example.com/login", result.normalizedUrl)
        assertEquals("example.com", result.host)
    }

    @Test
    fun testUrlNormalizer_stripsDefaultPorts() {
        val resultHttp = UrlNormalizer.normalize("http://example.com:80/page")
        assertEquals("http://example.com/page", resultHttp.normalizedUrl)

        val resultHttps = UrlNormalizer.normalize("https://example.com:443/secure")
        assertEquals("https://example.com/secure", resultHttps.normalizedUrl)

        val resultCustom = UrlNormalizer.normalize("https://example.com:8443/custom")
        assertEquals("https://example.com:8443/custom", resultCustom.normalizedUrl)
    }

    @Test
    fun testHostnameParser_multiPartTld() {
        val parsed = HostnameParser.parse("secure.login.banking.co.uk")
        assertEquals("co.uk", parsed.tld)
        assertEquals("banking.co.uk", parsed.registeredDomain)
        assertEquals("secure.login", parsed.subdomain)
    }

    @Test
    fun testHostnameParser_singlePartTld() {
        val parsed = HostnameParser.parse("api.v2.services.google.com")
        assertEquals("com", parsed.tld)
        assertEquals("google.com", parsed.registeredDomain)
        assertEquals("api.v2.services", parsed.subdomain)
    }

    @Test
    fun testHomographDetector_detectsPunycode() {
        val findings = HomographDetector.analyze("xn--pple-43d.com")
        assertTrue(findings.isNotEmpty())
        assertEquals("HOMOGRAPH_PUNYCODE", findings.first().ruleId)
    }

    @Test
    fun testHomographDetector_detectsMixedCyrillic() {
        // Cyrillic small 'а' (U+0430) mixed with Latin 'pple.com'
        val fakeApple = "\u0430pple.com"
        val findings = HomographDetector.analyze(fakeApple)
        assertTrue(findings.any { it.ruleId == "HOMOGRAPH_MIXED_SCRIPT" })
    }

    @Test
    fun testHomographDetector_safeForNormalDomain() {
        val findings = HomographDetector.analyze("apple.com")
        assertTrue(findings.isEmpty())
    }

    @Test
    fun testImpersonationDetector_officialBrandSafe() {
        val parsed = HostnameParser.parse("www.apple.com")
        val findings = ImpersonationDetector.analyze(parsed)
        assertTrue(findings.isEmpty())
    }

    @Test
    fun testImpersonationDetector_detectsCombosquatting() {
        val parsed = HostnameParser.parse("paypal-security-login-update.com")
        val findings = ImpersonationDetector.analyze(parsed)
        assertTrue(findings.any { it.ruleId == "IMPERSONATION_COMBOSQUAT" })
    }

    @Test
    fun testImpersonationDetector_detectsTyposquatting() {
        // 'gogle.com' is 1 edit distance from 'google'
        val parsed = HostnameParser.parse("gogle.com")
        val findings = ImpersonationDetector.analyze(parsed)
        assertTrue(findings.any { it.ruleId == "IMPERSONATION_TYPOSQUAT" })
    }

    @Test
    fun testIpAddressDetector_detectsRawIpv4() {
        val findingsPublic = IpAddressDetector.analyze("185.220.101.5")
        assertTrue(findingsPublic.any { it.ruleId == "IP_HOST_IPV4" })
        assertEquals(RiskLevel.DANGEROUS, findingsPublic.first().severity)

        val findingsPrivate = IpAddressDetector.analyze("192.168.1.1")
        assertTrue(findingsPrivate.any { it.ruleId == "IP_HOST_IPV4" })
        assertEquals(RiskLevel.CAUTION, findingsPrivate.first().severity)
    }

    @Test
    fun testSubdomainAnalyzer_detectsExcessiveDepth() {
        val parsed = HostnameParser.parse("a.b.c.d.e.malicious.com")
        val findings = SubdomainAnalyzer.analyze(parsed)
        assertTrue(findings.any { it.ruleId == "SUBDOMAIN_EXCESSIVE_DEPTH" })
    }

    @Test
    fun testSubdomainAnalyzer_detectsDeceptiveKeywords() {
        val parsed = HostnameParser.parse("verify-account-login.evil-site.net")
        val findings = SubdomainAnalyzer.analyze(parsed)
        assertTrue(findings.any { it.ruleId == "SUBDOMAIN_DECEPTIVE_KEYWORD" })
    }

    @Test
    fun testPhishingPatternDetector_detectsHighRiskTldAndCredentials() {
        val norm = UrlNormalizer.normalize("http://account-update.xyz/login.php")
        val parsed = HostnameParser.parse("account-update.xyz")
        val findings = PhishingPatternDetector.analyze(norm, parsed)

        assertTrue(findings.any { it.ruleId == "PHISHING_TLD_HIGH_RISK" })
        assertTrue(findings.any { it.ruleId == "PHISHING_CREDENTIAL_PATH" })
        assertTrue(findings.any { it.ruleId == "PHISHING_INSECURE_LOGIN" })
    }

    @Test
    fun testUrlSanitizer_redactsTokensAndPasswords() {
        val raw = "https://example.com/auth?user=john&token=secret12345&session_id=abc999&page=1"
        val sanitized = UrlSanitizer.sanitizeUrl(raw)

        assertFalse(sanitized.contains("secret12345"))
        assertFalse(sanitized.contains("abc999"))
        assertTrue(sanitized.contains("token=%5BREDACTED%5D") || sanitized.contains("token=[REDACTED]"))
        assertTrue(sanitized.contains("page=1"))
    }

    @Test
    fun testEvidenceRiskAssessor_assignsDangerousOnSevereEvidence() {
        val norm = UrlNormalizer.normalize("https://paypal-verify-login.xyz")
        val parsed = HostnameParser.parse("paypal-verify-login.xyz")
        val findings = ImpersonationDetector.analyze(parsed)

        val report = EvidenceRiskAssessor.assess(
            originalUrl = "https://paypal-verify-login.xyz",
            normalizedResult = norm,
            parsedHost = parsed,
            findings = findings,
            checksCompleted = emptyList(),
            checksUnavailable = emptyList()
        )

        assertEquals(RiskLevel.DANGEROUS, report.riskLevel)
        assertTrue(report.summary.contains("Potential security threat"))
    }
}
