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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
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
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var windowManager: WindowManager? = null

    private var bubbleView: ComposeView? = null
    private var panelView: ComposeView? = null
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
                // Screen capture started via ScreenCapturePromptActivity
            }
        }
    }

    companion object {
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    )
                } else {
                    startForeground(NOTIFICATION_ID, notification)
                }
                initOverlayWindows()
                startVoiceListening()
            }
        }
        return START_STICKY
    }

    private fun initOverlayWindows() {
        if (bubbleView != null) return

        val displayMetrics = resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        // 1. Bubble Layout Params
        bubbleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = screenWidth - 190
            y = 350
        }

        // 2. Panel Layout Params
        panelParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (screenWidth - 340 * displayMetrics.density.toInt()).coerceAtLeast(20)
            y = 200
        }

        // Create Bubble View
        val bubble = ComposeView(this).apply {
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

        setupBubbleTouchListener(bubble)
        bubbleView = bubble
        windowManager?.addView(bubble, bubbleParams)

        // Create Panel View
        val panel = ComposeView(this).apply {
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

                    FloatingPanelContent(
                        assistantState = state,
                        isCapturing = isCapturing,
                        isMicMuted = muted,
                        latestFrame = latestFrame,
                        rmsDb = rms,
                        lastTranscript = transcript,
                        lastResponse = responseText,
                        errorMessage = errorMsg,
                        onToggleScreenShare = { toggleScreenSharing() },
                        onToggleMic = { toggleMicrophone() },
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
        panelView = panel
    }

    private fun setupBubbleTouchListener(view: View) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isDragging = false

        view.setOnTouchListener { _, event ->
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
                    val dx = event.rawX - initialTouchX
                    val dy = event.rawY - initialTouchY
                    if (hypot(dx, dy) > 12f) {
                        isDragging = true
                    }
                    if (isDragging) {
                        bubbleParams.x = (initialX + dx).toInt()
                        bubbleParams.y = (initialY + dy).toInt()
                        windowManager?.updateViewLayout(bubbleView, bubbleParams)
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
        if (panelView != null && !isPanelOpen.value) {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            // Position panel conveniently near the bubble or centered
            panelParams.x = (bubbleParams.x - 280).coerceIn(20, screenWidth - 350)
            panelParams.y = bubbleParams.y.coerceIn(100, screenHeight - 500)

            windowManager?.addView(panelView, panelParams)
            isPanelOpen.value = true
        }
    }

    private fun hidePanel() {
        if (panelView != null && isPanelOpen.value) {
            windowManager?.removeView(panelView)
            isPanelOpen.value = false
        }
    }

    private fun startVoiceListening() {
        val app = LiveScreenApp.instance
        if (!isMicMuted.value) {
            app.audioInputManager.startListening(
                language = "hi-IN",
                continuous = true
            ) { speechResult ->
                if (speechResult.isNotBlank() && assistantState.value != AssistantState.THINKING) {
                    lastTranscript.value = speechResult
                    askScreenQuestion(speechResult)
                }
            }
            assistantState.value = AssistantState.LISTENING
        }
    }

    private fun askScreenQuestion(question: String) {
        serviceScope.launch {
            val app = LiveScreenApp.instance
            assistantState.value = AssistantState.THINKING
            app.audioInputManager.pauseListening()

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
                            assistantState.value = if (!isMicMuted.value) AssistantState.LISTENING else AssistantState.IDLE
                            if (!isMicMuted.value) {
                                app.audioInputManager.resumeListening()
                            }
                        }
                    } else {
                        assistantState.value = if (!isMicMuted.value) AssistantState.LISTENING else AssistantState.IDLE
                        if (!isMicMuted.value) {
                            app.audioInputManager.resumeListening()
                        }
                    }
                }
                is GeminiResult.Error -> {
                    errorMessage.value = result.errorMessage
                    assistantState.value = if (!isMicMuted.value) AssistantState.LISTENING else AssistantState.IDLE
                    if (!isMicMuted.value) {
                        app.audioInputManager.resumeListening()
                    }
                }
            }
        }
    }

    private fun toggleScreenSharing() {
        val app = LiveScreenApp.instance
        if (app.screenCaptureManager.isCapturing.value) {
            app.screenCaptureManager.stopCapture()
        } else {
            ScreenCapturePromptActivity.launchPrompt(this)
        }
    }

    private fun toggleMicrophone() {
        val app = LiveScreenApp.instance
        isMicMuted.value = !isMicMuted.value
        if (isMicMuted.value) {
            app.audioInputManager.stopListening()
            assistantState.value = AssistantState.IDLE
        } else {
            startVoiceListening()
        }
    }

    private fun interruptSpeech() {
        val app = LiveScreenApp.instance
        app.audioOutputManager.stop()
        assistantState.value = if (!isMicMuted.value) AssistantState.LISTENING else AssistantState.IDLE
        if (!isMicMuted.value) {
            app.audioInputManager.resumeListening()
        }
    }

    private fun stopFloatingAssistant() {
        _isRunning.value = false
        val app = LiveScreenApp.instance

        // 1. Release MediaProjection and Audio
        app.screenCaptureManager.stopCapture()
        app.audioInputManager.stopListening()
        app.audioOutputManager.stop()

        // 2. Remove Views from WindowManager
        hidePanel()
        if (bubbleView != null) {
            windowManager?.removeView(bubbleView)
            bubbleView = null
        }

        // 3. Stop Foreground Service
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
        if (bubbleView != null) {
            windowManager?.removeView(bubbleView)
            bubbleView = null
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
            .setContentText("स्क्रीन पर फ़्लोटिंग बबल दिखाई दे रहा है • किसी भी ऐप पर सहायता पाएं")
            .setSmallIcon(R.drawable.app_launcher_icon_1791691117930)
            .setContentIntent(pendingOpen)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "सत्र समाप्त करें",
                pendingStop
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
