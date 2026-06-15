package com.example.feature

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.ServiceLocator
import com.example.domain.ThaiLingoRepository
import com.example.domain.Vocabulary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PracticeViewModel(
    private val repository: ThaiLingoRepository
) : ViewModel() {

    private val _studyVocabs = MutableStateFlow<List<Vocabulary>>(emptyList())
    val studyVocabs: StateFlow<List<Vocabulary>> = _studyVocabs.asStateFlow()

    private val _heartsRestored = MutableStateFlow(false)
    val heartsRestored: StateFlow<Boolean> = _heartsRestored.asStateFlow()

    private val _shopError = MutableStateFlow("")
    val shopError: StateFlow<String> = _shopError.asStateFlow()

    private val _showFlashcardAnswers = MutableStateFlow<Set<Int>>(emptySet())
    val showFlashcardAnswers: StateFlow<Set<Int>> = _showFlashcardAnswers.asStateFlow()

    private val _flashcardCount = MutableStateFlow(5)
    val flashcardCount: StateFlow<Int> = _flashcardCount.asStateFlow()

    private val _seenWordsCount = MutableStateFlow(0)
    val seenWordsCount: StateFlow<Int> = _seenWordsCount.asStateFlow()

    private val _seenVocabList = MutableStateFlow<List<Vocabulary>>(emptyList())
    val seenVocabList: StateFlow<List<Vocabulary>> = _seenVocabList.asStateFlow()

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(
                repository.getAllVocabulary(),
                repository.getAllReviewWords(),
                _flashcardCount
            ) { allVocab, reviewWords, flashcardCount ->
                Triple(allVocab, reviewWords, flashcardCount)
            }.collect { (allVocab, reviewWords, flashcardCount) ->
                try {
                    val seenThaiWords = reviewWords.map { it.thai }.toSet()
                    val filteredVocab = allVocab.filter { it.thai in seenThaiWords }

                    val finalFiltered = if (filteredVocab.isEmpty()) {
                        // Fallback to first few words if none are seen yet
                        allVocab.take(10)
                    } else {
                        filteredVocab
                    }

                    _seenWordsCount.value = finalFiltered.size
                    _seenVocabList.value = finalFiltered
                    val currentCount = flashcardCount.coerceIn(1, maxOf(1, finalFiltered.size))
                    _studyVocabs.value = finalFiltered.shuffled().take(currentCount)
                } catch (e: Exception) {
                    _studyVocabs.value = emptyList()
                }
            }
        }
    }

    fun updateFlashcardCount(count: Int) {
        _flashcardCount.value = count
    }

    fun loadPracticeVocabs() {
        // Handled reactively via flow combinations above
    }

    fun toggleFlashcardReveal(vocabId: Int) {
        val currentSet = _showFlashcardAnswers.value
        _showFlashcardAnswers.value = if (currentSet.contains(vocabId)) {
            currentSet - vocabId
        } else {
            currentSet + vocabId
        }
    }

    fun clearShopError() {
        _shopError.value = ""
    }

    fun clearHeartsRestored() {
        _heartsRestored.value = false
    }

    fun spendXpToRefillHearts() {
        viewModelScope.launch {
            val progress = repository.getUserProgressOnce()
            if (progress.hearts >= 5) {
                _shopError.value = "Hearts are already full!"
                return@launch
            }

            val updatedProgress = progress.copy(
                hearts = 5
            )
            repository.saveUserProgress(updatedProgress)
            _heartsRestored.value = true
        }
    }

    fun completeVocabReviewToRestoreHeart() {
        viewModelScope.launch {
            val progress = repository.getUserProgressOnce()
            val finalHearts = (progress.hearts + 1).coerceAtMost(5)
            val updatedProgress = progress.copy(hearts = finalHearts)
            repository.saveUserProgress(updatedProgress)
            _heartsRestored.value = true
            loadPracticeVocabs() // Pull new set
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(PracticeViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return PracticeViewModel(ServiceLocator.getRepository(context)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
