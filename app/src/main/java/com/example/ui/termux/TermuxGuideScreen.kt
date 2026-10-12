package com.example.ui.termux

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TermuxGuideScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label कॉपी कर लिया गया", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Termux मोबाइल बिल्ड गाइड",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier.testTag("termux_back_button")
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
            // Header Info Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            text = "केवल मोबाइल फोन से APK निर्माण",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "बिना कंप्यूटर Android फोन पर Termux और OpenJDK के ज़रिए पूरा प्रोजेक्ट कंपाइल और इंस्टॉल करें।",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Step 1: Termux Environment Preparation
            CommandStepCard(
                stepNumber = "1",
                title = "Termux पैकेज और JDK सेटअप",
                description = "Termux खोलें और आवश्यक टूल्स व OpenJDK 17/21 इंस्टॉल करें:",
                command = "pkg update && pkg upgrade -y\npkg install -y git openjdk-17 gradle aapt zipalign apksigner",
                onCopy = { copyToClipboard("Step 1 Command", it) }
            )

            // Step 2: Android SDK Command-line Tools in Termux
            CommandStepCard(
                stepNumber = "2",
                title = "Android SDK पाथ सेट करें",
                description = "Termux एनवायरनमेंट में ANDROID_HOME और PATH कॉन्फ़िगर करें:",
                command = "export ANDROID_HOME=\$HOME/android-sdk\nexport PATH=\$PATH:\$ANDROID_HOME/cmdline-tools/latest/bin:\$ANDROID_HOME/platform-tools",
                onCopy = { copyToClipboard("Step 2 Command", it) }
            )

            // Step 3: Clone or Navigate to Project
            CommandStepCard(
                stepNumber = "3",
                title = "प्रोजेक्ट डायरेक्टरी में जाएं",
                description = "प्रोजेक्ट फोल्डर में जाएं और पर्यावरण चर लोड करें:",
                command = "cd ~/LiveScreenAIGuide\nls -la",
                onCopy = { copyToClipboard("Step 3 Command", it) }
            )

            // Step 4: Build Debug APK with Gradle in Termux
            CommandStepCard(
                stepNumber = "4",
                title = "APK कंपाइल और बिल्ड करें",
                description = "Gradle के माध्यम से प्रोजेक्ट को कंपाइल करके APK तैयार करें:",
                command = "gradle assembleDebug --stacktrace",
                onCopy = { copyToClipboard("Step 4 Command", it) }
            )

            // Step 5: Install APK Directly on the Phone
            CommandStepCard(
                stepNumber = "5",
                title = "तैयार APK इंस्टॉल करें",
                description = "बिल्ड होने के बाद APK को सीधे अपने Android फोन में इंस्टॉल करें:",
                command = "termux-open app/build/outputs/apk/debug/app-debug.apk",
                onCopy = { copyToClipboard("Step 5 Command", it) }
            )

            // Step 6: Debugging & Logcat in Termux
            CommandStepCard(
                stepNumber = "6",
                title = "लॉग्स और डिबगिंग देखें",
                description = "स्क्रीन शेयरिंग और वॉयस इवेंट्स के लॉग्स देखने के लिए:",
                command = "logcat -s LiveScreenGuide:V ScreenGuideService:V",
                onCopy = { copyToClipboard("Step 6 Command", it) }
            )
        }
    }
}

@Composable
fun CommandStepCard(
    stepNumber: String,
    title: String,
    description: String,
    command: String,
    onCopy: (String) -> Unit
) {
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
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = stepNumber,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF0F172A),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    SelectionContainer(modifier = Modifier.weight(1f)) {
                        Text(
                            text = command,
                            color = Color(0xFF38BDF8),
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 18.sp
                        )
                    }
                    IconButton(
                        onClick = { onCopy(command) },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Copy command",
                            tint = Color(0xFF94A3B8),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}
