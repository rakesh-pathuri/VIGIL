package com.vigil.security.ui.scanner

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigil.security.core.design.VigilTheme
import com.vigil.security.core.design.components.*
import com.vigil.security.core.model.Evidence
import com.vigil.security.core.model.RiskLevel
import com.vigil.security.core.model.SecurityReport

@Composable
fun ScannerScreen(
    viewModel: ScannerViewModel,
    onViewFullReport: (String) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 20.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Link Scanner",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Analyze any URL before opening.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // URL Input Field
        item {
            VigilCard {
                OutlinedTextField(
                    value = uiState.inputUrl,
                    onValueChange = { viewModel.onUrlInputChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            text = "https://example.com",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Search
                    ),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            keyboard?.hide()
                            viewModel.submitUrlForAnalysis()
                        }
                    ),
                    trailingIcon = {
                        if (uiState.inputUrl.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearInput() }) {
                                Icon(
                                    imageVector = Icons.Outlined.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(8.dp)
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            clipboard.getText()?.let {
                                viewModel.onUrlInputChanged(it.text)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentPaste,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Paste")
                    }

                    Button(
                        onClick = {
                            keyboard?.hide()
                            viewModel.submitUrlForAnalysis()
                        },
                        modifier = Modifier.weight(2f),
                        shape = RoundedCornerShape(8.dp),
                        enabled = uiState.scanStatus !is ScanStatus.Analyzing
                    ) {
                        Text(
                            text = if (uiState.scanStatus is ScanStatus.Analyzing) "Analyzing..." else "Analyze",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }

        // Scan Progress Indicator
        when (val status = uiState.scanStatus) {
            is ScanStatus.Analyzing -> {
                item {
                    VigilCard {
                        Text(
                            text = status.currentStage,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { status.progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                    }
                }
            }
            is ScanStatus.Error -> {
                item {
                    VigilCard(
                        borderColor = VigilTheme.securityColors.dangerous.copy(alpha = 0.5f),
                        backgroundColor = VigilTheme.securityColors.dangerousContainer
                    ) {
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodyMedium,
                            color = VigilTheme.securityColors.dangerous
                        )
                    }
                }
            }
            is ScanStatus.Success -> {
                item {
                    ReportResultCard(
                        report = status.report,
                        onViewFullReport = { onViewFullReport(status.report.reportId) },
                        context = context
                    )
                }
            }
            ScanStatus.Idle -> {}
        }
    }
}

@Composable
fun ReportResultCard(
    report: SecurityReport,
    onViewFullReport: () -> Unit,
    context: Context
) {
    val securityColors = VigilTheme.securityColors

    VigilCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "VERDICT",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.8.sp
            )
            VigilBadge(riskLevel = report.riskLevel)
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Destination",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = report.destinationDomain,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("domain", report.destinationDomain))
                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentCopy,
                    contentDescription = "Copy",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)

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

        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp), color = MaterialTheme.colorScheme.outline)

        Text(
            text = report.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = report.recommendedAction,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (report.riskLevel == RiskLevel.DANGEROUS) securityColors.dangerous else MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "Vigil Report: ${report.destinationDomain} - ${report.riskLevel.name}\n${report.summary}"
                        )
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share"))
                },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Share", maxLines = 1)
            }

            Button(
                onClick = onViewFullReport,
                modifier = Modifier.weight(2.2f),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Verify Report",
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
