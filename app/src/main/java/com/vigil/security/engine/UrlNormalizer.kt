package com.vigil.security.engine

import java.net.URI
import java.net.URLDecoder

object UrlNormalizer {

    /**
     * Stage 1: URL extraction and normalization.
     * Normalizes schemes, handles percent-encoding, removes default ports, trims whitespace.
     */
    fun normalize(rawUrl: String): NormalizedUrlResult {
        var trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) {
            return NormalizedUrlResult(
                isValid = false,
                originalUrl = rawUrl,
                normalizedUrl = "",
                error = "URL cannot be empty"
            )
        }

        // Add default scheme if omitted
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            trimmed = "https://$trimmed"
        }

        return try {
            val decoded = try {
                URLDecoder.decode(trimmed, "UTF-8")
            } catch (_: Exception) {
                trimmed
            }

            val uri = URI(trimmed)
            val scheme = (uri.scheme ?: "https").lowercase()
            val rawHost = uri.host ?: ""
            if (rawHost.isEmpty()) {
                return NormalizedUrlResult(
                    isValid = false,
                    originalUrl = rawUrl,
                    normalizedUrl = trimmed,
                    error = "Hostname missing or invalid"
                )
            }

            val host = rawHost.lowercase().trimEnd('.')
            val port = uri.port
            val cleanPort = if (port == -1 || (scheme == "http" && port == 80) || (scheme == "https" && port == 443)) {
                ""
            } else {
                ":$port"
            }

            val path = uri.rawPath ?: ""
            val query = if (uri.rawQuery != null) "?${uri.rawQuery}" else ""
            val fragment = if (uri.rawFragment != null) "#${uri.rawFragment}" else ""

            val normalized = "$scheme://$host$cleanPort$path$query$fragment"

            NormalizedUrlResult(
                isValid = true,
                originalUrl = rawUrl,
                normalizedUrl = normalized,
                scheme = scheme,
                host = host,
                path = path,
                query = query
            )
        } catch (e: Exception) {
            NormalizedUrlResult(
                isValid = false,
                originalUrl = rawUrl,
                normalizedUrl = trimmed,
                error = e.localizedMessage ?: "Invalid URL structure"
            )
        }
    }
}

data class NormalizedUrlResult(
    val isValid: Boolean,
    val originalUrl: String,
    val normalizedUrl: String,
    val scheme: String = "https",
    val host: String = "",
    val path: String = "",
    val query: String = "",
    val error: String? = null
)
