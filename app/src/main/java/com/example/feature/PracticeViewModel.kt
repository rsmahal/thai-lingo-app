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

    init {
        loadPracticeVocabs()
    }

    fun loadPracticeVocabs() {
        viewModelScope.launch {
            repository.getAllVocabulary().collect { list ->
                if (list.isNotEmpty()) {
                    // Pull 5 random vocab items for the practice workout
                    _studyVocabs.value = list.shuffled().take(5)
                }
            }
        }
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
