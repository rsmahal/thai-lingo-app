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
        val currentSubStep: Int = 0, // 0 = Thai -> English, 1 = English -> Thai
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
    private var currentWordMissed: Boolean = false

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
        
        currentWordMissed = false
        _uiState.value = state.copy(
            currentStep = 0,
            currentSubStep = 0,
            xpEarned = 0,
            correctCount = 0
        )
        generateOptionsForStep(0, 0)
    }

    private fun generateOptionsForStep(step: Int, subStep: Int) {
        val state = _uiState.value as? ReviewUiState.Active ?: return
        if (step < 0 || step >= state.reviewQueue.size) return

        val correctWord = state.reviewQueue[step]
        val finalOptions = if (subStep == 0) {
            // Thai -> English (Eng options)
            val correctTranslation = correctWord.english
            val distractors = allVocabularyList
                .filter { it.english != correctTranslation }
                .map { it.english }
                .distinct()
                .shuffled()
                .take(3)
            (distractors + correctTranslation).shuffled()
        } else {
            // English -> Thai (Thai options)
            val correctTranslation = correctWord.thai
            val distractors = allVocabularyList
                .filter { it.thai != correctTranslation }
                .map { it.thai }
                .distinct()
                .shuffled()
                .take(3)
            (distractors + correctTranslation).shuffled()
        }

        _uiState.value = state.copy(
            currentStep = step,
            currentSubStep = subStep,
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
        val isCorrect = if (state.currentSubStep == 0) {
            state.selectedOption == correctWord.english
        } else {
            state.selectedOption == correctWord.thai
        }

        if (!isCorrect) {
            currentWordMissed = true
        }

        _uiState.value = state.copy(
            isChecking = true,
            isCorrect = isCorrect
        )
    }

    fun continueToNext() {
        val state = _uiState.value as? ReviewUiState.Active ?: return
        
        if (state.currentSubStep == 0) {
            // Proceed to the English -> Thai portion of the same word
            generateOptionsForStep(state.currentStep, 1)
        } else {
            // Completed English -> Thai portion.
            // Persist overall word SRS status based on whether either was missed.
            val correctWord = state.reviewQueue[state.currentStep]
            val wasWordSuccessful = !currentWordMissed
            
            viewModelScope.launch {
                repository.updateReviewWordSrs(correctWord.thai, isCorrect = wasWordSuccessful)
            }

            val nextStep = state.currentStep + 1
            currentWordMissed = false // Reset for safe usage on the next word

            val newCorrectCount = if (wasWordSuccessful) state.correctCount + 1 else state.correctCount

            if (nextStep >= state.reviewQueue.size) {
                // Finish quiz! Earn XP proportional to correct reviews (3 XP per successfully completed word)
                val xpGain = newCorrectCount * 3
                _uiState.value = state.copy(
                    currentStep = state.reviewQueue.size, // Completion screen
                    currentSubStep = 0,
                    correctCount = newCorrectCount,
                    xpEarned = xpGain
                )

                viewModelScope.launch {
                    if (xpGain > 0) {
                        val progress = repository.getUserProgressOnce()
                        repository.saveUserProgress(progress.copy(xp = progress.xp + xpGain))
                    }
                }
            } else {
                _uiState.value = state.copy(
                    correctCount = newCorrectCount
                )
                generateOptionsForStep(nextStep, 0)
            }
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
