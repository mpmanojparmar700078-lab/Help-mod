package com.example

import android.app.Application
import com.example.ai.GeminiLiveClient
import com.example.data.local.ChatDatabase
import com.example.data.local.SettingsRepository
import com.example.data.repository.ChatRepository
import com.example.screen.ScreenCaptureManager
import com.example.voice.AudioInputManager
import com.example.voice.AudioOutputManager

class LiveScreenApp : Application() {
    lateinit var chatDatabase: ChatDatabase
        private set

    lateinit var chatRepository: ChatRepository
        private set

    lateinit var settingsRepository: SettingsRepository
        private set

    lateinit var screenCaptureManager: ScreenCaptureManager
        private set

    lateinit var audioOutputManager: AudioOutputManager
        private set

    lateinit var audioInputManager: AudioInputManager
        private set

    lateinit var geminiClient: GeminiLiveClient
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        chatDatabase = ChatDatabase.getDatabase(this)
        chatRepository = ChatRepository(chatDatabase.chatDao())
        settingsRepository = SettingsRepository(this)
        screenCaptureManager = ScreenCaptureManager(this)
        audioOutputManager = AudioOutputManager(this)
        audioInputManager = AudioInputManager(this)
        geminiClient = GeminiLiveClient()
    }

    companion object {
        lateinit var instance: LiveScreenApp
            private set
    }
}
