package com.example.data.repository

import com.example.data.local.ChatDao
import com.example.data.model.ChatMessage
import com.example.data.model.ChatSession
import kotlinx.coroutines.flow.Flow

class ChatRepository(private val chatDao: ChatDao) {
    val allSessions: Flow<List<ChatSession>> = chatDao.getAllSessions()

    fun getMessagesForSession(sessionId: String): Flow<List<ChatMessage>> {
        return chatDao.getMessagesForSession(sessionId)
    }

    suspend fun getRecentMessages(sessionId: String): List<ChatMessage> {
        return chatDao.getMessagesListForSession(sessionId)
    }

    suspend fun createOrUpdateSession(session: ChatSession) {
        chatDao.insertSession(session)
    }

    suspend fun addMessage(
        sessionId: String,
        sender: String,
        text: String,
        hindiText: String? = null,
        hasScreenContext: Boolean = false,
        screenAnalysisNote: String? = null
    ): Long {
        val message = ChatMessage(
            sessionId = sessionId,
            sender = sender,
            text = text,
            hindiText = hindiText,
            timestamp = System.currentTimeMillis(),
            hasScreenContext = hasScreenContext,
            screenAnalysisNote = screenAnalysisNote
        )
        val msgId = chatDao.insertMessage(message)

        val existing = chatDao.getSessionById(sessionId)
        if (existing != null) {
            val title = if (existing.title.startsWith("नया सत्र") && sender == "user") {
                if (text.length > 35) text.take(35) + "..." else text
            } else {
                existing.title
            }
            chatDao.updateSession(
                existing.copy(
                    title = title,
                    lastUpdatedTime = System.currentTimeMillis(),
                    messageCount = existing.messageCount + 1,
                    summary = if (sender == "assistant") text.take(60) else existing.summary
                )
            )
        } else {
            val initialTitle = if (sender == "user") {
                if (text.length > 35) text.take(35) + "..." else text
            } else "लाइव स्क्रीन सत्र"
            chatDao.insertSession(
                ChatSession(
                    sessionId = sessionId,
                    title = initialTitle,
                    startTime = System.currentTimeMillis(),
                    lastUpdatedTime = System.currentTimeMillis(),
                    messageCount = 1,
                    summary = text.take(60)
                )
            )
        }

        return msgId
    }

    suspend fun deleteSession(sessionId: String) {
        chatDao.deleteMessagesForSession(sessionId)
        chatDao.deleteSession(sessionId)
    }

    suspend fun clearAllHistory() {
        chatDao.clearAllMessages()
        chatDao.clearAllSessions()
    }
}
