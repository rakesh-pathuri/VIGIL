package com.vigil.security.ui.reports

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigil.security.core.design.VigilTheme
import com.vigil.security.core.design.components.*
import com.vigil.security.core.model.AnalysisCheck
import com.vigil.security.core.model.CheckStatus
import com.vigil.security.core.model.RiskLevel
import com.vigil.security.core.model.SecurityReport
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReportsScreen(
    viewModel: ReportViewModel,
    onNavigateToScanner: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val report = uiState.selectedReport

    if (report != null) {
        // Back press returns to the date/time-wise list of reports
        BackHandler(enabled = true) {
            viewModel.clearSelectedReport()
        }

        FullReportDetailView(
            report = report,
            onBack = { viewModel.clearSelectedReport() }
        )
    } else {
        ReportsListView(
            uiState = uiState,
            onSelectReport = { reportId -> viewModel.selectReportById(reportId) },
            onFilterSelected = { risk -> viewModel.onFilterSelected(risk) },
            onSearchQueryChanged = { query -> viewModel.onSearchQueryChanged(query) },
            onNavigateToScanner = onNavigateToScanner
        )
    }
}

/**
 * Screen 1: Date & Time-wise Classification of Reports List
 */
@Composable
private fun ReportsListView(
    uiState: ReportUiState,
    onSelectReport: (String) -> Unit,
    onFilterSelected: (RiskLevel?) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onNavigateToScanner: () -> Unit
) {
    val securityColors = VigilTheme.securityColors

    // Group reports by calendar date header (Today, Yesterday, Date)
    val groupedReports = remember(uiState.filteredReports) {
        val map = LinkedHashMap<String, MutableList<SecurityReport>>()
        for (item in uiState.filteredReports) {
            val header = formatDateGroupHeader(item.timestamp)
            map.getOrPut(header) { mutableListOf() }.add(item)
        }
        map
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Security Reports",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Date & time classified security reports.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Search Bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onSearchQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = {
                Text(
                    text = "Search by domain, URL, or app...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                if (uiState.searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChanged("") }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(8.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Classification Filter Chips
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                FilterChip(
                    selected = uiState.selectedFilter == null,
                    onClick = { onFilterSelected(null) },
                    label = { Text("All (${uiState.allReports.size})") },
                    shape = RoundedCornerShape(8.dp)
                )
            }
            item {
                val dangerousCount = uiState.allReports.count { it.riskLevel == RiskLevel.DANGEROUS }
                FilterChip(
                    selected = uiState.selectedFilter == RiskLevel.DANGEROUS,
                    onClick = {
                        onFilterSelected(if (uiState.selectedFilter == RiskLevel.DANGEROUS) null else RiskLevel.DANGEROUS)
                    },
                    label = {
                        Text(
                            text = "Dangerous ($dangerousCount)",
                            color = if (uiState.selectedFilter == RiskLevel.DANGEROUS) securityColors.dangerous else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
            item {
                val cautionCount = uiState.allReports.count { it.riskLevel == RiskLevel.CAUTION }
                FilterChip(
                    selected = uiState.selectedFilter == RiskLevel.CAUTION,
                    onClick = {
                        onFilterSelected(if (uiState.selectedFilter == RiskLevel.CAUTION) null else RiskLevel.CAUTION)
                    },
                    label = {
                        Text(
                            text = "Caution ($cautionCount)",
                            color = if (uiState.selectedFilter == RiskLevel.CAUTION) securityColors.caution else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
            item {
                val safeCount = uiState.allReports.count { it.riskLevel == RiskLevel.SAFE }
                FilterChip(
                    selected = uiState.selectedFilter == RiskLevel.SAFE,
                    onClick = {
                        onFilterSelected(if (uiState.selectedFilter == RiskLevel.SAFE) null else RiskLevel.SAFE)
                    },
                    label = {
                        Text(
                            text = "Safe ($safeCount)",
                            color = if (uiState.selectedFilter == RiskLevel.SAFE) securityColors.safe else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Content Area
        if (uiState.allReports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                VigilEmptyState(
                    title = "No classified reports yet",
                    description = "Analyzed links from WhatsApp, Chrome, or manual scans will appear here organized chronologically.",
                    actionText = "Open Link Scanner",
                    onActionClick = onNavigateToScanner
                )
            }
        } else if (uiState.filteredReports.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                VigilEmptyState(
                    title = "No matching reports",
                    description = "No classified scans match '${uiState.searchQuery}'. Tap 'All' to view all reports.",
                    actionText = "Clear Search",
                    onActionClick = {
                        onSearchQueryChanged("")
                        onFilterSelected(null)
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 4.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                groupedReports.forEach { (dateHeader, reportsForDate) ->
                    item(key = "header_$dateHeader") {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = dateHeader.uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 1.sp
                            )
                            Text(
                                text = "${reportsForDate.size} ${if (reportsForDate.size == 1) "scan" else "scans"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(reportsForDate, key = { it.reportId }) { reportItem ->
                        ReportItemCard(
                            report = reportItem,
                            onClick = { onSelectReport(reportItem.reportId) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Chronological Report Card Item in the List
 */
@Composable
private fun ReportItemCard(
    report: SecurityReport,
    onClick: () -> Unit
) {
    val securityColors = VigilTheme.securityColors
    val timeFormat = remember { SimpleDateFormat("h:mm a", Locale.getDefault()) }
    val timeStr = timeFormat.format(Date(report.timestamp))

    val (verdictColor, verdictBg) = when (report.riskLevel) {
        RiskLevel.SAFE -> securityColors.safe to securityColors.safeContainer.copy(alpha = 0.35f)
        RiskLevel.CAUTION -> securityColors.caution to securityColors.cautionContainer.copy(alpha = 0.35f)
        RiskLevel.DANGEROUS -> securityColors.dangerous to securityColors.dangerousContainer.copy(alpha = 0.35f)
        RiskLevel.UNKNOWN -> securityColors.unknown to securityColors.unknownContainer.copy(alpha = 0.35f)
    }

    VigilCard(
        onClick = onClick,
        borderColor = verdictColor.copy(alpha = 0.35f),
        backgroundColor = verdictBg,
        contentPadding = PaddingValues(14.dp)
    ) {
        // Top row: Domain & Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = report.destinationDomain,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = timeStr,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = " • ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = if (!report.sourceAppLabel.isNullOrBlank()) report.sourceAppLabel else "Manual Scan",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))
            VigilBadge(riskLevel = report.riskLevel)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Summary snippet
        Text(
            text = report.summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 18.sp
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Bottom row: Trust metric & View link
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(verdictColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${report.trustScore}% Trust • ${report.confidence.name} Confidence",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Medium
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "View Report",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = Icons.Outlined.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Screen 2: Full Scan Report Detail View with Back navigation
 */
@Composable
private fun FullReportDetailView(
    report: SecurityReport,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val securityColors = VigilTheme.securityColors
    var showTechnicalDetails by remember { mutableStateOf(false) }

    val dateFormat = remember { SimpleDateFormat("HH:mm • dd MMM yyyy", Locale.getDefault()) }
    val formattedTimestamp = dateFormat.format(Date(report.timestamp))

    val (verdictColor, verdictBg) = when (report.riskLevel) {
        RiskLevel.SAFE -> securityColors.safe to securityColors.safeContainer
        RiskLevel.CAUTION -> securityColors.caution to securityColors.cautionContainer
        RiskLevel.DANGEROUS -> securityColors.dangerous to securityColors.dangerousContainer
        RiskLevel.UNKNOWN -> securityColors.unknown to securityColors.unknownContainer
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Top Bar with Back Button & Share
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onBack)
                ) {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.Outlined.ArrowBack,
                            contentDescription = "Back to reports list",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(modifier = Modifier.width(2.dp))
                    Column {
                        Text(
                            text = "Full Scan Report",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = if (!report.sourceAppLabel.isNullOrBlank()) "Detected in ${report.sourceAppLabel}" else "Manual Scan",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                IconButton(
                    onClick = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "Vigil Security Report\nDomain: ${report.destinationDomain}\nVerdict: ${report.riskLevel.name}\nTrust Score: ${report.trustScore}%\nRecommendation: ${report.recommendedAction}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Report"))
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share Report",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // 1. Hero Verdict & Trust Card
        item {
            VigilCard(
                borderColor = verdictColor.copy(alpha = 0.4f),
                backgroundColor = verdictBg
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "SECURITY CLASSIFICATION",
                            style = MaterialTheme.typography.labelSmall,
                            color = verdictColor,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = report.destinationDomain,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = formattedTimestamp,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    VigilBadge(riskLevel = report.riskLevel)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Speedometer Gauges (Trust & Confidence side-by-side)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VigilTrustMeter(
                        score = report.trustScore,
                        riskLevel = report.riskLevel,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    VigilConfidenceMeter(
                        confidence = report.confidence,
                        score = report.confidenceScore,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // 2. Simple Explanation Card (What is this link?)
        item {
            VigilCard {
                Text(
                    text = "WHAT YOU NEED TO KNOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = report.summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    lineHeight = 22.sp
                )

                if (report.findings.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Key Reasons:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    for (finding in report.findings) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp)
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(verdictColor)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = finding.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = finding.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // 3. Recommended Action
        item {
            val recBg = if (report.riskLevel == RiskLevel.DANGEROUS) {
                securityColors.dangerousContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            }

            VigilCard(
                borderColor = verdictColor.copy(alpha = 0.3f),
                backgroundColor = recBg
            ) {
                Text(
                    text = "RECOMMENDED ACTION",
                    style = MaterialTheme.typography.labelSmall,
                    color = verdictColor,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = report.recommendedAction,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (report.riskLevel == RiskLevel.DANGEROUS) securityColors.dangerous else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // 4. Link & Privacy Details
        item {
            VigilCard {
                Text(
                    text = "LINK DETAILS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = report.originalUrl,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("url", report.originalUrl))
                            Toast.makeText(context, "Link copied", Toast.LENGTH_SHORT).show()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy Link",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

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
                        text = "100% on-device private scan • No browsing data leaves your phone",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Vigil can make mistakes. Always verify critical links.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                    )
                }
            }
        }

        // 5. Expandable Technical Details
        item {
            VigilCard(
                onClick = { showTechnicalDetails = !showTechnicalDetails }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Technical Forensic Inspection",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (showTechnicalDetails) "Tap to collapse advanced telemetry" else "Tap to view ${report.checksCompleted.size} heuristic verification stages",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = if (showTechnicalDetails) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        if (showTechnicalDetails) {
            item {
                Text(
                    text = "VERIFICATION STAGES (${report.checksCompleted.size})",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    letterSpacing = 0.8.sp
                )
            }

            items(report.checksCompleted.size) { index ->
                val check = report.checksCompleted[index]
                AnalysisCheckRow(check = check)
            }
        }
    }
}

@Composable
fun AnalysisCheckRow(check: AnalysisCheck) {
    val securityColors = VigilTheme.securityColors
    val statusColor = when (check.status) {
        CheckStatus.PASSED -> securityColors.safe
        CheckStatus.WARNING -> securityColors.caution
        CheckStatus.FAILED -> securityColors.dangerous
        CheckStatus.UNAVAILABLE -> securityColors.unknown
    }

    VigilCard(
        contentPadding = PaddingValues(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = check.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = check.explanation,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = check.status.name,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor
            )
        }
    }
}

/**
 * Groups timestamps into human readable calendar headers
 */
private fun formatDateGroupHeader(timestamp: Long): String {
    val now = Calendar.getInstance()
    val reportCal = Calendar.getInstance().apply { timeInMillis = timestamp }

    val isSameYear = now.get(Calendar.YEAR) == reportCal.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - reportCal.get(Calendar.DAY_OF_YEAR)

    return when {
        isSameYear && dayDiff == 0 -> "Today"
        isSameYear && dayDiff == 1 -> "Yesterday"
        isSameYear -> SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("d MMMM yyyy", Locale.getDefault()).format(Date(timestamp))
    }
}
