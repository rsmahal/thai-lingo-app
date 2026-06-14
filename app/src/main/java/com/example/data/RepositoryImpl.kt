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
        // Since we changed vocabulary count to 100 and exercise count to 8 per lesson, let's reset and populate if needed
        val currentVocabCount = vocabularyDao.getVocabularyCount()
        val lesson1Exercises = exerciseDao.getExercisesByLessonId(1)
        if (currentVocabCount < 100 || lesson1Exercises.size < 8) {
            // Clear current data first for clean repopulation
            vocabularyDao.clearVocabulary()
            lessonDao.clearLessons()
            exerciseDao.clearExercises()

            // Populate Vocabulary (100 words total!)
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

            // Setup default progress (if not existing)
            if (userProgressDao.getProgressOnce() == null) {
                userProgressDao.saveProgress(UserProgressEntity.fromDomain(UserProgress()))
            }
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
            // Greetings Lesson 1 (1-10)
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

            // Greetings Lesson 2 (11-20)
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

            // Food & Drink Lesson 3 (21-30)
            Vocabulary(21, "ข้าว", "Rice", "Khao", "Food", "ฉันชอบกินข้าวเหนียว", "I like to eat sticky rice."),
            Vocabulary(22, "น้ำ", "Water", "Nam", "Food", "ขอน้ำเปล่าแก้วหนึ่งครับ", "Please give me a glass of water."),
            Vocabulary(23, "อาหาร", "Food", "Ahan", "Food", "อาหารไทยอร่อยมาก", "Thai food is very delicious."),
            Vocabulary(24, "ต้มยำกุ้ง", "Spicy shrimp soup", "Tom yum goong", "Food", "ต้มยำกุ้งหม้อนี้เผ็ดมาก", "This pot of Tom Yum Goong is very spicy."),
            Vocabulary(25, "ผัดไทย", "Stir-fried noodles", "Pad Thai", "Food", "สั่งผัดไทยหนึ่งจานครับ", "Order one plate of Pad Thai, please."),
            Vocabulary(26, "ส้มตำ", "Papaya salad", "Som tam", "Food", "ส้มตำไทยไม่ใส่พริก", "Thai papaya salad without chili."),
            Vocabulary(27, "ผลไม้", "Fruit", "Phonlamai", "Food", "ผลไม้ไทยมีหลายชนิด", "There are many kinds of Thai fruits."),
            Vocabulary(28, "กาแฟ", "Coffee", "Kafae", "Food", "ฉันดื่มกาแฟร้อนตอนเช้า", "I drink hot coffee in the morning."),
            Vocabulary(29, "อร่อย", "Delicious", "Aroy", "Food", "ทุเรียนนี้อร่อยมาก", "This durian is very delicious."),
            Vocabulary(30, "เผ็ด", "Spicy", "Phet", "Food", "แกงเขียวหวานเผ็ดไหม", "Is the green curry spicy?"),

            // Food & Drink Lesson 4 (31-40)
            Vocabulary(31, "หิว", "Hungry", "Hiw", "Food", "ตอนนี้ฉันหิวข้าวแล้ว", "I am hungry for rice now."),
            Vocabulary(32, "กิน", "Eat", "Kin", "Food", "ไปกินข้าวกันเถอะ", "Let's go eat rice/food."),
            Vocabulary(33, "แกงเขียวหวาน", "Green curry", "Kaeng khiao wan", "Food", "ฉันกินแกงเขียวหวานกับข้าว", "I eat green curry with rice."),
            Vocabulary(34, "ไก่", "Chicken", "Kai", "Food", "ชอบกินไก่ย่างมาก", "I like to eat grilled chicken very much."),
            Vocabulary(35, "ชา", "Tea", "Cha", "Food", "ขอดื่มชาร้อนครับ", "I'd like to drink hot tea, please."),
            Vocabulary(36, "หมู", "Pork", "Moo", "Food", "ผัดกะเพราหมูสับไข่ดาว", "Stir-fried pork with holy basil and fried egg."),
            Vocabulary(37, "ปลา", "Fish", "Pla", "Food", "ฉันชอบกินปลาทอด", "I like to eat fried fish."),
            Vocabulary(38, "ไข่", "Egg", "Khai", "Food", "ไข่เจียวร้อนๆ อร่อยดี", "Hot omelet is delicious."),
            Vocabulary(39, "หวาน", "Sweet", "Wan", "Food", "ผลไม้นี้หวานมาก", "This fruit is very sweet."),
            Vocabulary(40, "ดื่ม", "Drink", "Duem", "Food", "เด็กๆ ดื่มนมทุกวัน", "Children drink milk every day."),

            // Numbers & Shopping Lesson 5 (41-50)
            Vocabulary(41, "หนึ่ง", "One", "Nung", "Numbers", "แมวหนึ่งตัว", "One cat."),
            Vocabulary(42, "สอง", "Two", "Song", "Numbers", "ขอเบียร์สองขวดครับ", "Two bottles of beer, please."),
            Vocabulary(43, "สาม", "Three", "Sam", "Numbers", "มีเวลาสามวัน", "Have three days."),
            Vocabulary(44, "สี่", "Four", "See", "Numbers", "สี่สิบห้าบาท", "Forty-five Baht."),
            Vocabulary(45, "ห้า", "Five", "Ha", "Numbers", "บวกอีกห้าบาทครับ", "Add five more Baht, please."),
            Vocabulary(46, "สิบ", "Ten", "Sip", "Numbers", "ราคาเก้าสิบเก้าบาท", "Price ninety-nine Baht."),
            Vocabulary(47, "ร้อย", "Hundred", "Roi", "Numbers", "หนึ่งร้อยบาทพอดี", "One hundred Baht exactly."),
            Vocabulary(48, "บาท", "Baht", "Baht", "Numbers", "จานละห้าสิบบาท", "Fifty Baht per plate."),
            Vocabulary(49, "ราคา", "Price", "Rakha", "Numbers", "ราคาเท่าไหร่ครับ", "What is the price?"),
            Vocabulary(50, "แพง", "Expensive", "Phaeng", "Numbers", "ของฝากนี้แพงมาก", "This souvenir is very expensive."),

            // Numbers & Shopping Lesson 6 (51-60)
            Vocabulary(51, "ถูก", "Cheap / Correct", "Thook", "Numbers", "เสื้อตัวนี้ราคาถูกดี", "This shirt is cheap / good price."),
            Vocabulary(52, "เท่าไหร่", "How much?", "Thao rai", "Numbers", "ส้มกิโลละเท่าไหร่ครับ", "How much per kilo of oranges?"),
            Vocabulary(53, "หก", "Six", "Hok", "Numbers", "มีไข่หกฟองในครัว", "There are six eggs in the kitchen."),
            Vocabulary(54, "เจ็ด", "Seven", "Chet", "Numbers", "ราคาเจ็ดสิบบาทครับ", "The price is seventy Baht."),
            Vocabulary(55, "แปด", "Eight", "Paet", "Numbers", "ทำงานแปดชั่วโมงต่อวัน", "Work eight hours per day."),
            Vocabulary(56, "เก้า", "Nine", "Kao", "Numbers", "ขอเก้าสิบบาททอนด้วย", "Keep ninety Baht, give change too."),
            Vocabulary(57, "ศูนย์", "Zero", "Soon", "Numbers", "คะแนนสอบเป็นศูนย์", "The exam score is zero."),
            Vocabulary(58, "เงิน", "Money", "Ngen", "Numbers", "ฉันไม่มีเงินเหลือเลย", "I don't have any money left at all."),
            Vocabulary(59, "เสื้อ", "Shirt", "Sua", "Numbers", "เสื้อตัวนี้ราคาถูกมาก", "This shirt is very cheap."),
            Vocabulary(60, "ซื้อ", "Buy", "Su", "Numbers", "ฉันอยากซื้อผลไม้", "I want to buy some fruits."),

            // Travel & Directions Lesson 7 (61-70)
            Vocabulary(61, "โรงแรม", "Hotel", "Rong raem", "Travel", "โรงแรมนี้น่าอยู่มาก", "This hotel is very nice to stay."),
            Vocabulary(62, "สนามบิน", "Airport", "Sanam bin", "Travel", "ตั๋วไปสนามบินสุวรรณภูมิ", "A ticket to Suvarnabhumi Airport."),
            Vocabulary(63, "สถานี", "Station", "Sathani", "Travel", "ถามทางไปสถานีรถไฟ", "Ask directions to the railway station."),
            Vocabulary(64, "ห้องน้ำ", "Restroom", "Hong nam", "Travel", "ห้องน้ำอยู่ข้างหลังครับ", "The restroom is in the back."),
            Vocabulary(65, "เลี้ยวซ้าย", "Turn left", "Liew sai", "Travel", "เลี้ยวซ้ายตรงสี่แยกหน้า", "Turn left at the next intersection."),
            Vocabulary(66, "เลี้ยวขวา", "Turn right", "Liew khwa", "Travel", "เลี้ยวขวาที่หน้าวัด", "Turn right in front of the temple."),
            Vocabulary(67, "ไป", "Go", "Pai", "Travel", "อยากไปเที่ยวภูเก็ต", "Want to travel to Phuket."),
            Vocabulary(68, "ที่ไหน", "Where?", "Thee nai", "Travel", "บ้านของคุณอยู่ที่ไหน", "Where is your house?"),
            Vocabulary(69, "แผนที่", "Map", "Phaen thee", "Travel", "เปิดแผนที่ในมือถือ", "Open the map on the phone."),
            Vocabulary(70, "รถตุ๊กตุ๊ก", "Tuk-Tuk", "Rot tuk-tuk", "Travel", "นั่งรถตุ๊กตุ๊กเที่ยวสนุกดี", "Riding a tuk-tuk is fun to tour around."),

            // Travel & Directions Lesson 8 (71-80)
            Vocabulary(71, "รถไฟ", "Train", "Rot fai", "Travel", "ฉันชอบนั่งรถไฟไปเที่ยว", "I like to take the train to travel."),
            Vocabulary(72, "วัด", "Temple", "Wat", "Travel", "วัดโพธิ์สวยงามมาก", "Wat Pho is very beautiful."),
            Vocabulary(73, "บ้าน", "House / Home", "Ban", "Travel", "ฉันอยากกลับบ้านแล้ว", "I want to go home already."),
            Vocabulary(74, "เลี้ยว", "Turn", "Liew", "Travel", "เลี้ยวตรงมุมนั้นเลยครับ", "Turn right at that corner."),
            Vocabulary(75, "ตรงไป", "Go straight", "Trong pai", "Travel", "ขับตรงไปอีกสองร้อยเมตร", "Drive straight for another two hundred meters."),
            Vocabulary(76, "ไกล", "Far", "Klai", "Travel", "สถานีรถไฟอยู่ไกลจากที่นี่ไหม", "Is the railway station far from here?"),
            Vocabulary(77, "ใกล้", "Near / Close", "Klai", "Travel", "โรงแรมอยู่ใกล้รถไฟฟ้า", "The hotel is near the skytrain."),
            Vocabulary(78, "รถยนต์", "Car", "Rot yon", "Travel", "ขับรถยนต์เที่ยวสนุกดี", "Driving a car is fun to travel."),
            Vocabulary(79, "ตั๋ว", "Ticket", "Tua", "Travel", "ซื้อตั๋วสถานีปลายทางไหน", "Which destination station for buying the ticket?"),
            Vocabulary(80, "เที่ยว", "Travel / Trip", "Thiao", "Travel", "สัปดาห์หน้าเพื่อนจะมาเที่ยว", "Next week, friends will come to travel."),

            // Family Lesson 9 (81-90)
            Vocabulary(81, "พ่อ", "Father", "Pho", "Family", "พ่อของฉันเป็นใจดีมาก", "My father is very kind."),
            Vocabulary(82, "แม่", "Mother", "Mae", "Family", "คุณแม่ทำอาหารอร่อยที่สุด", "Mother cooks the most delicious food."),
            Vocabulary(83, "พี่ชาย", "Older brother", "Phee chai", "Family", "พี่ชายของผมทำงานที่กรุงเทพฯ", "My older brother works in Bangkok."),
            Vocabulary(84, "พี่สาว", "Older sister", "Phee sao", "Family", "พี่สาวของเขาเรียนหมอ", "His older sister is studying medicine."),
            Vocabulary(85, "น้องชาย", "Younger brother", "Nong chai", "Family", "น้องชายชอบเล่นเกมฟุตบอล", "Younger brother likes to play football games."),
            Vocabulary(86, "น้องสาว", "Younger sister", "Nong sao", "Family", "น้องสาวเพิ่งเข้าโรงเรียนอนุบาล", "Younger sister just entered kindergarten."),
            Vocabulary(87, "ครอบครัว", "Family", "Khrop khrua", "Family", "พวกเราเป็นครอบครัวอบอุ่น", "We are a warm family."),
            Vocabulary(88, "รัก", "Love", "Rak", "Family", "ผมรักครอบครัวและเพื่อนๆ", "I love my family and friends."),
            Vocabulary(89, "เพื่อน", "Friend", "Phuan", "Family", "ฉันรักเพื่อนๆ ทุกคน", "I love all of my friends."),
            Vocabulary(90, "ลูก", "Child / Offspring", "Look", "Family", "ลูกๆ กำลังตื่นนอน", "The children are waking up."),

            // Family Lesson 10 (91-100)
            Vocabulary(91, "คุณยาย", "Grandmother (maternal)", "Khun yai", "Family", "คุณยายใจดีมากๆ เลยค่ะ", "Grandmother is very kind."),
            Vocabulary(92, "คุณตา", "Grandfather (maternal)", "Khun ta", "Family", "คุณตามักจะอ่านหนังสือเล่มนี้", "Grandfather usually reads this book."),
            Vocabulary(93, "แฟน", "Boyfriend / Girlfriend / Spouse", "Fan", "Family", "แฟนของฉันทำงานเก่งมาก", "My special someone works very hard."),
            Vocabulary(94, "คน", "Person / Classifier for people", "Khon", "Family", "ในบ้านมีคนห้าคน", "There are five people in the house."),
            Vocabulary(95, "มี", "Have", "Mee", "Family", "พ่อมีรถยนต์สีแดงหนึ่งคัน", "Father has one red car."),
            Vocabulary(96, "ดี", "Good", "Dee", "Family", "แม่ทำอาหารรสชาติดี", "Mother cooks good-tasty food."),
            Vocabulary(97, "ชอบ", "Like", "Chop", "Family", "ฉันชอบกินข้าวผัดปู", "I like to eat crab fried rice."),
            Vocabulary(98, "ไม่", "Not", "Mai", "Family", "ฉันไม่ชอบคนโกหก", "I do not like liars."),
            Vocabulary(99, "เด็ก", "Child / Kid", "Dek", "Family", "เด็กคนนั้นกำลังร้องไห้", "That child is crying."),
            Vocabulary(100, "และ", "And", "Lae", "Family", "รักแม่และพ่อมากๆ นะ", "Love mother and father very much.")
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
                1 -> vocabulary.filter { it.id in 1..10 }
                2 -> vocabulary.filter { it.id in 11..20 }
                3 -> vocabulary.filter { it.id in 21..30 }
                4 -> vocabulary.filter { it.id in 31..40 }
                5 -> vocabulary.filter { it.id in 41..50 }
                6 -> vocabulary.filter { it.id in 51..60 }
                7 -> vocabulary.filter { it.id in 61..70 }
                8 -> vocabulary.filter { it.id in 71..80 }
                9 -> vocabulary.filter { it.id in 81..90 }
                10 -> vocabulary.filter { it.id in 91..100 }
                else -> emptyList()
            }
            
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

            // 5. First English -> Thai Sentence Building Exercise (SENTENCE_BUILD)
            val sentenceEnToTh1 = when (lessonId) {
                1 -> Triple("Hello, nice to meet you.", "สวัสดี|ยินดีที่ได้รู้จัก", listOf("ขอโทษ", "ขอบคุณ", "ไม่ใช่", "โชคดี"))
                2 -> Triple("Goodbye, see you again.", "ลาก่อน|แล้วพบกันใหม่", listOf("สวัสดี", "ยินดีเสมอกับคุณครับ", "ใช่", "ผมชื่อ"))
                3 -> Triple("Delicious food.", "อาหาร|อร่อย", listOf("น้ำ", "ข้าว", "ผลไม้", "ขอโทษ"))
                4 -> Triple("I eat spicy shrimp soup.", "ฉัน|กิน|ต้มยำกุ้ง", listOf("กาแฟ", "หวาน", "ข้าว", "น้ำ"))
                5 -> Triple("Three Baht.", "สาม|บาท", listOf("ห้า", "ราคา", "สิบ", "หนึ่ง"))
                6 -> Triple("How much is the price?", "ราคา|เท่าไหร่", listOf("เงิน", "บาท", "แพง", "ถูก"))
                7 -> Triple("Where is the restroom?", "ห้องน้ำ|ที่ไหน", listOf("โรงแรม", "แผนที่", "สนามบิน", "ไป"))
                8 -> Triple("Go straight to the temple.", "ตรงไป|วัด", listOf("รถไฟ", "เลี้ยวซ้าย", "บ้าน", "ตั๋ว"))
                9 -> Triple("I love older sister.", "ฉัน|รัก|พี่สาว", listOf("น้องชาย", "ครอบครัว", "พ่อ", "เพื่อน"))
                10 -> Triple("Mother and father are good.", "แม่|และ|พ่อ|ดี", listOf("รัก", "คุณตา", "มี", "ไม่"))
                else -> Triple("Hello, thank you.", "สวัสดี|ขอบคุณ", listOf("ไม่ใช่", "ใช่", "สบายดี", "ยินดี"))
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

            // 6. Second English -> Thai Sentence Building Exercise (SENTENCE_BUILD)
            val sentenceEnToTh2 = when (lessonId) {
                1 -> Triple("Thank you, friend.", "ขอบคุณ|เพื่อน", listOf("ยินดี", "สวัสดี", "โชคดี", "ขอโทษ"))
                2 -> Triple("Good night, you.", "ราตรีสวัสดิ์|คุณ", listOf("ขอบคุณ", "สบายดี", "ผม", "ยินดีด้วย"))
                3 -> Triple("Drink water.", "ดื่ม|น้ำ", listOf("ข้าว", "กิน", "ส้มตำ", "ชา"))
                4 -> Triple("Hungry for chicken.", "หิว|ไก่", listOf("ชา", "หมู", "ปลา", "ผลไม้"))
                5 -> Triple("Price five Baht.", "ราคา|ห้า|บาท", listOf("สี่", "สอง", "ร้อย", "แพง"))
                6 -> Triple("Buy nine shirts.", "ซื้อ|เสื้อ|เก้า", listOf("หก", "เจ็ด", "แปด", "ศูนย์"))
                7 -> Triple("Go to the hotel.", "ไป|โรงแรม", listOf("เลี้ยวขวา", "เลี้ยวซ้าย", "สถานี", "ห้องน้ำ"))
                8 -> Triple("Train is near.", "รถไฟ|ใกล้", listOf("ไกล", "ตั๋ว", "บ้าน", "ตรงไป"))
                9 -> Triple("Father and younger brother.", "พ่อ|และ|น้องชาย", listOf("พี่ชาย", "แม่", "ลูก", "เพื่อน"))
                10 -> Triple("Have five children.", "มี|เด็ก|ห้า|คน", listOf("ไม่", "ดี", "ชอบ", "คุณยาย"))
                else -> Triple("Hello, thank you.", "สวัสดี|ขอบคุณ", listOf("ไม่ใช่", "ใช่", "สบายดี", "ยินดี"))
            }

            val enToThCorrect2 = sentenceEnToTh2.second
            val enToThCorrectList2 = enToThCorrect2.split("|")
            val enToThOptions2 = (enToThCorrectList2 + sentenceEnToTh2.third).shuffled()
            list.add(Exercise(
                id = lessonId * 100 + 6,
                lessonId = lessonId,
                type = ExerciseType.SENTENCE_BUILD,
                prompt = "Assemble the Thai words that translate this sentence:",
                question = sentenceEnToTh2.first,
                correctAnswer = enToThCorrect2,
                romanization = "",
                options = enToThOptions2,
                audioText = enToThCorrect2.replace("|", " ")
            ))

            // 7. First Thai -> English Sentence Building Exercise (SENTENCE_BUILD)
            val sentenceThToEn1 = when (lessonId) {
                1 -> Triple("ใช่ สบายดี", "Yes|I am fine", listOf("Hello", "Sorry", "No", "Goodbye"))
                2 -> Triple("ผม สบายดี ขอบคุณ", "I (male)|I am fine|Thank you", listOf("Yes", "Goodbye", "Not correct", "You"))
                3 -> Triple("กิน ข้าว", "Eat|Rice", listOf("Water", "Coffee", "Thank you", "Delicious"))
                4 -> Triple("ดื่ม กาแฟ", "Drink|Coffee", listOf("Eat", "Pork", "Fish", "Rice"))
                5 -> Triple("แกงเขียวหวาน แพง", "Green curry|Expensive", listOf("Delicious", "Cheap / Correct", "Egg", "Rice"))
                6 -> Triple("ซื้อ ไข่ ห้า", "Buy|Egg|Five", listOf("Eat", "Money", "Three", "Pork"))
                7 -> Triple("ไป สนามบิน", "Go|Airport", listOf("Turn left", "Restroom", "Hotel", "Tuk-Tuk"))
                8 -> Triple("โรงแรม ใกล้", "Hotel|Near / Close", listOf("Far", "Station", "Airport", "Map"))
                9 -> Triple("พ่อ รัก ลูก", "Father|Love|Child / Offspring", listOf("Mother", "Friend", "Older brother", "Near / Close"))
                10 -> Triple("ฉัน ชอบ ครอบครัว", "I (female)|Like|Family", listOf("Love", "Not", "Good", "Friend"))
                else -> Triple("ใช่ ไม่ใช่", "Yes|No / Not correct", listOf("Hello", "Goodbye", "Thank you", "Sorry"))
            }

            val thToEnCorrect1 = sentenceThToEn1.second
            val thToEnCorrectList1 = thToEnCorrect1.split("|")
            val thToEnOptions1 = (thToEnCorrectList1 + sentenceThToEn1.third).shuffled()
            list.add(Exercise(
                id = lessonId * 100 + 7,
                lessonId = lessonId,
                type = ExerciseType.SENTENCE_BUILD,
                prompt = "Translate this Thai sentence into English:",
                question = sentenceThToEn1.first,
                correctAnswer = thToEnCorrect1,
                romanization = "",
                options = thToEnOptions1,
                audioText = ""
            ))

            // 8. Second Thai -> English Sentence Building Exercise (SENTENCE_BUILD)
            val sentenceThToEn2 = when (lessonId) {
                1 -> Triple("สบายดีไหม ขอบคุณ", "How are you?|Thank you", listOf("Sorry", "Yes", "Goodbye", "Hello"))
                2 -> Triple("ยินดีด้วย แฟน", "Congratulations|Boyfriend / Girlfriend / Spouse", listOf("Good night", "How are you?", "Glad", "See you again"))
                3 -> Triple("กาแฟ อร่อย", "Coffee|Delicious", listOf("Water", "Spicy shrimp soup", "Stir-fried noodles", "Papaya salad"))
                4 -> Triple("กิน ไข่ อร่อย", "Eat|Egg|Delicious", listOf("Pork", "Green curry", "Chicken", "Sweet"))
                5 -> Triple("ราคา สิบ บาท", "Price|Ten|Baht", listOf("One", "Two", "Five", "Hundred"))
                6 -> Triple("เสื้อ ราคา ถูก", "Shirt|Price|Cheap / Correct", listOf("Buy", "Nine", "Six", "Eight"))
                7 -> Triple("เลี้ยวซ้าย ไป สถานี", "Turn left|Go|Station", listOf("Turn right", "Hotel", "Restroom", "Airport"))
                8 -> Triple("บ้าน อยู่ ไกล", "House / Home|Have|Far", listOf("Near / Close", "Ticket", "Train", "Turn"))
                9 -> Triple("พี่สาว มี เพื่อน", "Older sister|Have|Friend", listOf("Older brother", "Younger sister", "Family", "Love"))
                10 -> Triple("คุณยาย และ คุณตา", "Grandmother (maternal)|And|Grandfather (maternal)", listOf("Child / Kid", "Good", "Not", "Have"))
                else -> Triple("ใช่ ไม่ใช่", "Yes|No / Not correct", listOf("Hello", "Goodbye", "Thank you", "Sorry"))
            }

            val thToEnCorrect2 = sentenceThToEn2.second
            val thToEnCorrectList2 = thToEnCorrect2.split("|")
            val thToEnOptions2 = (thToEnCorrectList2 + sentenceThToEn2.third).shuffled()
            list.add(Exercise(
                id = lessonId * 100 + 8,
                lessonId = lessonId,
                type = ExerciseType.SENTENCE_BUILD,
                prompt = "Translate this Thai sentence into English:",
                question = sentenceThToEn2.first,
                correctAnswer = thToEnCorrect2,
                romanization = "",
                options = thToEnOptions2,
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
