package com.example.telegramvideotv.ui.messages

import com.example.telegramvideotv.core.client.TdLibDataSource
import com.example.telegramvideotv.data.messages.MessageContent
import com.example.telegramvideotv.data.messages.MessageItem
import com.example.telegramvideotv.data.messages.MessageRepository

import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import android.view.View
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaSession
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ClickableSurfaceShape
import androidx.tv.material3.Glow
import androidx.tv.material3.Icon
import androidx.tv.material3.IconButton
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import kotlin.text.get

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageListScreen(messages: List<MessageItem>, onBack: () -> Unit, messageRepository: MessageRepository, onNavigateToVideo: (Int, String) -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mensajes") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (messages.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    reverseLayout = true // Los más nuevos abajo
                ) {
                    // Invertimos la lista para que el más nuevo esté abajo con reverseLayout
                    items(messages.reversed(), key = { it.id }) { msg ->
                        MessageBubble(msg, messageRepository, onNavigateToVideo)
                    }
                }
            }
        }
//        LazyColumn(
//            modifier = Modifier.padding(padding),
//            reverseLayout = true // Los mensajes nuevos suelen aparecer abajo
//        ) {
//            items(messages) { message ->
//                Surface(modifier = Modifier.padding(8.dp).widthIn(max = 400.dp)) {
//                    MessageBubble(message, messageRepository, onNavigateToVideo)
//                }
//            }
//        }
    }
}

@Composable
fun MessageBubble(message: MessageItem, messageRepository: MessageRepository, onNavigateToVideo: (Int, String) -> Unit) {
    val alignment = if (message.isOutgoing) Alignment.End else Alignment.Start
    var isFocused by remember { mutableStateOf(false) }
    val color = if (isFocused) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    val focusRequester = remember { FocusRequester() }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp), horizontalAlignment = alignment) {
        Surface(modifier = Modifier.onFocusChanged { isFocused = it.isFocused }.focusRequester(focusRequester).padding(4.dp)) {
            when (val content = message.content) {
                is MessageContent.Text -> Text(content.text, modifier = Modifier.padding(8.dp))
                //is MessageContent.Photo -> TelegramImage(content.fileId)
                is MessageContent.Video -> VideoMessageItem(content, messageRepository, onNavigateToVideo)
                else -> {}
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoViewContainer(exoPlayer: ExoPlayer) {
    val focusRequester = remember { FocusRequester() }
    val context = LocalContext.current

    // Usamos remember para mantener la misma instancia de MediaSession
    // y DisposableEffect para crearla/destruirla correctamente
    DisposableEffect(exoPlayer) {
        val mediaSession = MediaSession.Builder(context, exoPlayer)
            .setId("video_session_${System.currentTimeMillis()}") // ID único
            .build()

        onDispose {
            mediaSession.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            PlayerView(ctx).apply {
                player = exoPlayer
                useController = true
                focusable = View.FOCUSABLE_AUTO
                isFocusableInTouchMode = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                controllerAutoShow = true
                controllerHideOnTouch = true
            }
        },
        modifier = Modifier.fillMaxSize().focusRequester(focusRequester).focusable()
    )

    LaunchedEffect(Unit) {
        focusRequester.requestFocus() // Forzamos que el control remoto controle el video
    }
}

@OptIn(UnstableApi::class)
@Composable
fun TelegramVideoPlayer(size: Long, fileId: Int, client: Client?) {
    val context = LocalContext.current
    val playbackState = remember { mutableStateOf(Player.STATE_IDLE) }
    val errorMessage = remember { mutableStateOf<String?>(null) }

    LaunchedEffect(fileId) {
        // 1. Cancelamos cualquier descarga previa para limpiar la cola
        client?.send(TdApi.CancelDownloadFile(fileId, false)) {
            // 2. Iniciamos la descarga en modo "Streaming" (el 'true' al final es vital)
            // El offset 0 es solo para arrancar; el DataSource pedirá el resto
            client.send(TdApi.DownloadFile(fileId, 32, 0, 0, true)) { }
        }
//        // 1. Priorizamos el inicio del archivo de forma agresiva
//        client?.send(TdApi.DownloadFile(fileId, 32, 0, 0, true)) { result ->
//            if (result is TdApi.Error) {
//                Log.e("Streaming", "Error al iniciar descarga: ${result.message}")
//            } else {
//                Log.d("Streaming", "Descarga de cabecera iniciada correctamente")
//            }
//        }
    }

    // 1. Construimos el reproductor

    // Inicializamos ExoPlayer
    val exoPlayer = remember {
        // 2. Creamos la factoría personalizada
        val dataSourceFactory = DataSource.Factory {
            TdLibDataSource(client, fileId, size)
        }

        // 3. Creamos la fuente de medios
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri("telegram://file/$fileId"))

        ExoPlayer.Builder(context).build().apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = false // No auto-reproducir para no saturar
        }
    }

    LaunchedEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e("ExoPlayer_Error", "Error de reproducción: ${error.message}")
                errorMessage.value = "Error de conexión con Telegram. Revisa tu internet."
                // Aquí podrías mostrar un Toast o un diálogo:
                // "Error de conexión. Reintentando..."

                // Opcional: Reintentar automáticamente
                exoPlayer.prepare()
                exoPlayer.play()
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState.value = state
                Log.d("ExoPlayer_Status", "Estado: $state")
            }
        }
        exoPlayer.addListener(listener)
    }

    // Gestionar el ciclo de vida para liberar el reproductor
    DisposableEffect(fileId) {
        onDispose {
            exoPlayer.stop()
            exoPlayer.release()
            // Indica a TDLib que ya no priorice este archivo y puede borrarlo de la caché
            client?.send(TdApi.CancelDownloadFile(fileId, true)) {
                // Opcional: Borrar el archivo localmente si ya se descargó mucho
                client.send(TdApi.DeleteFile(fileId)) {
                    Log.d("STORAGE", "Archivo $fileId eliminado para liberar espacio")
                }
            }
        }
    }

    // El contenedor visual
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).onKeyEvent({ keyEvent ->
        if (keyEvent.type == KeyEventType.KeyDown) {
            when (keyEvent.nativeKeyEvent.keyCode) {
                KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                    // Si presionan OK, mostrar/ocultar controles o pausar
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE -> {
                    if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                    true
                }
                KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                    exoPlayer.seekTo(exoPlayer.currentPosition + 10000) // +10 seg
                    true
                }
                KeyEvent.KEYCODE_MEDIA_REWIND -> {
                    exoPlayer.seekTo(exoPlayer.currentPosition - 10000) // -10 seg
                    true
                }
                else -> false
            }
        } else false
    })) {
        VideoViewContainer(exoPlayer)

        // Cargando...
        if (playbackState.value == Player.STATE_BUFFERING && errorMessage.value == null) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color.White)
        }

        // OVERLAY DE ERROR
        errorMessage.value?.let { message ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.8f)) // Fondo oscuro
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = message,
                        color = Color.White,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))

                    // Botón de Reintento (importante que sea enfocable para TV)
                    Button(
                        onClick = {
                            errorMessage.value = null
                            exoPlayer.prepare()
                            exoPlayer.play()
                        },
                        colors = ButtonDefaults.colors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("Reintentar conexión")
                    }
                }
            }
        }
    }
}

@Composable
fun VideoMessageItem(content: MessageContent.Video, messageRepository: MessageRepository, onNavigateToVideo: (Int, String) -> Unit) {
    // Este es el "Thumbnail" o botón que ve el usuario en el chat
    val filePaths by messageRepository.filePaths.collectAsState()
    val path = filePaths[content.fileId]

    Surface(
        onClick = {
            // Buscamos el path en el mapa, si no existe usamos ""
            val currentPath = messageRepository.filePaths.value[content.fileId] ?: ""

            // Solo codificamos si no es nulo.
            // Usamos un valor seguro para la ruta de navegación.
            val safePath = Uri.encode(currentPath)

            onNavigateToVideo(content.fileId, safePath)
        },
        modifier = Modifier
            .size(width = 320.dp, height = 180.dp) // Proporción 16:9 para TV
            .padding(8.dp),
        shape = ClickableSurfaceDefaults.shape(shape = RoundedCornerShape(12.dp)),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.1f) // Efecto visual de enfoque
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                "Streaming disponible",
                modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}
