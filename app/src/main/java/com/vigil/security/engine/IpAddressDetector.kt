package com.vigil.security.engine

import com.vigil.security.core.model.Evidence
import com.vigil.security.core.model.RiskLevel
import java.util.regex.Pattern

object IpAddressDetector {

    private val IPV4_PATTERN = Pattern.compile(
        "^((25[0-5]|(2[0-4]|1\\d|[1-9]|)\\d)\\.?\\b){4}$"
    )

    private val HEX_OCTAL_IP_PATTERN = Pattern.compile(
        "^(0x[0-9a-fA-F]+|0[0-7]+|\\d+)(\\.(0x[0-9a-fA-F]+|0[0-7]+|\\d+)){0,3}$"
    )

    fun analyze(host: String): List<Evidence> {
        val findings = mutableListOf<Evidence>()
        val cleanHost = host.trim('[', ']').lowercase()

        // IPv4 check
        if (IPV4_PATTERN.matcher(cleanHost).matches()) {
            val isPrivate = isPrivateIp(cleanHost)
            findings.add(
                Evidence(
                    ruleId = "IP_HOST_IPV4",
                    title = "Raw IP Address URL",
                    description = if (isPrivate) {
                        "This link directs directly to a local or private network IP address ($cleanHost) rather than a registered domain name."
                    } else {
                        "This link directs directly to a public IP address ($cleanHost) instead of a registered domain name. Legitimate consumer services rarely use raw IP addresses for user-facing links."
                    },
                    severity = if (isPrivate) RiskLevel.CAUTION else RiskLevel.DANGEROUS,
                    indicatorType = "IP Address",
                    technicalDetail = "Host is IPv4: $cleanHost"
                )
            )
        } else if (cleanHost.contains(':') && (cleanHost.count { it == ':' } >= 2)) {
            // IPv6 check
            findings.add(
                Evidence(
                    ruleId = "IP_HOST_IPV6",
                    title = "IPv6 Address Host",
                    description = "This link points directly to an IPv6 address ($cleanHost) instead of a verified domain name.",
                    severity = RiskLevel.CAUTION,
                    indicatorType = "IPv6 Address"
                )
            )
        } else if (HEX_OCTAL_IP_PATTERN.matcher(cleanHost).matches() && cleanHost.any { it.isDigit() }) {
            // Potential DWORD or Hexadecimal IP encoding evasion
            findings.add(
                Evidence(
                    ruleId = "IP_HOST_OBFUSCATED",
                    title = "Obfuscated Numerical Host Address",
                    description = "This URL uses an alternate numerical or hexadecimal notation to disguise an IP address destination.",
                    severity = RiskLevel.DANGEROUS,
                    indicatorType = "IP Evasion"
                )
            )
        }

        return findings
    }

    private fun isPrivateIp(ip: String): Boolean {
        return ip.startsWith("10.") ||
                ip.startsWith("192.168.") ||
                ip.startsWith("127.") ||
                ip.startsWith("169.254.") ||
                (ip.startsWith("172.") && is172Private(ip))
    }

    private fun is172Private(ip: String): Boolean {
        val parts = ip.split('.')
        if (parts.size > 1) {
            val second = parts[1].toIntOrNull() ?: 0
            return second in 16..31
        }
        return false
    }
}
