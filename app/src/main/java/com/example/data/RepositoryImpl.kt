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
    private val achievementDao: AchievementDao
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
            
            Lesson(3, "Thai Food Staples", "Master khao, nam, and food nouns.", "Food", unlocked = false, completed = false, stars = 0),
            Lesson(4, "Famous Dishes & Tastes", "Learn Som Tam, Pad Thai, spicy, and hungry.", "Food", unlocked = false, completed = false, stars = 0),
            
            Lesson(5, "Counting 1 to 5", "Build numbers and count objects.", "Numbers", unlocked = false, completed = false, stars = 0),
            Lesson(6, "Money & Shopping", "Master Baht, cheap, expensive, and asking the price.", "Numbers", unlocked = false, completed = false, stars = 0),
            
            Lesson(7, "Directions & Transit", "Ask where landmarks are, learn left and right.", "Travel", unlocked = false, completed = false, stars = 0),
            Lesson(8, "Transit & Tuk-Tuk", "Order taxis, ride tuk-tuks, and read maps.", "Travel", unlocked = false, completed = false, stars = 0),
            
            Lesson(9, "Parents & Core Family", "Talk about mother, father, and family.", "Family", unlocked = false, completed = false, stars = 0),
            Lesson(10, "Siblings & Love", "Discuss brothers, sisters, and sharing love.", "Family", unlocked = false, completed = false, stars = 0)
        )
    }

    private fun getSampleExercises(): List<Exercise> {
        val list = mutableListOf<Exercise>()
        
        // --- LESSON 1 EXERCISES (Greetings 101) ---
        list.add(Exercise(101, 1, ExerciseType.MULTIPLE_CHOICE, "How do you say 'Hello / Goodbye' in Thai?", "สวัสดี", "Hello / Goodbye", "Sawatdee", listOf("Hello / Goodbye", "Thank you", "Delicious", "Water"), "สวัสดี"))
        list.add(Exercise(102, 1, ExerciseType.TRANSLATE, "Translate: สบายดี", "สบายดี", "I am fine", "Sabai dee", emptyList(), "สบายดี"))
        list.add(Exercise(103, 1, ExerciseType.LISTENING, "Listen and select the correct translation:", "ขอบคุณ", "Thank you", "Khop khun", listOf("Thank you", "Excuse me", "Hello / Goodbye", "Yes"), "ขอบคุณ"))
        list.add(Exercise(104, 1, ExerciseType.SPEAKING, "Speak this phrase clearly into your microphone:", "สบายดีไหม", "สบายดีไหม", "Sabai dee mai", emptyList(), "สบายดีไหม"))
        list.add(Exercise(105, 1, ExerciseType.MATCHING, "Match matching English and Thai pairs:", "Match vocabulary", "สวัสดี=Hello / Goodbye|ขอบคุณ=Thank you|สบายดี=I am fine|สบายดีไหม=How are you?", "", listOf("สวัสดี", "ขอบคุณ", "สบายดี", "สบายดีไหม", "Hello / Goodbye", "Thank you", "I am fine", "How are you?"), ""))

        // --- LESSON 2 EXERCISES (More Greetings) ---
        list.add(Exercise(201, 2, ExerciseType.MULTIPLE_CHOICE, "What does 'ขอโทษ' mean?", "ขอโทษ", "Sorry / Excuse me", "Kho thot", listOf("Sorry / Excuse me", "Thank you", "Yes", "Goodbye"), "ขอโทษ"))
        list.add(Exercise(202, 2, ExerciseType.TRANSLATE, "Translate: ใช่", "ใช่", "Yes", "Chai", emptyList(), "ใช่"))
        list.add(Exercise(203, 2, ExerciseType.LISTENING, "Listen and select the correct words", "ลาก่อน", "Goodbye", "La kon", listOf("Goodbye", "Hello / Goodbye", "No / Not correct", "Good luck"), "ลาก่อน"))
        list.add(Exercise(204, 2, ExerciseType.SPEAKING, "Pronounce: โชคดี (Chok dee)", "โชคดี", "โชคดี", "Chok dee", emptyList(), "โชคดี"))
        list.add(Exercise(205, 2, ExerciseType.MATCHING, "Match the greetings", "Match", "ขอโทษ=Sorry / Excuse me|ใช่=Yes|ไม่ใช่=No / Not correct|ลาก่อน=Goodbye", "", listOf("ขอโทษ", "ใช่", "ไม่ใช่", "ลาก่อน", "Sorry / Excuse me", "Yes", "No / Not correct", "Goodbye"), ""))

        // --- LESSON 3 EXERCISES (Thai Food Staples) ---
        list.add(Exercise(301, 3, ExerciseType.MULTIPLE_CHOICE, "What is 'ข้าว' in English?", "ข้าว", "Rice", "Khao", listOf("Rice", "Water", "Food", "Spicy"), "ข้าว"))
        list.add(Exercise(302, 3, ExerciseType.TRANSLATE, "Translate: น้ำ", "น้ำ", "Water", "Nam", emptyList(), "น้ำ"))
        list.add(Exercise(303, 3, ExerciseType.LISTENING, "Listen and select the correct word", "อาหาร", "Food", "Ahan", listOf("Food", "Rice", "Water", "Delicious"), "อาหาร"))
        list.add(Exercise(304, 3, ExerciseType.SPEAKING, "Speak: กินข้าว", "กินข้าว", "กินข้าว", "Kin khao", emptyList(), "กินข้าว"))
        list.add(Exercise(305, 3, ExerciseType.MATCHING, "Match words", "Match", "ข้าว=Rice|น้ำ=Water|อาหาร=Food|กิน=Eat", "", listOf("ข้าว", "น้ำ", "อาหาร", "กิน", "Rice", "Water", "Food", "Eat"), ""))

        // --- LESSON 4 EXERCISES (Famous Dishes) ---
        list.add(Exercise(401, 4, ExerciseType.MULTIPLE_CHOICE, "Select 'Stir-fried noodles' (Pad Thai):", "ผัดไทย", "Stir-fried noodles", "Pad Thai", listOf("Stir-fried noodles", "Spicy shrimp soup", "Papaya salad", "Fruit"), "ผัดไทย"))
        list.add(Exercise(402, 4, ExerciseType.TRANSLATE, "Translate: อร่อย", "อร่อย", "Delicious", "Aroy", emptyList(), "อร่อย"))
        list.add(Exercise(403, 4, ExerciseType.LISTENING, "Select what you hear:", "ส้มตำ", "Papaya salad", "Som tam", listOf("Papaya salad", "Fruit", "Coffee", "Spicy"), "ส้มตำ"))
        list.add(Exercise(404, 4, ExerciseType.SPEAKING, "Speak: เผ็ดมาก", "เผ็ดมาก", "เผ็ดมาก", "Phet mak", emptyList(), "เผ็ดมาก"))
        list.add(Exercise(405, 4, ExerciseType.MATCHING, "Match elements", "Match", "ผลไม้=Fruit|กาแฟ=Coffee|อร่อย=Delicious|หิว=Hungry", "", listOf("ผลไม้", "กาแฟ", "อร่อย", "หิว", "Fruit", "Coffee", "Delicious", "Hungry"), ""))

        // --- LESSON 5 EXERCISES (Numbers 1 to 5) ---
        list.add(Exercise(501, 5, ExerciseType.MULTIPLE_CHOICE, "What number is 'หนึ่ง'?", "หนึ่ง", "One", "Nung", listOf("One", "Two", "Three", "Five"), "หนึ่ง"))
        list.add(Exercise(502, 5, ExerciseType.TRANSLATE, "Translate: สาม", "สาม", "Three", "Sam", emptyList(), "สาม"))
        list.add(Exercise(503, 5, ExerciseType.LISTENING, "Listen and select:", "ห้า", "Five", "Ha", listOf("Five", "Four", "Two", "One"), "ห้า"))
        list.add(Exercise(504, 5, ExerciseType.SPEAKING, "Speak 'สอง' (Song)", "สอง", "สอง", "Song", emptyList(), "สอง"))
        list.add(Exercise(505, 5, ExerciseType.MATCHING, "Match numbers 1-5", "Match", "หนึ่ง=One|สอง=Two|สาม=Three|สี่=Four|ห้า=Five", "", listOf("หนึ่ง", "สอง", "สาม", "สี่", "หนึ่ง", "One", "Two", "Three", "Four", "Five"), ""))

        // --- FILL GENERATED EXERCISES FOR REMAINING LESSONS (6 to 10) SO WE HAVE FULL COVERAGE ---
        for (lessonId in 6..10) {
            val offset = lessonId * 100
            val vocabList = getSampleVocabulary().filter {
                when (lessonId) {
                    6 -> it.category == "Numbers"
                    7 -> it.category == "Travel"
                    8 -> it.category == "Travel"
                    9 -> it.category == "Family"
                    10 -> it.category == "Family"
                    else -> true
                }
            }
            val v1 = vocabList.getOrElse(0) { getSampleVocabulary()[0] }
            val v2 = vocabList.getOrElse(1) { getSampleVocabulary()[1] }
            val v3 = vocabList.getOrElse(2) { getSampleVocabulary()[2] }
            val v4 = vocabList.getOrElse(3) { getSampleVocabulary()[3] }

            list.add(Exercise(offset + 1, lessonId, ExerciseType.MULTIPLE_CHOICE, "Select English meaning of '${v1.thai}':", v1.thai, v1.english, v1.romanization, listOf(v1.english, v2.english, "Airplane", "House"), v1.thai))
            list.add(Exercise(offset + 2, lessonId, ExerciseType.TRANSLATE, "Translate into English:", v2.thai, v2.english, v2.romanization, emptyList(), v2.thai))
            list.add(Exercise(offset + 3, lessonId, ExerciseType.LISTENING, "Listen and select:", v3.thai, v3.english, v3.romanization, listOf(v3.english, v4.english, "Friend", "Work"), v3.thai))
            list.add(Exercise(offset + 4, lessonId, ExerciseType.SPEAKING, "Speak: " + v4.thai, v4.thai, v4.thai, v4.romanization, emptyList(), v4.thai))
            list.add(Exercise(offset + 5, lessonId, ExerciseType.MATCHING, "Match the pairs:", "Match", "${v1.thai}=${v1.english}|${v2.thai}=${v2.english}|${v3.thai}=${v3.english}|${v4.thai}=${v4.english}", "", listOf(v1.thai, v2.thai, v3.thai, v4.thai, v1.english, v2.english, v3.english, v4.english), ""))
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
