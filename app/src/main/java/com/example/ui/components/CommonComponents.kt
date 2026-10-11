package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ListeningGreen
import com.example.ui.theme.SpeakingPurple
import com.example.ui.theme.ThinkingAmber

enum class AssistantState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING
}

@Composable
fun StateIndicatorBadge(
    state: AssistantState,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (state) {
        AssistantState.IDLE -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "तैयार (सत्र सक्रिय)"
        )
        AssistantState.LISTENING -> Triple(
            ListeningGreen.copy(alpha = 0.2f),
            ListeningGreen,
            "सुन रहे हैं..."
        )
        AssistantState.THINKING -> Triple(
            ThinkingAmber.copy(alpha = 0.2f),
            ThinkingAmber,
            "स्क्रीन का विश्लेषण कर रहे हैं..."
        )
        AssistantState.SPEAKING -> Triple(
            SpeakingPurple.copy(alpha = 0.2f),
            SpeakingPurple,
            "बोल रहे हैं..."
        )
    }

    Surface(
        modifier = modifier.testTag("state_indicator_badge"),
        shape = RoundedCornerShape(16.dp),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Text(
                text = label,
                color = textColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun AudioWaveVisualizer(
    isListening: Boolean,
    isSpeaking: Boolean,
    rmsDb: Float,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "wave_transition")

    val anim1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "anim1"
    )
    val anim2 by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(300, delayMillis = 100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "anim2"
    )
    val anim3 by infiniteTransition.animateFloat(
        initialValue = 0.1f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(450, delayMillis = 50, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "anim3"
    )

    val activeColor = when {
        isSpeaking -> SpeakingPurple
        isListening -> ListeningGreen
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
    }

    val baseScale = if (isListening) {
        // Boost with RMS dB if listening
        (rmsDb.coerceIn(0f, 10f) / 10f).coerceAtLeast(0.2f)
    } else if (isSpeaking) {
        0.75f
    } else {
        0.1f
    }

    Row(
        modifier = modifier
            .height(36.dp)
            .testTag("audio_wave_visualizer"),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val heights = listOf(
            anim1 * baseScale,
            anim2 * baseScale,
            anim3 * baseScale,
            anim1 * 0.8f * baseScale,
            anim2 * 0.9f * baseScale
        )

        heights.forEach { factor ->
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight(fraction = factor.coerceIn(0.15f, 1.0f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(activeColor)
            )
        }
    }
}
