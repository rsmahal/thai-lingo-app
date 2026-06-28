package com.example.feature

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import android.widget.Toast
import com.example.LocalShowRomanizationOnly
import com.example.LocalVocabularyList
import com.example.core.common.getRomanizedText
import com.example.core.common.ThaiToneIndicatorRow
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

    val sessionId = remember(lessonId) { System.currentTimeMillis() }

    val viewModel: LessonViewModel = viewModel(
        factory = LessonViewModel.Factory(lessonId, context),
        key = "LessonPlayVM_${lessonId}_$sessionId"
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
            text = { Text("You will lose all progress and stars earned in this lesson. Are you sure you want to go back?") },
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
                    val totalNonPopQuiz = state.exercises.count { !it.isPopQuiz }
                    val nonPopQuizMistakes = state.incorrectExercises.count { !it.first.isPopQuiz }
                    val correctNonPopQuiz = totalNonPopQuiz - nonPopQuizMistakes
                    val ratio = if (totalNonPopQuiz > 0) correctNonPopQuiz.toFloat() / totalNonPopQuiz else 1f
                    val starsAwarded = when {
                        ratio >= 1.0f -> 3
                        ratio >= 0.8f -> 2
                        ratio >= 0.6f -> 1
                        else -> 0
                    }
                    val passed = starsAwarded >= 1

                    if (state.isTopicTest) {
                        TestResultScreen(
                            lesson = state.lesson,
                            xpEarned = state.xpEarned,
                            exercises = state.exercises,
                            incorrectExercises = state.incorrectExercises,
                            onDone = {
                                if (state.xpEarned > 0 && passed) {
                                    onAwardXp(state.xpEarned)
                                }
                                onBackToHome()
                            },
                            onTryAgain = {
                                viewModel.restartSession()
                            }
                        )
                    } else {
                        SummaryCompletedScreen(
                            lesson = state.lesson,
                            xpEarned = state.xpEarned,
                            exercises = state.exercises,
                            incorrectExercises = state.incorrectExercises,
                            onDone = {
                                if (state.xpEarned > 0 && passed) {
                                    onAwardXp(state.xpEarned)
                                }
                                onBackToHome()
                            }
                        )
                    }
                } else if (state.isIntroducing && state.introWords.isNotEmpty()) {
                    LessonIntroduceLayout(
                        state = state,
                        onExitClick = { showExitDialog = true },
                        onVoiceClick = { viewModel.speakIntroWord() },
                        onNextClick = { viewModel.nextIntroWord() },
                        onPrevClick = { viewModel.prevIntroWord() },
                        onSpeakText = { viewModel.speakWordText(it) }
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
    val isDark = isSystemInDarkTheme()
    val isPopQuiz = currentExercise.isPopQuiz
    val bgyColor = if (isPopQuiz) {
        if (isDark) Color(0xFF51197A) else Color(0xFFF3E8FF)
    } else {
        MaterialTheme.colorScheme.background
    }

    val isTimerVisible = com.example.LocalActiveTimerVisible.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bgyColor)
            .then(if (isTimerVisible) Modifier else Modifier.statusBarsPadding())
            .navigationBarsPadding(),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // TOP STEPS METER BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = 20.dp,
                    end = 20.dp,
                    top = if (isTimerVisible) 4.dp else 12.dp,
                    bottom = if (isTimerVisible) 4.dp else 12.dp
                ),
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


        }

        // CENTER EXERCISE CAROUSEL
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            if (isPopQuiz) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isDark) Color(0xFF7B2CBF) else Color(0xFF6200EE),
                    modifier = Modifier.padding(bottom = 12.dp).testTag("pop_quiz_badge")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.OfflineBolt,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "POP QUIZ",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.sp
                        )
                    }
                }
            }
            
            // PROMPT TITLE
            Text(
                text = currentExercise.prompt,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold,
                color = if (isPopQuiz) {
                    if (isDark) Color(0xFFEADBFF) else Color(0xFF3B1E54)
                } else {
                    MaterialTheme.colorScheme.onBackground
                },
                modifier = copyOnLongPressModifier(currentExercise.prompt)
            )

            Spacer(modifier = Modifier.height(10.dp))

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
                        isMatchingCorrect = state.isMatchingCorrect,
                        checkActivePair = state.checkActivePair,
                        onSelect = onSelectMatching
                    )
                }

                ExerciseType.SENTENCE_BUILD -> {
                    SentenceBuildView(
                        exercise = currentExercise,
                        textVal = state.typedAnswer,
                        onTextChange = onTextAnswerChange,
                        isChecked = state.isChecked,
                        onVoicePlay = onVoiceClick
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
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
            matchingIncorrectAttempts = state.matchingIncorrectAttempts,
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
    val haptic = LocalHapticFeedback.current
    val showRomanizationOnly = LocalShowRomanizationOnly.current
    val vocabularyList = LocalVocabularyList.current

    Column {
        // Question bubble card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("choice_question_bubble"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)),
            shape = RoundedCornerShape(16.dp)
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val isQuestionThai = exercise.question.any { it in '\u0E00'..'\u0E7F' }
                if (isQuestionThai) {
                    IconButton(
                        onClick = onVoicePlay,
                        modifier = Modifier.background(GemCyan, CircleShape).size(36.dp)
                    ) {
                        Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Listen to pronunciation", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                Column {
                    Text(
                        text = if (showRomanizationOnly) getRomanizedText(exercise.question, vocabularyList) else exercise.question,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = copyOnLongPressModifier(if (showRomanizationOnly) getRomanizedText(exercise.question, vocabularyList) else exercise.question)
                    )
                    if (exercise.romanization.isNotEmpty() && !showRomanizationOnly) {
                        Text(
                            text = "(${exercise.romanization})",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                    ThaiToneIndicatorRow(
                        thaiWord = exercise.question,
                        showLabel = true,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Grid cards
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            exercise.options.forEach { option ->
                val isSelected = selectedValue == option
                val isCorrectAnswer = option == exercise.correctAnswer

                val bg: Color
                val border: Color
                val textColor: Color
                val borderWidth: androidx.compose.ui.unit.Dp

                if (isChecked) {
                    when {
                        isCorrectAnswer -> {
                            bg = CorrectFill.copy(alpha = 0.2f)
                            border = CorrectStroke
                            textColor = CorrectText
                            borderWidth = 3.dp
                        }
                        isSelected -> {
                            bg = IncorrectFill.copy(alpha = 0.2f)
                            border = HeartRed
                            textColor = HeartRed
                            borderWidth = 3.dp
                        }
                        else -> {
                            bg = MaterialTheme.colorScheme.surface
                            border = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            borderWidth = 1.5.dp
                        }
                    }
                } else {
                    if (isSelected) {
                        bg = GemCyan.copy(alpha = 0.08f)
                        border = GemCyan
                        textColor = GemCyan
                        borderWidth = 3.dp
                    } else {
                        bg = MaterialTheme.colorScheme.surface
                        border = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        textColor = MaterialTheme.colorScheme.onSurface
                        borderWidth = 1.5.dp
                    }
                }

                Card(
                    onClick = {
                        if (!isChecked) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(option)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("option_$option"),
                    colors = CardDefaults.cardColors(containerColor = bg),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        width = borderWidth,
                        brush = androidx.compose.ui.graphics.SolidColor(border)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (showRomanizationOnly) getRomanizedText(option, vocabularyList) else option,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        if (isChecked && (isSelected || option == exercise.correctAnswer)) {
                            ThaiToneIndicatorRow(thaiWord = option, showLabel = true)
                        }
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
    val showRomanizationOnly = LocalShowRomanizationOnly.current
    val vocabularyList = LocalVocabularyList.current

    Column {
        // Source card
        Card(
            modifier = Modifier.fillMaxWidth().testTag("translate_question_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            shape = RoundedCornerShape(20.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = if (showRomanizationOnly) getRomanizedText(exercise.question, vocabularyList) else exercise.question,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = copyOnLongPressModifier(if (showRomanizationOnly) getRomanizedText(exercise.question, vocabularyList) else exercise.question)
                )
                if (exercise.romanization.isNotEmpty() && !showRomanizationOnly) {
                    Text(
                        text = "(${exercise.romanization})",
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
                ThaiToneIndicatorRow(
                    thaiWord = exercise.question,
                    showLabel = true,
                    modifier = Modifier.padding(top = 4.dp)
                )
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentenceBuildView(
    exercise: Exercise,
    textVal: String,
    onTextChange: (String) -> Unit,
    isChecked: Boolean,
    onVoicePlay: () -> Unit
) {
    val showRomanizationOnly = LocalShowRomanizationOnly.current
    val vocabularyList = LocalVocabularyList.current

    val selectedTokens = remember(textVal) {
        if (textVal.isEmpty()) emptyList() else textVal.split("|")
    }

    // Smart duplicate-aware tracking of used indices in the options list
    val usedIndices = remember(selectedTokens, exercise.options) {
        val indices = mutableSetOf<Int>()
        for (token in selectedTokens) {
            val matchIndex = exercise.options.indices.firstOrNull { idx ->
                exercise.options[idx] == token && !indices.contains(idx)
            }
            if (matchIndex != null) {
                indices.add(matchIndex)
            }
        }
        indices
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Source Question Bubble Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("sentence_build_question_bubble"),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
            ),
            shape = RoundedCornerShape(20.dp)
        ) {
            val isListeningSb = exercise.prompt.contains("Listen", ignoreCase = true)
            Row(
                modifier = Modifier.padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (isListeningSb) {
                    IconButton(
                        onClick = onVoicePlay,
                        modifier = Modifier
                            .background(GemCyan, CircleShape)
                            .size(56.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Listen to pronunciation",
                            tint = Color.White,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Listen & Translate",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Audio-only challenge",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                } else {
                    IconButton(
                        onClick = onVoicePlay,
                        modifier = Modifier
                            .background(GemCyan, CircleShape)
                            .size(44.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Listen to pronunciation",
                            tint = Color.White
                        )
                    }
                    Column {
                        Text(
                            text = if (showRomanizationOnly) getRomanizedText(exercise.question, vocabularyList) else exercise.question,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = copyOnLongPressModifier(if (showRomanizationOnly) getRomanizedText(exercise.question, vocabularyList) else exercise.question)
                        )
                        if (exercise.romanization.isNotEmpty() && !showRomanizationOnly) {
                            Text(
                                text = "(${exercise.romanization})",
                                fontSize = 15.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // ASSEMBLED CHIPS CONTAINER
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 130.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f))
                .border(
                    width = 1.5.dp,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            if (selectedTokens.isEmpty()) {
                Text(
                    text = "Tap words below to assemble the translation",
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            } else {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    selectedTokens.forEachIndexed { index, token ->
                        // Chip representing assembled word
                        Card(
                            onClick = {
                                if (!isChecked) {
                                    val list = selectedTokens.toMutableList()
                                    list.removeAt(index)
                                    onTextChange(list.joinToString("|"))
                                }
                            },
                            enabled = !isChecked,
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            modifier = Modifier.testTag("assembled_chip_$index")
                        ) {
                            Text(
                                text = if (showRomanizationOnly) getRomanizedText(token, vocabularyList) else token,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // WORD CHOICES BANK
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            exercise.options.forEachIndexed { index, option ->
                val isUsed = usedIndices.contains(index)
                
                Box(modifier = Modifier.height(44.dp)) {
                    if (isUsed) {
                        // Ghost/Used item placeholder
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f),
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = option,
                                color = Color.Transparent, // Invisible text to reserve exact layout size
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Clickable choice chip
                        Card(
                            onClick = {
                                if (!isChecked) {
                                    val list = selectedTokens.toMutableList()
                                    list.add(option)
                                    onTextChange(list.joinToString("|"))
                                }
                            },
                            enabled = !isChecked,
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.onSurface
                            ),
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
                            modifier = Modifier.testTag("choice_chip_$index")
                        ) {
                            Text(
                                text = if (showRomanizationOnly) getRomanizedText(option, vocabularyList) else option,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
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
    val haptic = LocalHapticFeedback.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Speaker Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onVoicePlay()
                }
                .testTag("listen_speaker_box"),
            colors = CardDefaults.cardColors(containerColor = GemCyan.copy(alpha = 0.1f)),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder(enabled = true).copy(width = 1.5.dp, brush = androidx.compose.ui.graphics.SolidColor(GemCyan))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(GemCyan, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Play Pronunciation audio", tint = Color.White, modifier = Modifier.size(28.dp))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text("Tap to listen again", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = GemCyan)
                val thaiAudioText = if (exercise.audioText.isNotEmpty()) exercise.audioText else if (exercise.question.any { it in '\u0E00'..'\u0E7F' }) exercise.question else ""
                if (thaiAudioText.any { it in '\u0E00'..'\u0E7F' }) {
                    Spacer(modifier = Modifier.height(6.dp))
                    ThaiToneIndicatorRow(thaiWord = thaiAudioText, showLabel = true)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Options list
        Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            exercise.options.forEach { option ->
                val isSelected = selectedValue == option
                val isCorrectAnswer = option == exercise.correctAnswer

                val bg: Color
                val border: Color
                val textColor: Color
                val borderWidth: androidx.compose.ui.unit.Dp

                if (isChecked) {
                    when {
                        isCorrectAnswer -> {
                            bg = CorrectFill.copy(alpha = 0.2f)
                            border = CorrectStroke
                            textColor = CorrectText
                            borderWidth = 3.dp
                        }
                        isSelected -> {
                            bg = IncorrectFill.copy(alpha = 0.2f)
                            border = HeartRed
                            textColor = HeartRed
                            borderWidth = 3.dp
                        }
                        else -> {
                            bg = MaterialTheme.colorScheme.surface
                            border = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                            textColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            borderWidth = 1.5.dp
                        }
                    }
                } else {
                    if (isSelected) {
                        bg = GemCyan.copy(alpha = 0.08f)
                        border = GemCyan
                        textColor = GemCyan
                        borderWidth = 3.dp
                    } else {
                        bg = MaterialTheme.colorScheme.surface
                        border = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                        textColor = MaterialTheme.colorScheme.onSurface
                        borderWidth = 1.5.dp
                    }
                }

                Card(
                    onClick = {
                        if (!isChecked) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(option)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("listen_option_$option"),
                    colors = CardDefaults.cardColors(containerColor = bg),
                    shape = RoundedCornerShape(12.dp),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        width = borderWidth,
                        brush = androidx.compose.ui.graphics.SolidColor(border)
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = option,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = textColor
                        )
                        ThaiToneIndicatorRow(thaiWord = option, showLabel = true)
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
    val haptic = LocalHapticFeedback.current
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
                    textAlign = TextAlign.Center,
                    modifier = copyOnLongPressModifier(exercise.question)
                )
                if (exercise.romanization.isNotEmpty()) {
                    Text(
                        text = "Pronounce: \"${exercise.romanization}\"",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp).then(copyOnLongPressModifier(exercise.romanization))
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
                .clickable(enabled = !isChecked && !isSpeakingSimulated) {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTriggerSim()
                }
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
    isMatchingCorrect: Boolean?,
    checkActivePair: Pair<String, String>?,
    onSelect: (String, Boolean) -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val showRomanizationOnly = LocalShowRomanizationOnly.current
    val vocabularyList = LocalVocabularyList.current

    // Collect options and sort English/Thai separately to let user match
    val rawOptions = exercise.options
    // English words containing ASCII alphabet characters shuffled stably
    val englishWords = remember(rawOptions) {
        rawOptions.filter { word -> word.any { it in 'A'..'Z' || it in 'a'..'z' } }.shuffled()
    }
    val thaiWords = remember(rawOptions) {
        rawOptions.filter { !englishWords.contains(it) }.shuffled()
    }

    Row(
        modifier = Modifier.fillMaxWidth().testTag("matching_panel_row"),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Left Column (English words)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            englishWords.forEach { word ->
                val isMatched = matchedPairs.any { it.endsWith(":$word") }

                if (isMatched) {
                    Spacer(modifier = Modifier.fillMaxWidth().height(56.dp))
                } else {
                    val isSelected = selectedEnglish == word
                    val isActiveCheck = checkActivePair?.second == word

                    val bg = when {
                        isActiveCheck && isMatchingCorrect == true -> CorrectFill
                        isActiveCheck && isMatchingCorrect == false -> HeartRedLight
                        isSelected -> GemCyan.copy(alpha = 0.08f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val border = when {
                        isActiveCheck && isMatchingCorrect == true -> CorrectStroke
                        isActiveCheck && isMatchingCorrect == false -> HeartRed
                        isSelected -> GemCyan
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    }
                    val textColor = when {
                        isActiveCheck && isMatchingCorrect == true -> CorrectText
                        isActiveCheck && isMatchingCorrect == false -> HeartRed
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Card(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(word, true)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("matching_eng_$word"),
                        colors = CardDefaults.cardColors(containerColor = bg),
                        border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                            width = if (isSelected || isActiveCheck) 3.dp else 1.5.dp,
                            brush = androidx.compose.ui.graphics.SolidColor(border)
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = word,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontSize = 15.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Right Column (Thai words)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            thaiWords.forEach { word ->
                val isMatched = matchedPairs.any { it.startsWith("$word:") }

                if (isMatched) {
                    Spacer(modifier = Modifier.fillMaxWidth().height(56.dp))
                } else {
                    val isSelected = selectedThai == word
                    val isActiveCheck = checkActivePair?.first == word

                    val bg = when {
                        isActiveCheck && isMatchingCorrect == true -> CorrectFill
                        isActiveCheck && isMatchingCorrect == false -> HeartRedLight
                        isSelected -> GemCyan.copy(alpha = 0.08f)
                        else -> MaterialTheme.colorScheme.surface
                    }
                    val border = when {
                        isActiveCheck && isMatchingCorrect == true -> CorrectStroke
                        isActiveCheck && isMatchingCorrect == false -> HeartRed
                        isSelected -> GemCyan
                        else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    }
                    val textColor = when {
                        isActiveCheck && isMatchingCorrect == true -> CorrectText
                        isActiveCheck && isMatchingCorrect == false -> HeartRed
                        else -> MaterialTheme.colorScheme.onSurface
                    }

                    Card(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSelect(word, false)
                        },
                        modifier = Modifier.fillMaxWidth().height(56.dp).testTag("matching_thai_$word"),
                        colors = CardDefaults.cardColors(containerColor = bg),
                        border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                            width = if (isSelected || isActiveCheck) 3.dp else 1.5.dp,
                            brush = androidx.compose.ui.graphics.SolidColor(border)
                        )
                    ) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = if (showRomanizationOnly) getRomanizedText(word, vocabularyList) else word,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                fontSize = 16.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
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
    matchingIncorrectAttempts: Int,
    onCheck: () -> Unit,
    onContinue: () -> Unit
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    val isWarning = currentType == ExerciseType.MATCHING && isChecked && !isCorrect

    LaunchedEffect(isChecked) {
        if (isChecked && (isCorrect || isWarning)) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    val hasMadeSelection = when (currentType) {
        ExerciseType.MULTIPLE_CHOICE, ExerciseType.LISTENING -> selectedValue.isNotEmpty()
        ExerciseType.TRANSLATE, ExerciseType.SPEAKING, ExerciseType.SENTENCE_BUILD -> typedValue.isNotBlank()
        ExerciseType.MATCHING -> matchedPairs.size == correctAnswer.split("|").size
    }

    Surface(
        tonalElevation = 8.dp,
        color = when {
            isWarning -> WarningFill
            isChecked && isCorrect -> CorrectFill
            isChecked && !isCorrect -> IncorrectFill
            else -> MaterialTheme.colorScheme.surface
        },
        border = if (isChecked) {
            androidx.compose.foundation.BorderStroke(2.dp, when {
                isWarning -> WarningStroke
                isCorrect -> CorrectStroke
                else -> IncorrectStroke
            })
        } else null,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 18.dp)
        ) {
            if (isChecked) {
                val boxBg = when {
                    isWarning -> WarningStroke
                    isCorrect -> CorrectStroke
                    else -> IncorrectStroke
                }
                
                val iconVector = when {
                    isWarning -> Icons.Default.PriorityHigh
                    isCorrect -> Icons.Default.Check
                    else -> Icons.Default.PriorityHigh
                }
                
                val headerText = when {
                    isWarning -> "Nearly"
                    isCorrect -> "Excellent job! You got it."
                    else -> "Oops, incorrect!"
                }
                
                val headerColor = when {
                    isWarning -> WarningText
                    isCorrect -> CorrectText
                    else -> IncorrectText
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(boxBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = "Status",
                            tint = Color.White
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = headerText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = headerColor
                        )
                        if (isWarning) {
                            Text(
                                text = "You made $matchingIncorrectAttempts incorrect attempts.",
                                fontSize = 14.sp,
                                color = WarningText.copy(alpha = 0.8f)
                            )
                        } else {
                            val formattedCorrectAnswer = if (currentType == ExerciseType.SENTENCE_BUILD) {
                                correctAnswer.replace("|", " ")
                            } else {
                                correctAnswer
                            }
                            if (!isCorrect) {
                                Text(
                                    text = "Correct answer: $formattedCorrectAnswer",
                                    fontSize = 14.sp,
                                    color = IncorrectText.copy(alpha = 0.8f),
                                    modifier = copyOnLongPressModifier(formattedCorrectAnswer)
                                )
                            } else {
                                Text(
                                    text = "Answer: $formattedCorrectAnswer",
                                    fontSize = 14.sp,
                                    color = CorrectText.copy(alpha = 0.8f),
                                    modifier = copyOnLongPressModifier(formattedCorrectAnswer)
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onContinue()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .testTag("grading_continue_btn"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = when {
                            isWarning -> WarningStroke
                            isCorrect -> CorrectStroke
                            else -> IncorrectStroke
                        }
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("CONTINUE", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            } else {
                Button(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onCheck()
                    },
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
    exercises: List<Exercise> = emptyList(),
    incorrectExercises: List<Pair<Exercise, String>> = emptyList(),
    onDone: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val totalNonPopQuiz = exercises.count { !it.isPopQuiz }
    val nonPopQuizMistakes = incorrectExercises.count { !it.first.isPopQuiz }
    val correctNonPopQuiz = totalNonPopQuiz - nonPopQuizMistakes
    val ratio = if (totalNonPopQuiz > 0) correctNonPopQuiz.toFloat() / totalNonPopQuiz else 1f
    
    val starsAwarded = when {
        ratio >= 1.0f -> 3
        ratio >= 0.8f -> 2
        ratio >= 0.6f -> 1
        else -> 0
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Background rating visual FX
        when (starsAwarded) {
            3 -> ThreeStarsPartyAnimation()
            2 -> TwoStarsFloatAnimation()
            1 -> OneStarStormAnimation()
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Star Canvas Draw
            Box(modifier = Modifier.size(140.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(color = LevelGold, radius = 54f, center = center)
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = when (starsAwarded) {
                            3 -> Icons.Default.EmojiEvents
                            2 -> Icons.Default.WorkspacePremium
                            1 -> Icons.Default.Cloud
                            else -> Icons.Default.ErrorOutline
                        },
                        contentDescription = "Rating Trophy",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = when (starsAwarded) {
                    3 -> "Perfect Flawless! 🎉"
                    2 -> "Well Done! ⭐⭐"
                    1 -> "Lesson Passed! ⭐"
                    else -> "Keep Practicing! 🎯"
                },
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = when (starsAwarded) {
                    3 -> LevelGold
                    2 -> DuoGreen
                    1 -> MaterialTheme.colorScheme.primary
                    else -> HeartRed
                },
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = when (starsAwarded) {
                    3 -> "Sensational! You completed '${lesson.title}' with 3 stars!"
                    2 -> "Great practice! '${lesson.title}' completed with 2 stars."
                    1 -> "Nice job! '${lesson.title}' completed with 1 star."
                    else -> "You got ${(ratio * 100).toInt()}% correct on '${lesson.title}'. You need at least 60% (1 Star) to unlock the next lesson!"
                },
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.75f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            ThaiLingoMascot(
                expression = if (starsAwarded > 0) MascotExpression.HAPPY else MascotExpression.SAD,
                customMessage = when (starsAwarded) {
                    3 -> "Sawatdee ka! Look at you! 🏆 Perfect 3-star victory! Outstanding performance! 🎉✨"
                    2 -> "Amazing session! 🌟 2-stars earned! Excellent learning progress!"
                    1 -> "You passed! ⭐ 1-star achieved! Let's aim even higher tomorrow!"
                    else -> "Oh, we didn't pass today. But don't worry, let's review together and try again! ❤️"
                },
                size = 94.dp,
                modifier = Modifier.testTag("summary_mascot")
            )

            Spacer(modifier = Modifier.height(20.dp))

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
                        Text("STATUS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GemCyan)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = when (starsAwarded) {
                                3 -> "PERFECT"
                                2, 1 -> "COMPLETED"
                                else -> "TRY AGAIN"
                            },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = GemCyan
                        )
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = LevelGold.copy(alpha = 0.1f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("STARS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = LevelGold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Row {
                            if (starsAwarded == 0) {
                                Text("0 Stars", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                repeat(starsAwarded) {
                                    Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = LevelGold, modifier = Modifier.size(20.dp))
                                }
                            }
                        }
                    }
                }
            }

            if (incorrectExercises.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Review Your Mistakes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(12.dp))

                incorrectExercises.forEach { (exercise, userAns) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = exercise.prompt,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = exercise.question,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Your answer: $userAns",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Correct: ${exercise.correctAnswer.replace("|", " ")}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuoGreen
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDone()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("lesson_finished_done_btn"),
                colors = ButtonDefaults.buttonColors(
                    containerColor = when (starsAwarded) {
                        3 -> LevelGold
                        2, 1 -> DuoGreen
                        else -> HeartRed
                    }
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Back to Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }
}

// SUPPORTING ANIMATED CLASSES & COMPOSABLES FOR RATING FXs:

class ConfettiParticle(
    var x: Float,
    var y: Float,
    var radius: Float,
    var color: Color,
    var speedY: Float,
    var speedX: Float,
    var rotation: Float,
    var rotationalSpeed: Float,
    var swayOffset: Float
)

class BubbleParticle(
    var x: Float,
    var y: Float,
    var radius: Float,
    var speedY: Float,
    var alpha: Float,
    var color: Color,
    var swayOffset: Float
)

class RainDropParticle(
    var x: Float,
    var y: Float,
    var length: Float,
    var speedY: Float
)

@Composable
fun ThreeStarsPartyAnimation() {
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val confettiList = remember { mutableStateListOf<ConfettiParticle>() }
    var ticker by remember { mutableStateOf(0) }
    
    // Create firework effect state
    var fireworkX by remember { mutableStateOf(0f) }
    var fireworkY by remember { mutableStateOf(0f) }
    var fireworkRadius by remember { mutableStateOf(0f) }
    var fireworkColor by remember { mutableStateOf(Color.Yellow) }
    var fireworkAlpha by remember { mutableStateOf(0f) }
    
    LaunchedEffect(screenSize) {
        if (screenSize.width == 0 || screenSize.height == 0) return@LaunchedEffect
        
        // Initialize confetti
        confettiList.clear()
        val colors = listOf(
            Color(0xFFFF4B4B), // Red
            Color(0xFF1CB0F6), // GemCyan
            Color(0xFF58CC02), // DuoGreen
            Color(0xFFFFC800), // LevelGold
            Color(0xFFFF9600), // StreakOrange
            Color(0xFFD946EF), // Magenta
            Color(0xFF8B5CF6)  // Violet
        )
        repeat(45) {
            confettiList.add(
                ConfettiParticle(
                    x = (0..screenSize.width).random().toFloat(),
                    y = (-screenSize.height..0).random().toFloat(),
                    radius = (8..20).random().toFloat(),
                    color = colors.random(),
                    speedY = (4..10).random().toFloat(),
                    speedX = (-3..3).random().toFloat(),
                    rotation = (0..360).random().toFloat(),
                    rotationalSpeed = (-10..10).random().toFloat(),
                    swayOffset = (0..360).random().toFloat()
                )
            )
        }
        
        var frame = 0
        while (true) {
            kotlinx.coroutines.delay(16) // ~60 FPS
            frame++
            
            // 1. Update confetti
            for (i in confettiList.indices) {
                val p = confettiList[i]
                p.y += p.speedY
                p.x += p.speedX + (kotlin.math.sin(p.swayOffset) * 1.5f)
                p.swayOffset += 0.05f
                p.rotation += p.rotationalSpeed
                
                // Reset when falls off bottom
                if (p.y > screenSize.height) {
                    p.y = -20f
                    p.x = (0..screenSize.width).random().toFloat()
                    p.speedY = (4..10).random().toFloat()
                }
            }
            
            // 2. Firework launcher
            if (fireworkAlpha > 0) {
                fireworkRadius += 8f
                fireworkAlpha -= 0.02f
            } else if (frame % 90 == 0) {
                // Spawn new firework safely
                val minX = (screenSize.width * 0.2f).toInt()
                val maxX = (screenSize.width * 0.8f).toInt()
                fireworkX = if (maxX > minX) (minX..maxX).random().toFloat() else screenSize.width / 2f
                
                val minY = (screenSize.height * 0.15f).toInt()
                val maxY = (screenSize.height * 0.6f).toInt()
                fireworkY = if (maxY > minY) (minY..maxY).random().toFloat() else screenSize.height * 0.4f
                
                fireworkRadius = 10f
                fireworkAlpha = 1f
                fireworkColor = listOf(Color.Red, Color.Yellow, Color.Cyan, Color.Green, Color.Magenta).random()
            }
            
            ticker++
        }
    }
    
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it }
    ) {
        val t = ticker // Force recomposition and redraw
        // Draw Fireworks if active
        if (fireworkAlpha > 0) {
            drawCircle(
                color = fireworkColor,
                radius = fireworkRadius,
                center = Offset(fireworkX, fireworkY),
                style = Stroke(width = 4.dp.toPx()),
                alpha = fireworkAlpha
            )
            // Spark particles
            for (angle in 0 until 360 step 45) {
                val rad = Math.toRadians(angle.toDouble())
                val sparkX = fireworkX + Math.cos(rad).toFloat() * (fireworkRadius * 1.2f)
                val sparkY = fireworkY + Math.sin(rad).toFloat() * (fireworkRadius * 1.2f)
                drawCircle(
                    color = fireworkColor,
                    radius = 6.dp.toPx() * fireworkAlpha,
                    center = Offset(sparkX, sparkY),
                    alpha = fireworkAlpha
                )
            }
        }
        
        // Draw Confetti
        confettiList.forEach { p ->
            rotate(degrees = p.rotation, pivot = Offset(p.x, p.y)) {
                drawRect(
                    color = p.color,
                    topLeft = Offset(p.x - p.radius, p.y - p.radius / 2),
                    size = Size(p.radius * 2, p.radius)
                )
            }
        }
    }
}

@Composable
fun TwoStarsFloatAnimation() {
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val bubbleList = remember { mutableStateListOf<BubbleParticle>() }
    var ticker by remember { mutableStateOf(0) }
    
    LaunchedEffect(screenSize) {
        if (screenSize.width == 0 || screenSize.height == 0) return@LaunchedEffect
        
        bubbleList.clear()
        val colors = listOf(
            LevelGold.copy(alpha = 0.6f),
            GemCyan.copy(alpha = 0.5f),
            DuoGreen.copy(alpha = 0.5f),
            Color.White.copy(alpha = 0.7f)
        )
        repeat(20) {
            bubbleList.add(
                BubbleParticle(
                    x = (0..screenSize.width).random().toFloat(),
                    y = (0..screenSize.height).random().toFloat(),
                    radius = (10..30).random().toFloat(),
                    speedY = -(1.5f + (0..3).random().toFloat() * 0.5f),
                    alpha = (0.3f + (0..5).random().toFloat() * 0.1f),
                    color = colors.random(),
                    swayOffset = (0..360).random().toFloat()
                )
            )
        }
        
        while (true) {
            kotlinx.coroutines.delay(16)
            for (i in bubbleList.indices) {
                val b = bubbleList[i]
                b.y += b.speedY
                b.x += kotlin.math.sin(b.swayOffset) * 0.8f
                b.swayOffset += 0.04f
                
                // Floating up off top, reset to bottom
                if (b.y < -30f) {
                    b.y = screenSize.height + 30f
                    b.x = (0..screenSize.width).random().toFloat()
                }
            }
            ticker++
        }
    }
    
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { screenSize = it }
    ) {
        val t = ticker // Force recomposition and redraw
        bubbleList.forEach { p ->
            drawCircle(
                color = p.color,
                radius = p.radius,
                center = Offset(p.x, p.y),
                alpha = p.alpha
            )
            // Elegant inner glass reflection crescent/shine
            drawCircle(
                color = Color.White.copy(alpha = p.alpha * 0.4f),
                radius = p.radius * 0.3f,
                center = Offset(p.x - p.radius * 0.3f, p.y - p.radius * 0.3f)
            )
        }
    }
}

@Composable
fun OneStarStormAnimation() {
    var screenSize by remember { mutableStateOf(IntSize.Zero) }
    val rainDropList = remember { mutableStateListOf<RainDropParticle>() }
    var lightningAlpha by remember { mutableStateOf(0f) }
    var ticker by remember { mutableStateOf(0) }
    
    LaunchedEffect(screenSize) {
        if (screenSize.width == 0 || screenSize.height == 0) return@LaunchedEffect
        
        rainDropList.clear()
        repeat(35) {
            rainDropList.add(
                RainDropParticle(
                    x = (0..screenSize.width).random().toFloat(),
                    y = (0..screenSize.height).random().toFloat(),
                    length = (15..35).random().toFloat(),
                    speedY = (15..25).random().toFloat()
                )
            )
        }
        
        var frame = 0
        while (true) {
            kotlinx.coroutines.delay(16)
            frame++
            
            // Update rain
            for (i in rainDropList.indices) {
                val r = rainDropList[i]
                r.y += r.speedY
                r.x -= 2f // Slight angle falling downwards to the left
                
                if (r.y > screenSize.height) {
                    r.y = -r.length
                    r.x = (0..screenSize.width).random().toFloat()
                }
            }
            
            // Random lightning flash
            if (lightningAlpha > 0) {
                lightningAlpha -= 0.1f
            } else if (frame % 200 == 0 && (0..1).random() == 1) {
                lightningAlpha = 0.8f
            }
            
            ticker++
        }
    }
    
    // Background Darkening layer dynamically integrated
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.4f)) // Stormy overcast tint
            .onSizeChanged { screenSize = it }
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height
            val t = ticker // Force recomposition and redraw
            
            // Draw storm clouds at the top
            val cloudColor = Color(0xFF3F4E52)
            drawCircle(
                color = cloudColor,
                radius = 120.dp.toPx(),
                center = Offset(0f, 0f)
            )
            drawCircle(
                color = cloudColor.copy(alpha = 0.9f),
                radius = 140.dp.toPx(),
                center = Offset(width * 0.4f, -40.dp.toPx())
            )
            drawCircle(
                color = cloudColor,
                radius = 110.dp.toPx(),
                center = Offset(width * 0.7f, -40.dp.toPx())
            )
            drawCircle(
                color = cloudColor.copy(alpha = 0.95f),
                radius = 130.dp.toPx(),
                center = Offset(width, 0f)
            )
            
            // Raindrops
            rainDropList.forEach { p ->
                drawLine(
                    color = Color(0x6663D7FE),
                    start = Offset(p.x, p.y),
                    end = Offset(p.x - 2f, p.y + p.length),
                    strokeWidth = 2.dp.toPx()
                )
            }
            
            // Flash lightning overlay
            if (lightningAlpha > 0) {
                drawRect(
                    color = Color.White,
                    size = Size(width, height),
                    alpha = lightningAlpha * 0.3f
                )
            }
        }
    }
}

@Composable
fun LessonIntroduceLayout(
    state: LessonUiState.Playing,
    onExitClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onNextClick: () -> Unit,
    onPrevClick: () -> Unit,
    onSpeakText: (String) -> Unit
) {
    val currentWord = state.introWords[state.currentIntroWordIdx]
    val showRomanizationOnly = LocalShowRomanizationOnly.current
    val totalWords = state.introWords.size
    val progressFraction = (state.currentIntroWordIdx + 1).toFloat() / totalWords.toFloat()

    val isTimerVisible = com.example.LocalActiveTimerVisible.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(if (isTimerVisible) Modifier else Modifier.statusBarsPadding())
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
                color = GemCyan,
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
            )

            Text(
                text = "${state.currentIntroWordIdx + 1}/$totalWords",
                fontWeight = FontWeight.Black,
                fontSize = 15.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
            )
        }

        // MAIN CONTENT CONTAINER
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header Badge
            Text(
                text = if (state.lesson.completed) "VOCABULARY" else "NEW WORD",
                fontSize = 18.sp,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Black,
                color = Color(0xFFCE82FF), // Purple Duolingo style text
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Main Card housing the vocabulary details
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(width = 2.dp, color = GemCyan.copy(alpha = 0.25f), shape = RoundedCornerShape(24.dp)),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Thai word & Sound button displayed stacked to support longer words comfortably without overlapping
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val wordText = if (showRomanizationOnly) currentWord.romanization else currentWord.thai
                        Text(
                            text = wordText,
                            fontSize = if (wordText.length > 10) 32.sp else 42.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = DuoGreen,
                            textAlign = TextAlign.Center,
                            lineHeight = if (wordText.length > 10) 38.sp else 46.sp,
                            modifier = Modifier.padding(horizontal = 8.dp).then(copyOnLongPressModifier(wordText))
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        IconButton(
                            onClick = onVoiceClick,
                            modifier = Modifier
                                .size(48.dp)
                                .background(DuoGreen, shape = CircleShape)
                                .testTag("voice_word_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.VolumeUp,
                                contentDescription = "Listen voice pronunciation",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }

                    if (!showRomanizationOnly) {
                        Spacer(modifier = Modifier.height(12.dp))

                        // Transliteration / Romanization block
                        Text(
                            text = "/ ${currentWord.romanization} /",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            textAlign = TextAlign.Center
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    ThaiToneIndicatorRow(
                        thaiWord = currentWord.thai,
                        showLabel = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Divider
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                        thickness = 1.dp
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // English Translation
                    Text(
                        text = "ENGLISH MEANING",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = currentWord.english,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        modifier = copyOnLongPressModifier(currentWord.english)
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // Sample Context Sentence
                    val haptic = LocalHapticFeedback.current
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onSpeakText(currentWord.exampleThai)
                            },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                        shape = RoundedCornerShape(16.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalAlignment = Alignment.Start
                        ) {
                            Text(
                                text = "SAMPLE SENTENCE",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuoGreen,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )

                            Text(
                                text = currentWord.exampleThai,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = copyOnLongPressModifier(currentWord.exampleThai)
                            )

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = currentWord.exampleEnglish,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f),
                                modifier = copyOnLongPressModifier(currentWord.exampleEnglish)
                            )
                        }
                    }
                }
            }
        }

        // BOTTOM ACTION STRIP WITH NAVIGATION
        Surface(
            tonalElevation = 4.dp,
            shadowElevation = 8.dp,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (state.currentIntroWordIdx > 0) {
                    OutlinedButton(
                        onClick = onPrevClick,
                        modifier = Modifier
                            .size(56.dp)
                            .testTag("vocabulary_prev_btn"),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(0.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Previous Word",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.size(56.dp))
                }

                // Middle Text showing word index progress
                Text(
                    text = "${state.currentIntroWordIdx + 1} of $totalWords",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                )

                val isLastWord = state.currentIntroWordIdx + 1 == totalWords
                Button(
                    onClick = onNextClick,
                    modifier = Modifier
                        .size(56.dp)
                        .testTag("vocabulary_next_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = DuoGreen),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Icon(
                        imageVector = if (isLastWord) Icons.Default.PlayArrow else Icons.Default.ArrowForward,
                        contentDescription = if (isLastWord) "Start Exercises" else "Next Word",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TestResultScreen(
    lesson: Lesson,
    xpEarned: Int,
    exercises: List<Exercise> = emptyList(),
    incorrectExercises: List<Pair<Exercise, String>> = emptyList(),
    onDone: () -> Unit,
    onTryAgain: () -> Unit
) {
    val haptic = LocalHapticFeedback.current
    val totalNonPopQuiz = exercises.count { !it.isPopQuiz }
    val nonPopQuizMistakes = incorrectExercises.count { !it.first.isPopQuiz }
    val correctNonPopQuiz = totalNonPopQuiz - nonPopQuizMistakes
    val ratio = if (totalNonPopQuiz > 0) correctNonPopQuiz.toFloat() / totalNonPopQuiz else 1f
    
    val starsAwarded = when {
        ratio >= 1.0f -> 3
        ratio >= 0.8f -> 2
        ratio >= 0.6f -> 1
        else -> 0
    }
    val passed = starsAwarded >= 1

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (passed) {
            // PASS CASE
            Box(modifier = Modifier.size(140.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(color = LevelGold, radius = 54f, center = center)
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Trophy Success",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Topic Test Passed!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = DuoGreen,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Congratulations! You got a score of ${(ratio * 100).toInt()}% ($starsAwarded ${if (starsAwarded == 1) "Star" else "Stars"}). The next topic is now unlocked!",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            ThaiLingoMascot(
                expression = MascotExpression.HAPPY,
                customMessage = "Chai-Yo! 🎉 You did it! Excellent performance! You conquered the Topic Test with ${(ratio * 100).toInt()}% correct and unlocked the next topic! 🔑🔓✨",
                size = 94.dp,
                modifier = Modifier.testTag("test_result_mascot_pass")
            )

            Spacer(modifier = Modifier.height(20.dp))

            Card(
                modifier = Modifier.width(180.dp),
                colors = CardDefaults.cardColors(containerColor = GemCyan.copy(alpha = 0.1f))
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("RESULT", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = GemCyan)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("PASSED", fontSize = 22.sp, fontWeight = FontWeight.Black, color = GemCyan)
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDone()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("test_finished_done_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = DuoGreen),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Back to Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
        } else {
            // FAIL CASE
            Box(modifier = Modifier.size(140.dp)) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(color = HeartRed, radius = 54f, center = center)
                }
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.ErrorOutline,
                        contentDescription = "Test Failed",
                        tint = Color.White,
                        modifier = Modifier.size(64.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "Topic Test Failed!",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = HeartRed,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Your score was ${(ratio * 100).toInt()}% (0 Stars). To unlock the next topic, you must achieve at least 1 star (60% correct).",
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            ThaiLingoMascot(
                expression = MascotExpression.SAD,
                customMessage = "Mai pen rai! ❤️ Mistake review is where real learning happens. Keep up the high energy and you'll crush it on the next try. Let's practice! 💪",
                size = 94.dp,
                modifier = Modifier.testTag("test_result_mascot_fail")
            )

            Spacer(modifier = Modifier.height(20.dp))

            if (incorrectExercises.isNotEmpty()) {
                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = "Review Your Mistakes",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.align(Alignment.Start)
                )

                Spacer(modifier = Modifier.height(12.dp))

                incorrectExercises.forEach { (exercise, userAns) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = exercise.prompt,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = exercise.question,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Your answer: $userAns",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                            )
                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Correct: ${exercise.correctAnswer.replace("|", " ")}",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = DuoGreen
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onTryAgain()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("test_try_again_btn"),
                colors = ButtonDefaults.buttonColors(containerColor = DuoGreen),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Try Again", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onDone()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("test_fail_back_btn"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text("Back to Dashboard", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun copyOnLongPressModifier(text: String): Modifier {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    return Modifier.pointerInput(text) {
        detectTapGestures(
            onLongPress = {
                clipboardManager.setText(AnnotatedString(text))
                Toast.makeText(context, "Copied to clipboard!", Toast.LENGTH_SHORT).show()
            }
        )
    }
}

