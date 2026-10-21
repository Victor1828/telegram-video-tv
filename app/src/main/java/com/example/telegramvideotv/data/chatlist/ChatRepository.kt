package com.example.telegramvideotv.data.chatlist

import androidx.media3.common.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

class ChatRepository() {
    private var client: Client? = null
    private val _chats = MutableStateFlow<List<ChatItem>>(emptyList())
    val chats: StateFlow<List<ChatItem>> = _chats

    // Usamos un mapa temporal para organizar los chats por ID
    private val chatMap = mutableMapOf<Long, ChatItem>()

    fun initClient(clientInstance: Client) {
        // En la versión oficial, Client.create recibe:
        // 1. Un ResultHandler para actualizaciones (updates)
        // 2. Un ResultHandler para errores de actualización (opcional)
        // 3. Un ResultHandler para errores internos (opcional)
        client = clientInstance
    }

    fun handleChatUpdate(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateNewChat -> {
                val chat = update.chat
                val photoFileId = chat.photo?.big?.id
                val mainOrder = chat.positions.find { it.list is TdApi.ChatListMain }?.order ?: 0L
                val newItem = ChatItem(
                    id = chat.id,
                    title = chat.title,
                    lastMessage = "", // Se llenará con UpdateChatLastMessage
                    unreadCount = chat.unreadCount,
                    order = mainOrder,
                    photoFileId = photoFileId
                )
                chatMap[chat.id] = newItem
                updateUi()
            }
            is TdApi.UpdateChatPosition -> {
                // Este es el evento clave para el orden
                if (update.position.list is TdApi.ChatListMain) {
                    chatMap[update.chatId]?.let {
                        chatMap[update.chatId] = it.copy(order = update.position.order)
                        updateUi()
                    }
                }
            }
            is TdApi.UpdateChatLastMessage -> {
                val chatId = update.chatId
                val lastMessage = when (val content = update.lastMessage?.content) {
                    is TdApi.MessageText -> content.text.text
                    is TdApi.MessageVideo -> "🎥 Video"
                    is TdApi.MessagePhoto -> "🖼️ Foto"
                    else -> "Multimedia"
                }

                chatMap[chatId]?.let {
                    chatMap[chatId] = it.copy(lastMessage = lastMessage)
                    updateUi()
                }
            }
        }
    }

    private fun updateUi() {
        _chats.value = chatMap.values.toList().sortedByDescending { it.order } // Simplificado
    }

    fun loadChats() {
        // Esto le dice a TDLib que empiece a enviarnos la lista de los primeros 20 chats
        client?.send(TdApi.GetChats(TdApi.ChatListMain(), 20)) { }
    }
}
