package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VoiceInputState {
    IDLE,
    LISTENING,
    SPEECH_DETECTED,
    PROCESSING,
    ERROR
}

class AudioInputManager(private val context: Context) {
    companion object {
        private const val TAG = "AudioInputManager"
        private const val SPEECH_RMS_THRESHOLD = 2.5f
    }

    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _voiceState = MutableStateFlow(VoiceInputState.IDLE)
    val voiceState: StateFlow<VoiceInputState> = _voiceState.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private val _partialTranscript = MutableStateFlow("")
    val partialTranscript: StateFlow<String> = _partialTranscript.asStateFlow()

    private val _recognitionError = MutableStateFlow<String?>(null)
    val recognitionError: StateFlow<String?> = _recognitionError.asStateFlow()

    private var onSpeechResultCallback: ((String) -> Unit)? = null
    private var isContinuous = false
    private var isManuallyStopped = false
    private var targetLanguage = "hi-IN"

    private fun initRecognizerIfNeeded() {
        if (speechRecognizer == null) {
            if (SpeechRecognizer.isRecognitionAvailable(context)) {
                speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                    setRecognitionListener(createListener())
                }
            } else {
                Log.w(TAG, "SpeechRecognizer is NOT available on this device")
                _recognitionError.value = "डिवाइस पर स्पीच रिकग्निशन उपलब्ध नहीं है।"
            }
        }
    }

    private fun createListener(): RecognitionListener {
        return object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "onReadyForSpeech")
                _isListening.value = true
                _voiceState.value = VoiceInputState.LISTENING
                _recognitionError.value = null
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech: user started speaking")
                _voiceState.value = VoiceInputState.SPEECH_DETECTED
                _recognitionError.value = null
            }

            override fun onRmsChanged(rmsdB: Float) {
                _rmsDb.value = rmsdB
                if (rmsdB > SPEECH_RMS_THRESHOLD && _voiceState.value == VoiceInputState.LISTENING) {
                    _voiceState.value = VoiceInputState.SPEECH_DETECTED
                }
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech: speech input completed, processing...")
                _rmsDb.value = 0f
                _voiceState.value = VoiceInputState.PROCESSING
            }

            override fun onError(error: Int) {
                Log.w(TAG, "onError: code $error")
                _rmsDb.value = 0f
                _isListening.value = false

                val message = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "ऑडियो रिकॉर्डिंग त्रुटि"
                    SpeechRecognizer.ERROR_CLIENT -> "रिकग्निशन क्लाइंट त्रुटि"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "माइक्रोफ़ोन अनुमति की आवश्यकता है"
                    SpeechRecognizer.ERROR_NETWORK -> "नेटवर्क कनेक्शन त्रुटि"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "नेटवर्क टाइमआउट"
                    SpeechRecognizer.ERROR_NO_MATCH -> "आवाज़ स्पष्ट नहीं थी, कृपया पुनः बोलें"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "पहचानकर्ता व्यस्त है"
                    SpeechRecognizer.ERROR_SERVER -> "स्पीच सर्वर त्रुटि"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "कोई आवाज़ नहीं सुनी गई"
                    else -> "स्पीच पहचान त्रुटि ($error)"
                }

                _voiceState.value = VoiceInputState.ERROR
                _recognitionError.value = message

                // Do NOT aggressively loop in background if silent or no match
                // Only if continuous mode is explicitly enabled by the user and not manually stopped
                if (isContinuous && !isManuallyStopped && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                    mainHandler.postDelayed({
                        if (isContinuous && !isManuallyStopped) {
                            startListeningInternal()
                        }
                    }, 1200)
                }
            }

            override fun onResults(results: Bundle?) {
                _rmsDb.value = 0f
                _isListening.value = false
                _voiceState.value = VoiceInputState.IDLE

                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val recognizedText = matches?.firstOrNull()?.trim()
                Log.d(TAG, "onResults: recognized '$recognizedText'")

                if (!recognizedText.isNullOrEmpty()) {
                    _partialTranscript.value = recognizedText
                    onSpeechResultCallback?.invoke(recognizedText)
                }

                // If continuous mode is explicitly enabled and not stopped, listen again after pause
                if (isContinuous && !isManuallyStopped) {
                    mainHandler.postDelayed({
                        if (isContinuous && !isManuallyStopped) {
                            startListeningInternal()
                        }
                    }, 1000)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                val partial = matches?.firstOrNull()?.trim()
                if (!partial.isNullOrEmpty()) {
                    _partialTranscript.value = partial
                    _voiceState.value = VoiceInputState.SPEECH_DETECTED
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }
    }

    /**
     * Starts listening with optional continuous mode (defaults to push-to-talk single query)
     */
    fun startListening(language: String = "hi-IN", continuous: Boolean = false, onResult: (String) -> Unit) {
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
                    // Noise and pause thresholds for mobile device usage
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1400L)
                    putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 1500L)
                }
                speechRecognizer?.startListening(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting speech recognition: ${e.message}", e)
                _voiceState.value = VoiceInputState.ERROR
                _recognitionError.value = "माइक्रोफ़ोन प्रारंभ करने में त्रुटि: ${e.message}"
            }
        }
    }

    fun pauseListening() {
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                _isListening.value = false
                _voiceState.value = VoiceInputState.IDLE
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
                _voiceState.value = VoiceInputState.IDLE
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
