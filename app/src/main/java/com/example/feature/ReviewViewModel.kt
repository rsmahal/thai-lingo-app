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
    data class ReviewStep(
        val word: ReviewWord,
        val subStep: Int // 0 = Thai -> English, 1 = English -> Thai
    )

    private var activeQuizSteps: List<ReviewStep> = emptyList()
    private val missedWordThais = mutableSetOf<String>()

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
                reviewQueue = if (currentState.currentStep >= 0) currentState.reviewQueue else dueWords,
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

    private fun generateReviewSteps(words: List<ReviewWord>): List<ReviewStep> {
        if (words.isEmpty()) return emptyList()
        val steps = mutableListOf<ReviewStep>()
        if (words.size == 1) {
            steps.add(ReviewStep(words[0], 0))
            steps.add(ReviewStep(words[0], 1))
            return steps
        }
        
        if (words.size == 2) {
            return listOf(
                ReviewStep(words[0], 0),
                ReviewStep(words[1], 1),
                ReviewStep(words[0], 1),
                ReviewStep(words[1], 0)
            )
        }
        
        val baseSteps = mutableListOf<ReviewStep>()
        words.forEach { word ->
            baseSteps.add(ReviewStep(word, 0))
            baseSteps.add(ReviewStep(word, 1))
        }
        
        var shuffled = baseSteps.shuffled().toMutableList()
        var hasConsecutive = true
        var attempts = 0
        while (hasConsecutive && attempts < 500) {
            hasConsecutive = false
             for (i in 0 until shuffled.size - 1) {
                if (shuffled[i].word.thai == shuffled[i + 1].word.thai) {
                    hasConsecutive = true
                    val j = shuffled.indices.random()
                    val temp = shuffled[i + 1]
                    shuffled[i + 1] = shuffled[j]
                    shuffled[j] = temp
                }
            }
            if (!hasConsecutive) {
                return shuffled
            }
            attempts++
        }
        
        val list0 = words.map { ReviewStep(it, 0) }
        val list1 = words.map { ReviewStep(it, 1) }
        val result = mutableListOf<ReviewStep>()
        for (i in 0 until words.size) {
            result.add(list0[i])
        }
        for (i in 0 until words.size) {
            result.add(list1[(i + 1) % words.size])
        }
        return result
    }

    fun startQuiz() {
        val state = _uiState.value as? ReviewUiState.Active ?: return
        if (state.reviewQueue.isEmpty()) return
        
        missedWordThais.clear()
        val dueWords = state.reviewQueue
        activeQuizSteps = generateReviewSteps(dueWords)
        
        val mappedQueue = activeQuizSteps.map { it.word }
        
        _uiState.value = state.copy(
            reviewQueue = mappedQueue,
            currentStep = 0,
            currentSubStep = activeQuizSteps[0].subStep,
            xpEarned = 0,
            correctCount = 0
        )
        generateOptionsForStep(0, activeQuizSteps[0].subStep)
    }

    private fun generateOptionsForStep(step: Int, subStep: Int) {
        val state = _uiState.value as? ReviewUiState.Active ?: return
        if (step < 0 || step >= state.reviewQueue.size) return

        val correctWord = state.reviewQueue[step]
        val finalOptions = if (subStep == 0) {
            val correctTranslation = correctWord.english
            val distractors = allVocabularyList
                .filter { it.category.equals(correctWord.category, ignoreCase = true) && it.english != correctTranslation }
                .map { it.english }
                .distinct()
                .shuffled()
                .take(3)
            (distractors + correctTranslation).shuffled()
        } else {
            val correctTranslation = correctWord.thai
            val distractors = allVocabularyList
                .filter { it.category.equals(correctWord.category, ignoreCase = true) && it.thai != correctTranslation }
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
            missedWordThais.add(correctWord.thai)
        }

        _uiState.value = state.copy(
            isChecking = true,
            isCorrect = isCorrect
        )
    }

    fun continueToNext() {
        val state = _uiState.value as? ReviewUiState.Active ?: return
        
        val nextStep = state.currentStep + 1
        val wasCurrentCorrect = state.isCorrect ?: false
        val newCorrectCount = if (wasCurrentCorrect) state.correctCount + 1 else state.correctCount

        if (nextStep >= state.reviewQueue.size) {
            val uniqueWordsInQuiz = activeQuizSteps.map { it.word }.distinct()
            var successfulWordsCount = 0
            uniqueWordsInQuiz.forEach { word ->
                if (word.thai !in missedWordThais) {
                    successfulWordsCount++
                    viewModelScope.launch {
                        repository.updateReviewWordSrs(word.thai, isCorrect = true)
                    }
                } else {
                    viewModelScope.launch {
                        repository.updateReviewWordSrs(word.thai, isCorrect = false)
                    }
                }
            }

            val xpGain = successfulWordsCount * 3
            _uiState.value = state.copy(
                reviewQueue = uniqueWordsInQuiz,
                currentStep = uniqueWordsInQuiz.size,
                currentSubStep = 0,
                correctCount = successfulWordsCount,
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
            generateOptionsForStep(nextStep, activeQuizSteps[nextStep].subStep)
        }
    }

    fun quitOrFinishQuiz() {
        val currentState = _uiState.value as? ReviewUiState.Active
        if (currentState != null) {
            _uiState.value = currentState.copy(currentStep = -1)
        } else {
            _uiState.value = ReviewUiState.Loading
        }
        updateActiveState()
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
