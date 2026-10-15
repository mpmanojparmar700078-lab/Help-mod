package com.example.service

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.LiveScreenApp

class ScreenCapturePromptActivity : ComponentActivity() {
    private val captureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val app = LiveScreenApp.instance
            // Start the dedicated mediaProjection foreground service first (Android 14+ mandate)
            ScreenGuideService.startService(this)
            app.screenCaptureManager.startCapture(result.resultCode, result.data!!)
            val intent = Intent(FloatingAssistantService.ACTION_SCREEN_CAPTURE_GRANTED).apply {
                setPackage(packageName)
            }
            sendBroadcast(intent)
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val mediaProjectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            val captureIntent = mediaProjectionManager.createScreenCaptureIntent()
            captureLauncher.launch(captureIntent)
        } catch (e: Exception) {
            android.util.Log.e("ScreenCapturePrompt", "Error creating screen capture intent: ${e.message}", e)
            finish()
        }
    }

    companion object {
        fun launchPrompt(context: Context) {
            val intent = Intent(context, ScreenCapturePromptActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            }
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    try {
                        val options = android.app.ActivityOptions.makeBasic().apply {
                            setPendingIntentBackgroundActivityStartMode(
                                android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED
                            )
                        }
                        context.startActivity(intent, options.toBundle())
                    } catch (_: Exception) {
                        context.startActivity(intent)
                    }
                } else {
                    context.startActivity(intent)
                }
            } catch (e: Exception) {
                android.util.Log.e("ScreenCapturePrompt", "Error starting prompt activity: ${e.message}", e)
            }
        }
    }
}
