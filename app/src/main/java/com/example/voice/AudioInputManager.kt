package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AudioInputManager(private val context: Context) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _recognitionError = MutableStateFlow<String?>(null)
    val recognitionError: StateFlow<String?> = _recognitionError.asStateFlow()

    private var onSpeechResultCallback: ((String) -> Unit)? = null
    private var isContinuous = true
    private var isManuallyStopped = false
    private var targetLanguage = "hi-IN"

    private fun initRecognizerIfNeeded() {
        if (speechRecognizer == null) {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
            }
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                _isListening.value = true
                _recognitionError.value = null
            }

            override fun onBeginningOfSpeech() {
                _recognitionError.value = null
            }

            override fun onRmsChanged(rmsdB: Float) {
                _rmsDb.value = rmsdB
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                _rmsDb.value = 0f
            }

            override fun onError(error: Int) {
                _rmsDb.value = 0f
                _isListening.value = false

                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "ऑडियो रिकॉर्डिंग त्रुटि"
                    SpeechRecognizer.ERROR_CLIENT -> "क्लाइंट त्रुटि"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "माइक्रोफ़ोन अनुमति आवश्यक है"
                    SpeechRecognizer.ERROR_NETWORK -> "नेटवर्क कनेक्शन त्रुटि"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "नेटवर्क टाइमआउट"
                    SpeechRecognizer.ERROR_NO_MATCH -> "आवाज़ समझ नहीं आई, कृपया पुनः बोलें"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "पहचानकर्ता व्यस्त है"
                    SpeechRecognizer.ERROR_SERVER -> "सर्वर त्रुटि"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "कोई आवाज़ नहीं सुनी गई"
                    else -> "अज्ञात स्पीच त्रुटि ($error)"
                }

                // If continuous mode is on and error was timeout or no match, restart after slight pause
                if (isContinuous && !isManuallyStopped && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    mainHandler.postDelayed({
                        if (isContinuous && !isManuallyStopped) {
                            startListeningInternal()
                        }
                    }, 600)
                } else {
                    _recognitionError.value = message
                }
            }

            override fun onResults(results: Bundle?) {
                _rmsDb.value = 0f
                _isListening.value = false
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()?.trim()

                if (!recognizedText.isNullOrEmpty()) {
                    _partialTranscript.value = recognizedText
                    onSpeechResultCallback?.invoke(recognizedText)
                }

                // Auto restart in continuous mode
                if (isContinuous && !isManuallyStopped) {
                    mainHandler.postDelayed({
                        if (isContinuous && !isManuallyStopped) {
                            startListeningInternal()
                        }
                    }, 500)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim()
                if (!partial.isNullOrEmpty()) {
                    _partialTranscript.value = partial
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    fun startListening(language: String = "hi-IN", continuous: Boolean = true, onResult: (String) -> Unit) {
        targetLanguage = language
        isContinuous = continuous
        isManuallyStopped = false
        onSpeechResultCallback = onResult
        _partialTranscript.value = ""
        _recognitionError.value = null

        startListeningInternal()
    }

    private fun startListeningInternal() {
        mainHandler.post {
            try {
                initRecognizerIfNeeded()
                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE, targetLanguage)
                    putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, targetLanguage)
                    putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, targetLanguage)
                    putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                    putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                _recognitionError.value = "माइक्रोफ़ोन प्रारंभ करने में त्रुटि: ${e.message}"
            }
        }
    }

    fun pauseListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                _isListening.value = false
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun resumeListening() {
        if (!isManuallyStopped) {
            startListeningInternal()
        }
    }

    fun stopListening() {
        isManuallyStopped = true
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
                _isListening.value = false
                _rmsDb.value = 0f
            } catch (e: Exception) {
                // ignore
            }
        }
    }

    fun release() {
        stopListening()
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                // ignore
            }
        }
    }
}
