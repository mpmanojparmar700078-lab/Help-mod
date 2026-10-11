package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "chat_sessions")
data class ChatSession(
    @PrimaryKey
    val sessionId: String,
    val title: String,
    val startTime: Long = System.currentTimeMillis(),
    val lastUpdatedTime: Long = System.currentTimeMillis(),
    val messageCount: Int = 0,
    val summary: String? = null
)

@Entity(tableName = "chat_messages")
data class ChatMessage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val sessionId: String,
    val sender: String, // "user" or "assistant" or "system"
    val text: String,
    val hindiText: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val hasScreenContext: Boolean = false,
    val screenAnalysisNote: String? = null
)
