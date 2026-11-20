package com.vigil.security.engine

object HostnameParser {

    private val MULTI_PART_TLDS = setOf(
        "co.uk", "org.uk", "me.uk", "gov.uk", "ac.uk",
        "com.au", "net.au", "org.au", "edu.au", "gov.au",
        "co.nz", "net.nz", "org.nz",
        "com.br", "org.br", "gov.br",
        "co.in", "net.in", "org.in", "gen.in", "firm.in", "ind.in",
        "co.za", "net.za", "org.za",
        "co.jp", "ne.jp", "ac.jp", "go.jp",
        "com.mx", "org.mx", "gob.mx",
        "com.sg", "net.sg", "org.sg", "gov.sg",
        "com.tr", "edu.tr", "gov.tr"
    )

    fun parse(host: String): ParsedHost {
        val cleanHost = host.lowercase().trimEnd('.')
        val parts = cleanHost.split('.')

        if (parts.size <= 1) {
            return ParsedHost(
                fullHost = cleanHost,
                subdomain = "",
                registeredDomain = cleanHost,
                tld = ""
            )
        }

        // Check multi-part TLDs first
        if (parts.size >= 3) {
            val potentialMultiTld = "${parts[parts.size - 2]}.${parts[parts.size - 1]}"
            if (MULTI_PART_TLDS.contains(potentialMultiTld)) {
                val tld = potentialMultiTld
                val domainName = parts[parts.size - 3]
                val registeredDomain = "$domainName.$tld"
                val subdomain = if (parts.size > 3) {
                    parts.subList(0, parts.size - 3).joinToString(".")
                } else {
                    ""
                }
                return ParsedHost(
                    fullHost = cleanHost,
                    subdomain = subdomain,
                    registeredDomain = registeredDomain,
                    tld = tld
                )
            }
        }

        // Standard single-part TLD
        val tld = parts.last()
        val domainName = parts[parts.size - 2]
        val registeredDomain = "$domainName.$tld"
        val subdomain = if (parts.size > 2) {
            parts.subList(0, parts.size - 2).joinToString(".")
        } else {
            ""
        }

        return ParsedHost(
            fullHost = cleanHost,
            subdomain = subdomain,
            registeredDomain = registeredDomain,
            tld = tld
        )
    }
}

data class ParsedHost(
    val fullHost: String,
    val subdomain: String,
    val registeredDomain: String,
    val tld: String
)
