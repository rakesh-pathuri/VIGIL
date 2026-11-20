package com.vigil.security.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigil.security.R
import com.vigil.security.core.design.ChakraPetchFontFamily
import com.vigil.security.core.design.VigilTheme
import com.vigil.security.core.design.components.DottedShield
import com.vigil.security.core.design.components.OpenPhishTelemetryCard
import com.vigil.security.core.design.components.VigilBadge
import com.vigil.security.core.design.components.VigilCard
import com.vigil.security.core.design.components.VigilEmptyState
import com.vigil.security.core.model.SecurityReport
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToScanner: () -> Unit,
    onNavigateToActivity: () -> Unit,
    onSelectReport: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val securityColors = VigilTheme.securityColors

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // App Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(id = R.drawable.ic_vigil_transparent),
                        contentDescription = "Vigil Logo",
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = "VIGIL",
                        style = TextStyle(
                            fontFamily = ChakraPetchFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 30.sp,
                            letterSpacing = 4.sp,
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFFFFFFFF),
                                    Color(0xFF38BDF8),
                                    Color(0xFF00F5D4)
                                )
                            )
                        )
                    )
                }

                Switch(
                    checked = uiState.protectionState.isProtectionActive,
                    onCheckedChange = { enabled ->
                        if (enabled && !uiState.protectionState.isAccessibilityEnabled) {
                            Toast.makeText(context, "Enable Vigil in Accessibility to start protection", Toast.LENGTH_SHORT).show()
                            viewModel.openAccessibilitySettings(context)
                        } else {
                            viewModel.toggleMonitoring(enabled)
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = securityColors.safe
                    )
                )
            }
        }

        // Protection Status Card
        item {
            val isActive = uiState.protectionState.isProtectionActive
            val statusColor = if (isActive) securityColors.safe else securityColors.unknown
            val statusBg = if (isActive) securityColors.safeContainer else securityColors.unknownContainer
            var replay by remember { mutableIntStateOf(0) }

            VigilCard(
                borderColor = if (isActive) securityColors.safe.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                borderWidth = if (isActive) 1.5.dp else 1.dp,
                backgroundColor = statusBg
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(statusColor)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = if (isActive) "REAL-TIME PROTECTION ACTIVE" else "MONITORING INACTIVE",
                                style = MaterialTheme.typography.labelLarge,
                                color = statusColor,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = if (isActive) {
                                "Monitoring links across WhatsApp, Telegram, SMS, and browsers."
                            } else {
                                "Monitoring is paused or requires accessibility permission."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .clickable { replay++ },
                        contentAlignment = Alignment.Center
                    ) {
                        DottedShield(
                            modifier = Modifier.fillMaxSize(),
                            hueCycle = false,
                            replayKey = replay
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(securityColors.safe)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "100% On-Device Analysis • No URLs or messages leave your device",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Permissions Section
        item {
            Text(
                text = "PERMISSIONS",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
        }

        item {
            val hasAccessibility = uiState.protectionState.isAccessibilityEnabled
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
                            text = if (hasAccessibility) "Active" else "Permission required",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasAccessibility) securityColors.safe else securityColors.caution
                        )
                    }

                    if (!hasAccessibility) {
                        OutlinedButton(
                            onClick = { viewModel.openAccessibilitySettings(context) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Enable")
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Active",
                            tint = securityColors.safe,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        item {
            val hasOverlay = uiState.protectionState.isOverlayPermissionGranted
            VigilCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Link Safety Indicators",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (hasOverlay) "Active" else "Permission recommended",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (hasOverlay) securityColors.safe else securityColors.unknown
                        )
                    }

                    if (!hasOverlay) {
                        OutlinedButton(
                            onClick = { viewModel.openOverlaySettings(context) },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Grant")
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.CheckCircle,
                            contentDescription = "Active",
                            tint = securityColors.safe,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Vivo / Background Keep-Alive card (Prevents Android from revoking accessibility)
        item {
            val powerManager = remember { context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager }
            val isIgnoringBattery = powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: true

            if (!isIgnoringBattery) {
                VigilCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Background Keep-Alive",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Recommended on Vivo to prevent system from auto-disabling accessibility",
                                style = MaterialTheme.typography.bodySmall,
                                color = securityColors.caution
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = {
                                try {
                                    val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                        data = android.net.Uri.parse("package:${context.packageName}")
                                    }
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    try {
                                        val intent = Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                        context.startActivity(intent)
                                    } catch (_: Exception) {}
                                }
                            },
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Allow")
                        }
                    }
                }
            }
        }

        // OpenPhish Real-Time Threat Intelligence Card (Compact, glowing dotted theme)
        item {
            OpenPhishTelemetryCard(
                stats = uiState.openPhishStats,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Live Statistics Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VigilCard(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Links Analyzed",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${uiState.protectionState.linksAnalyzedCount}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                VigilCard(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Suspicious Links",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${uiState.protectionState.suspiciousDetectedCount}",
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (uiState.protectionState.suspiciousDetectedCount > 0)
                            securityColors.dangerous
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Security Classification Color Guide
        item {
            var isGuideExpanded by remember { mutableStateOf(false) }
            VigilCard(onClick = { isGuideExpanded = !isGuideExpanded }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(securityColors.safe)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(securityColors.caution)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(securityColors.dangerous)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Security Color Guide",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (isGuideExpanded) "Tap to collapse" else "Tap to see what each color means",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Icon(
                        imageVector = if (isGuideExpanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }

                if (isGuideExpanded) {
                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(12.dp))

                    ColorLegendItem(
                        color = securityColors.dangerous,
                        title = "DANGEROUS (Scam / Phishing)",
                        description = "High-risk threat, known phishing signature, or brand spoofing. Avoid opening or entering credentials."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ColorLegendItem(
                        color = securityColors.caution,
                        title = "CAUTION (Suspicious)",
                        description = "Suspicious characteristics detected (lookalike domain, unusual extension, or deceptive redirect). Verify before proceeding."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ColorLegendItem(
                        color = securityColors.safe,
                        title = "SAFE (Passed Checks)",
                        description = "Passed local heuristics and matches authentic official domain patterns without detected threats."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ColorLegendItem(
                        color = securityColors.unknown,
                        title = "UNKNOWN (Unverified)",
                        description = "Insufficient local data or incomplete telemetry available to verify destination safety."
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ColorLegendItem(
                        color = Color(0xFF2563EB),
                        title = "ANALYZING (In Progress)",
                        description = "On-device security inspection is running in real time. Wait before clicking."
                    )
                }
            }
        }

        // Recent Activity Section
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "RECENT ACTIVITY",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )
                if (uiState.recentScans.isNotEmpty()) {
                    TextButton(onClick = onNavigateToActivity) {
                        Text("View All")
                    }
                }
            }
        }

        if (uiState.recentScans.isEmpty()) {
            item {
                VigilEmptyState(
                    title = "No links analyzed yet",
                    description = "Detected links from messaging and browsers will appear here.",
                    actionText = "Open Scanner",
                    onActionClick = onNavigateToScanner
                )
            }
        } else {
            items(uiState.recentScans, key = { it.reportId }) { scan ->
                RecentScanItem(
                    report = scan,
                    onClick = { onSelectReport(scan.reportId) }
                )
            }
        }
    }
}

@Composable
fun RecentScanItem(
    report: SecurityReport,
    onClick: () -> Unit
) {
    val dateFormat = SimpleDateFormat("HH:mm • dd MMM", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(report.timestamp))

    VigilCard(
        onClick = onClick,
        contentPadding = PaddingValues(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.destinationDomain,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (!report.sourceAppLabel.isNullOrBlank()) {
                        "${report.sourceAppLabel} • $formattedDate"
                    } else {
                        "Scanner • $formattedDate"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            VigilBadge(riskLevel = report.riskLevel, compact = true)
        }
    }
}

@Composable
fun ColorLegendItem(
    color: Color,
    title: String,
    description: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .padding(top = 4.dp)
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
