package com.example.feature

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.DuoGreen
import com.example.ui.theme.DuoGreenLight
import com.example.ui.theme.DuoGreenDark
import com.example.ui.theme.StreakOrange
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onFinished: (name: String, avatar: String, dailyGoal: Int, showRomanizationOnly: Boolean) -> Unit,
    onImportProgress: suspend (String) -> Boolean
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var name by remember { mutableStateOf("") }
    var selectedAvatar by remember { mutableStateOf("🐘 Elephant") }
    var selectedGoal by remember { mutableIntStateOf(10) } // Standard: 10 min
    var showRomanizationOnly by remember { mutableStateOf(false) }
    var currentStep by remember { mutableIntStateOf(0) } // 0: Welcome Choice, 1: Profile (Name & Avatar), 2: Daily Goal

    // Import Launcher for existing backup progress file
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
                            android.widget.Toast.makeText(context, "Welcome back! Progress imported successfully.", android.widget.Toast.LENGTH_LONG).show()
                        } else {
                            android.widget.Toast.makeText(context, "Failed to import: Invalid backup file format.", android.widget.Toast.LENGTH_LONG).show()
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // STEP PROGRESS INDEX INDICATOR
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                repeat(3) { idx ->
                    val color = if (idx <= currentStep) DuoGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(6.dp)
                            .padding(horizontal = 4.dp)
                            .background(color, shape = RoundedCornerShape(3.dp))
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // STEP SWITCH CONTEXT (Scrollable to adjust on low density devices)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                when (currentStep) {
                    0 -> OnboardingWelcomeChoiceView(
                        onStartNew = { currentStep = 1 },
                        onImportClick = { importLauncher.launch(arrayOf("application/json")) }
                    )
                    1 -> OnboardingProfileView(
                        name = name,
                        onNameChanged = { name = it },
                        showRomanizationOnly = showRomanizationOnly,
                        onRomanizationChange = { showRomanizationOnly = it },
                        selectedAvatar = selectedAvatar,
                        onAvatarSelect = { selectedAvatar = it }
                    )
                    2 -> SetupGoalView(
                        selectedGoal = selectedGoal,
                        onGoalChanged = { selectedGoal = it }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // NAVIGATION BUTTONS (Visible on Profile setup (1) and Goal setup (2))
            if (currentStep > 0) {
                Button(
                    onClick = {
                        if (currentStep < 2) {
                            currentStep++
                        } else {
                            onFinished(name, selectedAvatar, selectedGoal, showRomanizationOnly)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("onboarding_continue_btn"),
                    colors = ButtonDefaults.buttonColors(containerColor = DuoGreen),
                    shape = RoundedCornerShape(16.dp),
                    enabled = currentStep != 1 || (name.isNotBlank() && selectedAvatar.isNotBlank())
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = if (currentStep == 2) "Start Learning Thai!" else "Continue",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Forward step",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OnboardingWelcomeChoiceView(
    onStartNew: () -> Unit,
    onImportClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Beautiful Logo Icon Container
        Box(
            modifier = Modifier
                .size(130.dp)
                .background(DuoGreen.copy(alpha = 0.1f), RoundedCornerShape(36.dp))
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Translate,
                contentDescription = "ThaiLingo Logo Image",
                tint = DuoGreen,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome to ThaiLingo!",
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = DuoGreen,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Master vocabulary, pronunciation, speaking, and listening completely offline in a personalized spaced-repetition program.",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        // Play Option 1: Start New Game
        Button(
            onClick = onStartNew,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .testTag("onboarding_start_new_btn"),
            colors = ButtonDefaults.buttonColors(containerColor = DuoGreen),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = "Start New Adventure",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Play Option 2: Import Backup Save
        OutlinedButton(
            onClick = onImportClick,
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .testTag("onboarding_import_save_btn"),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = DuoGreenDark),
            shape = RoundedCornerShape(16.dp),
            border = ButtonDefaults.outlinedButtonBorder(enabled = true).copy(
                width = 2.dp,
                brush = androidx.compose.ui.graphics.SolidColor(DuoGreen)
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = null,
                    tint = DuoGreenDark
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Import Existing Save",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = DuoGreenDark
                )
            }
        }
    }
}

@Composable
fun OnboardingProfileView(
    name: String,
    onNameChanged: (String) -> Unit,
    showRomanizationOnly: Boolean,
    onRomanizationChange: (Boolean) -> Unit,
    selectedAvatar: String,
    onAvatarSelect: (String) -> Unit
) {
    val animalOptions = listOf(
        "🐘 Elephant",
        "🐯 Tiger",
        "🐒 Monkey",
        "🐼 Panda",
        "🐨 Koala",
        "🦊 Fox",
        "🦁 Lion",
        "🐻 Bear",
        "🐰 Bunny",
        "🐱 Cat"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Create Your Profile",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Choose your avatar companion and enter your name",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Custom Name Input
        OutlinedTextField(
            value = name,
            onValueChange = onNameChanged,
            placeholder = { Text("What should we call you?") },
            label = { Text("Your Name") },
            modifier = Modifier
                .fillMaxWidth()
                .testTag("onboarding_name_input"),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DuoGreen,
                unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                focusedLabelColor = DuoGreen
            ),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Avatar Predefined Animal Selection Title
        Text(
            text = "Select Animal Companion",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start
        )

        Spacer(modifier = Modifier.height(8.dp))

        // We lay them out in a robust adaptive custom Grid Row flowing or wrapping
        val chunkedAnimals = animalOptions.chunked(5)
        chunkedAnimals.forEach { chunk ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chunk.forEach { animal ->
                    val isSelected = selectedAvatar == animal
                    val parts = animal.split(" ", limit = 2)
                    val emoji = parts.getOrNull(0) ?: "🐘"
                    val animalName = parts.getOrNull(1) ?: ""
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .background(
                                color = if (isSelected) DuoGreen.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .border(
                                width = if (isSelected) 3.dp else 1.dp,
                                color = if (isSelected) DuoGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onAvatarSelect(animal) }
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(text = emoji, fontSize = 28.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = animalName,
                                fontSize = 10.sp,
                                fontWeight = if (isSelected) FontWeight.Black else FontWeight.Normal,
                                color = if (isSelected) DuoGreenDark else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                                maxLines = 1,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Romanization Toggle Option Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.12f)
            ),
            shape = RoundedCornerShape(16.dp),
            border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                width = 1.dp,
                brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f))
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onRomanizationChange(!showRomanizationOnly) }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show Romanization Only (Karaoke)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = DuoGreenDark
                    )
                    Text(
                        text = "Show phonetic Romanization instead of Thai script characters",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Switch(
                    checked = showRomanizationOnly,
                    onCheckedChange = onRomanizationChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = DuoGreen,
                        checkedTrackColor = DuoGreen.copy(alpha = 0.4f)
                    ),
                    modifier = Modifier.testTag("romanization_switch_onboarding")
                )
            }
        }
    }
}

@Composable
fun SetupGoalView(
    selectedGoal: Int,
    onGoalChanged: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Choose Your Daily Goal",
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Select how much time you want to learn each day to maintain your learning streak.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(20.dp))

        val goals = listOf(
            Triple("Casual", 5, "5 min / day"),
            Triple("Regular", 10, "10 min / day"),
            Triple("Serious", 20, "20 min / day"),
            Triple("Insane", 40, "40 min / day")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            goals.forEach { (title, minutes, duration) ->
                val isSelected = selectedGoal == minutes
                val borderCol = if (isSelected) DuoGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)
                val bgCol = if (isSelected) DuoGreen.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surface

                Card(
                    onClick = { onGoalChanged(minutes) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .testTag("goal_card_$minutes"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = bgCol),
                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                        width = if (isSelected) 3.dp else 1.5.dp,
                        brush = androidx.compose.ui.graphics.SolidColor(borderCol)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = title,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) DuoGreenDark else MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = duration,
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }
                        Text(
                            text = "$minutes min",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isSelected) StreakOrange else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}
