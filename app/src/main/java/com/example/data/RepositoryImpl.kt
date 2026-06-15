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
        // Since we changed vocabulary count to 500 and exercise count to 8 per lesson, let's reset and populate if needed
        val currentVocabCount = vocabularyDao.getVocabularyCount()
        val hasLesson113 = lessonDao.getLessonById(113) != null
        if (currentVocabCount < 500 || !hasLesson113) {
            // Clear current data first for clean repopulation
            vocabularyDao.clearVocabulary()
            lessonDao.clearLessons()
            exerciseDao.clearExercises()

            // Populate Vocabulary (500 words total!)
            val vocabulary = getSampleVocabulary()
            vocabularyDao.insertVocabulary(vocabulary.map { VocabularyEntity.fromDomain(it) })

            // Populate Lessons (50 lessons + 5 tests)
            val lessons = getSampleLessons()
            lessonDao.insertLessons(lessons.map { LessonEntity.fromDomain(it) })

            // Populate Exercises (fixed matching and sentence building for all 50 lessons)
            val exercises = getSampleExercises()
            exerciseDao.insertExercises(exercises.map { ExerciseEntity.fromDomain(it) })

            // Populate Achievements
            val achievements = getSampleAchievements()
            achievementDao.insertAchievements(achievements.map { AchievementEntity.fromDomain(it) })

            // Setup default progress (if not existing)
            if (userProgressDao.getProgressOnce() == null) {
                userProgressDao.saveProgress(UserProgressEntity.fromDomain(UserProgress()))
            }
        } else {
            // Make sure the Topic Test lessons are populated if they are missing
            if (lessonDao.getLessonById(101) == null || lessonDao.getLessonById(113) == null) {
                val tests = getSampleLessons().filter { it.id >= 100 }
                lessonDao.insertLessons(tests.map { LessonEntity.fromDomain(it) })
            }
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
            Vocabulary(28, "เธอ", "You (informal/female) / Her", "Thoe", "Greetings", "เธอน่ารักมาก", "You are very cute."),
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
            Vocabulary(281, "เงินสดกระดาษ", "Cash", "Ngen sot", "Numbers", "ตลาดสดเล็กๆ ไม่รับสแกนต้องใช้เงินสดหมื่นบาท", "Tiny markets only take cash, no scanning."),
            Vocabulary(282, "บัตรเครดิตพลาสติก", "Credit card", "Bat khredit", "Numbers", "ห้างแบรนด์เนมสไลด์รูดบัตรเครดิตสะดวกมาก", "Mall slide-swipes credit card easily."),
            Vocabulary(283, "เงินทอนครบ", "Give change / Refund", "Thon ngen", "Numbers", "ทอนเงินทอนครบถ้วนไร้กังวลยอดโกงเงิน", "Giving exact correct change, no fraud worries."),
            Vocabulary(284, "เงินเงินทอน", "Change / Coins", "Ngen thon", "Numbers", "โกยเหรียญและเศษเงินเงินทอนใส่กระเป๋าสะพายก้าว", "Stashed coins and change into sling-bag."),
            Vocabulary(285, "ตู้กดเงิน", "ATM", "ATM", "Numbers", "ตู้กดเงินเอบีเอ็มโชว์รหัสวิซ่าล่มชั่วคราว", "ATM displays service error during visa check."),
            Vocabulary(286, "โอนเงินเข้า", "Transfer money", "On ngen", "Numbers", "โอนเงินเข้าบัญชีโรงพยาบาลช่วยผู้ป่วยเจ็บ", "Transfer money to hospital to help injured souls."),
            Vocabulary(287, "บัญชีเงินฝาก", "Account", "Banchi", "Numbers", "ออมเงินล้านบาทลึกในบัญชีส่วนบุคคลมั่นคง", "Keep million Baht safe in individual account."),
            Vocabulary(288, "สแกนคิวอาร์", "Scan", "Scan", "Numbers", "ช้อปปิ้งสแกนคิวอาร์สะดวกรวดเร็วไม่พกเงินสด", "Bargaining with QR scan, no cash needed."),
            Vocabulary(289, "สลิปโอนเงิน", "Receipt / Slip", "Slip", "Numbers", "ส่งภาพถ่ายสลิปยืนยันการจ่ายเงินซื้อของฝาก", "Sent transfer receipt photo to back purchase."),
            Vocabulary(290, "ตั๋วแลกเงิน", "Bank note / Note", "Tua ngen", "Numbers", "พกตั๋วแลกเงินแสนล้านผ่านตรวจด่านตม.ราบรื่น", "Carried high-value notes through customs safely."),
            Vocabulary(291, "ของฝากเมืองไทย", "Souvenir / Gift", "Khong fak", "Numbers", "ซื้อพวงกุญแจตุ๊กตาช้างเป็นของฝากเพื่อนหมอ", "Bought elephant keychain souvenir for doctor friend."),
            Vocabulary(292, "เลือกสรร", "Choose / Select", "Lueak", "Numbers", "เลือกสรรเสื้อผ้าสีเหลืองสยบสีดำเรียบหรู", "Select yellow shirt over classic plain black."),
            Vocabulary(293, "ลองสวม", "Try on", "Long", "Numbers", "ลองสวมแว่นตารองเท้าผ้าใบสะพายเป้เท่ระบาด", "Try on sunglasses, sneakers, backpack."),
            Vocabulary(294, "พวงกุญแจช้าง", "Keychain", "Phuang kunchae", "Numbers", "พวงกุญแจไม้ทำรูปช้างราคาห้าสิบบาทขาดตัว", "Elephant wooden keychain priced fifty Baht flat."),
            Vocabulary(295, "โปสการ์ดวัดอรุณ", "Postcard", "Postcard", "Numbers", "บันเดิลกระดาษเขียนส่งโปสการ์ดสวดพรให้ยาย", "Wrote a lovely postcard to grandma wishing well."),
            Vocabulary(296, "ร่มกางฝน", "Umbrella", "Rom", "Numbers", "กางร่มกันแดดกลางสะพานลอยลมพัดแรงสะพัด", "Open umbrella to block sun on breezy walk."),
            Vocabulary(297, "สบู่เหลวหอม", "Soap", "Sabu", "Numbers", "ถูสบู่เหลวเย็นซ่าล้างเนื้อตัวหลังลุยฝนตก", "Wash with cooling liquid soap after rain shower."),
            Vocabulary(298, "แชมพูสระผม", "Shampoo", "Shampoo", "Numbers", "สระนวดผมด้วยแชมพูมะกรูดสมุนไพรสดเปี่ยมสะอาด", "Wash hair with organic bergamot shampoo."),
            Vocabulary(299, "แปรงสีฟันสะอาด", "Toothbrush", "Praeng see fan", "Numbers", "เปลี่ยนแปรงสีฟันอันใหม่นุ่มสบายเหงือกฟัน", "Swapped to a brand new soft-bristle toothbrush."),
            Vocabulary(300, "ยาสีฟันระบาย", "Toothpaste", "Ya see fan", "Numbers", "บีบยาสีฟันรสชาติสะระแหน่เย็นซ่าฟองหอมฟุ้ง", "Squeezed minty-fresh cooling toothpaste.")
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
            Vocabulary(323, "ภูเขาสูง", "Mountain", "Phu khao", "Travel", "ไปตากอากาศลากไกด์นำทัวร์ขึ้นภูเขาสูง", "Going to the high mountains with our guide."),
            Vocabulary(324, "น้ำตกใส", "Waterfall", "Nam tok", "Travel", "อาบต้มเย็นน้ำตกกระเด้งสะพานหน้าผายักษ์", "Bathing in clean cold mountain waterfalls."),
            Vocabulary(325, "ตลาดน้ำโบราณ", "Floating market", "Talat nam", "Travel", "นั่งเรือพายชมแม่ค้าตลาดน้ำโบราณพัดเบสิก", "Riding a paddleboat to explore floating markets."),
            Vocabulary(326, "สวนสาธารณะ", "Park / Garden", "Suan satharana", "Travel", "พาสุนัขจิ๋ววิ่งกลมรอบสวนสาธารณะร่มรื่น", "Took our tiny puppy to run around the green park."),
            Vocabulary(327, "พิพิธภัณฑ์ศิลป์", "Museum / Archives", "Phiphithaphan", "Travel", "ยืนสงบนิ่งเสพรูปภาพในพิพิธภัณฑ์ศิลป์ระดับชาติ", "Admirably looking at ancient paintings in museum."),
            Vocabulary(328, "สวนสัตว์ปลา", "Zoo / Wildlife park", "Suan sat", "Travel", "พาน้องสาวซื้อตั๋วชี้ดูกวางเสือในสวนสัตว์ปลา", "Bought a ticket to watch deer and tigers at zoo."),
            Vocabulary(329, "ในเมืองหลวง", "City / Urban", "Mueang", "Travel", "รถไฟฟ้าบีทีเอสส่งตรงสะพานใกล้จุดตึกในเมืองหลวง", "Skytrain travels straight to the city center."),
            Vocabulary(330, "ต่างจังหวัด", "Countryside / Province", "Tang changwat", "Travel", "ขึ้นสายรถทัวร์หนีเมืองยักษ์ไปสูดควันต่างจังหวัด", "Taking tour bus away from urban chaos to countryside."),
            Vocabulary(331, "รถบัสทัวร์", "Bus / Coach", "Rot bat", "Travel", "กระเป๋าเดินทางสิบใบเก็บลานเก็บล้อใต้รถบัสทัวร์", "Ten bags of luggage stored under tour bus belly."),
            Vocabulary(332, "รถเมล์ประจำทาง", "City bus", "Rot me", "Travel", "โหนจับราวยาวเหงื่อท่วมเบียดเสียดในรถเมล์ประจำทาง", "Holding rails tight inside hot crowded city buses."),
            Vocabulary(333, "เรือหางยาว", "Boat / Vessel", "Rua", "Travel", "เรือหางยาวติดเครื่องเบสิกสับฟองคลื่นสาดเปียกฝน", "Longtail boat motor roars across white splash waves."),
            Vocabulary(334, "เครื่องบินยักษ์", "Airplane / Flight", "Khruang bin", "Travel", "เครื่องบินเหินสยบท้องฟ้าข้ามไปเมืองจีนสามแยก", "Giant airplane soars high crossing skies above clouds."),
            Vocabulary(335, "จักรยานถีบ", "Bicycle / Cycle", "Chakkrayan", "Travel", "ขี่จักรยานชมชายแดนวัดโพธิ์ใกล้สะพานโรงแรม", "Riding a bicycle around temples near the hotel."),
            Vocabulary(336, "มอเตอร์ไซค์รับจ้าง", "Motorcycle", "Motorcycle", "Travel", "สวมหมวกสีแดงซ้อนวินมอเตอร์ไซค์รับจ้างซิ่งคล่อง", "Wore red helmet riding taxi motorcycle cleanly."),
            Vocabulary(337, "แท็กซี่มิเตอร์", "Taxi / Cab", "Taxi", "Travel", "เปิดประตูขึ้นโบกทางขึ้นแท็กซี่มิเตอร์ติดแอร์ชุ่ม", "Opened door to call a cold aircon taxi meter."),
            Vocabulary(338, "รถไฟฟ้าใต้ดิน", "Subway / MRT", "MRT", "Travel", "แลกเหรียญกลมซื้อตั๋วแตะรูดผ่านคิวรถไฟฟ้าใต้ดิน", "Swapped coins to scan ticket entering subway station."),
            Vocabulary(339, "รถไฟลอยฟ้า", "Skytrain / BTS", "BTS", "Travel", "สแกนสลิปผ่านทางขึ้นรถไฟลอยฟ้าสถานีตึกสิบชัน", "Scanned ticket receipt to ascend skytrain gates."),
            Vocabulary(340, "สถานีตำรวจหนุน", "Police station", "Sathani tamruat", "Travel", "รีบวิ่งหน้าตื่นลนลานไปแจ้งเรื่องด่านสถานีตำรวจหนุน", "Ran in absolute urgency straight to the police station."),
            Vocabulary(341, "เช็คอินเร็ว", "Check-in", "check-in", "Travel", "เดินแตะเคาน์เตอร์แจ้งเช็คอินโรงแรมห้าแสนดาวหมอก", "Approached receptionist counter to do hotel check-in."),
            Vocabulary(342, "เช็คเอาท์ด่วน", "Check-out", "check-out", "Travel", "คืนรหัสกุญแจเช็คเอาท์เตรียมลากเป้ลุยแผนที่ต่อ", "Returned keys doing check-out to resume travel."),
            Vocabulary(343, "กุญแจห้องหัก", "Key / Card key", "Kunchae", "Travel", "กระเป๋าเป้ซ่อนกุญแจทองหรูเหลี่ยมคล้องพวงช้าง", "My backpack kept a secure golden card key."),
            Vocabulary(344, "ห้องอัปเกรด", "Room suite / Bedchamber", "Hong phak", "Travel", "เปิดกลอนประตูแง้มรับห้องอัปเกรดกว้างเตียงขนนุ่ม", "Unlocked door to find a spacious luxury room suite."),
            Vocabulary(345, "เตียงกว้าง", "Bed / Mattress", "Tiang", "Travel", "ม้วนลอยตัวทิ้งเหนื่อยลงบนเตียงกว้างปุยขนนกขาว", "Roll dived onto a fluffy feathered soft bed."),
            Vocabulary(346, "ผ้าเช็ดตัวใหม่", "Towel / Bath towel", "Pha chet tua", "Travel", "เช็ดตัวเย็นฉ่ำให้แห้งด้วยผ้าเช็ดตัวใหม่กลิ่นน้ำหอม", "Wiped dry with freshly scented giant bath towel."),
            Vocabulary(347, "อินเทอร์เน็ตห่วย", "Internet / Web", "internet", "Travel", "เช็คสตรีมภาพโพสต์วิดีโอผ่านอินเทอร์เน็ตคุณภาพสูง", "Connected to global server via online high-speed web."),
            Vocabulary(348, "รหัสไวไฟ", "Wifi password", "wifi", "Travel", "ถ่ายรูปป้ายรหัสไวไฟใต้เก้าอี้ต้อนรับหน้าล็อบบี้", "Captured wifi password tag sign from lobby desk."),
            Vocabulary(349, "พนักงานต้อนรับยิ้ม", "Receptionist / Clerk", "receptionist", "Travel", "ถอดแว่นสายตาทักทายพนักงานต้อนรับยิ้มชวนสบายดี", "Greeted the ever-smiling professional desk clerk."),
            Vocabulary(350, "หมอนขนนิ่ม", "Pillow / Cushion", "Mon", "Travel", "ดึงหนุนหมอนนุ่มนิ่มรองเท้าสะพานคอระบายลอยหลับ", "Pillowed head onto dream comfy cloud cushion."),
            Vocabulary(351, "ตารางเวลาวิ่ง", "Timetable", "tarang wela", "Travel", "สแกนถ่ายแผนภูมิตารางเวลาวิ่งรถไฟฟ้าไม่ผิดนัด", "Captured skytrain scheduling timetable printout."),
            Vocabulary(352, "กระเป๋าเดินทางล้อ", "Luggage / Suitcase", "kra pao doentang", "Travel", "ลากกระเป๋าเดินทางล้อเลื่อนหนักก้าวข้ามแอปเปิ้ลหล่น", "Wheeled heavy black travel suitcase across terminal."),
            Vocabulary(353, "หนังสือเดินทางไทย", "Passport", "passport", "Travel", "ดึงยื่นหนังสือเดินทางไทยเล่มส้มผ่านช่องตม.ทันที", "Handed orange tourist passport to officer."),
            Vocabulary(354, "วีซ่าผ่านทาง", "Visa paper", "visa", "Travel", "ถือวีซ่าประทับตราอนุญาตสิบปีเข้าเมืองหลวงราบรื่น", "Clipped 10-year tourist visa paper directly."),
            Vocabulary(355, "ตรวจคนเข้าเมือง", "Immigration / Customs", "TMy", "Travel", "สลัดแว่นตาดำโค้งคำนับยิ้มทักพนักงานด่านตรวจคนเข้าเมือง", "Took off sunglasses greeting immigration officer."),
            Vocabulary(356, "ด่านสแกน", "Border / Security checkpoint", "dan", "Travel", "ผ่านด่านสแกนค้นตัวไม่เจอขวดละเงินทองสูญพ้น", "Cleared scanning at airport security checkpoint."),
            Vocabulary(357, "เที่ยวบินด่วน", "Flight number", "thiao bin", "Travel", "หูเงี่ยฟังประกาศเที่ยวบินเลทชั่วโมงสามสิบนาที", "Listened hard to delay announcements of flight."),
            Vocabulary(358, "ทางออกหนีไฟ", "Exit sign", "thang ok", "Travel", "วิ่งจ็อกกิ้งก้าวตามป้ายทางออกหนีไฟสีเขียวสว่างดึก", "Walked matching green emergency exit signs."),
            Vocabulary(359, "ทางเข้าหลัก", "Entrance / Gate", "thang khao", "Travel", "ประตูกระจกทางเข้าหลักเปิดกวักไอคอนการค้าสะพานลอย", "Automatic glass entry at major entrance gate opens."),
            Vocabulary(360, "ประตูล็อค", "Gate terminal / Portal", "pratu", "Travel", "ลนลานสแกนสลิปตั๋ววิ่งหาประตูล็อคเบอร์ยี่สิบหกตรง", "Ran matching ticket layout to find gate terminal 26."),
            Vocabulary(361, "แอปแผนที่นำ", "GPS Map", "phan", "Travel", "พึ่งพาแอปแผนที่นำจีพีเอสนำซอยลึกหมาเห่าระบาด", "Relying on mobile GPS map app inside deep alleys."),
            Vocabulary(362, "ทิศเหนือลมเย็น", "North direction", "thit nuea", "Travel", "หันหน้าหาทิศเหนือลมเย็นพัดผ่านยอดภูเขาสูงขาว", "Chilling north wind blows from high mountain peaks."),
            Vocabulary(363, "ทิศใต้ทะเลอุ่น", "South direction", "thit tai", "Travel", "ขับเที่ยวทิศใต้ทะเลอุ่นต้อนรับกระแสน้ำพริกเผ็ด", "Driving to the sunny south of blue beaches."),
            Vocabulary(364, "ทิศตะวันออกไบ", "East direction", "thit tawan ok", "Travel", "ตื่นสี่ทุ่มดูอาทิตย์แซมฟ้าทอกทิศตะวันออกไบ", "Woke up early watching sunrise east horizon skies."),
            Vocabulary(365, "ทิศตะวันตกดิ่ง", "West direction", "thit tawan tok", "Travel", "ถ่ายโพลารอยด์อาทิตย์ลับทรายเฉียงทิศตะวันตกดิ่ง", "Captured golden sunset fading on west coastal sandy sea."),
            Vocabulary(366, "สี่แยกไฟกะพริบ", "Intersection / Crossroad", "see yaek", "Travel", "สี่แยกไฟกะพริบระวังชนวินข้ามทางม้าลายเหลือง", "Crossing dynamic traffic intersection safety check."),
            Vocabulary(367, "สามแยกตลาด", "Junction", "sam yaek", "Travel", "สามแยกตลาดโบราณขายมะม่วงทุเรียนน้ำอุ่นอร่อย", "Old market three-way road junction."),
            Vocabulary(368, "สะพานพระรามเก้า", "Bridge / Flyover", "saphan", "Travel", "เดินพึ่งแดดถ่ายฉากตึกยักษ์เหนือสะพานพระรามเก้า", "Walking bridge with breathtaking view of sky."),
            Vocabulary(369, "ทางม้าลายมล", "Pedestrian crossing", "thang ma lai", "Travel", "จูงมือคุณตาข้ามถนนสิบเลนหุ้มทางม้าลายมลสะอาด", "Walked grandpa safely through pedestrian stripe crossing."),
            Vocabulary(370, "ซอยแคบมาก", "Alleyway / Lane", "soi", "Travel", "แท็กซี่เลี้ยวผ่านซอยแคบมากเบียดกำแพงบ้านสวยงาม", "Taxi enters narrow alleyway between old brick homes."),
            Vocabulary(371, "ขี่ช้างสุรินทร์", "Ride / Mount", "khee", "Travel", "ห้างจัดทัวร์ขี่ช้างสุรินทร์ท่องเที่ยวพนาสะพานลอย", "Fulfilling dream of mounting Asian elephant travel."),
            Vocabulary(372, "ขับแท็กซี่", "Drive / Pilot", "khap", "Travel", "คุณพ่อทำงานขับแท็กซี่หาเบียร์สองบาทซื้อไก่", "Father works driving city taxis for home food."),
            Vocabulary(373, "เดินช้อปปิ้ง", "Walk / Stroll", "doen", "Travel", "ลากเพื่อนรักเดินช้อปปิ้งหาของทอดกล้วยทอดตลาดสด", "Walked looking at delicious street food."),
            Vocabulary(374, "วิ่งจ็อกกิ้งรอบ", "Run / Sprint", "wing", "Travel", "ตื่นเช้าหกนาฬิกาวิ่งจ็อกกิ้งรอบสวนสาธารณะสะใจปอด", "Started morning running speed inside the park."),
            Vocabulary(375, "หยุดรถรวด", "Stop / Halt", "yut", "Travel", "เหยียบเบรกเตะหยุดรถรวดเร็วเฉียดหมาสองตัวนอนหลับ", "Pushed brakes hard to stop vehicle on path."),
            Vocabulary(376, "รอรับสลิปเงิน", "Wait / Linger", "ro", "Travel", "ต่อแถวต่อราคาเสร็จยืนรอรับสลิปเงินคืนห้าบาท", "Stood waiting patiently at billing cash counter."),
            Vocabulary(377, "หลงทางในป่า", "Get lost / Lost", "long thang", "Travel", "แผนที่หายเปิดจีพีเอสเดี้ยงหลงทางในป่าภูเขาใหญ่", "Relying on helper while getting lost in city."),
            Vocabulary(378, "ข้ามฝั่งแม่น้ำ", "Cross over / Intersect", "kham", "Travel", "นั่งเรือหางยาวข้ามฝั่งแม่น้ำเจ้าพระยาไปกินเป็ด", "Rode boat crossing the river to roasted food shop."),
            Vocabulary(379, "ขึ้นรถอย่างรวดเร็ว", "Get on / Board", "khuen rot", "Travel", "กวักมือเรียกตุ๊กตุ๊กโบกขึ้นรถอย่างรวดเร็วฝนกระหน่ำ", "Waved hand getting on vehicle quickly to shield rain."),
            Vocabulary(380, "ลงรถโดยสาร", "Get off / Alight", "long rot", "Travel", "กดกริ่งแท็กซี่ไฟสว่างลงรถโดยสารหน้าทางเข้าห้าง", "Pressed button getting off vehicle near front gate."),
            Vocabulary(381, "ถ่ายภาพครอบครัว", "Take photo / Shoot", "thai phap", "Travel", "ชักมือถือเลนส์งามแชะถ่ายภาพครอบครัวหนุนวัดงาม", "Took memorable holiday photo shot of family."),
            Vocabulary(382, "กล้องระดับโปร", "Camera lens", "klong", "Travel", "กล้องระดับโปรจับภาพแตงโมสุกแดดเก๋งชายหาดเย็น", "Took beach camera to shoot sunset photos."),
            Vocabulary(383, "ไกด์ชวนรู้", "Tour guide / Pilot", "guide", "Travel", "ไกด์ชวนรู้แนะนำประวัติศาสตร์เจดีย์ทองโบราณสิบชั้น", "Interactive travel guide explaining temple details."),
            Vocabulary(384, "แนะนำร้านอร่อย", "Recommend / Endorse", "nae nam", "Travel", "พี่พนักงานแนะนำร้านอร่อยหมูกรอบส้มตำเด็ดดวงใต้สะพาน", "Highly recommend local crispy fried roasted dishes."),
            Vocabulary(385, "คูปองลดอาหาร", "Coupon voucher", "coupon", "Travel", "ฉีกรับยื่นคูปองลดอาหารสิบห้าบาทฟรีแกกล่องสด", "Gave a paper coupon voucher receiving sweet discount."),
            Vocabulary(386, "แผ่นพับท่องเที่ยว", "Leaflet handbook", "leaflet", "Travel", "หยิบกางแผ่นพับท่องเที่ยวหน้าสนามบินดึงแผนที่", "Reading free guide leaflet handbook about city."),
            Vocabulary(387, "ลดราคาสินค้า", "Discount ticket", "lot rakha", "Travel", "ป้ายแดงหราลดราคาสินค้ารองเท้าคู่หมี่เหลือง", "Shop displayed discount tags on winter items."),
            Vocabulary(388, "เที่ยวสนุกเพลิดเพลิน", "Travel joy / Fun trip", "thiao sanuk", "Travel", "เดินทางปลอดภัยขอให้เที่ยวสนุกเพลิดเพลินเมืองไทย", "Wish you have extreme travel joy visiting Chiang Mai."),
            Vocabulary(389, "สวยงามอัศจรรย์", "Beautiful view", "suay ngam", "Travel", "วัดพระแก้วมีสีศิลปะทองคำสวยงามอัศจรรย์ตาโลก", "Emerald temple beauty is incredibly stunning."),
            Vocabulary(390, "ของแท้ร้อยเปอร์", "Genuine brand", "khong thae", "Travel", "ผ้าพันคอสัมผัสขนนกของแท้ร้อยเปอร์ไม่มีต้มตุ๋น", "Genuine high-quality handwoven Thai silk scarf."),
            Vocabulary(391, "ช่วยด้วยคนตก", "Emergency help", "chuay duay", "Travel", "ส่งเสียงตะโกนช่วยด้วยคนตกสะพานแม่น้ำตกปลายนิ้ว", "Screamed emergency help call as bag floated away."),
            Vocabulary(392, "โรงพยาบาลฉุกเฉิน", "Hospital building", "rong phayaban", "Travel", "รถไซเรนส่งตรงคนเจ็บเข้าโรงพยาบาลฉุกเฉินระดับจี", "Ambulance sped to the general hospital ward."),
            Vocabulary(393, "ยารูปวงกลม", "Medicine pills", "ya", "Travel", "ปวดหัวเจ็บเท้าหมอจัดยารูปวงกลมแกะต้มกินสบาย", "Doctor prescribed effective medicine pills."),
            Vocabulary(394, "คลินิกทันตแพทย์", "Medical clinic", "clinic", "Travel", "นัดพบตรวจคราบหินปูนคลินิกทันตแพทย์ย่านตึกสิบชัน", "Visiting dental care clinic inside city."),
            Vocabulary(395, "รถพยาบาลไซเรน", "Ambulance", "ambulance", "Travel", "เสียงหวูดดังรถพยาบาลไซเรนสับซอยหลีกแยกด่วนจี", "Ambulance speeds past traffic gridlock safely."),
            Vocabulary(396, "ลืมเงินทอน", "Forget / Loose memory", "luem", "Travel", "เดินเหม่อลอยซื้อไก่ลืมเงินทอนแปดสิบบาทน่าเจ็บหัว", "I forgot my shopping change coin on counter."),
            Vocabulary(397, "กระเป๋าสะพายหาย", "Lost / Vanished", "hai", "Travel", "สืบค้นหากกุญแจกระเป๋าสะพายหายลนลานพึ่งแผนที่สแกน", "My wallet went lost, searching around lobby."),
            Vocabulary(398, "ขโมยของสะพาย", "Thief robbery", "khamoy", "Travel", "ตะโกนเรียกตำรวจขโมยของสะพายส่งสายตาโกรธจัดหัว", "Stop that thief who grabbed our travel backpack!"),
            Vocabulary(399, "อันตรายเขตราง", "Dangerous sign", "antarai", "Travel", "ป้ายห้ามขึ้นจับเข็มสะพานลอยเสาไฟอันตรายเขตราง", "Danger high voltage fence sign keep safe distance."),
            Vocabulary(400, "ระวังหนูสัตว์", "Watch out! / Caution", "rawang", "Travel", "ทางโค้งซอยมืดลื่นระวังหนูสัตว์ตกถนนกระแทกลงหัว", "Watch out on wet lane to drive safely!")
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
            Vocabulary(430, "ญาติคนเยอะ", "Relatives / Cousin", "Yat", "Family", "งานรวมญาติคนเยอะคึกคักคุยกันก้องห้องนั่งเล่น", "Big family reunion meeting up together inside lobby."),
            Vocabulary(431, "สามีแต่งงาน", "Husband", "samee", "Family", "สามีขยันตื่นตอนเช้าขับรถยนต์ทำงานคลินิกยี่สิบปี", "Diligently working husband drives to clinic daily."),
            Vocabulary(432, "ภรรยาสตรี", "Wife", "phanraya", "Family", "ภรรยาเตรียมแกงเขียวหวานต้มยำกุ้งรสชาติอร่อยมาก", "The sweet wife cooks tasty green curry and soup."),
            Vocabulary(433, "พ่อแม่เลี้ยงดู", "Parents / Guardians", "pho mae", "Family", "รักเคารพพ่อแม่เลี้ยงดูส่งเสียเรียนหนังสือจบสูงสุดเก่ง", "Highly respect parents who raised us to graduate."),
            Vocabulary(434, "แต่งงานสม", "Married / Wedlock", "taeng ngan", "Family", "คู่รักแต่งงานอบอุ่นใจสัญญารักกันชั่วกาลนานตลอดไป", "The lovely couple married promising to love forever."),
            Vocabulary(435, "โสดสนิท", "Single status", "sot", "Family", "โสดสนิทสนุกสนานชวนเพื่อนรักตะลอนเที่ยวทะเลชายหาด", "Happily single traveling beaches with best friends."),
            Vocabulary(436, "แฟนเก่ารัก", "Ex-lover / Ex-partner", "fan kao", "Family", "แม้เป็นแฟนเก่ารักยังคุยยิ้มปรารถนาดีช่วยเหลือเกื้อหนุน", "Even as exes, we remain supportive of each other."),
            Vocabulary(437, "เพื่อนบ้านคุย", "Neighbor / Resident", "phuan ban", "Family", "เพื่อนบ้านใจดีแบ่งปันผลไม้มะละกอกล้วยสับปะรดให้เรากอบ", "Friendly neighbor shares fresh organic papaya fruits."),
            Vocabulary(438, "เพื่อนร่วมงานเจ๋ง", "Colleague / Coworker", "phuan ruam ngan", "Family", "เพื่อนร่วมงานสองคนแฉะถ่ายรูปชิ้นงานส่งหัวหน้าสแกน", "Colleague captures and uploads sheet slide review to boss."),
            Vocabulary(439, "หัวหน้าขยัน", "Boss / Supervisor", "hua na", "Family", "หัวหน้าขยันชมเชยเลื่อนขั้นมอบกระเป๋าเงินทอนแสนบาท", "Diligent boss praises team and awards high bonus."),
            Vocabulary(440, "ลูกน้องคอย", "Subordinate / Staff", "look nong", "Family", "ลูกน้องคอยช่วยเหลืองานเตรียมสไลด์ตารางเวลาห้างปิด", "Dynamic staff organizes schedules prior to release."),
            Vocabulary(441, "หมอฟันช่วย", "Doctor / Physician", "mo", "Family", "หมอบอกว่าเจ็บน่องซ้ายขอนึ่งยารูปวงกลมให้กินระงับ", "Professional doctor specifies pill recipe to reduce pain."),
            Vocabulary(442, "พยาบาลเฝ้า", "Nurse", "phayaban", "Family", "พยาบาลเฝ้าคนไข้เจ็บบริการน้ำส้มชาอุ่นตลอดสิบสองชั่วโมง", "Kind nurse brings orange juice and tea to patients."),
            Vocabulary(443, "ครูใหญ่สอน", "Teacher / Educator", "khru", "Family", "ครูใหญ่สอนนักเรียนขยันเขียนตอบแบบประเมินสีชมพู", "The principal teaches students to write diligent replies."),
            Vocabulary(444, "นักเรียนดี", "Student / Pupil", "nak rian", "Family", "นักเรียนดีอ่านหนังสือหนาเตอะไม่ขี้เกียจสอบได้เต็ม", "Diligent student reads thick books scoring high marks."),
            Vocabulary(445, "ตำรวจจราจร", "Police officer", "tamruat", "Family", "ตำรวจจราจรเป่านกหวีดคุมสี่แยกทางม้าลายสว่างไฟสิบ", "Police officer blowing whistle directs intersection flow."),
            Vocabulary(446, "ทหารบกป้องกัน", "Soldier / Military", "thahan", "Family", "ทหารบกเข้มแข็งป้องกันป่าเขาแถวชายแดนอันตรายซ่อน", "Strong soldiers shield borders from national danger."),
            Vocabulary(447, "ค้าขายมะม่วง", "Merchant / Trader", "kha khai", "Family", "ค้าขายตลาดน้ำพายเรือสั่งกุยช้ายทอดกล้วยไข่ทอดร้อน", "Traditional canal dealer selling sweet hot dishes."),
            Vocabulary(448, "นักธุรกิจหนุ่ม", "Businessman / Exec", "nak thurakit", "Family", "นักธุรกิจร้อยล้านโอนเงินสดพันล้านผ่านม่านรถไฟฟ้า", "Successful executive transferring high funds safely."),
            Vocabulary(449, "วิศวกรโครง", "Engineer / Builder", "witsawakon", "Family", "วิศวกรวิลล่าสร้างสะพานถนนข้ามซอยใหญ่สี่เลนลื่นไหลก้าว", "Expert engineer building giant urban flyover bridge."),
            Vocabulary(450, "แม่บ้านซัก", "Housewife / Maid", "mae ban", "Family", "แม่บ้านซักผ้าปูที่นอนหมอนอิงสะอาดใส่น้ำหอมกลิ่นกล้วย", "Maid washes bedsheets till they are clean-scented."),
            Vocabulary(451, "คุยสนุกเก", "Chat / Talk", "khuy", "Family", "นั่งลานหญ้าล้อมคุยสนุกเรื่องของขวัญตลาดน้ำพริก", "Relaxing on green grass chatting about holiday gifts."),
            Vocabulary(452, "หัวเราะร่า", "Laugh / Chuckle", "hua ro", "Family", "ทุกคนหัวเราะร่าความสุขเปี่ยมล้นใจหน้าสวนหน้าขาว", "Everyone giggles with absolute smiles in home garden."),
            Vocabulary(453, "ร้องไห้หนัก", "Cry / Weep", "rong hai", "Family", "เด็กตัวน้อยร้องไห้หนักพ่อลืมซื้อโปสการ์ดของฝากตุ๊กตา", "The kid wept because father forgot to buy the toy."),
            Vocabulary(454, "นัดพบปะ", "Meet up / Rendezvous", "nat phop", "Family", "นัดพบปะเพื่อนร่วมงานห้าโมงเย็นจิบกาแฟร้อนแบรนด์หรู", "Set a coordinate to meet colleagues for hot coffee."),
            Vocabulary(455, "รู้จักรัก", "Know / Acquainted", "ru chak", "Family", "ยินดีได้รับรู้จักอาคุณยายผู้ขยันซื่อสัตย์สุภาพใจดี", "Pleased to be acquainted with your kind grandmother."),
            Vocabulary(456, "ช่วยเหลือกัน", "Assist / Help out", "chuay luea", "Family", "คนในซอยช่วยเหลือกันลากกระเป๋าเดินทางข้ามทางหมาลาย", "Neighbors help wheeled heavy suitcases across intersection."),
            Vocabulary(457, "แบ่งปันร้อย", "Share / Divide", "baeng pan", "Family", "แบ่งปันส้มผลไม้รสแกงเข็ดต้มยำปูกลางโต๊ะกลมปรก", "Share papaya salads and fresh mango fruits cleanly."),
            Vocabulary(458, "สัญญามั่น", "Promise / Oath", "sanya", "Family", "สัญญามั่นรักภรรยาตลอดไปชั่วฟ้าดินทราเสากุญแจ", "Vowed a solemn promise to love spouse forever."),
            Vocabulary(459, "เชื่อใจหมอ", "Trust / Faith", "chua chai", "Family", "เชื่อใจพยาบาลหมอจัดยาที่ดีให้ร่างกายสบายดีกระเตื้อง", "Trust the clinic to prescribe correct healthy pills."),
            Vocabulary(460, "คิดถึงบ้าน", "Miss / Yearn for", "khit thueng", "Family", "เดินทางไกลเมืองหลวงเจ็ดคืนคิดถึงบ้านห้องนอนอุ่นเตียง", "Traveling far away makes me miss my sweet cozy bed."),
            Vocabulary(461, "อายุครบสิบ", "Age / Life span", "ayu", "Family", "น้องชายอายุครบสิบขวบชอบขี่จักรยานวิ่งสวนวัด", "Younger brother's age is ten years old this week."),
            Vocabulary(462, "วันเกิดสุข", "Birthday / Natal day", "wan koet", "Family", "ฉลองเป่าขนมเค้กวันเกิดสุขลวดลายถุงเท้าขนนิ่มสุดขีด", "Blew birthday candles receiving lovely warm socks."),
            Vocabulary(463, "ชื่อเล่นต๊ะ", "Nickname", "cheu len", "Family", "ฉันมีชื่อเล่นต๊ะไกด์พาทับทิมเที่ยวตอกยู่สบายจัง", "My colloquial nickname is Ta, nice to meet you."),
            Vocabulary(464, "นามสกุลรัก", "Last name / Surname", "nam sakun", "Family", "เขียนนามสกุลรักษ์ถิ่นไทยในช่องวีซ่าด่านสนามบินด่วน", "Wrote family surname strictly on immigration forms."),
            Vocabulary(465, "ระบุเพศ", "Gender / Sex", "phet", "Family", "ช่องฟอร์มระบุเพศหญิงเพศชายระดับภาษาถูกต้องยอด", "Specify male/female gender on the questionnaire form."),
            Vocabulary(466, "เบอร์โทร", "Phone number", "ber thorasap", "Family", "จดบันทึกเขียนเบอร์โทรติดต่อลุงเผื่อหลงทางซอยลึกดึก", "Jotted phone number down in case I get lost in alley."),
            Vocabulary(467, "ที่อยู่บ้าน", "Address / Residence", "thee yu", "Family", "แท็กซี่ขับส่งถึงที่อยู่บ้านเลขที่สามสิบสี่สะปังโสด", "The cab drove straight to our home address correctly."),
            Vocabulary(468, "ทำงานออฟ", "Work / Labor", "tham ngan", "Family", "ลุงทำงานออฟฟิศหรูตึกสิบชั้นได้เงินหมื่นบาทแสนสุขใจ", "Uncle labors in city office for ten thousand Baht salary."),
            Vocabulary(469, "เรียนต้ม", "Study / Learn", "rian", "Family", "ฉันเรียนต้มยำกุ้งส้มตำไข่เจียวหมูกรอบจากป้าใจดี", "I study cooking pad thai and soups from my aunt."),
            Vocabulary(470, "เกษียณสุข", "Retired status", "kasiat", "Family", "ปู่เกษียณสุขภาพดีชอบเดินชมวัดโพธิ์กางร่มทฟองแดง", "Grandfather is happily retired strolling around temples."),
            Vocabulary(471, "นิสัยนิ่ม", "Habit / Temperament", "nisai", "Family", "พี่สาวนิสัยนิ่มนวลสุภาพจริงใจพูดคุยน่ารักสวยเสมอ", "Older sister has sweet polite temperament, very cute."),
            Vocabulary(472, "ใจกว้างสุด", "Generous / Caring", "chai kwang", "Family", "ลุงเป็นคนใจกว้างสุดๆ เลี้ยงข้าวผัดราดซอสผลไม้หวาน", "Our generous uncle treated us to tasty fried rice plates."),
            Vocabulary(473, "ขี้อายเก่ง", "Shy / Timid", "khee ai", "Family", "น้องสาวขี้อายเก่งเวลาคนแปลกหน้าถามชื่อชื่อเล่นเพศ", "Sister is quite shy when strangers ask her questions."),
            Vocabulary(474, "สนุกสนานรื่น", "Cheerful / Merry", "sanuk sanan", "Family", "คุยเพื่อนบ้านหัวเราะรื่นลานสวนมีความสนุกสนานรื่นเริง", "Chatting with cheerful neighbors inside home garden."),
            Vocabulary(475, "ซื่อสัตย์แท้", "Honest / Loyal", "suay sat", "Family", "ตำรวจต้องขยันซื่อสัตย์แท้ดูแลประชาชนพ้นขโมยร้าย", "Police officers must be highly honest patrolling streets."),
            Vocabulary(476, "ขยันอัด", "Diligent / Hardworking", "khayan", "Family", "แม่ขยันอัดเย็บเสื้อเสื้อหนาวขัดเงินทอนบาทประหยัด", "Mother is hardworking at design making winter clothes."),
            Vocabulary(477, "ขี้เกียจลุก", "Lazy / Slothful", "khee kiat", "Family", "คืนหนาวเหน็ดขี้เกียจลุกล้างมีดช้อนส้อมนอนห่มนาหนา", "Feeling slothful and choosing to sleep inside cozy sheets."),
            Vocabulary(478, "อดทนรอบ", "Patient / Tolerant", "ot thon", "Family", "รอคิวสามแยกนานต้องอดทนรอบรถเมล์โบกพยากล้อง", "Gridlocks require patients waiting for our city bus."),
            Vocabulary(479, "สุภาพสุด", "Polite / Courteous", "suphap", "Family", "พนักงานต้อนรับสุภาพสุดยกมือสวัสดีครับคุณยายคุณตา", "The host is courteous greeting grandpa and grandma."),
            Vocabulary(480, "ลึกลับลึก", "Mysterious / Hidden", "luep lap", "Family", "แผนที่แสดงวัดลึกลับลึกห่างไกลกลางป่าเขาสูงหมอก", "The layout keeps a mysterious ancient hidden temple."),
            Vocabulary(481, "บ้านหลัง", "Home / Lodge", "ban", "Family", "บ้านหลังน้อยทาสีชมพูหนุนสวนสาธารณะริมน้ำสวยงาม", "Cozy pink home backing onto water and park view."),
            Vocabulary(482, "ห้องนอนอุ่น", "Bedroom / Suite", "hong non", "Family", "จัดเตียงนอนแชมพูผ้าห่มหนาเดี๋ยวในห้องนอนอุ่นใจ", "Sleeping comfortably inside a clean warm bedroom."),
            Vocabulary(483, "ห้องนั่งเล่นขวัญ", "Living room", "hong nang len", "Family", "พักผ่อนล้อมวงห้องนั่งเล่นขวัญพวงกุญแจหัวเราะร่า", "We gather inside cozy family living room to chat."),
            Vocabulary(484, "ห้องครัวต้ม", "Kitchen room", "hong khrua", "Family", "แม่เปิดแก๊สห้องครัวต้มไข่สับกระเทียมส้มพริกน้ำตาล", "The aroma of cooking spices sweeps around kitchen."),
            Vocabulary(485, "สวนร่มรื่น", "Garden park", "suan", "Family", "ป้าจูงเด็กจิ๋วชมสวนร่มรื่นดอกมะม่วงสีเหลืองเบ่ง", "Walking around clean garden filled with yellow flowers."),
            Vocabulary(486, "หน้าต่างม่าน", "Window glass", "na tang", "Family", "แง้มเหลี่ยมหน้าต่างม่านสีน้ำเงินมองเห็นฝนตกชุ่ม", "Opened blue window glass curtains watching rainy storm."),
            Vocabulary(487, "ประตูอัต", "Door portal", "pratu", "Family", "เคาะกวักลักประตูอัตเรียกน้องชายให้ปลดล็อคกุญแจ", "Opened the mahogany wooden door of home directly."),
            Vocabulary(488, "ทีวีกว้าง", "TV screen", "tevee", "Family", "นอนหยอดสุกกินปูดูละครทีวีกว้างแปดสิบนิ้วเพลิน", "Watching colorful series screens on nice main tv."),
            Vocabulary(489, "ตู้เย็นเย็น", "Fridge / Refrigerator", "tu yen", "Family", "หยิบขวดน้ำเบียร์ส้มตู้เย็นเย็นมาลดร้อนแดดเผาหัว", "Grabbing ice and orange juice from cooling fridge."),
            Vocabulary(490, "เตียงนอนนวด", "Bed / Mattress cushion", "tiang non", "Family", "สลัดเข็มขัดท่อนร่างทิ้งเหนื่อยลงเตียงนอนนวดหัวใจ", "Divine soft mattress cushion for relaxing sleep."),
            Vocabulary(491, "มีความรักรส", "In love / Affection", "mee khwam rak", "Family", "คู่สมใจมีความรักรสลอยฟุ้งเฉียบนมชมพูกล่องฟิน", "Feelings of deep romantic affection and love."),
            Vocabulary(492, "คิดถึงกันเสมอ", "Miss each other", "khit thueng kan", "Family", "ห่างไกลข้ามเมืองหลวงต่างจังหวัดขอเราคิดถึงกันเสมอ", "Even across cities we miss each other always."),
            Vocabulary(493, "อบอุ่นใจ", "Warm comfort", "op un", "Family", "กอดพ่อแม่ลูกสัมผัสอบอุ่นใจไร้ทุกข์โศกเศร้าลมหนาว", "Hugging family brings warm comfort and safety."),
            Vocabulary(494, "ปลอดภัยพ้น", "Safe / Protected", "plot phai", "Family", "ตำรวจตรวจตราสถานีตำรวจด่านให้พวกเราปลอดภัยพ้นภัย", "Police officers patrol streets keeping us highly safe."),
            Vocabulary(495, "มีความหวังวาด", "Hopeful / Bright", "mee khwam wang", "Family", "เด็กเรียนเก่งวิศวกรทำงานในเมืองมีความหวังวาดฝัน", "Dreaming of the future with hopeful bright minds."),
            Vocabulary(496, "รวมกันเฉลิม", "Gather / Assemble", "ruam kan", "Family", "พวกเราสิบคนรวมกันเฉลิมกินต้มยำปลาทับทิมทอดแห้ง", "Gathering around tables celebrating family holiday."),
            Vocabulary(497, "งานเลี้ยงปี", "Festive party", "ngan liang", "Family", "เปิดไวน์แดงฉลองงานเลี้ยงปีใหม่ทุกคนหัวเราะร่ารื่น", "Popping red wine celebrating New Year festive party."),
            Vocabulary(498, "ของหวานเชื่อม", "Sweet dessert", "khong wan", "Family", "กินส้มตำเปรี้ยวเผ็ดสยบด้วยของหวานเชื่อมมะพร้าวอ่อน", "Finished our spicy meal with sweet coconut dessert."),
            Vocabulary(499, "ความสุขล้น", "Happiness / Joy", "khwam suk", "Family", "ขอคุณยายคุณตามีน่าอายุยืนยาวล้านปีความสุขล้น", "Wishing grandpa and grandma absolute happiness and joy."),
            Vocabulary(500, "ตลอดไปถนอม", "Forevermore / Eternal", "talot pai", "Family", "ครอบครัวอบอุ่นรักถนอมกันมั่นสัญญาตลอดไปถนอมใจ", "Loving our sweet family and friends forevermore.")
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

            // 1. English word -> select Thai (multiple choice)
            val w1 = lessonVocab[0]
            val otherThais1 = vocabulary.filter { it.id != w1.id }
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

            // 3. Listening exercise. Sound plays in Thai. Answers in English
            val w3 = lessonVocab[2]
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

            // 5. English -> Thai Sentence Building Exercise (SENTENCE_BUILD)
            val sentenceEnToTh1 = when (lessonId) {
                1 -> Triple("Hello, nice to meet you.", "สวัสดี|ยินดีที่ได้รู้จัก", listOf("ขอโทษ", "ขอบคุณ", "ไม่ใช่", "โชคดี"))
                2 -> Triple("Goodbye, see you again.", "ลาก่อน|แล้วพบกันใหม่", listOf("สวัสดี", "ยินดีเสมอกับคุณครับ", "ใช่", "ผมชื่อ"))
                5 -> Triple("Delicious food.", "อาหาร|อร่อย", listOf("น้ำ", "ข้าว", "ผลไม้", "ขอโทษ"))
                6 -> Triple("I eat spicy shrimp soup.", "ฉัน|กิน|ต้มยำกุ้ง", listOf("กาแฟ", "หวาน", "ข้าว", "น้ำ"))
                9 -> Triple("Three Baht.", "สาม|บาท", listOf("ห้า", "ราคา", "สิบ", "หนึ่ง"))
                10 -> Triple("How much is the price?", "ราคา|เท่าไหร่", listOf("เงิน", "บาท", "แพง", "ถูก"))
                13 -> Triple("Where is the restroom?", "ห้องน้ำ|ที่ไหน", listOf("โรงแรม", "แผนที่", "สนามบิน", "ไป"))
                14 -> Triple("Go straight to the temple.", "ตรงไป|วัด", listOf("รถไฟ", "เลี้ยวซ้าย", "บ้าน", "ตั๋ว"))
                17 -> Triple("I love older sister.", "ฉัน|รัก|พี่สาว", listOf("น้องชาย", "ครอบครัว", "พ่อ", "เพื่อน"))
                18 -> Triple("Mother and father are good.", "แม่|และ|พ่อ|ดี", listOf("รัก", "คุณตา", "มี", "ไม่"))
                else -> {
                    val w0 = lessonVocab.getOrElse(0) { lessonVocab[0] }
                    val w1 = lessonVocab.getOrElse(1) { lessonVocab[0] }
                    val eng0 = w0.english.split("/").first().trim()
                    val eng1 = w1.english.split("/").first().trim()
                    Triple("$eng0 and $eng1.", "${w0.thai}|และ|${w1.thai}", listOf("ไม่ใช่", "ใช่", "สบายดี", "ยินดี"))
                }
            }

            val enToThCorrect1 = sentenceEnToTh1.second
            val enToThCorrectList1 = enToThCorrect1.split("|")
            val enToThOptions1 = (enToThCorrectList1 + sentenceEnToTh1.third).shuffled()
            list.add(Exercise(
                id = lessonId * 100 + 5,
                lessonId = lessonId,
                type = ExerciseType.SENTENCE_BUILD,
                prompt = "Assemble the Thai words that translate this sentence:",
                question = sentenceEnToTh1.first,
                correctAnswer = enToThCorrect1,
                romanization = "",
                options = enToThOptions1,
                audioText = enToThCorrect1.replace("|", " ")
            ))

            // 6. Thai -> English Sentence Building Exercise (SENTENCE_BUILD)
            val sentenceThToEn1 = when (lessonId) {
                1 -> Triple("ใช่ สบายดี", "Yes|I am fine", listOf("Hello", "Sorry", "No", "Goodbye"))
                2 -> Triple("สบายดี ขอบคุณ", "I am fine|Thank you", listOf("Yes", "Goodbye", "Not correct", "You"))
                5 -> Triple("กิน ข้าว", "Eat|Rice", listOf("Water", "Coffee", "Thank you", "Delicious"))
                6 -> Triple("ดื่ม กาแฟ", "Drink|Coffee", listOf("Eat", "Pork", "Fish", "Rice"))
                9 -> Triple("แกงเขียวหวาน แพง", "Green curry|Expensive", listOf("Delicious", "Cheap / Correct", "Egg", "Rice"))
                10 -> Triple("ซื้อ ไข่ ห้า", "Buy|Egg|Five", listOf("Eat", "Money", "Three", "Pork"))
                13 -> Triple("ไป สนามบิน", "Go|Airport", listOf("Turn left", "Restroom", "Hotel", "Tuk-Tuk"))
                14 -> Triple("โรงแรม ใกล้", "Hotel|Near / Close", listOf("Far", "Station", "Airport", "Map"))
                17 -> Triple("พ่อ รัก ลูก", "Father|Love|Child / Offspring", listOf("Mother", "Friend", "Older brother", "Near / Close"))
                18 -> Triple("ฉัน ชอบ ครอบครัว", "I (female)|Like|Family", listOf("Love", "Not", "Good", "Friend"))
                else -> {
                    val w4 = lessonVocab.getOrElse(4) { lessonVocab[0] }
                    val w5 = lessonVocab.getOrElse(5) { lessonVocab[0] }
                    val eng4 = w4.english.split("/").first().trim()
                    val eng5 = w5.english.split("/").first().trim()
                    Triple("${w4.thai} และ ${w5.thai}", "$eng4|and|$eng5", listOf("Hello", "Sorry", "No", "Goodbye"))
                }
            }

            val thToEnCorrect1 = sentenceThToEn1.second
            val thToEnCorrectList1 = thToEnCorrect1.split("|")
            val thToEnOptions1 = (thToEnCorrectList1 + sentenceThToEn1.third).shuffled()
            list.add(Exercise(
                id = lessonId * 100 + 6,
                lessonId = lessonId,
                type = ExerciseType.SENTENCE_BUILD,
                prompt = "Translate this Thai sentence into English:",
                question = sentenceThToEn1.first,
                correctAnswer = thToEnCorrect1,
                romanization = "",
                options = thToEnOptions1,
                audioText = ""
            ))

            // 7. Listening Sentence Building Exercise (SENTENCE_BUILD)
            val sentenceThToEn2 = when (lessonId) {
                1 -> Triple("สบายดีไหม ขอบคุณ", "How are you?|Thank you", listOf("Sorry", "Yes", "Goodbye", "Hello"))
                2 -> Triple("ยินดีด้วย คุณ", "Congratulations|You", listOf("Good night", "How are you?", "Glad", "See you again"))
                5 -> Triple("กาแฟ อร่อย", "Coffee|Delicious", listOf("Water", "Spicy shrimp soup", "Stir-fried noodles", "Papaya salad"))
                6 -> Triple("กิน ไข่ อร่อย", "Eat|Egg|Delicious", listOf("Pork", "Green curry", "Chicken", "Sweet"))
                9 -> Triple("ราคา สิบ บาท", "Price|Ten|Baht", listOf("One", "Two", "Five", "Hundred"))
                10 -> Triple("เสื้อ ราคา ถูก", "Shirt|Price|Cheap / Correct", listOf("Buy", "Nine", "Six", "Eight"))
                13 -> Triple("เลี้ยวซ้าย ไป สถานี", "Turn left|Go|Station", listOf("Turn right", "Hotel", "Restroom", "Airport"))
                14 -> Triple("บ้าน อยู่ ไกล", "House / Home|Have|Far", listOf("Near / Close", "Ticket", "Train", "Turn"))
                17 -> Triple("พี่สาว มี เพื่อน", "Older sister|Have|Friend", listOf("Older brother", "Younger sister", "Family", "Love"))
                18 -> Triple("คุณยาย และ คุณตา", "Grandmother (maternal)|And|Grandfather (maternal)", listOf("Child / Kid", "Good", "Not", "Have"))
                else -> {
                    val w6 = lessonVocab.getOrElse(6) { lessonVocab[0] }
                    val w7 = lessonVocab.getOrElse(7) { lessonVocab[0] }
                    val eng6 = w6.english.split("/").first().trim()
                    val eng7 = w7.english.split("/").first().trim()
                    Triple("${w6.thai} และ ${w7.thai}", "$eng6|and|$eng7", listOf("Good night", "How are you?", "Glad", "See you again"))
                }
            }

            val thToEnCorrect2 = sentenceThToEn2.second
            val thToEnCorrectList2 = thToEnCorrect2.split("|")
            val thToEnOptions2 = (thToEnCorrectList2 + sentenceThToEn2.third).shuffled()
            list.add(Exercise(
                id = lessonId * 100 + 7,
                lessonId = lessonId,
                type = ExerciseType.SENTENCE_BUILD,
                prompt = "Listen and assemble the English translation:",
                question = "", // Empty to play only Thai voice with no text shown
                correctAnswer = thToEnCorrect2,
                romanization = "",
                options = thToEnOptions2,
                audioText = sentenceThToEn2.first
            ))

            // 8. Additional Translation Question (MULTIPLE_CHOICE to compensate)
            val normalCorrectAnswer = thToEnCorrect2.replace("|", " ")
            val distractor1 = sentenceThToEn1.second.replace("|", " ")
            val distractor2 = sentenceEnToTh1.first
            val distractor3 = "Please try again."
            val mcOptions = listOf(normalCorrectAnswer, distractor1, distractor2, distractor3).distinct().shuffled()
            list.add(Exercise(
                id = lessonId * 100 + 8,
                lessonId = lessonId,
                type = ExerciseType.MULTIPLE_CHOICE,
                prompt = "Translate this Thai sentence:",
                question = sentenceThToEn2.first,
                correctAnswer = normalCorrectAnswer,
                romanization = "",
                options = mcOptions,
                audioText = sentenceThToEn2.first
            ))
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
        root.put("schemaVersion", 1)
        
        // 1. Progress
        val progress = userProgressDao.getProgressOnce() ?: UserProgressEntity.fromDomain(UserProgress())
        val progressObj = JSONObject().apply {
            put("name", progress.name)
            put("streak", progress.streak)
            put("xp", progress.xp)
            put("hearts", progress.hearts)
            put("level", progress.level)
            put("selectedLanguageGoal", progress.selectedLanguageGoal)
            put("lastActiveDate", progress.lastActiveDate)
            put("soundEnabled", progress.soundEnabled)
            put("isDarkMode", progress.isDarkMode)
            put("currentLessonId", progress.currentLessonId)
            put("showRomanizationOnly", progress.showRomanizationOnly)
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

    override suspend fun importProgressJson(jsonString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val root = JSONObject(jsonString)
            if (!root.has("progress") || !root.has("lessons")) {
                return@withContext false
            }
            
            // 1. Progress
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
                showRomanizationOnly = showRomanizationOnly
            )
            userProgressDao.saveProgress(updatedProgress)
            
            // 2. Lessons
            val lessonsArray = root.getJSONArray("lessons")
            for (i in 0 until lessonsArray.length()) {
                val lessonObj = lessonsArray.getJSONObject(i)
                val lessonId = lessonObj.getInt("id")
                val unlocked = lessonObj.getBoolean("unlocked")
                val completed = lessonObj.getBoolean("completed")
                val stars = lessonObj.getInt("stars")
                
                val existing = lessonDao.getLessonById(lessonId)
                if (existing != null) {
                    val updatedLesson = existing.copy(
                        unlocked = unlocked,
                        completed = completed,
                        stars = stars
                    )
                    lessonDao.updateLesson(updatedLesson)
                }
            }
            
            // 3. Review Words
            if (root.has("reviewWords")) {
                reviewWordDao.clearReviewQueue()
                val reviewArray = root.getJSONArray("reviewWords")
                for (i in 0 until reviewArray.length()) {
                    val wordObj = reviewArray.getJSONObject(i)
                    val thai = wordObj.getString("thai")
                    val english = wordObj.getString("english")
                    val romanization = wordObj.getString("romanization")
                    val category = wordObj.getString("category")
                    val addedAt = wordObj.optLong("addedAt", System.currentTimeMillis())
                    val intervalDays = wordObj.optInt("intervalDays", 0)
                    val wordStreak = wordObj.optInt("streak", 0)
                    val lastReviewedAt = wordObj.optLong("lastReviewedAt", 0)
                    val nextDueAt = wordObj.optLong("nextDueAt", System.currentTimeMillis())
                    val isMastered = wordObj.optBoolean("isMastered", false)
                    
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
                }
            }
            
            true
        } catch (e: Exception) {
            android.util.Log.e("RepositoryImpl", "Import failed", e)
            false
        }
    }
}
