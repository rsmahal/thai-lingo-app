package com.example.feature

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.domain.Exercise
import com.example.domain.ExerciseType
import com.example.domain.Lesson
import com.example.ui.theme.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun LessonPlayScreen(
    lessonId: Int,
    onBackToHome: () -> Unit,
    onAwardXp: (Int) -> Unit
) {
    val context = LocalContext.current
    var showExitDialog by remember { mutableStateOf(false) }

    val viewModel: LessonViewModel = viewModel(
        factory = LessonViewModel.Factory(lessonId, context),
        key = "LessonPlayVM_$lessonId"
    )

    val uiState by viewModel.uiState.collectAsState()

    // Back button interception
    BackHandler {
        showExitDialog = true
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Quit Lesson?", fontWeight = FontWeight.Bold) },
            text = { Text("You will lose all XP progress and hearts earned in this lesson. Are you sure you want to go back?") },
            confirmButton = {
                Button(
                    onClick = {
                        showExitDialog = false
                        onBackToHome()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HeartRed)
                ) {
                    Text("Quit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        when (val state = uiState) {
            is LessonUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = DuoGreen)
                }
            }

            is LessonUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(imageVector = Icons.Default.Error, contentDescription = null, tint = HeartRed, modifier = Modifier.size(64.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(state.message, textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = onBackToHome, colors = ButtonDefaults.buttonColors(containerColor = DuoGreen)) {
                        Text("Back to Home")
                    }
                }
            }

            is LessonUiState.Playing -> {
                if (state.isLessonFinished) {
                    SummaryCompletedScreen(
                        lesson = state.lesson,
                        xpEarned = state.xpEarned,
                        heartsLeft = state.hearts,
                        onDone = {
                            if (state.xpEarned > 0) {
                                onAwardXp(state.xpEarned)
                            }
                            onBackToHome()
                        }
                    )
                } else {
                    LessonPlayingLayout(
                        state = state,
                        onExitClick = { showExitDialog = true },
                        onVoiceClick = { viewModel.speakCurrentText() },
                        onSelectOption = { viewModel.selectOption(it) },
                        onTextAnswerChange = { viewModel.updateTypedAnswer(it) },
                        onSelectMatching = { s, isEng -> viewModel.selectMatchingItem(s, isEng) },
                        onSimulateSpeakingClick = { viewModel.simulateMicSpeaking() },
                        onCheckClick = { viewModel.checkAnswer() },
                        onContinueClick = { viewModel.continueToNext() }
                    )
                }
            }
        }
    }
}

@Composable
fun LessonPlayingLayout(
    state: LessonUiState.Playing,
    onExitClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onSelectOption: (String) -> Unit,
    onTextAnswerChange: (String) -> Unit,
    onSelectMatching: (String, Boolean) -> Unit,
    onSimulateSpeakingClick: () -> Unit,
    onCheckClick: () -> Unit,
    onContinueClick: () -> Unit
) {
    val currentExercise = state.exercises[state.currentStep]
    val progressFraction = state.currentStep.toFloat() / state.exercises.size.toFloat()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP STEPS METER BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            IconButton(
                onClick = onExitClick,
                modifier = Modifier.size(36.dp).testTag("lesson_exit_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit study session",
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            LinearProgressIndicator(
                progress = { progressFraction },
                modifier = Modifier
                    .weight(1f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(7.dp)),
                color = DuoGreen,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Life pool meter",
                    tint = HeartRed,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = state.hearts.toString(),
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // CENTER EXERCISE CAROUSEL
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            // PROMPT TITLE
            Text(
                text = currentExercise.prompt,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(24.dp))

            // RENDER BY TYPE
            when (currentExercise.type) {
                ExerciseType.MULTIPLE_CHOICE -> {
                    MultipleChoiceView(
                        exercise = currentExercise,
                        selectedValue = state.selectedOption,
                        isChecked = state.isChecked,
                        onSelect = onSelectOption,
                        onVoicePlay = onVoiceClick
                    )
                }

                ExerciseType.TRANSLATE -> {
                    TranslateView(
                        exercise = currentExercise,
                        textVal = state.typedAnswer,
                        onTextChange = onTextAnswerChange,
                        isChecked = state.isChecked
                    )
                }

                ExerciseType.LISTENING -> {
                    ListeningView(
                        exercise = currentExercise,
                        selectedValue = state.selectedOption,
                        isChecked = state.isChecked,
                        onSelect = onSelectOption,
                        onVoicePlay = onVoiceClick
                    )
                }

                ExerciseType.SPEAKING -> {
                    SpeakingView(
                        exercise = currentExercise,
                        isSpeakingSimulated = state.isSpeakingSimulated,
                        typedAnswer = state.typedAnswer,
                        onTriggerSim = onSimulateSpeakingClick,
                        isChecked = state.isChecked
                    )
                }

                ExerciseType.MATCHING -> {
                    MatchingView(
                        exercise = currentExercise,
                        selectedEnglish = state.selectedEnglish,
                        selectedThai = state.selectedThai,
                        matchedPairs = state.matchedPairs,
                        onSelect = onSelectMatching
                    )
                }
            }
        }

        // GAUGE BOTTOM EVALUATOR STRIP
        BottomActionStrip(
            isChecked = state.isChecked,
            isCorrect = state.isCorrect,
            selectedValue = state.selectedOption,
            typedValue = state.typedAnswer,
            matchedPairs = state.matchedPairs,
            currentType = currentExercise.type,
            correctAnswer = currentExercise.correctAnswer,
            onCheck = onCheckClick,
            onContinue = onContinueClick
        )
    }
}

@Composable
fun MultipleChoiceView(
    exercise: Exercise,
    selectedValue: String,
    isChecked: Boolean,
    onSelect: (String) -> Unit,
    onVoicePlay: () -> Unit
) {
    Column {
        // Question bubble card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("choice_question_bubble"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Row(
                modifier = Modifier.padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                IconButton(
                    onClick = onVoicePlay,
                    modifier = Modifier.background(GemCyan, CircleShape).size(48.dp)
                ) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Listen to pronunciation", tint = Color.White)
                }
                Column {
                    Text(
                        text = exercise.question,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (exercise.romanization.isNotEmpty()) {
                        Text(
                            text = "(${exercise.romanization})",
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))

        // Grid cards
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            exercise.options.forEach { option ->
                val isSelected = selectedValue == option
                val border = when {
                    isSelected -> DuoGreen
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                }
                val bg = if (isSelected) DuoGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface

                Card(
                    onClick = { if (!isChecked) onSelect(option) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("option_$option"),
                    colors = CardDefaults.cardColors(containerColor = bg),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        width = if (isSelected) 3.dp else 1.5.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(border)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = option,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = if (isSelected) DuoGreenDark else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TranslateView(
    exercise: Exercise,
    textVal: String,
    onTextChange: (String) -> Unit,
    isChecked: Boolean
) {
    Column {
        // Source card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("translate_question_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = exercise.question,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (exercise.romanization.isNotEmpty()) {
                    Text(
                        text = "(${exercise.romanization})",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Input Box
        OutlinedTextField(
            value = textVal,
            onValueChange = onTextChange,
            placeholder = { Text("Type your English translation here...") },
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .testTag("translate_input_field"),
            enabled = !isChecked,
            maxLines = 4,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DuoGreen,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                disabledBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            ),
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun ListeningView(
    exercise: Exercise,
    selectedValue: String,
    isChecked: Boolean,
    onSelect: (String) -> Unit,
    onVoicePlay: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Speaker Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onVoicePlay() }
                .testTag("listen_speaker_box"),
            colors = CardDefaults.cardColors(containerColor = GemCyan.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(20.dp),
            border = CardDefaults.outlinedCardBorder(enabled = true).copy(width = 2.dp, brush = androidx.compose.ui.graphics.SolidColor(GemCyan))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(GemCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Play Pronunciation audio", tint = Color.White, modifier = Modifier.size(44.dp))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Tap to listen again", fontWeight = FontWeight.Bold, color = GemCyan)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Options list
        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            exercise.options.forEach { option ->
                val isSelected = selectedValue == option
                val border = if (isSelected) DuoGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                val bg = if (isSelected) DuoGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface

                Card(
                    onClick = { if (!isChecked) onSelect(option) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("listen_option_$option"),
                    colors = CardDefaults.cardColors(containerColor = bg),
                    shape = RoundedCornerShape(16.dp),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        width = if (isSelected) 3.dp else 1.5.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(border)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                        Text(
                            text = option,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp),
                            color = if (isSelected) DuoGreenDark else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SpeakingView(
    exercise: Exercise,
    isSpeakingSimulated: Boolean,
    typedAnswer: String,
    onTriggerSim: () -> Unit,
    isChecked: Boolean
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Text Target
        Card(
            modifier = Modifier.fillMaxWidth().testTag("speaking_target_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = exercise.question,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = DuoGreen,
                    textAlign = TextAlign.Center
                )
                if (exercise.romanization.isNotEmpty()) {
                    Text(
                        text = "Pronounce: \"${exercise.romanization}\"",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        // Simulated Mic Interaction
        Box(
            modifier = Modifier
                .size(130.dp)
                .clip(CircleShape)
                .background(
                    if (isSpeakingSimulated) HeartRed.copy(alpha = 0.15f) else DuoGreen.copy(alpha = 0.15f)
                )
                .clickable(enabled = !isChecked && !isSpeakingSimulated) { onTriggerSim() }
                .testTag("microphone_sim_btn"),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(90.dp)
                    .background(if (isSpeakingSimulated) HeartRed else DuoGreen, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isSpeakingSimulated) Icons.Default.MicNone else Icons.Default.Mic,
                    contentDescription = if (isSpeakingSimulated) "Listening" else "Record pronunciation",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isSpeakingSimulated) {
            Text("🔈 Mock Analyzing Pronunciation...", fontWeight = FontWeight.Bold, color = HeartRed)
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(5) { idx ->
                    Box(modifier = Modifier.width(6.dp).height(24.dp).background(HeartRed, shape = RoundedCornerShape(3.dp)))
                }
            }
        } else {
            if (typedAnswer.isNotEmpty()) {
                Text("✅ Captured Output: \"$typedAnswer\"", fontWeight = FontWeight.Bold, color = DuoGreen)
            } else {
                Text("Tap the Mic and speak details clearly!", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
fun MatchingView(
    exercise: Exercise,
    selectedEnglish: String,
    selectedThai: String,
    matchedPairs: Set<String>,
    onSelect: (String, Boolean) -> Unit
) {
    // Collect options and sort English/Thai separately to let user match
    val rawOptions = exercise.options
    // Sort words
    val englishWords = remember(rawOptions) { rawOptions.filter { it.firstOrNull()?.isLetter() ?: false }.shuffled() }
    val thaiWords = remember(rawOptions) { rawOptions.filter { !englishWords.contains(it) }.shuffled() }

    Row(modifier = Modifier.fillMaxWidth().testTag("matching_panel_row"), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        // Left Thai Cards
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            thaiWords.forEach { word ->
                val isMatched = matchedPairs.any { it.startsWith("$word:") }
                val isSelected = selectedThai == word
                val bg = when {
                    isMatched -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    isSelected -> DuoGreen.copy(alpha = 0.08f)
                    else -> MaterialTheme.colorScheme.surface
                }
                val border = when {
                    isMatched -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    isSelected -> DuoGreen
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                }

                Card(
                    onClick = { if (!isMatched) onSelect(word, false) },
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("matching_thai_$word"),
                    colors = CardDefaults.cardColors(containerColor = bg),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        width = if (isSelected) 3.dp else 1.5.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(border)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = word,
                            fontWeight = FontWeight.Bold,
                            color = if (isMatched) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }

        // Right English Cards
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            englishWords.forEach { word ->
                val isMatched = matchedPairs.any { it.endsWith(":$word") }
                val isSelected = selectedEnglish == word
                val bg = when {
                    isMatched -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    isSelected -> DuoGreen.copy(alpha = 0.08f)
                    else -> MaterialTheme.colorScheme.surface
                }
                val border = when {
                    isMatched -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    isSelected -> DuoGreen
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                }

                Card(
                    onClick = { if (!isMatched) onSelect(word, true) },
                    modifier = Modifier.fillMaxWidth().height(56.dp).testTag("matching_eng_$word"),
                    colors = CardDefaults.cardColors(containerColor = bg),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        width = if (isSelected) 3.dp else 1.5.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(border)
                    )
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = word,
                            fontWeight = FontWeight.Bold,
                            color = if (isMatched) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun BottomActionStrip(
    isChecked: Boolean,
    isCorrect: Boolean,
    selectedValue: String,
    typedValue: String,
    matchedPairs: Set<String>,
    currentType: ExerciseType,
    correctAnswer: String,
    onCheck: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val hasMadeSelection = when (currentType) {
        ExerciseType.MULTIPLE_CHOICE, ExerciseType.LISTENING -> selectedValue.isNotEmpty()
        ExerciseType.TRANSLATE, ExerciseType.SPEAKING -> typedValue.isNotBlank()
        ExerciseType.MATCHING -> matchedPairs.size == correctAnswer.split("|").size
    }

    Surface(
        tonalElevation = 8.dp,
        color = when {
            isChecked && isCorrect -> CorrectFill
            isChecked && !isCorrect -> IncorrectFill
            else -> MaterialTheme.colorScheme.surface
        },
        border = if (isChecked) {
            androidx.compose.foundation.BorderStroke(2.dp, if (isCorrect) CorrectStroke else IncorrectStroke)
        } else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            if (isChecked) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(if (isCorrect) CorrectStroke else IncorrectStroke, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCorrect) Icons.Default.Check else Icons.Default.PriorityHigh,
                            contentDescription = if (isCorrect) "Correct answer" else "Incorrect answer",
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (isCorrect) "Excellent job! You got it." else "Oops, incorrect!",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isCorrect) CorrectText else IncorrectText
                        )
                        if (!isCorrect) {
                            Text(
                                text = "Correct answer: $correctAnswer",
                                fontSize = 14.sp,
                                color = IncorrectText.copy(alpha = 0.8f)
                            )
                        }
                    }
                }

                Button(
                    onClick = onContinue,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("grading_continue_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isCorrect) CorrectStroke else IncorrectStroke
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CONTINUE", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                Button(
                    onClick = onCheck,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("grading_check_btn"),
                    enabled = hasMadeSelection,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DuoGreen,
                        disabledContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CHECK ANSWER", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun SummaryCompletedScreen(
    lesson: Lesson,
    xpEarned: Int,
    heartsLeft: Int,
    onDone: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Star Canvas Draw
        Box(modifier = Modifier.size(140.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val center = Offset(size.width / 2f, size.height / 2f)
                // Draw Golden Cup
                drawCircle(color = LevelGold, radius = 54f, center = center)
            }
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = "Trophy Success", tint = Color.White, modifier = Modifier.size(64.dp))
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Lesson Completed!",
            fontSize = 28.sp,
            fontWeight = FontWeight.Black,
            color = DuoGreen,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Sabai dee! You studied '${lesson.title}' successfully offline.",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = GemCyan.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("XP EARNED", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GemCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("+$xpEarned XP", fontSize = 22.sp, fontWeight = FontWeight.Black, color = GemCyan)
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = HeartRed.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("STARS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = HeartRed)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        repeat(heartsLeft.coerceAtLeast(1)) {
                            Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = LevelGold, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .testTag("lesson_finished_done_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = DuoGreen),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text("Back to Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
    }
}
