package com.vigil.security.core.design.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vigil.security.core.design.VigilTheme
import com.vigil.security.core.model.RiskLevel
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun VigilCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    borderWidth: Dp = 1.dp,
    shape: RoundedCornerShape = RoundedCornerShape(12.dp),
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
            ),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun VigilBadge(
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val securityColors = VigilTheme.securityColors
    val (bgColor, textColor) = when (riskLevel) {
        RiskLevel.SAFE -> securityColors.safeContainer to securityColors.safe
        RiskLevel.CAUTION -> securityColors.cautionContainer to securityColors.caution
        RiskLevel.DANGEROUS -> securityColors.dangerousContainer to securityColors.dangerous
        RiskLevel.UNKNOWN -> securityColors.unknownContainer to securityColors.unknown
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
            .padding(
                horizontal = if (compact) 6.dp else 10.dp,
                vertical = if (compact) 3.dp else 5.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = riskLevel.name,
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.labelLarge,
            color = textColor,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun VigilButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isSecondary: Boolean = false,
    isDanger: Boolean = false,
    leadingIcon: ImageVector? = null
) {
    val securityColors = VigilTheme.securityColors
    val containerColor = when {
        isDanger -> securityColors.dangerous
        isSecondary -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    val contentColor = when {
        isDanger -> Color.White
        isSecondary -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp),
        enabled = enabled,
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.4f),
            disabledContentColor = contentColor.copy(alpha = 0.6f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 0.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (leadingIcon != null) {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun VigilStatusDot(
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val securityColors = VigilTheme.securityColors
    val color = if (isActive) securityColors.safe else securityColors.unknown
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
fun VigilSpeedometerGauge(
    score: Int,
    title: String,
    label: String,
    meterColor: Color,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (score.coerceIn(0, 100)) / 100f,
        animationSpec = tween(durationMillis = 900, easing = FastOutSlowInEasing),
        label = "speedometer_progress"
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            letterSpacing = 0.8.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .size(width = 136.dp, height = 78.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 14.dp.toPx()
                val outerBorderWidth = 2.dp.toPx()
                val pad = strokeWidth / 2f + 4.dp.toPx()
                val arcSize = Size(size.width - pad * 2, (size.height - pad) * 2f)
                val topLeft = Offset(pad, pad)

                // 1. Subtle outer framing border (matching reference image)
                val borderPad = 2.dp.toPx()
                val borderSize = Size(size.width - borderPad * 2, (size.height - borderPad) * 2f)
                drawArc(
                    color = Color(0xFF334155).copy(alpha = 0.5f),
                    startAngle = 180f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(borderPad, borderPad),
                    size = borderSize,
                    style = Stroke(width = outerBorderWidth, cap = StrokeCap.Round)
                )

                // 2. Three colored segments (Reversed: Low is Red on left, High is Green on right!)
                // Left 1/3 (0% - 33%): Red
                drawArc(
                    color = Color(0xFFEF4444),
                    startAngle = 180f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                // Middle 1/3 (34% - 66%): Yellow / Amber
                drawArc(
                    color = Color(0xFFF59E0B),
                    startAngle = 240f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                // Right 1/3 (67% - 100%): Green
                drawArc(
                    color = Color(0xFF10B981),
                    startAngle = 300f,
                    sweepAngle = 60f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                )

                // 3. Pointer Needle (matching reference image)
                val centerX = size.width / 2f
                val centerY = size.height - 2.dp.toPx()
                val needleAngle = Math.toRadians((180f + 180f * animatedProgress).toDouble())
                val needleLength = size.width / 2f - strokeWidth - 2.dp.toPx()

                val tipX = centerX + (needleLength * cos(needleAngle)).toFloat()
                val tipY = centerY + (needleLength * sin(needleAngle)).toFloat()

                // Needle shaft
                val perpAngle = needleAngle + Math.PI / 2
                val baseRadius = 5.dp.toPx()
                val baseLeftX = centerX + (baseRadius * cos(perpAngle)).toFloat()
                val baseLeftY = centerY + (baseRadius * sin(perpAngle)).toFloat()
                val baseRightX = centerX - (baseRadius * cos(perpAngle)).toFloat()
                val baseRightY = centerY - (baseRadius * sin(perpAngle)).toFloat()

                val needlePath = androidx.compose.ui.graphics.Path().apply {
                    moveTo(baseLeftX, baseLeftY)
                    lineTo(tipX, tipY)
                    lineTo(baseRightX, baseRightY)
                    close()
                }
                drawPath(needlePath, color = Color(0xFF0F172A))
                drawPath(needlePath, color = Color.White, style = Stroke(width = 1.dp.toPx()))

                // Needle Hub (black circle with white hollow center, matching reference!)
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = 9.dp.toPx(),
                    center = Offset(centerX, centerY)
                )
                drawCircle(
                    color = Color.White,
                    radius = 8.5.dp.toPx(),
                    center = Offset(centerX, centerY),
                    style = Stroke(width = 1.dp.toPx())
                )
                drawCircle(
                    color = Color.White,
                    radius = 3.5.dp.toPx(),
                    center = Offset(centerX, centerY)
                )
            }

            // Digital readout in the center
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(bottom = 2.dp)
            ) {
                Text(
                    text = "$score%",
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 15.sp),
                    fontWeight = FontWeight.ExtraBold,
                    color = when {
                        score >= 67 -> Color(0xFF10B981)
                        score >= 34 -> Color(0xFFF59E0B)
                        else -> Color(0xFFEF4444)
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = when {
                score >= 67 -> Color(0xFF10B981)
                score >= 34 -> Color(0xFFF59E0B)
                else -> Color(0xFFEF4444)
            },
            maxLines = 1
        )
    }
}

@Composable
fun VigilTrustMeter(
    score: Int,
    riskLevel: RiskLevel,
    modifier: Modifier = Modifier
) {
    val securityColors = VigilTheme.securityColors
    val (meterColor, label) = when (riskLevel) {
        RiskLevel.SAFE -> securityColors.safe to "Safe / High Trust"
        RiskLevel.CAUTION -> securityColors.caution to "Suspicious"
        RiskLevel.DANGEROUS -> securityColors.dangerous to "Critical Threat"
        RiskLevel.UNKNOWN -> securityColors.unknown to "Unverified"
    }

    VigilSpeedometerGauge(
        score = score,
        title = "TRUST",
        label = label,
        meterColor = meterColor,
        modifier = modifier
    )
}

@Composable
fun VigilConfidenceMeter(
    confidence: com.vigil.security.core.model.ConfidenceLevel,
    score: Int,
    modifier: Modifier = Modifier
) {
    VigilSpeedometerGauge(
        score = score,
        title = "CONFIDENCE",
        label = "${confidence.displayName} Certainty",
        meterColor = MaterialTheme.colorScheme.primary,
        modifier = modifier
    )
}

@Composable
fun VigilEmptyState(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onActionClick: (() -> Unit)? = null
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionText != null && onActionClick != null) {
            Spacer(modifier = Modifier.height(20.dp))
            OutlinedButton(
                onClick = onActionClick,
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Text(
                    text = actionText,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun PulsingHeart(
    modifier: Modifier = Modifier,
    size: Dp = 15.dp,
    color: Color = Color(0xFFEF4444)
) {
    val infiniteTransition = rememberInfiniteTransition(label = "heartPulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1100
                0.9f at 0 with FastOutSlowInEasing
                1.25f at 180 with FastOutSlowInEasing
                1.02f at 360 with FastOutSlowInEasing
                1.28f at 540 with FastOutSlowInEasing
                0.9f at 900 with FastOutSlowInEasing
                0.9f at 1100
            },
            repeatMode = RepeatMode.Restart
        ),
        label = "heartScale"
    )

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        val w = this.size.width
        val h = this.size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.85f)
            cubicTo(
                w * 0.08f, h * 0.55f,
                w * 0.02f, h * 0.22f,
                w * 0.28f, h * 0.12f
            )
            cubicTo(
                w * 0.42f, h * 0.06f,
                w * 0.5f, h * 0.25f,
                w * 0.5f, h * 0.25f
            )
            cubicTo(
                w * 0.5f, h * 0.25f,
                w * 0.58f, h * 0.06f,
                w * 0.72f, h * 0.12f
            )
            cubicTo(
                w * 0.98f, h * 0.22f,
                w * 0.92f, h * 0.55f,
                w * 0.5f, h * 0.85f
            )
            close()
        }
        drawPath(path = path, color = color)
    }
}

