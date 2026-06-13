package com.example.feature

import android.content.Context
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.ReviewWord
import com.example.ui.theme.*

@Composable
fun ReviewScreen(
    context: Context = LocalContext.current,
    viewModel: ReviewViewModel = viewModel(factory = ReviewViewModel.Factory(context))
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {
            ReviewUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DuoGreen)
                }
            }
            is ReviewUiState.Active -> {
                if (state.currentStep == -1) {
                    // Spaced Repetition Dashboard
                    ReviewDashboardListing(
                        state = state,
                        onStartQuiz = { viewModel.startQuiz() },
                        onSimulatePass = { viewModel.simulateOneDayPass() },
                        onResetSimulate = { viewModel.resetTimeOffset() }
                    )
                } else if (state.currentStep >= 0 && state.currentStep < state.reviewQueue.size) {
                    // Active SRS Quiz Step
                    ReviewQuizOverlay(
                        step = state.currentStep,
                        totalSteps = state.reviewQueue.size,
                        word = state.reviewQueue[state.currentStep],
                        options = state.options,
                        selectedOption = state.selectedOption,
                        isChecking = state.isChecking,
                        isCorrect = state.isCorrect,
                        onSelectOption = { viewModel.selectOption(it) },
                        onCheck = { viewModel.checkAnswer() },
                        onContinue = { viewModel.continueToNext() },
                        onQuit = { viewModel.quitOrFinishQuiz() }
                    )
                } else {
                    // Completion Screen
                    ReviewQuizCompletion(
                        totalWordsInQuiz = state.reviewQueue.size,
                        correctCount = state.correctCount,
                        xpEarned = state.xpEarned,
                        onFinish = { viewModel.quitOrFinishQuiz() }
                    )
                }
            }
        }
    }
}

@Composable
fun ReviewDashboardListing(
    state: ReviewUiState.Active,
    onStartQuiz: () -> Unit,
    onSimulatePass: () -> Unit,
    onResetSimulate: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .statusBarsPadding()
    ) {
        // 1. HEADER & EXPLANATION
        Text(
            text = "Spaced Repetition Review",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Text(
            text = "Every word you encounter in lessons is automatically scheduled here. Standard reviews reappear with multiplying delays as your memory streak grows.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            lineHeight = 18.sp,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // 2. SRS METRICS DASHBOARD ROW
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Due count card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp)
                    .testTag("srs_due_badge"),
                colors = CardDefaults.cardColors(containerColor = StreakOrange.copy(alpha = 0.08f)),
                border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                    width = 1.5.dp,
                    brush = androidx.compose.ui.graphics.SolidColor(StreakOrange.copy(alpha = 0.25f))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = StreakOrange, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("DUE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = StreakOrange)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.reviewQueue.size.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("words to review", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            // Mastered count card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp)
                    .testTag("srs_mastered_badge"),
                colors = CardDefaults.cardColors(containerColor = DuoGreen.copy(alpha = 0.08f)),
                border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                    width = 1.5.dp,
                    brush = androidx.compose.ui.graphics.SolidColor(DuoGreen.copy(alpha = 0.25f))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = LevelGold, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("MASTERED", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = DuoGreenDark)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.masteredCount.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("streak of 4+ or 14d", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }

            // Total count card
            Card(
                modifier = Modifier
                    .weight(1f)
                    .height(98.dp)
                    .testTag("srs_total_badge"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                    width = 1.dp,
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Book, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("TOTAL", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = state.allReviewWords.size.toString(),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text("exposed words", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                }
            }
        }

        // 3. SRS TIME SIMULATION BAR
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("srs_simulation_bar"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(12.dp),
            border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = "Simulate",
                            tint = DuoGreenDark,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Time Simulation Control",
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Text(
                        text = if (state.timeOffsetDays == 0) "Viewing current exact database due hours." else "Simulated timeline: +${state.timeOffsetDays} days pass",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.timeOffsetDays > 0) {
                        TextButton(
                            onClick = onResetSimulate,
                            modifier = Modifier.height(36.dp).testTag("reset_simulation_btn")
                        ) {
                            Text("Reset", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = HeartRed)
                        }
                    }
                    Button(
                        onClick = onSimulatePass,
                        modifier = Modifier
                            .height(36.dp)
                            .testTag("simulate_1day_btn"),
                        colors = ButtonDefaults.buttonColors(containerColor = DuoGreen),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(Icons.Default.SkipNext, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("+1 Day", fontSize = 12.sp, fontWeight = FontWeight.Black)
                    }
                }
            }
        }

        // 4. BIG ACTION REVIEW PLAY BUTTON
        Button(
            onClick = onStartQuiz,
            enabled = state.reviewQueue.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("start_review_quiz_btn"),
            colors = ButtonDefaults.buttonColors(
                containerColor = StreakOrange,
                disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
            ),
            shape = RoundedCornerShape(12.dp)
        ) {
            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = if (state.reviewQueue.isEmpty()) "All Caught Up (0 Due!)" else "Start Spaced Review (${state.reviewQueue.size} due)",
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. WORD DIRECTORY (GROUPED)
        if (state.allReviewWords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("empty_review_view"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(DuoGreen.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.School,
                            contentDescription = "Ready",
                            tint = DuoGreen,
                            modifier = Modifier.size(44.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Exposed Vocabulary Yet",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Once you unlock levels and answer interactive exercises in the Learn tab, words are logged here automatically to manage your memory schedule.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        lineHeight = 18.sp
                    )
                }
            }
        } else {
            // Header for dictionary list
            Text(
                text = "SRS MEMORY TRACKER (${state.allReviewWords.size})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("review_words_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.allReviewWords) { word ->
                    val nowMs = System.currentTimeMillis() + state.timeOffsetDays * 24L * 3600 * 1000
                    val isWordDue = word.nextDueAt <= nowMs
                    val diffDays = ((word.nextDueAt - nowMs) / (24L * 3600 * 1000)).toInt()

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("review_word_item_${word.thai}"),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isWordDue) StreakOrange.copy(alpha = 0.015f) else MaterialTheme.colorScheme.surface
                        ),
                        border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                            width = if (isWordDue) 1.5.dp else 1.dp,
                            brush = androidx.compose.ui.graphics.SolidColor(
                                if (isWordDue) StreakOrange.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            )
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = word.thai,
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (word.romanization.isNotEmpty()) {
                                    Text(
                                        text = "[ ${word.romanization} ]",
                                        fontSize = 12.sp,
                                        color = DuoGreenDark,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = word.english,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                                
                                Spacer(modifier = Modifier.height(4.dp))
                                
                                // Info Row of statistics
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    // Streak Badge
                                    Box(
                                        modifier = Modifier
                                            .background(StreakOrange.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "🔥 Streak: ${word.streak}",
                                            fontSize = 9.sp,
                                            color = StreakOrange,
                                            fontWeight = FontWeight.Black
                                        )
                                    }

                                    // Next interval
                                    Box(
                                        modifier = Modifier
                                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.04f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "Interval: ${word.intervalDays}d",
                                            fontSize = 9.sp,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                            fontWeight = FontWeight.Bold
                                        )
                                    }

                                    // Status timeline label
                                    val statusText = when {
                                        isWordDue -> "DUE"
                                        diffDays <= 0 -> "Due today"
                                        else -> "Due: ${diffDays}d"
                                    }
                                    val statusColor = if (isWordDue) StreakOrange else DuoGreenDark

                                    Box(
                                        modifier = Modifier
                                            .background(statusColor.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = statusText.uppercase(),
                                            fontSize = 9.sp,
                                            color = statusColor,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            // Category Tag or Mastered Badge
                            Column(horizontalAlignment = Alignment.End) {
                                if (word.isMastered) {
                                    Box(
                                        modifier = Modifier
                                            .background(LevelGold.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                            .border(1.dp, LevelGold.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = LevelGold, modifier = Modifier.size(10.dp))
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = "MASTERED",
                                                fontSize = 9.sp,
                                                color = LevelGold,
                                                fontWeight = FontWeight.Black
                                            )
                                        }
                                    }
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .background(DuoGreen.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = word.category,
                                            fontSize = 9.sp,
                                            color = DuoGreenDark,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}



@Composable
fun ReviewQuizOverlay(
    step: Int,
    totalSteps: Int,
    word: ReviewWord,
    options: List<String>,
    selectedOption: String,
    isChecking: Boolean,
    isCorrect: Boolean?,
    onSelectOption: (String) -> Unit,
    onCheck: () -> Unit,
    onContinue: () -> Unit,
    onQuit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // TOP RETOUR / PROGRESS BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onQuit,
                modifier = Modifier.testTag("review_quiz_quit_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Quiz",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            
            // Linear Progress Indicators
            val progressFraction = (step.toFloat() / totalSteps.coerceAtLeast(1))
            LinearProgressIndicator(
                progress = progressFraction,
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(CircleShape),
                color = StreakOrange,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "${step + 1} / $totalSteps",
                fontWeight = FontWeight.Black,
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                modifier = Modifier.padding(end = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // CENTRAL CARD
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "WHAT DOES THIS MEAN?",
                fontSize = 13.sp,
                fontWeight = FontWeight.Black,
                color = StreakOrange,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Thai presenting card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = word.thai,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    if (word.romanization.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = word.romanization,
                            fontSize = 16.sp,
                            color = DuoGreenDark,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // MULTIPLE CHOICE LIST
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                options.forEach { option ->
                    val isSelected = selectedOption == option
                    val isOptionCorrectAnswer = option == word.english

                    val bg = when {
                        isChecking && isOptionCorrectAnswer -> CorrectFill
                        isChecking && isSelected && isCorrect == false -> HeartRedLight
                        isSelected -> DuoGreen.copy(alpha = 0.08f)
                        else -> MaterialTheme.colorScheme.surface
                    }

                    val borderCol = when {
                        isChecking && isOptionCorrectAnswer -> CorrectStroke
                        isChecking && isSelected && isCorrect == false -> HeartRed
                        isSelected -> DuoGreen
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    }

                    val textCol = when {
                        isChecking && isOptionCorrectAnswer -> CorrectText
                        isChecking && isSelected && isCorrect == false -> HeartRed
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Card(
                        onClick = { if (!isChecking) onSelectOption(option) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(58.dp)
                            .testTag("review_option_$option"),
                        colors = CardDefaults.cardColors(containerColor = bg),
                        border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                            width = if (isSelected || (isChecking && isOptionCorrectAnswer)) 3.dp else 1.5.dp,
                            brush = androidx.compose.ui.graphics.SolidColor(borderCol)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = option,
                                fontWeight = FontWeight.Bold,
                                color = textCol,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        // BOTTOM DRAWER PROGRESS DRAWER
        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            color = when {
                isChecking && isCorrect == true -> CorrectFill
                isChecking && isCorrect == false -> HeartRedLight
                else -> MaterialTheme.colorScheme.surface
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                if (isChecking) {
                    val messageTitle = if (isCorrect == true) "Correct Review!" else "Need revision"
                    val messageDesc = if (isCorrect == true) {
                        "Excellent! Spacing interval pushed to next memory station."
                    } else {
                        "Correct: \"${word.english}\". Re-scheduled for immediate practice."
                    }
                    val icon = if (isCorrect == true) Icons.Default.CheckCircle else Icons.Default.Error
                    val iconColor = if (isCorrect == true) CorrectText else HeartRed

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = messageTitle,
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = iconColor
                            )
                            Text(
                                text = messageDesc,
                                fontSize = 13.sp,
                                color = iconColor.copy(alpha = 0.85f),
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("review_quiz_continue_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isCorrect == true) CorrectStroke else HeartRed
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CONTINUE", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                } else {
                    Button(
                        onClick = onCheck,
                        enabled = selectedOption.isNotEmpty(),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("review_quiz_check_btn"),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = DuoGreen,
                            disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("CHECK ANSWER", fontWeight = FontWeight.Black, fontSize = 16.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun ReviewQuizCompletion(
    totalWordsInQuiz: Int,
    correctCount: Int,
    xpEarned: Int,
    onFinish: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .background(StreakOrange.copy(alpha = 0.12f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Star,
                    contentDescription = "Success Star",
                    tint = StreakOrange,
                    modifier = Modifier.size(56.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Workout Complete!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Spaced Repetition items updated successfully. Constant workouts build strong memory muscles!",
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Rewards indicators in Card Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = StreakOrange.copy(alpha = 0.05f)),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(StreakOrange.copy(alpha = 0.2f))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("REVIEWS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = StreakOrange)
                        Text(
                            text = "$correctCount / $totalWordsInQuiz",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text("Words Corrected", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = LevelGold.copy(alpha = 0.05f)),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        width = 1.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(LevelGold.copy(alpha = 0.2f))
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("TOTAL XP", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LevelGold)
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "XP Icon",
                                tint = LevelGold,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text(
                                text = "+$xpEarned XP",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text("SRS Completed", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("review_comp_back_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = DuoGreen),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("DONE", fontWeight = FontWeight.Black, fontSize = 16.sp)
            }
        }
    }
}
