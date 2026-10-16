package com.example.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.RecordVoiceOver
import androidx.compose.material.icons.automirrored.filled.ScreenShare
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.LiveScreenApp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val app = LiveScreenApp.instance
    val scope = rememberCoroutineScope()
    val settings by app.settingsRepository.settingsFlow.collectAsState(initial = com.example.data.local.UserSettings())

    var customApiKeyInput by remember(settings.customApiKey) { mutableStateOf(settings.customApiKey) }
    var apiKeySavedNotice by remember { mutableStateOf(false) }

    var speechRateSlider by remember(settings.speechRate) { mutableFloatStateOf(settings.speechRate) }
    var speechPitchSlider by remember(settings.speechPitch) { mutableFloatStateOf(settings.speechPitch) }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "सेटिंग्स और गोपनीयता",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Language Selection Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "सहायक की भाषा",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "एआई किस भाषा में आपकी स्क्रीन को समझाए:",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.preferredLanguage == "hi-IN",
                            onClick = {
                                scope.launch { app.settingsRepository.updatePreferredLanguage("hi-IN") }
                            },
                            label = { Text("हिन्दी (Hindi)") },
                            modifier = Modifier.testTag("lang_hi_chip")
                        )
                        FilterChip(
                            selected = settings.preferredLanguage == "hi-en",
                            onClick = {
                                scope.launch { app.settingsRepository.updatePreferredLanguage("hi-en") }
                            },
                            label = { Text("Hinglish") },
                            modifier = Modifier.testTag("lang_hinglish_chip")
                        )
                        FilterChip(
                            selected = settings.preferredLanguage == "en-US",
                            onClick = {
                                scope.launch { app.settingsRepository.updatePreferredLanguage("en-US") }
                            },
                            label = { Text("English") },
                            modifier = Modifier.testTag("lang_en_chip")
                        )
                    }
                }
            }

            // Voice & Speech Settings Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RecordVoiceOver,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "आवाज़ और स्पीच सेटिंग्स",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "उत्तर आवाज़ में बोलें", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = "सहायक के उत्तर को अपने आप बोलकर सुनाएं",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoSpeakResponses,
                            onCheckedChange = {
                                scope.launch { app.settingsRepository.updateAutoSpeak(it) }
                            },
                            modifier = Modifier.testTag("auto_speak_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "सतत आवाज़ संवाद", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Text(
                                text = "बोलने के बाद स्वतः अगला प्रश्न सुनें",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.continuousVoiceEnabled,
                            onCheckedChange = {
                                scope.launch { app.settingsRepository.updateContinuousVoice(it) }
                            },
                            modifier = Modifier.testTag("continuous_voice_switch")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Voice Persona Selection
                    Text(
                        text = "आवाज़ का चयन (Voice Persona):",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.voicePersona == "natural_female",
                            onClick = {
                                scope.launch {
                                    app.settingsRepository.updateVoicePersona("natural_female")
                                    app.audioOutputManager.setVoicePersona("natural_female")
                                }
                            },
                            label = { Text("प्राकृतिक महिला") },
                            modifier = Modifier.testTag("voice_female_chip")
                        )
                        FilterChip(
                            selected = settings.voicePersona == "natural_male",
                            onClick = {
                                scope.launch {
                                    app.settingsRepository.updateVoicePersona("natural_male")
                                    app.audioOutputManager.setVoicePersona("natural_male")
                                }
                            },
                            label = { Text("प्राकृतिक पुरुष") },
                            modifier = Modifier.testTag("voice_male_chip")
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Speech Rate Slider
                    Text(
                        text = "बोलने की गति (Speed): ${(speechRateSlider * 10).toInt() / 10.0}x",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = speechRateSlider,
                        onValueChange = { speechRateSlider = it },
                        onValueChangeFinished = {
                            scope.launch {
                                app.settingsRepository.updateSpeechRate(speechRateSlider)
                                app.audioOutputManager.setSpeechRate(speechRateSlider)
                            }
                        },
                        valueRange = 0.7f..1.4f,
                        steps = 6,
                        modifier = Modifier.testTag("speech_rate_slider")
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Speech Pitch Slider
                    Text(
                        text = "आवाज़ का सुर (Pitch): ${(speechPitchSlider * 10).toInt() / 10.0}x",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Slider(
                        value = speechPitchSlider,
                        onValueChange = { speechPitchSlider = it },
                        onValueChangeFinished = {
                            scope.launch {
                                app.settingsRepository.updateSpeechPitch(speechPitchSlider)
                                app.audioOutputManager.setSpeechPitch(speechPitchSlider)
                            }
                        },
                        valueRange = 0.8f..1.2f,
                        steps = 4,
                        modifier = Modifier.testTag("speech_pitch_slider")
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Active Voice Status Display
                    val ttsEngineStatus by app.audioOutputManager.ttsStatus.collectAsState()
                    Text(
                        text = ttsEngineStatus,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Test Voice Button
                    Button(
                        onClick = {
                            app.audioOutputManager.speak(
                                "नमस्ते! मैं आपका लाइव स्क्रीन एआई गाइड हूँ। आपकी स्क्रीन देखकर, मैं आपको हर कदम पर स्पष्ट और स्वाभाविक मदद करूँगा।"
                            )
                        },
                        modifier = Modifier
                            .align(Alignment.End)
                            .testTag("test_voice_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "आवाज़ जांचें", fontSize = 13.sp)
                    }
                }
            }

            // Screen & AI Explanation Detail Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ScreenShare,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "स्क्रीन विश्लेषण और विवरण",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "उत्तर का विस्तार स्तर:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = settings.explanationDetail == "concise",
                            onClick = {
                                scope.launch { app.settingsRepository.updateExplanationDetail("concise") }
                            },
                            label = { Text("संक्षिप्त (2-4 वाक्य)") },
                            modifier = Modifier.testTag("detail_concise_chip")
                        )
                        FilterChip(
                            selected = settings.explanationDetail == "detailed",
                            onClick = {
                                scope.launch { app.settingsRepository.updateExplanationDetail("detailed") }
                            },
                            label = { Text("विस्तृत (चरण-दर-चरण)") },
                            modifier = Modifier.testTag("detail_detailed_chip")
                        )
                    }
                }
            }

            // Gemini API Key Configuration Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Gemini API कुंजी कॉन्फ़िगरेशन",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "डिफ़ॉल्ट रूप से बिल्ट-इन कुंजी उपयोग की जाती है। यदि आप अपनी व्यक्तिगत Google AI Studio Gemini API Key का उपयोग करना चाहते हैं, तो यहाँ दर्ज करें:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 17.sp
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = customApiKeyInput,
                        onValueChange = {
                            customApiKeyInput = it
                            apiKeySavedNotice = false
                        },
                        placeholder = { Text("AIzaSy...") },
                        label = { Text("कस्टम Gemini API Key") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("custom_api_key_field"),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (apiKeySavedNotice) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "सुरक्षित सहेजा गया",
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        } else {
                            Spacer(modifier = Modifier.width(1.dp))
                        }

                        Button(
                            onClick = {
                                scope.launch {
                                    app.settingsRepository.updateCustomApiKey(customApiKeyInput)
                                    apiKeySavedNotice = true
                                }
                            },
                            modifier = Modifier.testTag("save_api_key_button")
                        ) {
                            Text("कुंजी सहेजें")
                        }
                    }
                }
            }

            // Privacy & Policy Transparency Card (Requirement 3.F & 5)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                )
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Text(
                            text = "डेटा सुरक्षा और नीति विवरण",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "• केवल मार्गदर्शन: यह ऐप केवल आपको निर्देश देता है। यह आपके फोन पर किसी भी प्रकार का स्वचालित क्लिक, टाइपिंग या ऐप ओपनिंग नहीं करता।\n\n" +
                               "• स्क्रीन और ऑडियो प्रोसेसिंग: लाइव सत्र के दौरान स्क्रीन फ्रेम और आवाज़ का विश्लेषण करने के लिए Google Gemini API को भेजा जाता है।\n\n" +
                               "• स्थानीय इतिहास: बातचीत का इतिहास केवल आपके फोन के Room डेटाबेस में सुरक्षित रहता है। आप इसे कभी भी इतिहास स्क्रीन से मिटा सकते हैं।\n\n" +
                               "• संवेदनशील डेटा: स्क्रीन पर दिखने वाले बैंक पासवर्ड या ओटीपी पिन कभी भी सहेजे नहीं जाते।",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}
