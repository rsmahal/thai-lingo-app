package com.example.data

import com.example.data.local.*
import com.example.domain.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import org.json.JSONObject
import org.json.JSONArray

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
        return reviewWordDao.getAllReviewWords().map { list -> 
            list.map { it.toDomain() }.filter { it.thai != it.english }
        }
    }

    override suspend fun addWordToReviewQueue(thaiWord: String) = withContext(Dispatchers.IO) {
        updateReviewWordSrs(thaiWord, isCorrect = false)
    }

    override suspend fun updateReviewWordSrs(thaiWord: String, isCorrect: Boolean) = withContext(Dispatchers.IO) {
        val existing = reviewWordDao.getReviewWord(thaiWord)
        val now = System.currentTimeMillis()
        if (existing == null) {
            val allVocab = getSampleVocabulary()
            val vocab = allVocab.find { it.thai.trim() == thaiWord.trim() }
            if (vocab == null) {
                return@withContext
            }
            val english = vocab.english
            val romanization = vocab.romanization
            val category = vocab.category

            val actualThaiWord = vocab.thai // use the official dictionary spelling

            if (isCorrect) {
                val intervalDays = 1
                val entity = ReviewWordEntity(
                    thai = actualThaiWord,
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
                    thai = actualThaiWord,
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

    override suspend fun unlockReviewWord(thaiWord: String) = withContext(Dispatchers.IO) {
        val existing = reviewWordDao.getReviewWord(thaiWord)
        if (existing == null) {
            val allVocab = getSampleVocabulary()
            val vocab = allVocab.find { it.thai == thaiWord }
            if (vocab != null) {
                val now = System.currentTimeMillis()
                val entity = ReviewWordEntity(
                    thai = vocab.thai,
                    english = vocab.english,
                    romanization = vocab.romanization,
                    category = vocab.category,
                    addedAt = now,
                    intervalDays = 1,
                    streak = 1,
                    lastReviewedAt = now,
                    nextDueAt = now + 1 * 24 * 3600 * 1000L,
                    isMastered = false
                )
                reviewWordDao.insertReviewWord(entity)
            }
        }
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
        // ALWAYS synchronize local static tables (Vocabulary and Exercises) with the latest definitions in code.
        // This makes sure corrections to spelling, vocab mappings, or sentence building distractors
        // take effect instantly when the app is updated/restarted, even if the progress is restored from Android backup or another device.

        // 1. Populate/Update Vocabulary
        val vocabulary = getSampleVocabulary()
        vocabularyDao.insertVocabulary(vocabulary.map { VocabularyEntity.fromDomain(it) })

        // 1.5. Synchronize existing review words with the latest vocabulary definitions
        // to update fields like english, romanization, and category if they have changed.
        val latestVocabMap = vocabulary.associateBy { it.thai }
        val existingReviewWords = reviewWordDao.getAllReviewWordsOnce()
        for (rw in existingReviewWords) {
            val matchingVocab = latestVocabMap[rw.thai]
            if (matchingVocab != null && (rw.english != matchingVocab.english || rw.romanization != matchingVocab.romanization || rw.category != matchingVocab.category)) {
                val updatedRw = rw.copy(
                    english = matchingVocab.english,
                    romanization = matchingVocab.romanization,
                    category = matchingVocab.category
                )
                reviewWordDao.insertReviewWord(updatedRw)
            }
        }

        // 2. Clear and Insert all Exercises (ensures edits to sentence paths operate correctly/instantly)
        exerciseDao.clearExercises()
        val exercises = getSampleExercises()
        exerciseDao.insertExercises(exercises.map { ExerciseEntity.fromDomain(it) })

        // 3. Populate/Update Lessons without erasing user completed state, unlocked state, or star counts
        val sampleLessons = getSampleLessons()
        for (sample in sampleLessons) {
            val existing = lessonDao.getLessonById(sample.id)
            if (existing != null) {
                // Preserve user-dynamic fields (unlocked, completed, stars), update static ones
                val updated = existing.copy(
                    title = sample.title,
                    description = sample.description,
                    category = sample.category,
                    xpReward = sample.xpReward
                )
                lessonDao.updateLesson(updated)
            } else {
                // New lesson, insert
                lessonDao.insertLessons(listOf(LessonEntity.fromDomain(sample)))
            }
        }

        // 4. Populate achievements if none exist
        val achievements = getSampleAchievements()
        achievementDao.insertAchievements(achievements.map { AchievementEntity.fromDomain(it) })

        // 5. Setup default progress (if not existing)
        if (userProgressDao.getProgressOnce() == null) {
            userProgressDao.saveProgress(UserProgressEntity.fromDomain(UserProgress()))
        }
    }

    private fun getSampleVocabulary(): List<Vocabulary> {
        val list = mutableListOf<Vocabulary>()
        list.addAll(getGreetingsVocabulary())
        list.addAll(getFoodVocabulary())
        list.addAll(getNumbersVocabulary())
        list.addAll(getTravelVocabulary())
        list.addAll(getFamilyVocabulary())
        return list
    }

    private fun getGreetingsVocabulary(): List<Vocabulary> {
        return listOf(
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
            Vocabulary(11, "ยินดี", "Welcome / Glad", "Yindee", "Greetings", "ยินดีเสมอกับคุณครับ", "Always welcome / glad for you."),
            Vocabulary(12, "แล้วพบกันใหม่", "See you again", "Laew phop kan mai", "Greetings", "แล้วพบกันใหม่พรุ่งนี้", "See you again tomorrow."),
            Vocabulary(13, "ราตรีสวัสดิ์", "Good night", "Ratree sawat", "Greetings", "ราตรีสวัสดิ์นะลูก", "Good night, my child."),
            Vocabulary(14, "คุณชื่ออะไร", "What is your name?", "Khun cheu arai", "Greetings", "คุณชื่ออะไรครับพี่", "What is your name, older brother/sister?"),
            Vocabulary(15, "ผมชื่อ", "My name is (male)", "Phom cheu", "Greetings", "ผมชื่อสมชายครับ", "My name is Somchai."),
            Vocabulary(16, "ฉันชื่อ", "My name is (female)", "Chan cheu", "Greetings", "ฉันชื่อมารีค่ะ", "My name is Marie."),
            Vocabulary(17, "คุณ", "You", "Khun", "Greetings", "คุณสูงมากนะ", "You are very tall."),
            Vocabulary(18, "ผม", "I (male)", "Phom", "Greetings", "ผมอยากดื่มน้ำ", "I want to drink water."),
            Vocabulary(19, "ฉัน", "I (female)", "Chan", "Greetings", "ฉันคิดถึงครอบครัว", "I miss my family."),
            Vocabulary(20, "ยินดีด้วย", "Congratulations", "Yindee duay", "Greetings", "ยินดีด้วยกับงานใหม่นะ", "Congratulations on your new job!"),
            Vocabulary(21, "ครับ", "Polite particle (male)", "Khrap", "Greetings", "สบายดีครับ", "I am fine (polite male)."),
            Vocabulary(22, "ค่ะ", "Polite particle (female statement)", "Kha", "Greetings", "ขอบคุณค่ะ", "Thank you (polite female)."),
            Vocabulary(23, "เรา", "We / Us", "Rao", "Greetings", "เราไปกินข้าวกันเถอะ", "Let's go eat."),
            Vocabulary(24, "เขา", "He / She / They", "Khao", "Greetings", "เขาเป็นคนดีมาก", "They are a very good person."),
            Vocabulary(25, "มัน", "It", "Man", "Greetings", "มันใหญ่มากจริงๆ", "It is really very big."),
            Vocabulary(26, "พวกเรา", "We (plural)", "Phuak rao", "Greetings", "พวกเราชอบภาษาไทย", "We like Thai language."),
            Vocabulary(27, "พวกเขา", "They (plural)", "Phuak khao", "Greetings", "พวกเขากำลังมา", "They are coming."),
            Vocabulary(28, "เธอ", "She/Her", "Thoe", "Greetings", "เธอน่ารักมาก", "You are very cute."),
            Vocabulary(29, "ท่าน", "You (polite) / Respectable person", "Than", "Greetings", "เชิญท่านข้างในครับ", "Please enter inside, sir/ma'am."),
            Vocabulary(30, "ตัวเอง", "Ourselves / Self", "Tua eng", "Greetings", "ดูแลตัวเองด้วยนะ", "Take care of yourself."),
            Vocabulary(31, "อรุณสวัสดิ์", "Good morning", "Arun sawat", "Greetings", "อรุณสวัสดิ์ทุกคน", "Good morning, everyone."),
            Vocabulary(32, "สวัสดีตอนบ่าย", "Good afternoon", "Sawatdee ton bai", "Greetings", "สวัสดีตอนบ่ายครับคุณครู", "Good afternoon, teacher."),
            Vocabulary(33, "สวัสดีตอนเย็น", "Good evening", "Sawatdee ton yen", "Greetings", "สวัสดีตอนเย็นครับพ่อ", "Good evening, father."),
            Vocabulary(34, "วันนี้", "Today", "Wan nee", "Greetings", "วันนี้ฝนตกหนัก", "Today it is raining heavily."),
            Vocabulary(35, "พรุ่งนี้", "Tomorrow", "Phrung nee", "Greetings", "พรุ่งนี้เจอกันนะครับ", "See you tomorrow."),
            Vocabulary(36, "เมื่อวาน", "Yesterday", "Muea wan", "Greetings", "เมื่อวานสนุกมาก", "Yesterday was very fun."),
            Vocabulary(37, "คืนนี้", "Tonight", "Khuen nee", "Greetings", "คืนนี้เจอกันที่ร้านนะ", "See you tonight at the shop."),
            Vocabulary(38, "ตอนเช้า", "Morning", "Ton chao", "Greetings", "ฉันชอบตื่นตอนเช้า", "I like to wake up in the morning."),
            Vocabulary(39, "ตอนเย็น", "Evening", "Ton yen", "Greetings", "เราไปเล่นที่สวนตอนเย็น", "We go play at the park in the evening."),
            Vocabulary(40, "สัปดาห์", "Week", "Sapdah", "Greetings", "สัปดาห์หน้าสู้ใหม่", "Try again next week."),
            Vocabulary(41, "สบาย", "Comfortable / Easy", "Sabai", "Greetings", "เบาะนี้นั่งสบายมาก", "This seat is very comfortable."),
            Vocabulary(42, "เหนื่อย", "Tired", "Neuay", "Greetings", "เดินทางเหนื่อยมากไหม", "Are you very tired from traveling?"),
            Vocabulary(43, "ง่วง", "Sleepy", "Nguang", "Greetings", "ง่วงนอนแล้ว ราตรีสวัสดิ์", "Sleepy already, good night."),
            Vocabulary(44, "มีความสุข", "Happy", "Mee khwam suk", "Greetings", "ขอให้มีความสุขมากๆ", "Wish you to be very happy."),
            Vocabulary(45, "เศร้า", "Sad", "Sao", "Greetings", "อย่าเศร้าไปเลยชีวิตเริ่มใหม่ได้", "Don't be sad, life can start anew."),
            Vocabulary(46, "โกรธ", "Angry", "Krot", "Greetings", "เขาโกรธที่ฉันมาสาย", "He was angry because I was late."),
            Vocabulary(47, "สนุก", "Fun", "Sanuk", "Greetings", "เรียนคุยภาษาไทยสนุกจัง", "Learning to speak Thai is so much fun!"),
            Vocabulary(48, "น่ารัก", "Cute", "Na rak", "Greetings", "หมาตัวนี้น่ารักมากนะ", "This dog is very cute."),
            Vocabulary(49, "ใจดี", "Kind", "Chai dee", "Greetings", "คุณยายใจดีเสมอกับหลาน", "Grandmother is always kind to grandchildren."),
            Vocabulary(50, "สวย", "Beautiful", "Suay", "Greetings", "ประเทศไทยสวยงามมาก", "Thailand is very beautiful."),
            Vocabulary(51, "พูด", "Speak", "Phut", "Greetings", "กรุณาพูดช้าๆ ครับ", "Please speak slowly."),
            Vocabulary(52, "ฟัง", "Listen", "Fang", "Greetings", "ฟังฉันหน่อยได้ไหม", "Can you listen to me please?"),
            Vocabulary(53, "อ่าน", "Read", "An", "Greetings", "เด็กๆ ชอบอ่านหนังสือการ์ตูน", "Children like to read comic books."),
            Vocabulary(54, "เขียน", "Write", "Khian", "Greetings", "เขียนชื่อของคุณตรงนี้", "Write your name here."),
            Vocabulary(55, "เข้าใจ", "Understand", "Khao chai", "Greetings", "ฉันเข้าใจภาษาไทยแล้ว", "I understand Thai language now."),
            Vocabulary(56, "ไม่เข้าใจ", "Do not understand", "Mai khao chai", "Greetings", "ขอโทษค่ะฉันไม่เข้าใจ", "Sorry, I don't understand."),
            Vocabulary(57, "รู้", "Know", "Ru", "Greetings", "เขารู้คำตอบถูกต้อง", "He knows the correct answer."),
            Vocabulary(58, "ไม่รู้", "Do not know", "Mai ru", "Greetings", "เรื่องนี้ฉันไม่รู้จริงๆ", "About this, I really do not know."),
            Vocabulary(59, "ถาม", "Ask", "Tham", "Greetings", "มีคำถามอะไรอยากถามไหม", "Do you have any questions to ask?"),
            Vocabulary(60, "ตอบ", "Answer", "Top", "Greetings", "คุณตอบคำถามได้ยอดเยี่ยม", "You answered the question excellently."),
            Vocabulary(61, "ช่วย", "Help", "Chuay", "Greetings", "ช่วยฉันด้วยครับมีเรื่อง", "Please help me, something happened."),
            Vocabulary(62, "ได้", "Can / Okay", "Dai", "Greetings", "พูดแบบนี้ได้ไหม", "Can you say it like this?"),
            Vocabulary(63, "ไม่ได้", "Cannot", "Mai dai", "Greetings", "ทำแบบนั้นไม่ได้เด็ดขาด", "Absolutely cannot do like that."),
            Vocabulary(64, "ได้ไหม", "Can you?", "Dai mai", "Greetings", "ขอนั่งตรงนี้ได้ไหมครับ", "Can I sit here?"),
            Vocabulary(65, "ขอ", "Please give me / Ask for", "Kho", "Greetings", "ขอเมนูอาหารไทยหน่อยครับ", "Please give me the Thai food menu."),
            Vocabulary(66, "อยาก", "Want to", "Yak", "Greetings", "ฉันอยากกินต้มยำกุ้ง", "I want to eat Tom Yum Goong."),
            Vocabulary(67, "ต้องการ", "Need / Require", "Tong karn", "Greetings", "ต้องการเพื่อนช่วยทำงาน", "Need friends to help at work."),
            Vocabulary(68, "กรุณา", "Please", "Karuna", "Greetings", "กรุณาต่อคิวตรงนี้นะครับ", "Please queue here."),
            Vocabulary(69, "เชิญ", "Invite / Please enter", "Choen", "Greetings", "เชิญนั่งเก้าอี้เสมอนะครับ", "Please sit on the chair."),
            Vocabulary(70, "ยินดีต้อนรับ", "Welcome", "Yindee ton rap", "Greetings", "ยินดีต้อนรับสู่กรุงเทพ", "Welcome to Bangkok."),
            Vocabulary(71, "อะไร", "What", "Arai", "Greetings", "นี่คือราคาอะไรครับ", "What is this price?"),
            Vocabulary(72, "ที่ไหน", "Where", "Thee nai", "Greetings", "ห้องน้ำอยู่ที่ไหนครับ", "Where is the restroom?"),
            Vocabulary(73, "เมื่อไหร่", "When", "Muea rai", "Greetings", "รถไฟจะมาถึงเมื่อไหร่", "When will the train arrive?"),
            Vocabulary(74, "ทำไม", "Why", "Tham mai", "Greetings", "ทำไมคุณไม่คุยกับฉัน", "Why don't you chat with me?"),
            Vocabulary(75, "อย่างไร", "How", "Yang rai", "Greetings", "ทำคำคอบอย่างไรให้ถูก", "How to make the answer correct?"),
            Vocabulary(76, "ใคร", "Who", "Khrai", "Greetings", "ใครชอบกินทุเรียนบ้าง", "Who likes to eat durian?"),
            Vocabulary(77, "เท่าไหร่", "How much", "Tao rai", "Greetings", "น้ำเปล่าขวดละเท่าไหร่", "How much is a bottle of water?"),
            Vocabulary(78, "อันไหน", "Which one", "An nai", "Greetings", "อยากได้รองเท้าอันไหนครับ", "Which pair of shoes do you want?"),
            Vocabulary(79, "กี่", "How many", "Kee", "Greetings", "ต้องการไข่ดาวกี่ฟอง", "How many fried eggs do you want?"),
            Vocabulary(80, "ไหม", "Question particle", "Mai", "Greetings", "คุณชอบกินหมูกรอบไหม", "Do you like to eat crispy pork?"),
            Vocabulary(81, "ใช่ไหม", "Right? / Is it?", "Chai mai", "Greetings", "ของฝากนี้ราคาถูกใช่ไหม", "This souvenir is cheap, right?"),
            Vocabulary(82, "หนาว", "Cold (weather)", "Nao", "Greetings", "คืนนี้อากาศหนาวมาก", "Tonight the weather is very cold."),
            Vocabulary(83, "ร้อน", "Hot", "Ron", "Greetings", "ชาไทยเย็นช่วยลดร้อนได้ดี", "Iced Thai tea helps reduce heat well."),
            Vocabulary(84, "ฝนตก", "Raining", "Fon tok", "Greetings", "ฝนตกนอนสบายมากๆ", "Raining makes sleeping very comfortable."),
            Vocabulary(85, "ลมแรง", "Windy", "Lom raeng", "Greetings", "ชายหาดตอนค่ำมีลมแรง", "The beach has strong wind at night."),
            Vocabulary(86, "แดดออก", "Sunny", "Daet ok", "Greetings", "แดดออกเที่ยวสนุกแต่ร้อน", "Sunny is fun for travel but hot."),
            Vocabulary(87, "ท้องฟ้า", "Sky", "Thong fah", "Greetings", "ท้องฟ้าวันนี้ใสสะอาดมาก", "The sky today is very clear and clean."),
            Vocabulary(88, "สภาพอากาศ", "Weather", "Saphap akat", "Greetings", "สภาพอากาศเมืองไทยร้อนชื้น", "Thailand's weather is hot and humid."),
            Vocabulary(89, "สบายดี", "Healthy / In good shape", "Sabai dee", "Greetings", "ร่างกายสบายดีไม่อ่อนแอ", "My body is healthy and not weak."),
            Vocabulary(90, "เจ็บ", "Hurt", "Chep", "Greetings", "ฉันเจ็บสะพานเท้าเพราะเดินเยอะ", "My feet hurt from walking a lot."),
            Vocabulary(91, "จริง", "Real / True", "Ching", "Greetings", "นี่เป็นเรื่องจริงแน่นอน", "This is definitely a true story."),
            Vocabulary(92, "ไม่จริง", "Not true", "Mai ching", "Greetings", "ข่าวแบบนี้ไม่จริงเสมอนะ", "News like this is not always true."),
            Vocabulary(93, "แน่นอน", "Of course / Definitely", "Nae non", "Greetings", "ฉันรักคุณแน่นอนที่สุด", "I love you definitely the most."),
            Vocabulary(94, "อาจจะ", "Maybe", "At cha", "Greetings", "พรุ่งนี้ฝนอาจจะตกนะ", "Tomorrow it might rain."),
            Vocabulary(95, "เห็นด้วย", "Agree", "Hen duay", "Greetings", "ฉันเห็นด้วยกับทางเลือกนี้", "I agree with this choice."),
            Vocabulary(96, "ไม่เห็นด้วย", "Disagree", "Mai hen duay", "Greetings", "เขาไม่เห็นด้วยเรื่องซื้อของแพง", "He disagreed with buying expensive things."),
            Vocabulary(97, "เก่ง", "Smart / Good at", "Keng", "Greetings", "นักเรียนห้องนี้เรียนเก่งทุกคน", "The students in this room are all smart."),
            Vocabulary(98, "ถูกต้อง", "Correct", "Thook tong", "Greetings", "คำตอบแบบนั้นถูกต้องที่สุด", "An answer like that is the most correct."),
            Vocabulary(99, "เกือบ", "Almost", "Kueap", "Greetings", "หมากัดขาฉันเกือบโดนแล้ว", "The dog almost bit my leg."),
            Vocabulary(100, "ดีใจ", "Glad / Pleased", "Dee chai", "Greetings", "ดีใจด้วยที่มีความสุข", "Glad for you to be happy.")
        )
    }

    private fun getFoodVocabulary(): List<Vocabulary> {
        return listOf(
            Vocabulary(101, "ข้าว", "Rice", "Khao", "Food", "ฉันชอบกินข้าวเหนียว", "I like to eat sticky rice."),
            Vocabulary(102, "น้ำ", "Water", "Nam", "Food", "ขอน้ำเปล่าแก้วหนึ่งครับ", "Please give me a glass of water."),
            Vocabulary(103, "อาหาร", "Food", "Ahan", "Food", "อาหารไทยอร่อยมาก", "Thai food is very delicious."),
            Vocabulary(104, "ต้มยำกุ้ง", "Spicy shrimp soup", "Tom yum goong", "Food", "ต้มยำกุ้งหม้อนี้เผ็ดมาก", "This pot of Tom Yum Goong is very spicy."),
            Vocabulary(105, "ผัดไทย", "Stir-fried noodles", "Pad Thai", "Food", "สั่งผัดไทยหนึ่งจานครับ", "Order one plate of Pad Thai, please."),
            Vocabulary(106, "ส้มตำ", "Papaya salad", "Som tam", "Food", "ส้มตำไทยไม่ใส่พริก", "Thai papaya salad without chili."),
            Vocabulary(107, "ผลไม้", "Fruit", "Phonlamai", "Food", "ผลไม้ไทยมีหลายชนิด", "There are many kinds of Thai fruits."),
            Vocabulary(108, "กาแฟ", "Coffee", "Kafae", "Food", "ฉันดื่มกาแฟร้อนตอนเช้า", "I drink hot coffee in the morning."),
            Vocabulary(109, "อร่อย", "Delicious", "Aroy", "Food", "ทุเรียนนี้อร่อยมาก", "This durian is very delicious."),
            Vocabulary(110, "เผ็ด", "Spicy", "Phet", "Food", "แกงเขียวหวานเผ็ดไหม", "Is the green curry spicy?"),
            Vocabulary(111, "หิว", "Hungry", "Hiw", "Food", "ตอนนี้ฉันหิวข้าวแล้ว", "I am hungry for rice now."),
            Vocabulary(112, "กิน", "Eat", "Kin", "Food", "ไปกินข้าวกันเถอะ", "Let's go eat rice/food."),
            Vocabulary(113, "แกงเขียวหวาน", "Green curry", "Kaeng khiao wan", "Food", "ฉันกินแกงเขียวหวานกับข้าว", "I eat green curry with rice."),
            Vocabulary(114, "ไก่", "Chicken", "Kai", "Food", "ชอบกินไก่ย่างมาก", "I like to eat grilled chicken very much."),
            Vocabulary(115, "ชา", "Tea", "Cha", "Food", "ขอดื่มชาร้อนครับ", "I'd like to drink hot tea, please."),
            Vocabulary(116, "หมู", "Pork", "Moo", "Food", "ผัดกะเพราหมูสับไข่ดาว", "Stir-fried pork with holy basil and fried egg."),
            Vocabulary(117, "ปลา", "Fish", "Pla", "Food", "ฉันชอบกินปลาทอด", "I like to eat fried fish."),
            Vocabulary(118, "ไข่", "Egg", "Khai", "Food", "ไข่เจียวร้อนๆ อร่อยดี", "Hot omelet is delicious."),
            Vocabulary(119, "หวาน", "Sweet", "Wan", "Food", "ผลไม้นี้หวานมาก", "This fruit is very sweet."),
            Vocabulary(120, "ดื่ม", "Drink", "Duem", "Food", "เด็กๆ ดื่มนมทุกวัน", "Children drink milk every day."),
            Vocabulary(121, "เกลือ", "Salt", "Klua", "Food", "แกงจืดใส่เกลือเล็กน้อย", "Mild soup with a pinch of salt."),
            Vocabulary(122, "น้ำตาล", "Sugar", "Nam tan", "Food", "กาแฟใส่น้ำตาลหนึ่งช้อน", "Coffee with one spoonful of sugar."),
            Vocabulary(123, "พริก", "Chili", "Phrik", "Food", "พริกขี้หนูเผ็ดร้อนแรง", "Bird's eye chili is very spicy hot."),
            Vocabulary(124, "มะนาว", "Lime", "Ma nao", "Food", "บีบมะนาวใส่ส้มตำไทย", "Squeeze lime into Thai papaya salad."),
            Vocabulary(125, "มะพร้าว", "Coconut", "Ma phrao", "Food", "น้ำมะพร้าวหอมหวานเย็นชื่นใจ", "Coconut water is sweet, fragrant, and refreshing."),
            Vocabulary(126, "กระเทียม", "Garlic", "Kra thiam", "Food", "ผัดผักบุ้งใส่กระเทียมเยอะๆ", "Stir-fried morning glory with lots of garlic."),
            Vocabulary(127, "หอมแดง", "Shallot", "Hom daeng", "Food", "หอมแดงซอยในยำหมูยอ", "Sliced shallots in white pork sausage spicy salad."),
            Vocabulary(128, "กะปิ", "Shrimp paste", "Ka pi", "Food", "น้ำพริกกะปิกินกับปลาทู", "Shrimp paste chili dip eaten with mackerel."),
            Vocabulary(129, "น้ำปลา", "Fish sauce", "Nam pla", "Food", "พริกน้ำปลาถ้วยนี้อร่อย", "This cup of chili fish sauce is delicious."),
            Vocabulary(130, "ซอส", "Sauce", "Sos", "Food", "ทอดไก่กินกับซอสมะเขือเทศ", "Fried chicken eaten with tomato sauce."),
            Vocabulary(131, "เนื้อวัว", "Beef", "Nua wua", "Food", "คนไทยบางคนไม่กินเนื้อวัว", "Some Thai people don't eat beef."),
            Vocabulary(132, "กุ้ง", "Shrimp", "Kung", "Food", "ฉันแกะเปลือกกุ้งเผาให้แฟน", "I peel grilled shrimp shell for my partner."),
            Vocabulary(133, "ปู", "Crab", "Pu", "Food", "ส้มตำปูม้าอร่อยมากๆ", "Blue crab papaya salad is extremely delicious."),
            Vocabulary(134, "หอย", "Shellfish", "Hoi", "Food", "หอยลายผัดน้ำพริกเผาเผ็ดหวาน", "Stir-fried clams with sweet chili paste."),
            Vocabulary(135, "หมูกรอบ", "Crispy pork", "Moo krop", "Food", "คะน้าหมูกรอบราดข้าวอร่อยดี", "Stir-fried Chinese broccoli with crispy pork over rice is good."),
            Vocabulary(136, "เป็ด", "Duck", "Pet", "Food", "บะหมี่เป็ดย่างจานร้อน", "Hot plate roasted duck egg noodles."),
            Vocabulary(137, "เต้าหู้", "Tofu", "Tao hu", "Food", "แกงจืดเต้าหู้หมูสับสาหร่าย", "Clear soup with tofu, minced pork, and seaweed."),
            Vocabulary(138, "นม", "Milk", "Nom", "Food", "ดื่มนมอุ่นๆ ก่อนนอนหลับ", "Drink warm milk before sleeping."),
            Vocabulary(139, "เนย", "Butter", "Noey", "Food", "ทาเนยบนขนมปังปิ้งกรอบ", "Spread butter on crispy toasted bread."),
            Vocabulary(140, "ชีส", "Cheese", "Chees", "Food", "พิซซ่าถาดนี้ใส่ชีสเยอะมาก", "This pizza tray has a lot of cheese."),
            Vocabulary(141, "เบียร์", "Beer", "Beer", "Food", "เบียร์เย็นๆ เหมาะสำหรับวันหยุด", "Cold beer is perfect for holidays."),
            Vocabulary(142, "ไวน์", "Wine", "Wine", "Food", "ดื่มไวน์แดงคลาสสิกคู่เนื้อ", "Drink classic red wine with beef."),
            Vocabulary(143, "น้ำส้ม", "Orange juice", "Nam som", "Food", "คั้นน้ำส้มสดๆ ดื่มตอนเช้า", "Squeeze fresh orange juice to drink in the morning."),
            Vocabulary(144, "น้ำมะพร้าวอ่อน", "Young coconut water", "Nam ma phrao on", "Food", "น้ำมะพร้าวอ่อนเย็นสดชื่น", "Young coconut water is cold and refreshing."),
            Vocabulary(145, "โซดา", "Soda", "Soda", "Food", "แดงมะนาวโซดาหวานซ่าสะใจ", "Red sweet lime soda is bubbly and satisfying."),
            Vocabulary(146, "น้ำแข็ง", "Ice", "Nam khaeng", "Food", "ขอน้ำแข็งเพิ่มหนึ่งถัง", "Please add one bucket of ice."),
            Vocabulary(147, "ชาร้อน", "Hot tea", "Cha ron", "Food", "ดื่มชาร้อนเพื่อสุขภาพดี", "Drink hot tea for good health."),
            Vocabulary(148, "ชาเย็น", "Thai iced tea", "Cha yen", "Food", "สั่งชาเย็นแก้วใหญ่หวานน้อย", "Order a large cup of Thai iced tea with less sugar."),
            Vocabulary(149, "นมเย็น", "Pink milk", "Nom yen", "Food", "นมเย็นสีชมพูหวานถูกใจเด็กๆ", "Sweet pink milk is favored by kids."),
            Vocabulary(150, "สุรา", "Alcohol", "Sura", "Food", "ห้ามดื่มสุราขณะขับขี่รถยนต์", "Forbidden to drink alcohol while driving cars."),
            Vocabulary(151, "กล้วย", "Banana", "Kluai", "Food", "กล้วยหอมช่วยเพิ่มพลังงานรีบ", "Fragrant bananas help quickly boost energy."),
            Vocabulary(152, "มะม่วง", "Mango", "Ma muang", "Food", "มะม่วงอกร่องกินกับข้าวเหนียวมูน", "Ok-rong mango eaten with sweet sticky rice."),
            Vocabulary(153, "สับปะรด", "Pineapple", "Sap pa rot", "Food", "ผัดเปรี้ยวหวานใส่สับปะรดเปรี้ยว", "Stir-fried sweet and sour dish with sour pineapple."),
            Vocabulary(154, "ทุเรียน", "Durian", "Thu rian", "Food", "ทุเรียนหมอนทองกลิ่นหอมฟุ้ง", "Monthong durian scent is very fragrant."),
            Vocabulary(155, "มังคุด", "Mangosteen", "Mang khut", "Food", "มังคุดเป็นราชินีแห่งผลไม้คู่ทุเรียน", "Mangosteen is the queen of fruits paired with durian."),
            Vocabulary(156, "แตงโม", "Watermelon", "Taeng mo", "Food", "แตงโมปั่นแก้วนี้สีแดงหวานเจี๊ยบ", "This blended watermelon cup is sweet and red."),
            Vocabulary(157, "ส้ม", "Orange", "Som", "Food", "แกะส้มหวานกินริมทะเล", "Peel sweet oranges to eat along the sea."),
            Vocabulary(158, "มะละกอ", "Papaya", "Ma la ko", "Food", "ใช้มะละกอดิบตำส้มตำปูปลาร้า", "Use raw papaya to pound papaya salad with crab."),
            Vocabulary(159, "แอปเปิ้ล", "Apple", "Apple", "Food", "ปอกแอปเปิ้ลแดงแช่น้ำเกลือสะอาด", "Peel clean red apples soaked in saltwater."),
            Vocabulary(160, "มะพร้าวเผา", "Roasted coconut", "Ma phrao phao", "Food", "น้ำมะพร้าวเผาหอมอร่อยสะพาน", "Roasted coconut water is delightfully aromatic."),
            Vocabulary(161, "ต้ม", "Boil", "Tom", "Food", "ต้มจืดวุ้นเส้นเต้าหู้หมูสับ", "Boil clear vermicelli soup with tofu and minced pork."),
            Vocabulary(162, "ผัด", "Stir-fry", "Phat", "Food", "ผัดกะเพราเมนูยอดฮิตคนไทย", "Stir-fried holy basil is Thais' most popular menu."),
            Vocabulary(163, "แกง", "Curry", "Kaeng", "Food", "แกงมัสมั่นไก่รสเข้มข้นกลิ่นหอม", "Massaman chicken curry with rich flavor and aroma."),
            Vocabulary(164, "ทอด", "Deep-fry", "Thot", "Food", "ทอดปลาทับทิมกรอบสะใจจิ้มแจ่ว", "Deep-fry ruby fish till crispy, dip in spicy sauce."),
            Vocabulary(165, "ย่าง", "Grill", "Yang", "Food", "หมูย่างเสียบไม้ขายดีหน้าโรงเรียน", "Grilled pork skewers sell well in front of schools."),
            Vocabulary(166, "นึ่ง", "Steam", "Nueng", "Food", "ปลากระพงนึ่งมะนาวเปรี้ยวเผ็ดแซ่บ", "Steamed sea bass with lime, sour, spicy, and delicious."),
            Vocabulary(167, "อบ", "Bake / Roast", "Op", "Food", "ข้าวอบสับปะรดหอมกรุ่นน่ากิน", "Pineapple baked rice is freshly aromatic and tasty."),
            Vocabulary(168, "สับ", "Chop", "Sap", "Food", "สับหมูเนื้อละเอียดทำแกงจืด", "Chop pork meat finely to make clear soup."),
            Vocabulary(169, "ตำ", "Pound", "Tam", "Food", "ตำน้ำพริกหนุ่มเผ็ดกำลังดีคุณยาย", "Pound Northern green chili paste with moderate spiciness."),
            Vocabulary(170, "ยำ", "Spicy salad", "Yam", "Food", "ยำวุ้นเส้นทะเลรสเปรี้ยวเค็มหวาน", "Spicy glass noodle seafood salad with sour, salty, sweet taste."),
            Vocabulary(171, "เส้นก๋วยเตี๋ยว", "Noodle strands", "Sen kuay tiao", "Food", "ลวกเส้นก๋วยเตี๋ยวเส้นใหญ่เหนียวนุ่ม", "Scald large flat noodles till chewy-tender."),
            Vocabulary(172, "บะหมี่สำเร็จรูป", "Instant noodles", "Ba mee samretrup", "Food", "ต้มบะหมี่สำเร็จรูปตอนนอนตื่นสาย", "Boiled instant noodles when waking up late."),
            Vocabulary(173, "ข้าวเหนียว", "Sticky rice", "Khao niao", "Food", "กินไก่ย่างส้มตำคู่ข้าวเหนียวอุ่นๆ", "Eat grilled chicken and papaya salad with warm sticky rice."),
            Vocabulary(174, "ข้าวผัดปู", "Crab fried rice", "Khao phat pu", "Food", "ข้าวผัดปูจานใหญ่บีบมะนาว", "Large plate of crab fried rice squeezed with lime."),
            Vocabulary(175, "ผัดผักบุ้งไฟแดง", "Stir-fried morning glory", "Phat phak bung", "Food", "ผัดผักบุ้งไฟแดงกรอบเค็มอร่อย", "Stir-fried morning glory is crispy, salty, and yummy."),
            Vocabulary(176, "ไข่เจียว", "Omelet", "Khai chiao", "Food", "ไข่เจียวหมูสับทอดกรอบฟูราดข้าว", "Crispy minced pork omelet over rice."),
            Vocabulary(177, "ไข่ดาว", "Fried egg", "Khai dao", "Food", "ไข่ดาวขอบกรอบไข่แดงไม่สุกเยิ้ม", "Crispy-edged fried egg with runny yolk."),
            Vocabulary(178, "ไข่ต้ม", "Boiled egg", "Khai tom", "Food", "กินไข่ต้มครึ่งซีกกับน้ำปลาพริก", "Eat half a boiled egg with chili fish sauce."),
            Vocabulary(179, "ซุปก้อน", "Soup cube", "Soup", "Food", "โยนซุปก้อนลงหม้อต้มน้ำเดือดพล่าน", "Throw a soup cube into the boiling water pot."),
            Vocabulary(180, "ขนมหวาน", "Dessert", "Khanom wan", "Food", "กินขนมหวานบัวลอยไข่หวานหลังอาหาร", "Eat sweet Bua Loy with sweet egg after the meal."),
            Vocabulary(181, "จาน", "Plate", "Chan", "Food", "วางจานหรูสีขาวบนโต๊ะอาหารสะอาด", "Place a white luxury plate on the clean dining table."),
            Vocabulary(182, "ชาม", "Bowl", "Cham", "Food", "ล้างชามใส่แกงพะแนงสีแดงหอมฉุย", "Wash the bowl containing red fragrant Panang curry."),
            Vocabulary(183, "ช้อน", "Spoon", "Chon", "Food", "ตักข้าวสวยด้วยช้อนพลาสติกแข็งสีฟ้า", "Scoop jasmine rice with a hard blue plastic spoon."),
            Vocabulary(184, "ส้อม", "Fork", "Som", "Food", "ใช้ส้อมจิ้มผลไม้ต้มยำกุ้งกินสะดวก", "Use a fork to poke fruited chunks to eat easily."),
            Vocabulary(185, "ตะเกียบ", "Chopsticks", "Ta kiap", "Food", "คีบบะหมี่ลูกชิ้นปลาด้วยตะเกียบยาว", "Pinch fish ball noodles with long chopsticks."),
            Vocabulary(186, "แก้วน้ำ", "Glass", "Kaew nam", "Food", "รินน้ำแข็งใสแก้วน้ำแก้วนี้ชื่นใจ", "Pour ice into this glass, refreshing."),
            Vocabulary(187, "ถ้วย", "Cup / Small bowl", "Thuay", "Food", "ขอถ้วยเล็กแบ่งต้มยำกุ้งหน่อย", "Request a small cup to share Tom Yum Goong."),
            Vocabulary(188, "มีด", "Knife", "Meet", "Food", "ใช้มีดทู่ๆ ปลอกทุเรียนยากลำบาก", "Using a blunt knife to peel durian is hard."),
            Vocabulary(189, "หลอด", "Straw", "Lot", "Food", "ดูดโกโก้เย็นผ่านหลอดสีพาสเทล", "Sip cold cocoa through a pastel-colored straw."),
            Vocabulary(190, "กระดาษทิชชู", "Napkin / Tissue", "Tissue", "Food", "เช็ดปากที่เลอะด้วยกระดาษทิชชูขาว", "Wipe the messy mouth with a white napkin."),
            Vocabulary(191, "สั่งอาหาร", "Order food", "Sang", "Food", "พี่ยกมือสั่งอาหารเพิ่มสองเมนู", "Older brother raised hand to order two more dishes."),
            Vocabulary(192, "เก็บเงินด้วย", "Check bill please", "Check bill", "Food", "อิ่มแล้วครับ น้องเก็บเงินด้วยครับ", "Full already, check bill please, sibling."),
            Vocabulary(193, "รายการอาหาร", "Menu", "Menu", "Food", "หยิบรายการอาหารมาดูราคาแพงไหม", "Grab the menu list to see if the price is expensive."),
            Vocabulary(194, "ร้านอาหาร", "Restaurant", "Ran ahan", "Food", "ร้านอาหารครัวไทยริมน้ำลมโชยดี", "Thai kitchen riverside restaurant has good breeze."),
            Vocabulary(195, "พนักงานต้อนรับ", "Waiter / Host", "Phanak ngan", "Food", "พนักงานต้อนรับห้องอาหารพูดจาสุภาพ", "The restaurant waiter/host speaks very politely."),
            Vocabulary(196, "โต๊ะอาหาร", "Dining table", "To", "Food", "จัดโต๊ะอาหารสำหรับครอบครัวสิบที่นั่ง", "Set the dining table for family of ten seats."),
            Vocabulary(197, "เก้าอี้", "Chair", "Kao-i", "Food", "เก้าอี้ไม้นิ่มนั่งสบายผ่อนคลายเท้า", "Soft wooden chair is comfortable for relaxing feet."),
            Vocabulary(198, "ห่อกลับบ้าน", "Take away", "Sue klap ban", "Food", "อาหารกินไม่หมดขอห่อกลับบ้านหน่อย", "Food not finished, please wrap for take away."),
            Vocabulary(199, "รสชาติ", "Taste", "Rot chat", "Food", "แกงไตปลามีรสชาติจัดจ้านเผ็ดสะใจ", "Fish kidney curry has intense and pleasingly spicy taste."),
            Vocabulary(200, "เผ็ดน้อย", "Less spicy", "Phet noi", "Food", "แม่ครัวส้มตำขอรสชาตเผ็ดน้อยใจดี", "Cook of papaya salad, please make it less spicy, thank you.")
        )
    }

    private fun getNumbersVocabulary(): List<Vocabulary> {
        return listOf(
            Vocabulary(201, "หนึ่ง", "One", "Nung", "Numbers", "แมวหนึ่งตัว", "One cat."),
            Vocabulary(202, "สอง", "Two", "Song", "Numbers", "ขอเบียร์สองขวดครับ", "Two bottles of beer, please."),
            Vocabulary(203, "สาม", "Three", "Sam", "Numbers", "มีเวลาสามวัน", "Have three days."),
            Vocabulary(204, "สี่", "Four", "See", "Numbers", "สี่สิบห้าบาท", "Forty-five Baht."),
            Vocabulary(205, "ห้า", "Five", "Ha", "Numbers", "บวกอีกห้าบาทครับ", "Add five more Baht, please."),
            Vocabulary(206, "สิบ", "Ten", "Sip", "Numbers", "ราคาเก้าสิบเก้าบาท", "Price ninety-nine Baht."),
            Vocabulary(207, "ร้อย", "Hundred", "Roi", "Numbers", "หนึ่งร้อยบาทพอดี", "One hundred Baht exactly."),
            Vocabulary(208, "บาท", "Baht", "Baht", "Numbers", "จานละห้าสิบบาท", "Fifty Baht per plate."),
            Vocabulary(209, "ราคา", "Price", "Rakha", "Numbers", "ราคาเท่าไหร่ครับ", "What is the price?"),
            Vocabulary(210, "แพง", "Expensive", "Phaeng", "Numbers", "ของฝากนี้แพงมาก", "This souvenir is very expensive."),
            Vocabulary(211, "ถูก", "Cheap / Correct", "Thook", "Numbers", "เสื้อตัวนี้ราคาถูกดี", "This shirt is cheap / good price."),
            Vocabulary(212, "เท่าไหร่", "How much?", "Thao rai", "Numbers", "ส้มกิโลละเท่าไหร่ครับ", "How much per kilo of oranges?"),
            Vocabulary(213, "หก", "Six", "Hok", "Numbers", "มีไข่หกฟองในครัว", "There are six eggs in the kitchen."),
            Vocabulary(214, "เจ็ด", "Seven", "Chet", "Numbers", "ราคาเจ็ดสิบบาทครับ", "The price is seventy Baht."),
            Vocabulary(215, "แปด", "Eight", "Paet", "Numbers", "ทำงานแปดชั่วโมงต่อวัน", "Work eight hours per day."),
            Vocabulary(216, "เก้า", "Nine", "Kao", "Numbers", "ขอเก้าสิบบาททอนด้วย", "Keep ninety Baht, give change too."),
            Vocabulary(217, "ศูนย์", "Zero", "Soon", "Numbers", "คะแนนสอบเป็นศูนย์", "The exam score is zero."),
            Vocabulary(218, "เงิน", "Money", "Ngen", "Numbers", "ฉันไม่มีเงินเหลือเลย", "I don't have any money left at all."),
            Vocabulary(219, "เสื้อ", "Shirt", "Sua", "Numbers", "เสื้อตัวนี้ราคาถูกมาก", "This shirt is very cheap."),
            Vocabulary(220, "ซื้อ", "Buy", "Su", "Numbers", "ฉันอยากซื้อ fruit", "I want to buy some fruits."),
            Vocabulary(221, "สิบเอ็ด", "Eleven", "Sip-et", "Numbers", "มีส้มจีนสิบเอ็ดผลบนโต๊ะ", "There are eleven Mandarin oranges on the table."),
            Vocabulary(222, "สิบสอง", "Twelve", "Sip-song", "Numbers", "ขวดเบียร์สิบสองขวดในกล่องกระดาษ", "Twelve bottles of beer in the cardboard box."),
            Vocabulary(223, "ยี่สิบ", "Twenty", "Yee-sip", "Numbers", "ยี่สิบบาทซื้อมะม่วงลูกโตได้ไหม", "Can twenty Baht buy a big mango?"),
            Vocabulary(224, "ยี่สิบเอ็ด", "Twenty-one", "Yee-sip-et", "Numbers", "อายุครบยี่สิบเอ็ดปีบริบูรณ์วันเกิด", "Turned twenty-one years old on birthday."),
            Vocabulary(225, "สามสิบ", "Thirty", "Sam-sip", "Numbers", "ราคาอาหารสตรีทฟู้ดสามสิบบาท", "The street food price is thirty Baht."),
            Vocabulary(226, "สี่สิบ", "Forty", "See-sip", "Numbers", "วิ่งหนีหมาสี่สิบนาทีสะพานเหนื่อยเหน็ด", "Ran away from dogs for forty minutes, exhausted."),
            Vocabulary(227, "ห้าสิบ", "Fifty", "Ha-sip", "Numbers", "แบงก์ห้าสิบบาทสีฟ้าลอยกลางลมฝน", "Blue fifty Baht note floated in rainy wind."),
            Vocabulary(228, "หกสิบ", "Sixty", "Hok-sip", "Numbers", "คุณตาวัยหกสิบปีชอบขับรถตุ๊กตุ๊ก", "Sixty-year-old grandfather likes driving tuk-tuks."),
            Vocabulary(229, "เจ็ดสิบ", "Seventy", "Chet-sip", "Numbers", "เจ็ดสิบบาทก็อิ่มแปล้กับข้าวแกงใต้", "Seventy Baht gets you full with Southern rice-curry."),
            Vocabulary(230, "แปดสิบ", "Eighty", "Paet-sip", "Numbers", "รองเท้ายางลดแปดสิบเปอร์เซ็นต์ราคาถูก", "Rubber shoes discount eighty percent, cheap price."),
            Vocabulary(231, "เก้าสิบ", "Ninety", "Kao-sip", "Numbers", "ตึกสูงเก้าสิบชั้นหน้าห้างสรรพสินค้า", "Ninety-story tall building in front of mall."),
            Vocabulary(232, "พัน", "Thousand", "Phan", "Numbers", "ราคาผ้าไหมหนึ่งพันบาทเนื้อดีละเอียด", "A thousand Baht silk cloth of fine weave."),
            Vocabulary(233, "หมื่น", "Ten thousand", "Muen", "Numbers", "เสียเงินหนึ่งหมื่นบาทมัดจำค่าเช่าห้องพัก", "Lost ten thousand Baht deposit for renting room."),
            Vocabulary(234, "แสน", "Hundred thousand", "Saen", "Numbers", "ของฝากทำด้วยทองราคาหนึ่งแสนบาทถ้วน", "Gold souvenir priced one hundred thousand Baht flat."),
            Vocabulary(235, "ล้าน", "Million", "Lan", "Numbers", "จังหวัดนี้มีประชากรถึงสองล้านคนยอดกัง", "This province has up to two million people."),
            Vocabulary(236, "ครึ่ง", "Half", "Khrueng", "Numbers", "ขอทุเรียนครึ่งซีกกินหวานมันหอมสะใจ", "Ask for half a durian piece, sweet and buttery."),
            Vocabulary(237, "คู่", "Pair", "Khu", "Numbers", "ซื้อตะเกียบไม้หนึ่งคู่เพื่อกินบะหมี่น้ำ", "Buy one pair of wooden chopsticks to eat noodle soup."),
            Vocabulary(238, "เดี่ยว", "Single / Solo", "Diao", "Numbers", "นอนห้องพักเดี่ยวราคาถูกประหยัดเงินในกระเป๋า", "Sleeping in single room cheap price saves wallet."),
            Vocabulary(239, "ทั้งหมด", "Total / All", "Thang mot", "Numbers", "รวมเงินทั้งหมดสี่ร้อยบาทพอดีไร้ขาดทอน", "Sum total money is four hundred Baht flat, no change needed."),
            Vocabulary(240, "น้อย", "Little / Few", "Noi", "Numbers", "มีเวลาเหลือน้อยขอนั่งบีทีเอสสะพานเร็ว", "Little time left, please take BTS quickly."),
            Vocabulary(241, "ของขวัญ", "Gift", "Khong khwan", "Numbers", "ซื้อของขวัญปีใหม่ส่งความรักให้แม่ใจดี", "Buy New Year gift to send love to kind mom."),
            Vocabulary(242, "ตลาดสด", "Fresh market", "Talat", "Numbers", "เดินตลาดสดเช้าคึกคักได้ปลายักษ์ย่าง", "Walk bustling morning fresh market, got giant grilled fish."),
            Vocabulary(243, "ห้างสรรพสินค้า", "Department store", "Hang", "Numbers", "ห้างสรรพสินค้าติดเครื่องปรับอากาศเย็นฉ่ำหน้าหนาว", "Department store with aircon is cold-chilled."),
            Vocabulary(244, "ร้านค้า", "Shop", "Ran kha", "Numbers", "ร้านค้าริมถนนปูพรมต้อนรับไกด์นำเที่ยว", "Roadside shop rolling carpet to welcome guide."),
            Vocabulary(245, "ถุงพลาสติก", "Plastic bag", "Thung", "Numbers", "ใส่ส้มตำไทยลงถุงพลาสติกเหนียวสะอาดมีสเกล", "Put papaya salad in clean tough plastic bag."),
            Vocabulary(246, "ลดสุดๆ", "Discounts / Lot Rakha", "Lot rakha", "Numbers", "ร้านค้าเสื้อยืดลดสุดๆ ราคาตัวยี่สิบบาท", "T-shirt shop has mega discounts, pricing twenty Baht."),
            Vocabulary(247, "แพงเกินไป", "Too expensive", "Phaeng koen pai", "Numbers", "อาหารจานนี้แพงเกินไปจ่ายไม่ไหวครับ", "This food plate is too expensive, cannot afford to pay."),
            Vocabulary(248, "ต่อราคาลด", "Bargain / Bargaining", "To rakha", "Numbers", "คนต่างชาติต่อยอดต่อราคาในตลาดสดไทย", "Foreigners bargaining prices in Thai fresh market."),
            Vocabulary(249, "จ่ายเงินสด", "Pay money / Pay", "Chai ngen", "Numbers", "จ่ายเงินสดซื้อแชมพูยาสีฟันสบู่ในห้าง", "Pay cash to buy shampoo, toothpaste, soap in mall."),
            Vocabulary(250, "ทอนเงินผิด", "Give change / Refund", "Thon ngen", "Numbers", "พนักงานทอนเงินผิดพลาดมากไปยี่สิบบาท", "Staff gave incorrect change, twenty Baht too much."),
            Vocabulary(251, "สีสัน", "Color", "See", "Numbers", "สภาพอากาศสดใสทำให้เสื้อมีสีสันสวยงาม", "Bright climate makes shirt colors beautiful."),
            Vocabulary(252, "สีแดง", "Red", "See daeng", "Numbers", "ขับรถยนต์สีแดงไปเที่ยวทะเลกับฝูงเพื่อน", "Drive red car to beach holiday with friend squad."),
            Vocabulary(253, "สีน้ำเงิน", "Blue", "See nam ngoen", "Numbers", "เสื้อยืดสีน้ำเงินตัวใหม่ราคาถูกใจพี่ชาย", "New blue cotton t-shirt pleased older brother."),
            Vocabulary(254, "สีเขียว", "Green", "See khiao", "Numbers", "แกงเขียวหวานไทยมีสีเขียวสว่างหอมสมุนไพร", "Green curry has a herbal bright green color."),
            Vocabulary(255, "สีเหลือง", "Yellow", "See lueang", "Numbers", "สับปะรดมะม่วงทุเรียนสุกเหลืองหอมหวานมัน", "Aromatic ripe pineapple, mango, durian are yellow."),
            Vocabulary(256, "สีขาว", "White", "See khao", "Numbers", "ใส่กางเกงกระโปรงสีขาวสะอาดยอดตาผู้ใหญ่", "Wearing clean white pants/skirts pleases elders."),
            Vocabulary(257, "สีดำ", "Black", "See dam", "Numbers", "รองเท้าแว่นตาเข็มขัดสีดำหรูระดับแบรนด์", "Luxury brand black shoes, glasses, belt."),
            Vocabulary(258, "สีชมพู", "Pink", "See chom phoo", "Numbers", "นมเย็นสีชมพูหวานประทับใจเด็กน้อยแก้มแดง", "Pink sweet milk impressed the red-cheeked kid."),
            Vocabulary(259, "สีส้ม", "Orange", "See som", "Numbers", "ผลส้มหวานเปลือกสีส้มลื่นสะพานมือ", "Sweet orange fruits have smooth orange skin."),
            Vocabulary(260, "สีม่วง", "Purple", "See muang", "Numbers", "ดอกไม้สีม่วงข้างรั่วบ้านสวยงามจนต้องถ่ายภาพ", "Purple flowers by home fence are lovely to shoot."),
            Vocabulary(261, "เสื้อยืด", "T-shirt", "Sua yuet", "Numbers", "ซื้อเสื้อยืดผ้าพริ้วบางเบาสบายระบายร้อน", "Buy thin breezy t-shirt comfortable for hot day."),
            Vocabulary(262, "กางเกงขายาว", "Pants / Trousers", "Kang keng", "Numbers", "กางเกงขายาวสีขาวกันแดดร้อนเผาขา", "White long pants shield legs from blazing sun."),
            Vocabulary(263, "กระโปรงยีนส์", "Skirt", "Kra prong", "Numbers", "น้องสาวสวมกระโปรงยีนส์ตัวใหม่ไปเดินห้าง", "Younger sister wore new denim skirt to mall."),
            Vocabulary(264, "รองเท้าผ้าใบ", "Shoes", "Rong thao", "Numbers", "ใส่รองเท้าผ้าใบคู่โปรดเดินชมวัดโพธิ์", "Wear favorite sneaker shoes to tour Wat Pho."),
            Vocabulary(265, "ถุงเท้าขนนุ่ม", "Socks", "Thung thao", "Numbers", "สวมถุงเท้าหนากักอากาศหนาวตอนนอนคืนนี้", "Wear thick warm socks against cold tonight."),
            Vocabulary(266, "หมวกฟาง", "Hat / Cap", "Muak", "Numbers", "หมวกฟางปีกกว้างช่วยบังแสงแดดจัดเต็มใบหน้า", "Straw hat blocks blazing sun on face."),
            Vocabulary(267, "แว่นตากันแดด", "Glasses / Spectacles", "Waen ta", "Numbers", "แว่นตากันดำสยบแดดจ้าสะท้อนผิวน้ำทะเล", "Sunglasses master blinding glares of sea wave."),
            Vocabulary(268, "กระเป๋าสะพาย", "Bag / Wallet", "Kra pao", "Numbers", "สะพายกระเป๋าหนังข้ามสะพานลอยสะบัดพวงกุญแจ", "Sling leather bag over flyover, keypad jingles."),
            Vocabulary(269, "เข็มขัดหนัง", "Belt", "Khem khat", "Numbers", "ดึงเข็มขัดหนังสลักตราห้าแสนล้านบาทหรู", "Tighten classic luxury branded leather belt."),
            Vocabulary(270, "เสื้อหนาว", "Sweater / Jacket", "Sua kan nao", "Numbers", "เสื้อหนาวหนาสีชมพูกันลมอ่อนพัดเฉียดใจ", "Thick pink sweater shields against chilling wind."),
            Vocabulary(271, "ใหญ่โต", "Big / Large", "Yai", "Numbers", "บ้านใหญ่โตมโหฬารโอบกอดทุกคนในครอบครัว", "Big lodge embraces entire big family warmly."),
            Vocabulary(272, "เล็กจิ๋ว", "Small / Tiny", "Lek", "Numbers", "แมวน้อยตัวเล็กจิ๋วนอนกลมกลิ้งกะละมังน้ำ", "Tiny kitten rolls inside plastic washbasin."),
            Vocabulary(273, "ยาวไกล", "Long (length)", "Yao", "Numbers", "ทางม้าลายสีขาวดำวาดยาวข้ามถนนแปดเลน", "Striped pedestrian crossing spans across long road."),
            Vocabulary(274, "สั้นกุด", "Short (length)", "San", "Numbers", "น้องสาวใส่กางเกงสั้นกุดดั่งแฟชั่นสตรีท", "Younger sister wears short shorts like street fashion."),
            Vocabulary(275, "น้ำหนักเยอะ", "Heavy (weight)", "Nak", "Numbers", "กระเป๋าเดินทางข้ามทวีปมีน้ำหนักเยอะมากเกินพิกัด", "Intercontinental luggage is extremely heavy."),
            Vocabulary(276, "บางเบา", "Light (weight)", "Bao", "Numbers", "ผลมะพร้าวแห้งแก่จัดแห้งจนมีน้ำหนักบางเบา", "The fully dried coconut is lightweight."),
            Vocabulary(277, "ใหม่เอี่ยม", "New", "Mai", "Numbers", "ซื้อกล้องคู่ไกด์ตัวใหม่เอี่ยมแกะกล่องสดๆ", "Bought a brand new out-of-the-box camera."),
            Vocabulary(278, "เก่าแก่", "Old (object)", "Kao", "Numbers", "วัดโพธิ์เก่าแก่มีนักท่องเที่ยวทั่วสนามบินมาเยือน", "Ancient old temple has global travelers visiting."),
            Vocabulary(279, "หนาเตอะ", "Thick", "Na", "Numbers", "อ่านหนังสือเล่มหนาเตอะจนง่วงราตรีสวัสดิ์", "Read thick book until sleepy, good night."),
            Vocabulary(280, "บางเฉียบ", "Thin / Flat", "Bang", "Numbers", "กระดาษทิชชูแผ่นบางเฉียบแต่เหนียวซึมซับดี", "Tissue sheet is thin but tough and absorbing."),
            Vocabulary(281, "เงินสด", "Cash", "Ngen sot", "Numbers", "ตลาดสดเล็กๆ ไม่รับสแกนต้องใช้เงินสดหมื่นบาท", "Tiny markets only take cash, no scanning."),
            Vocabulary(282, "บัตรเครดิต", "Credit card", "Bat khredit", "Numbers", "ห้างแบรนด์เนมสไลด์รูดบัตรเครดิตสะดวกมาก", "Mall slide-swipes credit card easily."),
            Vocabulary(283, "เงินทอน", "Give change / Refund", "Thon ngen", "Numbers", "ทอนเงินทอนครบถ้วนไร้กังวลยอดโกงเงิน", "Giving exact correct change, no fraud worries."),
            Vocabulary(284, "เหรียญ", "Change / Coins", "Rian", "Numbers", "โกยเหรียญและเศษเงินเงินทอนใส่กระเป๋าสะพายก้าว", "Stashed coins and change into sling-bag."),
            Vocabulary(285, "ตู้กดเงิน", "ATM", "ATM", "Numbers", "ตู้กดเงินเอบีเอ็มโชว์รหัสวิซ่าล่มชั่วคราว", "ATM displays service error during visa check."),
            Vocabulary(286, "โอนเงิน", "Transfer money", "On ngen", "Numbers", "โอนเงินเข้าบัญชีโรงพยาบาลช่วยผู้ป่วยเจ็บ", "Transfer money to hospital to help injured souls."),
            Vocabulary(287, "บัญชี", "Account", "Banchi", "Numbers", "ออมเงินล้านบาทลึกในบัญชีส่วนบุคคลมั่นคง", "Keep million Baht safe in individual account."),
            Vocabulary(288, "สแกน", "Scan", "Scan", "Numbers", "ช้อปปิ้งสแกนคิวอาร์สะดวกรวดเร็วไม่พกเงินสด", "Bargaining with QR scan, no cash needed."),
            Vocabulary(289, "สลิป", "Receipt / Slip", "Slip", "Numbers", "ส่งภาพถ่ายสลิปยืนยันการจ่ายเงินซื้อของฝาก", "Sent transfer receipt photo to back purchase."),
            Vocabulary(290, "ธนบัตร", "Bank note / Note", "Thonnabat", "Numbers", "พกธนบัตรหนาใบละหนึ่งพันบาทจ่ายค่าอาหารสะดวกดี", "Carrying thick banknotes makes paying for food convenient."),
            Vocabulary(291, "ของฝาก", "Souvenir / Gift", "Khong fak", "Numbers", "ซื้อพวงกุญแจตุ๊กตาช้างเป็นของฝากเพื่อนหมอ", "Bought elephant keychain souvenir for doctor friend."),
            Vocabulary(292, "เลือก", "Choose / Select", "Lueak", "Numbers", "เลือกเสื้อผ้าสีเหลืองสยบสีดำเรียบหรู", "Select yellow shirt over classic plain black."),
            Vocabulary(293, "ลอง", "Try on", "Long", "Numbers", "ลองแว่นตารองเท้าผ้าใบสะพายเป้เท่ระบาด", "Try on sunglasses, sneakers, backpack."),
            Vocabulary(294, "พวงกุญแจ", "Keychain", "Phuang kunchae", "Numbers", "พวงกุญแจไม้ทำรูปช้างราคาห้าสิบบาทขาดตัว", "Elephant wooden keychain priced fifty Baht flat."),
            Vocabulary(295, "โปสการ์ด", "Postcard", "Postcard", "Numbers", "บันเดิลกระดาษเขียนส่งโปสการ์ดสวดพรให้ยาย", "Wrote a lovely postcard to grandma wishing well."),
            Vocabulary(296, "ร่ม", "Umbrella", "Rom", "Numbers", "กางร่มกันแดดกลางสะพานลอยลมพัดแรงสะพัด", "Open umbrella to block sun on breezy walk."),
            Vocabulary(297, "สบู่", "Soap", "Sabu", "Numbers", "ถูสบู่เหลวเย็นซ่าล้างเนื้อตัวหลังลุยฝนตก", "Wash with cooling liquid soap after rain shower."),
            Vocabulary(298, "แชมพู", "Shampoo", "Shampoo", "Numbers", "สระนวดผมด้วยแชมพูมะกรูดสมุนไพรสดเปี่ยมสะอาด", "Wash hair with organic bergamot shampoo."),
            Vocabulary(299, "แปรงสีฟัน", "Toothbrush", "Praeng see fan", "Numbers", "เปลี่ยนแปรงสีฟันอันใหม่นุ่มสบายเหงือกฟัน", "Swapped to a brand new soft-bristle toothbrush."),
            Vocabulary(300, "ยาสีฟัน", "Toothpaste", "Ya see fan", "Numbers", "บีบยาสีฟันรสชาติสะระแหน่เย็นซ่าฟองหอมฟุ้ง", "Squeezed minty-fresh cooling toothpaste.")
        )
    }

    private fun getTravelVocabulary(): List<Vocabulary> {
        return listOf(
            Vocabulary(301, "โรงแรม", "Hotel", "Rong raem", "Travel", "โรงแรมนี้น่าอยู่มาก", "This hotel is very nice to stay."),
            Vocabulary(302, "สนามบิน", "Airport", "Sanam bin", "Travel", "ตั๋วไปสนามบินสุวรรณภูมิ", "A ticket to Suvarnabhumi Airport."),
            Vocabulary(303, "สถานี", "Station", "Sathani", "Travel", "ถามทางไปสถานีรถไฟ", "Ask directions to the railway station."),
            Vocabulary(304, "ห้องน้ำ", "Restroom", "Hong nam", "Travel", "ห้องน้ำอยู่ข้างหลังครับ", "The restroom is in the back."),
            Vocabulary(305, "เลี้ยวซ้าย", "Turn left", "Liew sai", "Travel", "เลี้ยวซ้ายตรงสี่แยกหน้า", "Turn left at the next intersection."),
            Vocabulary(306, "เลี้ยวขวา", "Turn right", "Liew khwa", "Travel", "เลี้ยวขวาที่หน้าวัด", "Turn right in front of the temple."),
            Vocabulary(307, "ไป", "Go", "Pai", "Travel", "อยากไปเที่ยวภูเก็ต", "Want to travel to Phuket."),
            Vocabulary(308, "ที่ไหน", "Where?", "Thee nai", "Travel", "บ้านของคุณอยู่ที่ไหน", "Where is your house?"),
            Vocabulary(309, "แผนที่", "Map", "Phaen thee", "Travel", "เปิดแผนที่ในมือถือ", "Open the map on the phone."),
            Vocabulary(310, "รถตุ๊กตุ๊ก", "Tuk-Tuk", "Rot tuk-tuk", "Travel", "นั่งรถตุ๊กตุ๊กเที่ยวสนุกดี", "Riding a tuk-tuk is fun to tour around."),
            Vocabulary(311, "รถไฟ", "Train", "Rot fai", "Travel", "ฉันชอบนั่งรถไฟไปเที่ยว", "I like to take the train to travel."),
            Vocabulary(312, "วัด", "Temple", "Wat", "Travel", "วัดโพธิ์สวยงามมาก", "Wat Pho is very beautiful."),
            Vocabulary(313, "บ้าน", "House / Home", "Ban", "Travel", "ฉันอยากกลับบ้านแล้ว", "I want to go home already."),
            Vocabulary(314, "เลี้ยว", "Turn", "Liew", "Travel", "เลี้ยวตรงมุมนั้นเลยครับ", "Turn right at that corner."),
            Vocabulary(315, "ตรงไป", "Go straight", "Trong pai", "Travel", "ขับตรงไปอีกสองร้อยเมตร", "Drive straight for another two hundred meters."),
            Vocabulary(316, "ไกล", "Far", "Klai", "Travel", "สถานีรถไฟอยู่ไกลจากที่นี่ไหม", "Is the railway station far from here?"),
            Vocabulary(317, "ใกล้", "Near / Close", "Klai", "Travel", "โรงแรมอยู่ใกล้รถไฟฟ้า", "The hotel is near the skytrain."),
            Vocabulary(318, "รถยนต์", "Car", "Rot yon", "Travel", "ขับรถยนต์เที่ยวสนุกดี", "Driving a car is fun to travel."),
            Vocabulary(319, "ตั๋ว", "Ticket", "Tua", "Travel", "ซื้อตั๋วสถานีปลายทางไหน", "Which destination station for buying the ticket?"),
            Vocabulary(320, "เที่ยว", "Travel / Trip", "Thiao", "Travel", "สัปดาห์หน้าเพื่อนจะมาเที่ยว", "Next week, friends will come to travel."),
            Vocabulary(321, "ทะเล", "Sea / Ocean", "Thale", "Travel", "นั่งดริ้งก์น้ำหวานสาดแดดชายทะเลระบายดี", "Relaxing with drinks beside the sunny ocean."),
            Vocabulary(322, "ชายหาด", "Beach / Coastal", "Chai hat", "Travel", "สวมแว่นกันแดดเหยียบเม็ดทรายสลับชายหาดขาว", "Wore sunglasses stepping on fine coastal beaches."),
            Vocabulary(323, "ภูเขา", "Mountains", "Phu khao", "Travel", "ไปตากอากาศลากไกด์นำทัวร์ขึ้นภูเขาสูง", "Going to the high mountains with our guide."),
            Vocabulary(324, "น้ำตก", "Waterfall", "Nam tok", "Travel", "อาบต้มเย็นน้ำตกกระเด้งสะพานหน้าผายักษ์", "Bathing in clean cold mountain waterfalls."),
            Vocabulary(325, "ตลาดน้ำ", "Floating market", "Talat nam", "Travel", "นั่งเรือพายชมแม่ค้าตลาดน้ำโบราณพัดเบสิก", "Riding a paddleboat to explore floating markets."),
            Vocabulary(326, "สวนสาธารณะ", "Park / Garden", "Suan satharana", "Travel", "พาสุนัขจิ๋ววิ่งกลมรอบสวนสาธารณะร่มรื่น", "Took our tiny puppy to run around the green park."),
            Vocabulary(327, "พิพิธภัณฑ์", "Museum", "Phiphithaphan", "Travel", "ยืนสงบนิ่งเสพรูปภาพในพิพิธภัณฑ์ศิลป์ระดับชาติ", "Admirably looking at ancient paintings in museum."),
            Vocabulary(328, "สวนสัตว์", "Zoo", "Suan sat", "Travel", "พาน้องสาวซื้อตั๋วชี้ดูกวางเสือในสวนสัตว์ปลา", "Bought a ticket to watch deer and tigers at zoo."),
            Vocabulary(329, "เมือง", "City", "Mueang", "Travel", "รถไฟฟ้าบีทีเอสส่งตรงสะพานใกล้จุดตึกในเมืองหลวง", "Skytrain travels straight to the city center."),
            Vocabulary(330, "จังหวัด", "Province", "Changwat", "Travel", "ขึ้นสายรถทัวร์หนีเมืองยักษ์ไปสูดควันต่างจังหวัด", "Taking tour bus away from urban chaos to countryside."),
            Vocabulary(331, "รถตู้", "Van", "Rot too", "Travel", "จองตั๋วนั่งรถตู้ไปต่างจังหวัดรวดเร็วดี", "Booked a ticket to ride a van to the province which is fast."),
            Vocabulary(332, "รถเมล์", "Bus", "Rot me", "Travel", "โหนจับราวยาวเหงื่อท่วมเบียดเสียดในรถเมล์ประจำทาง", "Holding rails tight inside hot crowded city buses."),
            Vocabulary(333, "เรือ", "Boat", "Rua", "Travel", "เรือหางยาวติดเครื่องเบสิกสับฟองคลื่นสาดเปียกฝน", "Longtail boat motor roars across white splash waves."),
            Vocabulary(334, "เครื่องบิน", "Airplane", "Khruang bin", "Travel", "เครื่องบินเหินสยบท้องฟ้าข้ามไปเมืองจีนสามแยก", "Giant airplane soars high crossing skies above clouds."),
            Vocabulary(335, "จักรยาน", "Bicycle", "Chakkrayan", "Travel", "ขี่จักรยานชมชายแดนวัดโพธิ์ใกล้สะพานโรงแรม", "Riding a bicycle around temples near the hotel."),
            Vocabulary(336, "มอเตอร์ไซค์", "Motorcycle", "Motorcycle", "Travel", "สวมหมวกสีแดงซ้อนวินมอเตอร์ไซค์รับจ้างซิ่งคล่อง", "Wore red helmet riding taxi motorcycle cleanly."),
            Vocabulary(337, "แท็กซี่", "Taxi", "Taxi", "Travel", "เปิดประตูขึ้นโบกทางขึ้นแท็กซี่มิเตอร์ติดแอร์ชุ่ม", "Opened door to call a cold aircon taxi meter."),
            Vocabulary(338, "รถไฟใต้ดิน", "Subway", "MRT", "Travel", "แลกเหรียญกลมซื้อตั๋วแตะรูดผ่านคิวรถไฟฟ้าใต้ดิน", "Swapped coins to scan ticket entering subway station."),
            Vocabulary(339, "รถไฟฟ้า", "Skytrain", "BTS", "Travel", "สแกนสลิปผ่านทางขึ้นรถไฟลอยฟ้าสถานีตึกสิบชัน", "Scanned ticket receipt to ascend skytrain gates."),
            Vocabulary(340, "สถานีตำรวจ", "Police station", "Sathani tamruat", "Travel", "รีบวิ่งหน้าตื่นลนลานไปแจ้งเรื่องด่านสถานีตำรวจหนุน", "Ran in absolute urgency straight to the police station."),
            Vocabulary(341, "เช็คอิน", "Check-in", "check-in", "Travel", "เดินแตะเคาน์เตอร์แจ้งเช็คอินโรงแรมห้าแสนดาวหมอก", "Approached receptionist counter to do hotel check-in."),
            Vocabulary(342, "เช็คเอาท์", "Check-out", "check-out", "Travel", "คืนรหัสกุญแจเช็คเอาท์เตรียมลากเป้ลุยแผนที่ต่อ", "Returned keys doing check-out to resume travel."),
            Vocabulary(343, "กุญแจ", "Key", "Kunchae", "Travel", "กระเป๋าเป้ซ่อนกุญแจทองหรูเหลี่ยมคล้องพวงช้าง", "My backpack kept a secure golden card key."),
            Vocabulary(344, "ห้องพัก", "Room", "Hong phak", "Travel", "เปิดกลอนประตูแง้มรับห้องอัปเกรดกว้างเตียงขนนุ่ม", "Unlocked door to find a spacious luxury room suite."),
            Vocabulary(345, "เตียง", "Bed", "Tiang", "Travel", "ม้วนลอยตัวทิ้งเหนื่อยลงบนเตียงกว้างปุยขนนกขาว", "Roll dived onto a fluffy feathered soft bed."),
            Vocabulary(346, "ผ้าเช็ดตัว", "Towel", "Pha chet tua", "Travel", "เช็ดตัวเย็นฉ่ำให้แห้งด้วยผ้าเช็ดตัวใหม่กลิ่นน้ำหอม", "Wiped dry with freshly scented giant bath towel."),
            Vocabulary(347, "อินเทอร์เน็ต", "Internet", "internet", "Travel", "เช็คสตรีมภาพโพสต์วิดีโอผ่านอินเทอร์เน็ตคุณภาพสูง", "Connected to global server via online high-speed web."),
            Vocabulary(348, "ไวไฟ", "Wifi", "wifi", "Travel", "ถ่ายรูปป้ายรหัสไวไฟใต้เก้าอี้ต้อนรับหน้าล็อบบี้", "Captured wifi password tag sign from lobby desk."),
            Vocabulary(349, "พนักงานต้อนรับ", "Receptionist", "receptionist", "Travel", "ถอดแว่นสายตาทักทายพนักงานต้อนรับยิ้มชวนสบายดี", "Greeted the ever-smiling professional desk clerk."),
            Vocabulary(350, "หมอน", "Pillow", "Mon", "Travel", "ดึงหนุนหมอนนุ่มนิ่มรองเท้าสะพานคอระบายลอยหลับ", "Pillowed head onto dream comfy cloud cushion."),
            Vocabulary(351, "ตารางเวลา", "Timetable", "tarang wela", "Travel", "สแกนถ่ายแผนภูมิตารางเวลาวิ่งรถไฟฟ้าไม่ผิดนัด", "Captured skytrain scheduling timetable printout."),
            Vocabulary(352, "กระเป๋าเดินทาง", "Luggage", "kra pao doentang", "Travel", "ลากกระเป๋าเดินทางล้อเลื่อนหนักก้าวข้ามแอปเปิ้ลหล่น", "Wheeled heavy black travel suitcase across terminal."),
            Vocabulary(353, "หนังสือเดินทาง", "Passport", "passport", "Travel", "ดึงยื่นหนังสือเดินทางไทยเล่มส้มผ่านช่องตม.ทันที", "Handed orange tourist passport to officer."),
            Vocabulary(354, "วีซ่า", "Visa", "visa", "Travel", "ถือวีซ่าประทับตราอนุญาตสิบปีเข้าเมืองหลวงราบรื่น", "Clipped 10-year tourist visa paper directly."),
            Vocabulary(355, "ตรวจคนเข้าเมือง", "Immigration", "TMy", "Travel", "สลัดแว่นตาดำโค้งคำนับยิ้มทักพนักงานด่านตรวจคนเข้าเมือง", "Took off sunglasses greeting immigration officer."),
            Vocabulary(356, "ด่านตรวจ", "Checkpoint", "dan", "Travel", "ผ่านด่านสแกนค้นตัวไม่เจอขวดละเงินทองสูญพ้น", "Cleared scanning at airport security checkpoint."),
            Vocabulary(357, "เที่ยวบิน", "Flight", "thiao bin", "Travel", "หูเงี่ยฟังประกาศเที่ยวบินเลทชั่วโมงสามสิบนาที", "Listened hard to delay announcements of flight."),
            Vocabulary(358, "ทางออก", "Exit", "thang ok", "Travel", "วิ่งจ็อกกิ้งก้าวตามป้ายทางออกหนีไฟสีเขียวสว่างดึก", "Walked matching green emergency exit signs."),
            Vocabulary(359, "ทางเข้า", "Entrance", "thang khao", "Travel", "ประตูกระจกทางเข้าหลักเปิดกวักไอคอนการค้าสะพานลอย", "Automatic glass entry at major entrance gate opens."),
            Vocabulary(360, "ประตู", "Gate", "pratu", "Travel", "ลนลานสแกนสลิปตั๋ววิ่งหาประตูล็อคเบอร์ยี่สิบหกตรง", "Ran matching ticket layout to find gate terminal 26."),
            Vocabulary(361, "แผนที่", "Map", "phan", "Travel", "พึ่งพาแอปแผนที่นำจีพีเอสนำซอยลึกหมาเห่าระบาด", "Relying on mobile GPS map app inside deep alleys."),
            Vocabulary(362, "ทิศเหนือ", "North", "thit nuea", "Travel", "หันหน้าหาทิศเหนือลมเย็นพัดผ่านยอดภูเขาสูงขาว", "Chilling north wind blows from high mountain peaks."),
            Vocabulary(363, "ทิศใต้", "South", "thit tai", "Travel", "ขับเที่ยวทิศใต้ทะเลอุ่นต้อนรับกระแสน้ำพริกเผ็ด", "Driving to the sunny south of blue beaches."),
            Vocabulary(364, "ทิศตะวันออก", "East", "thit tawan ok", "Travel", "ตื่นสี่ทุ่มดูอาทิตย์แซมฟ้าทอกทิศตะวันออกไบ", "Woke up early watching sunrise east horizon skies."),
            Vocabulary(365, "ทิศตะวันตก", "West", "thit tawan tok", "Travel", "ถ่ายโพลารอยด์อาทิตย์ลับทรายเฉียงทิศตะวันตกดิ่ง", "Captured golden sunset fading on west coastal sandy sea."),
            Vocabulary(366, "สี่แยก", "Intersection", "see yaek", "Travel", "สี่แยกไฟกะพริบระวังชนวินข้ามทางม้าลายเหลือง", "Crossing dynamic traffic intersection safety check."),
            Vocabulary(367, "สามแยก", "Junction", "sam yaek", "Travel", "สามแยกตลาดโบราณขายมะม่วงทุเรียนน้ำอุ่นอร่อย", "Old market three-way road junction."),
            Vocabulary(368, "สะพาน", "Bridge", "saphan", "Travel", "เดินพึ่งแดดถ่ายฉากตึกยักษ์เหนือสะพานพระรามเก้า", "Walking bridge with breathtaking view of sky."),
            Vocabulary(369, "ทางม้าลาย", "Pedestrian crossing", "thang ma lai", "Travel", "จูงมือคุณตาข้ามถนนสิบเลนหุ้มทางม้าลายมลสะอาด", "Walked grandpa safely through pedestrian stripe crossing."),
            Vocabulary(370, "ซอย", "Alley", "soi", "Travel", "แท็กซี่เลี้ยวผ่านซอยแคบมากเบียดกำแพงบ้านสวยงาม", "Taxi enters narrow alleyway between old brick homes."),
            Vocabulary(371, "ขี่", "Ride", "khee", "Travel", "ห้างจัดทัวร์ขี่ช้างสุรินทร์ท่องเที่ยวพนาสะพานลอย", "Fulfilling dream of mounting Asian elephant travel."),
            Vocabulary(372, "ขับ", "Drive", "khap", "Travel", "คุณพ่อทำงานขับแท็กซี่หาเบียร์สองบาทซื้อไก่", "Father works driving city taxis for home food."),
            Vocabulary(373, "เดิน", "Walk", "doen", "Travel", "ลากเพื่อนรักเดินช้อปปิ้งหาของทอดกล้วยทอดตลาดสด", "Walked looking at delicious street food."),
            Vocabulary(374, "วิ่ง", "Run", "wing", "Travel", "ตื่นเช้าหกนาฬิกาวิ่งจ็อกกิ้งรอบสวนสาธารณะสะใจปอด", "Started morning running speed inside the park."),
            Vocabulary(375, "หยุด", "Stop", "yut", "Travel", "เหยียบเบรกเตะหยุดรถรวดเร็วเฉียดหมาสองตัวนอนหลับ", "Pushed brakes hard to stop vehicle on path."),
            Vocabulary(376, "รอ", "Wait", "ro", "Travel", "ต่อแถวต่อราคาเสร็จยืนรอรับสลิปเงินคืนห้าบาท", "Stood waiting patiently at billing cash counter."),
            Vocabulary(377, "หลงทาง", "Get lost", "long thang", "Travel", "แผนที่หายเปิดจีพีเอสเดี้ยงหลงทางในป่าภูเขาใหญ่", "Relying on helper while getting lost in city."),
            Vocabulary(378, "ข้าม", "Cross", "kham", "Travel", "นั่งเรือหางยาวข้ามฝั่งแม่น้ำเจ้าพระยาไปกินเป็ด", "Rode boat crossing the river to roasted food shop."),
            Vocabulary(379, "ขึ้นรถ", "Get on", "khuen rot", "Travel", "กวักมือเรียกตุ๊กตุ๊กโบกขึ้นรถอย่างรวดเร็วฝนกระหน่ำ", "Waved hand getting on vehicle quickly to shield rain."),
            Vocabulary(380, "ลงรถ", "Get off", "long rot", "Travel", "กดกริ่งแท็กซี่ไฟสว่างลงรถโดยสารหน้าทางเข้าห้าง", "Pressed button getting off vehicle near front gate."),
            Vocabulary(381, "ถ่ายรูป", "Take photo", "thai phap", "Travel", "ชักมือถือเลนส์งามแชะถ่ายภาพครอบครัวหนุนวัดงาม", "Took memorable holiday photo shot of family."),
            Vocabulary(382, "กล้อง", "Camera", "klong", "Travel", "กล้องระดับโปรจับภาพแตงโมสุกแดดเก๋งชายหาดเย็น", "Took beach camera to shoot sunset photos."),
            Vocabulary(383, "ไกด์", "Tour guide", "guide", "Travel", "ไกด์ชวนรู้แนะนำประวัติศาสตร์เจดีย์ทองโบราณสิบชั้น", "Interactive travel guide explaining temple details."),
            Vocabulary(384, "แนะนำ", "Recommend", "nae nam", "Travel", "พี่พนักงานแนะนำร้านอร่อยหมูกรอบส้มตำเด็ดดวงใต้สะพาน", "Highly recommend local crispy fried roasted dishes."),
            Vocabulary(385, "คูปอง", "Coupon", "coupon", "Travel", "ฉีกรับยื่นคูปองลดอาหารสิบห้าบาทฟรีแกกล่องสด", "Gave a paper coupon voucher receiving sweet discount."),
            Vocabulary(386, "แผ่นพับ", "Leaflet", "leaflet", "Travel", "หยิบกางแผ่นพับท่องเที่ยวหน้าสนามบินดึงแผนที่", "Reading free guide leaflet handbook about city."),
            Vocabulary(387, "ลดราคา", "Discount", "lot rakha", "Travel", "ป้ายแดงหราลดราคาสินค้ารองเท้าคู่หมี่เหลือง", "Shop displayed discount tags on winter items."),
            Vocabulary(388, "เที่ยวสนุก", "Travel joy", "thiao sanuk", "Travel", "เดินทางปลอดภัยขอให้เที่ยวสนุกเพลิดเพลินเมืองไทย", "Wish you have extreme travel joy visiting Chiang Mai."),
            Vocabulary(389, "สวยงาม", "Beautiful", "suay ngam", "Travel", "วัดพระแก้วมีสีศิลปะทองคำสวยงามอัศจรรย์ตาโลก", "Emerald temple beauty is incredibly stunning."),
            Vocabulary(390, "ของแท้", "Genuine", "khong thae", "Travel", "ผ้าพันคอสัมผัสขนนกของแท้ร้อยเปอร์ไม่มีต้มตุ๋น", "Genuine high-quality handwoven Thai silk scarf."),
            Vocabulary(391, "ช่วยด้วย", "Help", "chuay duay", "Travel", "ส่งเสียงตะโกนช่วยด้วยคนตกสะพานแม่น้ำตกปลายนิ้ว", "Screamed emergency help call as bag floated away."),
            Vocabulary(392, "โรงพยาบาล", "Hospital", "rong phayaban", "Travel", "รถไซเรนส่งตรงคนเจ็บเข้าโรงพยาบาลฉุกเฉินระดับจี", "Ambulance sped to the general hospital ward."),
            Vocabulary(393, "ยา", "Medicine", "ya", "Travel", "ปวดหัวเจ็บเท้าหมอจัดยารูปวงกลมแกะต้มกินสบาย", "Doctor prescribed effective medicine pills."),
            Vocabulary(394, "คลินิก", "Clinic", "clinic", "Travel", "นัดพบตรวจคราบหินปูนคลินิกทันตแพทย์ย่านตึกสิบชัน", "Visiting dental care clinic inside city."),
            Vocabulary(395, "รถพยาบาล", "Ambulance", "ambulance", "Travel", "เสียงหวูดดังรถพยาบาลไซเรนสับซอยหลีกแยกด่วนจี", "Ambulance speeds past traffic gridlock safely."),
            Vocabulary(396, "ลืม", "Forget", "luem", "Travel", "เดินเหม่อลอยซื้อไก่ลืมเงินทอนแปดสิบบาทน่าเจ็บหัว", "I forgot my shopping change coin on counter."),
            Vocabulary(397, "หาย", "Lost", "hai", "Travel", "สืบค้นหากกุญแจกระเป๋าสะพายหายลนลานพึ่งแผนที่สแกน", "My wallet went lost, searching around lobby."),
            Vocabulary(398, "ขโมย", "Thief", "khamoy", "Travel", "ตะโกนเรียกตำรวจขโมยของสะพายส่งสายตาโกรธจัดหัว", "Stop that thief who grabbed our travel backpack!"),
            Vocabulary(399, "อันตราย", "Dangerous", "antarai", "Travel", "ป้ายห้ามขึ้นจับเข็มสะพานลอยเสาไฟอันตรายเขตราง", "Danger high voltage fence sign keep safe distance."),
            Vocabulary(400, "ระวัง", "Watch out", "rawang", "Travel", "ทางโค้งซอยมืดลื่นระวังหนูสัตว์ตกถนนกระแทกลงหัว", "Watch out on wet lane to drive safely!")
        )
    }

    private fun getFamilyVocabulary(): List<Vocabulary> {
        return listOf(
            Vocabulary(401, "พ่อ", "Father", "Pho", "Family", "พ่อของฉันเป็นใจดีมาก", "My father is very kind."),
            Vocabulary(402, "แม่", "Mother", "Mae", "Family", "คุณแม่ทำอาหารอร่อยที่สุด", "Mother cooks the most delicious food."),
            Vocabulary(403, "พี่ชาย", "Older brother", "Phee chai", "Family", "พี่ชายของผมทำงานที่กรุงเทพฯ", "My older brother works in Bangkok."),
            Vocabulary(404, "พี่สาว", "Older sister", "Phee sao", "Family", "พี่สาวของเขาเรียนหมอ", "His older sister is studying medicine."),
            Vocabulary(405, "น้องชาย", "Younger brother", "Nong chai", "Family", "น้องชายชอบเล่นเกมฟุตบอล", "Younger brother likes to play football games."),
            Vocabulary(406, "น้องสาว", "Younger sister", "Nong sao", "Family", "น้องสาวเพิ่งเข้าโรงเรียนอนุบาล", "Younger sister just entered kindergarten."),
            Vocabulary(407, "ครอบครัว", "Family", "Khrop khrua", "Family", "พวกเราเป็นครอบครัวอบอุ่น", "We are a warm family."),
            Vocabulary(408, "รัก", "Love", "Rak", "Family", "ผมรักครอบครัวและเพื่อนๆ", "I love my family and friends."),
            Vocabulary(409, "เพื่อน", "Friend", "Phuan", "Family", "ฉันรักเพื่อนๆ ทุกคน", "I love all of my friends."),
            Vocabulary(410, "ลูก", "Child / Offspring", "Look", "Family", "ลูกๆ กำลังตื่นนอน", "The children are waking up."),
            Vocabulary(411, "คุณยาย", "Grandmother (maternal)", "Khun yai", "Family", "คุณยายใจดีมากๆ เลยค่ะ", "Grandmother is very kind."),
            Vocabulary(412, "คุณตา", "Grandfather (maternal)", "Khun ta", "Family", "คุณตามักจะอ่านหนังสือเล่มนี้", "Grandfather usually reads this book."),
            Vocabulary(413, "แฟน", "Boyfriend / Girlfriend / Spouse", "Fan", "Family", "แฟนของฉันทำงานเก่งมาก", "My special someone works very hard."),
            Vocabulary(414, "คน", "Person / Classifier for people", "Khon", "Family", "ในบ้านมีคนห้าคน", "There are five people in the house."),
            Vocabulary(415, "มี", "Have", "Mee", "Family", "พ่อมีรถยนต์สีแดงหนึ่งคัน", "Father has one red car."),
            Vocabulary(416, "ดี", "Good", "Dee", "Family", "แม่ทำอาหารรสชาติดี", "Mother cooks good-tasty food."),
            Vocabulary(417, "ชอบ", "Like", "Chop", "Family", "ฉันชอบกินข้าวผัดปู", "I like to eat crab fried rice."),
            Vocabulary(418, "ไม่", "Not", "Mai", "Family", "ฉันไม่ชอบคนโกหก", "I do not like liars."),
            Vocabulary(419, "เด็ก", "Child / Kid", "Dek", "Family", "เด็กคนนั้นกำลังร้องไห้", "That child is crying."),
            Vocabulary(420, "และ", "And", "Lae", "Family", "รักแม่และพ่อมากๆ นะ", "Love mother and father very much."),
            Vocabulary(421, "ปู่", "Paternal grandfather", "Pu", "Family", "ปู่ชอบดื่มชาร้อนนั่งเก้าอี้ไม้ตัวใหญ่ในสวน", "Grandfather likes warm tea on his wooden chair."),
            Vocabulary(422, "ย่า", "Paternal grandmother", "Ya", "Family", "ย่าลั่นสัญญาสอนหลานเย็บผ้าห่อของขวัญเสื้อสวย", "Grandmother promises to teach us craft sewing."),
            Vocabulary(423, "ลุง", "Uncle (older than parents)", "Lung", "Family", "ลุงใจดีซื้อรองเท้าผ้าใบสีแดงตัวนี้ลดราคาให้", "My kind uncle bought these discounted red sneakers."),
            Vocabulary(424, "ป้า", "Aunt (older than parents)", "Pa", "Family", "ป้าตำส้มตำไทยอร่อยเผ็ดน้อยจัดโต๊ะรอครอบครัว", "My aunt cooked less-spicy papaya salad for table."),
            Vocabulary(425, "น้า", "Maternal aunt/uncle", "Na", "Family", "น้าสอนภาษาอังกฤษให้การเขียนอ่านการพิมพ์เร็วสิบ", "Our aunt teaches us english conversation."),
            Vocabulary(426, "อา", "Paternal aunt/uncle", "A", "Family", "อาทำงานหนักออมเงินสดล้านบาทไว้ซื้อบ้านใหม่เดี่ยว", "Our uncle saves a million Baht to buy modern house."),
            Vocabulary(427, "พี่น้อง", "Siblings / Kin", "Phee nong", "Family", "พี่น้องห้าคนรักอบอุ่นช่วยเหลือแบ่งปันของฝาก", "Five siblings loving, assisting, and sharing souvenirs."),
            Vocabulary(428, "หลานชาย", "Nephew / Grandson", "Lan chai", "Family", "หลานชายสุดเก่งตอบคำถามคณิตได้รางวัลห้าร้อยบาท", "Grandson won a five hundred Baht prize at math."),
            Vocabulary(429, "หลานสาว", "Niece / Granddaughter", "Lan sao", "Family", "หลานสาวคนเก่งเรียนหมอช่วยคนพังโรงพยาบาลเมืองหลวง", "Granddaughter studying medicine to save lives at hospital."),
            Vocabulary(430, "ญาติ", "Relatives", "Yat", "Family", "งานรวมญาติคนเยอะคึกคักคุยกันก้องห้องนั่งเล่น", "Big family reunion meeting up together inside lobby."),
            Vocabulary(431, "สามี", "Husband", "samee", "Family", "สามีขยันตื่นตอนเช้าขับรถยนต์ทำงานคลินิกยี่สิบปี", "Diligently working husband drives to clinic daily."),
            Vocabulary(432, "ภรรยา", "Wife", "phanraya", "Family", "ภรรยาเตรียมแกงเขียวหวานต้มยำกุ้งรสชาติอร่อยมาก", "The sweet wife cooks tasty green curry and soup."),
            Vocabulary(433, "พ่อแม่", "Parents", "pho mae", "Family", "รักเคารพพ่อแม่เลี้ยงดูส่งเสียเรียนหนังสือจบสูงสุดเก่ง", "Highly respect parents who raised us to graduate."),
            Vocabulary(434, "แต่งงาน", "Married", "taeng ngan", "Family", "คู่รักแต่งงานอบอุ่นใจสัญญารักกันชั่วกาลนานตลอดไป", "The lovely couple married promising to love forever."),
            Vocabulary(435, "โสด", "Single", "sot", "Family", "โสดสนิทสนุกสนานชวนเพื่อนรักตะลอนเที่ยวทะเลชายหาด", "Happily single traveling beaches with best friends."),
            Vocabulary(436, "แฟนเก่า", "Ex-boyfriend / Ex-girlfriend", "fan kao", "Family", "แม้เป็นแฟนเก่ารักยังคุยยิ้มปรารถนาดีช่วยเหลือเกื้อหนุน", "Even as exes, we remain supportive of each other."),
            Vocabulary(437, "เพื่อนบ้าน", "Neighbor", "phuan ban", "Family", "เพื่อนบ้านใจดีแบ่งปันผลไม้มะละกอกล้วยสับปะรดให้เรากอบ", "Friendly neighbor shares fresh organic papaya fruits."),
            Vocabulary(438, "เพื่อนร่วมงาน", "Colleague", "phuan ruam ngan", "Family", "เพื่อนร่วมงานสองคนแฉะถ่ายรูปชิ้นงานส่งหัวหน้าสแกน", "Colleague captures and uploads sheet slide review to boss."),
            Vocabulary(439, "หัวหน้า", "Boss", "hua na", "Family", "หัวหน้าขยันชมเชยเลื่อนขั้นมอบกระเป๋าเงินทอนแสนบาท", "Diligent boss praises team and awards high bonus."),
            Vocabulary(440, "ลูกน้อง", "Subordinate", "look nong", "Family", "ลูกน้องคอยช่วยเหลืองานเตรียมสไลด์ตารางเวลาห้างปิด", "Dynamic staff organizes schedules prior to release."),
            Vocabulary(441, "หมอ", "Doctor", "mo", "Family", "หมอบอกว่าเจ็บน่องซ้ายขอนึ่งยารูปวงกลมให้กินระงับ", "Professional doctor specifies pill recipe to reduce pain."),
            Vocabulary(442, "พยาบาล", "Nurse", "phayaban", "Family", "พยาบาลเฝ้าคนไข้เจ็บบริการน้ำส้มชาอุ่นตลอดสิบสองชั่วโมง", "Kind nurse brings orange juice and tea to patients."),
            Vocabulary(443, "ครู", "Teacher", "khru", "Family", "ครูใหญ่สอนนักเรียนขยันเขียนตอบแบบประเมินสีชมพู", "The principal teaches students to write diligent replies."),
            Vocabulary(444, "นักเรียน", "Student", "nak rian", "Family", "นักเรียนดีอ่านหนังสือหนาเตอะไม่ขี้เกียจสอบได้เต็ม", "Diligent student reads thick books scoring high marks."),
            Vocabulary(445, "ตำรวจ", "Police", "tamruat", "Family", "ตำรวจจราจรเป่านกหวีดคุมสี่แยกทางม้าลายสว่างไฟสิบ", "Police officer blowing whistle directs intersection flow."),
            Vocabulary(446, "ทหาร", "Soldier", "thahan", "Family", "ทหารบกเข้มแข็งป้องกันป่าเขาแถวชายแดนอันตรายซ่อน", "Strong soldiers shield borders from national danger."),
            Vocabulary(447, "ค้าขาย", "Merchant", "kha khai", "Family", "ค้าขายตลาดน้ำพายเรือสั่งกุยช้ายทอดกล้วยไข่ทอดร้อน", "Traditional canal dealer selling sweet hot dishes."),
            Vocabulary(448, "นักธุรกิจ", "Businessman", "nak thurakit", "Family", "นักธุรกิจร้อยล้านโอนเงินสดพันล้านผ่านม่านรถไฟฟ้า", "Successful executive transferring high funds safely."),
            Vocabulary(449, "วิศวกร", "Engineer", "witsawakon", "Family", "วิศวกรวิลล่าสร้างสะพานถนนข้ามซอยใหญ่สี่เลนลื่นไหลก้าว", "Expert engineer building giant urban flyover bridge."),
            Vocabulary(450, "แม่บ้าน", "Maid", "mae ban", "Family", "แม่บ้านซักผ้าปูที่นอนหมอนอิงสะอาดใส่น้ำหอมกลิ่นกล้วย", "Maid washes bedsheets till they are clean-scented."),
            Vocabulary(451, "คุย", "Chat", "khuy", "Family", "นั่งลานหญ้าล้อมคุยสนุกเรื่องของขวัญตลาดน้ำพริก", "Relaxing on green grass chatting about holiday gifts."),
            Vocabulary(452, "หัวเราะ", "Laugh", "hua ro", "Family", "ทุกคนหัวเราะร่าความสุขเปี่ยมล้นใจหน้าสวนหน้าขาว", "Everyone giggles with absolute smiles in home garden."),
            Vocabulary(453, "ร้องไห้", "Cry", "rong hai", "Family", "เด็กตัวน้อยร้องไห้หนักพ่อลืมซื้อโปสการ์ดของฝากตุ๊กตา", "The kid wept because father forgot to buy the toy."),
            Vocabulary(454, "นัดพบ", "Meet up", "nat phop", "Family", "นัดพบปะเพื่อนร่วมงานห้าโมงเย็นจิบกาแฟร้อนแบรนด์หรู", "Set a coordinate to meet colleagues for hot coffee."),
            Vocabulary(455, "รู้จัก", "Know", "ru chak", "Family", "ยินดีได้รับรู้จักอาคุณยายผู้ขยันซื่อสัตย์สุภาพใจดี", "Pleased to be acquainted with your kind grandmother."),
            Vocabulary(456, "ช่วยเหลือ", "Help", "chuay luea", "Family", "คนในซอยช่วยเหลือกันลากกระเป๋าเดินทางข้ามทางหมาลาย", "Neighbors help wheeled heavy suitcases across intersection."),
            Vocabulary(457, "แบ่งปัน", "Share", "baeng pan", "Family", "แบ่งปันส้มผลไม้รสแกงเข็ดต้มยำปูกลางโต๊ะกลมปรก", "Share papaya salads and fresh mango fruits cleanly."),
            Vocabulary(458, "สัญญา", "Promise", "sanya", "Family", "สัญญามั่นรักภรรยาตลอดไปชั่วฟ้าดินทราเสากุญแจ", "Vowed a solemn promise to love spouse forever."),
            Vocabulary(459, "เชื่อใจ", "Trust", "chua chai", "Family", "เชื่อใจพยาบาลหมอจัดยาที่ดีให้ร่างกายสบายดีกระเตื้อง", "Trust the clinic to prescribe correct healthy pills."),
            Vocabulary(460, "คิดถึงบ้าน", "Miss home", "khit thueng ban", "Family", "เดินทางไกลเมืองหลวงเจ็ดคืนคิดถึงบ้านห้องนอนอุ่นเตียง", "Traveling far away makes me miss my sweet cozy bed."),
            Vocabulary(461, "อายุ", "Age", "ayu", "Family", "น้องชายอายุครบสิบขวบชอบขี่จักรยานวิ่งสวนวัด", "Younger brother's age is ten years old this week."),
            Vocabulary(462, "วันเกิด", "Birthday", "wan koet", "Family", "ฉลองเป่าขนมเค้กวันเกิดสุขลวดลายถุงเท้าขนนิ่มสุดขีด", "Blew birthday candles receiving lovely warm socks."),
            Vocabulary(463, "ชื่อเล่น", "Nickname", "cheu len", "Family", "ฉันมีชื่อเล่นต๊ะไกด์พาทับทิมเที่ยวตอกยู่สบายจัง", "My colloquial nickname is Ta, nice to meet you."),
            Vocabulary(464, "นามสกุล", "Last name", "nam sakun", "Family", "เขียนนามสกุลรักษ์ถิ่นไทยในช่องวีซ่าด่านสนามบินด่วน", "Wrote family surname strictly on immigration forms."),
            Vocabulary(465, "เพศ", "Gender", "phet", "Family", "ช่องฟอร์มระบุเพศหญิงเพศชายระดับภาษาถูกต้องยอด", "Specify male/female gender on the questionnaire form."),
            Vocabulary(466, "เบอร์โทร", "Phone number", "ber thorasap", "Family", "จดบันทึกเขียนเบอร์โทรติดต่อลุงเผื่อหลงทางซอยลึกดึก", "Jotted phone number down in case I get lost in alley."),
            Vocabulary(467, "ที่อยู่", "Address", "thee yu", "Family", "แท็กซี่ขับส่งถึงที่อยู่บ้านเลขที่สามสิบสี่สะปังโสด", "The cab drove straight to our home address correctly."),
            Vocabulary(468, "ทำงาน", "Work", "tham ngan", "Family", "ลุงทำงานออฟฟิศหรูตึกสิบชั้นได้เงินหมื่นบาทแสนสุขใจ", "Uncle labors in city office for ten thousand Baht salary."),
            Vocabulary(469, "เรียน", "Study", "rian", "Family", "ฉันเรียนต้มยำกุ้งส้มตำไข่เจียวหมูกรอบจากป้าใจดี", "I study cooking pad thai and soups from my aunt."),
            Vocabulary(470, "เกษียณ", "Retired", "kasiat", "Family", "ปู่เกษียณสุขภาพดีชอบเดินชมวัดโพธิ์กางร่มทฟองแดง", "Grandfather is happily retired strolling around temples."),
            Vocabulary(471, "นิสัย", "Habit", "nisai", "Family", "พี่สาวนิสัยนิ่มนวลสุภาพจริงใจพูดคุยน่ารักสวยเสมอ", "Older sister has sweet polite temperament, very cute."),
            Vocabulary(472, "ใจกว้าง", "Generous", "chai kwang", "Family", "ลุงเป็นคนใจกว้างสุดๆ เลี้ยงข้าวผัดราดซอสผลไม้หวาน", "Our generous uncle treated us to tasty fried rice plates."),
            Vocabulary(473, "ขี้อาย", "Shy", "khee ai", "Family", "น้องสาวขี้อายเก่งเวลาคนแปลกหน้าถามชื่อชื่อเล่นเพศ", "Sister is quite shy when strangers ask her questions."),
            Vocabulary(474, "สนุกสนาน", "Cheerful", "sanuk sanan", "Family", "คุยเพื่อนบ้านหัวเราะรื่นลานสวนมีความสนุกสนานรื่นเริง", "Chatting with cheerful neighbors inside home garden."),
            Vocabulary(475, "ซื่อสัตย์", "Honest", "suay sat", "Family", "ตำรวจต้องขยันซื่อสัตย์แท้ดูแลประชาชนพ้นขโมยร้าย", "Police officers must be highly honest patrolling streets."),
            Vocabulary(476, "ขยัน", "Diligent", "khayan", "Family", "แม่ขยันอัดเย็บเสื้อเสื้อหนาวขัดเงินทอนบาทประหยัด", "Mother is hardworking at design making winter clothes."),
            Vocabulary(477, "ขี้เกียจ", "Lazy", "khee kiat", "Family", "คืนหนาวเหน็ดขี้เกียจลุกล้างมีดช้อนส้อมนอนห่มนาหนา", "Feeling slothful and choosing to sleep inside cozy sheets."),
            Vocabulary(478, "อดทน", "Patient", "ot thon", "Family", "รอคิวสามแยกนานต้องอดทนรอบรถเมล์โบกพยากล้อง", "Gridlocks require patients waiting for our city bus."),
            Vocabulary(479, "สุภาพ", "Polite", "suphap", "Family", "พนักงานต้อนรับสุภาพสุดยกมือสวัสดีครับคุณยายคุณตา", "The host is courteous greeting grandpa and grandma."),
            Vocabulary(480, "ลึกลับ", "Mysterious", "luep lap", "Family", "แผนที่แสดงวัดลึกลับลึกห่างไกลกลางป่าเขาสูงหมอก", "The layout keeps a mysterious ancient hidden temple."),
            Vocabulary(481, "บ้าน", "Home", "ban", "Family", "บ้านหลังน้อยทาสีชมพูหนุนสวนสาธารณะริมน้ำสวยงาม", "Cozy pink home backing onto water and park view."),
            Vocabulary(482, "ห้องนอน", "Bedroom", "hong non", "Family", "จัดเตียงนอนแชมพูผ้าห่มหนาเดี๋ยวในห้องนอนอุ่นใจ", "Sleeping comfortably inside a clean warm bedroom."),
            Vocabulary(483, "ห้องนั่งเล่น", "Living room", "hong nang len", "Family", "พักผ่อนล้อมวงห้องนั่งเล่นขวัญพวงกุญแจหัวเราะร่า", "We gather inside cozy family living room to chat."),
            Vocabulary(484, "ห้องครัว", "Kitchen", "hong khrua", "Family", "แม่เปิดแก๊สห้องครัวต้มไข่สับกระเทียมส้มพริกน้ำตาล", "The aroma of cooking spices sweeps around kitchen."),
            Vocabulary(485, "สวน", "Garden", "suan", "Family", "ป้าจูงเด็กจิ๋วชมสวนร่มรื่นดอกมะม่วงสีเหลืองเบ่ง", "Walking around clean garden filled with yellow flowers."),
            Vocabulary(486, "หน้าต่าง", "Window", "na tang", "Family", "แง้มเหลี่ยมหน้าต่างม่านสีน้ำเงินมองเห็นฝนตกชุ่ม", "Opened blue window glass curtains watching rainy storm."),
            Vocabulary(487, "ประตู", "Door", "pratu", "Family", "เคาะกวักลักประตูอัตเรียกน้องชายให้ปลดล็อคกุญแจ", "Opened the mahogany wooden door of home directly."),
            Vocabulary(488, "ทีวี", "TV", "tevee", "Family", "นอนหยอดสุกกินปูดูละครทีวีกว้างแปดสิบนิ้วเพลิน", "Watching colorful series screens on nice main tv."),
            Vocabulary(489, "ตู้เย็น", "Fridge", "tu yen", "Family", "หยิบขวดน้ำเบียร์ส้มตู้เย็นเย็นมาลดร้อนแดดเผาหัว", "Grabbing ice and orange juice from cooling fridge."),
            Vocabulary(490, "เตียงนอน", "Bed", "tiang non", "Family", "สลัดเข็มขัดท่อนร่างทิ้งเหนื่อยลงเตียงนอนนวดหัวใจ", "Divine soft mattress cushion for relaxing sleep."),
            Vocabulary(491, "มีความรัก", "In love", "mee khwam rak", "Family", "คู่สมใจมีความรักรสลอยฟุ้งเฉียบนมชมพูกล่องฟิน", "Feelings of deep romantic affection and love."),
            Vocabulary(492, "คิดถึงกัน", "Miss each other", "khit thueng kan", "Family", "ห่างไกลข้ามเมืองหลวงต่างจังหวัดขอเราคิดถึงกันเสมอ", "Even across cities we miss each other always."),
            Vocabulary(493, "อบอุ่น", "Warm", "op un", "Family", "กอดพ่อแม่ลูกสัมผัสอบอุ่นใจไร้ทุกข์โศกเศร้าลมหนาว", "Hugging family brings warm comfort and safety."),
            Vocabulary(494, "ปลอดภัย", "Safe", "plot phai", "Family", "ตำรวจตรวจตราสถานีตำรวจด่านให้พวกเราปลอดภัยพ้นภัย", "Police officers patrol streets keeping us highly safe."),
            Vocabulary(495, "มีความหวัง", "Hopeful", "mee khwam wang", "Family", "เด็กเรียนเก่งวิศวกรทำงานในเมืองมีความหวังวาดฝัน", "Dreaming of the future with hopeful bright minds."),
            Vocabulary(496, "รวมกัน", "Gather", "ruam kan", "Family", "พวกเราสิบคนรวมกันเฉลิมกินต้มยำปลาทับทิมทอดแห้ง", "Gathering around tables celebrating family holiday."),
            Vocabulary(497, "งานเลี้ยง", "Party", "ngan liang", "Family", "เปิดไวน์แดงฉลองงานเลี้ยงปีใหม่ทุกคนหัวเราะร่ารื่น", "Popping red wine celebrating New Year festive party."),
            Vocabulary(498, "ของหวาน", "Dessert", "khong wan", "Family", "กินส้มตำเปรี้ยวเผ็ดสยบด้วยของหวานเชื่อมมะพร้าวอ่อน", "Finished our spicy meal with sweet coconut dessert."),
            Vocabulary(499, "ความสุข", "Happiness", "khwam suk", "Family", "ขอคุณยายคุณตามีน่าอายุยืนยาวล้านปีความสุขล้น", "Wishing grandpa and grandma absolute happiness and joy."),
            Vocabulary(500, "ตลอดไป", "Forever", "talot pai", "Family", "ครอบครัวอบอุ่นรักถนอมกันมั่นสัญญาตลอดไปถนอมใจ", "Loving our sweet family and friends forevermore.")
        )
    }

    private fun getSampleLessons(): List<Lesson> {
        val list = mutableListOf<Lesson>()
        
        val topics = listOf(
            Triple("Greetings Basic", "Greetings", "Learn sawatdee, khop khun and core basics."),
            Triple("Food Staples", "Food", "Master khao, nam, and daily essential food nouns."),
            Triple("Numbers & Money", "Numbers", "Build numbers and count Thai Baht efficiently."),
            Triple("Directions & Transit", "Travel", "Ask where landmarks are, learn left and right."),
            Triple("Parents & Relatives", "Family", "Talk about mother, father, and family roots."),
            Triple("More Greetings & Feelings", "Greetings", "Practice how to apologize, say goodbye, express feelings."),
            Triple("Famous Dishes & Cafe", "Food", "Learn Som Tam, Pad Thai, spicy, hungry, and drinks."),
            Triple("Shopping & Bargaining", "Numbers", "Master cheap, expensive, and asking the price."),
            Triple("Sightseeing & Tuk-Tuk", "Travel", "Order taxis, ride tuk-tuks, and read maps."),
            Triple("Siblings, Friends & Relatives", "Family", "Discuss brothers, sisters, friends, and happy families."),
            Triple("Conversational Politeness", "Greetings", "Polite particles, formal pronouns, and advanced greetings."),
            Triple("Dine Out & Tastes", "Food", "Order food and describe complex flavors perfectly."),
            Triple("Deep Social Connections", "Family", "Express deep social states and beautiful relationships.")
        )
        
        for (topicIdx in topics.indices) {
            val (topicName, duolingoCategory, descPrefix) = topics[topicIdx]
            val testId = 101 + topicIdx
            val lessonCount = if (topicIdx == 12) 2 else 4
            val startLessonId = topicIdx * 4 + 1
            val endLessonId = startLessonId + lessonCount - 1
            
            for (lessonId in startLessonId..endLessonId) {
                val part = lessonId - startLessonId + 1
                val title = "$topicName (Part $part)"
                val desc = "$descPrefix (Part $part description and practice exercises)"
                list.add(
                    Lesson(
                        id = lessonId,
                        title = title,
                        description = desc,
                        category = topicName,
                        unlocked = (lessonId == 1),
                        completed = false,
                        stars = 0
                    )
                )
            }
            
            // Uniquely structured sentence nodes for all topics
            val sentenceLessonId = 501 + topicIdx
            list.add(
                Lesson(
                    id = sentenceLessonId,
                    title = "$topicName Sentences",
                    description = "Complete 9 interactive sentence building exercises using core vocabulary from $topicName.",
                    category = topicName,
                    unlocked = false,
                    completed = false,
                    stars = 0,
                    xpReward = 30
                )
            )

            list.add(
                Lesson(
                    id = testId,
                    title = "$topicName Test",
                    description = "$topicName comprehensive test of all studied vocabulary.",
                    category = topicName,
                    unlocked = false,
                    completed = false,
                    stars = 0,
                    xpReward = 50
                )
            )
        }
        
        return list
    }

    private fun getSampleExercises(): List<Exercise> {
        val list = mutableListOf<Exercise>()
        val vocabulary = getSampleVocabulary()

        for (lessonId in 1..50) {
            val range = getLessonVocabIdsRange(lessonId)
            val lessonVocab = vocabulary.filter { it.id in range }
            
            if (lessonVocab.isEmpty()) continue

            val topicTestId = when {
                lessonId in 49..50 -> 113
                else -> 101 + (lessonId - 1) / 4
            }
            val topicRanges = when (topicTestId) {
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
            val w1 = lessonVocab[0]
            val topicVocab = vocabulary.filter { it.category.equals(w1.category, ignoreCase = true) }
            val otherThais1 = topicVocab.filter { it.id != w1.id }
                .map { it.thai }
                .distinct()
                .shuffled()
                .take(3)
            val options1 = (otherThais1 + w1.thai).shuffled()
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

            // 2. Thai word -> select English (multiple choice)
            val w2 = lessonVocab[1]
            val otherEnglishesForW2 = topicVocab.filter { it.id != w2.id }
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

            // 3. Listening exercise. Sound plays in Thai. Answers in English
            val w3 = lessonVocab[2]
            val otherEnglishesForW3 = topicVocab.filter { it.id != w3.id }
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

            // 4. The pairing/matching exercise
            val pairingWords = lessonVocab.shuffled().take(4)
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

        // Lesson 501: Greetings Basic Sentences
        val sentences501 = listOf(
            // 3 English to Thai Sentence Build
            Triple("Hello, nice to meet you.", "สวัสดี|ยินดีที่ได้รู้จัก", listOf("ขอโทษ", "ขอบคุณ", "ไม่ใช่", "โชคดี")),
            Triple("How are you today?", "คุณ|สบายดีไหม|วันนี้", listOf("พรุ่งนี้", "เมื่อวาน", "ขอบคุณ", "ยินดี")),
            Triple("Today I am fine.", "วันนี้|ฉัน|สบายดี", listOf("พรุ่งนี้", "เมื่อวาน", "ขอบคุณ", "ยินดี")),
            
            // 3 Thai to English Sentence Build
            Triple("ใช่ สบายดี", "Yes|I|am|fine", listOf("Hello", "Sorry", "No", "Goodbye")),
            Triple("สบายดี ขอบคุณ", "I|am|fine|Thank|you", listOf("Yes", "Goodbye", "Not correct", "You")),
            Triple("ยินดีด้วย คุณ โชคดี", "Congratulations|you|are|lucky", listOf("thank you", "fine", "sorry", "today")),
            
            // 3 Listening Thai (spoken) with English words
            Triple("ยินดีที่ได้รู้จัก", "Nice|to|meet|you", listOf("Hello", "Sorry", "Tomorrow", "Today")),
            Triple("ราตรีสวัสดิ์", "Good|night", listOf("Good morning", "Goodbye", "Thank you", "Yes")),
            Triple("ลาก่อน พรุ่งนี้ แล้วพบกันใหม่", "Goodbye|tomorrow|see|you|again", listOf("Good afternoon", "Yesterday", "Good night", "Today"))
        )

        for (i in sentences501.indices) {
            val (questionText, correctAnswer, distractors) = sentences501[i]
            val correctList = correctAnswer.split("|")
            val options = (correctList + distractors).shuffled()
            val type = ExerciseType.SENTENCE_BUILD
            
            if (i < 3) {
                list.add(Exercise(
                    id = 50100 + (i + 1),
                    lessonId = 501,
                    type = type,
                    prompt = "Assemble the Thai words that translate this sentence:",
                    question = questionText,
                    correctAnswer = correctAnswer,
                    romanization = "",
                    options = options,
                    audioText = correctAnswer.replace("|", " ")
                ))
            } else if (i < 6) {
                list.add(Exercise(
                    id = 50100 + (i + 1),
                    lessonId = 501,
                    type = type,
                    prompt = "Translate this Thai sentence into English:",
                    question = questionText,
                    correctAnswer = correctAnswer,
                    romanization = "",
                    options = options,
                    audioText = ""
                ))
            } else {
                list.add(Exercise(
                    id = 50100 + (i + 1),
                    lessonId = 501,
                    type = type,
                    prompt = "Listen and assemble the English translation:",
                    question = "",
                    correctAnswer = correctAnswer,
                    romanization = "",
                    options = options,
                    audioText = questionText
                ))
            }
        }

        // Lesson 502: Food Staples Sentences
        val sentences502 = listOf(
            // 3 English to Thai Sentence Build
            Triple("Today I eat chicken.", "วันนี้|ฉัน|กิน|ไก่", listOf("พริก", "ไข่", "พรุ่งนี้", "ปลา")),
            Triple("I am hungry for rice.", "ผม|หิว|ข้าว", listOf("หวาน", "ดื่ม", "เกลือ", "น้ำ")),
            Triple("Green curry is spicy and delicious.", "แกงเขียวหวาน|เผ็ด|อร่อย", listOf("ข้าว", "หมู", "ปู", "ไข่")),
            
            // 3 Thai to English Sentence Build
            Triple("ต้มยำกุ้ง อร่อย เผ็ด", "Spicy|shrimp|soup|is|delicious|and|spicy", listOf("Sweet", "Water", "Rice", "Tea")),
            Triple("กิน ส้มตำ อร่อย", "Eat|delicious|papaya|salad", listOf("Chicken", "Fish", "Pork", "Salt")),
            Triple("เขา ดื่ม น้ำ", "He|drinks|water", listOf("Coffee", "Tea", "Delicious", "Chicken")),
            
            // 3 Listening Thai (spoken) with English words
            Triple("วันนี้ ฉัน ดื่ม ชา หวาน", "Today|I|drink|sweet|tea", listOf("Water", "Fruit", "Egg", "Pork")),
            Triple("เธอ กิน ผลไม้ หวาน", "She|eats|sweet|fruits", listOf("Coffee", "Water", "Rice", "Tea")),
            Triple("กิน กุ้ง ปู อร่อย", "Eat|delicious|shrimp|and|crab", listOf("Duck", "Pork", "Tea", "Milk"))
        )

        for (i in sentences502.indices) {
            val (questionText, correctAnswer, distractors) = sentences502[i]
            val correctList = correctAnswer.split("|")
            val options = (correctList + distractors).shuffled()
            val type = ExerciseType.SENTENCE_BUILD
            
            if (i < 3) {
                list.add(Exercise(
                    id = 50200 + (i + 1),
                    lessonId = 502,
                    type = type,
                    prompt = "Assemble the Thai words that translate this sentence:",
                    question = questionText,
                    correctAnswer = correctAnswer,
                    romanization = "",
                    options = options,
                    audioText = correctAnswer.replace("|", " ")
                ))
            } else if (i < 6) {
                list.add(Exercise(
                    id = 50200 + (i + 1),
                    lessonId = 502,
                    type = type,
                    prompt = "Translate this Thai sentence into English:",
                    question = questionText,
                    correctAnswer = correctAnswer,
                    romanization = "",
                    options = options,
                    audioText = ""
                ))
            } else {
                list.add(Exercise(
                    id = 50200 + (i + 1),
                    lessonId = 502,
                    type = type,
                    prompt = "Listen and assemble the English translation:",
                    question = "",
                    correctAnswer = correctAnswer,
                    romanization = "",
                    options = options,
                    audioText = questionText
                ))
            }
        }

        // Lesson 503: Numbers & Money Sentences
        val sentences503 = listOf(
            // 3 English to Thai Sentence Build
            Triple("How much is the shirt?", "เสื้อ|ราคา|เท่าไหร่", listOf("เงิน", "บาท", "แพง", "ซื้อ")),
            Triple("Today I buy food for fifty Baht.", "วันนี้|ฉัน|ซื้อ|อาหาร|ห้าสิบ|บาท", listOf("ยี่สิบ", "สิบเอ็ด", "สี่สิบ", "สามสิบ")),
            Triple("The shirt is cheap.", "เสื้อ|ราคา|ถูก", listOf("แพง", "หนึ่ง", "ซื้อ", "เงิน")),
            
            // 3 Thai to English Sentence Build
            Triple("ราคา ทั้งหมด สี่ร้อย บาท", "The|total|price|is|four|hundred|Baht", listOf("Three", "Five", "Ten", "Seven")),
            Triple("ฉัน ซื้อ หมู ยี่สิบ บาท", "I|buy|pork|for|twenty|Baht", listOf("Thirty", "Ten", "One", "Two")),
            Triple("ฉัน ซื้อ กาแฟ หนึ่ง", "I|buy|one|coffee", listOf("Thirty", "Ten", "Two", "Forty")),
            
            // 3 Listening Thai (spoken) with English words
            Triple("เงิน ทั้งหมด สามพัน บาท", "Total|money|is|three|thousand|Baht", listOf("hundred", "million", "Fifty", "Ten")),
            Triple("เสื้อ หนึ่ง ราคา ห้าสิบ บาท", "One|shirt|is|fifty|Baht", listOf("twenty", "thirty", "expensive", "cheap")),
            Triple("ฉัน ซื้อ อาหาร สองร้อย บาท", "I|buy|food|for|two|hundred|Baht", listOf("Thirty", "Ten", "Omelet", "Water"))
        )

        for (i in sentences503.indices) {
            val (questionText, correctAnswer, distractors) = sentences503[i]
            val correctList = correctAnswer.split("|")
            val options = (correctList + distractors).shuffled()
            val type = ExerciseType.SENTENCE_BUILD
            
            if (i < 3) {
                list.add(Exercise(
                    id = 50300 + (i + 1),
                    lessonId = 503,
                    type = type,
                    prompt = "Assemble the Thai words that translate this sentence:",
                    question = questionText,
                    correctAnswer = correctAnswer,
                    romanization = "",
                    options = options,
                    audioText = correctAnswer.replace("|", " ")
                ))
            } else if (i < 6) {
                list.add(Exercise(
                    id = 50300 + (i + 1),
                    lessonId = 503,
                    type = type,
                    prompt = "Translate this Thai sentence into English:",
                    question = questionText,
                    correctAnswer = correctAnswer,
                    romanization = "",
                    options = options,
                    audioText = ""
                ))
            } else {
                list.add(Exercise(
                    id = 50300 + (i + 1),
                    lessonId = 503,
                    type = type,
                    prompt = "Listen and assemble the English translation:",
                    question = "",
                    correctAnswer = correctAnswer,
                    romanization = "",
                    options = options,
                    audioText = questionText
                ))
            }
        }

        // Lesson 504: Directions & Transit Sentences
        val sentences504 = listOf(
            // 3 English to Thai Sentence Build
            Triple("Where is the restroom?", "ห้องน้ำ|ที่ไหน", listOf("โรงแรม", "แผนที่", "สนามบิน", "ไป")),
            Triple("Go straight to the temple.", "ตรงไป|วัด", listOf("รถไฟ", "เลี้ยวซ้าย", "บ้าน", "ตั๋ว")),
            Triple("Go to the hotel.", "ไป|โรงแรม", listOf("สนามบิน", "เลี้ยวขวา", "รถตุ๊กตุ๊ก", "ห้องน้ำ")),
            
            // 3 Thai to English Sentence Build
            Triple("ไป สนามบิน", "Go|Airport", listOf("Turn left", "Restroom", "Hotel", "Tuk-Tuk")),
            Triple("โรงแรม ใกล้", "Hotel|Near / Close", listOf("Far", "Station", "Airport", "Map")),
            Triple("เลี้ยวซ้าย ไป สถานี", "Turn left|Go|Station", listOf("Turn right", "Hotel", "Restroom", "Airport")),
            
            // 3 Listening Thai (spoken) with English words
            Triple("บ้าน อยู่ ไกล", "House / Home|Have|Far", listOf("Near / Close", "Ticket", "Train", "Turn")),
            Triple("เลี้ยวขวา ไป วัด", "Turn right|Go|Temple", listOf("Airport", "Hotel", "Transit", "Go straight")),
            Triple("นั่ง รถตุ๊กตุ๊ก ไป เที่ยว", "Riding|tuk-tuk|is|fun|to|tour", listOf("Train", "Hotel", "Map", "Where"))
        )

        for (i in sentences504.indices) {
            val (questionText, correctAnswer, distractors) = sentences504[i]
            val correctList = correctAnswer.split("|")
            val options = (correctList + distractors).shuffled()
            val type = ExerciseType.SENTENCE_BUILD
            
            if (i < 3) {
                list.add(Exercise(
                    id = 50400 + (i + 1),
                    lessonId = 504,
                    type = type,
                    prompt = "Assemble the Thai words that translate this sentence:",
                    question = questionText,
                    correctAnswer = correctAnswer,
                    romanization = "",
                    options = options,
                    audioText = correctAnswer.replace("|", " ")
                ))
            } else if (i < 6) {
                list.add(Exercise(
                    id = 50400 + (i + 1),
                    lessonId = 504,
                    type = type,
                    prompt = "Translate this Thai sentence into English:",
                    question = questionText,
                    correctAnswer = correctAnswer,
                    romanization = "",
                    options = options,
                    audioText = ""
                ))
            } else {
                list.add(Exercise(
                    id = 50400 + (i + 1),
                    lessonId = 504,
                    type = type,
                    prompt = "Listen and assemble the English translation:",
                    question = "",
                    correctAnswer = correctAnswer,
                    romanization = "",
                    options = options,
                    audioText = questionText
                ))
            }
        }

        val extraSentences = mapOf(
            505 to listOf(
                Triple("My father loves mother.", "พ่อ|รัก|แม่", listOf("พี่ชาย", "น้องสาว", "ลูก", "แฟน")),
                Triple("Grandfather and grandmother have children.", "คุณตา|และ|คุณยาย|มี|ลูก", listOf("เพื่อน", "สามี", "คน", "ดี")),
                Triple("I have a good family.", "ฉัน|มี|ครอบครัว|ดี", listOf("รัก", "ไม่", "เพื่อน", "ลูก")),
                Triple("สามี และ ภรรยา", "Husband|and|wife", listOf("Brother", "Sister", "Grandpa", "Friend")),
                Triple("พ่อแม่ รัก ลูก", "Parents|love|children", listOf("Single", "Married", "Ex", "Friend")),
                Triple("พี่ชาย ไม่ โสด", "Older brother|is|not|single", listOf("Good", "Like", "Love", "Married")),
                Triple("คุณตา รัก พี่สาว", "Grandfather|loves|older sister", listOf("husband", "wife", "parents", "ex-boyfriend")),
                Triple("น้องสาว และ น้องชาย", "Younger sister|and|younger brother", listOf("mother", "father", "husband", "wife")),
                Triple("คุณยาย มี แฟน ดี", "Grandmother|has|a|good|boyfriend", listOf("parents", "children", "single", "married"))
            ),
            506 to listOf(
                Triple("I am tired and sleepy.", "ฉัน|เหนื่อย|และ|ง่วง", listOf("สบาย", "เศร้า", "โกรธ", "สวย")),
                Triple("He speaks, I listen.", "เขา|พูด|ฉัน|ฟัง", listOf("เขียน", "อ่าน", "รู้", "ถาม")),
                Triple("I do not understand what you say.", "ฉัน|ไม่เข้าใจ|คุณ|พูด", listOf("รู้", "ตอบ", "ถาม", "เขียน")),
                Triple("เขากำลังพูดอะไร", "What|is|he|speaking?", listOf("Where", "Why", "When", "Who")),
                Triple("เธอ น่ารัก และ ใจดี", "She|is|cute|and|kind", listOf("Tired", "Angry", "Sleepy", "Sad")),
                Triple("ฉัน อยาก พูด ภาษาไทย", "I|want|to|speak|Thai", listOf("write", "listen", "read", "sleep")),
                Triple("เธอ มีความสุข ไหม", "Are|you|happy?", listOf("tired", "sleepy", "sad", "angry")),
                Triple("เขา โกรธ และ เศร้า", "He|is|angry|and|sad", listOf("happy", "comfortable", "cute", "kind")),
                Triple("คุณ สวย และ ใจดี", "You|are|beautiful|and|kind", listOf("sad", "angry", "tired", "sleepy"))
            ),
            507 to listOf(
                Triple("I like mango and sticky rice.", "ฉัน|ชอบ|มะม่วง|และ|ข้าวเหนียว", listOf("เบียร์", "ชาร้อน", "ส้ม", "ย่าง")),
                Triple("Please give me young coconut water.", "ขอ|น้ำมะพร้าวอ่อน", listOf("สุรา", "ไวน์", "กล้วย", "สับปะรด")),
                Triple("I want to drink Thai iced tea.", "ฉัน|อยาก|ดื่ม|ชาเย็น", listOf("ส้มตำ", "ไก่", "แกง", "สับปะรด")),
                Triple("ทุเรียน มี กลิ่นหอม", "Durian|is|very|fragrant", listOf("Banana", "Watermelon", "Omelet", "Beer")),
                Triple("ข้าวผัดปู และ ไข่เจียว", "Crab|fried|rice|and|omelet", listOf("Banana", "Young coconut water", "Hot tea", "Wine")),
                Triple("ฉัน ชอบ กิน ส้ม", "I|like|to|eat|oranges", listOf("boil", "fry", "steam", "bake")),
                Triple("ดื่ม ไวน์ และ เบียร์", "Drink|wine|and|beer", listOf("young coconut water", "orange juice", "hot tea", "omelet")),
                Triple("ย่าง ไก่ และ ทอด ปลา", "Grill|chicken|and|deep-fry|fish", listOf("banana", "mango", "watermelon", "pineapple")),
                Triple("ขอ น้ำส้ม และ น้ำแข็ง", "Please|give|orange|juice|and|ice", listOf("beer", "wine", "durian", "mango"))
            ),
            508 to listOf(
                Triple("This red shirt is too expensive.", "เสื้อ|สีแดง|นี้|แพงเกินไป", listOf("ลดสุดๆ", "ถูก", "สีเขียว", "เงิน")),
                Triple("I pay cash at the market.", "ฉัน|จ่ายเงินสด|ที่|ตลาดสด", listOf("ของขวัญ", "แว่นตากันแดด", "กระเป๋าสะพาย", "สีดำ")),
                Triple("Please bargain for a discount.", "กรุณา|ต่อราคาลด", listOf("จ่ายเงินสด", "ของขวัญ", "สีเหลือง", "เล็กจิ๋ว")),
                Triple("ฉัน ซื้อ เสื้อยืด สีขาว", "I|buy|a|white|t-shirt", listOf("black", "red", "blue", "green")),
                Triple("กระเป๋าสะพาย สีน้ำเงิน ราคา ถูก", "The|blue|bag|is|cheap", listOf("expensive", "heavy", "thick", "old")),
                Triple("เขา อยาก ซื้อ รองเท้าผ้าใบ", "He|wants|to|buy|sneakers", listOf("t-shirt", "pants", "socks", "hat")),
                Triple("เสื้อยืด สีเขียว ตัว เล็กจิ๋ว", "The|green|t-shirt|is|tiny", listOf("red", "white", "black", "blue")),
                Triple("ซื้อ แว่นตากันแดด และ หมวกฟาง", "Buy|sunglasses|and|a|straw|hat", listOf("pants", "socks", "t-shirt", "belt")),
                Triple("จ่ายเงินสด ซื้อ ของขวัญ สีชมพู", "Pay|cash|to|buy|a|pink|gift", listOf("green", "black", "blue", "yellow"))
            ),
            509 to listOf(
                Triple("Check-in at the hotel.", "เช็คอิน|โรงแรม", listOf("วีซ่า", "ตั๋ว", "ทางออก", "ทิศเหนือ")),
                Triple("Please give me the room key.", "ขอ|กุญแจ|ห้องพัก", listOf("กระเป๋าเดินทาง", "พนักงานต้อนรับ", "หมอน", "สะพาน")),
                Triple("I wait at the checkpoint.", "ฉัน|รอ|ที่|ด่านตรวจ", listOf("ไวไฟ", "อินเทอร์เน็ต", "ซอย", "ทิศใต้")),
                Triple("แสดง หนังสือเดินทาง และ วีซ่า", "Show|passport|and|visa", listOf("key", "pillow", "map", "towel")),
                Triple("เดิน ข้าม ทางม้าลาย", "Walk|cross|the|pedestrian|crossing", listOf("ride", "drive", "run", "stop")),
                Triple("ขึ้นรถ ที่ ทางเข้า โรงแรม", "Get|on|at|the|hotel|entrance", listOf("exit", "gate", "bridge", "intersection")),
                Triple("หลงทาง ใน ซอย", "Get|lost|in|the|alley", listOf("wait", "stop", "drive", "ride")),
                Triple("พนักงานต้อนรับ ดี และ สุภาพ", "The|receptionist|is|good|and|polite", listOf("tired", "angry", "sad", "lazy")),
                Triple("เลี้ยวขวา ที่ สามแยก", "Turn|right|at|the|junction", listOf("left", "straight", "bridge", "alley"))
            ),
            510 to listOf(
                Triple("The doctor is generous and kind.", "หมอ|ใจกว้าง|และ|ใจดี", listOf("ตำรวจ", "ทหาร", "ครู", "นักเรียน")),
                Triple("The student is diligent and polite.", "นักเรียน|ขยัน|และ|สุภาพ", listOf("หมอ", "ขี้เกียจ", "ขี้อาย", "ตำรวจ")),
                Triple("คุย และ หัวเราะ", "Chat|and|laugh", listOf("cry", "work", "study", "meet up")),
                Triple("ครู ช่วยเหลือ นักเรียน", "The|teacher|helps|students", listOf("doctor", "nurse", "police", "soldier")),
                Triple("ฉัน สัญญา จะ ซื่อสัตย์", "I|promise|to|be|honest", listOf("lazy", "shy", "patient", "polite")),
                Triple("พยาบาล ทำงาน ที่ โรงพยาบาล", "The|nurse|works|at|the|hospital", listOf("teacher", "student", "merchant", "soldier")),
                Triple("ตำรวจ ซื่อสัตย์ และ ขยัน", "The|police|is|honest|and|diligent", listOf("lazy", "shy", "ex-boyfriend", "boss")),
                Triple("น้องสาว ขี้อาย และ สุภาพ", "Younger|sister|is|shy|and|polite", listOf("generous", "cheerful", "honest", "lazy")),
                Triple("ปู่ เกษียณ และ มี ความสุข", "Grandfather|is|retired|and|happy", listOf("teacher", "doctor", "student", "soldier"))
            ),
            511 to listOf(
                Triple("Check the bill please.", "เก็บเงินด้วย", listOf("สั่งอาหาร", "รายการอาหาร", "ห่อกลับบ้าน", "รสชาติ")),
                Triple("Please give me spoon and fork.", "ขอ|ช้อน|และ|ส้อม", listOf("ตะเกียบ", "มีด", "แก้วน้ำ", "จาน")),
                Triple("I agree, the food is delicious.", "ฉัน|เห็นด้วย|อาหาร|อร่อย", listOf("ไม่เห็นด้วย", "ไม่จริง", "จริง", "แน่นอน")),
                Triple("ฝนตก และ หนาว", "Raining|and|cold", listOf("hot", "sunny", "windy", "sky")),
                Triple("ขอกระดาษทิชชู บน โต๊ะอาหาร", "Please|give|napkin|on|the|dining|table", listOf("spoon", "fork", "knife", "cup")),
                Triple("พนักงานต้อนรับ สุภาพ แน่นอน", "The|waiter|is|polite|definitely", listOf("angry", "tired", "sad", "lazy")),
                Triple("ฉัน ดีใจ ที่ คุณ สบายดี", "I|am|glad|that|you|are|healthy", listOf("sad", "angry", "hurt", "tired")),
                Triple("สภาพอากาศ วันนี้ แดดออก", "The|weather|today|is|sunny", listOf("raining", "windy", "cold", "sky")),
                Triple("สั่งอาหาร เผ็ดน้อย ได้ไหม", "Can|I|order|food|less|spicy?", listOf("take away", "check bill", "expensive", "cheap"))
            ),
            512 to listOf(
                Triple("Pay with a credit card.", "จ่าย|บัตรเครดิตพลาสติก", listOf("เงินสดกระดาษ", "สลิปโอนเงิน", "คูปอง", "สบู่เหลวหอม")),
                Triple("Please take a photo.", "ขอ|ถ่ายรูป", listOf("ลองสวม", "เลือกสรร", "ลืม", "หาย")),
                Triple("I forget my passport.", "ฉัน|ลืม|หนังสือเดินทาง", listOf("กล้อง", "ไกด์", "คูปอง", "แชมพูสระผม")),
                Triple("ระวัง ขโมย อันตราย", "Watch|out|dangerous|thief", listOf("towel", "umbrella", "soap", "shampoo")),
                Triple("โอนเงินเข้า บัญชีเงินฝาก", "Transfer|money|to|the|account", listOf("ATM", "credit card", "receipt", "toothbrush")),
                Triple("กล้อง สวยงาม และ ของแท้", "The|camera|is|beautiful|and|genuine", listOf("dangerous", "lost", "shampoo", "soap")),
                Triple("สแกนคิวอาร์ จ่ายเงิน", "Scan|QR|to|pay|money", listOf("ATM", "credit card", "shampoo", "toothbrush")),
                Triple("ไป โรงพยาบาล", "Go|to|the|hospital", listOf("hotel", "airport", "station", "shop")),
                Triple("ไกด์ แนะนำ ของฝากเมืองไทย", "The|guide|recommends|Thai|souvenirs", listOf("camera", "shampoo", "soap", "toothpaste"))
            ),
            513 to listOf(
                Triple("รวมกัน ที่ บ้าน", "Gather|at|home", listOf("party", "dessert", "garden", "fridge")),
                Triple("I miss you forever.", "ฉัน|คิดถึงกัน|ตลอดไป", listOf("มีความรัก", "อบอุ่น", "ปลอดภัย", "มีความหวัง")),
                Triple("The bedroom is warm and safe.", "ห้องนอน|อบอุ่น|และ|ปลอดภัย", listOf("ห้องครัว", "ทีวี", "ตู้เย็น", "หน้าต่าง")),
                Triple("มีความรัก และ ความสุข", "In|love|and|happiness", listOf("sadness", "tired", "angry", "sleepy")),
                Triple("เปิด ประตู และ หน้าต่าง", "Open|door|and|window", listOf("TV", "fridge", "bed", "garden")),
                Triple("กิน ของหวาน ใน ห้องครัว", "Eat|dessert|in|the|kitchen", listOf("bedroom", "living room", "garden", "TV")),
                Triple("งานเลี้ยง มี ความสุข", "The|party|has|happiness", listOf("bedroom", "fridge", "door", "window")),
                Triple("มี น้ำส้ม ใน ตู้เย็น", "There|is|orange|juice|in|the|fridge", listOf("bed", "TV", "door", "window")),
                Triple("นอน บน เตียงนอน", "Sleep|on|the|bed", listOf("TV", "fridge", "window", "door"))
            )
        )

        for ((lId, sList) in extraSentences) {
            for (i in sList.indices) {
                val (questionText, correctAnswer, distractors) = sList[i]
                val correctList = correctAnswer.split("|")
                val options = (correctList + distractors).shuffled()
                val type = ExerciseType.SENTENCE_BUILD
                
                if (i < 3) {
                    list.add(Exercise(
                        id = lId * 100 + (i + 1),
                        lessonId = lId,
                        type = type,
                        prompt = "Assemble the Thai words that translate this sentence:",
                        question = questionText,
                        correctAnswer = correctAnswer,
                        romanization = "",
                        options = options,
                        audioText = correctAnswer.replace("|", " ")
                    ))
                } else if (i < 6) {
                    list.add(Exercise(
                        id = lId * 100 + (i + 1),
                        lessonId = lId,
                        type = type,
                        prompt = "Translate this Thai sentence into English:",
                        question = questionText,
                        correctAnswer = correctAnswer,
                        romanization = "",
                        options = options,
                        audioText = ""
                    ))
                } else {
                    list.add(Exercise(
                        id = lId * 100 + (i + 1),
                        lessonId = lId,
                        type = type,
                        prompt = "Listen and assemble the English translation:",
                        question = "",
                        correctAnswer = correctAnswer,
                        romanization = "",
                        options = options,
                        audioText = questionText
                    ))
                }
            }
        }

        return list
    }

    private fun getSampleAchievements(): List<Achievement> {
        return listOf(
            Achievement("streak_1", "Streak Starter", "Achieve a 1-day study streak.", 0, 1, isUnlocked = false, "streak"),
            Achievement("streak_3", "Streak Master", "Achieve a 3-day study streak.", 0, 3, isUnlocked = false, "streak"),
            Achievement("stars_15", "Star Collector", "Earn 15 Stars in total.", 0, 15, isUnlocked = false, "star"),
            Achievement("stars_60", "Star Champion", "Earn 60 Stars in total.", 0, 60, isUnlocked = false, "star"),
            Achievement("lessons_3", "Graduate", "Complete 3 full lessons.", 0, 3, isUnlocked = false, "lesson")
        )
    }

    override suspend fun exportProgressJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()
        root.put("schemaVersion", 2)
        
        // 1. Progress
        val progress = userProgressDao.getProgressOnce() ?: UserProgressEntity.fromDomain(UserProgress())
        val format = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
        val formattedDate = format.format(java.util.Date())
        
        val updatedWithBackup = progress.copy(lastBackupTime = formattedDate)
        userProgressDao.saveProgress(updatedWithBackup)
        
        val progressObj = JSONObject().apply {
            put("name", updatedWithBackup.name)
            put("streak", updatedWithBackup.streak)
            put("xp", updatedWithBackup.xp)
            put("hearts", updatedWithBackup.hearts)
            put("level", updatedWithBackup.level)
            put("selectedLanguageGoal", updatedWithBackup.selectedLanguageGoal)
            put("lastActiveDate", updatedWithBackup.lastActiveDate)
            put("soundEnabled", updatedWithBackup.soundEnabled)
            put("isDarkMode", updatedWithBackup.isDarkMode)
            put("currentLessonId", updatedWithBackup.currentLessonId)
            put("showRomanizationOnly", updatedWithBackup.showRomanizationOnly)
            put("avatar", updatedWithBackup.avatar)
            put("lastBackupTime", updatedWithBackup.lastBackupTime)
            put("isOnboardingCompleted", updatedWithBackup.isOnboardingCompleted)
        }
        root.put("progress", progressObj)
        
        // 2. Lessons
        val lessonsList = lessonDao.getAllLessons().first()
        val lessonsArray = JSONArray()
        for (lesson in lessonsList) {
            val lessonObj = JSONObject().apply {
                put("id", lesson.id)
                put("unlocked", lesson.unlocked)
                put("completed", lesson.completed)
                put("stars", lesson.stars)
            }
            lessonsArray.put(lessonObj)
        }
        root.put("lessons", lessonsArray)
        
        // 3. Review Words
        val reviewWordsList = reviewWordDao.getAllReviewWords().first()
        val reviewArray = JSONArray()
        for (word in reviewWordsList) {
            val wordObj = JSONObject().apply {
                put("thai", word.thai)
                put("english", word.english)
                put("romanization", word.romanization)
                put("category", word.category)
                put("addedAt", word.addedAt)
                put("intervalDays", word.intervalDays)
                put("streak", word.streak)
                put("lastReviewedAt", word.lastReviewedAt)
                put("nextDueAt", word.nextDueAt)
                put("isMastered", word.isMastered)
            }
            reviewArray.put(wordObj)
        }
        root.put("reviewWords", reviewArray)
        
        root.toString(2)
    }

    override suspend fun importProgressJson(jsonString: String): Boolean = withContext(Dispatchers.IO + kotlinx.coroutines.NonCancellable) {
        try {
            val root = JSONObject(jsonString)
            if (!root.has("progress") || !root.has("lessons")) {
                android.util.Log.e("RepositoryImpl", "Import failed: progress or lessons missing")
                return@withContext false
            }
            
            // 1. Progress
            try {
                val progressObj = root.getJSONObject("progress")
                val name = progressObj.optString("name", "Thai Learner")
                val streak = progressObj.optInt("streak", 1)
                val xp = progressObj.optInt("xp", 0)
                val hearts = progressObj.optInt("hearts", 5)
                val level = progressObj.optInt("level", 1)
                val selectedLanguageGoal = progressObj.optInt("selectedLanguageGoal", 20)
                val lastActiveDate = progressObj.optString("lastActiveDate", "")
                val soundEnabled = progressObj.optBoolean("soundEnabled", true)
                val isDarkMode = progressObj.optBoolean("isDarkMode", false)
                val currentLessonId = progressObj.optInt("currentLessonId", 1)
                val showRomanizationOnly = progressObj.optBoolean("showRomanizationOnly", false)
                val avatar = progressObj.optString("avatar", "🐘 Elephant")
                val lastBackupTime = progressObj.optString("lastBackupTime", "")
                val isOnboardingCompleted = progressObj.optBoolean("isOnboardingCompleted", true)
                
                val updatedProgress = UserProgressEntity(
                    id = 1,
                    name = name,
                    streak = streak,
                    xp = xp,
                    hearts = hearts,
                    level = level,
                    selectedLanguageGoal = selectedLanguageGoal,
                    lastActiveDate = lastActiveDate,
                    soundEnabled = soundEnabled,
                    isDarkMode = isDarkMode,
                    currentLessonId = currentLessonId,
                    showRomanizationOnly = showRomanizationOnly,
                    avatar = avatar,
                    lastBackupTime = lastBackupTime,
                    isOnboardingCompleted = isOnboardingCompleted
                )
                userProgressDao.saveProgress(updatedProgress)
                android.util.Log.d("RepositoryImpl", "Successfully imported progress for user: $name")
            } catch (e: Exception) {
                android.util.Log.e("RepositoryImpl", "Failed to import progress sub-block", e)
            }
            
            // 2. Lessons
            try {
                val lessonsArray = root.getJSONArray("lessons")
                var successCount = 0
                for (i in 0 until lessonsArray.length()) {
                    try {
                        val lessonObj = lessonsArray.getJSONObject(i)
                        val lessonId = lessonObj.optInt("id", -1)
                        val unlocked = lessonObj.optBoolean("unlocked", false)
                        val completed = lessonObj.optBoolean("completed", false)
                        val stars = lessonObj.optInt("stars", 0)
                        
                        if (lessonId == -1) continue
                        
                        val existing = lessonDao.getLessonById(lessonId)
                        if (existing != null) {
                            val updatedLesson = existing.copy(
                                unlocked = unlocked,
                                completed = completed,
                                stars = stars
                            )
                            lessonDao.updateLesson(updatedLesson)
                            successCount++
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("RepositoryImpl", "Failed to import line $i in lessons", e)
                    }
                }
                android.util.Log.d("RepositoryImpl", "Imported $successCount / ${lessonsArray.length()} lessons successfully")
            } catch (e: Exception) {
                android.util.Log.e("RepositoryImpl", "Failed to import lessons main sub-block", e)
            }
            
            // 3. Review Words
            if (root.has("reviewWords")) {
                try {
                    reviewWordDao.clearReviewQueue()
                    val reviewArray = root.getJSONArray("reviewWords")
                    var successCount = 0
                    for (i in 0 until reviewArray.length()) {
                        try {
                            val wordObj = reviewArray.getJSONObject(i)
                            val thai = wordObj.optString("thai", "")
                            val english = wordObj.optString("english", "")
                            val romanization = wordObj.optString("romanization", "")
                            val category = wordObj.optString("category", "General")
                            val addedAt = wordObj.optLong("addedAt", System.currentTimeMillis())
                            val intervalDays = wordObj.optInt("intervalDays", 0)
                            val wordStreak = wordObj.optInt("streak", 0)
                            val lastReviewedAt = wordObj.optLong("lastReviewedAt", 0)
                            val nextDueAt = wordObj.optLong("nextDueAt", System.currentTimeMillis())
                            val isMastered = wordObj.optBoolean("isMastered", false)
                            
                            if (thai.isEmpty() || english.isEmpty()) continue
                            
                            val entity = ReviewWordEntity(
                                thai = thai,
                                english = english,
                                romanization = romanization,
                                category = category,
                                addedAt = addedAt,
                                intervalDays = intervalDays,
                                streak = wordStreak,
                                lastReviewedAt = lastReviewedAt,
                                nextDueAt = nextDueAt,
                                isMastered = isMastered
                            )
                            reviewWordDao.insertReviewWord(entity)
                            successCount++
                        } catch (e: Exception) {
                            android.util.Log.e("RepositoryImpl", "Failed to import line $i in reviewWords", e)
                        }
                    }
                    android.util.Log.d("RepositoryImpl", "Imported $successCount / ${reviewArray.length()} reviewWords successfully")
                } catch (e: Exception) {
                    android.util.Log.e("RepositoryImpl", "Failed to import reviewWords main sub-block", e)
                }
            }
            
            true
        } catch (e: Exception) {
            android.util.Log.e("RepositoryImpl", "Import failed with fatal error", e)
            false
        }
    }
}
