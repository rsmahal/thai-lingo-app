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
import kotlinx.coroutines.launch

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
        val xpEarned: Int = 0
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

    private fun loadLesson() {
        viewModelScope.launch {
            try {
                val lesson = repository.getLessonById(lessonId)
                val exercises = repository.getExercisesForLesson(lessonId)
                val progress = repository.getUserProgressOnce()
                
                if (lesson == null || exercises.isEmpty()) {
                    _uiState.value = LessonUiState.Error("Lesson not found or empty.")
                } else {
                    _uiState.value = LessonUiState.Playing(
                        lesson = lesson,
                        exercises = exercises,
                        currentStep = 0,
                        hearts = progress.hearts
                    )
                    // Auto-speak if the first exercise is listening
                    speakCurrentIfListening(exercises[0])
                }
            } catch (e: Exception) {
                _uiState.value = LessonUiState.Error("Error loading lesson: ${e.localizedMessage}")
            }
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
            hearts = nextHearts
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
                    val starsAwarded = state.hearts.coerceAtLeast(1)
                    
                    // Mark lesson completed
                    val updatedLesson = state.lesson.copy(completed = true, stars = starsAwarded)
                    repository.updateLesson(updatedLesson)
                    
                    // Unlock next lesson
                    val nextLId = lessonId + 1
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
