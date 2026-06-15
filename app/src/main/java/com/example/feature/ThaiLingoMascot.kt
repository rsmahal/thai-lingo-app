package com.example.feature

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R

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
    // Generate context-appropriate speech messages
    val neutralPhrases = remember {
        listOf(
            "Sawatdee ka! Ready to expand your Thai vocabulary? 🐘",
            "Nong Chang is here to guide you. Speak, match, and learn! 🇹🇭",
            "Consistency is the key! Let's conquer a lesson! 🌟",
            "Fun fact: 'Chang' (ช้าง) means Elephant in Thai! 🐘❤️"
        )
    }

    val happyPhrases = remember {
        listOf(
            "Chai-Yo! 🎉 Excellent work!",
            "Outstanding! You are doing amazing! ⭐",
            "Incredible streak! Your progress is shining bright! 🔥",
            "Nong Chang is so proud of you! Keep going! 🥳"
        )
    }

    val encouragingPhrases = remember {
        listOf(
            "Su su! (Keep fighting!) You've got this! 🌟",
            "Nong Chang believes in you. Take your time! ❤️",
            "Every correct match boosts your language superpower! ⚡",
            "Focus like a master! We can finish this together!"
        )
    }

    val sadPhrases = remember {
        listOf(
            "Mai pen rai! (Never mind!) Mistakes help us learn. ❤️",
            "Almost there! Let's carefully review and try again! 🎯",
            "Every mistake is a step closer to fluency. Try again!",
            "Nong Chang is cheering for your comeback! You can do it!"
        )
    }

    // Select suitable message based on expression and trigger reload when expression changes
    val resolvedMessage = remember(expression, customMessage) {
        customMessage ?: when (expression) {
            MascotExpression.NEUTRAL -> neutralPhrases.random()
            MascotExpression.HAPPY -> happyPhrases.random()
            MascotExpression.ENCOURAGING -> encouragingPhrases.random()
            MascotExpression.SAD -> sadPhrases.random()
        }
    }

    // Gentle floating anim
    val infiniteTransition = rememberInfiniteTransition(label = "mascot_float")
    val bobOffset by infiniteTransition.animateFloat(
        initialValue = -4f,
        targetValue = 4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = EaseInOutQuad),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob_offset"
    )

    // Gentle scale/heartbeat anim for excitement
    val scaleFactor by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale_factor"
    )

    // Dynamic rotation spec
    val rotationAngle = when (expression) {
        MascotExpression.HAPPY -> 3f * (if (bobOffset > 0) 1f else -1f)
        MascotExpression.ENCOURAGING -> 1.5f * (if (bobOffset > 0) 1f else -1f)
        MascotExpression.SAD -> -1f
        MascotExpression.NEUTRAL -> 0f
    }

    val drawableRes = when (expression) {
        MascotExpression.NEUTRAL -> R.drawable.ic_mascot_neutral
        MascotExpression.HAPPY -> R.drawable.ic_mascot_happy
        MascotExpression.ENCOURAGING -> R.drawable.ic_mascot_encouraging
         MascotExpression.SAD -> R.drawable.ic_mascot_sad
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .testTag("thai_lingo_mascot_layout"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        // Mascot character itself
        Box(
            modifier = Modifier
                .size(size)
                .offset(y = bobOffset.dp)
                .rotate(rotationAngle)
                .testTag("mascot_avatar_container")
        ) {
            Image(
                painter = painterResource(id = drawableRes),
                contentDescription = "ThaiLingo Mascot Companion - Nong Chang",
                modifier = Modifier
                    .fillMaxSize()
                    .aspectRatio(1f)
            )
        }

        // Animated speech bubble
        if (showBubble) {
            Spacer(modifier = Modifier.width(6.dp))
            BubbleMessage(
                message = resolvedMessage,
                expression = expression,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun BubbleMessage(
    message: String,
    expression: MascotExpression,
    modifier: Modifier = Modifier
) {
    val bubbleColor = when (expression) {
        MascotExpression.HAPPY -> Color(0xFFF0FDF4) // Soft light green
        MascotExpression.ENCOURAGING -> Color(0xFFEFF6FF) // Soft light blue
        MascotExpression.SAD -> Color(0xFFFFECEB) // Soft warm peach
        MascotExpression.NEUTRAL -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    val bubbleBorderColor = when (expression) {
        MascotExpression.HAPPY -> Color(0xFFBBF7D0)
        MascotExpression.ENCOURAGING -> Color(0xFFBFDBFE)
        MascotExpression.SAD -> Color(0xFFFECACA)
        MascotExpression.NEUTRAL -> MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    }

    val textColor = when (expression) {
        MascotExpression.HAPPY -> Color(0xFF15803D)
        MascotExpression.ENCOURAGING -> Color(0xFF1D4ED8)
        MascotExpression.SAD -> Color(0xFFB91C1C)
        MascotExpression.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = modifier
            .padding(horizontal = 4.dp, vertical = 2.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Speech triangle tail pointing to mascot
        Canvas(
            modifier = Modifier
                .size(width = 8.dp, height = 12.dp)
                .background(Color.Transparent)
        ) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(this@Canvas.size.width, 0f)
                lineTo(0f, this@Canvas.size.height / 2f)
                lineTo(this@Canvas.size.width, this@Canvas.size.height)
                close()
            }
            drawPath(path = path, color = bubbleColor)
        }

        // Main bubble message text container
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bubbleColor,
            border = androidx.compose.foundation.BorderStroke(1.5.dp, bubbleBorderColor),
            modifier = Modifier
                .shadow(2.dp, RoundedCornerShape(16.dp))
                .fillMaxWidth()
                .testTag("mascot_speech_bubble")
        ) {
            Text(
                text = message,
                color = textColor,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
                lineHeight = 18.sp,
                modifier = Modifier
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }
}
