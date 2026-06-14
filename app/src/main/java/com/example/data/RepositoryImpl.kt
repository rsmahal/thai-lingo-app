package com.example.data

import com.example.data.local.*
import com.example.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

class RepositoryImpl(
    private val userProgressDao: UserProgressDao,
    private val vocabularyDao: VocabularyDao,
    private val lessonDao: LessonDao,
    private val exerciseDao: ExerciseDao,
    private val achievementDao: AchievementDao,
    private val reviewWordDao: ReviewWordDao
) : ThaiLingoRepository {

    override fun getUserProgress(): Flow<UserProgress?> {
        return userProgressDao.getProgress().map { it?.toDomain() }
    }

    override suspend fun getUserProgressOnce(): UserProgress = withContext(Dispatchers.IO) {
        val userEntity = userProgressDao.getProgressOnce()
        if (userEntity == null) {
            val defaultProgress = UserProgress()
            userProgressDao.saveProgress(UserProgressEntity.fromDomain(defaultProgress))
            defaultProgress
        } else {
            userEntity.toDomain()
        }
    }

    override suspend fun saveUserProgress(progress: UserProgress) = withContext(Dispatchers.IO) {
        userProgressDao.saveProgress(UserProgressEntity.fromDomain(progress))
    }

    override fun getAllLessons(): Flow<List<Lesson>> {
        return lessonDao.getAllLessons().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun getLessonById(id: Int): Lesson? = withContext(Dispatchers.IO) {
        lessonDao.getLessonById(id)?.toDomain()
    }

    override suspend fun updateLesson(lesson: Lesson) = withContext(Dispatchers.IO) {
        lessonDao.updateLesson(LessonEntity.fromDomain(lesson))
    }

    override fun getAllVocabulary(): Flow<List<Vocabulary>> {
        return vocabularyDao.getAllVocabulary().map { list -> list.map { it.toDomain() } }
    }

    override fun getAllReviewWords(): Flow<List<ReviewWord>> {
        return reviewWordDao.getAllReviewWords().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun addWordToReviewQueue(thaiWord: String) = withContext(Dispatchers.IO) {
        updateReviewWordSrs(thaiWord, isCorrect = false)
    }

    override suspend fun updateReviewWordSrs(thaiWord: String, isCorrect: Boolean) = withContext(Dispatchers.IO) {
        val existing = reviewWordDao.getReviewWord(thaiWord)
        val now = System.currentTimeMillis()
        if (existing == null) {
            val allVocab = getSampleVocabulary()
            val vocab = allVocab.find { it.thai == thaiWord }
            val (english, romanization, category) = if (vocab != null) {
                Triple(vocab.english, vocab.romanization, vocab.category)
            } else {
                Triple(thaiWord, "", "General")
            }

            if (isCorrect) {
                val intervalDays = 1
                val entity = ReviewWordEntity(
                    thai = thaiWord,
                    english = english,
                    romanization = romanization,
                    category = category,
                    addedAt = now,
                    intervalDays = intervalDays,
                    streak = 1,
                    lastReviewedAt = now,
                    nextDueAt = now + intervalDays * 24 * 3600 * 1000L,
                    isMastered = false
                )
                reviewWordDao.insertReviewWord(entity)
            } else {
                val entity = ReviewWordEntity(
                    thai = thaiWord,
                    english = english,
                    romanization = romanization,
                    category = category,
                    addedAt = now,
                    intervalDays = 0,
                    streak = 0,
                    lastReviewedAt = now,
                    nextDueAt = now,
                    isMastered = false
                )
                reviewWordDao.insertReviewWord(entity)
            }
        } else {
            val nextEntity = if (isCorrect) {
                val nextStreak = existing.streak + 1
                val nextIntervalDays = when (nextStreak) {
                    1 -> 1
                    2 -> 3
                    3 -> 7
                    4 -> 14
                    else -> (existing.intervalDays * 2).coerceAtMost(180)
                }
                val isMasteredNow = nextStreak >= 4 || nextIntervalDays >= 14
                existing.copy(
                    streak = nextStreak,
                    intervalDays = nextIntervalDays,
                    lastReviewedAt = now,
                    nextDueAt = now + nextIntervalDays * 24 * 3600 * 1000L,
                    isMastered = isMasteredNow
                )
            } else {
                existing.copy(
                    streak = 0,
                    intervalDays = 0,
                    lastReviewedAt = now,
                    nextDueAt = now,
                    isMastered = false
                )
            }
            reviewWordDao.insertReviewWord(nextEntity)
        }
    }

    override suspend fun removeWordFromReviewQueue(thaiWord: String) = withContext(Dispatchers.IO) {
        reviewWordDao.deleteReviewWord(thaiWord)
    }

    override suspend fun getExercisesForLesson(lessonId: Int): List<Exercise> = withContext(Dispatchers.IO) {
        exerciseDao.getExercisesByLessonId(lessonId).map { it.toDomain() }
    }

    override fun getAllAchievements(): Flow<List<Achievement>> {
        return achievementDao.getAllAchievements().map { list -> list.map { it.toDomain() } }
    }

    override suspend fun updateAchievementProgress(id: String, progressValue: Int) = withContext(Dispatchers.IO) {
        val all = achievementDao.getAllAchievements()
        // Simple manual update since achievements is local
    }

    override suspend fun resetAllProgress() = withContext(Dispatchers.IO) {
        // Clear and rebuild
        userProgressDao.saveProgress(UserProgressEntity.fromDomain(UserProgress()))
        
        val lessons = lessonDao.getAllLessons().map { list -> list.map { it.toDomain() } }
        // Update first lesson unlocked, others locked
        val rawLessons = getSampleLessons()
        lessonDao.insertLessons(rawLessons.map { LessonEntity.fromDomain(it) })
        reviewWordDao.clearReviewQueue()
    }

    override suspend fun initializeDatabase() = withContext(Dispatchers.IO) {
        // Call this on launch to make sure data is populated if counts are 0
        if (vocabularyDao.getVocabularyCount() == 0) {
            // Populate Vocabulary
            val vocabulary = getSampleVocabulary()
            vocabularyDao.insertVocabulary(vocabulary.map { VocabularyEntity.fromDomain(it) })

            // Populate Lessons
            val lessons = getSampleLessons()
            lessonDao.insertLessons(lessons.map { LessonEntity.fromDomain(it) })

            // Populate Exercises
            val exercises = getSampleExercises()
            exerciseDao.insertExercises(exercises.map { ExerciseEntity.fromDomain(it) })

            // Populate Achievements
            val achievements = getSampleAchievements()
            achievementDao.insertAchievements(achievements.map { AchievementEntity.fromDomain(it) })

            // Setup default progress
            userProgressDao.saveProgress(UserProgressEntity.fromDomain(UserProgress()))
        } else {
            // Make sure the Topic Test lessons are populated if they are missing
            if (lessonDao.getLessonById(101) == null) {
                val tests = getSampleLessons().filter { it.id >= 100 }
                lessonDao.insertLessons(tests.map { LessonEntity.fromDomain(it) })
            }
        }
    }

    private fun getSampleVocabulary(): List<Vocabulary> {
        return listOf(
            // Greetings (1-10)
            Vocabulary(1, "สวัสดี", "Hello / Goodbye", "Sawatdee", "Greetings", "สวัสดีครับ ยินดีที่ได้รู้จัก", "Hello, nice to meet you."),
            Vocabulary(2, "ขอบคุณ", "Thank you", "Khop khun", "Greetings", "ขอบคุณมากครับสำหรับอาหาร", "Thank you very much for the food."),
            Vocabulary(3, "สบายดีไหม", "How are you?", "Sabai dee mai", "Greetings", "สบายดีไหมครับวันนี้", "How are you doing today?"),
            Vocabulary(4, "สบายดี", "I am fine", "Sabai dee", "Greetings", "ผมสบายดี ขอบคุณครับ", "I am fine, thank you."),
            Vocabulary(5, "ยินดีที่ได้รู้จัก", "Nice to meet you", "Yindee thee dai roo jak", "Greetings", "ยินดีที่ได้รู้จักเช่นกัน", "Nice to meet you too."),
            Vocabulary(6, "ขอโทษ", "Sorry / Excuse me", "Kho thot", "Greetings", "ขอโทษครับ ห้องน้ำไปทางไหน", "Excuse me, where is the bathroom?"),
            Vocabulary(7, "ใช่", "Yes", "Chai", "Greetings", "ใช่ครับ ผมเป็นคนอเมริกัน", "Yes, I am American."),
            Vocabulary(8, "ไม่ใช่", "No / Not correct", "Mai chai", "Greetings", "ไม่ใช่ครับ นั่นไม่ถูกต้อง", "No, that is not correct."),
            Vocabulary(9, "ลาก่อน", "Goodbye", "La kon", "Greetings", "ลาก่อนนะเพื่อน", "Goodbye, my friend."),
            Vocabulary(10, "โชคดี", "Good luck", "Chok dee", "Greetings", "โชคดีในการสอบนะ", "Good luck on your exam!"),

            // Food & Drink (11-22)
            Vocabulary(11, "ข้าว", "Rice", "Khao", "Food", "ฉันชอบกินข้าวเหนียว", "I like to eat sticky rice."),
            Vocabulary(12, "น้ำ", "Water", "Nam", "Food", "ขอน้ำเปล่าแก้วหนึ่งครับ", "Please give me a glass of water."),
            Vocabulary(13, "อาหาร", "Food", "Ahan", "Food", "อาหารไทยอร่อยมาก", "Thai food is very delicious."),
            Vocabulary(14, "ต้มยำกุ้ง", "Spicy shrimp soup", "Tom yum goong", "Food", "ต้มยำกุ้งหม้อนี้เผ็ดมาก", "This pot of Tom Yum Goong is very spicy."),
            Vocabulary(15, "ผัดไทย", "Stir-fried noodles", "Pad Thai", "Food", "สั่งผัดไทยหนึ่งจานครับ", "Order one plate of Pad Thai, please."),
            Vocabulary(16, "ส้มตำ", "Papaya salad", "Som tam", "Food", "ส้มตำไทยไม่ใส่พริก", "Thai papaya salad without chili."),
            Vocabulary(17, "ผลไม้", "Fruit", "Phonlamai", "Food", "ผลไม้ไทยมีหลายชนิด", "There are many kinds of Thai fruits."),
            Vocabulary(18, "กาแฟ", "Coffee", "Kafae", "Food", "ฉันดื่มกาแฟร้อนตอนเช้า", "I drink hot coffee in the morning."),
            Vocabulary(19, "อร่อย", "Delicious", "Aroy", "Food", "ทุเรียนนี้อร่อยมาก", "This durian is very delicious."),
            Vocabulary(20, "เผ็ด", "Spicy", "Phet", "Food", "แกงเขียวหวานเผ็ดไหม", "Is the green curry spicy?"),
            Vocabulary(21, "หิว", "Hungry", "Hiw", "Food", "ตอนนี้ฉันหิวข้าวแล้ว", "I am hungry for rice now."),
            Vocabulary(22, "กิน", "Eat", "Kin", "Food", "ไปกินข้าวกันเถอะ", "Let's go eat rice/food."),

            // Numbers & Shopping (23-34)
            Vocabulary(23, "หนึ่ง", "One", "Nung", "Numbers", "แมวหนึ่งตัว", "One cat."),
            Vocabulary(24, "สอง", "Two", "Song", "Numbers", "ขอเบียร์สองขวดครับ", "Two bottles of beer, please."),
            Vocabulary(25, "สาม", "Three", "Sam", "Numbers", "มีเวลาสามวัน", "Have three days."),
            Vocabulary(26, "สี่", "Four", "See", "Numbers", "สี่สิบห้าบาท", "Forty-five Baht."),
            Vocabulary(27, "ห้า", "Five", "Ha", "Numbers", "บวกอีกห้าบาทครับ", "Add five more Baht, please."),
            Vocabulary(28, "สิบ", "Ten", "Sip", "Numbers", "ราคาเก้าสิบเก้าบาท", "Price ninety-nine Baht."),
            Vocabulary(29, "ร้อย", "Hundred", "Roi", "Numbers", "หนึ่งร้อยบาทพอดี", "One hundred Baht exactly."),
            Vocabulary(30, "บาท", "Baht", "Baht", "Numbers", "จานละห้าสิบบาท", "Fifty Baht per plate."),
            Vocabulary(31, "ราคา", "Price", "Rakha", "Numbers", "ราคาเท่าไหร่ครับ", "What is the price?"),
            Vocabulary(32, "แพง", "Expensive", "Phaeng", "Numbers", "ของฝากนี้แพงมาก", "This souvenir is very expensive."),
            Vocabulary(33, "ถูก", "Cheap / Correct", "Thook", "Numbers", "เสื้อตัวนี้ราคาถูกดี", "This shirt is cheap / good price."),
            Vocabulary(34, "เท่าไหร่", "How much?", "Thao rai", "Numbers", "ส้มกิโลละเท่าไหร่ครับ", "How much per kilo of oranges?"),

            // Travel & Directions (35-44)
            Vocabulary(35, "โรงแรม", "Hotel", "Rong raem", "Travel", "โรงแรมนี้น่าอยู่มาก", "This hotel is very nice to stay."),
            Vocabulary(36, "สนามบิน", "Airport", "Sanam bin", "Travel", "ตั๋วไปสนามบินสุวรรณภูมิ", "A ticket to Suvarnabhumi Airport."),
            Vocabulary(37, "สถานี", "Station", "Sathani", "Travel", "ถามทางไปสถานีรถไฟ", "Ask directions to the railway station."),
            Vocabulary(38, "ห้องน้ำ", "Restroom", "Hong nam", "Travel", "ห้องน้ำอยู่ข้างหลังครับ", "The restroom is in the back."),
            Vocabulary(39, "เลี้ยวซ้าย", "Turn left", "Liew sai", "Travel", "เลี้ยวซ้ายตรงสี่แยกหน้า", "Turn left at the next intersection."),
            Vocabulary(40, "เลี้ยวขวา", "Turn right", "Liew khwa", "Travel", "เลี้ยวขวาที่หน้าวัด", "Turn right in front of the temple."),
            Vocabulary(41, "ไป", "Go", "Pai", "Travel", "อยากไปเที่ยวภูเก็ต", "Want to travel to Phuket."),
            Vocabulary(42, "ที่ไหน", "Where?", "Thee nai", "Travel", "บ้านของคุณอยู่ที่ไหน", "Where is your house?"),
            Vocabulary(43, "แผนที่", "Map", "Phaen thee", "Travel", "เปิดแผนที่ในมือถือ", "Open the map on the phone."),
            Vocabulary(44, "รถตุ๊กตุ๊ก", "Tuk-Tuk", "Rot tuk-tuk", "Travel", "นั่งรถตุ๊กตุ๊กเที่ยวสนุกดี", "Riding a tuk-tuk is fun to tour around."),

            // Family (45-52)
            Vocabulary(45, "พ่อ", "Father", "Pho", "Family", "พ่อของฉันเป็นใจดีมาก", "My father is very kind."),
            Vocabulary(46, "แม่", "Mother", "Mae", "Family", "คุณแม่ทำอาหารอร่อยที่สุด", "Mother cooks the most delicious food."),
            Vocabulary(47, "พี่ชาย", "Older brother", "Phee chai", "Family", "พี่ชายของผมทำงานที่กรุงเทพฯ", "My older brother works in Bangkok."),
            Vocabulary(48, "พี่สาว", "Older sister", "Phee sao", "Family", "พี่สาวของเขาเรียนหมอ", "His older sister is studying medicine."),
            Vocabulary(49, "น้องชาย", "Younger brother", "Nong chai", "Family", "น้องชายชอบเล่นเกมฟุตบอล", "Younger brother likes to play football games."),
            Vocabulary(50, "น้องสาว", "Younger sister", "Nong sao", "Family", "น้องสาวเพิ่งเข้าโรงเรียนอนุบาล", "Younger sister just entered kindergarten."),
            Vocabulary(51, "ครอบครัว", "Family", "Khrop khrua", "Family", "พวกเราเป็นครอบครัวอบอุ่น", "We are a warm family."),
            Vocabulary(52, "รัก", "Love", "Rak", "Family", "ผมรักครอบครัวและเพื่อนๆ", "I love my family and friends.")
        )
    }

    private fun getSampleLessons(): List<Lesson> {
        return listOf(
            Lesson(1, "Greetings 101", "Learn sawatdee, khop khun and essentials.", "Greetings", unlocked = true, completed = false, stars = 0),
            Lesson(2, "More Greetings & Politeness", "Practice how to apologize or say goodbye.", "Greetings", unlocked = false, completed = false, stars = 0),
            Lesson(101, "Greetings Topic Test", "Greetings comprehensive 20-question test.", "Greetings", unlocked = false, completed = false, stars = 0, xpReward = 50),
            
            Lesson(3, "Thai Food Staples", "Master khao, nam, and food nouns.", "Food", unlocked = false, completed = false, stars = 0),
            Lesson(4, "Famous Dishes & Tastes", "Learn Som Tam, Pad Thai, spicy, and hungry.", "Food", unlocked = false, completed = false, stars = 0),
            Lesson(102, "Food Topic Test", "Food comprehensive 20-question test.", "Food", unlocked = false, completed = false, stars = 0, xpReward = 50),
            
            Lesson(5, "Counting 1 to 5", "Build numbers and count objects.", "Numbers", unlocked = false, completed = false, stars = 0),
            Lesson(6, "Money & Shopping", "Master Baht, cheap, expensive, and asking the price.", "Numbers", unlocked = false, completed = false, stars = 0),
            Lesson(103, "Numbers Topic Test", "Numbers comprehensive 20-question test.", "Numbers", unlocked = false, completed = false, stars = 0, xpReward = 50),
            
            Lesson(7, "Directions & Transit", "Ask where landmarks are, learn left and right.", "Travel", unlocked = false, completed = false, stars = 0),
            Lesson(8, "Transit & Tuk-Tuk", "Order taxis, ride tuk-tuks, and read maps.", "Travel", unlocked = false, completed = false, stars = 0),
            Lesson(104, "Travel Topic Test", "Travel comprehensive 20-question test.", "Travel", unlocked = false, completed = false, stars = 0, xpReward = 50),
            
            Lesson(9, "Parents & Core Family", "Talk about mother, father, and family.", "Family", unlocked = false, completed = false, stars = 0),
            Lesson(10, "Siblings & Love", "Discuss brothers, sisters, and sharing love.", "Family", unlocked = false, completed = false, stars = 0),
            Lesson(105, "Family Topic Test", "Family comprehensive 20-question test.", "Family", unlocked = false, completed = false, stars = 0, xpReward = 50)
        )
    }

    private fun getSampleExercises(): List<Exercise> {
        val list = mutableListOf<Exercise>()
        val vocabulary = getSampleVocabulary()

        for (lessonId in 1..10) {
            val lessonVocab = when (lessonId) {
                1 -> vocabulary.filter { it.id in 1..5 }
                2 -> vocabulary.filter { it.id in 6..10 }
                3 -> vocabulary.filter { it.id in 11..16 }
                4 -> vocabulary.filter { it.id in 17..22 }
                5 -> vocabulary.filter { it.id in 23..28 }
                6 -> vocabulary.filter { it.id in 29..34 }
                7 -> vocabulary.filter { it.id in 35..39 }
                8 -> vocabulary.filter { it.id in 40..44 }
                9 -> vocabulary.filter { it.id in 45..48 }
                10 -> vocabulary.filter { it.id in 49..52 }
                else -> emptyList()
            }
            
            if (lessonVocab.isEmpty()) continue

            // 1. English word in question. Answers in thai
            val w1 = lessonVocab[0]
            val otherThais = vocabulary.filter { it.id != w1.id }
                .map { it.thai }
                .distinct()
                .shuffled()
                .take(3)
            val options1 = (otherThais + w1.thai).shuffled()
            list.add(Exercise(
                id = lessonId * 100 + 1,
                lessonId = lessonId,
                type = ExerciseType.MULTIPLE_CHOICE,
                prompt = "Select the correct Thai translation for this English word:",
                question = w1.english,
                correctAnswer = w1.thai,
                romanization = "",
                options = options1,
                audioText = w1.thai
            ))

            // 2. Thai word in question. Answers in english
            val w2 = lessonVocab.getOrElse(1) { lessonVocab[0] }
            val otherEnglishesForW2 = vocabulary.filter { it.id != w2.id }
                .map { it.english }
                .distinct()
                .shuffled()
                .take(3)
            val options2 = (otherEnglishesForW2 + w2.english).shuffled()
            list.add(Exercise(
                id = lessonId * 100 + 2,
                lessonId = lessonId,
                type = ExerciseType.MULTIPLE_CHOICE,
                prompt = "What is the English meaning of this Thai word?",
                question = w2.thai,
                correctAnswer = w2.english,
                romanization = w2.romanization,
                options = options2,
                audioText = w2.thai
            ))

            // 3. Listening exercise. Sound plays in thai. Answers in english
            val w3 = lessonVocab.getOrElse(2) { lessonVocab[0] }
            val otherEnglishesForW3 = vocabulary.filter { it.id != w3.id }
                .map { it.english }
                .distinct()
                .shuffled()
                .take(3)
            val options3 = (otherEnglishesForW3 + w3.english).shuffled()
            list.add(Exercise(
                id = lessonId * 100 + 3,
                lessonId = lessonId,
                type = ExerciseType.LISTENING,
                prompt = "Listen and select the correct English translation:",
                question = w3.thai,
                correctAnswer = w3.english,
                romanization = "",
                options = options3,
                audioText = w3.thai
            ))

            // 4. The pairing exercise
            val pairingWords = if (lessonVocab.size >= 4) {
                lessonVocab.take(4)
            } else {
                lessonVocab + vocabulary.filter { it.category == w1.category && !lessonVocab.contains(it) }.take(4 - lessonVocab.size)
            }
            val pairingCorrectAnswer = pairingWords.joinToString("|") { "${it.thai}=${it.english}" }
            val pairingOptions = pairingWords.flatMap { listOf(it.thai, it.english) }.shuffled()
            list.add(Exercise(
                id = lessonId * 100 + 4,
                lessonId = lessonId,
                type = ExerciseType.MATCHING,
                prompt = "Tap the matching English and Thai pairs:",
                question = "Match vocabulary",
                correctAnswer = pairingCorrectAnswer,
                romanization = "",
                options = pairingOptions,
                audioText = ""
            ))
        }

        return list
    }

    private fun getSampleAchievements(): List<Achievement> {
        return listOf(
            Achievement("streak_1", "Streak Starter", "Achieve a 1-day study streak.", 0, 1, isUnlocked = false, "streak"),
            Achievement("streak_3", "Streak Master", "Achieve a 3-day study streak.", 0, 3, isUnlocked = false, "streak"),
            Achievement("xp_50", "Knowledge Seeker", "Earn 50 XP in total.", 0, 50, isUnlocked = false, "xp"),
            Achievement("xp_200", "XP Champion", "Earn 200 XP in total.", 0, 200, isUnlocked = false, "xp"),
            Achievement("lessons_3", "Graduate", "Complete 3 full lessons.", 0, 3, isUnlocked = false, "lesson")
        )
    }
}
