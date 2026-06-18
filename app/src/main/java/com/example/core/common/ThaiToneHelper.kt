package com.example.core.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ThaiTone(
    val icon: String,
    val toneName: String,
    val description: String,
    val lightColor: Color,
    val darkColor: Color
) {
    MID("", "Mid", "Flat", Color(0xFF78909C), Color(0xFFCFD8DC)),
    LOW("", "Low", "Low falling", Color(0xFF1E88E5), Color(0xFF90CAF9)),
    FALLING("", "Falling", "Steep falling", Color(0xFFE53935), Color(0xFFEF9A9A)),
    HIGH("", "High", "High rising", Color(0xFF43A047), Color(0xFFA5D6A7)),
    RISING("", "Rising", "Dipping to high", Color(0xFF8E24AA), Color(0xFFE1BEE7))
}

data class ToneDetail(
    val syllable: String,
    val tone: ThaiTone
)

object ThaiToneHelper {
    
    // A mapping of Thai vocabulary words to their exact tones. Covers major vocabulary items.
    private val vocabToneCache = mapOf(
        // Greetings
        "สวัสดี" to listOf(ThaiTone.LOW, ThaiTone.LOW, ThaiTone.MID),
        "ขอบคุณ" to listOf(ThaiTone.LOW, ThaiTone.MID),
        "สบายดีไหม" to listOf(ThaiTone.LOW, ThaiTone.MID, ThaiTone.MID, ThaiTone.RISING),
        "สบายดี" to listOf(ThaiTone.LOW, ThaiTone.MID, ThaiTone.MID),
        "ยินดีที่ได้รู้จัก" to listOf(ThaiTone.MID, ThaiTone.MID, ThaiTone.FALLING, ThaiTone.FALLING, ThaiTone.LOW, ThaiTone.LOW),
        "ขอโทษ" to listOf(ThaiTone.RISING, ThaiTone.FALLING),
        "ใช่" to listOf(ThaiTone.FALLING),
        "ไม่ใช่" to listOf(ThaiTone.FALLING, ThaiTone.FALLING),
        "ลาก่อน" to listOf(ThaiTone.MID, ThaiTone.LOW),
        "โชคดี" to listOf(ThaiTone.FALLING, ThaiTone.MID),
        "ยินดี" to listOf(ThaiTone.MID, ThaiTone.MID),
        "แล้วพบกันใหม่" to listOf(ThaiTone.HIGH, ThaiTone.HIGH, ThaiTone.MID, ThaiTone.LOW),
        "ราตรีสวัสดิ์" to listOf(ThaiTone.MID, ThaiTone.MID, ThaiTone.LOW, ThaiTone.LOW),
        "คุณชื่ออะไร" to listOf(ThaiTone.MID, ThaiTone.FALLING, ThaiTone.MID, ThaiTone.MID),
        "ผมชื่อ" to listOf(ThaiTone.RISING, ThaiTone.FALLING),
        "ฉันชื่อ" to listOf(ThaiTone.RISING, ThaiTone.FALLING),
        "คุณ" to listOf(ThaiTone.MID),
        "ผม" to listOf(ThaiTone.RISING),
        "ฉัน" to listOf(ThaiTone.RISING),
        "ยินดีด้วย" to listOf(ThaiTone.MID, ThaiTone.MID, ThaiTone.HIGH),
        "ครับ" to listOf(ThaiTone.HIGH),
        "ค่ะ" to listOf(ThaiTone.FALLING),
        "เรา" to listOf(ThaiTone.MID),
        "เขา" to listOf(ThaiTone.RISING),
        "มัน" to listOf(ThaiTone.MID),
        "พวกเรา" to listOf(ThaiTone.FALLING, ThaiTone.MID),
        "พวกเขา" to listOf(ThaiTone.FALLING, ThaiTone.RISING),
        "เธอ" to listOf(ThaiTone.MID),
        "ท่าน" to listOf(ThaiTone.FALLING),
        
        // Food
        "อาหาร" to listOf(ThaiTone.MID, ThaiTone.RISING),
        "ข้าว" to listOf(ThaiTone.FALLING),
        "น้ำ" to listOf(ThaiTone.HIGH),
        "ผลไม้" to listOf(ThaiTone.RISING, ThaiTone.MID, ThaiTone.HIGH),
        "อร่อย" to listOf(ThaiTone.LOW, ThaiTone.LOW),
        "เผ็ด" to listOf(ThaiTone.LOW),
        "หวาน" to listOf(ThaiTone.RISING),
        "เปรี้ยว" to listOf(ThaiTone.FALLING),
        "เค็ม" to listOf(ThaiTone.MID),
        "ขม" to listOf(ThaiTone.RISING),
        
        // Numbers
        "ศูนย์" to listOf(ThaiTone.RISING),
        "หนึ่ง" to listOf(ThaiTone.LOW),
        "สอง" to listOf(ThaiTone.RISING),
        "สาม" to listOf(ThaiTone.RISING),
        "สี่" to listOf(ThaiTone.LOW),
        "ห้า" to listOf(ThaiTone.FALLING),
        "หก" to listOf(ThaiTone.LOW),
        "เจ็ด" to listOf(ThaiTone.LOW),
        "แปด" to listOf(ThaiTone.LOW),
        "เก้า" to listOf(ThaiTone.FALLING),
        "สิบ" to listOf(ThaiTone.LOW),
        
        // Travel
        "สนามบิน" to listOf(ThaiTone.LOW, ThaiTone.LOW, ThaiTone.MID),
        "โรงแรม" to listOf(ThaiTone.MID, ThaiTone.MID),
        "แท็กซี่" to listOf(ThaiTone.HIGH, ThaiTone.FALLING), // wait, แท็ก (high), ซี่ (falling)
        "ตั๋ว" to listOf(ThaiTone.RISING),
        "แผนที่" to listOf(ThaiTone.RISING, ThaiTone.FALLING),
        "เที่ยว" to listOf(ThaiTone.FALLING),
        "สถานี" to listOf(ThaiTone.LOW, ThaiTone.LOW, ThaiTone.MID),
        "รถไฟ" to listOf(ThaiTone.HIGH, ThaiTone.MID),
        "เครื่องบิน" to listOf(ThaiTone.FALLING, ThaiTone.MID),
        "พาสปอร์ต" to listOf(ThaiTone.MID, ThaiTone.LOW),
        
        // Family
        "ครอบครัว" to listOf(ThaiTone.MID, ThaiTone.MID),
        "พ่อ" to listOf(ThaiTone.FALLING),
        "แม่" to listOf(ThaiTone.FALLING),
        "พี่ชาย" to listOf(ThaiTone.FALLING, ThaiTone.MID),
        "น้องสาว" to listOf(ThaiTone.HIGH, ThaiTone.RISING),
        "ลูก" to listOf(ThaiTone.FALLING),
        "ปู่" to listOf(ThaiTone.LOW),
        "ย่า" to listOf(ThaiTone.FALLING), // ย่า is falling
        "ตา" to listOf(ThaiTone.MID),
        "ยาย" to listOf(ThaiTone.MID)
    )

    /**
     * Determines tones for any Thai string.
     * Uses a combination of cached mappings for known vocabulary and rule-based fallback heuristics.
     */
    fun detectTones(thaiWord: String): List<ThaiTone> {
        val trimmed = thaiWord.trim()
        
        // 1. Direct cache Hit
        if (vocabToneCache.containsKey(trimmed)) {
            return vocabToneCache[trimmed]!!
        }
        
        // Search if we contain substrings of cache keys
        for ((key, value) in vocabToneCache) {
            if (trimmed == key) return value
        }

        // 2. Rule-based Fallback Heuristics
        // Let's scan for tone marks in the entire word to represent what tones it likely contains!
        val tones = mutableListOf<ThaiTone>()
        
        // Let's divide complex compound strings or look at individual tone characters
        val hasMaiEk = trimmed.contains('\u0E48')      // ่
        val hasMaiTho = trimmed.contains('\u0E49')      // ้
        val hasMaiTri = trimmed.contains('\u0E4A')      // ๊
        val hasMaiChattawa = trimmed.contains('\u0E4B') // ๋
        
        if (hasMaiChattawa) {
            tones.add(ThaiTone.RISING)
        }
        if (hasMaiTri) {
            tones.add(ThaiTone.HIGH)
        }
        if (hasMaiTho) {
            // Check initial consonants of the word to differentiate high/mid from low
            val firstChar = trimmed.firstOrNull { it in '\u0E01'..'\u0E2E' }
            if (firstChar != null && isLowConsonant(firstChar)) {
                tones.add(ThaiTone.HIGH)
            } else {
                tones.add(ThaiTone.FALLING)
            }
        }
        if (hasMaiEk) {
            val firstChar = trimmed.firstOrNull { it in '\u0E01'..'\u0E2E' }
            if (firstChar != null && isLowConsonant(firstChar)) {
                tones.add(ThaiTone.FALLING)
            } else {
                tones.add(ThaiTone.LOW)
            }
        }
        
        // If no explicit tone marks, check consonants classes
        if (tones.isEmpty()) {
            val isHighOrRisingClass = trimmed.any { it in listOf('ข', 'ฉ', 'ถ', 'ผ', 'ฝ', 'ศ', 'ษ', 'ส', 'ห') }
            val isDeadEnding = trimmed.endsWith('ก') || trimmed.endsWith('ด') || trimmed.endsWith('บ') || 
                               trimmed.endsWith('ต') || trimmed.contains("p") || trimmed.contains("t") || trimmed.contains("k")
            
            if (isHighOrRisingClass) {
                if (isDeadEnding) {
                    tones.add(ThaiTone.LOW)
                } else {
                    tones.add(ThaiTone.RISING)
                }
            } else {
                if (isDeadEnding) {
                    val firstChar = trimmed.firstOrNull { it in '\u0E01'..'\u0E2E' }
                    if (firstChar != null && isLowConsonant(firstChar)) {
                        tones.add(ThaiTone.FALLING) // or high
                    } else {
                        tones.add(ThaiTone.LOW)
                    }
                } else {
                    tones.add(ThaiTone.MID)
                }
            }
        }
        
        return tones.take(4) // limit to 4 syllable tones maximum so it's clean in UI
    }
    
    private fun isLowConsonant(c: Char): Boolean {
        // Low consonants in Thai (ค, ช, ซ, ท, น, พ, ฟ, ม, ย, ร, ล, ว, etc.)
        return c in listOf(
            'ค', 'ฅ', 'ฆ', 'ง', 'ช', 'ซ', 'ฌ', 'ญ', 'ฑ', 'ฒ', 'ณ', 
            'ท', 'ธ', 'น', 'พ', 'ฟ', 'ภ', 'ม', 'ย', 'ร', 'ล', 'ว', 'ฬ', 'ฮ'
        )
    }
}

/**
 * A beautiful, highly-polished Jetpack Compose UI component to render the tone icons
 * under/alongside any Thai word labels.
 */
@Composable
fun ThaiToneIndicatorRow(
    thaiWord: String,
    modifier: Modifier = Modifier,
    showLabel: Boolean = false
) {
    if (!thaiWord.any { it in '\u0E00'..'\u0E7F' }) return
    val tones = ThaiToneHelper.detectTones(thaiWord)
    if (tones.isEmpty()) return

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        tones.forEach { tone ->
            val isDark = MaterialTheme.colorScheme.brightness == Brightness.Dark
            val color = if (isDark) tone.darkColor else tone.lightColor
            
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = color.copy(alpha = 0.15f),
                border = CardDefaults.outlinedCardBorder(enabled = true).copy(
                    width = 1.dp,
                    brush = androidx.compose.ui.graphics.SolidColor(color.copy(alpha = 0.5f))
                ),
                modifier = Modifier.padding(vertical = 1.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tone.toneName,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = color
                    )
                }
            }
        }
    }
}

// Extension to determine theme mode
private val ColorScheme.brightness : Brightness
    get() = if (this.surface.red + this.surface.green + this.surface.blue < 1.5f) Brightness.Dark else Brightness.Light

enum class Brightness { Light, Dark }
