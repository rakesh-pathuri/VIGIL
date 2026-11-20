package com.vigil.security.ui.activity

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigil.security.core.design.VigilTheme
import com.vigil.security.core.design.components.VigilBadge
import com.vigil.security.core.design.components.VigilCard
import com.vigil.security.core.design.components.VigilEmptyState
import com.vigil.security.core.model.RiskLevel
import com.vigil.security.core.model.SecurityReport
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ActivityScreen(
    viewModel: ActivityViewModel,
    onSelectReport: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = {
                Text(
                    text = "Clear Security Activity",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently clear all link inspection history? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        showClearConfirmDialog = false
                    }
                ) {
                    Text(
                        text = "Clear All",
                        color = VigilTheme.securityColors.dangerous,
                        fontWeight = FontWeight.Bold
                    )
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Security Activity",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Local chronological audit of inspected links.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (uiState.scans.isNotEmpty()) {
                IconButton(onClick = { showClearConfirmDialog = true }) {
                    Icon(
                        imageVector = Icons.Outlined.DeleteSweep,
                        contentDescription = "Clear History",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = { viewModel.onSearchQueryChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = {
                Text(
                    text = "Search by domain, URL or app...",
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
                    IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
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
                    onClick = { viewModel.onFilterSelected(null) },
                    label = { Text("All") },
                    shape = RoundedCornerShape(8.dp)
                )
            }
            items(RiskLevel.values()) { risk ->
                FilterChip(
                    selected = uiState.selectedFilter == risk,
                    onClick = {
                        viewModel.onFilterSelected(if (uiState.selectedFilter == risk) null else risk)
                    },
                    label = { Text(risk.name) },
                    shape = RoundedCornerShape(8.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Activity List
        if (uiState.scans.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                VigilEmptyState(
                    title = if (uiState.searchQuery.isNotEmpty()) "No matching records" else "No security records",
                    description = if (uiState.searchQuery.isNotEmpty()) {
                        "No analyzed links match '${uiState.searchQuery}'. Try another query or clear filters."
                    } else {
                        "Your link inspection history is currently empty. Detected URLs will be safely indexed here."
                    }
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(uiState.scans, key = { it.reportId }) { report ->
                    ActivityItemCard(
                        report = report,
                        onClick = { onSelectReport(report.reportId) },
                        onDelete = { viewModel.deleteScan(report.reportId) }
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityItemCard(
    report: SecurityReport,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("HH:mm • dd MMM yyyy", Locale.getDefault())
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = report.destinationDomain,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = if (!report.sourceAppLabel.isNullOrBlank()) {
                        "Source: ${report.sourceAppLabel} • $formattedDate"
                    } else {
                        "Source: Manual Scan • $formattedDate"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = report.originalUrl,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VigilBadge(riskLevel = report.riskLevel, compact = true)
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Delete,
                        contentDescription = "Delete record",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}
