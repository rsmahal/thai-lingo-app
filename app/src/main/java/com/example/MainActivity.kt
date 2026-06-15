package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.common.ServiceLocator
import com.example.feature.*
import com.example.ui.theme.ThaiLingoTheme
import com.example.ui.theme.DuoGreen
import com.example.ui.theme.DuoGreenLight
import com.example.ui.theme.DuoGreenDark

enum class AppTab {
    LEARN,
    REVIEW,
    PRACTICE,
    PROFILE
}

val LocalShowRomanizationOnly = androidx.compose.runtime.staticCompositionLocalOf { false }
val LocalVocabularyList = androidx.compose.runtime.staticCompositionLocalOf { emptyList<com.example.domain.Vocabulary>() }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val mainViewModel: MainViewModel = viewModel(
                factory = MainViewModel.Factory(context)
            )

            val userProgressState by mainViewModel.userProgress.collectAsState()
            val lessons by mainViewModel.lessons.collectAsState()
            val achievements by mainViewModel.achievements.collectAsState()
            val vocabulary by mainViewModel.vocabulary.collectAsState()
            val isInitializing by mainViewModel.isInitializing.collectAsState()

            val progress = userProgressState
            val isDarkTheme = progress?.isDarkMode ?: false

            androidx.compose.runtime.CompositionLocalProvider(
                LocalShowRomanizationOnly provides (progress?.showRomanizationOnly ?: false),
                LocalVocabularyList provides vocabulary
            ) {
                ThaiLingoTheme(darkTheme = isDarkTheme) {
                if (isInitializing) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = DuoGreen)
                    }
                } else if (progress == null || progress.name == "Lingo Learner" && progress.streak == 0 && progress.xp == 0) {
                    // Start Onboarding
                    OnboardingScreen(
                        onFinished = { name, goal, showRomanOnly ->
                            mainViewModel.completeOnboarding(name, goal, showRomanOnly)
                        }
                    )
                } else {
                    // Main workspace
                    var activeTab by remember { mutableStateOf(AppTab.LEARN) }
                    var activeLessonId by remember { mutableStateOf<Int?>(null) }

                    // If a lesson exercise is running, show full screen distraction-free overlay
                    activeLessonId?.let { lessonId ->
                        LessonPlayScreen(
                            lessonId = lessonId,
                            onBackToHome = { activeLessonId = null },
                            onAwardXp = { xp -> mainViewModel.awardXp(xp) }
                        )
                    } ?: run {
                        // Main Tab Navigation Workspace
                        Scaffold(
                            modifier = Modifier.fillMaxSize(),
                            bottomBar = {
                                NavigationBar(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                        .navigationBarsPadding(),
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    tonalElevation = 8.dp
                                ) {
                                    NavigationBarItem(
                                        selected = activeTab == AppTab.LEARN,
                                        onClick = { activeTab = AppTab.LEARN },
                                        icon = { Icon(imageVector = Icons.Default.School, contentDescription = "Curriculum roadmap") },
                                        label = { Text("Learn", fontWeight = FontWeight.Bold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = DuoGreenDark,
                                            selectedTextColor = DuoGreenDark,
                                            indicatorColor = DuoGreenLight
                                        ),
                                        modifier = Modifier.testTag("learn_tab_btn")
                                    )

                                    NavigationBarItem(
                                        selected = activeTab == AppTab.REVIEW,
                                        onClick = { activeTab = AppTab.REVIEW },
                                        icon = { Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Review mistakes") },
                                        label = { Text("Review", fontWeight = FontWeight.Bold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = DuoGreenDark,
                                            selectedTextColor = DuoGreenDark,
                                            indicatorColor = DuoGreenLight
                                        ),
                                        modifier = Modifier.testTag("review_tab_btn")
                                    )

                                    NavigationBarItem(
                                        selected = activeTab == AppTab.PRACTICE,
                                        onClick = { activeTab = AppTab.PRACTICE },
                                        icon = { Icon(imageVector = Icons.Default.Book, contentDescription = "Practice & heart shop") },
                                        label = { Text("Practice", fontWeight = FontWeight.Bold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = DuoGreenDark,
                                            selectedTextColor = DuoGreenDark,
                                            indicatorColor = DuoGreenLight
                                        ),
                                        modifier = Modifier.testTag("practice_tab_btn")
                                    )

                                    NavigationBarItem(
                                        selected = activeTab == AppTab.PROFILE,
                                        onClick = { activeTab = AppTab.PROFILE },
                                        icon = { Icon(imageVector = Icons.Default.Person, contentDescription = "Stats & profiles") },
                                        label = { Text("Profile", fontWeight = FontWeight.Bold) },
                                        colors = NavigationBarItemDefaults.colors(
                                            selectedIconColor = DuoGreenDark,
                                            selectedTextColor = DuoGreenDark,
                                            indicatorColor = DuoGreenLight
                                        ),
                                        modifier = Modifier.testTag("profile_tab_btn")
                                    )
                                }
                            }
                        ) { innerPadding ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            ) {
                                when (activeTab) {
                                    AppTab.LEARN -> {
                                        HomeScreen(
                                            progress = progress,
                                            lessons = lessons,
                                            onStartLesson = { lessonId ->
                                                activeLessonId = lessonId
                                            }
                                        )
                                    }

                                    AppTab.REVIEW -> {
                                        ReviewScreen()
                                    }

                                    AppTab.PRACTICE -> {
                                        PracticeScreen(
                                            vocabulary = vocabulary,
                                            lessons = lessons
                                        )
                                    }

                                    AppTab.PROFILE -> {
                                        ProfileScreen(
                                            progress = progress,
                                            achievements = achievements,
                                            lessons = lessons,
                                            onToggleSound = { mainViewModel.toggleSound(it) },
                                            onToggleDarkMode = { mainViewModel.toggleDarkMode(it) },
                                            onToggleRomanization = { mainViewModel.toggleRomanization(it) },
                                            onResetProgress = { mainViewModel.resetProgress() }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            ServiceLocator.getTtsHelper(applicationContext).shutdown()
        } catch (e: Exception) {
            // Safe shutdown skip
        }
    }
}
