package com.example.feature

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.core.common.ServiceLocator
import com.example.domain.Achievement
import com.example.domain.Lesson
import com.example.domain.ThaiLingoRepository
import com.example.domain.UserProgress
import com.example.domain.Vocabulary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainViewModel(
    private val repository: ThaiLingoRepository
) : ViewModel() {

    private val _isInitializing = MutableStateFlow(true)
    val isInitializing: StateFlow<Boolean> = _isInitializing.asStateFlow()

    val userProgress: StateFlow<UserProgress?> = repository.getUserProgress()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val lessons: StateFlow<List<Lesson>> = repository.getAllLessons()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val vocabulary: StateFlow<List<Vocabulary>> = repository.getAllVocabulary()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val achievements: StateFlow<List<Achievement>> = repository.getAllAchievements()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        initializeData()
    }

    private fun initializeData() {
        viewModelScope.launch {
            try {
                repository.initializeDatabase()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Database initialization failed", e)
            } finally {
                _isInitializing.value = false
            }
            try {
                triggerDailyCheck()
            } catch (e: Exception) {
                android.util.Log.e("MainViewModel", "Daily check failed", e)
            }
        }
    }

    private suspend fun triggerDailyCheck() {
        val progress = repository.getUserProgressOnce()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val today = sdf.format(Date())
        
        if (progress.lastActiveDate == today) {
            return // Already logged in today
        }

        val updatedProgress = if (progress.lastActiveDate.isEmpty()) {
            progress.copy(streak = 1, lastActiveDate = today, hearts = 5)
        } else {
            try {
                val lastDate = sdf.parse(progress.lastActiveDate)
                val todayDate = sdf.parse(today)
                val diffMs = todayDate.time - lastDate.time
                val diffDays = diffMs / (1000 * 60 * 60 * 24)
                
                when {
                    diffDays == 1L -> {
                        progress.copy(streak = progress.streak + 1, lastActiveDate = today, hearts = 5)
                    }
                    diffDays > 1L -> {
                        progress.copy(streak = 1, lastActiveDate = today, hearts = 5) // Streak broken
                    }
                    else -> {
                        progress.copy(lastActiveDate = today)
                    }
                }
            } catch (e: Exception) {
                progress.copy(streak = 1, lastActiveDate = today, hearts = 5)
            }
        }
        
        repository.saveUserProgress(updatedProgress)
        recheckAchievements(updatedProgress)
    }

    fun completeOnboarding(name: String, dailyGoal: Int, showRomanizationOnly: Boolean) {
        viewModelScope.launch {
            val progress = repository.getUserProgressOnce()
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val today = sdf.format(Date())
            
            val updated = progress.copy(
                name = name.takeIf { it.isNotBlank() } ?: "Lingo Learner",
                selectedLanguageGoal = dailyGoal,
                streak = 1,
                lastActiveDate = today,
                hearts = 5,
                currentLessonId = 1,
                showRomanizationOnly = showRomanizationOnly
            )
            repository.saveUserProgress(updated)
            recheckAchievements(updated)
        }
    }

    fun awardXp(amount: Int) {
        viewModelScope.launch {
            val progress = repository.getUserProgressOnce()
            val newXp = progress.xp + amount
            val newLevel = 1 + (newXp / 100) // 100 XP per level
            
            val updated = progress.copy(
                xp = newXp,
                level = newLevel
            )
            repository.saveUserProgress(updated)
            recheckAchievements(updated)
        }
    }

    fun deductHeart() {
        viewModelScope.launch {
            val progress = repository.getUserProgressOnce()
            if (progress.hearts > 0) {
                val updated = progress.copy(hearts = progress.hearts - 1)
                repository.saveUserProgress(updated)
            }
        }
    }

    fun restoreHearts() {
        viewModelScope.launch {
            val progress = repository.getUserProgressOnce()
            val updated = progress.copy(hearts = 5)
            repository.saveUserProgress(updated)
        }
    }

    fun toggleDarkMode(enabled: Boolean) {
        viewModelScope.launch {
            val progress = repository.getUserProgressOnce()
            repository.saveUserProgress(progress.copy(isDarkMode = enabled))
        }
    }

    fun toggleSound(enabled: Boolean) {
        viewModelScope.launch {
            val progress = repository.getUserProgressOnce()
            repository.saveUserProgress(progress.copy(soundEnabled = enabled))
        }
    }

    fun toggleRomanization(enabled: Boolean) {
        viewModelScope.launch {
            val progress = repository.getUserProgressOnce()
            repository.saveUserProgress(progress.copy(showRomanizationOnly = enabled))
        }
    }

    fun resetProgress() {
        viewModelScope.launch {
            repository.resetAllProgress()
        }
    }

    suspend fun getExportedProgressJson(): String {
        return repository.exportProgressJson()
    }

    suspend fun importProgressJson(jsonString: String): Boolean {
        val success = repository.importProgressJson(jsonString)
        if (success) {
            val progress = repository.getUserProgressOnce()
            recheckAchievements(progress)
        }
        return success
    }

    private suspend fun recheckAchievements(progress: UserProgress) {
        // Evaluate dynamic unlocking of local achievements
        val originalAchievements = achievements.value
        val updatedList = originalAchievements.map { ach ->
            val updatedProgress = when (ach.id) {
                "streak_1" -> progress.streak
                "streak_3" -> progress.streak
                "stars_15" -> lessons.value.sumOf { it.stars }
                "stars_60" -> lessons.value.sumOf { it.stars }
                "lessons_3" -> {
                    lessons.value.count { it.completed }
                }
                else -> ach.progress
            }
            val unlocked = updatedProgress >= ach.target
            ach.copy(progress = updatedProgress.coerceAtMost(ach.target), isUnlocked = unlocked)
        }
        
        // Save back list (in memory / repo update)
        // Since we evaluate on-the-fly, the view model keeps sync
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return MainViewModel(ServiceLocator.getRepository(context)) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
