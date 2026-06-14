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
        val testHasMistakes: Boolean = false
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
                    when (lessonId) {
                        1 -> allVocab.filter { it.id in 1..5 }
                        2 -> allVocab.filter { it.id in 6..10 }
                        3 -> allVocab.filter { it.id in 11..16 }
                        4 -> allVocab.filter { it.id in 17..22 }
                        5 -> allVocab.filter { it.id in 23..28 }
                        6 -> allVocab.filter { it.id in 29..34 }
                        7 -> allVocab.filter { it.id in 35..39 }
                        8 -> allVocab.filter { it.id in 40..44 }
                        9 -> allVocab.filter { it.id in 45..48 }
                        10 -> allVocab.filter { it.id in 49..52 }
                        else -> emptyList()
                    }
                }

                val exercises = if (isTopicTest) {
                    val topicCategory = when (lessonId) {
                        101 -> "Greetings"
                        102 -> "Food"
                        103 -> "Numbers"
                        104 -> "Travel"
                        105 -> "Family"
                        else -> "Greetings"
                    }
                    val topicVocab = allVocab.filter { it.category.equals(topicCategory, ignoreCase = true) }
                    generateTestExercises(topicVocab, topicCategory, allVocab)
                } else {
                    repository.getExercisesForLesson(lessonId)
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
        
        // Random assortment of MULTIPLE_CHOICE, TRANSLATE, LISTENING for 18 questions
        val nonMatchingTypes = listOf(ExerciseType.MULTIPLE_CHOICE, ExerciseType.TRANSLATE, ExerciseType.LISTENING)
        
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

                val expectedCount = currentExercise.correctAnswer.split("|").size
                val allMatchedNow = nextMatched.size == expectedCount

                _uiState.value = currentState.copy(
                    selectedEnglish = "",
                    selectedThai = "",
                    matchedPairs = nextMatched,
                    isMatchingCorrect = null,
                    checkActivePair = null,
                    isChecked = allMatchedNow,
                    isCorrect = if (allMatchedNow) true else currentState.isCorrect
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
            if (currentExercise.type != ExerciseType.MATCHING) {
                val isEnglishToThai = currentExercise.question.any { it in 'A'..'Z' || it in 'a'..'z' }
                val thaiWordForSrs = if (isEnglishToThai) currentExercise.correctAnswer else currentExercise.question
                repository.updateReviewWordSrs(thaiWordForSrs, isCorrect = isCorrect)
            }
        }

        _uiState.value = state.copy(
            isChecked = true,
            isCorrect = isCorrect,
            hearts = nextHearts,
            testHasMistakes = nextTestHasMistakes
        )
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
                            val nextUnlockLId = when (lessonId) {
                                101 -> 3 // Greetings -> Food
                                102 -> 5 // Food -> Numbers
                                103 -> 7 // Numbers -> Travel
                                104 -> 9 // Travel -> Family
                                else -> -1
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
                        val nextLId = when (lessonId) {
                            1 -> 2
                            2 -> 101
                            3 -> 4
                            4 -> 102
                            5 -> 6
                            6 -> 103
                            7 -> 8
                            8 -> 104
                            9 -> 10
                            10 -> 105
                            else -> lessonId + 1
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
                checkActivePair = null
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
