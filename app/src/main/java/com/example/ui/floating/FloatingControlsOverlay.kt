package com.example.ui.floating

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.AssistantState
import com.example.ui.components.AudioWaveVisualizer
import com.example.ui.components.StateIndicatorBadge
import com.example.ui.theme.ListeningGreen
import com.example.ui.theme.SpeakingPurple
import com.example.ui.theme.ThinkingAmber
import com.example.voice.VoiceInputState

@Composable
fun FloatingBubbleContent(
    assistantState: AssistantState,
    isCapturing: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val stateColor = when (assistantState) {
        AssistantState.LISTENING -> ListeningGreen
        AssistantState.SPEAKING -> SpeakingPurple
        AssistantState.THINKING -> ThinkingAmber
        AssistantState.IDLE -> MaterialTheme.colorScheme.primary
    }

    Box(
        modifier = modifier
            .size(62.dp)
            .scale(if (assistantState != AssistantState.IDLE) pulseScale else 1.0f),
        contentAlignment = Alignment.Center
    ) {
        // Outer glowing ring
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            stateColor.copy(alpha = 0.5f),
                            Color.Transparent
                        )
                    )
                )
        )

        // Main Bubble Surface
        Surface(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .border(2.dp, stateColor, CircleShape),
            color = Color(0xF00F172A),
            shadowElevation = 8.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(id = R.drawable.app_launcher_icon_1791691117930),
                    contentDescription = "Floating Assistant Bubble",
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                )

                // State indicator pill at corner
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 2.dp, end = 2.dp)
                        .clip(CircleShape)
                        .background(stateColor)
                        .border(1.5.dp, Color(0xFF0F172A), CircleShape)
                )

                // Screen Capture active green dot
                if (isCapturing) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .align(Alignment.TopStart)
                            .padding(top = 2.dp, start = 2.dp)
                            .clip(CircleShape)
                            .background(ListeningGreen)
                            .border(1.dp, Color(0xFF0F172A), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun FloatingPanelContent(
    assistantState: AssistantState,
    voiceState: VoiceInputState,
    isCapturing: Boolean,
    isMicMuted: Boolean,
    latestFrame: Bitmap?,
    rmsDb: Float,
    lastTranscript: String,
    lastResponse: String,
    errorMessage: String?,
    onToggleScreenShare: () -> Unit,
    onToggleMic: () -> Unit,
    onPushToTalk: () -> Unit,
    onStopListening: () -> Unit,
    onAskScreenNow: () -> Unit,
    onInterruptSpeech: () -> Unit,
    onMinimize: () -> Unit,
    onEndSession: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .width(330.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xF50F172A)
        ),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Bar with branding, minimize and close
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.app_launcher_icon_1791691117930),
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    }
                    Column {
                        Text(
                            text = "Live Screen Guide",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(ListeningGreen)
                            )
                            Text(
                                text = "Gemini AI कनेक्टेड",
                                fontSize = 10.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "कंट्रोल छिपाएं (Minimize)",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    IconButton(
                        onClick = onEndSession,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "सत्र समाप्त करें (End Session)",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // State & Waveform Status Banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF1E293B),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    StateIndicatorBadge(state = assistantState)

                    AudioWaveVisualizer(
                        isListening = assistantState == AssistantState.LISTENING && !isMicMuted,
                        isSpeaking = assistantState == AssistantState.SPEAKING,
                        rmsDb = rmsDb
                    )

                    if (assistantState == AssistantState.SPEAKING) {
                        IconButton(
                            onClick = onInterruptSpeech,
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = "आवाज़ रोकें (Stop speech)",
                                tint = SpeakingPurple,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    } else if (latestFrame != null && isCapturing) {
                        Box(
                            modifier = Modifier
                                .size(width = 24.dp, height = 36.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .border(1.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(4.dp))
                        ) {
                            Image(
                                bitmap = latestFrame.asImageBitmap(),
                                contentDescription = "Screen thumbnail",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }

            // Error Message Banner if any
            if (errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0x33EF4444),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = errorMessage,
                            color = Color(0xFFFCA5A5),
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = onRetry,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "पुनः प्रयास",
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Live Caption / Transcript & AI Response Display
            if (lastTranscript.isNotBlank() || lastResponse.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF1E293B).copy(alpha = 0.8f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        if (lastTranscript.isNotBlank()) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = lastTranscript,
                                    color = Color(0xFF94A3B8),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (lastResponse.isNotBlank()) {
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = lastResponse,
                                color = Color.White,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 1-Tap Screen Inquiry Button
            Button(
                onClick = onAskScreenNow,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (assistantState == AssistantState.THINKING) "स्क्रीन समझ रहे हैं..." else "वर्तमान स्क्रीन का विश्लेषण करें",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Push-to-Talk Voice Button (Reliable speech input)
            val isCurrentlyListening = assistantState == AssistantState.LISTENING || voiceState == VoiceInputState.LISTENING
            Button(
                onClick = {
                    if (isCurrentlyListening) {
                        onStopListening()
                    } else {
                        onPushToTalk()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isCurrentlyListening) ListeningGreen else Color(0xFF334155),
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (isCurrentlyListening) Icons.Default.Stop else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isCurrentlyListening) "सुन रहे हैं... (टैप करके रोकें)" else "बोलने के लिए टैप करें (Push to Talk)",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            // Secondary Controls: Screen Share Toggle + Mic Mute
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Screen Sharing Toggle
                FilledTonalButton(
                    onClick = onToggleScreenShare,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isCapturing) Color(0x3310B981) else Color(0xFF334155),
                        contentColor = if (isCapturing) ListeningGreen else Color(0xFFCBD5E1)
                    )
                ) {
                    Icon(
                        imageVector = if (isCapturing) Icons.AutoMirrored.Filled.ScreenShare else Icons.Default.PhoneAndroid,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isCapturing) "स्क्रीन चालू" else "स्क्रीन शेयर करें",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Mic Mute Toggle
                FilledTonalButton(
                    onClick = onToggleMic,
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = if (isMicMuted) Color(0x33EF4444) else Color(0xFF334155),
                        contentColor = if (isMicMuted) Color(0xFFFCA5A5) else Color(0xFFCBD5E1)
                    )
                ) {
                    Icon(
                        imageVector = if (isMicMuted) Icons.Default.MicOff else Icons.Default.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isMicMuted) "माइक म्यूट" else "माइक चालू",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
