package com.vigil.security.threatintel

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.vigil.security.core.model.Evidence
import com.vigil.security.core.model.RiskLevel
import com.vigil.security.core.security.KeyStoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class GoogleSafeBrowsingProvider(
    private val keyStoreManager: KeyStoreManager,
    private val client: OkHttpClient = defaultClient
) : ThreatIntelligenceProvider {

    override val providerName: String = "Google Safe Browsing"

    override val isConfigured: Boolean
        get() = keyStoreManager.googleSafeBrowsingApiKey.isNotBlank()

    // Local in-memory LRU-like cache (URL -> ThreatIntelResult)
    private val cache = ConcurrentHashMap<String, ThreatIntelResult>()

    override suspend fun checkUrl(normalizedUrl: String, domain: String): ThreatIntelResult =
        withContext(Dispatchers.IO) {
            val apiKey = keyStoreManager.googleSafeBrowsingApiKey
            if (apiKey.isBlank()) {
                return@withContext ThreatIntelResult(
                    providerName = providerName,
                    isEvaluated = false,
                    threatDetected = false,
                    errorMessage = "Provider inactive: API key not configured"
                )
            }

            // Check cache first
            cache[normalizedUrl]?.let { return@withContext it }

            val requestJson = JsonObject().apply {
                add("client", JsonObject().apply {
                    addProperty("clientId", "vigil-android")
                    addProperty("clientVersion", "1.0.0")
                })
                add("threatInfo", JsonObject().apply {
                    val threatTypes = com.google.gson.JsonArray().apply {
                        add("MALWARE")
                        add("SOCIAL_ENGINEERING")
                        add("UNWANTED_SOFTWARE")
                        add("POTENTIALLY_HARMFUL_APPLICATION")
                    }
                    val platformTypes = com.google.gson.JsonArray().apply {
                        add("ALL_PLATFORMS")
                    }
                    val threatEntryTypes = com.google.gson.JsonArray().apply {
                        add("URL")
                    }
                    val threatEntries = com.google.gson.JsonArray().apply {
                        add(JsonObject().apply { addProperty("url", normalizedUrl) })
                    }
                    add("threatTypes", threatTypes)
                    add("platformTypes", platformTypes)
                    add("threatEntryTypes", threatEntryTypes)
                    add("threatEntries", threatEntries)
                })
            }

            val mediaType = "application/json; charset=utf-8".toMediaType()
            val body = requestJson.toString().toRequestBody(mediaType)
            val endpoint = "https://safebrowsing.googleapis.com/v4/threatMatches:find?key=$apiKey"

            val request = Request.Builder()
                .url(endpoint)
                .post(body)
                .header("User-Agent", "Vigil-Android/1.0")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext ThreatIntelResult(
                            providerName = providerName,
                            isEvaluated = false,
                            threatDetected = false,
                            errorMessage = "Safe Browsing HTTP error: ${response.code}"
                        )
                    }

                    val responseBody = response.body?.string() ?: "{}"
                    val jsonResponse = JsonParser.parseString(responseBody).asJsonObject
                    val matches = jsonResponse.getAsJsonArray("matches")

                    val result = if (matches != null && matches.size() > 0) {
                        val firstMatch = matches.get(0).asJsonObject
                        val threatType = firstMatch.get("threatType")?.asString ?: "MALWARE"
                        val platformType = firstMatch.get("platformType")?.asString ?: "ALL_PLATFORMS"

                        ThreatIntelResult(
                            providerName = providerName,
                            isEvaluated = true,
                            threatDetected = true,
                            riskLevel = RiskLevel.DANGEROUS,
                            threatType = threatType,
                            evidence = listOf(
                                Evidence(
                                    ruleId = "GSB_MATCH_$threatType",
                                    title = "Google Safe Browsing Threat Detection",
                                    description = "Flagged as $threatType on $platformType by Google Safe Browsing threat intelligence.",
                                    severity = RiskLevel.DANGEROUS,
                                    indicatorType = "External Reputation",
                                    technicalDetail = "Threat Match: $threatType"
                                )
                            )
                        )
                    } else {
                        ThreatIntelResult(
                            providerName = providerName,
                            isEvaluated = true,
                            threatDetected = false,
                            riskLevel = RiskLevel.SAFE
                        )
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
