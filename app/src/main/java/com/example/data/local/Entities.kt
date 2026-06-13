package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.domain.Achievement
import com.example.domain.Exercise
import com.example.domain.ExerciseType
import com.example.domain.Lesson
import com.example.domain.UserProgress
import com.example.domain.Vocabulary
import com.example.domain.ReviewWord

@Entity(tableName = "user_progress")
data class UserProgressEntity(
    @PrimaryKey val id: Int = 1,
    val name: String,
    val streak: Int,
    val xp: Int,
    val hearts: Int,
    val level: Int,
    val selectedLanguageGoal: Int,
    val lastActiveDate: String,
    val soundEnabled: Boolean,
    val isDarkMode: Boolean,
    val currentLessonId: Int
) {
    fun toDomain() = UserProgress(
        name = name,
        streak = streak,
        xp = xp,
        hearts = hearts,
        level = level,
        selectedLanguageGoal = selectedLanguageGoal,
        lastActiveDate = lastActiveDate,
        soundEnabled = soundEnabled,
        isDarkMode = isDarkMode,
        currentLessonId = currentLessonId
    )

    companion object {
        fun fromDomain(domain: UserProgress) = UserProgressEntity(
            name = domain.name,
            streak = domain.streak,
            xp = domain.xp,
            hearts = domain.hearts,
            level = domain.level,
            selectedLanguageGoal = domain.selectedLanguageGoal,
            lastActiveDate = domain.lastActiveDate,
            soundEnabled = domain.soundEnabled,
            isDarkMode = domain.isDarkMode,
            currentLessonId = domain.currentLessonId
        )
    }
}

@Entity(tableName = "vocabulary")
data class VocabularyEntity(
    @PrimaryKey val id: Int,
    val thai: String,
    val english: String,
    val romanization: String,
    val category: String,
    val exampleThai: String,
    val exampleEnglish: String
) {
    fun toDomain() = Vocabulary(
        id = id,
        thai = thai,
        english = english,
        romanization = romanization,
        category = category,
        exampleThai = exampleThai,
        exampleEnglish = exampleEnglish
    )

    companion object {
        fun fromDomain(domain: Vocabulary) = VocabularyEntity(
            id = domain.id,
            thai = domain.thai,
            english = domain.english,
            romanization = domain.romanization,
            category = domain.category,
            exampleThai = domain.exampleThai,
            exampleEnglish = domain.exampleEnglish
        )
    }
}

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey val id: Int,
    val title: String,
    val description: String,
    val category: String,
    val unlocked: Boolean,
    val completed: Boolean,
    val stars: Int,
    val xpReward: Int
) {
    fun toDomain() = Lesson(
        id = id,
        title = title,
        description = description,
        category = category,
        unlocked = unlocked,
        completed = completed,
        stars = stars,
        xpReward = xpReward
    )

    companion object {
        fun fromDomain(domain: Lesson) = LessonEntity(
            id = domain.id,
            title = domain.title,
            description = domain.description,
            category = domain.category,
            unlocked = domain.unlocked,
            completed = domain.completed,
            stars = domain.stars,
            xpReward = domain.xpReward
        )
    }
}

@Entity(tableName = "exercises")
data class ExerciseEntity(
    @PrimaryKey val id: Int,
    val lessonId: Int,
    val type: String, // Store enum name
    val prompt: String,
    val question: String,
    val correctAnswer: String,
    val romanization: String,
    val optionsString: String, // Delimited by "|"
    val audioText: String
) {
    fun toDomain() = Exercise(
        id = id,
        lessonId = lessonId,
        type = ExerciseType.valueOf(type),
        prompt = prompt,
        question = question,
        correctAnswer = correctAnswer,
        romanization = romanization,
        options = if (optionsString.isEmpty()) emptyList() else optionsString.split("|"),
        audioText = audioText
    )

    companion object {
        fun fromDomain(domain: Exercise) = ExerciseEntity(
            id = domain.id,
            lessonId = domain.lessonId,
            type = domain.type.name,
            prompt = domain.prompt,
            question = domain.question,
            correctAnswer = domain.correctAnswer,
            romanization = domain.romanization,
            optionsString = domain.options.joinToString("|"),
            audioText = domain.audioText
        )
    }
}

@Entity(tableName = "achievements")
data class AchievementEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String,
    val progress: Int,
    val target: Int,
    val isUnlocked: Boolean,
    val iconName: String
) {
    fun toDomain() = Achievement(
        id = id,
        title = title,
        description = description,
        progress = progress,
        target = target,
        isUnlocked = isUnlocked,
        iconName = iconName
    )

    companion object {
        fun fromDomain(domain: Achievement) = AchievementEntity(
            id = domain.id,
            title = domain.title,
            description = domain.description,
            progress = domain.progress,
            target = domain.target,
            isUnlocked = domain.isUnlocked,
            iconName = domain.iconName
        )
    }
}

@Entity(tableName = "review_words")
data class ReviewWordEntity(
    @PrimaryKey val thai: String,
    val english: String,
    val romanization: String,
    val category: String,
    val addedAt: Long = System.currentTimeMillis(),
    val intervalDays: Int = 0,
    val streak: Int = 0,
    val lastReviewedAt: Long = 0,
    val nextDueAt: Long = System.currentTimeMillis(),
    val isMastered: Boolean = false
) {
    fun toDomain() = ReviewWord(
        thai = thai,
        english = english,
        romanization = romanization,
        category = category,
        addedAt = addedAt,
        intervalDays = intervalDays,
        streak = streak,
        lastReviewedAt = lastReviewedAt,
        nextDueAt = nextDueAt,
        isMastered = isMastered
    )

    companion object {
        fun fromDomain(domain: ReviewWord) = ReviewWordEntity(
            thai = domain.thai,
            english = domain.english,
            romanization = domain.romanization,
            category = domain.category,
            addedAt = domain.addedAt,
            intervalDays = domain.intervalDays,
            streak = domain.streak,
            lastReviewedAt = domain.lastReviewedAt,
            nextDueAt = domain.nextDueAt,
            isMastered = domain.isMastered
        )
    }
}
