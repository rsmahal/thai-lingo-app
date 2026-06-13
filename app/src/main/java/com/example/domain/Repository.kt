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
    
    suspend fun getExercisesForLesson(lessonId: Int): List<Exercise>
    
    fun getAllAchievements(): Flow<List<Achievement>>
    suspend fun updateAchievementProgress(id: String, progressValue: Int)
    
    suspend fun initializeDatabase()
    suspend fun resetAllProgress()
}
