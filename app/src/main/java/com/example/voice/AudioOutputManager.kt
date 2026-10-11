package com.example.voice

import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

class AudioOutputManager(private val context: Context) : TextToSpeech.OnInitListener {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ttsStatus = MutableStateFlow("प्रारंभ हो रहा है...")
    val ttsStatus: StateFlow<String> = _ttsStatus.asStateFlow()

    private var pendingOnComplete: (() -> Unit)? = null
    private var currentLanguage = Locale.forLanguageTag("hi-IN")
    private var currentRate = 1.0f
    private var currentPitch = 1.0f

    init {
        tts = TextToSpeech(context.applicationContext, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(currentLanguage)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                // Try fallback to general Hindi or default
                val hindiGeneral = Locale.forLanguageTag("hi")
                val fallbackResult = tts?.setLanguage(hindiGeneral)
                if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    _ttsStatus.value = "हिन्दी आवाज़ डेटा उपलब्ध नहीं है, डिफ़ॉल्ट आवाज़ का उपयोग किया जाएगा"
                    tts?.language = Locale.getDefault()
                } else {
                    _ttsStatus.value = "हिन्दी आवाज़ तैयार है"
                }
            } else {
                _ttsStatus.value = "हिन्दी आवाज़ तैयार है"
            }

            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    _isSpeaking.value = true
                }

                override fun onDone(utteranceId: String?) {
                    _isSpeaking.value = false
                    pendingOnComplete?.invoke()
                    pendingOnComplete = null
                }

                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    _isSpeaking.value = false
                    pendingOnComplete?.invoke()
                    pendingOnComplete = null
                }

                override fun onError(utteranceId: String?, errorCode: Int) {
                    _isSpeaking.value = false
                    pendingOnComplete?.invoke()
                    pendingOnComplete = null
                }
            })

            tts?.setSpeechRate(currentRate)
            tts?.setPitch(currentPitch)
            isInitialized = true
        } else {
            _ttsStatus.value = "TextToSpeech प्रारंभ नहीं हो सका"
        }
    }

    fun setSpeechRate(rate: Float) {
        currentRate = rate
        tts?.setSpeechRate(rate)
    }

    fun setSpeechPitch(pitch: Float) {
        currentPitch = pitch
        tts?.setPitch(pitch)
    }

    fun setLanguage(locale: Locale) {
        currentLanguage = locale
        if (isInitialized) {
            tts?.setLanguage(locale)
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized || text.isBlank()) {
            onComplete?.invoke()
            return
        }

        stop()
        pendingOnComplete = onComplete
        val utteranceId = UUID.randomUUID().toString()
        val params = Bundle().apply {
            putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
        }

        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, utteranceId)
    }

    fun stop() {
        tts?.stop()
        _isSpeaking.value = false
        pendingOnComplete?.invoke()
        pendingOnComplete = null
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
