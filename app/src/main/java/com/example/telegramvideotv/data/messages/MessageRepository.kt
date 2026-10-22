package com.example.telegramvideotv.data.messages

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.State
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import com.example.telegramvideotv.data.users.UserRepository
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

class MessageRepository(val users: UserRepository) {
    var client: Client? = null
    private val messagesMap = MutableStateFlow<Map<Long, MessageItem>>(emptyMap())
    val messages: StateFlow<List<MessageItem>> = messagesMap
        .map { it.values.sortedBy { msg -> msg.date } } // Orden cronológico (antiguos arriba)
        .stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Lazily, emptyList())
    private val filteredMessagesMap = MutableStateFlow<Map<Long, MessageItem>>(emptyMap())
    val filteredMessages: StateFlow<List<MessageItem>> = filteredMessagesMap
        .map { it.values.sortedBy { msg -> msg.date } } // Orden cronológico (antiguos arriba)
        .stateIn(CoroutineScope(Dispatchers.IO), SharingStarted.Lazily, emptyList())
    private val _filePaths = MutableStateFlow<Map<Int, String>>(emptyMap())
    val filePaths: StateFlow<Map<Int, String>> = _filePaths
    var currentOpenChatId: Long? = null
    private var _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    // Fase 2: citas en curso de resolución (evita doble GetMessage del mismo reply)
    private val pendingReplyFetches = HashSet<Long>()

    // Fase 4b: estado de lectura del chat abierto (se inicializa con GetChat y
    // se mantiene al día con UpdateChatReadOutbox / UpdateChatReadInbox)
    private val _lastReadOutboxMessageId = MutableStateFlow(0L)
    val lastReadOutboxMessageId: StateFlow<Long> = _lastReadOutboxMessageId
    private val _lastReadInboxMessageId = MutableStateFlow(0L)
    val lastReadInboxMessageId: StateFlow<Long> = _lastReadInboxMessageId

    fun handleChatReadOutbox(chatId: Long, messageId: Long) {
        if (chatId == currentOpenChatId) {
            _lastReadOutboxMessageId.value = maxOf(_lastReadOutboxMessageId.value, messageId)
        }
    }

    fun handleChatReadInbox(chatId: Long, messageId: Long) {
        if (chatId == currentOpenChatId) {
            _lastReadInboxMessageId.value = maxOf(_lastReadInboxMessageId.value, messageId)
        }
    }

    private companion object {
        const val TAG = "MessageRepository"
        const val SNIPPET_MAX_LEN = 120
    }

    fun initClient(clientInstance: Client) {
        // En la versión oficial, Client.create recibe:
        // 1. Un ResultHandler para actualizaciones (updates)
        // 2. Un ResultHandler para errores de actualización (opcional)
        // 3. Un ResultHandler para errores internos (opcional)
        client = clientInstance
    }

    fun setCurrentChat(chatId: Long?) {
        currentOpenChatId = chatId
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun handleNewMessage(message: TdApi.Message) {
        messagesMap.update { current ->
            current + (message.id to message.toMessageItem())
        }
    }

    fun loadMessages(chatId: Long) {
        messagesMap.update { emptyMap() }
        // Fase 4b: reset del estado de lectura y carga inicial vía GetChat
        _lastReadOutboxMessageId.value = 0L
        _lastReadInboxMessageId.value = 0L
        client?.send(TdApi.GetChat(chatId)) { obj ->
            if (obj is TdApi.Chat) {
                _lastReadOutboxMessageId.value = obj.lastReadOutboxMessageId
                _lastReadInboxMessageId.value = obj.lastReadInboxMessageId
            }
        }
        // Pedimos los últimos 50 mensajes del chat
        client?.send(TdApi.GetChatHistory(chatId, 0, 0, 50, false)) { result ->
            when (result) {
                is TdApi.Messages -> {
                    val newItems = result.messages.associate { it.id to it.toMessageItem() }
                    // update es atómico y asegura que el StateFlow emita el cambio
                    messagesMap.update { current -> current + newItems }
                    if (result.messages.size == 1) {
                        loadMoreMessages(chatId)
                    }
                    Log.d("STREAM_DEBUG", "Cargados ${result.messages.size} mensajes para el chat $chatId")
                }
                is TdApi.Error -> {
                    Log.e("STREAM_DEBUG", "Error al cargar historial: ${result.message}")
                }
            }
        }
    }

    fun loadMoreMessages(chatId: Long) {
        // Obtenemos nuestra lista actual ordenada por fecha
        val currentMessages = messagesMap.value.values.sortedBy { it.date }

        // Si no hay mensajes, cargamos desde el principio (0)
        // Si hay, tomamos el ID del primero (el más antiguo)
        val lastMessageId = if (currentMessages.isEmpty()) 0L else currentMessages.first().id

        // Pedimos 50 mensajes ANTERIORES a ese ID (offset = 0, limit = 50)
        client?.send(TdApi.GetChatHistory(chatId, lastMessageId, 0, 50, false)) { result ->
            if (result is TdApi.Messages) {
                Log.d("stream", "content: ${result}")
                val olderItems = result.messages.associate { it.id to it.toMessageItem() }

                // Unimos los mensajes antiguos con los que ya tenemos
                messagesMap.update { current -> current + olderItems }

                Log.d("STREAM_DEBUG", "Cargados ${result.messages.size} mensajes antiguos")
            }
        }
    }

    // Función de extensión para convertir TdApi.Message a nuestro modelo
    private fun TdApi.Message.toMessageItem(): MessageItem {
        val content = when (val c = this.content) {
            is TdApi.MessageText -> MessageContent.Text(c.text.text)
            // Fase 4: emojis animados (big emoji / emojis con animación de Telegram)
            is TdApi.MessageAnimatedEmoji -> MessageContent.Text(c.emoji)
            is TdApi.MessagePhoto -> {
                val photoFile = c.photo.sizes.last().photo
                // Si la foto ya está en el disco, la registramos
                if (photoFile.local.isDownloadingCompleted) {
                    updateFileStatus(photoFile.id, photoFile.local.path)
                }
                MessageContent.Photo(photoFile.id, c.caption.text)
            }
            is TdApi.MessageVideo -> {
                val videoFile = c.video.video
                // Si el video ya está en el disco (caché), lo registramos
                if (videoFile.local.isDownloadingCompleted) {
                    updateFileStatus(videoFile.id, videoFile.local.path)
                }
                MessageContent.Video(videoFile.id, c.caption.text, videoFile.size, c.video.duration)
            }
            is TdApi.MessageSticker -> {
                val sticker = c.sticker
                MessageContent.Sticker(sticker.sticker.id, sticker.format)
            }
            is TdApi.MessageAnimation -> {
                val animFile = c.animation.animation
                if (animFile.local.isDownloadingCompleted) {
                    updateFileStatus(animFile.id, animFile.local.path)
                }
                MessageContent.Animation(animFile.id, c.caption.text, animFile.size, c.animation.duration)
            }
            is TdApi.MessageDocument -> {
                val docFile = c.document.document
                MessageContent.Document(docFile.id, c.document.fileName, docFile.size)
            }
            is TdApi.MessageVoiceNote -> {
                val voiceFile = c.voiceNote.voice
                MessageContent.VoiceNote(voiceFile.id, c.voiceNote.duration, voiceFile.size)
            }
            is TdApi.MessageAudio -> {
                val audioFile = c.audio.audio
                if (audioFile.local.isDownloadingCompleted) {
                    updateFileStatus(audioFile.id, audioFile.local.path)
                }
                MessageContent.Audio(audioFile.id, c.audio.title, c.audio.performer, audioFile.size, c.audio.duration)
            }
            is TdApi.MessageVideoNote -> {
                val noteFile = c.videoNote.video
                if (noteFile.local.isDownloadingCompleted) {
                    updateFileStatus(noteFile.id, noteFile.local.path)
                }
                MessageContent.VideoNote(noteFile.id, noteFile.size, c.videoNote.duration)
            }
            else -> MessageContent.Text("[Contenido no soportado]")
        }
        // Remitente: usuarios tienen id positivo, canales/chats autoenviados usan chatId
        val fromSender = when (val s = this.senderId) {
            is TdApi.MessageSenderUser -> s.userId
            is TdApi.MessageSenderChat -> s.chatId
            else -> 0L
        }
        // Reply (Fase 2): id del mensaje citado, si existe
        val replyTo = (this.replyTo as? TdApi.MessageReplyToMessage)?.messageId
        val item = MessageItem(
            id = this.id,
            senderId = fromSender,
            senderName = users.senderNameOf(fromSender),
            content = content,
            isOutgoing = this.isOutgoing,
            date = this.date,
            isEdited = this.editDate > 0,
            replyToMessageId = replyTo
        )
        // Resolvemos la cita (nombre + snippet) sin bloquear la conversión
        if (replyTo != null) resolveReply(this.chatId, replyTo, this.id)
        return item
    }

    /**
     * Fase 2: resuelve el preview de una cita. Si el mensaje citado ya está
     * en el mapa en memoria lo usamos al tiro; si no, pedimos GetMessage y
     * parcheamos el MessageItem cuando llegue la respuesta.
     */
    private fun resolveReply(chatId: Long, replyToMessageId: Long, originalMessageId: Long) {
        // Fast path: el mensaje citado ya está en memoria (chat abierto normal)
        val quoted = messagesMap.value[replyToMessageId]
        if (quoted != null) {
            applyReplyPreview(
                originalMessageId,
                quoted.senderName.ifEmpty { "Usuario" },
                quoted.content.toSnippetLabel()
            )
            return
        }

        // Evita pedir la misma cita dos veces (doble tap, recomposiciones…)
        if (!pendingReplyFetches.add(replyToMessageId)) return
        val c = client ?: return

        c.send(TdApi.GetMessage(chatId, replyToMessageId)) { obj ->
            if (obj is TdApi.Message) {
                // Nombre del autor de la cita (UserRepository lo cachea)
                val senderLong = when (val s = obj.senderId) {
                    is TdApi.MessageSenderUser -> s.userId
                    is TdApi.MessageSenderChat -> s.chatId
                    else -> 0L
                }
                if (senderLong != 0L) users.ensureSender(senderLong)
                val name = users.senderNameOf(senderLong).ifEmpty { "Usuario" }
                pendingReplyFetches.remove(replyToMessageId)
                applyReplyPreview(originalMessageId, name, obj.content.toSnippet())
            } else if (obj is TdApi.Error) {
                pendingReplyFetches.remove(replyToMessageId)
                Log.e(TAG, "GetMessage(reply) error: ${obj.code} ${obj.message}")
            }
        }
    }

    private fun applyReplyPreview(messageId: Long, senderName: String, snippet: String) {
        val preview = ReplyPreview(senderName, snippet.take(SNIPPET_MAX_LEN))
        messagesMap.update { map ->
            val updated = map[messageId]?.copy(replyPreview = preview) ?: return@update map
            map + (messageId to updated)
        }
        filteredMessagesMap.update { map ->
            val updated = map[messageId]?.copy(replyPreview = preview) ?: return@update map
            map + (messageId to updated)
        }
    }

    // Snippet corto para la cita, por tipo de contenido (TDLib remoto)
    private fun TdApi.MessageContent.toSnippet(): String = when (this) {
        is TdApi.MessageText -> text.text
        is TdApi.MessagePhoto -> caption.text.ifEmpty { "🖼 Foto" }
        is TdApi.MessageVideo -> caption.text.ifEmpty { "🎥 Video" }
        is TdApi.MessageAnimation -> caption.text.ifEmpty { "🎞 GIF" }
        is TdApi.MessageAnimatedEmoji -> emoji
        is TdApi.MessageSticker -> "🎯 Sticker"
        is TdApi.MessageVoiceNote -> "🎤 Nota de voz"
        is TdApi.MessageAudio -> audio.title.takeIf { it.isNotEmpty() } ?: audio.fileName
        is TdApi.MessageDocument -> document.fileName.takeIf { it.isNotEmpty() } ?: "📄 Documento"
        is TdApi.MessageVideoNote -> "⚪ Nota de video"
        else -> "Mensaje"
    }

    // Snippet corto para la cita, desde nuestro modelo en memoria
    private fun MessageContent.toSnippetLabel(): String = when (this) {
        is MessageContent.Text -> text
        is MessageContent.Photo -> caption.takeIf { it.isNotEmpty() } ?: "🖼 Foto"
        is MessageContent.Video -> caption.takeIf { it.isNotEmpty() } ?: "🎥 Video"
        is MessageContent.Sticker -> "🎯 Sticker"
        is MessageContent.Animation -> caption.takeIf { it.isNotEmpty() } ?: "🎞 GIF"
        is MessageContent.Document -> fileName.takeIf { it.isNotEmpty() } ?: "📄 Documento"
        is MessageContent.VoiceNote -> "🎤 Nota de voz"
        is MessageContent.Audio -> title.takeIf { it.isNotEmpty() } ?: "🎵 Audio"
        is MessageContent.VideoNote -> "⚪ Nota de video"
    }

    // Esta función la llamaremos desde el MainActivity cuando llegue un UpdateFile
    fun updateFileStatus(fileId: Int, path: String) {
        if (path.isNotEmpty()) {
            _filePaths.value += (fileId to path)
        }
    }

    fun searchMessages(chatId: Long?, query: String) {
        if (chatId == null || query.isEmpty()) {
            filteredMessagesMap.value = emptyMap()
            return
        }
        filteredMessagesMap.update { emptyMap() }
        val searchFunction = TdApi.SearchChatMessages(
            chatId,              // ID del chat actual
            null,               // El texto que el usuario escribe
            query,                // Filter: Puedes pasar TdApi.SearchMessagesFilterVideo() para buscar solo videos
            null,                   // fromMessageId: 0 para empezar desde el más reciente
            0,                   // offset: 0 para la primera página
            0,                  // limit: Cuántos resultados traer
            20,                // messageThreadId: Generalmente null
            null                // savedMessagesTopicId: 0 para chats normales
        )

        client?.send(searchFunction) { result ->
            Log.d("STREAM_DEBUG", "Iniciando búsqueda para: $query")
            Log.d("stream", "$result")
            when (result) {
                is TdApi.FoundChatMessages -> {
                    // IMPORTANTE: Convierte a MessageItem en el hilo de la respuesta

                    // Forzamos el hilo principal para la UI
//                    CoroutineScope(Dispatchers.Main).launch {
//                        val newItems = result.messages.associate { it.id to it.toMessageItem() }
//                        // update es atómico y asegura que el StateFlow emita el cambio
//                        messagesMap.update { current -> current + newItems }
//                        Log.d("STREAM_DEBUG", "Mapa actualizado con ${messagesMap} resultados")
//                    }
                    val newItems = result.messages.associate { it.id to it.toMessageItem() }
                    // update es atómico y asegura que el StateFlow emita el cambio
                    filteredMessagesMap.value = newItems
                    Log.d("STREAM_DEBUG", "Resultados encontrados y mapeados: ${newItems.size}")
                }

                is TdApi.Error -> {
                    Log.e("STREAM_DEBUG", "Error al cargar historial: ${result.message}")
                }
            }
        }
    }

    /**
     * Descarga un archivo pequeño de UI (avatar, sticker, miniatura de burbuja)
     * con TDLib y entrega a la UI la ruta LOCAL que YA gestiona TDLib.
     *
     * Cambio vs versión anterior: ya NO copiamos/renombramos a cacheDir:
     * - La copia duplicaba el uso de disco por cada thumbnail y dejaba
     *   huérfanos tdlib_cache_* acumulándose entre sesiones.
     * - Renombrar el archivo fuera de la ruta de TDLib rompía su registro
     *   interno de rutas (provocaba re-descargas innecesarias del mismo fileId).
     * TDLib mantiene estos archivos en su file cache, que se depura con
     * OptimizeStorage desde MainActivity. Prioridad 16 = descarga de UI rápida.
     */
    fun downloadToCache(fileId: Int, context: Context, onReady: (String) -> Unit) {
        client?.send(TdApi.DownloadFile(fileId, 16, 0, 0, true)) { obj ->
            when (obj) {
                is TdApi.File ->
                    if (obj.local.isDownloadingCompleted) {
                        onReady(obj.local.path)
                    } else {
                        Log.w("MessageRepository", "downloadToCache: archivo $fileId llegó sin completar")
                    }
                is TdApi.Error ->
                    Log.e("MessageRepository", "downloadToCache error ($fileId): ${obj.code} ${obj.message}")
            }
        }
    }

    fun decompressTgs(source: File, destination: File) {
        GZIPInputStream(source.inputStream()).use { input ->
            FileOutputStream(destination).use { output ->
                input.copyTo(output)
            }
        }
    }

//    fun downloadVideoIfNeeded(fileId: Int) {
//        // Le pedimos a TDLib que empiece la descarga
//        // priority 1-32, offset 0, limit 0, synchronous false
//        client?.send(TdApi.DownloadFile(fileId, 1, 0, 0, false)) {
//            // La respuesta aquí es solo un "Ok", el progreso llega por el Handler
//        }
//    }
//
//    fun startStreamingDownload(fileId: Int) {
//        // Pedimos a TDLib que empiece a descargar de forma persistente
//        // pero no esperamos a que termine para abrir el player
//        client?.send(TdApi.DownloadFile(fileId, 32, 0, 0, false)) { /* Inicia el flujo */ }
//    }
//
//    private fun cleanupVideoResources(fileId: Int) {
//        // 1. Primero cancelamos la descarga activa para detener el tráfico de red
//        client?.send(TdApi.CancelDownloadFile(fileId, true)) {
//            Log.d("STORAGE", "Descarga cancelada para el archivo: $fileId")
//
//            // 2. Una vez cancelada, ordenamos borrar el archivo físico
//            // Esto recuperará los MB usados inmediatamente
//            client?.send(TdApi.DeleteFile(fileId)) {
//                Log.d("STORAGE", "Archivo eliminado físicamente del disco")
//            }
//        }
//    }
}
