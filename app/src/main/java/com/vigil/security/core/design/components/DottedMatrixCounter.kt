package com.vigil.security.core.design.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigil.security.threatintel.OpenPhishStats
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.util.*
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow

/* ---------- 3x5 Dot Matrix Digit Definitions ---------- */

private val DIGIT_3X5: Map<Char, List<BooleanArray>> = mapOf(
    '0' to listOf(
        booleanArrayOf(true, true, true),
        booleanArrayOf(true, false, true),
        booleanArrayOf(true, false, true),
        booleanArrayOf(true, false, true),
        booleanArrayOf(true, true, true)
    ),
    '1' to listOf(
        booleanArrayOf(false, true, false),
        booleanArrayOf(true, true, false),
        booleanArrayOf(false, true, false),
        booleanArrayOf(false, true, false),
        booleanArrayOf(true, true, true)
    ),
    '2' to listOf(
        booleanArrayOf(true, true, true),
        booleanArrayOf(false, false, true),
        booleanArrayOf(true, true, true),
        booleanArrayOf(true, false, false),
        booleanArrayOf(true, true, true)
    ),
    '3' to listOf(
        booleanArrayOf(true, true, true),
        booleanArrayOf(false, false, true),
        booleanArrayOf(true, true, true),
        booleanArrayOf(false, false, true),
        booleanArrayOf(true, true, true)
    ),
    '4' to listOf(
        booleanArrayOf(true, false, true),
        booleanArrayOf(true, false, true),
        booleanArrayOf(true, true, true),
        booleanArrayOf(false, false, true),
        booleanArrayOf(false, false, true)
    ),
    '5' to listOf(
        booleanArrayOf(true, true, true),
        booleanArrayOf(true, false, false),
        booleanArrayOf(true, true, true),
        booleanArrayOf(false, false, true),
        booleanArrayOf(true, true, true)
    ),
    '6' to listOf(
        booleanArrayOf(true, true, true),
        booleanArrayOf(true, false, false),
        booleanArrayOf(true, true, true),
        booleanArrayOf(true, false, true),
        booleanArrayOf(true, true, true)
    ),
    '7' to listOf(
        booleanArrayOf(true, true, true),
        booleanArrayOf(false, false, true),
        booleanArrayOf(false, false, true),
        booleanArrayOf(false, true, false),
        booleanArrayOf(false, true, false)
    ),
    '8' to listOf(
        booleanArrayOf(true, true, true),
        booleanArrayOf(true, false, true),
        booleanArrayOf(true, true, true),
        booleanArrayOf(true, false, true),
        booleanArrayOf(true, true, true)
    ),
    '9' to listOf(
        booleanArrayOf(true, true, true),
        booleanArrayOf(true, false, true),
        booleanArrayOf(true, true, true),
        booleanArrayOf(false, false, true),
        booleanArrayOf(true, true, true)
    ),
    ',' to listOf(
        booleanArrayOf(false),
        booleanArrayOf(false),
        booleanArrayOf(false),
        booleanArrayOf(true),
        booleanArrayOf(true)
    )
)

/**
 * Renders a numeric string as a glowing 3x5 cyber dot matrix.
 * Matches DottedShield shader style: radial glow halo, solid neon core, and specular highlight.
 */
@Composable
fun DottedMatrixCounter(
    text: String,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "matrix_counter")
    val wave by inf.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2800, easing = LinearEasing)),
        label = "matrix_wave"
    )
    val breath by inf.animateFloat(
        initialValue = 0.9f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1600, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "matrix_breath"
    )

    Canvas(modifier = modifier) {
        val chars = text.toCharArray()
        var totalCols = 0
        val colWidths = IntArray(chars.size)

        for (i in chars.indices) {
            val ch = chars[i]
            val w = if (ch == ',') 1 else 3
            colWidths[i] = w
            totalCols += w
            if (i < chars.size - 1) {
                totalCols += if (ch == ',' || chars[i + 1] == ',') 1 else 1
            }
        }

        if (totalCols == 0) return@Canvas

        val pitchX = size.width / (totalCols + 1f)
        val pitchY = size.height / 5f
        val pitch = min(pitchX, pitchY)

        val totalW = totalCols * pitch
        val totalH = 5f * pitch
        val startX = (size.width - totalW) / 2f + pitch * 0.5f
        val startY = (size.height - totalH) / 2f + pitch * 0.5f

        val dotRadius = pitch * 0.32f
        val haloRadius = dotRadius * 2.8f

        var curCol = 0
        for (charIdx in chars.indices) {
            val ch = chars[charIdx]
            val pattern = DIGIT_3X5[ch]
            val w = colWidths[charIdx]

            for (row in 0..4) {
                for (col in 0 until w) {
                    val isLit = pattern?.getOrNull(row)?.getOrNull(col) == true
                    val x = startX + (curCol + col) * pitch
                    val y = startY + row * pitch
                    val center = Offset(x, y)

                    val tX = (curCol + col).toFloat() / totalCols
                    val pulse = (0.5f + 0.5f * cos(2f * PI.toFloat() * (tX - wave))).pow(2.5f)

                    if (isLit) {
                        // Neon Cyan to Electric Sky gradient
                        val color = Color(
                            red = 0.05f + 0.15f * (1f - tX),
                            green = 0.90f - 0.15f * tX,
                            blue = 0.85f + 0.15f * tX,
                            alpha = 1f
                        )
                        val glow = (0.4f + 0.6f * pulse) * breath

                        // Outer soft glowing halo
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to color.copy(alpha = 0.45f * glow),
                                1f to Color.Transparent,
                                center = center,
                                radius = haloRadius
                            ),
                            radius = haloRadius,
                            center = center
                        )

                        // Core bright dot
                        drawCircle(
                            color = color,
                            radius = dotRadius * (1f + 0.12f * pulse),
                            center = center
                        )

                        // Specular highlight
                        drawCircle(
                            color = Color.White.copy(alpha = 0.3f * pulse),
                            radius = dotRadius * 0.4f,
                            center = center - Offset(dotRadius * 0.2f, dotRadius * 0.2f)
                        )
                    } else {
                        // Faint inactive matrix guide dot for authentic LED matrix appearance
                        drawCircle(
                            color = Color(0xFF38BDF8).copy(alpha = 0.07f),
                            radius = dotRadius * 0.55f,
                            center = center
                        )
                    }
                }
            }

            curCol += w
            if (charIdx < chars.size - 1) {
                curCol += 1
            }
        }
    }
}

/**
 * Real-time OpenPhish Threat Intelligence card displaying verified stats from openphish.com:
 * - Active Confirmed Phishing URLs in Glowing Dotted Matrix (e.g. 5,681)
 * - URLs Processed (e.g. 4,770,780)
 * - Brands Targeted (e.g. 151)
 */
@Composable
fun OpenPhishTelemetryCard(
    stats: OpenPhishStats,
    modifier: Modifier = Modifier
) {
    val inf = rememberInfiniteTransition(label = "pulse_indicator")
    val dotAlpha by inf.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(900, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "dot_alpha"
    )

    VigilCard(
        modifier = modifier,
        borderColor = Color(0xFF38BDF8).copy(alpha = 0.3f),
        backgroundColor = Color(0xFF040D1A)
    ) {
        // Header Row: Pulsing Live Indicator & Source Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444).copy(alpha = dotAlpha))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "OPENPHISH THREAT INTEL",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF38BDF8),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFF00F5D4).copy(alpha = 0.12f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "openphish.com",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF00F5D4),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Center Dotted Matrix Digits: Active Confirmed Phishing URLs (e.g. 5,681)
        DottedMatrixCounter(
            text = stats.newPhishingUrls,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "ACTIVE CONFIRMED PHISHING URLS",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFEF4444),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0xFF38BDF8).copy(alpha = 0.15f))
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Real OpenPhish Statistics Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "URLs Processed: ${stats.urlsProcessed}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
            Text(
                text = "Brands Targeted: ${stats.brandsTargeted}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp
            )
        }
    }
}

@Composable
fun GlobalAttackTelemetryCard(
    modifier: Modifier = Modifier
) {
    OpenPhishTelemetryCard(
        stats = OpenPhishStats(),
        modifier = modifier
    )
}
