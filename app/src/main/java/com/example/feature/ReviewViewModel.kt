package com.example.feature

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.ServiceLocator
import com.example.domain.ThaiLingoRepository
import com.example.domain.Vocabulary
import com.example.domain.ReviewWord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ReviewUiState {
    object Loading : ReviewUiState
    data class Active(
        val allReviewWords: List<ReviewWord> = emptyList(),
        val reviewQueue: List<ReviewWord> = emptyList(), // Only due review words
        val masteredCount: Int = 0,
        val isChecking: Boolean = false,
        val isCorrect: Boolean? = null,
        val selectedOption: String = "",
        val currentStep: Int = -1, // -1 means viewing main review dashboard, >= 0 means active quiz step
        val options: List<String> = emptyList(),
        val xpEarned: Int = 0,
        val correctCount: Int = 0,
        val timeOffsetDays: Int = 0 // Time offset to simulate spaced repetition days
    ) : ReviewUiState
}

class ReviewViewModel(
    private val repository: ThaiLingoRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow<ReviewUiState>(ReviewUiState.Loading)
    val uiState: StateFlow<ReviewUiState> = _uiState.asStateFlow()

    private var allVocabularyList: List<Vocabulary> = emptyList()
    private var internalReviewWords: List<ReviewWord> = emptyList()
    private var internalTimeOffsetDays: Int = 0

    init {
        viewModelScope.launch {
            // Load full vocabulary to use as distractor options in quiz
            repository.getAllVocabulary().collect { list ->
                allVocabularyList = list
            }
        }

        viewModelScope.launch {
            repository.getAllReviewWords().collect { words ->
                internalReviewWords = words
                updateActiveState()
            }
        }
    }

    private fun updateActiveState() {
        val now = System.currentTimeMillis() + internalTimeOffsetDays * 24 * 3600 * 1000L
        
        // Due reviews are those whose nextDueAt is less than or equal to now, and are not yet "fully mastered" or are due again
        // Wait, standard spaced repetition implies even mastered words can be due eventually (e.g. in 180 days),
        // but unreviewed or recently-forgotten words are always due.
        val dueWords = internalReviewWords.filter { it.nextDueAt <= now }
        val masteredCount = internalReviewWords.count { it.isMastered }

        val currentState = _uiState.value
        if (currentState is ReviewUiState.Active) {
            _uiState.value = currentState.copy(
                allReviewWords = internalReviewWords,
                reviewQueue = dueWords,
                masteredCount = masteredCount,
                timeOffsetDays = internalTimeOffsetDays
            )
        } else {
            _uiState.value = ReviewUiState.Active(
                allReviewWords = internalReviewWords,
                reviewQueue = dueWords,
                masteredCount = masteredCount,
                timeOffsetDays = internalTimeOffsetDays
            )
        }
    }

    // Advance simulated time forward by 1 day to test due date intervals
    fun simulateOneDayPass() {
        internalTimeOffsetDays += 1
        updateActiveState()
    }

    fun resetTimeOffset() {
        internalTimeOffsetDays = 0
        updateActiveState()
    }

    fun startQuiz() {
        val state = _uiState.value as? ReviewUiState.Active ?: return
        if (state.reviewQueue.isEmpty()) return
        
        _uiState.value = state.copy(
            currentStep = 0,
            xpEarned = 0,
            correctCount = 0
        )
        generateOptionsForStep(0)
    }

    private fun generateOptionsForStep(step: Int) {
        val state = _uiState.value as? ReviewUiState.Active ?: return
        if (step < 0 || step >= state.reviewQueue.size) return

        val correctWord = state.reviewQueue[step]
        val correctTranslation = correctWord.english

        // Distractor options from general vocabulary
        val distractors = allVocabularyList
            .filter { it.english != correctTranslation }
            .shuffled()
            .take(3)
            .map { it.english }

        val finalOptions = (distractors + correctTranslation).shuffled()

        _uiState.value = state.copy(
            currentStep = step,
            selectedOption = "",
            isChecking = false,
            isCorrect = null,
            options = finalOptions
        )
    }

    fun selectOption(option: String) {
        val state = _uiState.value as? ReviewUiState.Active ?: return
        if (state.isChecking) return
        _uiState.value = state.copy(selectedOption = option)
    }

    fun checkAnswer() {
        val state = _uiState.value as? ReviewUiState.Active ?: return
        if (state.selectedOption.isEmpty() || state.isChecking) return

        val correctWord = state.reviewQueue[state.currentStep]
        val isCorrect = state.selectedOption == correctWord.english

        _uiState.value = state.copy(
            isChecking = true,
            isCorrect = isCorrect,
            correctCount = if (isCorrect) state.correctCount + 1 else state.correctCount
        )

        viewModelScope.launch {
            repository.updateReviewWordSrs(correctWord.thai, isCorrect = isCorrect)
        }
    }

    fun continueToNext() {
        val state = _uiState.value as? ReviewUiState.Active ?: return
        val nextStep = state.currentStep + 1

        if (nextStep >= state.reviewQueue.size) {
            // Finish quiz! Earn XP proportional to correct reviews
            val xpGain = state.correctCount * 3
            _uiState.value = state.copy(
                currentStep = state.reviewQueue.size, // Completion screen
                xpEarned = xpGain
            )

            viewModelScope.launch {
                if (xpGain > 0) {
                    val progress = repository.getUserProgressOnce()
                    repository.saveUserProgress(progress.copy(xp = progress.xp + xpGain))
                }
            }
        } else {
            generateOptionsForStep(nextStep)
        }
    }

    fun quitOrFinishQuiz() {
        _uiState.value = ReviewUiState.Loading
        viewModelScope.launch {
            // Get fresh status
            updateActiveState()
            val state = _uiState.value as? ReviewUiState.Active
            if (state != null) {
                _uiState.value = state.copy(currentStep = -1)
            }
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(ReviewViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return ReviewViewModel(ServiceLocator.getRepository(context)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
