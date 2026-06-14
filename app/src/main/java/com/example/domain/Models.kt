package com.example.domain

data class UserProgress(
    val name: String = "Lingo Learner",
    val streak: Int = 0,
    val xp: Int = 0,
    val hearts: Int = 5,
    val level: Int = 1,
    val selectedLanguageGoal: Int = 20, // Daily XP goal
    val lastActiveDate: String = "", // e.g., "y-M-d"
    val soundEnabled: Boolean = true,
    val isDarkMode: Boolean = false,
    val currentLessonId: Int = 1
) {
    fun xpProgressFraction(): Float {
        val xpForNextLevel = level * 100
        val xpForCurrentLevel = (level - 1) * 100
        val earnedInThisLevel = (xp - xpForCurrentLevel).coerceAtLeast(0)
        val neededInThisLevel = 100f
        return (earnedInThisLevel.toFloat() / neededInThisLevel).coerceIn(0f, 1f)
    }
}

data class Vocabulary(
    val id: Int,
    val thai: String,
    val english: String,
    val romanization: String,
    val category: String,
    val exampleThai: String = "",
    val exampleEnglish: String = ""
)

data class Lesson(
    val id: Int,
    val title: String,
    val description: String,
    val category: String, // Greetings, Food, Travel, Numbers, Family
    val unlocked: Boolean,
    val completed: Boolean,
    val stars: Int,
    val xpReward: Int = 20,
    val totalLessonsInCategory: Int = 3
)

data class Exercise(
    val id: Int,
    val lessonId: Int,
    val type: ExerciseType,
    val prompt: String, // "Translate this", "Listen and Type", "Match the Paris", etc.
    val question: String, // Thai script or English sentence
    val correctAnswer: String,
    val romanization: String = "", // Helpful tooltips
    val options: List<String> = emptyList(), // For Multiple Choice and Matching
    val audioText: String = "" // Specifically for TTS
)

enum class ExerciseType {
    MULTIPLE_CHOICE, // Select translation
    TRANSLATE,       // Type / construct translation
    LISTENING,       // Listen via TTS, select / type
    SPEAKING,        // Speak into microphone
    MATCHING,        // Pair English with Thai words
    SENTENCE_BUILD   // Select words in the correct sequence to build the translation
}

data class Achievement(
    val id: String,
    val title: String,
    val description: String,
    val progress: Int,
    val target: Int,
    val isUnlocked: Boolean,
    val iconName: String
)

data class ReviewWord(
    val thai: String,
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
    fun toVocabulary() = Vocabulary(
        id = thai.hashCode(),
        thai = thai,
        english = english,
        romanization = romanization,
        category = category,
        exampleThai = "",
        exampleEnglish = ""
    )
}
