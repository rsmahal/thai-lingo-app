package com.example.feature

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.ServiceLocator
import com.example.core.common.ThaiTtsHelper
import com.example.domain.Exercise
import com.example.domain.ExerciseType
import com.example.domain.Lesson
import com.example.domain.ThaiLingoRepository
import com.example.domain.UserProgress
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.example.domain.Vocabulary
import com.example.domain.getLessonVocabIdsRange
import com.example.domain.getTopicTestVocabIdsRange

sealed interface LessonUiState {
    object Loading : LessonUiState
    data class Error(val message: String) : LessonUiState
    data class Playing(
        val lesson: Lesson,
        val exercises: List<Exercise>,
        val currentStep: Int, // 0 to exercises.size - 1
        val hearts: Int,
        val selectedOption: String = "",
        val typedAnswer: String = "",
        val isChecked: Boolean = false,
        val isCorrect: Boolean = false,
        // สำหรับ MATCHING type
        val selectedEnglish: String = "",
        val selectedThai: String = "",
        val matchedPairs: Set<String> = emptySet(), // Ex. "สวัสดี:Hello / Goodbye"
        val isMatchingCorrect: Boolean? = null,
        val checkActivePair: Pair<String, String>? = null,
        val isSpeakingSimulated: Boolean = false,
        val isLessonFinished: Boolean = false,
        val xpEarned: Int = 0,
        val isIntroducing: Boolean = true,
        val introWords: List<Vocabulary> = emptyList(),
        val currentIntroWordIdx: Int = 0,
        val isTopicTest: Boolean = false,
        val testHasMistakes: Boolean = false,
        val matchingHadMistake: Boolean = false,
        val matchingIncorrectAttempts: Int = 0,
        val incorrectExercises: List<Pair<Exercise, String>> = emptyList()
    ) : LessonUiState
}

class LessonViewModel(
    private val lessonId: Int,
    private val repository: ThaiLingoRepository,
    private val ttsHelper: ThaiTtsHelper
) : ViewModel() {

    private val _uiState = MutableStateFlow<LessonUiState>(LessonUiState.Loading)
    val uiState: StateFlow<LessonUiState> = _uiState.asStateFlow()

    init {
        loadLesson()
    }

    fun restartSession() {
        _uiState.value = LessonUiState.Loading
        loadLesson()
    }

    private fun loadLesson() {
        viewModelScope.launch {
            try {
                val isTopicTest = lessonId >= 100
                val lesson = repository.getLessonById(lessonId)
                val progress = repository.getUserProgressOnce()
                val allVocab = repository.getAllVocabulary().first()
                
                // Load specific vocabulary for the lesson to introduce them at the start
                val lessonVocab = if (isTopicTest) {
                    emptyList()
                } else {
                    val range = getLessonVocabIdsRange(lessonId)
                    allVocab.filter { it.id in range }
                }

                val exercises = if (isTopicTest) {
                    val ranges = getTopicTestVocabIdsRange(lessonId)
                    val topicVocab = allVocab.filter { vocab ->
                        ranges.any { r -> vocab.id in r }
                    }
                    generateTestExercises(topicVocab, "", allVocab)
                } else {
                    val dbExercises = repository.getExercisesForLesson(lessonId)
                    val fixedMatching = dbExercises.firstOrNull { it.type == ExerciseType.MATCHING }
                    val fixedSentences = dbExercises.filter { it.type == ExerciseType.SENTENCE_BUILD }
                    
                    val vocabExercises = generateVocabExercises(lessonVocab, allVocab)
                    
                    val combined = mutableListOf<Exercise>()
                    combined.addAll(vocabExercises)
                    if (fixedMatching != null) {
                        combined.add(fixedMatching)
                    } else {
                        // Fallback matching
                        val pairingWords = lessonVocab.shuffled().take(minOf(4, lessonVocab.size))
                        if (pairingWords.isNotEmpty()) {
                            val pairingCorrectAnswer = pairingWords.joinToString("|") { "${it.thai}=${it.english}" }
                            val pairingOptions = pairingWords.flatMap { listOf(it.thai, it.english) }.shuffled()
                            combined.add(Exercise(
                                id = lessonId * 100 + 4,
                                lessonId = lessonId,
                                type = ExerciseType.MATCHING,
                                prompt = "Tap the matching English and Thai pairs:",
                                question = "Match vocabulary",
                                correctAnswer = pairingCorrectAnswer,
                                romanization = "",
                                options = pairingOptions,
                                audioText = ""
                            ))
                        }
                    }
                    combined.addAll(fixedSentences)
                    combined.shuffled()
                }
                
                if (lesson == null || exercises.isEmpty()) {
                    _uiState.value = LessonUiState.Error("Lesson not found or empty.")
                } else {
                    _uiState.value = LessonUiState.Playing(
                        lesson = lesson,
                        exercises = exercises,
                        currentStep = 0,
                        hearts = if (isTopicTest) 5 else progress.hearts,
                        isIntroducing = lessonVocab.isNotEmpty(),
                        introWords = lessonVocab,
                        currentIntroWordIdx = 0,
                        isTopicTest = isTopicTest,
                        testHasMistakes = false
                    )
                    // Auto-speak the first vocabulary word if in introduction mode
                    if (lessonVocab.isNotEmpty()) {
                        ttsHelper.speak(lessonVocab[0].thai)
                    } else {
                        speakCurrentIfListening(exercises[0])
                    }
                }
            } catch (e: Exception) {
                _uiState.value = LessonUiState.Error("Error loading lesson: ${e.localizedMessage}")
            }
        }
    }

    private fun generateTestExercises(topicVocab: List<Vocabulary>, categoryString: String, allVocab: List<Vocabulary>): List<Exercise> {
        val exercisesList = mutableListOf<Exercise>()
        
        // Exactly 2 pairing questions (MATCHING type)
        repeat(2) { index ->
            val pairingWords = topicVocab.shuffled().take(minOf(4, topicVocab.size))
            if (pairingWords.isNotEmpty()) {
                val pairingCorrectAnswer = pairingWords.joinToString("|") { "${it.thai}=${it.english}" }
                val pairingOptions = pairingWords.flatMap { listOf(it.thai, it.english) }.shuffled()
                exercisesList.add(Exercise(
                    id = 9000 + index,
                    lessonId = lessonId,
                    type = ExerciseType.MATCHING,
                    prompt = "Tap the matching English and Thai pairs:",
                    question = "Match vocabulary",
                    correctAnswer = pairingCorrectAnswer,
                    romanization = "",
                    options = pairingOptions,
                    audioText = ""
                ))
            }
        }
        
        // Random assortment of MULTIPLE_CHOICE, LISTENING for 18 questions
        val nonMatchingTypes = listOf(ExerciseType.MULTIPLE_CHOICE, ExerciseType.LISTENING)
        
        if (topicVocab.isNotEmpty()) {
            repeat(18) { index ->
                val vocabWord = topicVocab.random()
                val type = nonMatchingTypes.random()
                
                when (type) {
                    ExerciseType.MULTIPLE_CHOICE -> {
                        val isEngQuestion = listOf(true, false).random()
                        if (isEngQuestion) {
                            val otherThais = allVocab.filter { it.id != vocabWord.id }
                                .map { it.thai }
                                .distinct()
                                .shuffled()
                                .take(3)
                            val options = (otherThais + vocabWord.thai).shuffled()
                            exercisesList.add(Exercise(
                                id = 9100 + index,
                                lessonId = lessonId,
                                type = ExerciseType.MULTIPLE_CHOICE,
                                prompt = "Select the correct Thai translation for this English word:",
                                question = vocabWord.english,
                                correctAnswer = vocabWord.thai,
                                romanization = "",
                                options = options,
                                audioText = vocabWord.thai
                            ))
                        } else {
                            val otherEnglishes = allVocab.filter { it.id != vocabWord.id }
                                .map { it.english }
                                .distinct()
                                .shuffled()
                                .take(3)
                            val options = (otherEnglishes + vocabWord.english).shuffled()
                            exercisesList.add(Exercise(
                                id = 9200 + index,
                                lessonId = lessonId,
                                type = ExerciseType.MULTIPLE_CHOICE,
                                prompt = "What is the English meaning of this Thai word?",
                                question = vocabWord.thai,
                                correctAnswer = vocabWord.english,
                                romanization = vocabWord.romanization,
                                options = options,
                                audioText = vocabWord.thai
                            ))
                        }
                    }
                    ExerciseType.LISTENING -> {
                        val otherEnglishes = allVocab.filter { it.id != vocabWord.id }
                            .map { it.english }
                            .distinct()
                            .shuffled()
                            .take(3)
                        val options = (otherEnglishes + vocabWord.english).shuffled()
                        exercisesList.add(Exercise(
                            id = 9300 + index,
                            lessonId = lessonId,
                            type = ExerciseType.LISTENING,
                            prompt = "Listen and select the correct English translation:",
                            question = vocabWord.thai,
                            correctAnswer = vocabWord.english,
                            romanization = "",
                            options = options,
                            audioText = vocabWord.thai
                        ))
                    }
                    ExerciseType.TRANSLATE -> {
                        exercisesList.add(Exercise(
                            id = 9400 + index,
                            lessonId = lessonId,
                            type = ExerciseType.TRANSLATE,
                            prompt = "Type the English translation for this Thai word:",
                            question = vocabWord.thai,
                            correctAnswer = vocabWord.english,
                            romanization = vocabWord.romanization,
                            options = emptyList(),
                            audioText = vocabWord.thai
                        ))
                    }
                    else -> {}
                }
            }
        }
        
        return exercisesList.shuffled()
    }

    private fun generateVocabExercises(
        lessonVocab: List<Vocabulary>,
        allVocab: List<Vocabulary>
    ): List<Exercise> {
        val list = mutableListOf<Exercise>()
        var exerciseIdCounter = 10000
        var listeningCount = 0

        lessonVocab.forEach { v ->
            val possibleTypes = mutableListOf("EN_TO_TH_MC", "TH_TO_EN_MC")
            if (listeningCount < 8) {
                possibleTypes.add("LISTENING")
            }
            val selectedTypes = possibleTypes.shuffled().take(2)
            if (selectedTypes.contains("LISTENING")) {
                listeningCount++
            }

            selectedTypes.forEach { type ->
                when (type) {
                    "EN_TO_TH_MC" -> {
                        val otherThais = allVocab.filter { it.id != v.id }
                            .map { it.thai }
                            .distinct()
                            .shuffled()
                            .take(3)
                        val options = (otherThais + v.thai).shuffled()
                        list.add(Exercise(
                            id = exerciseIdCounter++,
                            lessonId = lessonId,
                            type = ExerciseType.MULTIPLE_CHOICE,
                            prompt = "Select the correct Thai translation for this English word:",
                            question = v.english,
                            correctAnswer = v.thai,
                            romanization = "",
                            options = options,
                            audioText = v.thai
                        ))
                    }
                    "TH_TO_EN_MC" -> {
                        val otherEnglishes = allVocab.filter { it.id != v.id }
                            .map { it.english }
                            .distinct()
                            .shuffled()
                            .take(3)
                        val options = (otherEnglishes + v.english).shuffled()
                        list.add(Exercise(
                            id = exerciseIdCounter++,
                            lessonId = lessonId,
                            type = ExerciseType.MULTIPLE_CHOICE,
                            prompt = "What is the English meaning of this Thai word?",
                            question = v.thai,
                            correctAnswer = v.english,
                            romanization = v.romanization,
                            options = options,
                            audioText = v.thai
                        ))
                    }
                    "LISTENING" -> {
                        val otherEnglishes = allVocab.filter { it.id != v.id }
                            .map { it.english }
                            .distinct()
                            .shuffled()
                            .take(3)
                        val options = (otherEnglishes + v.english).shuffled()
                        list.add(Exercise(
                            id = exerciseIdCounter++,
                            lessonId = lessonId,
                            type = ExerciseType.LISTENING,
                            prompt = "Listen and select the correct English translation:",
                            question = v.thai,
                            correctAnswer = v.english,
                            romanization = "",
                            options = options,
                            audioText = v.thai
                        ))
                    }
                }
            }
        }
        return list
    }

    fun speakWordText(text: String) {
        ttsHelper.speak(text)
    }

    fun speakIntroWord() {
        val state = _uiState.value as? LessonUiState.Playing ?: return
        if (state.currentIntroWordIdx in state.introWords.indices) {
            val word = state.introWords[state.currentIntroWordIdx]
            ttsHelper.speak(word.thai)
        }
    }

    fun nextIntroWord() {
        val state = _uiState.value as? LessonUiState.Playing ?: return
        val nextIdx = state.currentIntroWordIdx + 1
        if (nextIdx < state.introWords.size) {
            _uiState.value = state.copy(currentIntroWordIdx = nextIdx)
            speakWordText(state.introWords[nextIdx].thai)
        } else {
            _uiState.value = state.copy(isIntroducing = false)
            if (state.exercises.isNotEmpty()) {
                speakCurrentIfListening(state.exercises[0])
            }
        }
    }

    fun prevIntroWord() {
        val state = _uiState.value as? LessonUiState.Playing ?: return
        val prevIdx = state.currentIntroWordIdx - 1
        if (prevIdx >= 0) {
            _uiState.value = state.copy(currentIntroWordIdx = prevIdx)
            speakWordText(state.introWords[prevIdx].thai)
        }
    }

    fun speakCurrentText() {
        val state = _uiState.value as? LessonUiState.Playing ?: return
        val currentExercise = state.exercises[state.currentStep]
        if (currentExercise.audioText.isNotEmpty()) {
            ttsHelper.speak(currentExercise.audioText)
        }
    }

    private fun speakCurrentIfListening(exercise: Exercise) {
        if (exercise.type == ExerciseType.LISTENING && exercise.audioText.isNotEmpty()) {
            ttsHelper.speak(exercise.audioText)
        } else if (exercise.type != ExerciseType.MATCHING && exercise.question.any { it in '\u0E00'..'\u0E7F' }) {
            // Auto play Thai audio when the question contains Thai text 
            ttsHelper.speak(exercise.question)
        }
    }

    fun selectOption(option: String) {
        val state = _uiState.value as? LessonUiState.Playing ?: return
        if (state.isChecked) return
        _uiState.value = state.copy(selectedOption = option)
    }

    fun updateTypedAnswer(text: String) {
        val state = _uiState.value as? LessonUiState.Playing ?: return
        if (state.isChecked) return
        _uiState.value = state.copy(typedAnswer = text)
    }

    fun selectMatchingItem(content: String, isEnglish: Boolean) {
        val state = _uiState.value as? LessonUiState.Playing ?: return
        if (state.isChecked) return
        if (state.checkActivePair != null) return // Ignore interaction during animation

        var nextEng = state.selectedEnglish
        var nextThai = state.selectedThai

        if (isEnglish) {
            if (state.matchedPairs.any { it.endsWith(":$content") }) return
            if (nextEng == content) {
                _uiState.value = state.copy(selectedEnglish = "")
                return
            }
            nextEng = content
        } else {
            if (state.matchedPairs.any { it.startsWith("$content:") }) return
            if (nextThai == content) {
                _uiState.value = state.copy(selectedThai = "")
                return
            }
            nextThai = content
        }

        _uiState.value = state.copy(
            selectedEnglish = nextEng,
            selectedThai = nextThai
        )

        // If both options are selected, check the pair
        if (nextEng.isNotEmpty() && nextThai.isNotEmpty()) {
            val currentExercise = state.exercises[state.currentStep]
            val validPairs = currentExercise.correctAnswer.split("|").associate {
                val parts = it.split("=")
                parts[0] to parts[1]
            }

            val isCorrectMatch = validPairs[nextThai] == nextEng

            _uiState.value = (_uiState.value as LessonUiState.Playing).copy(
                isMatchingCorrect = isCorrectMatch,
                checkActivePair = Pair(nextThai, nextEng)
            )

            viewModelScope.launch {
                kotlinx.coroutines.delay(800) // Brief glowing duration

                val currentState = _uiState.value as? LessonUiState.Playing ?: return@launch
                val nextMatched = currentState.matchedPairs.toMutableSet()

                if (isCorrectMatch) {
                    nextMatched.add("$nextThai:$nextEng")
                    repository.updateReviewWordSrs(nextThai, isCorrect = true)
                } else {
                    repository.updateReviewWordSrs(nextThai, isCorrect = false)
                }

                var currentHearts = currentState.hearts
                var nextTestHasMistakes = currentState.testHasMistakes
                val mismatchHappened = !isCorrectMatch

                if (mismatchHappened) {
                    currentHearts = (currentHearts - 1).coerceAtLeast(0)
                    nextTestHasMistakes = true
                    viewModelScope.launch {
                        try {
                            val progress = repository.getUserProgressOnce()
                            repository.saveUserProgress(progress.copy(hearts = currentHearts))
                        } catch (e: Exception) {
                            // Safe save skip
                        }
                    }
                }

                val nextMatchingHadMistake = currentState.matchingHadMistake || mismatchHappened
                val nextIncorrectAttempts = currentState.matchingIncorrectAttempts + if (mismatchHappened) 1 else 0
                val expectedCount = currentExercise.correctAnswer.split("|").size
                val allMatchedNow = nextMatched.size == expectedCount

                val nextIncorrectExs = if (mismatchHappened) {
                    val alreadyAdded = currentState.incorrectExercises.any { it.first.id == currentExercise.id }
                    if (!alreadyAdded) {
                        currentState.incorrectExercises + (currentExercise to "Mismatching pairs tapped")
                    } else {
                        currentState.incorrectExercises
                    }
                } else {
                    currentState.incorrectExercises
                }

                _uiState.value = currentState.copy(
                    selectedEnglish = "",
                    selectedThai = "",
                    matchedPairs = nextMatched,
                    isMatchingCorrect = null,
                    checkActivePair = null,
                    isChecked = allMatchedNow,
                    isCorrect = if (allMatchedNow) !nextMatchingHadMistake else false,
                    hearts = currentHearts,
                    testHasMistakes = nextTestHasMistakes,
                    matchingHadMistake = nextMatchingHadMistake,
                    matchingIncorrectAttempts = nextIncorrectAttempts,
                    incorrectExercises = nextIncorrectExs
                )
            }
        }
    }

    fun simulateMicSpeaking() {
        val state = _uiState.value as? LessonUiState.Playing ?: return
        if (state.isChecked) return
        
        viewModelScope.launch {
            _uiState.value = state.copy(isSpeakingSimulated = true)
            // Hold drawing animation to mimic real live audio sampling
            kotlinx.coroutines.delay(2000)
            val updatedState = _uiState.value as? LessonUiState.Playing ?: return@launch
            _uiState.value = updatedState.copy(
                isSpeakingSimulated = false,
                typedAnswer = updatedState.exercises[updatedState.currentStep].correctAnswer // Lock correct simulated output
            )
        }
    }

    fun checkAnswer() {
        val state = _uiState.value as? LessonUiState.Playing ?: return
        if (state.isChecked) return

        val currentExercise = state.exercises[state.currentStep]
        var isCorrect = false

        when (currentExercise.type) {
            ExerciseType.MULTIPLE_CHOICE, ExerciseType.LISTENING -> {
                isCorrect = state.selectedOption.equals(currentExercise.correctAnswer, ignoreCase = true)
            }
            ExerciseType.TRANSLATE, ExerciseType.SPEAKING -> {
                val formattedInput = state.typedAnswer.trim().lowercase().removeSuffix(".").removeSuffix(",")
                val formattedAns = currentExercise.correctAnswer.trim().lowercase().removeSuffix(".").removeSuffix(",")
                isCorrect = formattedInput == formattedAns || formattedInput.contains(formattedAns) || formattedAns.contains(formattedInput)
            }
            ExerciseType.MATCHING -> {
                // Valid targets are loaded in currentExercise.correctAnswer format "Thai=Eng|..."
                val expectedCount = currentExercise.correctAnswer.split("|").size
                isCorrect = state.matchedPairs.size == expectedCount
            }
            ExerciseType.SENTENCE_BUILD -> {
                val formattedInput = state.typedAnswer.trim().lowercase()
                val formattedAns = currentExercise.correctAnswer.trim().lowercase()
                isCorrect = formattedInput == formattedAns
            }
        }

        var nextHearts = state.hearts
        if (!isCorrect) {
            nextHearts = (nextHearts - 1).coerceAtLeast(0)
        }

        val nextTestHasMistakes = state.testHasMistakes || !isCorrect

        viewModelScope.launch {
            if (!isCorrect) {
                val progress = repository.getUserProgressOnce()
                repository.saveUserProgress(progress.copy(hearts = nextHearts))
            }
            if (currentExercise.type != ExerciseType.MATCHING && currentExercise.type != ExerciseType.SENTENCE_BUILD) {
                val isEnglishToThai = currentExercise.question.any { it in 'A'..'Z' || it in 'a'..'z' }
                val thaiWordForSrs = if (isEnglishToThai) currentExercise.correctAnswer else currentExercise.question
                repository.updateReviewWordSrs(thaiWordForSrs, isCorrect = isCorrect)
            }
        }

        val userAns = when (currentExercise.type) {
            ExerciseType.MULTIPLE_CHOICE, ExerciseType.LISTENING -> if (state.selectedOption.isNotBlank()) state.selectedOption else "No option selected"
            ExerciseType.TRANSLATE, ExerciseType.SPEAKING -> state.typedAnswer
            ExerciseType.SENTENCE_BUILD -> state.typedAnswer.replace("|", " ")
            ExerciseType.MATCHING -> "Mismatching pairs tapped"
        }

        val nextIncorrectExercises = if (!isCorrect) {
            val alreadyAdded = state.incorrectExercises.any { it.first.id == currentExercise.id }
            if (!alreadyAdded) {
                state.incorrectExercises + (currentExercise to userAns)
            } else {
                state.incorrectExercises
            }
        } else {
            state.incorrectExercises
        }

        _uiState.value = state.copy(
            isChecked = true,
            isCorrect = isCorrect,
            hearts = nextHearts,
            testHasMistakes = nextTestHasMistakes,
            incorrectExercises = nextIncorrectExercises
        )

        // Play Thai sound on result for English -> Thai translation (Correct or Incorrect)
        if (currentExercise.audioText.any { it in '\u0E00'..'\u0E7F' }) {
            ttsHelper.speak(currentExercise.audioText)
        } else if (currentExercise.correctAnswer.any { it in '\u0E00'..'\u0E7F' } && currentExercise.type != ExerciseType.MATCHING) {
            ttsHelper.speak(currentExercise.correctAnswer.replace("|", " "))
        }
    }

    fun continueToNext() {
        val state = _uiState.value as? LessonUiState.Playing ?: return
        if (!state.isChecked) return

        if (state.hearts <= 0) {
            // Out of hearts visual fallback
            _uiState.value = state.copy(isLessonFinished = true, xpEarned = 0)
            return
        }

        val nextStep = state.currentStep + 1
        if (nextStep >= state.exercises.size) {
            // Finish Lesson! Award XP and mark lesson complete
            viewModelScope.launch {
                try {
                    val progress = repository.getUserProgressOnce()
                    val xpReward = state.lesson.xpReward
                    val starsAwarded = state.hearts.coerceIn(1, 3)
                    
                    if (state.isTopicTest) {
                        val passed = !state.testHasMistakes
                        if (passed) {
                            // Mark test completed
                            val updatedLesson = state.lesson.copy(completed = true, stars = 3)
                            repository.updateLesson(updatedLesson)
                            
                            // Unlock first lesson of next topic
                            val nextUnlockLId = if (lessonId in 101..112) {
                                (lessonId - 100) * 4 + 1
                            } else {
                                -1
                            }
                            var unlockedNextLesson: Lesson? = null
                            if (nextUnlockLId != -1) {
                                val nextLesson = repository.getLessonById(nextUnlockLId)
                                if (nextLesson != null) {
                                    val updatedNext = nextLesson.copy(unlocked = true)
                                    repository.updateLesson(updatedNext)
                                    unlockedNextLesson = updatedNext
                                }
                            }
                            
                            // Award XP and update progress
                            val finalProgress = progress.copy(
                                xp = progress.xp + xpReward,
                                hearts = 5, // fill hearts on pass!
                                currentLessonId = if (unlockedNextLesson != null) unlockedNextLesson.id else lessonId
                            )
                            repository.saveUserProgress(finalProgress)
                        } else {
                            // Failed the test. No database mark as complete.
                        }
                        
                        _uiState.value = state.copy(
                            currentStep = nextStep,
                            isLessonFinished = true,
                            xpEarned = if (passed) xpReward else 0
                        )
                    } else {
                        // Mark lesson completed
                        val updatedLesson = state.lesson.copy(completed = true, stars = maxOf(state.lesson.stars, starsAwarded))
                        repository.updateLesson(updatedLesson)
                        
                        // Unlock next custom lesson or Topic Test
                        val nextLId = if (lessonId % 4 == 0) {
                            100 + (lessonId / 4)
                        } else if (lessonId == 50) {
                            113
                        } else {
                            lessonId + 1
                        }
                        val nextLesson = repository.getLessonById(nextLId)
                        if (nextLesson != null) {
                            repository.updateLesson(nextLesson.copy(unlocked = true))
                        }

                        // Update User Profile Stats
                        val finalProgress = progress.copy(
                            xp = progress.xp + xpReward,
                            hearts = state.hearts,
                            currentLessonId = if (nextLesson != null) nextLId else lessonId
                        )
                        repository.saveUserProgress(finalProgress)

                        _uiState.value = state.copy(
                            currentStep = nextStep,
                            isLessonFinished = true,
                            xpEarned = xpReward
                        )
                    }
                } catch (e: Exception) {
                    _uiState.value = LessonUiState.Error("Fail completing lesson: ${e.localizedMessage}")
                }
            }
        } else {
            // Advance to next step
            val nextExercise = state.exercises[nextStep]
            _uiState.value = state.copy(
                currentStep = nextStep,
                selectedOption = "",
                typedAnswer = "",
                isChecked = false,
                isCorrect = false,
                selectedEnglish = "",
                selectedThai = "",
                matchedPairs = emptySet(),
                isMatchingCorrect = null,
                checkActivePair = null,
                matchingHadMistake = false,
                matchingIncorrectAttempts = 0
            )
            speakCurrentIfListening(nextExercise)
        }
    }

    class Factory(
        private val lessonId: Int,
        private val context: Context
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LessonViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return LessonViewModel(
                    lessonId,
                    ServiceLocator.getRepository(context),
                    ServiceLocator.getTtsHelper(context)
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
