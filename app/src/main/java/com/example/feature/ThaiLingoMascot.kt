package com.example.feature

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class MascotExpression {
    NEUTRAL,
    HAPPY,
    ENCOURAGING,
    SAD
}

@Composable
fun ThaiLingoMascot(
    expression: MascotExpression,
    modifier: Modifier = Modifier,
    customMessage: String? = null,
    showBubble: Boolean = true,
    size: Dp = 90.dp
) {
    // Generate context-appropriate helpful messages
    val neutralPhrases = remember {
        listOf(
            "Ready to expand your Thai vocabulary? Let's check matching pairs! 🇹🇭",
            "Consistency is the key! Let's conquer this lesson! 🌟",
            "Did you know? Learning daily significantly improves memory retention! 🧠"
        )
    }

    val happyPhrases = remember {
        listOf(
            "Excellent work! 🎉 You are doing perfectly!",
            "Outstanding! Your streak is looking amazing! ⭐",
            "Keep up the awesome momentum! 🥳"
        )
    }

    val encouragingPhrases = remember {
        listOf(
            "Su su! (Keep fighting!) You've got this! 🌟",
            "Take your time and listen to the pronunciation carefully! ❤️",
            "Every completed task boosts your language superpower! ⚡"
        )
    }

    val sadPhrases = remember {
        listOf(
            "Mai pen rai! (Never mind!) Mistakes help us learn. ❤️",
            "Almost there! Let's carefully review and try again! 🎯",
            "Every correct correction brings you closer to fluency!"
        )
    }

    val resolvedMessage = remember(expression, customMessage) {
        customMessage ?: when (expression) {
            MascotExpression.NEUTRAL -> neutralPhrases.random()
            MascotExpression.HAPPY -> happyPhrases.random()
            MascotExpression.ENCOURAGING -> encouragingPhrases.random()
            MascotExpression.SAD -> sadPhrases.random()
        }
    }

    val containerColor = when (expression) {
        MascotExpression.HAPPY -> Color(0xFFF0FDF4) // Soft light green
        MascotExpression.ENCOURAGING -> Color(0xFFEFF6FF) // Soft light blue
        MascotExpression.SAD -> Color(0xFFFFECEB) // Soft warm peach
        MascotExpression.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val borderColor = when (expression) {
        MascotExpression.HAPPY -> Color(0xFFBBF7D0)
        MascotExpression.ENCOURAGING -> Color(0xFFBFDBFE)
        MascotExpression.SAD -> Color(0xFFFECACA)
        MascotExpression.NEUTRAL -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    val tintColor = when (expression) {
        MascotExpression.HAPPY -> Color(0xFF15803D)
        MascotExpression.ENCOURAGING -> Color(0xFF1D4ED8)
        MascotExpression.SAD -> Color(0xFFB91C1C)
        MascotExpression.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val iconVector = when (expression) {
        MascotExpression.HAPPY -> Icons.Default.CheckCircle
        MascotExpression.ENCOURAGING -> Icons.Default.Lightbulb
        MascotExpression.SAD -> Icons.Default.Cancel
        MascotExpression.NEUTRAL -> Icons.Default.Info
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = containerColor,
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .testTag("thai_lingo_mascot_layout")
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .animateContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = iconVector,
                contentDescription = null,
                tint = tintColor,
                modifier = Modifier
                    .size(28.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = resolvedMessage,
                color = tintColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = java.text.Normalizer.normalize(resolvedMessage, java.text.Normalizer.Form.NFC).let { _ -> androidx.compose.ui.text.style.TextAlign.Start },
                lineHeight = 18.sp,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

