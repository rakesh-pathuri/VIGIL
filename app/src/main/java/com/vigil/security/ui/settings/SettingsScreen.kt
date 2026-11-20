package com.vigil.security.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigil.security.core.design.VigilTheme
import com.vigil.security.core.design.components.PulsingHeart
import com.vigil.security.core.design.components.VigilCard

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val securityColors = VigilTheme.securityColors

    var gsbKeyInput by remember(uiState.gsbApiKey) { mutableStateOf(uiState.gsbApiKey) }
    var vtKeyInput by remember(uiState.vtApiKey) { mutableStateOf(uiState.vtApiKey) }
    var showClearConfirmDialog by remember { mutableStateOf(false) }
    var showTermsDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    if (showTermsDialog) {
        TermsOfServiceDialog(onDismiss = { showTermsDialog = false })
    }

    if (showPrivacyDialog) {
        PrivacyPolicyDialog(onDismiss = { showPrivacyDialog = false })
    }

    LaunchedEffect(Unit) {
        viewModel.loadSettings()
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Clear Local Data",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Permanently delete all local scan history?",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearConfirmDialog = false
                        Toast.makeText(context, "History cleared", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text(text = "Clear", color = securityColors.dangerous, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text(text = "Cancel")
                }
            },
            shape = RoundedCornerShape(12.dp)
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }

        // Section: System Permissions
        item {
            Text(
                text = "PERMISSIONS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
        }

        item {
            VigilCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Cross-App Link Detection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (uiState.isAccessibilityGranted) "Active" else "Not granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isAccessibilityGranted) securityColors.safe else securityColors.caution
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (uiState.isAccessibilityGranted) "Active" else "Grant")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Floating Indicator Overlay",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (uiState.isOverlayGranted) "Active" else "Not granted",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (uiState.isOverlayGranted) securityColors.safe else securityColors.unknown
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            ).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                            }
                            context.startActivity(intent)
                        },
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (uiState.isOverlayGranted) "Active" else "Grant")
                    }
                }
            }
        }

        // Section: Protection Controls
        item {
            Text(
                text = "PROTECTION CONTROLS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
        }

        item {
            VigilCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Link Monitoring",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Inspect URLs across applications",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.isMonitoringEnabled,
                        onCheckedChange = { viewModel.toggleMonitoring(it) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local Heuristic Engine",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "On-device typosquatting and homoglyph analysis",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.isLocalHeuristicsEnabled,
                        onCheckedChange = { viewModel.toggleLocalHeuristics(it) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = MaterialTheme.colorScheme.outline)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Show Indicator for Safe Links",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Displays safety badge even when no threat is detected",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = uiState.showOverlayOnSafe,
                        onCheckedChange = { viewModel.toggleShowOverlayOnSafe(it) }
                    )
                }
            }
        }

        // Section: Threat Intelligence APIs
        item {
            Text(
                text = "THREAT INTELLIGENCE APIS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
        }

        item {
            VigilCard {
                Text(
                    text = "Google Safe Browsing",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = gsbKeyInput,
                    onValueChange = { gsbKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("API Key (Stored in Keystore)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.saveGsbApiKey(gsbKeyInput)
                        Toast.makeText(context, "Safe Browsing key saved", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Key")
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = MaterialTheme.colorScheme.outline)

                Text(
                    text = "VirusTotal",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = vtKeyInput,
                    onValueChange = { vtKeyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("API Key (Stored in Keystore)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        viewModel.saveVtApiKey(vtKeyInput)
                        Toast.makeText(context, "VirusTotal key saved", Toast.LENGTH_SHORT).show()
                    },
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("Save Key")
                }
            }
        }

        // Section: Threat Intelligence Definitions (In-App Direct Update without Play Store)
        item {
            Text(
                text = "THREAT INTELLIGENCE (IN-APP UPDATES)",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
        }

        item {
            VigilCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local Threat Database",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "${uiState.threatDefinitions.version} • ${uiState.threatDefinitions.signatureCount} signatures",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Button(
                        onClick = { viewModel.checkForDefinitionUpdates() },
                        enabled = !uiState.isCheckingForUpdates && !uiState.isApplyingUpdate,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(if (uiState.isCheckingForUpdates) "Checking..." else "Check Updates")
                    }
                }

                if (uiState.threatDefinitions.updateAvailable) {
                    Spacer(modifier = Modifier.height(12.dp))
                    VigilCard(
                        backgroundColor = securityColors.cautionContainer,
                        borderColor = securityColors.caution.copy(alpha = 0.5f)
                    ) {
                        Text(
                            text = "Live Feed Available: ${uiState.threatDefinitions.latestVersion}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = securityColors.caution
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = uiState.threatDefinitions.updateDescription ?: "Live verified threat feed available from OpenPhish.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.applyDefinitionUpdate() },
                            enabled = !uiState.isApplyingUpdate,
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = securityColors.safe)
                        ) {
                            Text(if (uiState.isApplyingUpdate) "Syncing Live Feed..." else "Sync Live Feed Now (In-App)")
                        }
                    }
                }

                if (!uiState.updateMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = uiState.updateMessage ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (uiState.updateMessage?.contains("fail", ignoreCase = true) == true || uiState.updateMessage?.contains("error", ignoreCase = true) == true) securityColors.dangerous else securityColors.safe,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Source: OpenPhish Live Threat Intelligence Feed. Signatures are downloaded directly via HTTPS and stored locally in on-device database.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Section: Privacy & Clear History
        item {
            Text(
                text = "PRIVACY & LOCAL ARCHITECTURE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
        }

        item {
            VigilCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local Scan History",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Private on-device Room database. No telemetry leaves device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { showClearConfirmDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = securityColors.dangerous)
                    ) {
                        Text("Clear")
                    }
                }
            }
        }

        // Section: Legal & Compliance
        item {
            Text(
                text = "LEGAL & COMPLIANCE",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
        }

        // Mandatory Automatic Binding Notice
        item {
            VigilCard(
                borderColor = Color(0xFF38BDF8).copy(alpha = 0.4f),
                backgroundColor = Color(0xFF040D1A)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        imageVector = Icons.Outlined.Gavel,
                        contentDescription = "Legal Notice",
                        tint = Color(0xFF38BDF8),
                        modifier = Modifier
                            .size(22.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AUTOMATIC BINDING AGREEMENT",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "By installing, accessing, or using Vigil, you are automatically and irrevocably bound by the Terms of Service and Privacy Policy. If you do not agree to these terms, you must immediately cease all use and uninstall the Application.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // Terms and Privacy Action Rows
        item {
            VigilCard {
                // Terms of Service Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showTermsDialog = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Terms of Service",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Binding conditions, disclaimers & liability limits",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "View Terms",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .height(1.dp)
                        .background(MaterialTheme.colorScheme.outline)
                )

                // Privacy Policy Row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showPrivacyDialog = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Policy,
                            contentDescription = null,
                            tint = Color(0xFF10B981),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Privacy Policy",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Zero-knowledge architecture & local data sovereignty",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = Icons.Outlined.ChevronRight,
                        contentDescription = "View Privacy Policy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Section: Centered Brand & Dedication (Requested by User: no card, centered text, nothing else)
        item {
            Spacer(modifier = Modifier.height(24.dp))
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Vigil Link Protection",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Version 2.4.0 (Build 2026.11)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(14.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Made with ",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    PulsingHeart(
                        modifier = Modifier.padding(horizontal = 4.dp),
                        size = 15.dp,
                        color = Color(0xFFEF4444)
                    )
                    Text(
                        text = " by Rakesh Pathuri",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }
    }
}
