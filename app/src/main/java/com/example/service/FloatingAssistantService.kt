package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import com.example.LiveScreenApp
import com.example.MainActivity
import com.example.R
import com.example.ai.GeminiResult
import com.example.ui.components.AssistantState
import com.example.ui.floating.FloatingBubbleContent
import com.example.ui.floating.FloatingPanelContent
import com.example.ui.theme.MyApplicationTheme
import com.example.voice.VoiceInputState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.hypot

class FloatingAssistantService : Service() {
    companion object {
        private const val TAG = "FloatingAssistantService"
        const val CHANNEL_ID = "live_screen_floating_channel"
        const val NOTIFICATION_ID = 2002
        const val ACTION_START = "com.example.service.FLOATING_START"
        const val ACTION_STOP = "com.example.service.FLOATING_STOP"
        const val ACTION_SCREEN_CAPTURE_GRANTED = "com.example.service.ACTION_SCREEN_CAPTURE_GRANTED"

        private val _isRunning = MutableStateFlow(false)
        val isRunning = _isRunning

        fun startService(context: Context) {
            val intent = Intent(context, FloatingAssistantService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, FloatingAssistantService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null

    private var bubbleContainer: FrameLayout? = null
    private var panelContainer: FrameLayout? = null
    private val lifecycleOwner = OverlayLifecycleOwner()

    private lateinit var bubbleParams: WindowManager.LayoutParams
    private lateinit var panelParams: WindowManager.LayoutParams

    private val isPanelOpen = MutableStateFlow(false)
    private val isMicMuted = MutableStateFlow(false)
    private val assistantState = MutableStateFlow(AssistantState.IDLE)
    private val lastTranscript = MutableStateFlow("")
    private val lastResponse = MutableStateFlow("")
    private val errorMessage = MutableStateFlow<String?>(null)

    private var currentSessionId = UUID.randomUUID().toString()

    private val captureBroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == ACTION_SCREEN_CAPTURE_GRANTED) {
                Log.d(TAG, "Screen capture broadcast received")
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lifecycleOwner.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createNotificationChannel()

        val filter = IntentFilter(ACTION_SCREEN_CAPTURE_GRANTED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(captureBroadcastReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(captureBroadcastReceiver, filter)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopFloatingAssistant()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                _isRunning.value = true
                val notification = buildNotification()

                // Foreground service startup with microphone type (or standard for older APIs)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    try {
                        startForeground(
                            NOTIFICATION_ID,
                            notification,
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "startForeground error: ${e.message}, falling back")
                        startForeground(NOTIFICATION_ID, notification)
                    }
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }

                initOverlayWindows()
            }
        }
        return START_STICKY
    }

    private fun initOverlayWindows() {
        if (bubbleContainer != null) return

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // 1. Bubble Layout Params (without FLAG_LAYOUT_NO_LIMITS for stable coordinate tracking)
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - 180).coerceAtLeast(20)
            y = 350
        }

        // 2. Panel Layout Params
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 200
        }

        // FrameLayout wrapper for the draggable Bubble
        val container = FrameLayout(this)
        val composeBubble = ComposeView(this).apply {
            lifecycleOwner.attachToComposeView(this)
            setContent {
                MyApplicationTheme {
                    val app = LiveScreenApp.instance
                    val isCapturing by app.screenCaptureManager.isCapturing.collectAsState()
                    val state by assistantState.collectAsState()

                    FloatingBubbleContent(
                        assistantState = state,
                        isCapturing = isCapturing
                    )
                }
            }
        }
        container.addView(composeBubble)
        setupBubbleTouchListener(container)
        bubbleContainer = container

        try {
            windowManager?.addView(container, bubbleParams)
            Log.d(TAG, "Bubble overlay attached to WindowManager successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error adding bubble overlay: ${e.message}", e)
        }

        // FrameLayout wrapper for the Floating Panel
        val panelCont = FrameLayout(this)
        val composePanel = ComposeView(this).apply {
            lifecycleOwner.attachToComposeView(this)
            setContent {
                MyApplicationTheme {
                    val app = LiveScreenApp.instance
                    val isCapturing by app.screenCaptureManager.isCapturing.collectAsState()
                    val latestFrame by app.screenCaptureManager.latestFrame.collectAsState()
                    val state by assistantState.collectAsState()
                    val muted by isMicMuted.collectAsState()
                    val rms by app.audioInputManager.rmsDb.collectAsState()
                    val transcript by lastTranscript.collectAsState()
                    val responseText by lastResponse.collectAsState()
                    val errorMsg by errorMessage.collectAsState()
                    val voiceState by app.audioInputManager.voiceState.collectAsState()

                    FloatingPanelContent(
                        assistantState = state,
                        voiceState = voiceState,
                        isCapturing = isCapturing,
                        isMicMuted = muted,
                        latestFrame = latestFrame,
                        rmsDb = rms,
                        lastTranscript = transcript,
                        lastResponse = responseText,
                        errorMessage = errorMsg,
                        onToggleScreenShare = { toggleScreenSharing() },
                        onToggleMic = { toggleMicrophone() },
                        onPushToTalk = { startPushToTalk() },
                        onStopListening = { stopVoiceListening() },
                        onAskScreenNow = { askScreenQuestion("मेरी स्क्रीन पर अभी क्या दिख रहा है और मुझे क्या करना चाहिए?") },
                        onInterruptSpeech = { interruptSpeech() },
                        onMinimize = { hidePanel() },
                        onEndSession = { stopFloatingAssistant() },
                        onRetry = {
                            errorMessage.value = null
                            askScreenQuestion("मेरी स्क्रीन का पुनः विश्लेषण करें")
                        }
                    )
                }
            }
        }
        panelCont.addView(composePanel)

        // Dismiss panel on outside touch
        panelCont.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_OUTSIDE) {
                hidePanel()
                true
            } else {
                false
            }
        }

        panelContainer = panelCont
    }

    private fun setupBubbleTouchListener(container: FrameLayout) {
        val touchSlop = ViewConfiguration.get(this).scaledTouchSlop
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        container.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = bubbleParams.x
                    initialY = bubbleParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isDragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (hypot(dx.toDouble(), dy.toDouble()) > touchSlop) {
                        isDragging = true
                    }
                    if (isDragging) {
                        val metrics = resources.displayMetrics
                        val maxX = metrics.widthPixels - 120
                        val maxY = metrics.heightPixels - 150
                        bubbleParams.x = (initialX + dx).coerceIn(0, maxX)
                        bubbleParams.y = (initialY + dy).coerceIn(40, maxY)
                        try {
                            windowManager?.updateViewLayout(bubbleContainer, bubbleParams)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating bubble layout: ${e.message}")
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isDragging) {
                        togglePanel()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun togglePanel() {
        if (isPanelOpen.value) {
            hidePanel()
        } else {
            showPanel()
        }
    }

    private fun showPanel() {
        if (panelContainer != null && !isPanelOpen.value) {
            val metrics = resources.displayMetrics
            val panelWidth = (330 * metrics.density).toInt()
            panelParams.x = (bubbleParams.x - panelWidth / 2).coerceIn(16, (metrics.widthPixels - panelWidth - 16).coerceAtLeast(16))
            panelParams.y = (bubbleParams.y + 130).coerceIn(80, (metrics.heightPixels - 560).coerceAtLeast(80))

            try {
                windowManager?.addView(panelContainer, panelParams)
                isPanelOpen.value = true
                Log.d(TAG, "Floating panel displayed")
            } catch (e: Exception) {
                Log.e(TAG, "Error adding panel overlay: ${e.message}", e)
            }
        }
    }

    private fun hidePanel() {
        if (panelContainer != null && isPanelOpen.value) {
            try {
                windowManager?.removeView(panelContainer)
                Log.d(TAG, "Floating panel hidden")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing panel overlay: ${e.message}")
            }
            isPanelOpen.value = false
        }
    }

    private fun startPushToTalk() {
        val app = LiveScreenApp.instance
        if (assistantState.value == AssistantState.SPEAKING) {
            interruptSpeech()
        }
        assistantState.value = AssistantState.LISTENING
        app.audioInputManager.startListening(
            language = "hi-IN",
            continuous = false
        ) { speechResult ->
            if (speechResult.isNotBlank() && assistantState.value != AssistantState.THINKING) {
                lastTranscript.value = speechResult
                askScreenQuestion(speechResult)
            }
        }
    }

    private fun stopVoiceListening() {
        val app = LiveScreenApp.instance
        app.audioInputManager.stopListening()
        assistantState.value = AssistantState.IDLE
    }

    private fun askScreenQuestion(question: String) {
        serviceScope.launch {
            val app = LiveScreenApp.instance
            assistantState.value = AssistantState.THINKING
            app.audioInputManager.stopListening()

            app.chatRepository.addMessage(
                sessionId = currentSessionId,
                sender = "user",
                text = question,
                hindiText = question,
                hasScreenContext = app.screenCaptureManager.isCapturing.value
            )

            val currentBitmap = app.screenCaptureManager.getLatestFrame()
            val settings = app.settingsRepository.settingsFlow.first()

            val result = app.geminiClient.analyzeScreenAndRespond(
                apiKey = settings.effectiveApiKey,
                screenBitmap = currentBitmap,
                userQuestion = question,
                explanationDetail = settings.explanationDetail,
                language = settings.preferredLanguage
            )

            when (result) {
                is GeminiResult.Success -> {
                    errorMessage.value = null
                    lastResponse.value = result.text
                    app.chatRepository.addMessage(
                        sessionId = currentSessionId,
                        sender = "assistant",
                        text = result.text,
                        hindiText = result.text,
                        hasScreenContext = currentBitmap != null
                    )

                    if (settings.autoSpeakResponses) {
                        assistantState.value = AssistantState.SPEAKING
                        app.audioOutputManager.speak(result.text) {
                            assistantState.value = AssistantState.IDLE
                        }
                    } else {
                        assistantState.value = AssistantState.IDLE
                    }
                }
                is GeminiResult.Error -> {
                    errorMessage.value = result.errorMessage
                    assistantState.value = AssistantState.IDLE
                }
            }
        }
    }

    private fun toggleScreenSharing() {
        val app = LiveScreenApp.instance
        if (app.screenCaptureManager.isCapturing.value) {
            app.screenCaptureManager.stopCapture()
            ScreenGuideService.stopService(this)
        } else {
            ScreenCapturePromptActivity.launchPrompt(this)
        }
    }

    private fun toggleMicrophone() {
        isMicMuted.value = !isMicMuted.value
        val app = LiveScreenApp.instance
        if (isMicMuted.value) {
            app.audioInputManager.stopListening()
            assistantState.value = AssistantState.IDLE
        }
    }

    private fun interruptSpeech() {
        val app = LiveScreenApp.instance
        app.audioOutputManager.stop()
        assistantState.value = AssistantState.IDLE
    }

    private fun stopFloatingAssistant() {
        _isRunning.value = false
        val app = LiveScreenApp.instance

        // Do NOT stop active screen capture unless explicitly closed by the user
        // Stop audio and remove overlay views cleanly
        app.audioInputManager.stopListening()
        app.audioOutputManager.stop()

        hidePanel()
        if (bubbleContainer != null) {
            try {
                windowManager?.removeView(bubbleContainer)
            } catch (e: Exception) {
                // ignore
            }
            bubbleContainer = null
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        _isRunning.value = false
        runCatching { unregisterReceiver(captureBroadcastReceiver) }
        hidePanel()
        if (bubbleContainer != null) {
            try {
                windowManager?.removeView(bubbleContainer)
            } catch (e: Exception) {
                // ignore
            }
            bubbleContainer = null
        }
        lifecycleOwner.onDestroy()
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Live Screen Floating Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "फ़्लोटिंग एआई सहायक ओवरले की सक्रिय स्थिति"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, FloatingAssistantService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Live Screen AI Guide फ़्लोटिंग मोड सक्रिय है")
            .setContentText("स्क्रीन पर फ़्लोटिंग बबल उपलब्ध है • किसी भी ऐप पर सहायता पाएं")
            .setSmallIcon(R.drawable.app_launcher_icon_1791691117930)
            .setContentIntent(pendingOpen)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "सहायक बंद करें",
                pendingStop
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
