package com.vigil.security.core.security

import java.net.URI

object UrlSanitizer {

    private val SENSITIVE_QUERY_PARAMS = setOf(
        "token", "access_token", "auth", "api_key", "apikey",
        "key", "secret", "password", "pwd", "pass",
        "session", "session_id", "sessionid", "code",
        "state", "otp", "pin", "jwt", "bearer", "signature"
    )

    /**
     * Redacts sensitive credentials, tokens, and private tokens from query parameters
     * before persisting or displaying URLs.
     */
    fun sanitizeUrl(rawUrl: String): String {
        return try {
            val qIndex = rawUrl.indexOf('?')
            if (qIndex == -1 || qIndex == rawUrl.length - 1) {
                return rawUrl
            }

            val base = rawUrl.substring(0, qIndex)
            val queryWithFragment = rawUrl.substring(qIndex + 1)
            val hashIndex = queryWithFragment.indexOf('#')
            val queryString = if (hashIndex != -1) queryWithFragment.substring(0, hashIndex) else queryWithFragment
            val fragment = if (hashIndex != -1) queryWithFragment.substring(hashIndex) else ""

            val pairs = queryString.split('&')
            val sanitizedPairs = pairs.map { pair ->
                val eqIndex = pair.indexOf('=')
                if (eqIndex != -1) {
                    val key = pair.substring(0, eqIndex)
                    val lowerKey = key.lowercase()
                    if (SENSITIVE_QUERY_PARAMS.contains(lowerKey) ||
                        lowerKey.contains("token") ||
                        lowerKey.contains("auth") ||
                        lowerKey.contains("secret") ||
                        lowerKey.contains("password") ||
                        lowerKey.contains("session")
                    ) {
                        "$key=[REDACTED]"
                    } else {
                        pair
                    }
                } else {
                    pair
                }
            }

            "$base?${sanitizedPairs.joinToString("&")}$fragment"
        } catch (_: Exception) {
            rawUrl
        }
    }
}
