package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProgressDao {
    @Query("SELECT * FROM user_progress WHERE id = 1 LIMIT 1")
    fun getProgress(): Flow<UserProgressEntity?>

    @Query("SELECT * FROM user_progress WHERE id = 1 LIMIT 1")
    suspend fun getProgressOnce(): UserProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveProgress(progress: UserProgressEntity)

    @Update
    suspend fun updateProgress(progress: UserProgressEntity)
}

@Dao
interface VocabularyDao {
    @Query("SELECT * FROM vocabulary ORDER BY id ASC")
    fun getAllVocabulary(): Flow<List<VocabularyEntity>>

    @Query("SELECT COUNT(*) FROM vocabulary")
    suspend fun getVocabularyCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVocabulary(vocab: List<VocabularyEntity>)

    @Query("DELETE FROM vocabulary")
    suspend fun clearVocabulary()
}

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons ORDER BY id ASC")
    fun getAllLessons(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE id = :lessonId LIMIT 1")
    suspend fun getLessonById(lessonId: Int): LessonEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>)

    @Update
    suspend fun updateLesson(lesson: LessonEntity)

    @Query("DELETE FROM lessons")
    suspend fun clearLessons()
}

@Dao
interface ExerciseDao {
    @Query("SELECT * FROM exercises WHERE lessonId = :lessonId ORDER BY id ASC")
    suspend fun getExercisesByLessonId(lessonId: Int): List<ExerciseEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercises(exercises: List<ExerciseEntity>)

    @Query("DELETE FROM exercises")
    suspend fun clearExercises()
}

@Dao
interface AchievementDao {
    @Query("SELECT * FROM achievements ORDER BY id ASC")
    fun getAllAchievements(): Flow<List<AchievementEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAchievements(achievements: List<AchievementEntity>)

    @Update
    suspend fun updateAchievement(achievement: AchievementEntity)
}

@Dao
interface ReviewWordDao {
    @Query("SELECT * FROM review_words ORDER BY addedAt DESC")
    fun getAllReviewWords(): Flow<List<ReviewWordEntity>>

    @Query("SELECT * FROM review_words WHERE thai = :thai LIMIT 1")
    suspend fun getReviewWord(thai: String): ReviewWordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReviewWord(word: ReviewWordEntity)

    @Query("DELETE FROM review_words WHERE thai = :thai")
    suspend fun deleteReviewWord(thai: String)

    @Query("DELETE FROM review_words")
    suspend fun clearReviewQueue()
}
