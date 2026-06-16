package com.example.feature

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.Achievement
import com.example.domain.Lesson
import com.example.domain.UserProgress
import com.example.ui.theme.*
import com.example.ui.ThaiRank
import com.example.ui.RankBadge

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    progress: UserProgress,
    lessons: List<Lesson>,
    onStartLesson: (Int) -> Unit
) {
    var selectedLessonForSheet by remember { mutableStateOf<Lesson?>(null) }
    var lessonToConfirmReset by remember { mutableStateOf<Lesson?>(null) }

    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()

    val scrollToIndex = remember(lessons) {
        if (lessons.isEmpty()) return@remember 0
        
        val groupedByCategory = lessons.groupBy { it.category }
        var currentIndex = 1 // starts after greeting card item (index 0)
        
        for (entry in groupedByCategory.entries) {
            val category = entry.key
            val lessonsInCat = entry.value
            val standardLessons = lessonsInCat.filter { it.id < 100 }
            val sentenceLesson = lessonsInCat.find { it.id in 501..600 }
            val testLesson = lessonsInCat.find { it.id in 101..499 }
            
            // Check if there is any standard lesson unlocked but not completed
            val incompleteStandardIdx = standardLessons.indexOfFirst { it.unlocked && !it.completed }
            if (incompleteStandardIdx != -1) {
                return@remember currentIndex + 1 + incompleteStandardIdx
            }
            
            if (sentenceLesson != null && sentenceLesson.unlocked && !sentenceLesson.completed) {
                return@remember currentIndex + 1 + standardLessons.size
            }
            
            if (testLesson != null && testLesson.unlocked && !testLesson.completed) {
                return@remember currentIndex + 1 + standardLessons.size + (if (sentenceLesson != null) 1 else 0)
            }
            
            currentIndex += 1 // Category banner (stickyHeader)
            currentIndex += standardLessons.size // Items
            if (sentenceLesson != null) {
                currentIndex += 1 // Sentence lesson item
            }
            if (testLesson != null) {
                currentIndex += 1 // Topic test item
            }
            currentIndex += 1 // Spacer item
        }
        0
    }

    LaunchedEffect(scrollToIndex) {
        if (scrollToIndex > 0) {
            lazyListState.scrollToItem(scrollToIndex)
        }
    }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // DUOLINGO-STYLE HEADER WITH TOP BAR INSETS
            HomeHeader(progress = progress, lessons = lessons)
 
            // ROADMAP OF CURRICULUM
            LazyColumn(
                state = lazyListState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("lessons_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Welcoming study progress greeting card
                item {
                    val streakMessage = if (progress.streak <= 1) {
                        "Sawatdee ka! Welcome back! Ready to continue your journey? Let's start today's practice and build your daily study habits! ✨"
                    } else {
                        "Keep the momentum! You've maintained a 🔥 ${progress.streak}-day study streak. Continue practicing to master more Thai sentences!"
                    }
                    
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .testTag("home_study_greeting_card")
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (progress.streak > 1) Icons.Default.Whatshot else Icons.Default.Star,
                                contentDescription = null,
                                tint = if (progress.streak > 1) StreakOrange else DuoGreen,
                                modifier = Modifier
                                    .size(36.dp)
                                    .background(
                                        color = (if (progress.streak > 1) StreakOrange else DuoGreen).copy(alpha = 0.1f),
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(6.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = if (progress.streak > 1) "Daily Streak Active" else "Welcome Back",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = streakMessage,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }
                }

                // Section wise grouping
                val groupedByCategory = lessons.groupBy { it.category }
                
                groupedByCategory.forEach { (category, lessonsInCat) ->
                    val standardLessons = lessonsInCat.filter { it.id < 100 }
                    val sentenceLesson = lessonsInCat.find { it.id in 501..600 }
                    val testLesson = lessonsInCat.find { it.id in 101..499 }
                    val totalCount = standardLessons.size
                    val completedCount = standardLessons.count { it.completed }

                    // CATEGORY BANNER CRADLE (Sticky header blocks lessons below during scroll)
                    stickyHeader {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background)
                        ) {
                            CategoryBanner(
                                category = category,
                                completedCount = completedCount,
                                totalCount = totalCount
                            )
                        }
                    }

                    // LESSON NODES CASCADE
                    items(standardLessons) { lesson ->
                        LessonBadgeNode(
                            lesson = lesson,
                            onClick = {
                                if (lesson.unlocked) {
                                    selectedLessonForSheet = lesson
                                }
                            }
                        )
                    }

                    // Show Sentence Lesson Node (if present)
                    if (sentenceLesson != null) {
                        item {
                            SentenceBadgeNode(
                                lesson = sentenceLesson,
                                onClick = {
                                    if (sentenceLesson.unlocked) {
                                        selectedLessonForSheet = sentenceLesson
                                    }
                                }
                            )
                        }
                    }
                    
                    // Show Topic Test Node (always visible, disabled until available defined by lesson.unlocked)
                    if (testLesson != null) {
                        item {
                            TopicTestBadgeNode(
                                lesson = testLesson,
                                onClick = {
                                    selectedLessonForSheet = testLesson
                                }
                            )
                        }
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(36.dp))
                }
            }
        }

        // LESSON DETAIL BOTTOM SHEET DIALOG
        selectedLessonForSheet?.let { lesson ->
            ModalBottomSheet(
                onDismissRequest = { selectedLessonForSheet = null },
                containerColor = MaterialTheme.colorScheme.surface,
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
            ) {
                LessonStartDetailsSheetContent(
                    lesson = lesson,
                    onStart = {
                        if (lesson.stars == 3) {
                            lessonToConfirmReset = lesson
                        } else {
                            selectedLessonForSheet = null
                            onStartLesson(lesson.id)
                        }
                    },
                    onDismiss = { selectedLessonForSheet = null }
                )
            }
        }

        lessonToConfirmReset?.let { lesson ->
            AlertDialog(
                onDismissRequest = { lessonToConfirmReset = null },
                title = { Text("Reset Progress?", fontWeight = FontWeight.Bold) },
                text = { Text("Your progress for this lesson will be reset. Are you sure you want to continue?") },
                confirmButton = {
                    Button(
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        onClick = {
                            lessonToConfirmReset = null
                            selectedLessonForSheet = null
                            onStartLesson(lesson.id)
                        }
                    ) {
                        Text("Continue", fontWeight = FontWeight.Bold, color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { lessonToConfirmReset = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun HomeHeader(
    progress: UserProgress,
    lessons: List<Lesson>
) {
    var showLevelDialog by remember { mutableStateOf(false) }
    val totalStars = lessons.sumOf { it.stars }
    val currentRank = ThaiRank.fromStars(totalStars)
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val getRankColor: (ThaiRank) -> Color = { rank ->
        if (isDark) {
            when (rank) {
                ThaiRank.IRON -> Color(0xFFD1D5DB) // Bright Slate Silver
                ThaiRank.BRONZE -> Color(0xFFFDBA74) // Luminous Copper Peach
                ThaiRank.SILVER -> Color(0xFF99F6E4) // Radiant Lotus Teal
                ThaiRank.GOLD -> Color(0xFFFDE047) // Glowing Gold
                ThaiRank.EMERALD -> Color(0xFF86EFAC) // Bright Emerald Mint
            }
        } else {
            rank.secColor
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // League of Legends styled dynamic Thai rank badge
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(currentRank.primaryColor.copy(alpha = 0.12f))
                        .border(1.dp, currentRank.primaryColor.copy(alpha = 0.40f), RoundedCornerShape(8.dp))
                        .clickable { showLevelDialog = true }
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RankBadge(
                        rank = currentRank,
                        modifier = Modifier.size(20.dp),
                        showBackgroundFrame = false
                    )
                    Text(
                        text = currentRank.title,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        color = getRankColor(currentRank)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Rank details dropdown",
                        tint = getRankColor(currentRank),
                        modifier = Modifier.size(12.dp)
                    )
                }

                // Total Stars
                HeaderStatItem(
                    icon = Icons.Default.Star,
                    text = "$totalStars Stars",
                    color = LevelGold,
                    contentDescription = "Total Stars"
                )

                // Streak Fire
                HeaderStatItem(
                    icon = Icons.Default.LocalFireDepartment,
                    text = "${progress.streak} d",
                    color = StreakOrange,
                    contentDescription = "Active streak"
                )


            }

            if (showLevelDialog) {
                AlertDialog(
                    onDismissRequest = { showLevelDialog = false },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            RankBadge(
                                rank = currentRank,
                                modifier = Modifier.size(38.dp),
                                showBackgroundFrame = true
                            )
                            Column {
                                Text(
                                    text = "Thai Rank Tier",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Current Rank: ${currentRank.title}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = getRankColor(currentRank)
                                )
                            }
                        }
                    },
                    text = {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "Earn Stars to ascend through ancient Thai ranks and unlock custom symbolic badges of heritage. Redo completed lessons as many times as you like to max out your stars!",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                            
                            val ranks = ThaiRank.values()
                            
                            ranks.forEach { r ->
                                val isMyRank = currentRank == r
                                val isUnlocked = totalStars >= r.minStars
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isMyRank) {
                                            r.primaryColor.copy(alpha = 0.15f)
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                        }
                                    ),
                                    border = if (isMyRank) {
                                        BorderStroke(2.dp, r.primaryColor)
                                    } else if (isUnlocked) {
                                        BorderStroke(1.dp, r.primaryColor.copy(alpha = 0.4f))
                                    } else {
                                        BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                                    ) {
                                        RankBadge(
                                            rank = r,
                                            modifier = Modifier.size(44.dp),
                                            showBackgroundFrame = true
                                        )
                                        Column(modifier = Modifier.weight(1f)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(
                                                    text = "${r.title} (${r.badgeSymbol})",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = if (isUnlocked) getRankColor(r) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                )
                                                Text(
                                                    text = r.starRange,
                                                    fontWeight = FontWeight.Medium,
                                                    fontSize = 11.sp,
                                                    color = if (isUnlocked) r.primaryColor else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                )
                                            }
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                text = r.description,
                                                fontSize = 10.5.sp,
                                                lineHeight = 14.sp,
                                                color = if (isUnlocked) {
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                } else {
                                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                                                }
                                            )
                                        }
                                        if (isMyRank) {
                                            Icon(
                                                imageVector = Icons.Default.CheckCircle,
                                                contentDescription = "Active Rank",
                                                tint = getRankColor(r),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        } else if (!isUnlocked) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = "Locked Rank",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showLevelDialog = false }) {
                            Text("Awesome", fontWeight = FontWeight.Bold, color = DuoGreenDark)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun HeaderStatItem(
    icon: ImageVector,
    text: String,
    color: Color,
    contentDescription: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = color,
            modifier = Modifier.size(24.dp)
        )
        Text(
            text = text,
            fontWeight = FontWeight.Black,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun CategoryBanner(category: String, completedCount: Int, totalCount: Int) {
    val (color, label) = when {
        category.contains("Greetings", ignoreCase = true) || category.contains("Politeness", ignoreCase = true) -> Pair(DuoGreen, category)
        category.contains("Food", ignoreCase = true) || category.contains("Dishes", ignoreCase = true) || category.contains("Dine", ignoreCase = true) -> Pair(StreakOrange, category)
        category.contains("Numbers", ignoreCase = true) || category.contains("Money", ignoreCase = true) || category.contains("Bargaining", ignoreCase = true) || category.contains("Shopping", ignoreCase = true) -> Pair(LevelGold, category)
        category.contains("Transit", ignoreCase = true) || category.contains("Travel", ignoreCase = true) || category.contains("Tuk-Tuk", ignoreCase = true) || category.contains("Directions", ignoreCase = true) || category.contains("Sightseeing", ignoreCase = true) -> Pair(GemCyan, category)
        category.contains("Parents", ignoreCase = true) || category.contains("Relatives", ignoreCase = true) || category.contains("Siblings", ignoreCase = true) || category.contains("Family", ignoreCase = true) || category.contains("Connections", ignoreCase = true) -> Pair(HeartRed, category)
        else -> Pair(DuoGreen, category)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.15f)),
        border = CardDefaults.outlinedCardBorder(enabled = true).copy(
            width = 1.5.dp,
            brush = androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.5f))
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(color, shape = CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when {
                        category.contains("Greetings", ignoreCase = true) || category.contains("Politeness", ignoreCase = true) -> Icons.Default.Translate
                        category.contains("Food", ignoreCase = true) || category.contains("Dishes", ignoreCase = true) || category.contains("Dine", ignoreCase = true) -> Icons.Default.Restaurant
                        category.contains("Numbers", ignoreCase = true) || category.contains("Money", ignoreCase = true) || category.contains("Bargaining", ignoreCase = true) || category.contains("Shopping", ignoreCase = true) -> Icons.Default.AttachMoney
                        category.contains("Transit", ignoreCase = true) || category.contains("Travel", ignoreCase = true) || category.contains("Tuk-Tuk", ignoreCase = true) || category.contains("Directions", ignoreCase = true) || category.contains("Sightseeing", ignoreCase = true) -> Icons.Default.DirectionsTransit
                        category.contains("Parents", ignoreCase = true) || category.contains("Relatives", ignoreCase = true) || category.contains("Siblings", ignoreCase = true) || category.contains("Family", ignoreCase = true) || category.contains("Connections", ignoreCase = true) -> Icons.Default.FamilyRestroom
                        else -> Icons.Default.School
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$completedCount of $totalCount Lessons",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                    Text(
                        text = "${if (totalCount > 0) (completedCount * 100 / totalCount) else 0}%",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        color = color
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { if (totalCount > 0) completedCount.toFloat() / totalCount else 0F },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = color,
                    trackColor = color.copy(alpha = 0.15f)
                )
            }
        }
    }
}

@Composable
fun LessonBadgeNode(
    lesson: Lesson,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(enabled = lesson.unlocked, onClick = onClick)
            .testTag("lesson_node_${lesson.id}")
    ) {
        // Double wrap border to outline progress
        val borderBrush = when {
            lesson.completed -> DuoGreen
            lesson.unlocked -> GemCyan
            else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
        }

        Box(
            modifier = Modifier
                .size(76.dp)
                .clip(CircleShape)
                .background(
                    if (lesson.unlocked) borderBrush.copy(alpha = 0.1f) else Color.Transparent
                )
                .border(width = 4.dp, color = borderBrush, shape = CircleShape)
                .padding(6.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(if (lesson.unlocked) borderBrush else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                if (lesson.unlocked) {
                    Icon(
                        imageVector = if (lesson.completed) Icons.Default.Check else Icons.Default.PlayArrow,
                        contentDescription = if (lesson.completed) "Completed" else "Play lesson",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked topic",
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = lesson.title,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = if (lesson.unlocked) MaterialTheme.colorScheme.onBackground else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            textAlign = TextAlign.Center
        )
        
        if (lesson.unlocked && lesson.completed) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                repeat(3) { idx ->
                    Icon(
                        imageVector = Icons.Default.Star,
                        contentDescription = "Earned star",
                        tint = if (idx < lesson.stars) LevelGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun LessonStartDetailsSheetContent(
    lesson: Lesson,
    onStart: () -> Unit,
    onDismiss: () -> Unit
) {
    val isTest = lesson.id in 100..499
    val isSentence = lesson.id in 501..600
    val badgeColor = if (isTest) Color(0xFFC026D3) else if (isSentence) Color(0xFF8B5CF6) else DuoGreen
    val badgeIcon = if (isTest) Icons.Default.School else if (isSentence) Icons.Default.QuestionAnswer else Icons.Default.LocalLibrary

    val descriptionText = if (isTest) {
        if (lesson.completed) {
            "You have passed this Topic Test with 100% accuracy. You can always replay it to test your skills!"
        } else {
            "Ready for the ultimate challenge? This test compiles 20 randomized exercises from this topic. You must pass with 100% accuracy (no mistakes) to unlock the next topic."
        }
    } else if (isSentence) {
        "Challenge yourself with 9 randomized sentence exercises (3 English to Thai, 3 Thai to English, and 3 spoken Listening challenges) using vocabulary from this topic and previous topics!"
    } else {
        if (lesson.completed) "Replay this lesson to refresh your vocabulary! (Keep your highest score & continue earning Stars)" else lesson.description
    }

    val rightLabel = if (isTest) "REQUIRED" else if (isSentence) "PRACTICE" else "WORDS"

    val newWordsCount = if (lesson.id in 1..50) 10 else 0
    val rightValue = if (isTest) "100% ACCURACY" else if (isSentence) "9 SENTENCES" else "$newWordsCount Words"
    val questionCount = if (isTest) "20 Questions" else if (isSentence) "9 Questions" else "25 Questions"

    val btnText = if (isTest) {
        if (lesson.completed) "REPLAY TOPIC TEST" else "TAKE TOPIC TEST"
    } else if (isSentence) {
        if (lesson.completed) "PRACTICE SENTENCES" else "START CHALLENGE"
    } else {
        if (lesson.completed) "REPLAY LESSON" else "START LESSON"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(68.dp)
                .background(badgeColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = badgeIcon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(32.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = lesson.title,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = descriptionText,
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("CURRENT SCORE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        repeat(3) { index ->
                            val filled = index < lesson.stars
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = if (filled) LevelGold else Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(rightLabel, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(rightValue, fontSize = 16.sp, fontWeight = FontWeight.Black, color = LevelGold)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("QUESTIONS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(questionCount, fontSize = 16.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.secondary)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("start_lesson_btn"),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = badgeColor)
        ) {
            Text(btnText, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))

        TextButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Maybe Later", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
        }
    }
}

@Composable
fun TopicTestBadgeNode(
    lesson: Lesson,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = lesson.unlocked, onClick = onClick)
            .testTag("topic_test_node_${lesson.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val testColor = if (lesson.completed) DuoGreen else if (lesson.unlocked) Color(0xFFC026D3) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = testColor.copy(alpha = 0.08f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Circular Badge Icon
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(testColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (lesson.completed) Icons.Default.CheckCircle else if (lesson.unlocked) Icons.Default.School else Icons.Default.Lock,
                        contentDescription = "Topic Test Icon",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Text(
                    text = lesson.title,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = if (lesson.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = if (lesson.completed) "COMPLETED! ⭐⭐⭐" else "20 QUESTIONS • 100% CORRECT REQ",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = if (lesson.unlocked) testColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun SentenceBadgeNode(
    lesson: Lesson,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = lesson.unlocked, onClick = onClick)
            .testTag("sentence_node_${lesson.id}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val sentenceColor = if (lesson.completed) DuoGreen else if (lesson.unlocked) Color(0xFF8B5CF6) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        
        Card(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = sentenceColor.copy(alpha = 0.08f)
            ),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Circular Badge Icon with Speech / Translate bubbles
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .background(sentenceColor, shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (lesson.completed) Icons.Default.CheckCircle else if (lesson.unlocked) Icons.Default.QuestionAnswer else Icons.Default.Lock,
                        contentDescription = "Sentences Icon",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                
                Text(
                    text = lesson.title,
                    fontWeight = FontWeight.Black,
                    fontSize = 17.sp,
                    color = if (lesson.unlocked) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
                
                Text(
                    text = if (lesson.completed) "COMPLETED! ⭐⭐⭐" else "9 SENTENCE BUILD CHALLENGES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Black,
                    color = if (lesson.unlocked) sentenceColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
