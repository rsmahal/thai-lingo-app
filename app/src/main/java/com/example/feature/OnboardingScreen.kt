package com.example.feature

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DuoGreen
import com.example.ui.theme.DuoGreenDark
import com.example.ui.theme.StreakOrange

@Composable
fun OnboardingScreen(
    onFinished: (name: String, dailyGoal: Int) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var selectedGoal by remember { mutableIntStateOf(20) } // Standard: 20 XP
    var currentStep by remember { mutableIntStateOf(0) } // 0: Welcome, 1: Name, 2: Goal

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // STEP PROGRESS INDEX INDICATOR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(3) { idx ->
                    val color = if (idx <= currentStep) DuoGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .padding(horizontal = 4.dp)
                            .background(color, shape = RoundedCornerShape(3.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // STEP SWITCH CONTEXT
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (currentStep) {
                    0 -> WelcomeView()
                    1 -> SetupNameView(name = name, onNameChanged = { name = it })
                    2 -> SetupGoalView(selectedGoal = selectedGoal, onGoalChanged = { selectedGoal = it })
                }
            }

            // NAVIGATION BUTTONS
            Button(
                onClick = {
                    if (currentStep < 2) {
                        currentStep++
                    } else {
                        onFinished(name, selectedGoal)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("onboarding_continue_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = DuoGreen),
                shape = RoundedCornerShape(16.dp),
                enabled = currentStep != 1 || name.isNotBlank()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (currentStep == 2) "Start Learning Thai!" else "Continue",
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Forward step",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
fun WelcomeView() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Beautiful Drawn Custom Mascot - Cute ThaiLingo Owl
        Box(
            modifier = Modifier
                .size(160.dp)
                .padding(12.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                
                // Draw feet
                drawCircle(color = Color(0xFFFF9600), radius = 16f, center = Offset(center.x - 30f, center.y + 60f))
                drawCircle(color = Color(0xFFFF9600), radius = 16f, center = Offset(center.x + 30f, center.y + 60f))
                
                // Draw Body
                drawCircle(color = DuoGreen, radius = 64f, center = center)
                
                // Draw Wing covers
                drawCircle(color = DuoGreenDark, radius = 24f, center = Offset(center.x - 56f, center.y + 10f))
                drawCircle(color = DuoGreenDark, radius = 24f, center = Offset(center.x + 56f, center.y + 10f))

                // Draw Eyes circle
                drawCircle(color = Color.White, radius = 22f, center = Offset(center.x - 22f, center.y - 12f))
                drawCircle(color = Color.White, radius = 22f, center = Offset(center.x + 22f, center.y - 12f))
                
                // Draw Pupils
                drawCircle(color = Color(0xFF131F22), radius = 10f, center = Offset(center.x - 22f, center.y - 12f))
                drawCircle(color = Color(0xFF131F22), radius = 10f, center = Offset(center.x + 22f, center.y - 12f))
                
                val pupilHighlights = 4f
                drawCircle(color = Color.White, radius = pupilHighlights, center = Offset(center.x - 26f, center.y - 16f))
                drawCircle(color = Color.White, radius = pupilHighlights, center = Offset(center.x + 18f, center.y - 16f))

                // Draw Beak
                drawCircle(color = Color(0xFFFF9600), radius = 10f, center = Offset(center.x, center.y + 4f))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome to ThaiLingo!",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DuoGreen,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Learn Thai completely offline in a fun, gamified way! Master vocabulary, pronunciation, listening and match phrases easily.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
}

@Composable
fun SetupNameView(
    name: String,
    onNameChanged: (String) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Translate,
            contentDescription = "Translate",
            modifier = Modifier.size(72.dp),
            tint = DuoGreen
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "What is your name?",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Let's create. We will use this to track your local achievements.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            placeholder = { Text("Enter your name") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("onboarding_name_input"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DuoGreen,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                focusedLabelColor = DuoGreen
            ),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun SetupGoalView(
    selectedGoal: Int,
    onGoalChanged: (Int) -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Choose your daily goal",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Select how much XP you want to earn each day to maintain your learning streak.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        val goals = listOf(
            Triple("Casual", 10, "5 min / day"),
            Triple("Regular", 20, "10 min / day"),
            Triple("Serious", 50, "20 min / day"),
            Triple("Insane", 100, "40 min / day")
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            goals.forEach { (title, xp, duration) ->
                val isSelected = selectedGoal == xp
                val borderCol = if (isSelected) DuoGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                val bgCol = if (isSelected) DuoGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface

                Card(
                    onClick = { onGoalChanged(xp) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .testTag("goal_card_$xp"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = bgCol),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        width = if (isSelected) 3.dp else 1.5.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(borderCol)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) DuoGreenDark else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = duration,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = "$xp XP / day",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSelected) StreakOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
