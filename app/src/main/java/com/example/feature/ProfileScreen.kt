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
import androidx.compose.ui.platform.LocalContext
import android.content.Context
import android.content.ClipData
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.UserProgress
import com.example.domain.Achievement
import com.example.domain.Lesson
import com.example.ui.theme.*
import com.example.ui.ThaiRank
import com.example.ui.RankBadge

@Composable
fun ProfileScreen(
    progress: UserProgress,
    achievements: List<Achievement>,
    lessons: List<Lesson>,
    onToggleSound: (Boolean) -> Unit,
    onToggleDarkMode: (Boolean) -> Unit,
    onToggleRomanization: (Boolean) -> Unit,
    onResetProgress: () -> Unit,
    onExportProgress: suspend () -> String,
    onImportProgress: suspend (String) -> Boolean
) {
    var showResetDialog by remember { mutableStateOf(false) }
    var showImportConfirmDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val jsonString = onExportProgress()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray())
                    }
                    android.widget.Toast.makeText(context, "Progress exported successfully!", android.widget.Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Export failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val content = context.contentResolver.openInputStream(uri)?.use { inputStream ->
                        inputStream.bufferedReader().use { it.readText() }
                    }
                    if (content != null) {
                        val success = onImportProgress(content)
                        if (success) {
                            android.widget.Toast.makeText(context, "Progress imported successfully!", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(context, "Failed to import: Invalid backup file", android.widget.Toast.LENGTH_LONG).show()
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Failed to read file", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    android.widget.Toast.makeText(context, "Import failed: ${e.localizedMessage}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    if (showImportConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showImportConfirmDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = DuoGreenDark)
                    Text("Import Backup?", fontWeight = FontWeight.Black, fontSize = 20.sp, color = DuoGreenDark)
                }
            },
            text = {
                Text(
                    text = "You are about to load your progress from a backup file. Your current offline lesson progress, stars, settings, and spaced-repetition cards WILL BE OVERWRITTEN. Are you sure you want to proceed?",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showImportConfirmDialog = false
                        importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = DuoGreen)
                ) {
                    Text("Confirm & Pick File", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirmDialog = false }) {
                    Text("Cancel", color = MaterialTheme.colorScheme.outline)
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text("Reset Progress?", fontWeight = FontWeight.Bold, color = HeartRed) },
            text = { Text("This will permanently delete your streak, star rank accomplishments, unlocked categories, and restore default profiles. This operation is offline and irreversible!") },
            confirmButton = {
                Button(
                    onClick = {
                        showResetDialog = false
                        onResetProgress()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = HeartRed)
                ) {
                    Text("Decline & Rebuild")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text("Keep Progress")
                }
            }
        )
    }

    val totalStars = lessons.sumOf { it.stars }
    val rank = ThaiRank.fromStars(totalStars)
    val isDark = androidx.compose.foundation.isSystemInDarkTheme()
    val getRankColor: (ThaiRank) -> Color = { r ->
        if (isDark) {
            if (r == ThaiRank.SILVER) Color(0xFF80CBC4) else r.primaryColor
        } else {
            r.secColor
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .testTag("profile_lazy_column"),
        contentPadding = PaddingValues(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // PROFILE HERO AVATAR DISPLAY
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 12.dp)
            ) {
                Box {
                    // Main Avatar Circle
                    Box(
                        modifier = Modifier
                            .size(96.dp)
                            .background(
                                brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                    colors = listOf(rank.primaryColor, rank.secColor)
                                ),
                                shape = CircleShape
                            )
                            .border(3.dp, Color.White, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        val initials = progress.name.firstOrNull()?.toString()?.uppercase() ?: "L"
                        Text(
                            text = initials,
                            fontSize = 38.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                    }

                    // Floating Rank Badge in corner
                    RankBadge(
                        rank = rank,
                        modifier = Modifier
                            .size(36.dp)
                            .align(Alignment.BottomEnd)
                            .background(Color.White, CircleShape)
                            .padding(2.dp),
                        showBackgroundFrame = true
                    )
                }

                Text(
                    text = progress.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "${rank.title} Rank",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = getRankColor(rank)
                    )
                    Text(
                        text = "•",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                    )
                    Text(
                        text = "$totalStars Stars",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }

        // STATS TILES GRID FRAME
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    icon = Icons.Default.LocalFireDepartment,
                    iconColor = StreakOrange,
                    value = "${progress.streak} days",
                    label = "Active Streak",
                    modifier = Modifier.weight(1f).testTag("stat_streak")
                )
                StatCard(
                    icon = Icons.Default.Star,
                    iconColor = LevelGold,
                    value = "$totalStars",
                    label = "Total Stars",
                    modifier = Modifier.weight(1f).testTag("stat_stars")
                )
            }
        }

        item {
            val completedLessonsCount = lessons.count { it.completed }
            StatCard(
                icon = Icons.Default.School,
                iconColor = GemCyan,
                value = "$completedLessonsCount / ${lessons.size}",
                label = "Completed Lessons",
                modifier = Modifier.fillMaxWidth().testTag("stat_lessons")
            )
        }

        // ACHIEVEMENTS TIER
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "🏆 Achievement Badges",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(achievements) { achievement ->
            AchievementRowCard(achievement = achievement)
        }

        // SETTINGS CONTROL CARD BOARD
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "⚙️ Settings & Preferences",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(20.dp),
                border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                    width = 1.dp,
                    brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // SOUND SWITCH
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = null, tint = DuoGreenDark)
                            Column {
                                Text("Sounds & Audio Pronunciation", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Text-to-Speech and level effects", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                        Switch(
                            checked = progress.soundEnabled,
                            onCheckedChange = onToggleSound,
                            colors = SwitchDefaults.colors(checkedThumbColor = DuoGreen, checkedTrackColor = DuoGreenLight),
                            modifier = Modifier.testTag("sound_toggle")
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // THEME SWITCH
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.DarkMode, contentDescription = null, tint = GemCyan)
                            Column {
                                Text("Dark theme skin", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("High contrast night mode overlay", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                        Switch(
                            checked = progress.isDarkMode,
                            onCheckedChange = onToggleDarkMode,
                            colors = SwitchDefaults.colors(checkedThumbColor = GemCyan, checkedTrackColor = GemCyanLight),
                            modifier = Modifier.testTag("theme_toggle")
                        )
                    }

                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))

                    // ROMANIZATION SWITCH
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(imageVector = Icons.Default.Translate, contentDescription = null, tint = DuoGreenDark)
                            Column {
                                Text("Show Romanization Only", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                Text("Show phonetic karaoke instead of Thai letters", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                            }
                        }
                        Switch(
                            checked = progress.showRomanizationOnly,
                            onCheckedChange = onToggleRomanization,
                            colors = SwitchDefaults.colors(checkedThumbColor = DuoGreen, checkedTrackColor = DuoGreenLight),
                            modifier = Modifier.testTag("romanization_toggle")
                        )
                    }
                }
            }
        }

        // LOCAL FILE BACKUP & RESTORE UTILITY
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Export Progress Card
                Card(
                    onClick = { exportLauncher.launch("thailingo_progress_backup.json") },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .testTag("export_progress_trigger_btn"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DuoGreen.copy(alpha = 0.08f)),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        width = 1.3.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(DuoGreen.copy(alpha = 0.3f))
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Share, contentDescription = null, tint = DuoGreenDark, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Export File",
                            color = DuoGreenDark,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }

                // Import Progress Card
                Card(
                    onClick = { showImportConfirmDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .height(60.dp)
                        .testTag("import_progress_trigger_btn"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = DuoGreen.copy(alpha = 0.08f)),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        width = 1.3.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(DuoGreen.copy(alpha = 0.3f))
                    )
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.CloudUpload, contentDescription = null, tint = DuoGreenDark, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Import File",
                            color = DuoGreenDark,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }

        // FACTORY DATA SYSTEM RISKS
        item {
            Spacer(modifier = Modifier.height(16.dp))
            Card(
                onClick = { showResetDialog = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .testTag("factory_reset_trigger_btn"),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = HeartRed.copy(alpha = 0.08f)),
                border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                    width = 1.3.dp,
                    brush = androidx.compose.ui.graphics.SolidColor(HeartRed.copy(alpha = 0.3f))
                )
            ) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Reset Offline Learning Progress",
                        color = HeartRed,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun StatCard(
    icon: ImageVector,
    iconColor: Color,
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = CardDefaults.outlinedCardBorder(enabled = true).copy(
            width = 1.dp,
            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(28.dp)
            )
            Text(
                text = value,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = label,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )
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
                        "star" -> Icons.Default.Star
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
