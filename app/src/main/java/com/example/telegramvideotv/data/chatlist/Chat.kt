package com.example.telegramvideotv.data.chatlist

data class ChatItem(
    val id: Long,
    val title: String,
    val lastMessage: String,
    val unreadCount: Int,
    val order: Long = 0L,
    val photoFileId: Int?
)
