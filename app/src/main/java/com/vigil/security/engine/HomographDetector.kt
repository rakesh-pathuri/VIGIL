package com.vigil.security.engine

import com.vigil.security.core.model.Evidence
import com.vigil.security.core.model.RiskLevel
import java.net.IDN

object HomographDetector {

    // Cyrillic and Greek homoglyphs commonly substituted for Latin letters
    private val CONFUSABLE_MAPPING = mapOf(
        '\u0430' to 'a', // Cyrillic small a
        '\u0441' to 'c', // Cyrillic small es (c)
        '\u0435' to 'e', // Cyrillic small ie (e)
        '\u0456' to 'i', // Cyrillic small byelorussian-ukrainian i
        '\u0458' to 'j', // Cyrillic small je
        '\u043E' to 'o', // Cyrillic small o
        '\u0440' to 'p', // Cyrillic small er (p)
        '\u0455' to 's', // Cyrillic small dze (s)
        '\u0443' to 'y', // Cyrillic small u (y)
        '\u0445' to 'x', // Cyrillic small ha (x)
        '\u03BF' to 'o', // Greek small omicron
        '\u03BD' to 'v'  // Greek small nu
    )

    fun analyze(host: String): List<Evidence> {
        val findings = mutableListOf<Evidence>()
        val lowerHost = host.lowercase()

        // 1. Check for Punycode prefix
        if (lowerHost.contains("xn--")) {
            val decoded = try {
                IDN.toUnicode(lowerHost)
            } catch (_: Exception) {
                lowerHost
            }
            findings.add(
                Evidence(
                    ruleId = "HOMOGRAPH_PUNYCODE",
                    title = "Internationalized Domain (Punycode)",
                    description = "Domain uses Punycode encoding ($lowerHost) which renders as '$decoded'. This technique is frequently used in phishing attacks to mimic legitimate brand names.",
                    severity = RiskLevel.CAUTION,
                    indicatorType = "Punycode",
                    technicalDetail = "Decoded domain: $decoded"
                )
            )
        }

        // 2. Check for mixed script / Cyrillic confusable characters
        val detectedConfusables = mutableListOf<Pair<Char, Char>>()
        var hasLatin = false
        var hasConfusable = false

        for (ch in lowerHost) {
            if (ch in 'a'..'z') {
                hasLatin = true
            } else if (CONFUSABLE_MAPPING.containsKey(ch)) {
                hasConfusable = true
                detectedConfusables.add(ch to CONFUSABLE_MAPPING[ch]!!)
            }
        }

        if (hasConfusable && hasLatin) {
            val details = detectedConfusables.joinToString(", ") { "U+${Integer.toHexString(it.first.code).uppercase()} looks like '${it.second}'" }
            findings.add(
                Evidence(
                    ruleId = "HOMOGRAPH_MIXED_SCRIPT",
                    title = "Mixed-Script Homoglyph Attack Detected",
                    description = "Domain contains mixed alphabets (e.g., Cyrillic characters substituted for visually identical Latin letters) to impersonate a legitimate destination.",
                    severity = RiskLevel.DANGEROUS,
                    indicatorType = "Confusable Script",
                    technicalDetail = details
                )
            )
        }

        // 3. Zero-width and hidden characters
        val hasHiddenChars = lowerHost.any { it == '\u200B' || it == '\u200C' || it == '\u200D' || it == '\uFEFF' || it == '\u202E' }
        if (hasHiddenChars) {
            findings.add(
                Evidence(
                    ruleId = "HOMOGRAPH_HIDDEN_CHARS",
                    title = "Hidden or Directional Characters",
                    description = "Domain contains zero-width or right-to-left override characters designed to conceal the actual destination from visual inspection.",
                    severity = RiskLevel.DANGEROUS,
                    indicatorType = "Character Obfuscation"
                )
            )
        }

        return findings
    }
}
