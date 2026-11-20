package com.vigil.security.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Gavel
import androidx.compose.material.icons.outlined.Policy
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * Reusable modal legal document container with obsidian cybersecurity styling.
 */
@Composable
private fun LegalDocumentViewer(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.88f)
                .border(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF030712),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        icon()
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF38BDF8).copy(alpha = 0.2f))
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Scrollable Legal Text
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        content()
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color(0xFF38BDF8).copy(alpha = 0.2f))
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Mandatory Acceptance Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = "I Understand & Acknowledge",
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

/**
 * Attorney-Grade Terms of Service Dialog
 */
@Composable
fun TermsOfServiceDialog(
    onDismiss: () -> Unit
) {
    LegalDocumentViewer(
        title = "Terms of Service",
        subtitle = "Legally Binding End User Agreement",
        icon = {
            Icon(
                imageVector = Icons.Outlined.Gavel,
                contentDescription = null,
                tint = Color(0xFF38BDF8),
                modifier = Modifier.size(28.dp)
            )
        },
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Mandatory Binding Notice Callout
            Surface(
                color = Color(0xFF0F172A),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "MANDATORY BINDING LEGAL NOTICE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFEF4444),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "BY DOWNLOADING, INSTALLING, ENABLING ACCESSIBILITY SERVICES, OR OTHERWISE ACCESSING OR UTILIZING THE VIGIL CYBERSECURITY APPLICATION (\"APPLICATION\"), YOU EXPRESSLY, VOLUNTARILY, AND UNCONDITIONALLY ACKNOWLEDGE AND AGREE TO BE FULLY AND AUTOMATICALLY BOUND BY ALL TERMS, CONDITIONS, COVENANTS, AND DISCLAIMERS CONTAINED IN THIS AGREEMENT AND THE INCORPORATED PRIVACY POLICY.\n\nIF YOU DO NOT AGREE TO THESE TERMS IN THEIR ENTIRETY, YOU HAVE NO LEGAL RIGHT OR LICENSE TO USE THE APPLICATION AND MUST IMMEDIATELY CEASE ALL USE AND PERMANENTLY UNINSTALL THE APPLICATION FROM YOUR DEVICE.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        lineHeight = 18.sp
                    )
                }
            }

            LegalSection(
                number = "1",
                title = "PARTIES AND NATURE OF SERVICE",
                body = "This End User License Agreement and Terms of Service (\"Agreement\") is entered into between you (\"User\", \"You\") and the developer of Vigil (\"Developer\", \"Author\"). Vigil operates as an autonomous, on-device hyperlink inspection utility engineered to analyze uniform resource identifiers (\"URLs\") across client applications using local heuristic classification, character transposition algorithms, and local threat signature databases. The Service is provided solely as an auxiliary informational utility."
            )

            LegalSection(
                number = "2",
                title = "AUTOMATIC CONSENT & PERMISSION DELEGATION",
                body = "By enabling the Android Accessibility Service ('Vigil Link Protection') and System Alert Window overlay permissions, You grant the Application explicit, limited authorization to parse on-screen accessibility window hierarchy events strictly for detecting web hyperlinks and rendering real-time safety indicator overlays. You understand and agree that revoking these permissions terminates the Application's ability to provide automated background protection."
            )

            LegalSection(
                number = "3",
                title = "COMPLETE DISCLAIMER OF WARRANTIES ('AS IS')",
                body = "TO THE MAXIMUM EXTENT PERMITTED BY APPLICABLE JURISDICTIONAL LAW, THE APPLICATION IS PROVIDED ON AN \"AS IS\", \"AS AVAILABLE\", AND \"WITH ALL FAULTS\" BASIS WITHOUT WARRANTIES OR GUARANTEES OF ANY KIND, WHETHER EXPRESS, IMPLIED, OR STATUTORY. THE DEVELOPER EXPRESSLY DISCLAIMS ALL WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE, FREEDOM FROM PROGRAMMING ERRORS, UNINTERRUPTED EXECUTION, OR COMPLETE CYBERSECURITY DEFENSE.\n\nCYBER THREATS, SOCIAL ENGINEERING SCHEMES, AND PHISHING TACTICS MUTATE CONTINUOUSLY. NO ALGORITHMIC ENGINE CAN GUARANTEE ZERO FALSE NEGATIVES (UNDETECTED MALICIOUS LINKS) OR ZERO FALSE POSITIVES (BENIGN LINKS CLASSIFIED AS CAUTION OR DANGEROUS). SECURITY VERDICTS ARE INFORMATIONAL ESTIMATES ONLY."
            )

            LegalSection(
                number = "4",
                title = "EXCLUSION AND LIMITATION OF LIABILITY",
                body = "UNDER NO CIRCUMSTANCES SHALL THE DEVELOPER (RAKESH PATHURI), DISTRIBUTORS, OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, CONSEQUENTIAL, SPECIAL, PUNITIVE, OR EXEMPLARY DAMAGES WHATSOEVER, INCLUDING BUT NOT LIMITED TO LOSS OF DATA, COMPROMISED PASSWORDS, ACCOUNT TAKEOVER, FINANCIAL THEFT, IDENTITY THEFT, SYSTEM DAMAGE, OR BUSINESS INTERRUPTION ARISING OUT OF OR IN CONNECTION WITH YOUR USE OF, OR INABILITY TO USE, THE APPLICATION, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH INJURY."
            )

            LegalSection(
                number = "5",
                title = "USER ASSUMPTION OF RISK & DUE DILIGENCE",
                body = "You acknowledge and affirm that the final, discretionary decision to navigate to, interact with, submit credentials to, or execute any web link, file, or domain rests solely and exclusively with You. The safety classifications generated by Vigil (Safe, Caution, Dangerous) are algorithmic risk indicators and must not substitute for personal discretion or standard verification protocols for sensitive personal or banking activities."
            )

            LegalSection(
                number = "6",
                title = "PROHIBITED USES",
                body = "You agree not to reverse engineer, decompile, maliciously modify, redistribute with harmful payload additions, or exploit the heuristic analysis engine for unlawful purposes, cyberattack automation, or adversarial validation of phishing campaigns."
            )

            LegalSection(
                number = "7",
                title = "SEVERABILITY AND ENTIRE AGREEMENT",
                body = "If any provision of this Agreement is held to be invalid, unenforceable, or contrary to statutory law, such provision shall be enforced to the maximum extent permissible, and the remaining provisions shall continue in full legal force and effect. Continued use of the Application following any updates constitutes acceptance of revised terms."
            )
        }
    }
}

/**
 * Attorney-Grade Zero-Knowledge Privacy Policy Dialog
 */
@Composable
fun PrivacyPolicyDialog(
    onDismiss: () -> Unit
) {
    LegalDocumentViewer(
        title = "Privacy Policy",
        subtitle = "Zero-Knowledge Local Data Architecture",
        icon = {
            Icon(
                imageVector = Icons.Outlined.Policy,
                contentDescription = null,
                tint = Color(0xFF10B981),
                modifier = Modifier.size(28.dp)
            )
        },
        onDismiss = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Privacy Guarantee Callout
            Surface(
                color = Color(0xFF064E3B).copy(alpha = 0.35f),
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "100% PRIVATE • ZERO EXFILTRATION GUARANTEE",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Vigil does not operate tracking servers, does not monetize user data, does not sell telemetry to data brokers, and does not maintain centralized database records of your browsing or messaging activities. Your data stays entirely in your custody on your physical device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 18.sp
                    )
                }
            }

            LegalSection(
                number = "1",
                title = "DATA MINIMIZATION & ARCHITECTURAL PRIVACY",
                body = "Vigil adheres to statutory privacy standards including global data protection principles (GDPR / CCPA / DPDP). The Application collects no personally identifiable information (PII), requires no registration or identity authentication, and assigns no persistent advertising or tracking identifiers."
            )

            LegalSection(
                number = "2",
                title = "ACCESSIBILITY EVENT SCOPE & VOLATILE PROCESSING",
                body = "The Android Accessibility Service permission is granted strictly for detecting uniform resource identifiers. The processing pipeline inspects text contents transiently in volatile device memory (RAM) solely against URL syntax filters. Any non-link textual content (including chat text, personal messages, passwords, usernames, phone numbers, contact lists, and application metadata) is immediately and permanently discarded without logging, caching, or retention."
            )

            LegalSection(
                number = "3",
                title = "ON-DEVICE STORAGE & LOCAL RETENTION",
                body = "Historical scan records, user preferences, and threat classifications are preserved strictly within a local, sandboxed SQLite Room database stored within the application's private directory on your device. This data is never synchronized to cloud infrastructure unless manually initiated by the user through system share intents."
            )

            LegalSection(
                number = "4",
                title = "HARDWARE-BACKED CRYPTOGRAPHIC SECURITY",
                body = "If You choose to configure third-party threat intelligence API keys (e.g., Google Safe Browsing or VirusTotal), such credentials are encrypted at rest using AES-256-GCM via the Android Keystore system. The Developer cannot view, access, or decrypt your private API credentials."
            )

            LegalSection(
                number = "5",
                title = "COMMUNITY THREAT FEEDS & NETWORK INTERACTION",
                body = "When threat intelligence definition updates are requested, the Application performs direct outbound HTTPS requests to open public threat definition repositories (such as openphish.com). These network calls transmit only generic HTTP client headers and zero device telemetry, browsing history, or user identifiers."
            )

            LegalSection(
                number = "6",
                title = "COMPLETE DATA SOVEREIGNTY & ERASURE",
                body = "You retain absolute sovereignty over all cached data. You may execute complete, permanent erasure of all local scan records at any time using the 'Clear Local Data' control in Settings. Uninstalling the Application permanently purges all databases, keystore aliases, and configuration preferences."
            )

            LegalSection(
                number = "7",
                title = "ACCEPTANCE UPON USE",
                body = "By downloading, installing, or executing the Application, You expressly acknowledge and consent to this Privacy Policy. Continued use following any statutory amendments constitutes ongoing consent."
            )
        }
    }
}

@Composable
private fun LegalSection(
    number: String,
    title: String,
    body: String
) {
    Column {
        Text(
            text = "SECTION $number: $title",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 0.5.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            lineHeight = 18.sp
        )
    }
}
