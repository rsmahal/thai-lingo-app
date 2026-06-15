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
    val currentLessonId: Int = 1,
    val showRomanizationOnly: Boolean = false
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
    val audioText: String = "", // Specifically for TTS
    val isPopQuiz: Boolean = false
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

fun getLessonVocabIdsRange(lessonId: Int): IntRange {
    return when (lessonId) {
        // Topic 1: Greetings Basic (1-4)
        1 -> 1..10
        2 -> 11..20
        3 -> 21..30
        4 -> 31..40
        
        // Topic 2: Food Staples (5-8)
        5 -> 101..110
        6 -> 111..120
        7 -> 121..130
        8 -> 131..140
        
        // Topic 3: Numbers & Money (9-12)
        9 -> 201..210
        10 -> 211..220
        11 -> 221..230
        12 -> 231..240
        
        // Topic 4: Directions & Transit (13-16)
        13 -> 301..310
        14 -> 311..320
        15 -> 321..330
        16 -> 331..340
        
        // Topic 5: Parents & Relatives (17-20)
        17 -> 401..410
        18 -> 411..420
        19 -> 421..430
        20 -> 431..440
        
        // Topic 6: More Greetings & Feelings (21-24)
        21 -> 41..50
        22 -> 51..60
        23 -> 61..70
        24 -> 71..80
        
        // Topic 7: Famous Dishes & Cafe (25-28)
        25 -> 141..150
        26 -> 151..160
        27 -> 161..170
        28 -> 171..180
        
        // Topic 8: Shopping & bargaining (29-32)
        29 -> 241..250
        30 -> 251..260
        31 -> 261..270
        32 -> 271..280
        
        // Topic 9: Sightseeing & Tuk-Tuk (33-36)
        33 -> 341..350
        34 -> 351..360
        35 -> 361..370
        36 -> 371..380
        
        // Topic 10: Siblings, Friends & Relatives (37-40)
        37 -> 441..450
        38 -> 451..460
        39 -> 461..470
        40 -> 471..480
        
        // Topic 11: Conversational Politeness (41-44)
        41 -> 81..90
        42 -> 91..100
        43 -> 181..190
        44 -> 191..200
        
        // Topic 12: Numbers & Travel Advanced (45-48)
        45 -> 281..290
        46 -> 291..300
        47 -> 381..390
        48 -> 391..400
        
        // Topic 13: Deep Social Connections (49-50)
        49 -> 481..490
        50 -> 491..500
        
        else -> 1..10
    }
}

fun getTopicTestVocabIdsRange(testId: Int): List<IntRange> {
    return when (testId) {
        101 -> listOf(1..40)
        102 -> listOf(101..140)
        103 -> listOf(201..240)
        104 -> listOf(301..340)
        105 -> listOf(401..440)
        106 -> listOf(41..80)
        107 -> listOf(141..180)
        108 -> listOf(241..280)
        109 -> listOf(341..380)
        110 -> listOf(441..480)
        111 -> listOf(81..100, 181..200)
        112 -> listOf(281..300, 381..400)
        113 -> listOf(481..500)
        else -> listOf(1..40)
    }
}
