package com.example.core.common

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class ThaiTtsHelper(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        try {
            tts = TextToSpeech(context.applicationContext, this)
        } catch (e: Exception) {
            Log.e("ThaiTtsHelper", "Failed to construct TTS engine", e)
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale("th", "TH"))
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Fallback to broader language locale if region specific is missing
                val secondResult = tts?.setLanguage(Locale("th"))
                if (secondResult == TextToSpeech.LANG_MISSING_DATA || secondResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("ThaiTtsHelper", "Thai language is not supported or requires engine downloads on this device.")
                } else {
                    isInitialized = true
                }
            } else {
                isInitialized = true
            }
        } else {
            Log.e("ThaiTtsHelper", "Initialization of TextToSpeech engine failed.")
        }
    }

    fun speak(text: String) {
        if (isInitialized && tts != null) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "ThaiLingoTTS")
        }
    }

    fun shutdown() {
        try {
            tts?.stop()
            tts?.shutdown()
        } catch (e: Exception) {
            Log.e("ThaiTtsHelper", "Error shutting down TTS", e)
        }
    }
}
