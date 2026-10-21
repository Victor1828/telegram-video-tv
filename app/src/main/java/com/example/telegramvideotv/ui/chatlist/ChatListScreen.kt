package com.example.telegramvideotv.ui.chatlist

import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlayCircleFilled
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Badge
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ListItem
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.rememberLottieComposition
import com.example.telegramvideotv.core.client.TelegramClient
import com.example.telegramvideotv.data.chatlist.ChatItem
import com.example.telegramvideotv.data.messages.MessageContent
import com.example.telegramvideotv.data.messages.MessageItem
import com.example.telegramvideotv.data.messages.MessageRepository
import org.drinkless.tdlib.TdApi
import java.io.File
import kotlin.math.ln
import kotlin.math.pow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(chats: List<ChatItem>, messages: List<MessageItem>, filteredMessages: List<MessageItem>, messageRepository: MessageRepository, onNavigateToVideo: (Int, Long) -> Unit) {
//    Scaffold(
//        topBar = {
//            TopAppBar(title = { Text("Telegram Clone") })
//        }
//    ) { padding ->
//        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
//            items(chats) { chat ->
//                ChatItemRow(chat, onChatClick)
//                HorizontalDivider(thickness = 0.5.dp)
//            }
//        }
//    }
    val searchFocusRequester = remember { FocusRequester() }
    val chatListFocusRequester = remember { FocusRequester() }

    // Estado para saber dónde estamos parados
    var isSearchBarFocused by remember { mutableStateOf(false) }
    var isMessageListFocused by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize().background(Color(0xFF0e1621)).onPreviewKeyEvent({keyEvent ->
        // Detectamos si es el botón ATRÁS y si se está presionando (KeyDown)
        if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_BACK &&
            keyEvent.type == KeyEventType.KeyDown) {

            // CASO 1: Si no estamos en la búsqueda ni en los chats,
            // asumimos que estamos en los mensajes.
            if (!isSearchBarFocused && !isMessageListFocused) {
                searchFocusRequester.requestFocus()
                messageRepository.setSearchQuery("")
                return@onPreviewKeyEvent true // "true" significa: yo manejé el botón, no cierres la app
            }

            // CASO 2: Si ya estamos en la búsqueda, saltamos a los chats
            if (isSearchBarFocused) {
                chatListFocusRequester.requestFocus()
                return@onPreviewKeyEvent true
            }
        }
        false // "false" significa: que el sistema haga lo que le toca (ej. cerrar app si estamos en los chats)
    })) {
        Column(modifier = Modifier.weight(0.4f).fillMaxHeight().border(0.5.dp, Color.Black.copy(alpha = 0.2f))) {
            Text(
                "Telegram TV",
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                modifier = Modifier.padding(16.dp)
            )
            LazyColumn(modifier = Modifier.focusRequester(chatListFocusRequester).onFocusChanged({ isMessageListFocused = it.isFocused })) {
                items(chats) { chat ->
                    ChatItemRow(
                        chat = chat,
                        isSelected = chat.id == messageRepository.currentOpenChatId, // Marcar cuál está abierto
                        messageRepository = messageRepository
                    )
                }
            }
        }

        val searchQuery by messageRepository.searchQuery.collectAsState()
//
//        Column(modifier = Modifier.fillMaxSize()) {
//            // 1. Barra de búsqueda arriba
//            SearchBar(
//                query = searchQuery,
//                onQueryChange = { messageRepository.setSearchQuery(it) }
//            )
//
//            // 2. Resultados abajo (pueden ser mensajes o miniaturas de video)
//            MessageListContent(messages, messageRepository, onNavigateToVideo) // Lista normal
//        }

        // PANEL DERECHO: CONTENIDO DEL CHAT (60% del ancho)
        Box(modifier = Modifier.weight(0.6f).fillMaxHeight().background(Color(0xFF17212b))) {
            if (messageRepository.currentOpenChatId != null) {
                // Aquí usamos tu lógica de mensajes pero adaptada
                //MessageListContent(messages, messageRepository, onNavigateToVideo)
                Column(modifier = Modifier.fillMaxSize()) {
                    // 1. Barra de búsqueda arriba
                    SearchBar(
                        query = searchQuery,
                        onQueryChange = { messageRepository.setSearchQuery(it) },
                        modifier = Modifier.focusRequester(searchFocusRequester).onFocusChanged({ isSearchBarFocused = it.isFocused }),
                        isFocused = isSearchBarFocused,
                        onSearchExecuteQuery = {
                            messageRepository.searchMessages(messageRepository.currentOpenChatId, searchQuery)
                        }
                    )

                    Box(modifier = Modifier
                        .weight(1f)
                        .onFocusChanged({ isMessageListFocused = it.isFocused })
                    ) {
                        if (filteredMessages.isEmpty()) {
                            // 2. Resultados abajo (pueden ser mensajes o miniaturas de video)
                            MessageListContent(messages, messageRepository, onNavigateToVideo) // Lista normal
                        } else {
                            // 2. Resultados abajo (pueden ser mensajes o miniaturas de video)
                            MessageListContent(filteredMessages, messageRepository, onNavigateToVideo) // Lista normal
                        }
                    }
                }
            } else {
                // Estado vacío
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.AutoMirrored.Default.Send, contentDescription = null, tint = Color(0xFF708499), modifier = Modifier.size(64.dp))
                    Text("Selecciona un chat para ver los mensajes", color = Color(0xFF708499))
                }
            }
        }
    }
}

@Composable
fun MessageListContent(
    messages: List<MessageItem>,
    messageRepository: MessageRepository,
    onNavigateToVideo: (Int, Long) -> Unit
) {
    Log.d("UI_DEBUG", "Renderizando ${messages.size} mensajes")
    Box(modifier = Modifier.fillMaxSize()) {
        // Fondo con patrón sutil de Telegram (puedes usar un color sólido o imagen)
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0e1621)))

        if (messages.isEmpty()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF2481cc))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                reverseLayout = true // Mensajes nuevos abajo
            ) {
                items(messages.reversed(), key = { it.id }) { msg ->
                    MessageBubble(msg, messageRepository, onNavigateToVideo)
                }
            }
        }
    }
}

fun onChatClick(chatId: Long, messageRepository: MessageRepository) {
    messageRepository.currentOpenChatId?.let { oldId: Long ->
        Log.d("stream", "${oldId}")
        Log.d("stream", "${chatId}")
        // Le decimos a TDLib que ya no nos interesa este chat por ahora
        messageRepository.client?.send(TdApi.CloseChat(oldId)) {}
        // Si tienes una lógica de limpieza de archivos temporales, actívala aquí
    }
    messageRepository.setCurrentChat(chatId)
    // Avisamos a TDLib que el chat está abierto (para el "visto" y priorizar tráfico)
    messageRepository.client?.send(TdApi.OpenChat(chatId)) {
        messageRepository.loadMessages(chatId)
    }
}

@Composable
fun StickerItem(sticker: MessageContent.Sticker, messageRepository: MessageRepository) {
    // Usamos el mismo truco de descarga que con las miniaturas
    val stickerPath = remember { mutableStateOf<String?>(null) }
    val stickerId = sticker.fileId
    val context = LocalContext.current
    val isAnimated = sticker.format is TdApi.StickerFormatTgs

    LaunchedEffect(stickerId) {
        messageRepository.downloadToCache(stickerId, context) { path ->
            if (isAnimated) {
                // Si es animado, lo descomprimimos a un archivo .json temporal
                val sourceFile = File(path)
                val jsonFile = File(context.cacheDir, "${sourceFile.nameWithoutExtension}.json")

                if (!jsonFile.exists()) {
                    try {
                        messageRepository.decompressTgs(sourceFile, jsonFile)
                    } catch (e: Exception) {
                        Log.e("LOTTIE_ERROR", "Fallo al descomprimir TGS", e)
                    }
                }
                stickerPath.value = jsonFile.absolutePath
            } else {
                stickerPath.value = path
            }
        }
    }

    Box(
        modifier = Modifier
            .size(160.dp) // Tamaño ideal para TV
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (isAnimated && stickerPath.value != null) {
            // Renderizado con Lottie
            val composition by rememberLottieComposition(
                LottieCompositionSpec.File(stickerPath.value!!)
            )
            LottieAnimation(
                composition = composition,
                iterations = LottieConstants.IterateForever,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            // Renderizado estático con Coil
            AsyncImage(
                model = stickerPath.value,
                contentDescription = "Sticker Estático",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ChatAvatar(photoFileId: Int?, messageRepository: MessageRepository) {
    val avatarPath = remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    LaunchedEffect(photoFileId) {
//        messageRepository.client?.send(TdApi.DownloadFile(photoFileId ?: 0, 1, 0, 0, true)) { file ->
//            if (file is TdApi.File) {
//                avatarPath.value = file.local.path
//            }
//        }
        messageRepository.downloadToCache(photoFileId ?: 0, context) { path ->
            avatarPath.value = path
        }
    }

    AsyncImage(
        model = avatarPath.value,
        contentDescription = "Avatar",
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape), // Esto le da el estilo circular de Telegram
        contentScale = ContentScale.Crop,
    )
}

@Composable
fun ChatItemRow(chat: ChatItem, isSelected: Boolean, messageRepository: MessageRepository) {
    var isFocused by remember { mutableStateOf(false) }
    val chatId = chat.id

//    LaunchedEffect(chatId) {
//        messageRepository.setCurrentChat(chatId)
//        // Avisamos a TDLib que el chat está abierto (para el "visto" y priorizar tráfico)
//        messageRepository.client?.send(TdApi.OpenChat(chatId)) {
//            messageRepository.loadMessages(chatId)
//        }
//    }
//
//    DisposableEffect(chatId) {
//        onDispose {
//            // 3. Al salir (presionar atrás), limpiamos el ID
//            // Así evitamos que mensajes de este chat sigan actualizando la UI en fondo
//            messageRepository.setCurrentChat(null)
//            messageRepository.client?.send(TdApi.CloseChat(chatId)) { }
//        }
//    }

    Surface(
        onClick = { onChatClick(chat.id, messageRepository) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .onFocusChanged { isFocused = it.isFocused },
        shape = RoundedCornerShape(10.dp),
        // Si tiene el foco es azul brillante, si está seleccionado pero sin foco es un gris azulado
        color = when {
            isFocused -> Color(0xFF2481cc)
            isSelected -> Color(0xFF2b5278).copy(alpha = 0.5f)
            else -> Color.Transparent
        }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Indicador vertical de chat seleccionado
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .width(4.dp)
                        .height(24.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF4ecaff))
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // Avatar
//            Surface(
//                modifier = Modifier.size(44.dp),
//                shape = CircleShape,
//                color = Color(0xFF2b5278)
//            ) {
//                Box(contentAlignment = Alignment.Center) {
//                    Text(chat.title.take(1), color = Color.White)
//                }
//            }

            ChatAvatar(chat.photoFileId, messageRepository)

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = chat.title,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Text(
                    text = chat.lastMessage,
                    color = if (isFocused) Color.White else Color(0xFF708499),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }

//    ListItem(
//        selected = false,
//        onClick = { onChatClick(chat.id) },
//        headlineContent = {
//            Text(text = chat.title)
//        },
//        supportingContent = {
//            Text(text = chat.lastMessage)
//        },
//        leadingContent = {
//            // Tu círculo de avatar
//            Surface(
//                modifier = Modifier.size(40.dp),
//                shape = CircleShape,
//                color = Color.Gray
//            ) { /* ... */ }
//        }
//    )
//    Row(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(16.dp),
//        verticalAlignment = Alignment.CenterVertically
//    ) {
//        // Avatar Temporal
//        Box(
//            modifier = Modifier
//                .size(50.dp)
//                .background(Color.Gray, CircleShape),
//            contentAlignment = Alignment.Center
//        ) {
//            Text(chat.title.take(1), color = Color.White)
//        }
//
//        Spacer(modifier = Modifier.width(16.dp))
//
//        Column(modifier = Modifier.weight(1f)) {
//            Text(text = chat.title, fontWeight = FontWeight.Bold, maxLines = 1)
//            Text(text = chat.lastMessage, style = MaterialTheme.typography.bodySmall, maxLines = 1)
//        }
//
//        if (chat.unreadCount > 0) {
//            Badge { Text("${chat.unreadCount}") }
//        }
//    }
}

@Composable
fun MessageBubble(msg: MessageItem, messageRepository: MessageRepository, onNavigateToVideo: (Int, Long) -> Unit) {
//    var isFocused by remember { mutableStateOf(false) }
//
//    // El color de fondo cambia según si es mío o del otro, y si tiene el foco del control remoto
//    val bubbleColor = when {
//        isFocused -> Color(0xFF2b5278) // Color al estar seleccionado
//        msg.isOutgoing -> Color(0xFF2b5278) // Azul Telegram (Mío)
//        else -> Color(0xFF182533) // Gris azulado (Otro)
//    }
//
//    Column(
//        modifier = Modifier
//            .fillMaxWidth()
//            .padding(vertical = 4.dp, horizontal = 12.dp),
//        horizontalAlignment = if (msg.isOutgoing) Alignment.End else Alignment.Start
//    ) {
//        Surface(
//            onClick = {
//                if (msg.content is MessageContent.Video) {
//                    val content = msg.content
//                    val currentPath = messageRepository.filePaths.value[content.fileId] ?: ""
//                    val safePath = Uri.encode(currentPath)
//
//                    Log.d("stream", "Navegando al video: ${content.fileId}")
//                    onNavigateToVideo(content.fileId, safePath)
//                }
//            },
//            modifier = Modifier
//                .onFocusChanged { isFocused = it.isFocused }
//                .focusable()
//                .widthIn(max = 450.dp), // Un poco más ancho para TV
//            shape = RoundedCornerShape(
//                topStart = 16.dp,
//                topEnd = 16.dp,
//                bottomStart = if (msg.isOutgoing) 16.dp else 2.dp,
//                bottomEnd = if (msg.isOutgoing) 2.dp else 16.dp
//            ),
//            color = bubbleColor,
//            border = if (isFocused) BorderStroke(2.dp, Color.White) else null,
////            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f) // Pequeño salto visual
//        ) {
//            Column(modifier = Modifier.padding(12.dp)) {
//                when (val content = msg.content) {
//                    is MessageContent.Text -> {
//                        Text(content.text, color = Color.White, style = MaterialTheme.typography.bodyLarge)
//                    }
//                    is MessageContent.Video -> {
//                        VideoThumbnail()
//                    }
//                    else -> {}
//                }
//
//                // Hora y Double Check
//                Row(
//                    modifier = Modifier.align(Alignment.End),
//                    verticalAlignment = Alignment.CenterVertically
//                ) {
//                    Text(
//                        "12:45 PM",
//                        style = MaterialTheme.typography.labelSmall,
//                        color = Color.White.copy(alpha = 0.5f)
//                    )
//                    if (msg.isOutgoing) {
//                        Spacer(Modifier.width(4.dp))
//                        Icon(Icons.Default.DoneAll, "Leído", modifier = Modifier.size(14.dp), tint = Color(0xFF4ecaff))
//                    }
//                }
//            }
//        }
//    }
    var isFocused by remember { mutableStateOf(false) }

    val bubbleColor = when {
        isFocused -> Color(0xFF2481cc) // Azul vibrante al estar enfocado
        msg.isOutgoing -> Color(0xFF2b5278)
        else -> Color(0xFF182533)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = 12.dp),
        horizontalAlignment = if (msg.isOutgoing) Alignment.End else Alignment.Start
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 450.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bubbleColor)
                .onFocusChanged { isFocused = it.isFocused }
                // Usamos clickable directamente en el modificador
                .clickable {
                    if (msg.content is MessageContent.Video) {
                        val content = msg.content
                        onNavigateToVideo(content.fileId, content.size)
                    }
                }
                .border(
                    width = if (isFocused) 2.dp else 0.dp,
                    color = if (isFocused) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(12.dp)
                )
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                when (val content = msg.content) {
                    is MessageContent.Text -> {
                        Text(text = content.text, color = Color.White)
                    }
                    is MessageContent.Video -> {
                        // El Thumbnail ya NO debe tener nada de lógica de click
                        VideoThumbnail(content.duration.toTimeFormat(), content.size.toReadableFileSize(), content.caption)
                        //VideoThumbnail(content.duration, content.fileSize, content.caption)
                    }
                    is MessageContent.Photo -> {
                        TelegramGenericCard(msg, onClick = {})
                    }
                    is MessageContent.Sticker -> {
                        StickerItem(content, messageRepository)
                    }
                    else -> {}
                }

                val msgDate = msg.date
                // Hora y Estado
                Text(
                    text = msgDate.toReadableDate(),
                    modifier = Modifier.align(Alignment.End),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

fun Long.toReadableFileSize(decimals: Int = 1): String {
    if (this < 0) return "Invalid size"
    if (this == 0L) return "0 B"

    val units = arrayOf("B", "KB", "MB", "GB", "TB", "PB")
    val k = 1024.0
    val dm = decimals.coerceAtLeast(0)

    val i = (ln(this.toDouble()) / ln(k)).toInt()
    val size = this / k.pow(i)

    return if (i == 0) {
        "$this ${units[i]}"
    } else {
        "%.${dm}f ${units[i]}".format(size)
    }
}

fun Int.toTimeFormat(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60

    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

fun Int.toReadableDate(pattern: String = "HH:mm"): String {
    // TDLib devuelve segundos, Java/Android usa milisegundos
    val date = java.util.Date(this.toLong() * 1000)
    val format = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
    return format.format(date)
}

@Composable
fun VideoThumbnail(duration: String, fileSize: String, caption: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(Color.Black, RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center
    ) {
        // Aquí podrías cargar una imagen miniatura con Coil
        Icon(
            imageVector = Icons.Default.PlayCircleFilled,
            contentDescription = "Play",
            modifier = Modifier.size(60.dp),
            tint = Color.White.copy(alpha = 0.8f)
        )

        // Etiqueta de duración o tamaño
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {

            Column {
                Text(
                    text = duration,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f)
                )
                Text(
                    text = fileSize,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }
        }
    }
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            text = caption,
            //maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White
        )
    }
}

@Composable
fun TelegramGenericCard(
    item: MessageItem,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }

    // Obtenemos el icono según el tipo de archivo si no hay miniatura
//    val defaultIcon = when {
//        item.isPdf -> Icons.Default.PictureAsPdf
//        item.isApk -> Icons.Default.Android
//        item.isZip -> Icons.Default.FolderZip
//        else -> Icons.Default.InsertDriveFile
//    }

    val defaultIcon = Icons.AutoMirrored.Default.InsertDriveFile

    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        modifier = Modifier
            .width(280.dp)
            .onFocusChanged { isFocused = it.isFocused }
            .padding(8.dp),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color(0xFF2B2B2B), // Gris oscuro Telegram
            focusedContainerColor = Color(0xFF353535)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color(0xFF40A7E3)))
        )
    ) {
        Column {
            // Parte Superior: Visual
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color.Black.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                // Si es imagen, cargamos miniatura. Si no, el icono default.
//                if (item.hasThumbnail) {
//                    val thumbPath by rememberThumbnailPath(item.thumbnailId)
//                    AsyncImage(
//                        model = thumbPath,
//                        contentDescription = null,
//                        contentScale = ContentScale.Crop,
//                        modifier = Modifier.fillMaxSize()
//                    )
//                } else {
//                    Icon(
//                        imageVector = defaultIcon,
//                        contentDescription = null,
//                        modifier = Modifier.size(48.dp),
//                        tint = Color(0xFF40A7E3)
//                    )
//                }
                Icon(
                    imageVector = defaultIcon,
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                    tint = Color(0xFF40A7E3)
                )
            }

            // Parte Inferior: Información y Caption
            Column(modifier = Modifier.padding(12.dp)) {
                // Título del archivo o nombre
                Text(
                    //text = item.fileName ?: "Archivo sin nombre",
                    text = "Archivo sin nombre",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Caption (El texto que acompaña al mensaje)
                when (item.content) {
                    is MessageContent.Photo -> {
                        if (item.content.caption.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = item.content.caption,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.LightGray,
                                //maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    else -> {}
                }

                // Tamaño del archivo
                Text(
                    //text = item.formattedSize,
                    text = "2 kb",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFF40A7E3).copy(alpha = 0.8f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
fun SearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier, // <--- Es vital recibir este modifier
    isFocused: Boolean,
    onSearchExecuteQuery: (String) -> Unit
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(
                color = if (isFocused) Color.White.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(8.dp)
            )
            .border(
                width = 2.dp,
                color = if (isFocused) Color.Gray else Color.Gray.copy(alpha = 0.5f),
                shape = RoundedCornerShape(8.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = "Buscar",
            tint = if (isFocused) Color.Gray else Color.White,
            modifier = Modifier.padding(start = 12.dp, end = 8.dp).size(24.dp)
        )

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            textStyle = TextStyle(color = Color.White, fontSize = 18.sp),
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 12.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, keyboardType = KeyboardType.Text),
            keyboardActions = KeyboardActions(onSearch = {
                onSearchExecuteQuery(query)
                keyboardController?.hide()
            }),
            singleLine = true,
            cursorBrush = SolidColor(Color.Gray),
            decorationBox = { innerTextField ->
                if (query.isEmpty()) {
                    Text("Buscar...", color = Color.Gray)
                }
                innerTextField()
            }
        )
    }
//    Surface(
//        modifier = modifier
//            .fillMaxWidth()
//            .padding(16.dp),
//        onClick = { /* Al hacer click OK se abre el teclado */ },
//        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
//        colors = ClickableSurfaceDefaults.colors(
//            containerColor = Color(0xFF242f3d),
//            focusedContainerColor = Color(0xFF2b3948) // Color más claro al tener el foco
//        ),
//        border = ClickableSurfaceDefaults.border(
//            focusedBorder = Border(BorderStroke(2.dp, Color(0xFF50a2e9)))
//        )
//    ) {
//        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
//            Icon(Icons.Default.Search, contentDescription = null, tint = Color.White)
//            Spacer(Modifier.width(8.dp))
//            Text(
//                text = if (query.isEmpty()) "Buscar..." else query,
//                color = if (query.isEmpty()) Color.Gray else Color.White
//            )
//        }
//    }
}