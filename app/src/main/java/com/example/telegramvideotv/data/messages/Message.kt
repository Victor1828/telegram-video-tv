package com.example.telegramvideotv.data.messages

import org.drinkless.tdlib.TdApi

data class MessageItem(
    val id: Long,
    val senderId: Long, // userId (positivo) o chatId de canal (negativo); 0 = desconocido
    val senderName: String,
    val content: MessageContent,
    val isOutgoing: Boolean,
    val date: Int,
    val isEdited: Boolean = false, // Fase 4b: TDLib editDate > 0
    val replyToMessageId: Long? = null, // Para Fase 2 (citas/reply)
    val replyPreview: ReplyPreview? = null // Cita ya resuelta (nombre + snippet)
)

data class ReplyPreview(
    val senderName: String,
    val snippet: String
)

sealed class MessageContent {
    data class Text(val text: String) : MessageContent()
    data class Photo(val fileId: Int, val caption: String) : MessageContent()
    data class Video(val fileId: Int, val caption: String, val size: Long, val duration: Int) : MessageContent()
    data class Sticker(val fileId: Int, val format: TdApi.StickerFormat) : MessageContent() // Puedes expandirlo después
    // Fase 3: contenidos adicionales
    data class Animation(val fileId: Int, val caption: String, val size: Long, val duration: Int) : MessageContent()
    data class Document(val fileId: Int, val fileName: String, val size: Long) : MessageContent()
    data class VoiceNote(val fileId: Int, val duration: Int, val size: Long) : MessageContent()
    data class Audio(val fileId: Int, val title: String, val performer: String, val size: Long, val duration: Int) : MessageContent()
    data class VideoNote(val fileId: Int, val size: Long, val duration: Int) : MessageContent()
}
