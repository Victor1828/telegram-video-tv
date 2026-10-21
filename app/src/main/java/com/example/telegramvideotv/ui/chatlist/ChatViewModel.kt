package com.example.telegramvideotv.ui.chatlist

import androidx.lifecycle.ViewModel
import com.example.telegramvideotv.data.chatlist.ChatRepository

class ChatViewModel(private val repository: ChatRepository) : ViewModel() {
    val chats = repository.chats

    fun loadInitialChats() {
        repository.loadChats()
    }
}
