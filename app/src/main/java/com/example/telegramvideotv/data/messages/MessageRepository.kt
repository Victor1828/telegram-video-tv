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
import java.io.File
import java.io.FileOutputStream
import java.util.zip.GZIPInputStream

class MessageRepository() {
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
            else -> MessageContent.Text("[Contenido no soportado]")
        }
        return MessageItem(this.id, "Usuario", content, this.isOutgoing, this.date)
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

    fun downloadToCache(fileId: Int, context: Context, onReady: (String) -> Unit) {
        client?.send(TdApi.DownloadFile(fileId, 1, 0, 0, true)) { obj ->
            if (obj is TdApi.File && obj.local.isDownloadingCompleted) {
                val sourceFile = File(obj.local.path)

                // Creamos el destino en la carpeta de CACHÉ del sistema
                val cacheFile = File(context.cacheDir, "tdlib_cache_${sourceFile.name}")

                if (sourceFile.exists()) {
                    // Movemos el archivo de la carpeta /files a /cache
                    sourceFile.renameTo(cacheFile)

                    // Le avisamos a la UI que use la ruta del caché
                    onReady(cacheFile.absolutePath)

                    // IMPORTANTE: Para que TDLib no intente buscarlo en la ruta vieja
                    // o lo vuelva a descargar, podemos dejarlo así.
                    // Al estar en caché, Android lo borrará si necesita esos 800MB.
                }
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
