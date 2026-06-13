package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProgressEntity::class,
        VocabularyEntity::class,
        LessonEntity::class,
        ExerciseEntity::class,
        AchievementEntity::class,
        ReviewWordEntity::class
    ],
    version = 4,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProgressDao(): UserProgressDao
    abstract fun vocabularyDao(): VocabularyDao
    abstract fun lessonDao(): LessonDao
    abstract fun exerciseDao(): ExerciseDao
    abstract fun achievementDao(): AchievementDao
    abstract fun reviewWordDao(): ReviewWordDao
}
