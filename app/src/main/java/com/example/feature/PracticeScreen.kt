package com.example.feature

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.core.common.ServiceLocator
import com.example.core.common.getRomanizedText
import com.example.core.common.ThaiToneIndicatorRow
import com.example.LocalShowRomanizationOnly
import com.example.LocalVocabularyList
import com.example.domain.Vocabulary
import com.example.domain.Lesson
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.example.ui.theme.*

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun PracticeScreen(
    vocabulary: List<Vocabulary>,
    lessons: List<Lesson>,
    onActiveStateChanged: (Boolean) -> Unit = {}
) {
    val showRomanizationOnly = LocalShowRomanizationOnly.current
    val vocabularyList = LocalVocabularyList.current

    val context = LocalContext.current
    val viewModel: PracticeViewModel = viewModel(factory = PracticeViewModel.Factory(context))
    
    val studyVocabs by viewModel.studyVocabs.collectAsState()
    val heartsRestored by viewModel.heartsRestored.collectAsState()
    val shopError by viewModel.shopError.collectAsState()
    val revealedFlashcards by viewModel.showFlashcardAnswers.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var activeChapter by remember { mutableIntStateOf(0) } // 0: Dictionary library, 1: Heart shop, 2: Active card study session
    var currentFlashcardStep by remember { mutableIntStateOf(0) }

    LaunchedEffect(activeChapter) {
        onActiveStateChanged(activeChapter == 2)
    }

    var flashcardLimit by remember { mutableIntStateOf(5) }
    var activeStudyVocabs by remember { mutableStateOf<List<Vocabulary>>(emptyList()) }

    val ttsHelper = remember { ServiceLocator.getTtsHelper(context) }
    
    val categories = listOf("All", "Greetings", "Food", "Numbers", "Travel", "Family")

    val seenVocab by viewModel.seenVocabList.collectAsState()

    LaunchedEffect(seenVocab) {
        if (seenVocab.isNotEmpty()) {
            flashcardLimit = flashcardLimit.coerceIn(1, seenVocab.size)
        }
    }

    val filteredVocab = remember(seenVocab, searchQuery, selectedCategory) {
        seenVocab.filter { word ->
            (selectedCategory == "All" || word.category == selectedCategory) &&
            (searchQuery.isEmpty() || 
             word.thai.contains(searchQuery, ignoreCase = true) || 
             word.english.contains(searchQuery, ignoreCase = true) || 
             word.romanization.contains(searchQuery, ignoreCase = true))
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // TOGGLE CHIPS HEADER
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(4.dp)
                ) {
                    val tabModifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .padding(vertical = 10.dp)
                    
                    Text(
                        text = "📖 Dictionary",
                        modifier = tabModifier
                            .background(if (activeChapter == 0) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { activeChapter = 0 }
                            .testTag("dict_tab"),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = if (activeChapter == 0) DuoGreenDark else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "⚡ Flashcards",
                        modifier = tabModifier
                            .background(if (activeChapter == 1) MaterialTheme.colorScheme.surface else Color.Transparent)
                            .clickable { activeChapter = 1 }
                            .testTag("shop_tab"),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = if (activeChapter == 1) DuoGreenDark else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }

                when (activeChapter) {
                    0 -> {
                        // DICTIONARY SECTION
                        Column(modifier = Modifier.fillMaxSize()) {
                            // SEARCH FIELD
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search Thai/English...") },
                                prefix = { Icon(imageVector = Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 20.dp)
                                    .testTag("vocab_search_input"),
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp)
                            )

                            // CATEGORY CHIPS SCROLLER
                            LazyRow(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentPadding = PaddingValues(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(categories) { cat ->
                                    val isSelected = selectedCategory == cat
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { selectedCategory = cat },
                                        label = { Text(cat) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = DuoGreen.copy(alpha = 0.15f),
                                            selectedLabelColor = DuoGreenDark
                                        ),
                                        modifier = Modifier.testTag("chip_$cat")
                                    )
                                }
                            }

                            // DICTIONARY CARDS LIST
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                                    .testTag("vocab_cards_list"),
                                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                if (filteredVocab.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(48.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                if (seenVocab.isEmpty()) "🚫 No words discovered yet!\n\nComplete standard lessons in the Learn track to unlock words in your bilingual practice dictionary." else "No Thai words found! Try another keyword.",
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }

                                items(filteredVocab) { word ->
                                    var cardFlipped by remember(word.id) { mutableStateOf(false) }

                                    Card(
                                        onClick = { cardFlipped = !cardFlipped },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("vocab_card_${word.id}"),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (cardFlipped) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface
                                        ),
                                        border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                                            width = 1.2.dp,
                                            brush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                                        ),
                                        shape = RoundedCornerShape(16.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(16.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                // Audio Play Button
                                                IconButton(
                                                    onClick = { ttsHelper.speak(word.thai) },
                                                    modifier = Modifier
                                                        .background(DuoGreen.copy(alpha = 0.12f), CircleShape)
                                                        .size(40.dp)
                                                ) {
                                                    Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Play voice audio", tint = DuoGreenDark)
                                                }

                                                Column {
                                                    Text(
                                                        text = if (showRomanizationOnly) word.romanization else word.thai,
                                                        fontSize = 22.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (!showRomanizationOnly) {
                                                        Text(
                                                            text = word.romanization,
                                                            fontSize = 13.sp,
                                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                                                        )
                                                        Spacer(modifier = Modifier.height(4.dp))
                                                        ThaiToneIndicatorRow(thaiWord = word.thai, showLabel = true)
                                                    } else {
                                                        ThaiToneIndicatorRow(thaiWord = word.thai, showLabel = true)
                                                    }
                                                    
                                                    AnimatedVisibility(
                                                        visible = cardFlipped,
                                                        enter = fadeIn() + expandVertically(),
                                                        exit = fadeOut() + shrinkVertically()
                                                    ) {
                                                        Column(modifier = Modifier.padding(top = 8.dp)) {
                                                            Text(
                                                                text = "Meaning: ${word.english}",
                                                                fontSize = 14.sp,
                                                                fontWeight = FontWeight.Black,
                                                                color = DuoGreenDark
                                                            )
                                                            if (word.exampleThai.isNotEmpty()) {
                                                                val haptic = LocalHapticFeedback.current
                                                                Spacer(modifier = Modifier.height(4.dp))
                                                                Text(
                                                                    text = "Ex: ${word.exampleThai}",
                                                                    fontSize = 13.sp,
                                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                                                    modifier = Modifier
                                                                        .clickable {
                                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                            ttsHelper.speak(word.exampleThai)
                                                                        }
                                                                        .padding(vertical = 2.dp)
                                                                )
                                                                Text(
                                                                    text = "(${word.exampleEnglish})",
                                                                    fontSize = 12.sp,
                                                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                                                    modifier = Modifier
                                                                        .clickable {
                                                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                            ttsHelper.speak(word.exampleThai)
                                                                        }
                                                                        .padding(vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                            Icon(
                                                imageVector = if (cardFlipped) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = "Details",
                                                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    1 -> {
                        // FLASHCARD PRACTICE WINDOW
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Top
                        ) {
                            Icon(imageVector = Icons.Default.School, contentDescription = null, tint = DuoGreen, modifier = Modifier.size(68.dp))
                            
                            Spacer(modifier = Modifier.height(8.dp))

                            Text("Flashcard Practice", fontSize = 24.sp, fontWeight = FontWeight.Black)
                            Text("Improve your vocabulary recall of words you already unlocked.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))

                            Spacer(modifier = Modifier.height(28.dp))

                            if (seenVocab.isNotEmpty()) {
                                // Horizontal Slider/Scrollbar Selection Card
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(bottom = 18.dp),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)),
                                    shape = RoundedCornerShape(16.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(16.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "VOCABULARY POOL: ${seenVocab.size} words unlocked",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = MaterialTheme.colorScheme.primary,
                                            letterSpacing = 1.sp
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "$flashcardLimit Flashcards to Study",
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Black,
                                            color = DuoGreenDark
                                        )
                                        Spacer(modifier = Modifier.height(10.dp))
                                        
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            IconButton(
                                                onClick = {
                                                    if (flashcardLimit > 1) flashcardLimit--
                                                },
                                                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                            ) {
                                                Icon(imageVector = Icons.Default.Remove, contentDescription = "Decrement flashcard limit", tint = DuoGreen)
                                            }
                                            
                                            Slider(
                                                value = flashcardLimit.toFloat(),
                                                onValueChange = { flashcardLimit = it.toInt().coerceIn(1, seenVocab.size) },
                                                valueRange = 1f..seenVocab.size.toFloat(),
                                                colors = SliderDefaults.colors(
                                                    thumbColor = DuoGreen,
                                                    activeTrackColor = DuoGreen,
                                                    inactiveTrackColor = DuoGreenLight.copy(alpha = 0.3f)
                                                ),
                                                modifier = Modifier.weight(1f).testTag("flashcard_limit_slider")
                                            )
                                            
                                            IconButton(
                                                onClick = {
                                                    if (flashcardLimit < seenVocab.size) flashcardLimit++
                                                },
                                                modifier = Modifier.size(36.dp).background(MaterialTheme.colorScheme.surface, CircleShape)
                                            ) {
                                                Icon(imageVector = Icons.Default.Add, contentDescription = "Increment flashcard limit", tint = DuoGreen)
                                            }
                                        }
                                    }
                                }

                                // START REVIEW WORKOUT CARD
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            activeStudyVocabs = seenVocab.shuffled().take(flashcardLimit)
                                            activeChapter = 2
                                            currentFlashcardStep = 0
                                        }
                                        .testTag("free_study_practice_btn"),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(16.dp),
                                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(width = 1.5.dp, brush = androidx.compose.ui.graphics.SolidColor(DuoGreen))
                                ) {
                                    Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                            Box(modifier = Modifier.size(44.dp).background(DuoGreen, CircleShape), contentAlignment = Alignment.Center) {
                                                Icon(imageVector = Icons.Default.AutoAwesome, contentDescription = null, tint = Color.White)
                                            }
                                            Column {
                                                Text("Start Review Workout", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                                Text("Review $flashcardLimit random flashcards", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                            }
                                        }
                                        Text("START", fontWeight = FontWeight.Black, fontSize = 16.sp, color = DuoGreen)
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Unlock some words in lessons first to start practicing with flashcards!",
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        // ACTIVE REVIEW FLASHCARD SESSION
                        if (activeStudyVocabs.isNotEmpty() && currentFlashcardStep < activeStudyVocabs.size) {
                            val activeVocab = activeStudyVocabs[currentFlashcardStep]
                            val isFlipped = revealedFlashcards.contains(activeVocab.id)
                            
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(24.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Progress Dots
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    repeat(activeStudyVocabs.size) { idx ->
                                        val color = if (idx <= currentFlashcardStep) DuoGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                                        Box(modifier = Modifier.size(12.dp).padding(horizontal = 2.dp).background(color, CircleShape))
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Large Flashcard Arena Frame
                                Card(
                                    onClick = { viewModel.toggleFlashcardReveal(activeVocab.id) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .testTag("flashcard_click_surface"),
                                    shape = RoundedCornerShape(24.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isFlipped) DuoGreenLight else MaterialTheme.colorScheme.surface
                                    ),
                                    border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                                        width = 3.dp,
                                        brush = androidx.compose.ui.graphics.SolidColor(if (isFlipped) DuoGreen else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
                                    )
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(24.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        IconButton(
                                            onClick = { ttsHelper.speak(activeVocab.thai) },
                                            modifier = Modifier.background(DuoGreen, CircleShape).size(56.dp)
                                        ) {
                                            Icon(imageVector = Icons.Default.VolumeUp, contentDescription = "Play voice audio", tint = Color.White, modifier = Modifier.size(28.dp))
                                        }

                                        Spacer(modifier = Modifier.height(28.dp))

                                        Text(
                                            text = if (showRomanizationOnly) activeVocab.romanization else activeVocab.thai,
                                            fontSize = 44.sp,
                                            fontWeight = FontWeight.Black,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            textAlign = TextAlign.Center
                                        )

                                        if (!showRomanizationOnly) {
                                            Text(
                                                text = "(${activeVocab.romanization})",
                                                fontSize = 18.sp,
                                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                                                textAlign = TextAlign.Center
                                            )
                                        }

                                        ThaiToneIndicatorRow(
                                            thaiWord = activeVocab.thai,
                                            showLabel = true,
                                            modifier = Modifier.padding(top = 12.dp)
                                        )

                                        Spacer(modifier = Modifier.height(36.dp))

                                        if (isFlipped) {
                                            Text("English Meaning:", fontSize = 13.sp, color = DuoGreenDark, fontWeight = FontWeight.Bold)
                                            Text(
                                                text = activeVocab.english,
                                                fontSize = 28.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = DuoGreenDark,
                                                textAlign = TextAlign.Center
                                            )
                                        } else {
                                            Text("Tap Card to Flip & Reveal Meaning", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f))
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(24.dp))

                                // Back / Next button controller
                                Button(
                                    onClick = {
                                        if (currentFlashcardStep < activeStudyVocabs.size - 1) {
                                            currentFlashcardStep++
                                        } else {
                                            // Finish Study Session! Restore Heart!
                                            viewModel.completeVocabReviewToRestoreHeart()
                                            activeChapter = 1
                                        }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(54.dp)
                                        .testTag("flashcard_next_btn"),
                                    colors = ButtonDefaults.buttonColors(containerColor = DuoGreen),
                                    shape = RoundedCornerShape(12.dp),
                                    enabled = isFlipped
                                ) {
                                    Text(
                                        text = if (currentFlashcardStep == activeStudyVocabs.size - 1) "FINISH WORKOUT" else "NEXT FLASHCARD",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
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
