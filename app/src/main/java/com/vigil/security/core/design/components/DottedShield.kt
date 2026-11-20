package com.vigil.security.core.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.util.lerp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt

/* ---------- Design space (matches the 1254 x 1254 reference image) ---------- */

private const val DESIGN = 1254f
private val CENTER = Offset(628f, 625f)

private class Dot(
    val pos: Offset,
    val radius: Float,
    val hue: Float,
    val sat: Float,
    val phase: Float,   // 0..1 position around the shield, drives the travelling glow
    val delay: Float,   // when this dot pops in during the intro
    val isCheck: Boolean
)

private class Row(val offset: Float, val radius: Float)
private class Seg(val a: Offset, val b: Offset, val rows: List<Row>)

private val SIDE = listOf(Row(21f, 13f), Row(-21f, 10f))
private val DIAG = listOf(Row(29f, 12.5f), Row(0f, 11f), Row(-29f, 9.5f))

// Shield centre-line, clockwise from the top
private val SHIELD = listOf(
    Seg(Offset(628f, 344f), Offset(872f, 448f), SIDE),
    Seg(Offset(872f, 448f), Offset(872f, 650f), SIDE),
    Seg(Offset(872f, 650f), Offset(628f, 910f), DIAG),
    Seg(Offset(628f, 910f), Offset(384f, 650f), DIAG),
    Seg(Offset(384f, 650f), Offset(384f, 448f), SIDE),
    Seg(Offset(384f, 448f), Offset(628f, 344f), SIDE),
)

private val CHECK_L = Seg(
    Offset(531f, 607f), Offset(620f, 692f),
    listOf(Row(21f, 11f), Row(-21f, 10f))
)
private val CHECK_R = Seg(
    Offset(620f, 692f), Offset(778f, 536f),
    listOf(Row(27f, 10f), Row(0f, 11.5f), Row(-27f, 10f))
)

// Tiny "stray" sparkle dots from the reference
private val STRAYS = listOf(
    Offset(465f, 387f) to 5f,
    Offset(452f, 471f) to 7f,
    Offset(836f, 404f) to 2.5f,
    Offset(888f, 689f) to 5f,
)

/* ---------- Colour helpers ---------- */

// (angle in degrees around the shield, hue) — unwrapped so interpolation is smooth.
// Left = green, top = amber, right = red, bottom-right = violet/blue, bottom = cyan-blue.
private val HUE_KEYS = listOf(
    180f to 160f, 220f to 158f, 240f to 105f, 255f to 62f, 270f to 45f,
    290f to 28f, 305f to 12f, 320f to -5f, 345f to -12f, 366f to -35f,
    380f to -80f, 392f to -130f, 450f to -155f, 495f to -170f, 540f to -200f
)

private fun hueAt(angleDeg: Float): Float {
    var a = angleDeg
    if (a < 180f) a += 360f
    if (a > 540f) a -= 360f
    for (i in 0 until HUE_KEYS.size - 1) {
        val (a0, h0) = HUE_KEYS[i]
        val (a1, h1) = HUE_KEYS[i + 1]
        if (a in a0..a1) {
            val h = lerp(h0, h1, (a - a0) / (a1 - a0))
            return ((h % 360f) + 360f) % 360f
        }
    }
    return 160f
}

// Checkmark: light blue -> cyan at the vertex -> near-white lavender at the tip
private fun checkHue(p: Float) =
    if (p < 0.5f) lerp(208f, 196f, p / 0.5f) else lerp(196f, 275f, (p - 0.5f) / 0.5f)

private fun checkSat(p: Float) =
    if (p < 0.5f) lerp(0.58f, 1f, p / 0.5f) else lerp(1f, 0.08f, (p - 0.5f) / 0.5f)

private fun easeOutBack(t: Float): Float {
    val c1 = 1.70158f
    val c3 = c1 + 1f
    val x = t - 1f
    return 1f + c3 * x * x * x + c1 * x * x
}

private fun colorFromHsv(hue: Float, sat: Float, value: Float = 1f): Color {
    val h = ((hue % 360f) + 360f) % 360f
    val s = sat.coerceIn(0f, 1f)
    val v = value.coerceIn(0f, 1f)
    return Color(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
}

/* ---------- Dot generation ---------- */

private fun forEachDot(seg: Seg, spacing: Float, block: (Offset, Float, Float) -> Unit) {
    val d = seg.b - seg.a
    val len = d.getDistance()
    val dir = d / len
    var n = Offset(-dir.y, dir.x)
    val mid = (seg.a + seg.b) / 2f
    if ((mid.x - CENTER.x) * n.x + (mid.y - CENTER.y) * n.y < 0f) n = -n // point outward

    val count = max(1, (len / spacing).roundToInt())
    for (i in 0..count) {
        val t = i / count.toFloat()
        seg.rows.forEachIndexed { rowIdx, row ->
            val stagger = if (rowIdx % 2 == 1) dir * (spacing / 2f) else Offset.Zero
            block(seg.a + d * t + stagger + n * row.offset, row.radius, t)
        }
    }
}

private fun outlineDot(p: Offset, r: Float): Dot {
    val ang = atan2(p.y - CENTER.y, p.x - CENTER.x)
    val deg = Math.toDegrees(ang.toDouble()).toFloat()
    val twoPi = (2 * PI).toFloat()
    val phase = (((ang + twoPi / 4f) / twoPi) % 1f + 1f) % 1f // 0 at top, clockwise
    return Dot(p, r, hueAt(deg), 0.95f, phase, phase * 0.7f, false)
}

private fun buildDots(): List<Dot> {
    val out = mutableListOf<Dot>()
    fun add(d: Dot) {
        if (out.none { (it.pos - d.pos).getDistance() < 27f }) out += d
    }

    SHIELD.forEach { seg -> forEachDot(seg, 43f) { p, r, _ -> add(outlineDot(p, r)) } }
    STRAYS.forEach { (p, r) -> out += outlineDot(p, r) }

    listOf(CHECK_L to 0f, CHECK_R to 0.5f).forEach { (seg, p0) ->
        forEachDot(seg, 40f) { p, r, t ->
            val prog = p0 + 0.5f * t
            add(Dot(p, r, checkHue(prog), checkSat(prog), prog, 0.62f + prog * 0.3f, true))
        }
    }
    return out
}

/* ---------- The composable ---------- */

/**
 * Glowing dotted cyber shield:
 *  1. Dots pop in (overshoot spring) clockwise around the shield, then the checkmark draws in.
 *  2. A bright pulse travels around the shield continuously.
 *  3. Whole thing gently breathes. Optional slow rainbow rotation via [hueCycle].
 *
 * Change [replayKey] to replay the intro animation.
 */
@Composable
fun DottedShield(
    modifier: Modifier = Modifier,
    hueCycle: Boolean = false,
    replayKey: Any? = null
) {
    val dots = remember { buildDots() }

    val intro = remember { Animatable(0f) }
    LaunchedEffect(replayKey) {
        intro.snapTo(0f)
        intro.animateTo(1.15f, tween(durationMillis = 2600, easing = LinearEasing))
    }

    val inf = rememberInfiniteTransition(label = "shield")
    val wave by inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "wave"
    )
    val hueShift by inf.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(14000, easing = LinearEasing)),
        label = "hue"
    )
    val breath by inf.animateFloat(
        initialValue = 0.88f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse
        ),
        label = "breath"
    )

    Canvas(modifier = modifier.aspectRatio(1f)) {
        val s = size.minDimension / DESIGN
        val ox = (size.width - DESIGN * s) / 2f
        val oy = (size.height - DESIGN * s) / 2f
        val introValue = intro.value

        for (dot in dots) {
            val t = ((introValue - dot.delay) / 0.15f).coerceIn(0f, 1f)
            if (t <= 0f) continue
            val popScale = easeOutBack(t)

            // Travelling pulse: 0..1, sharpened so only a section lights up
            val pulse = (0.5 + 0.5 * cos(2.0 * PI * (dot.phase.toDouble() - wave.toDouble()))).pow(2.5).toFloat()
            val glow = (0.35f + 0.65f * pulse) * breath

            val hue = if (hueCycle && !dot.isCheck) (dot.hue + hueShift) % 360f else dot.hue
            val color = colorFromHsv(hue, dot.sat, 1f)

            val r = dot.radius * s * popScale * (1f + 0.18f * pulse)
            val c = Offset(ox + dot.pos.x * s, oy + dot.pos.y * s)
            val glowR = r * 3.4f

            // Soft halo
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = 0.5f * glow * t),
                    1f to Color.Transparent,
                    center = c,
                    radius = glowR
                ),
                radius = glowR,
                center = c
            )
            // Core
            drawCircle(color = color, radius = r, center = c)
            // Tiny specular highlight
            drawCircle(
                color = Color.White.copy(alpha = 0.25f * pulse),
                radius = r * 0.45f,
                center = c - Offset(r * 0.2f, r * 0.2f)
            )
        }
    }
}
