package com.example.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class ThaiRank(
    val title: String,
    val starRange: String,
    val minStars: Int,
    val maxStars: Int,
    val badgeSymbol: String,
    val description: String,
    val primaryColor: Color,
    val secColor: Color
) {
    IRON(
        "Iron", 
        "0-149 Stars", 
        0, 
        149,
        "Backpack", 
        "Represents the beginning of your Thai journey. Equipping your travel pack!", 
        Color(0xFF8E9AA6), 
        Color(0xFF4E5A65)
    ),
    BRONZE(
        "Bronze", 
        "150-299 Stars", 
        150, 
        299,
        "Tuk-Tuk", 
        "Cruising the vibrant streets! Ready to explore local food markets and ride tuk-tuks.", 
        Color(0xFFCD7F32), 
        Color(0xFF8B4513)
    ),
    SILVER(
        "Silver", 
        "300-449 Stars", 
        300, 
        449,
        "Lotus", 
        "Knowledge begins to bloom serenely like a lotus. Master shopping, numbers, and transit.", 
        Color(0xFFE0F2F1), 
        Color(0xFF5E919c)
    ),
    GOLD(
        "Gold", 
        "450-599 Stars", 
        450, 
        599,
        "King's Crown", 
        "A royal handle on pronunciation and sentence structures. Fluency is shining bright.", 
        Color(0xFFFFD700), 
        Color(0xFFF57F17)
    ),
    EMERALD(
        "Emerald", 
        "600+ Stars", 
        600, 
        9999,
        "Buddha", 
        "Absolute enlightenment. Deep social connections and profound linguistic mastery.", 
        Color(0xFF00C853), 
        Color(0xFF004D40)
    );

    companion object {
        fun fromStars(stars: Int): ThaiRank {
            return when {
                stars < 150 -> IRON
                stars < 300 -> BRONZE
                stars < 450 -> SILVER
                stars < 600 -> GOLD
                else -> EMERALD
            }
        }
    }
}

@Composable
fun RankBadge(
    rank: ThaiRank,
    modifier: Modifier = Modifier,
    showBackgroundFrame: Boolean = true
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = if (showBackgroundFrame) {
            modifier
                .clip(CircleShape)
                .background(rank.secColor.copy(alpha = 0.12f))
                .border(1.5.dp, rank.primaryColor.copy(alpha = 0.35f), CircleShape)
                .padding(4.dp)
        } else {
            modifier
        }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val primary = rank.primaryColor
            val secondary = rank.secColor

            when (rank) {
                ThaiRank.IRON -> drawBackpack(primary, secondary)
                ThaiRank.BRONZE -> drawTukTuk(primary, secondary)
                ThaiRank.SILVER -> drawLotus(primary, secondary)
                ThaiRank.GOLD -> drawKingsCrown(primary, secondary)
                ThaiRank.EMERALD -> drawBuddha(primary, secondary)
            }
        }
    }
}

private fun DrawScope.drawBackpack(primary: Color, secondary: Color) {
    val cx = size.width / 2
    val cy = size.height / 2
    val w = size.width
    val h = size.height

    val bodyW = w * 0.6f
    val bodyH = h * 0.7f
    val rx = cx - bodyW / 2
    val ry = cy - bodyH / 2 + h * 0.05f

    // Top loop
    drawArc(
        color = secondary,
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(cx - bodyW * 0.25f, ry - bodyH * 0.12f),
        size = Size(bodyW * 0.5f, bodyH * 0.25f),
        style = Stroke(width = w * 0.08f, cap = StrokeCap.Round)
    )

    // Main pack
    drawRoundRect(
        color = primary,
        topLeft = Offset(rx, ry),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(w * 0.12f, w * 0.12f)
    )

    // Outer border
    drawRoundRect(
        color = secondary,
        topLeft = Offset(rx, ry),
        size = Size(bodyW, bodyH),
        cornerRadius = CornerRadius(w * 0.12f, w * 0.12f),
        style = Stroke(width = w * 0.06f)
    )

    // Front pocket
    val pocketW = bodyW * 0.75f
    val pocketH = bodyH * 0.4f
    val pocketX = cx - pocketW / 2
    val pocketY = ry + bodyH * 0.45f
    drawRoundRect(
        color = secondary.copy(alpha = 0.3f),
        topLeft = Offset(pocketX, pocketY),
        size = Size(pocketW, pocketH),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f)
    )
    drawRoundRect(
        color = secondary,
        topLeft = Offset(pocketX, pocketY),
        size = Size(pocketW, pocketH),
        cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
        style = Stroke(width = w * 0.05f)
    )

    // Pocket zip line
    drawLine(
        color = secondary,
        start = Offset(pocketX + pocketW * 0.15f, pocketY + pocketH * 0.35f),
        end = Offset(pocketX + pocketW * 0.85f, pocketY + pocketH * 0.35f),
        strokeWidth = w * 0.04f,
        cap = StrokeCap.Round
    )
}

private fun DrawScope.drawTukTuk(primary: Color, secondary: Color) {
    val cx = size.width / 2
    val cy = size.height / 2
    val w = size.width
    val h = size.height

    // Canopy/Roof
    val canopyW = w * 0.72f
    val canopyH = h * 0.23f
    val canopyY = cy - h * 0.38f
    val canopyPath = Path().apply {
        moveTo(cx - canopyW * 0.4f, canopyY + canopyH)
        quadraticTo(cx - canopyW * 0.45f, canopyY, cx, canopyY)
        quadraticTo(cx + canopyW * 0.45f, canopyY, cx + canopyW * 0.4f, canopyY + canopyH)
        close()
    }
    drawPath(path = canopyPath, color = primary)
    drawPath(path = canopyPath, color = secondary, style = Stroke(width = w * 0.05f))

    // Windshield glass
    val glassW = w * 0.58f
    val glassH = h * 0.22f
    val glassY = canopyY + canopyH * 0.82f
    drawRoundRect(
        color = Color.White.copy(alpha = 0.8f),
        topLeft = Offset(cx - glassW / 2, glassY),
        size = Size(glassW, glassH),
        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
    )
    drawRoundRect(
        color = secondary,
        topLeft = Offset(cx - glassW / 2, glassY),
        size = Size(glassW, glassH),
        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
        style = Stroke(width = w * 0.04f)
    )

    // Main lower frame banner
    val apronW = w * 0.62f
    val apronH = h * 0.28f
    val apronY = glassY + glassH - h * 0.02f
    val apronPath = Path().apply {
        moveTo(cx - apronW / 2, apronY)
        lineTo(cx + apronW / 2, apronY)
        lineTo(cx + apronW * 0.4f, apronY + apronH)
        lineTo(cx - apronW * 0.4f, apronY + apronH)
        close()
    }
    drawPath(path = apronPath, color = primary)
    drawPath(path = apronPath, color = secondary, style = Stroke(width = w * 0.05f))

    // Golden Headlights
    val lightRadius = w * 0.075f
    val lightY = apronY + apronH * 0.45f
    drawCircle(
        color = Color(0xFFFFEB3B),
        radius = lightRadius,
        center = Offset(cx - apronW * 0.25f, lightY)
    )
    drawCircle(
        color = secondary,
        radius = lightRadius,
        center = Offset(cx - apronW * 0.25f, lightY),
        style = Stroke(width = w * 0.035f)
    )
    drawCircle(
        color = Color(0xFFFFEB3B),
        radius = lightRadius,
        center = Offset(cx + apronW * 0.25f, lightY)
    )
    drawCircle(
        color = secondary,
        radius = lightRadius,
        center = Offset(cx + apronW * 0.25f, lightY),
        style = Stroke(width = w * 0.035f)
    )

    // Front tire wheel representation
    val wheelW = w * 0.16f
    val wheelH = h * 0.16f
    val wheelX = cx - wheelW / 2
    val wheelY = apronY + apronH - h * 0.02f
    drawRoundRect(
        color = Color(0xFF263238),
        topLeft = Offset(wheelX, wheelY),
        size = Size(wheelW, wheelH),
        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f)
    )
    drawRoundRect(
        color = secondary,
        topLeft = Offset(wheelX, wheelY),
        size = Size(wheelW, wheelH),
        cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
        style = Stroke(width = w * 0.035f)
    )
}

private fun DrawScope.drawLotus(primary: Color, secondary: Color) {
    val cx = size.width / 2
    val cy = size.height / 2
    val w = size.width
    val h = size.height

    val petalBrush = Brush.verticalGradient(
        colors = listOf(Color(0xFFFFF1F1), Color(0xFFF48FB1), secondary)
    )

    val baseY = cy + h * 0.15f

    // Bottom-Left support petal
    val bottomLeft = Path().apply {
        moveTo(cx, baseY)
        cubicTo(cx - w * 0.45f, baseY, cx - w * 0.32f, cy - h * 0.1f, cx - w * 0.4f, cy - h * 0.05f)
        cubicTo(cx - w * 0.25f, cy + h * 0.10f, cx, cy + h * 0.15f, cx, baseY)
    }
    drawPath(path = bottomLeft, brush = petalBrush)
    drawPath(path = bottomLeft, color = secondary, style = Stroke(width = w * 0.035f))

    // Bottom-Right support petal
    val bottomRight = Path().apply {
        moveTo(cx, baseY)
        cubicTo(cx + w * 0.45f, baseY, cx + w * 0.32f, cy - h * 0.1f, cx + w * 0.4f, cy - h * 0.05f)
        cubicTo(cx + w * 0.25f, cy + h * 0.10f, cx, cy + h * 0.15f, cx, baseY)
    }
    drawPath(path = bottomRight, brush = petalBrush)
    drawPath(path = bottomRight, color = secondary, style = Stroke(width = w * 0.035f))

    // Outer mid wings
    val midLeft = Path().apply {
        moveTo(cx, baseY)
        quadraticTo(cx - w * 0.36f, cy - h * 0.16f, cx - w * 0.23f, cy - h * 0.26f)
        quadraticTo(cx - w * 0.08f, cy, cx, baseY)
    }
    drawPath(path = midLeft, brush = petalBrush)
    drawPath(path = midLeft, color = secondary, style = Stroke(width = w * 0.035f))

    val midRight = Path().apply {
        moveTo(cx, baseY)
        quadraticTo(cx + w * 0.36f, cy - h * 0.16f, cx + w * 0.23f, cy - h * 0.26f)
        quadraticTo(cx + w * 0.08f, cy, cx, baseY)
    }
    drawPath(path = midRight, brush = petalBrush)
    drawPath(path = midRight, color = secondary, style = Stroke(width = w * 0.035f))

    // Center lotus petal
    val center = Path().apply {
        moveTo(cx, baseY)
        cubicTo(cx - w * 0.18f, cy, cx - w * 0.15f, cy - h * 0.42f, cx, cy - h * 0.44f)
        cubicTo(cx + w * 0.15f, cy - h * 0.42f, cx + w * 0.18f, cy, cx, baseY)
        close()
    }
    drawPath(path = center, brush = petalBrush)
    drawPath(path = center, color = secondary, style = Stroke(width = w * 0.045f))

    // Inner glowing stamens
    val goldY = cy + h * 0.02f
    drawCircle(color = Color(0xFFFFD54F), radius = w * 0.035f, center = Offset(cx, goldY))
    drawCircle(color = Color(0xFFFFD54F), radius = w * 0.025f, center = Offset(cx - w * 0.09f, goldY + h * 0.05f))
    drawCircle(color = Color(0xFFFFD54F), radius = w * 0.025f, center = Offset(cx + w * 0.09f, goldY + h * 0.05f))
}

private fun DrawScope.drawKingsCrown(primary: Color, secondary: Color) {
    val cx = size.width / 2
    val cy = size.height / 2
    val w = size.width
    val h = size.height

    // Crown base trim
    val baseW = w * 0.72f
    val baseH = h * 0.12f
    val baseY = cy + h * 0.16f
    val baseX = cx - baseW / 2

    val crownBrush = Brush.verticalGradient(
        colors = listOf(primary, Color(0xFFFF6D00))
    )

    // Complete crown peaks math coordinates
    val crownPath = Path().apply {
        moveTo(baseX, baseY)
        // Peak 1: Outer short left
        lineTo(baseX - w * 0.01f, cy - h * 0.06f)
        // Valley 1
        lineTo(baseX + baseW * 0.18f, cy + h * 0.05f)
        // Peak 2: Mid tall left
        lineTo(baseX + baseW * 0.28f, cy - h * 0.22f)
        // Valley 2
        lineTo(baseX + baseW * 0.42f, cy)
        // Peak 3: Middle giant royal peak
        lineTo(cx, cy - h * 0.38f)
        // Valley 3
        lineTo(baseX + baseW * 0.58f, cy)
        // Peak 4: Mid tall right
        lineTo(baseX + baseW * 0.72f, cy - h * 0.22f)
        // Valley 4
        lineTo(baseX + baseW * 0.82f, cy + h * 0.05f)
        // Peak 5: Outer short right
        lineTo(baseX + baseW + w * 0.01f, cy - h * 0.06f)
        lineTo(baseX + baseW, baseY)
        close()
    }

    drawPath(path = crownPath, brush = crownBrush)
    drawPath(path = crownPath, color = secondary, style = Stroke(width = w * 0.052f, join = StrokeJoin.Round))

    // Crown base headband
    drawRoundRect(
        color = secondary,
        topLeft = Offset(baseX, baseY),
        size = Size(baseW, baseH),
        cornerRadius = CornerRadius(w * 0.03f, w * 0.03f)
    )

    // Glowing base royal rubies
    val gemR = w * 0.038f
    val gemY = baseY + baseH / 2
    drawCircle(color = Color(0xFFD81B60), radius = gemR, center = Offset(baseX + baseW * 0.23f, gemY))
    drawCircle(color = Color(0xFF00E676), radius = gemR, center = Offset(cx, gemY))
    drawCircle(color = Color(0xFFD81B60), radius = gemR, center = Offset(baseX + baseW * 0.77f, gemY))

    // Pearled globes above peak tips
    val pearls = listOf(
        Offset(baseX - w * 0.01f, cy - h * 0.06f) to w * 0.045f,
        Offset(baseX + baseW * 0.28f, cy - h * 0.22f) to w * 0.052f,
        Offset(cx, cy - h * 0.38f) to w * 0.065f,
        Offset(baseX + baseW * 0.72f, cy - h * 0.22f) to w * 0.052f,
        Offset(baseX + baseW + w * 0.01f, cy - h * 0.06f) to w * 0.045f
    )

    pearls.forEach { (coord, sizeRadius) ->
        drawCircle(color = Color.White, radius = sizeRadius, center = coord)
        drawCircle(color = secondary, radius = sizeRadius, center = coord, style = Stroke(width = w * 0.018f))
    }
}

private fun DrawScope.drawBuddha(primary: Color, secondary: Color) {
    val cx = size.width / 2
    val cy = size.height / 2
    val w = size.width
    val h = size.height

    // Radial jade halo in back
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(primary.copy(alpha = 0.45f), Color.Transparent),
            center = Offset(cx, cy - h * 0.08f),
            radius = w * 0.46f
        ),
        radius = w * 0.46f,
        center = Offset(cx, cy - h * 0.08f)
    )

    // Solid inner glowing aura circle
    drawArc(
        color = primary.copy(alpha = 0.8f),
        startAngle = 0f,
        sweepAngle = 360f,
        useCenter = false,
        topLeft = Offset(cx - w * 0.28f, cy - h * 0.36f),
        size = Size(w * 0.56f, h * 0.56f),
        style = Stroke(width = w * 0.024f)
    )

    // Buddha silhouette path (ushnisha, head, long earlobes, triangular torso, posture base)
    val baseW = w * 0.65f
    val baseH = h * 0.25f
    val baseY = cy + h * 0.10f

    val buddhaPath = Path().apply {
        // Skull top knot (ushnisha base)
        moveTo(cx, cy - h * 0.31f)
        // Left skull/ear
        cubicTo(cx - w * 0.08f, cy - h * 0.31f, cx - w * 0.11f, cy - h * 0.18f, cx - w * 0.06f, cy - h * 0.18f)
        // Shoulder sweep left
        quadraticTo(cx - w * 0.18f, cy - h * 0.12f, cx - w * 0.22f, cy - h * 0.02f)
        // Left arm down to elbow and knee point
        quadraticTo(cx - w * 0.28f, cy + h * 0.10f, cx - baseW / 2, baseY + baseH * 0.6f)
        // Left foot/knee base fold segment
        quadraticTo(cx - baseW / 2 - w * 0.04f, baseY + baseH, cx, baseY + baseH)
        // Right foot/knee base fold segment
        quadraticTo(cx + baseW / 2 + w * 0.04f, baseY + baseH, cx + baseW / 2, baseY + baseH * 0.6f)
        // Right arm up from elbow
        quadraticTo(cx + w * 0.28f, cy + h * 0.10f, cx + w * 0.22f, cy - h * 0.02f)
        // Shoulder sweep right
        quadraticTo(cx + w * 0.18f, cy - h * 0.12f, cx + w * 0.06f, cy - h * 0.18f)
        // Right skull/ear
        cubicTo(cx + w * 0.11f, cy - h * 0.18f, cx + w * 0.08f, cy - h * 0.31f, cx, cy - h * 0.31f)
        close()
    }

    drawPath(path = buddhaPath, color = primary)
    drawPath(path = buddhaPath, color = secondary, style = Stroke(width = w * 0.045f, join = StrokeJoin.Round))

    // Flame Topknot (ushnisha absolute peak)
    val topKnot = Path().apply {
        moveTo(cx - w * 0.04f, cy - h * 0.30f)
        quadraticTo(cx, cy - h * 0.42f, cx, cy - h * 0.43f)
        quadraticTo(cx, cy - h * 0.42f, cx + w * 0.04f, cy - h * 0.30f)
        close()
    }
    drawPath(path = topKnot, color = primary)
    drawPath(path = topKnot, color = secondary, style = Stroke(width = w * 0.035f, join = StrokeJoin.Round))

    // Folded dhyana-mudra meditation hands curve
    val handsPath = Path().apply {
        moveTo(cx - w * 0.11f, cy + h * 0.12f)
        quadraticTo(cx, cy + h * 0.21f, cx + w * 0.11f, cy + h * 0.12f)
    }
    drawPath(path = handsPath, color = secondary, style = Stroke(width = w * 0.04f, cap = StrokeCap.Round))
}
