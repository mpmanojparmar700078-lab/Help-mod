package com.example.ui.session

import android.graphics.Bitmap
import android.media.projection.MediaProjectionManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.LiveScreenApp
import com.example.ai.GeminiResult
import com.example.data.model.ChatMessage
import com.example.service.ScreenGuideService
import com.example.ui.components.AssistantState
import com.example.ui.components.AudioWaveVisualizer
import com.example.ui.components.StateIndicatorBadge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveSessionScreen(
    sessionId: String,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val app = LiveScreenApp.instance
    val scope = rememberCoroutineScope()

    val messages by app.chatRepository.getMessagesForSession(sessionId).collectAsState(initial = emptyList())
    val isCapturing by app.screenCaptureManager.isCapturing.collectAsState()
    val latestFrame by app.screenCaptureManager.latestFrame.collectAsState()
    val isListening by app.audioInputManager.isListening.collectAsState()
    val rmsDb by app.audioInputManager.rmsDb.collectAsState()
    val partialTranscript by app.audioInputManager.partialTranscript.collectAsState()
    val isSpeaking by app.audioOutputManager.isSpeaking.collectAsState()
    val settings by app.settingsRepository.settingsFlow.collectAsState(initial = com.example.data.local.UserSettings())

    var isMicMuted by remember { mutableStateOf(false) }
    var assistantState by remember { mutableStateOf(AssistantState.IDLE) }
    var textInput by remember { mutableStateOf("") }
    var showEndDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // Auto-scroll when new messages arrive
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Process a user query against the current screen frame
    fun processQuery(queryText: String) {
        if (queryText.isBlank()) return

        scope.launch {
            assistantState = AssistantState.THINKING
            app.audioInputManager.pauseListening()

            // Save user message
            app.chatRepository.addMessage(
                sessionId = sessionId,
                sender = "user",
                text = queryText,
                hindiText = queryText,
                hasScreenContext = isCapturing
            )

            val currentBitmap = app.screenCaptureManager.getLatestFrame()
            val historyPairs = messages.takeLast(4).map { it.sender to it.text }

            val result = app.geminiClient.analyzeScreenAndRespond(
                apiKey = settings.effectiveApiKey,
                screenBitmap = currentBitmap,
                userQuestion = queryText,
                conversationHistory = historyPairs,
                explanationDetail = settings.explanationDetail,
                language = settings.preferredLanguage
            )

            when (result) {
                is GeminiResult.Success -> {
                    errorMessage = null
                    app.chatRepository.addMessage(
                        sessionId = sessionId,
                        sender = "assistant",
                        text = result.text,
                        hindiText = result.text,
                        hasScreenContext = currentBitmap != null,
                        screenAnalysisNote = if (currentBitmap != null) "स्क्रीन विश्लेषण शामिल है" else null
                    )

                    if (settings.autoSpeakResponses) {
                        assistantState = AssistantState.SPEAKING
                        app.audioOutputManager.speak(result.text) {
                            assistantState = AssistantState.IDLE
                            if (!isMicMuted && settings.continuousVoiceEnabled) {
                                app.audioInputManager.resumeListening()
                            }
                        }
                    } else {
                        assistantState = AssistantState.IDLE
                        if (!isMicMuted && settings.continuousVoiceEnabled) {
                            app.audioInputManager.resumeListening()
                        }
                    }
                }
                is GeminiResult.Error -> {
                    errorMessage = result.errorMessage
                    assistantState = AssistantState.IDLE
                    app.chatRepository.addMessage(
                        sessionId = sessionId,
                        sender = "assistant",
                        text = "माफ़ कीजिए, त्रुटि आई: ${result.errorMessage}",
                        hindiText = "माफ़ कीजिए, त्रुटि आई: ${result.errorMessage}"
                    )
                    if (!isMicMuted && settings.continuousVoiceEnabled) {
                        app.audioInputManager.resumeListening()
                    }
                }
            }
        }
    }

    // Media projection launcher if user starts screen sharing again in-session
    val mediaProjectionManager = remember {
        context.getSystemService(android.content.Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            ScreenGuideService.startCapture(context, result.resultCode, result.data!!)
        }
    }

    // Setup speech input lifecycle
    LaunchedEffect(isMicMuted, settings.continuousVoiceEnabled) {
        if (!isMicMuted && settings.continuousVoiceEnabled) {
            app.audioInputManager.startListening(
                language = settings.preferredLanguage,
                continuous = true
            ) { speechResult ->
                if (speechResult.isNotBlank() && assistantState != AssistantState.THINKING) {
                    processQuery(speechResult)
                }
            }
        } else {
            app.audioInputManager.stopListening()
        }
    }

    // Manage assistant visual state
    LaunchedEffect(isListening, isSpeaking, assistantState) {
        if (assistantState != AssistantState.THINKING) {
            assistantState = when {
                isSpeaking -> AssistantState.SPEAKING
                isListening -> AssistantState.LISTENING
                else -> AssistantState.IDLE
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            app.audioInputManager.stopListening()
            app.audioOutputManager.stop()
        }
    }

    BackHandler {
        showEndDialog = true
    }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("सत्र समाप्त करें?") },
            text = { Text("क्या आप इस स्क्रीन गाइड सत्र को समाप्त करना चाहते हैं? बातचीत का इतिहास सुरक्षित रहेगा।") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showEndDialog = false
                        app.audioInputManager.stopListening()
                        app.audioOutputManager.stop()
                        app.screenCaptureManager.stopCapture()
                        ScreenGuideService.stopService(context)
                        onNavigateBack()
                    },
                    modifier = Modifier.testTag("confirm_end_session_button")
                ) {
                    Text("हाँ, समाप्त करें", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) {
                    Text("रद्द करें")
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "लाइव स्क्रीन गाइड",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(if (isCapturing) Color(0xFF10B981) else Color(0xFFEF4444))
                            )
                            Text(
                                text = if (isCapturing) "स्क्रीन शेयरिंग सक्रिय" else "स्क्रीन शेयरिंग बंद",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = { showEndDialog = true },
                        modifier = Modifier.testTag("session_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    // Screen Share Toggle
                    IconButton(
                        onClick = {
                            if (isCapturing) {
                                app.screenCaptureManager.stopCapture()
                                ScreenGuideService.stopService(context)
                            } else {
                                val intent = mediaProjectionManager.createScreenCaptureIntent()
                                screenCaptureLauncher.launch(intent)
                            }
                        },
                        modifier = Modifier.testTag("toggle_screen_share_button")
                    ) {
                        Icon(
                            imageVector = if (isCapturing) Icons.AutoMirrored.Filled.ScreenShare else Icons.Default.PhoneAndroid,
                            contentDescription = "Screen Share Toggle",
                            tint = if (isCapturing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Mic Mute Toggle
                    IconButton(
                        onClick = {
                            isMicMuted = !isMicMuted
                        },
                        modifier = Modifier.testTag("toggle_mic_button")
                    ) {
                        Icon(
                            imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                            contentDescription = "Mic Toggle",
                            tint = if (isMicMuted) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }

                    // End Session Button
                    IconButton(
                        onClick = { showEndDialog = true },
                        modifier = Modifier.testTag("end_session_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.StopCircle,
                            contentDescription = "End Session",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    // Real-time voice transcript banner
                    if (partialTranscript.isNotBlank() && isListening) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = partialTranscript,
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // Bottom Row: Input and Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = textInput,
                            onValueChange = { textInput = it },
                            placeholder = { Text("प्रश्न लिखें या बोलें...", fontSize = 14.sp) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("session_text_input"),
                            shape = RoundedCornerShape(24.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            singleLine = true
                        )

                        if (textInput.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    val text = textInput.trim()
                                    textInput = ""
                                    processQuery(text)
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .testTag("send_query_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        } else {
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Push-to-Talk voice trigger
                                IconButton(
                                    onClick = {
                                        if (isListening) {
                                            app.audioInputManager.stopListening()
                                        } else {
                                            if (assistantState == AssistantState.SPEAKING) {
                                                app.audioOutputManager.stop()
                                            }
                                            app.audioInputManager.startListening(
                                                language = settings.preferredLanguage,
                                                continuous = false
                                            ) { speechResult ->
                                                if (speechResult.isNotBlank() && assistantState != AssistantState.THINKING) {
                                                    processQuery(speechResult)
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(if (isListening) com.example.ui.theme.ListeningGreen else MaterialTheme.colorScheme.secondaryContainer)
                                        .testTag("push_to_talk_button")
                                ) {
                                    Icon(
                                        imageVector = if (isListening) Icons.Default.Stop else Icons.Default.Mic,
                                        contentDescription = "Push to Talk",
                                        tint = if (isListening) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }

                                // Quick Action: Ask About Screen Now
                                FloatingActionButton(
                                    onClick = {
                                        processQuery("मेरी स्क्रीन पर क्या दिखाई दे रहा है और मुझे क्या करना चाहिए?")
                                    },
                                    modifier = Modifier
                                        .size(48.dp)
                                        .testTag("quick_ask_screen_button"),
                                    shape = CircleShape,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                                        contentDescription = "Ask Current Screen",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Live Status Banner with Waveform & State Indicator
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StateIndicatorBadge(state = assistantState)

                    AudioWaveVisualizer(
                        isListening = isListening && !isMicMuted,
                        isSpeaking = isSpeaking,
                        rmsDb = rmsDb
                    )

                    // Small screen thumbnail preview badge
                    if (latestFrame != null && isCapturing && !latestFrame!!.isRecycled) {
                        Box(
                            modifier = Modifier
                                .size(width = 32.dp, height = 48.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(6.dp))
                        ) {
                            Image(
                                bitmap = latestFrame!!.asImageBitmap(),
                                contentDescription = "Screen preview thumbnail",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Error notice if any
            if (errorMessage != null) {
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 12.sp,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { errorMessage = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "Dismiss error",
                                tint = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Chat Messages List
            if (messages.isEmpty() && assistantState != AssistantState.THINKING) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "स्क्रीन गाइड तैयार है",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "आप सीधे बोलकर पूछ सकते हैं: 'मेरी स्क्रीन पर क्या है?' या नीचे दिए गए बटन पर टैप करें।",
                            fontSize = 13.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 18.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages) { message ->
                        MessageBubble(
                            message = message,
                            onPlayVoice = {
                                app.audioOutputManager.speak(message.text)
                            }
                        )
                    }

                    if (assistantState == AssistantState.THINKING) {
                        item {
                            ThinkingBubble()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MessageBubble(
    message: ChatMessage,
    onPlayVoice: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.sender == "user"

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = if (isUser) 16.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 16.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                if (!isUser && message.hasScreenContext) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "स्क्रीन देखकर उत्तर दिया",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = message.text,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    color = if (isUser) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )

                if (!isUser) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        IconButton(
                            onClick = onPlayVoice,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Play response voice",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThinkingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "स्क्रीन का विश्लेषण कर रहे हैं...",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
