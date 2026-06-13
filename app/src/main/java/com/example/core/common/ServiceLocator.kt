package com.example.core.common

import android.content.Context
import androidx.room.Room
import com.example.data.RepositoryImpl
import com.example.data.local.AppDatabase
import com.example.domain.ThaiLingoRepository

object ServiceLocator {
    private var database: AppDatabase? = null
    private var repository: ThaiLingoRepository? = null
    private var ttsHelper: ThaiTtsHelper? = null

    private fun getDatabase(context: Context): AppDatabase {
        return database ?: synchronized(this) {
            val db = Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "thailingo_database"
            )
            .fallbackToDestructiveMigration()
            .build()
            database = db
            db
        }
    }

    fun getRepository(context: Context): ThaiLingoRepository {
        return repository ?: synchronized(this) {
            val db = getDatabase(context)
            val repo = RepositoryImpl(
                db.userProgressDao(),
                db.vocabularyDao(),
                db.lessonDao(),
                db.exerciseDao(),
                db.achievementDao(),
                db.reviewWordDao()
            )
            repository = repo
            repo
        }
    }

    fun getTtsHelper(context: Context): ThaiTtsHelper {
        return ttsHelper ?: synchronized(this) {
            val helper = ThaiTtsHelper(context.applicationContext)
            ttsHelper = helper
            helper
        }
    }
}
