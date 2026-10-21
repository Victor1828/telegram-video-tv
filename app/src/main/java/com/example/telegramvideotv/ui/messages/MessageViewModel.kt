package com.example.telegramvideotv.ui.messages

import androidx.lifecycle.ViewModel
import com.example.telegramvideotv.data.messages.MessageRepository

class MessageViewModel(private val repository: MessageRepository) : ViewModel() {
    val messages = repository.messages
    val filteredMessages = repository.filteredMessages
    val filePaths = repository.filePaths
    val messageRepository = repository

    fun setCurrentChat(chatId: Long?) {
        repository.currentOpenChatId = chatId
    }
    fun loadChatMessages(chatId: Long) {
        repository.loadMessages(chatId)
    }
}
