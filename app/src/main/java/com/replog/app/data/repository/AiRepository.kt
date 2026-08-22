package com.replog.app.data.repository

import com.replog.app.data.ai.AiPromptFactory
import com.replog.app.data.local.AiConversationEntity
import com.replog.app.data.local.AiDao
import com.replog.app.data.local.AiMessageEntity
import com.replog.app.data.remote.ApiService
import com.replog.app.data.prefs.SettingsRepository
import com.replog.app.domain.model.FitnessSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiRepository @Inject constructor(
    private val aiDao: AiDao,
    private val remoteApi: ApiService,
    private val localAiService: com.replog.app.data.ai.LocalAiService,
    private val settingsRepository: SettingsRepository
) {
    fun observeLatestConversation(): Flow<AiConversationEntity?> = aiDao.observeLatestConversation()

    fun observeMessages(conversationId: Long): Flow<List<AiMessageEntity>> =
        aiDao.observeMessages(conversationId)

    suspend fun clearAll() = aiDao.clearConversations()

    private suspend fun currentConversationId(): Long =
        aiDao.observeLatestConversation().firstOrNull()?.id
            ?: aiDao.insertConversation(
                AiConversationEntity(
                    title = "REPLOG AI",
                    createdAtMillis = System.currentTimeMillis()
                )
            )

    suspend fun chat(question: String, snapshot: FitnessSnapshot): String {
        val conversationId = currentConversationId()

        aiDao.insertMessage(
            AiMessageEntity(
                conversationId = conversationId, role = "user",
                content = question, createdAtMillis = System.currentTimeMillis()
            )
        )

        val history = aiDao.observeMessages(conversationId).first()
            .filter { it.role in listOf("user", "assistant") }
            .dropLast(1)
            .map { it.role to it.content }

        val reply = try {
            val response = remoteApi.chat(
                com.replog.app.data.remote.ChatRequest(
                    messages = AiPromptFactory.buildMessages(history, question),
                    context = AiPromptFactory.contextFor(snapshot),
                    mode = "coach"
                )
            )
            if (response.reply.isBlank()) throw IllegalStateException("Empty AI reply")
            response.reply.trim()
        } catch (e: Exception) {
            localAiService.chat(question, history, snapshot)
                .getOrElse { "Sorry — I couldn't process that right now." }
        }

        aiDao.insertMessage(
            AiMessageEntity(
                conversationId = conversationId, role = "assistant",
                content = reply, createdAtMillis = System.currentTimeMillis()
            )
        )
        return reply
    }

    suspend fun analyzeFood(base64Image: String): Result<String> {
        return try {
            val response = remoteApi.analyzeFood(
                com.replog.app.data.remote.VisionRequest(
                    image_base64 = base64Image,
                    prompt = AiPromptFactory.visionPrompt()
                )
            )
            if (response.raw.isBlank()) throw IllegalStateException("Empty AI vision response")
            Result.success(response.raw)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
