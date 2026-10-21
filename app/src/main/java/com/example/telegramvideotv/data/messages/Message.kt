package com.example.telegramvideotv.data.messages

import org.drinkless.tdlib.TdApi

data class MessageItem(
    val id: Long,
    val senderName: String,
    val content: MessageContent,
    val isOutgoing: Boolean,
    val date: Int
)

sealed class MessageContent {
    data class Text(val text: String) : MessageContent()
    data class Photo(val fileId: Int, val caption: String) : MessageContent()
    data class Video(val fileId: Int, val caption: String, val size: Long, val duration: Int) : MessageContent()
    data class Sticker(val fileId: Int, val format: TdApi.StickerFormat) : MessageContent() // Puedes expandirlo después
}
