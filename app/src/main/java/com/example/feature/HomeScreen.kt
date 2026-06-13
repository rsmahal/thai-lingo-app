package com.example.feature

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    progress: UserProgress,
    lessons: List<Lesson>,
    achievements: List<Achievement>,
    onStartLesson: (Int) -> Unit
) {
    var selectedLessonForSheet by remember { mutableStateOf<Lesson?>(null) }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // DUOLINGO-STYLE HEADER WITH TOP BAR INSETS
            HomeHeader(progress = progress)

            // ROADMAP OF CURRICULUM
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("lessons_list"),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Section wise grouping
                val groupedByCategory = lessons.groupBy { it.category }
                
                groupedByCategory.forEach { (category, lessonsInCat) ->
                    // CATEGORY BANNER CRADLE
                    item {
                        CategoryBanner(category = category)
                    }

                    // LESSON NODES CASCADE
                    items(lessonsInCat) { lesson ->
                        LessonBadgeNode(
                            lesson = lesson,
                            onClick = {
                                if (lesson.unlocked) {
                                    selectedLessonForSheet = lesson
                                }
                            }
                        )
                    }
                    
                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                // ACHIEVEMENTS TIER
                item {
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f), thickness = 2.dp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "🏆 Achievement Badges",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                items(achievements) { achievement ->
                    AchievementRowCard(achievement = achievement)
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
                        selectedLessonForSheet = null
                        onStartLesson(lesson.id)
                    },
                    onDismiss = { selectedLessonForSheet = null }
                )
            }
        }
    }
}

@Composable
fun HomeHeader(progress: UserProgress) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = 6.dp,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Level Badge
            HeaderStatItem(
                icon = Icons.Default.Stars,
                text = "Lvl ${progress.level}",
                color = LevelGold,
                contentDescription = "User level"
            )

            // Streak Fire
            HeaderStatItem(
                icon = Icons.Default.LocalFireDepartment,
                text = "${progress.streak} days",
                color = StreakOrange,
                contentDescription = "Active streak"
            )

            // XP Star
            HeaderStatItem(
                icon = Icons.Default.WorkspacePremium,
                text = "${progress.xp} XP",
                color = GemCyan,
                contentDescription = "Total experience points"
            )

            // Hearts Left
            HeaderStatItem(
                icon = Icons.Default.Favorite,
                text = if (progress.hearts == 0) "Empty" else "${progress.hearts}",
                color = HeartRed,
                contentDescription = "Hearts remaining"
            )
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
fun CategoryBanner(category: String) {
    val (color, label) = when (category) {
        "Greetings" -> Pair(DuoGreen, "Greetings & Manners")
        "Food" -> Pair(StreakOrange, "Thai Food & Cooking")
        "Numbers" -> Pair(LevelGold, "Counting & Market Shop")
        "Travel" -> Pair(GemCyan, "Directions & Travel")
        "Family" -> Pair(HeartRed, "Family & Relatives")
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
                    imageVector = when (category) {
                        "Greetings" -> Icons.Default.Translate
                        "Food" -> Icons.Default.Restaurant
                        "Numbers" -> Icons.Default.AttachMoney
                        "Travel" -> Icons.Default.DirectionsTransit
                        "Family" -> Icons.Default.FamilyRestroom
                        else -> Icons.Default.School
                    },
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column {
                Text(
                    text = label.uppercase(),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    color = color
                )
                Text(
                    text = "Unlock topics and test your skills offline!",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
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
                .background(DuoGreen, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.LocalLibrary,
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
            text = lesson.description,
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
                    Text("REWARD", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("+${lesson.xpReward} XP", fontSize = 18.sp, fontWeight = FontWeight.Black, color = GemCyan)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("100% Offline", fontSize = 18.sp, fontWeight = FontWeight.Black, color = DuoGreen)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("STEPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("5 Challenges", fontSize = 18.sp, fontWeight = FontWeight.Black, color = LevelGold)
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
            colors = ButtonDefaults.buttonColors(containerColor = DuoGreen)
        ) {
            Text("START LESSON", fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White)
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
fun AchievementRowCard(achievement: Achievement) {
    val progressFraction = achievement.progress.toFloat() / achievement.target.toFloat()
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("achievement_card_${achievement.id}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked) LevelGold.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = CardDefaults.outlinedCardBorder(enabled = true).copy(
            width = 1.dp,
            brush = androidx.compose.ui.graphics.SolidColor(
                if (achievement.isUnlocked) LevelGold.copy(alpha = 0.4f) else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Icon Badge
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        if (achievement.isUnlocked) LevelGold else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (achievement.iconName) {
                        "streak" -> Icons.Default.LocalFireDepartment
                        "xp" -> Icons.Default.WorkspacePremium
                        else -> Icons.Default.School
                    },
                    contentDescription = null,
                    tint = if (achievement.isUnlocked) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp)
                )
            }

            // Description and slider
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = achievement.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (achievement.isUnlocked) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Unlocked",
                            tint = DuoGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                
                Text(
                    text = achievement.description,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LinearProgressIndicator(
                        progress = { progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp)),
                        color = if (achievement.isUnlocked) LevelGold else DuoGreen,
                        trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                    )
                    Text(
                        text = "${achievement.progress}/${achievement.target}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}
