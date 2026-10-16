package com.example.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.speech.tts.Voice
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import java.util.UUID

object SpeechNormalizer {
    private val EMOJI_REGEX = Regex("[\\p{So}\\p{Cn}\\p{Cs}\\p{Sk}]+")
    private val MARKDOWN_BOLD_ITALIC = Regex("[*_~`#]")
    private val BULLET_REGEX = Regex("^[\\s]*[-•*\\d+]\\.?\\s+", RegexOption.MULTILINE)
    private val MULTI_SPACE_REGEX = Regex("\\s+")

    // Comprehensive phonetic dictionary converting English/Hinglish UI & tech terms to natural Hindi speech
    private val PHONETIC_MAP = listOf(
        // Common Android & phone UI terms
        Regex("(?i)\\bwi-?fi\\b") to "वाइफ़ाई",
        Regex("(?i)\\bbluetooth\\b") to "ब्लूटूथ",
        Regex("(?i)\\bwhatsapp\\b") to "व्हाट्सएप",
        Regex("(?i)\\byoutube\\b") to "यूट्यूब",
        Regex("(?i)\\bgoogle\\b") to "गूगल",
        Regex("(?i)\\bsettings?\\b") to "सेटिंग्स",
        Regex("(?i)\\bapp\\b") to "ऐप",
        Regex("(?i)\\bapps\\b") to "ऐप्स",
        Regex("(?i)\\bapk\\b") to "एपीके",
        Regex("(?i)\\bclick\\b") to "टैप",
        Regex("(?i)\\btap\\b") to "टैप",
        Regex("(?i)\\bok\\b") to "ओके",
        Regex("(?i)\\botp\\b") to "ओटीपी",
        Regex("(?i)\\bnotification\\b") to "नोटिफिकेशन",
        Regex("(?i)\\bnotifications\\b") to "नोटिफिकेशन्स",
        Regex("(?i)\\bicon\\b") to "आइकन",
        Regex("(?i)\\bscreen\\b") to "स्क्रीन",
        Regex("(?i)\\bscreenshot\\b") to "स्क्रीनशॉट",
        Regex("(?i)\\bbutton\\b") to "बटन",
        Regex("(?i)\\bdownload(ing)?\\b") to "डाउनलोड",
        Regex("(?i)\\binstall(ed)?\\b") to "इंस्टॉल",
        Regex("(?i)\\buninstall\\b") to "अनइंस्टॉल",
        Regex("(?i)\\bupdate\\b") to "अपडेट",
        Regex("(?i)\\bpermission\\b") to "परमिशन",
        Regex("(?i)\\bpermissions\\b") to "परमिशन्स",
        Regex("(?i)\\ballow\\b") to "अलाउ",
        Regex("(?i)\\bdeny\\b") to "डिनाय",
        Regex("(?i)\\bpassword\\b") to "पासवर्ड",
        Regex("(?i)\\bpin\\b") to "पिन",
        Regex("(?i)\\blogin\\b") to "लॉगिन",
        Regex("(?i)\\blogout\\b") to "लॉगआउट",
        Regex("(?i)\\bsign[- ]?in\\b") to "साइन इन",
        Regex("(?i)\\bsign[- ]?out\\b") to "साइन आउट",
        Regex("(?i)\\baccount\\b") to "अकाउंट",
        Regex("(?i)\\bprofile\\b") to "प्रोफ़ाइल",
        Regex("(?i)\\berror\\b") to "एरर",
        Regex("(?i)\\bsearch\\b") to "सर्च",
        Regex("(?i)\\bopen\\b") to "ओपन",
        Regex("(?i)\\bclose\\b") to "क्लोज़",
        Regex("(?i)\\bselect\\b") to "सिलेक्ट",
        Regex("(?i)\\boption\\b") to "ऑप्शन",
        Regex("(?i)\\boptions\\b") to "ऑप्शन्स",
        Regex("(?i)\\bscroll\\b") to "स्क्रॉल",
        Regex("(?i)\\bswipe\\b") to "स्वाइप",
        Regex("(?i)\\bmenu\\b") to "मेन्यू",
        Regex("(?i)\\bnext\\b") to "नेक्स्ट",
        Regex("(?i)\\bcancel\\b") to "कैंसिल",
        Regex("(?i)\\bdone\\b") to "डन",
        Regex("(?i)\\bconfirm\\b") to "कन्फर्म",
        Regex("(?i)\\bcontinue\\b") to "कंटिन्यू",
        Regex("(?i)\\bback\\b") to "बैक",
        Regex("(?i)\\bhome\\b") to "होम",
        Regex("(?i)\\bshare\\b") to "शेयर",
        Regex("(?i)\\bcamera\\b") to "कैमरा",
        Regex("(?i)\\bgallery\\b") to "गैलरी",
        Regex("(?i)\\bfile\\b") to "फ़ाइल",
        Regex("(?i)\\bfiles\\b") to "फ़ाइल्स",
        Regex("(?i)\\bstorage\\b") to "स्टोरेज",
        Regex("(?i)\\bclear cache\\b") to "क्लियर कैश",
        Regex("(?i)\\bcache\\b") to "कैश",
        Regex("(?i)\\bmic(rophone)?\\b") to "माइक",
        Regex("(?i)\\bspeaker\\b") to "स्पीकर",
        Regex("(?i)\\bvolume\\b") to "वॉल्यूम",
        Regex("(?i)\\blink\\b") to "लिंक",
        Regex("(?i)\\bonline\\b") to "ऑनलाइन",
        Regex("(?i)\\boffline\\b") to "ऑफलाइन",
        Regex("(?i)\\bmessage\\b") to "मैसेज",
        Regex("(?i)\\bmessages\\b") to "मैसेजेस",
        Regex("(?i)\\bchat\\b") to "चैट",
        Regex("(?i)\\bcall\\b") to "कॉल",
        Regex("(?i)\\bcontact\\b") to "कॉन्टैक्ट",
        Regex("(?i)\\bcontacts\\b") to "कॉन्टैक्ट्स",
        Regex("(?i)\\bnetwork\\b") to "नेटवर्क",
        Regex("(?i)\\binternet\\b") to "इंटरनेट",
        Regex("(?i)\\bdata\\b") to "डेटा",
        Regex("(?i)\\bbattery\\b") to "बैटरी",
        Regex("(?i)\\bdevice\\b") to "डिवाइस",
        Regex("(?i)\\bphone\\b") to "फ़ोन",
        Regex("(?i)\\bmobile\\b") to "मोबाइल",
        Regex("(?i)\\bsystem\\b") to "सिस्टम",
        Regex("(?i)\\bguide\\b") to "गाइड",
        Regex("(?i)\\bai\\b") to "एआई",
        Regex("(?i)\\blive\\b") to "लाइव",
        Regex("(?i)\\baudio\\b") to "ऑडियो",
        Regex("(?i)\\bvideo\\b") to "वीडियो",
        Regex("(?i)\\bphoto\\b") to "फ़ोटो",
        Regex("(?i)\\bphotos\\b") to "फ़ोटोज़",
        Regex("(?i)\\bimage\\b") to "इमेज",
        Regex("(?i)\\bimages\\b") to "इमेजेस",
        Regex("(?i)\\bdisplay\\b") to "डिस्प्ले",
        Regex("(?i)\\benable\\b") to "इनेबल",
        Regex("(?i)\\bdisable\\b") to "डिसेबल",
        Regex("(?i)\\brestart\\b") to "रीस्टार्ट",
        Regex("(?i)\\breboot\\b") to "रीबूट",
        Regex("(?i)\\bturn on\\b") to "चालू करें",
        Regex("(?i)\\bturn off\\b") to "बंद करें",
        Regex("(?i)\\btoggle\\b") to "टॉगल",
        Regex("(?i)\\bswitch\\b") to "स्विच",
        Regex("(?i)\\bcard\\b") to "कार्ड",
        Regex("(?i)\\btab\\b") to "टैब",
        Regex("(?i)\\bupi\\b") to "यूपीआई",
        Regex("(?i)\\bsim\\b") to "सिम",
        Regex("(?i)\\bram\\b") to "रैम",
        Regex("(?i)\\bgb\\b") to "जीबी",
        Regex("(?i)\\bmb\\b") to "एमबी",
        Regex("(?i)\\bpdf\\b") to "पीडीएफ",
        Regex("(?i)\\burl\\b") to "यूआरएल",
        Regex("(?i)\\bsms\\b") to "एसएमएस",
        Regex("(?i)\\bgps\\b") to "जीपीएस",
        Regex("(?i)\\bqr code\\b") to "क्यूआर कोड"
    )

    fun normalizeForSpeech(text: String): String {
        var cleaned = text
            // Strip code blocks and inline code
            .replace(Regex("```[a-zA-Z]*\\n[\\s\\S]*?```"), "")
            .replace(Regex("`[^`]*`"), "")
            // Strip markdown formatting symbols
            .replace(MARKDOWN_BOLD_ITALIC, " ")
            .replace(BULLET_REGEX, "")
            .replace(Regex("\\[([^\\]]+)\\]\\([^\\)]+\\)"), "$1")
            .replace(Regex("https?://\\S+"), "वेबसाइट लिंक")
            // Strip brackets, colons and unwanted punctuation symbols
            .replace(Regex("[()\\[\\]{}<>|~^#]"), " ")
            .replace(Regex("(?<!\\d):(?=\\s)"), "। ")
            .replace("%", " प्रतिशत ")
            .replace("&", " और ")
            .replace("+", " प्लस ")
            // Strip emojis which cause TTS to awkwardly read emoji descriptions
            .replace(EMOJI_REGEX, " ")

        // Smooth Hinglish and common tech terms into natural Hindi phonetic pronunciations
        for ((pattern, replacement) in PHONETIC_MAP) {
            cleaned = cleaned.replace(pattern, replacement)
        }

        // Normalize multiple punctuations into clean pause marks
        cleaned = cleaned
            .replace(Regex("[।!?.]{2,}"), "। ")
            .replace(Regex("\\s+([।,])"), "$1")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()

        return cleaned
    }
}

class AudioOutputManager(private val context: Context) : TextToSpeech.OnInitListener {
    companion object {
        private const val TAG = "AudioOutputManager"
        private const val GOOGLE_TTS_PACKAGE = "com.google.android.tts"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _ttsStatus = MutableStateFlow("प्राकृतिक मानवीय आवाज़ प्रारंभ हो रही है...")
    val ttsStatus: StateFlow<String> = _ttsStatus.asStateFlow()

    private var pendingOnComplete: (() -> Unit)? = null
    private var currentLanguage = Locale.forLanguageTag("hi-IN")
    private var currentPersona = "natural_female"
    private var currentRate = 1.0f
    private var currentPitch = 1.0f
    private var speechWatchdogRunnable: Runnable? = null

    init {
        initTtsEngine()
    }

    private fun initTtsEngine() {
        val hasGoogleTts = isPackageInstalled(GOOGLE_TTS_PACKAGE, context)
        tts = if (hasGoogleTts) {
            Log.i(TAG, "Initializing with Google Neural TTS engine ($GOOGLE_TTS_PACKAGE)")
            TextToSpeech(context.applicationContext, this, GOOGLE_TTS_PACKAGE)
        } else {
            Log.i(TAG, "Google TTS not detected, using system default TTS engine")
            TextToSpeech(context.applicationContext, this)
        }
    }

    private fun isPackageInstalled(packageName: String, context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: Exception) {
            false
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            setupTtsEngine()
        } else {
            Log.w(TAG, "Preferred TTS engine init failed with status $status, falling back to system default")
            tts = TextToSpeech(context.applicationContext) { fallbackStatus ->
                if (fallbackStatus == TextToSpeech.SUCCESS) {
                    setupTtsEngine()
                } else {
                    _ttsStatus.value = "TextToSpeech प्रारंभ नहीं हो सका"
                }
            }
        }
    }

    private fun setupTtsEngine() {
        // Configure assistant audio attributes for speech DSP optimization and noise reduction
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANT)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        tts?.setAudioAttributes(audioAttributes)

        selectBestNaturalVoice(currentLanguage, currentPersona)

        tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                _isSpeaking.value = true
            }

            override fun onDone(utteranceId: String?) {
                if (utteranceId != null && utteranceId.startsWith("final_")) {
                    finishSpeaking()
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId != null && utteranceId.startsWith("final_")) {
                    finishSpeaking()
                }
            }

            override fun onError(utteranceId: String?, errorCode: Int) {
                if (utteranceId != null && utteranceId.startsWith("final_")) {
                    finishSpeaking()
                }
            }
        })

        tts?.setSpeechRate(currentRate)
        tts?.setPitch(currentPitch)
        isInitialized = true
    }

    private fun finishSpeaking() {
        abandonAudioFocus()
        _isSpeaking.value = false
        speechWatchdogRunnable?.let {
            mainHandler.removeCallbacks(it)
            speechWatchdogRunnable = null
        }
        mainHandler.post {
            val cb = pendingOnComplete
            pendingOnComplete = null
            cb?.invoke()
        }
    }

    private fun requestAudioFocus(): Boolean {
        return try {
            if (audioManager == null) return false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val playbackAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(false)
                    .build()
                audioFocusRequest = request
                audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            } else {
                @Suppress("DEPRECATION")
                audioManager.requestAudioFocus(
                    null,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK
                ) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus request failed: ${e.message}")
            false
        }
    }

    private fun abandonAudioFocus() {
        try {
            if (audioManager == null) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
                audioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                audioManager.abandonAudioFocus(null)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Audio focus release failed: ${e.message}")
        }
    }

    /**
     * Intelligently selects the highest-fidelity natural neural voice (WaveNet/Google Neural)
     * matching the preferred language and chosen persona (female vs male).
     */
    fun selectBestNaturalVoice(locale: Locale, voicePersona: String = currentPersona) {
        currentLanguage = locale
        currentPersona = voicePersona

        try {
            val voices = tts?.voices
            val targetLang = locale.language
            val targetCountry = locale.country

            if (!voices.isNullOrEmpty()) {
                val matchingVoices = voices.filter { v ->
                    val vLang = v.locale.language
                    val notInstalled = v.features?.contains("notInstalled") == true
                    !notInstalled && (vLang.equals(targetLang, ignoreCase = true) ||
                            (targetLang == "hi" && v.locale.toLanguageTag().startsWith("hi", ignoreCase = true)))
                }

                if (matchingVoices.isNotEmpty()) {
                    val isMale = voicePersona == "natural_male"

                    val bestVoice = matchingVoices.maxWithOrNull(
                        compareBy<Voice> { v ->
                            var score = 0
                            val name = v.name.lowercase()

                            // Quality tier
                            when (v.quality) {
                                Voice.QUALITY_VERY_HIGH -> score += 60
                                Voice.QUALITY_HIGH -> score += 40
                                Voice.QUALITY_NORMAL -> score += 15
                            }

                            // Google neural/network voices are the most natural & human-sounding
                            if (name.contains("network")) score += 50
                            if (name.contains("natural")) score += 40

                            // Specific Google Hindi neural voice models
                            // hie/hic = Warm natural female
                            // hid/hif = Clear natural male
                            if (isMale) {
                                if (name.contains("hid")) score += 45
                                if (name.contains("hif")) score += 40
                                if (name.contains("male") && !name.contains("female")) score += 35
                            } else {
                                if (name.contains("hie")) score += 45
                                if (name.contains("hic")) score += 40
                                if (name.contains("female")) score += 35
                            }

                            if (name.contains("google")) score += 20
                            if (v.locale.country.equals(targetCountry, ignoreCase = true)) score += 15

                            score
                        }
                    )

                    if (bestVoice != null) {
                        Log.i(TAG, "Selected natural human-like voice: ${bestVoice.name} (quality=${bestVoice.quality}, persona=$voicePersona)")
                        tts?.voice = bestVoice
                        val personaLabel = if (isMale) "पुरुष" else "महिला"
                        _ttsStatus.value = "प्राकृतिक $personaLabel आवाज़ सक्रिय है (${bestVoice.name})"
                        return
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Voice selection query error: ${e.message}")
        }

        // Fallback to standard setLanguage
        val result = tts?.setLanguage(locale)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            val fallback = Locale.forLanguageTag("hi")
            val fallbackResult = tts?.setLanguage(fallback)
            if (fallbackResult == TextToSpeech.LANG_MISSING_DATA || fallbackResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.language = Locale.getDefault()
                _ttsStatus.value = "डिफ़ॉल्ट आवाज़ सक्रिय है"
            } else {
                _ttsStatus.value = "हिन्दी आवाज़ सक्रिय है"
            }
        } else {
            _ttsStatus.value = "प्राकृतिक हिन्दी आवाज़ तैयार है"
        }
    }

    fun updateSettings(language: Locale, rate: Float, pitch: Float, persona: String) {
        currentRate = rate
        currentPitch = pitch
        tts?.setSpeechRate(rate)
        tts?.setPitch(pitch)
        selectBestNaturalVoice(language, persona)
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
            selectBestNaturalVoice(locale, currentPersona)
        }
    }

    fun setVoicePersona(persona: String) {
        currentPersona = persona
        if (isInitialized) {
            selectBestNaturalVoice(currentLanguage, persona)
        }
    }

    /**
     * Speaks text using low-latency sentence-by-sentence streaming with natural human-like
     * prosody pauses between clauses.
     */
    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized || text.isBlank()) {
            onComplete?.invoke()
            return
        }

        stop()
        pendingOnComplete = onComplete

        val normalized = SpeechNormalizer.normalizeForSpeech(text)
        if (normalized.isBlank()) {
            val cb = pendingOnComplete
            pendingOnComplete = null
            cb?.invoke()
            return
        }

        val sentences = splitIntoSentences(normalized)
        if (sentences.isEmpty()) {
            val cb = pendingOnComplete
            pendingOnComplete = null
            cb?.invoke()
            return
        }

        requestAudioFocus()
        _isSpeaking.value = true
        val finalUtteranceId = "final_${UUID.randomUUID()}"

        // Safety watchdog: auto-clear if speech engine stalls or encounters internal error
        speechWatchdogRunnable?.let { mainHandler.removeCallbacks(it) }
        val timeoutMs = (normalized.length * 120L + 6000L).coerceIn(6000L, 35000L)
        speechWatchdogRunnable = Runnable {
            if (_isSpeaking.value) {
                Log.w(TAG, "TTS speech timed out after ${timeoutMs}ms, forcing completion")
                stop()
            }
        }.also {
            mainHandler.postDelayed(it, timeoutMs)
        }

        // Stream sentence-by-sentence for instant start latency & natural breath rhythm
        for (i in sentences.indices) {
            val sentence = sentences[i]
            val isFirst = (i == 0)
            val isLast = (i == sentences.size - 1)
            val queueMode = if (isFirst) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD
            val utteranceId = if (isLast) finalUtteranceId else "chunk_${UUID.randomUUID()}"

            val params = Bundle().apply {
                putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, utteranceId)
            }

            val result = tts?.speak(sentence, queueMode, params, utteranceId)
            if (result != TextToSpeech.SUCCESS && isFirst) {
                Log.w(TAG, "tts.speak failed on initial sentence chunk ($result), stopping")
                stop()
                return
            }

            // Insert a gentle 150ms breath pause between sentences for human-like cadence
            if (!isLast) {
                tts?.playSilentUtterance(150L, TextToSpeech.QUEUE_ADD, "pause_${UUID.randomUUID()}")
            }
        }
    }

    /**
     * Splits normalized text into natural phrasing chunks based on sentence terminators
     * and long comma-separated clauses.
     */
    private fun splitIntoSentences(text: String): List<String> {
        val rawChunks = text.split(Regex("(?<=[।?!\\n])\\s+"))
        val result = mutableListOf<String>()

        for (chunk in rawChunks) {
            val trimmed = chunk.trim()
            if (trimmed.isEmpty()) continue

            // If a clause is very long (> 100 chars), further break on commas for natural breath pauses
            if (trimmed.length > 100 && trimmed.contains(", ")) {
                val subParts = trimmed.split(Regex("(?<=,)\\s+"))
                for (part in subParts) {
                    val subTrimmed = part.trim()
                    if (subTrimmed.isNotEmpty()) {
                        result.add(subTrimmed)
                    }
                }
            } else {
                result.add(trimmed)
            }
        }

        return if (result.isNotEmpty()) result else listOf(text.trim())
    }

    fun stop() {
        speechWatchdogRunnable?.let {
            mainHandler.removeCallbacks(it)
            speechWatchdogRunnable = null
        }
        try {
            tts?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping TTS: ${e.message}")
        }
        abandonAudioFocus()
        _isSpeaking.value = false
        val cb = pendingOnComplete
        pendingOnComplete = null
        cb?.invoke()
    }

    fun release() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}
