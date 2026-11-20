package com.vigil.security.engine

import com.vigil.security.core.model.Evidence
import com.vigil.security.core.model.RiskLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

object UrlShortenerDetector {

    private val KNOWN_SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "t.co", "is.gd", "cutt.ly",
        "rb.gy", "shorturl.at", "ow.ly", "buff.ly", "goo.gl",
        "qr.ae", "rebrand.ly", "bl.ink", "tiny.cc", "tr.im",
        "snipurl.com", "bc.vc", "sh.st", "adf.ly", "bit.do"
    )

    fun isShortener(registeredDomain: String): Boolean {
        return KNOWN_SHORTENERS.contains(registeredDomain.lowercase())
    }

    suspend fun inspectShortener(
        url: String,
        registeredDomain: String,
        client: OkHttpClient = defaultClient
    ): ShortenerAnalysisResult = withContext(Dispatchers.IO) {
        if (!isShortener(registeredDomain)) {
            return@withContext ShortenerAnalysisResult(isShortener = false)
        }

        val findings = mutableListOf<Evidence>()
        findings.add(
            Evidence(
                ruleId = "SHORTENER_DETECTED",
                title = "URL Shortener Service",
                description = "This link uses a shortening service ($registeredDomain) which conceals the ultimate landing destination from the user.",
                severity = RiskLevel.CAUTION,
                indicatorType = "Link Obfuscation",
                technicalDetail = "Shortener domain: $registeredDomain"
            )
        )

        var resolvedDestination: String? = null
        try {
            val request = Request.Builder()
                .url(url)
                .head()
                .header("User-Agent", "Vigil-Security-Analyzer/1.0 (Android Native)")
                .build()

            client.newCall(request).execute().use { response ->
                val prior = response.priorResponse
                if (prior != null || response.isRedirect || response.code in 300..399) {
                    val location = response.header("Location") ?: response.request.url.toString()
                    resolvedDestination = location
                    findings.add(
                        Evidence(
                            ruleId = "SHORTENER_RESOLVED",
                            title = "Expanded Shortener Destination",
                            description = "The shortened link redirects to '$location'.",
                            severity = RiskLevel.SAFE,
                            indicatorType = "Resolved Destination",
                            technicalDetail = "HTTP Status: ${response.code}"
                        )
                    )
                } else if (response.request.url.toString() != url) {
                    resolvedDestination = response.request.url.toString()
                }
            }
        } catch (e: Exception) {
            findings.add(
                Evidence(
                    ruleId = "SHORTENER_UNRESOLVED",
                    title = "Unable to Trace Shortened Destination",
                    description = "The target destination could not be resolved without opening the link (${e.localizedMessage ?: "Network timeout"}). Caution is advised.",
                    severity = RiskLevel.CAUTION,
                    indicatorType = "Trace Incomplete"
                )
            )
        }

        ShortenerAnalysisResult(
            isShortener = true,
            expandedUrl = resolvedDestination,
            findings = findings
        )
    }

    private val defaultClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(4, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
}

data class ShortenerAnalysisResult(
    val isShortener: Boolean,
    val expandedUrl: String? = null,
    val findings: List<Evidence> = emptyList()
)
