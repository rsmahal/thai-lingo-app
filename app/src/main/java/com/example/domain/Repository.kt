package com.example.domain

import kotlinx.coroutines.flow.Flow

interface ThaiLingoRepository {
    fun getUserProgress(): Flow<UserProgress?>
    suspend fun getUserProgressOnce(): UserProgress
    suspend fun saveUserProgress(progress: UserProgress)
    
    fun getAllLessons(): Flow<List<Lesson>>
    suspend fun getLessonById(id: Int): Lesson?
    suspend fun updateLesson(lesson: Lesson)
    
    fun getAllVocabulary(): Flow<List<Vocabulary>>
    
    fun getAllReviewWords(): Flow<List<ReviewWord>>
    suspend fun addWordToReviewQueue(thaiWord: String)
    suspend fun removeWordFromReviewQueue(thaiWord: String)
    suspend fun updateReviewWordSrs(thaiWord: String, isCorrect: Boolean)
    suspend fun unlockReviewWord(thaiWord: String)
    
    suspend fun getExercisesForLesson(lessonId: Int): List<Exercise>
    
    fun getAllAchievements(): Flow<List<Achievement>>
    suspend fun updateAchievementProgress(id: String, progressValue: Int)
    
    suspend fun initializeDatabase()
    suspend fun resetAllProgress()
    
    suspend fun exportProgressJson(): String
    suspend fun importProgressJson(jsonString: String): Boolean
}
