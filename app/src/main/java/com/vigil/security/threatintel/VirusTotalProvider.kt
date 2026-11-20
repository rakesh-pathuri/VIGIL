package com.vigil.security.threatintel

import android.util.Base64
import com.google.gson.JsonParser
import com.vigil.security.core.model.Evidence
import com.vigil.security.core.model.RiskLevel
import com.vigil.security.core.security.KeyStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class VirusTotalProvider(
    private val keyStoreManager: KeyStoreManager,
    private val client: OkHttpClient = defaultClient
) : ThreatIntelligenceProvider {

    override val providerName: String = "VirusTotal Intelligence"

    override val isConfigured: Boolean
        get() = keyStoreManager.virusTotalApiKey.isNotBlank()

    private val cache = ConcurrentHashMap<String, ThreatIntelResult>()

    override suspend fun checkUrl(normalizedUrl: String, domain: String): ThreatIntelResult =
        withContext(Dispatchers.IO) {
            val apiKey = keyStoreManager.virusTotalApiKey
            if (apiKey.isBlank()) {
                return@withContext ThreatIntelResult(
                    providerName = providerName,
                    isEvaluated = false,
                    threatDetected = false,
                    errorMessage = "Provider inactive: API key not configured"
                )
            }

            cache[normalizedUrl]?.let { return@withContext it }

            // VT URL ID is base64url-encoded URL without padding
            val urlId = Base64.encodeToString(
                normalizedUrl.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )

            val endpoint = "https://www.virustotal.com/api/v3/urls/$urlId"
            val request = Request.Builder()
                .url(endpoint)
                .get()
                .header("x-apikey", apiKey)
                .header("User-Agent", "Vigil-Android/1.0")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (response.code == 404) {
                        // URL not yet analyzed by VirusTotal
                        val res = ThreatIntelResult(
                            providerName = providerName,
                            isEvaluated = true,
                            threatDetected = false,
                            riskLevel = RiskLevel.UNKNOWN,
                            errorMessage = "URL not yet indexed by VirusTotal network"
                        )
                        cache[normalizedUrl] = res
                        return@withContext res
                    }

                    if (response.code == 429) {
                        return@withContext ThreatIntelResult(
                            providerName = providerName,
                            isEvaluated = false,
                            threatDetected = false,
                            errorMessage = "Rate limit reached on VirusTotal API quota"
                        )
                    }

                    if (!response.isSuccessful) {
                        return@withContext ThreatIntelResult(
                            providerName = providerName,
                            isEvaluated = false,
                            threatDetected = false,
                            errorMessage = "VirusTotal HTTP error: ${response.code}"
                        )
                    }

                    val bodyString = response.body?.string() ?: "{}"
                    val json = JsonParser.parseString(bodyString).asJsonObject
                    val attributes = json.getAsJsonObject("data")?.getAsJsonObject("attributes")
                    val stats = attributes?.getAsJsonObject("last_analysis_stats")

                    val maliciousCount = stats?.get("malicious")?.asInt ?: 0
                    val suspiciousCount = stats?.get("suspicious")?.asInt ?: 0
                    val harmlessCount = stats?.get("harmless")?.asInt ?: 0

                    val result = when {
                        maliciousCount >= 2 -> {
                            ThreatIntelResult(
                                providerName = providerName,
                                isEvaluated = true,
                                threatDetected = true,
                                riskLevel = RiskLevel.DANGEROUS,
                                threatType = "Multi-Engine Detection",
                                evidence = listOf(
                                    Evidence(
                                        ruleId = "VT_DETECTION_MALICIOUS",
                                        title = "Flagged by Multiple Antivirus Engines",
                                        description = "$maliciousCount security vendors on VirusTotal identified this URL as malicious ($suspiciousCount flagged as suspicious).",
                                        severity = RiskLevel.DANGEROUS,
                                        indicatorType = "Vendor Consensus",
                                        technicalDetail = "Engines flagged: $maliciousCount malicious, $suspiciousCount suspicious"
                                    )
                                )
                            )
                        }
                        maliciousCount == 1 || suspiciousCount >= 2 -> {
                            ThreatIntelResult(
                                providerName = providerName,
                                isEvaluated = true,
                                threatDetected = true,
                                riskLevel = RiskLevel.CAUTION,
                                threatType = "Suspicious Signals",
                                evidence = listOf(
                                    Evidence(
                                        ruleId = "VT_DETECTION_SUSPICIOUS",
                                        title = "Flagged by Security Scanner",
                                        description = "URL received $maliciousCount malicious and $suspiciousCount suspicious vendor flags on VirusTotal.",
                                        severity = RiskLevel.CAUTION,
                                        indicatorType = "Vendor Consensus",
                                        technicalDetail = "Engines flagged: $maliciousCount malicious, $suspiciousCount suspicious"
                                    )
                                )
                            )
                        }
                        else -> {
                            ThreatIntelResult(
                                providerName = providerName,
                                isEvaluated = true,
                                threatDetected = false,
                                riskLevel = RiskLevel.SAFE
                            )
                        }
                    }

                    cache[normalizedUrl] = result
                    result
                }
            } catch (e: Exception) {
                ThreatIntelResult(
                    providerName = providerName,
                    isEvaluated = false,
                    threatDetected = false,
                    errorMessage = "Network or timeout error: ${e.localizedMessage ?: "Unknown"}"
                )
            }
        }

    companion object {
        private val defaultClient: OkHttpClient by lazy {
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        }
    }
}
