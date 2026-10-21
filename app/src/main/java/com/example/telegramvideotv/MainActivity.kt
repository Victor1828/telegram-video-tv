package com.example.telegramvideotv

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.tv.material3.*
import com.example.telegramvideotv.core.client.TelegramClient
import com.example.telegramvideotv.data.auth.AuthRepository
import com.example.telegramvideotv.data.auth.AuthState
import com.example.telegramvideotv.data.chatlist.ChatRepository
import com.example.telegramvideotv.data.messages.MessageRepository
import com.example.telegramvideotv.ui.auth.AuthViewModel
import com.example.telegramvideotv.ui.auth.LoginScreen
import com.example.telegramvideotv.ui.chatlist.ChatListScreen
import com.example.telegramvideotv.ui.chatlist.ChatViewModel
import com.example.telegramvideotv.ui.messages.MessageViewModel
import com.example.telegramvideotv.ui.messages.TelegramVideoPlayer
import com.example.telegramvideotv.ui.theme.TelegramVideoTvTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.File
import kotlin.getValue

class MainActivity : ComponentActivity() {
    private lateinit var authRepository: AuthRepository
    private lateinit var chatRepository: ChatRepository
    private lateinit var messageRepository: MessageRepository

    // Este es el manejador único que le pasas a Client.create()
    private val rootHandler = Client.ResultHandler { update ->
        // Ejecutamos en un hilo de fondo para no bloquear la app
        CoroutineScope(Dispatchers.Default).launch {

            when (update) {
                // 1. SIEMPRE VIGILAR EL ESTADO DE AUTH
                is TdApi.UpdateAuthorizationState -> {
                    authRepository.handleStateChange(update.authorizationState)
                }

                // 2. ACTUALIZACIONES DE CHATS (Solo si estamos logueados)
                is TdApi.UpdateNewChat,
                is TdApi.UpdateChatTitle,
                is TdApi.UpdateChatLastMessage,
                is TdApi.UpdateChatPosition -> {
                    chatRepository.handleChatUpdate(update)
                }

                // 3. ACTUALIZACIONES DE USUARIOS (Nombres, fotos de perfil)
                is TdApi.UpdateUser -> {
                    // userRepository.handleUserUpdate(update)
                }

                // 4. NUEVOS MENSAJES (Para notificaciones o chats abiertos)
                is TdApi.UpdateNewMessage -> {
                    val msg = update.message
                    // Solo procesamos si es el chat que estamos viendo
                    if (msg.chatId == messageRepository.currentOpenChatId) {
                        messageRepository.handleNewMessage(msg)
                    }
                }

                // En tu ResultHandler principal
                is TdApi.UpdateFile -> {
                    val file = update.file
                    if (file.local.isDownloadingCompleted) {
                        // Notificar a la UI o al Repositorio que el archivo ya tiene ruta:
                        // file.local.path
                        messageRepository.updateFileStatus(file.id, file.local.path)
                    }
                }
            }
        }
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()

        super.onCreate(savedInstanceState)

        val tdlibPath = baseContext.filesDir.absolutePath + "/tdlib"
        val dir = File(tdlibPath)
        if (!dir.exists()) dir.mkdirs()

        val mediaCacheDir = File(tdlibPath, "files/videos") // Ruta interna de TDLib para videos
        if (mediaCacheDir.exists()) {
            // Borrar videos que hayan quedado a medias de sesiones anteriores
            mediaCacheDir.listFiles()?.forEach { it.delete() }
            Log.d("STORAGE", "Caché de videos previa limpiada al arrancar.")
        }

        authRepository = AuthRepository()
        chatRepository = ChatRepository()
        messageRepository = MessageRepository()

        val client = Client.create(rootHandler, null, null)
        TelegramClient.client = client

        // Registrar el observador global
        ProcessLifecycleOwner.get().lifecycle.addObserver(AppLifecycleObserver(client))

        authRepository.initDatabasePath(tdlibPath)
        authRepository.initClient(client)
        chatRepository.initClient(client)
        messageRepository.initClient(client)

        val authViewModel = AuthViewModel(authRepository)
        val chatViewModel = ChatViewModel(chatRepository)
        val messageViewModel = MessageViewModel(messageRepository)

        setContent {
            TelegramVideoTvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    shape = RectangleShape,
                    colors = SurfaceDefaults.colors(containerColor = Color.White)
                ) {
                    MainNavigation(authViewModel, chatViewModel, messageViewModel)
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Ejecutamos una limpieza rápida de archivos huérfanos
        val optimizeFunction = TdApi.OptimizeStorage(
            200 * 1024 * 1024L, 0, 1, 0,
            arrayOf(
                TdApi.FileTypeVideo(),
                // Los stickers animados y los thumbnails de burbuja también
                // suelen acumularse en el file cache de TDLib entre sesiones.
                TdApi.FileTypeAnimation(),
                TdApi.FileTypeThumbnail()
            ),
            LongArray(0), LongArray(0), false, 0
        )
        TelegramClient.client?.send(optimizeFunction) {}
    }
}

@Composable
fun MainNavigation(authViewModel: AuthViewModel, chatViewModel: ChatViewModel, messageViewModel: MessageViewModel) {
    val authState by authViewModel.uiState.collectAsState()
    val messageRepository = messageViewModel.messageRepository
    val navController = rememberNavController()

    when (authState) {
        is AuthState.Authenticated -> {
            // El usuario ya entró, cargamos los chats
            NavHost(navController, "chat_list") {
                composable("chat_list") {
                    val chats by chatViewModel.chats.collectAsState()
                    val messages by messageViewModel.messages.collectAsState()
                    val filteredMessages by messageViewModel.filteredMessages.collectAsState()

                    ChatListScreen(chats, messages, filteredMessages, messageRepository, onNavigateToVideo = {fileId, size -> navController.navigate("video_player/${fileId}/${size}")})
                    LaunchedEffect(Unit) {
                        chatViewModel.loadInitialChats()
                    }
                    LaunchedEffect(filteredMessages) {
                        Log.d("STREAM_DEBUG", "La UI recibió ${filteredMessages.size} mensajes filtrados")
                    }
                }

                composable("chat_messages/{chatId}", listOf(navArgument("chatId") { type = NavType.LongType })) { backStackEntry ->
                    val chatId = backStackEntry.arguments?.getLong("chatId") ?: 0L
                    val messages by messageViewModel.messages.collectAsState()

                    LaunchedEffect(chatId) {
                        messageViewModel.setCurrentChat(chatId)
                        // Avisamos a TDLib que el chat está abierto (para el "visto" y priorizar tráfico)
                        TelegramClient.client?.send(TdApi.OpenChat(chatId)) {
                            messageViewModel.loadChatMessages(chatId)
                        }
                    }

                    DisposableEffect(chatId) {
                        onDispose {
                            // 3. Al salir (presionar atrás), limpiamos el ID
                            // Así evitamos que mensajes de este chat sigan actualizando la UI en fondo
                            messageViewModel.setCurrentChat(null)
                            TelegramClient.client?.send(TdApi.CloseChat(chatId)) { }
                        }
                    }

                    //MessageListScreen(messages, onBack = { navController.popBackStack() }, messageRepository = messageRepository, onNavigateToVideo = {fileId, uri -> navController.navigate("video_player/${fileId}/${uri}")})
                }

                composable(
                    route = "video_player/{fileId}/{size}",
                    arguments = listOf(
                        navArgument("fileId") { type = NavType.IntType },
                        navArgument("size") { type = NavType.LongType }
                    )
                ) { backStackEntry ->
                    val fileId = backStackEntry.arguments?.getInt("fileId") ?: 0
                    val size = backStackEntry.arguments?.getLong("size") ?: 0L

                    // Pantalla completa de video
                    TelegramVideoPlayer(size, fileId, messageRepository.client)

//                    // Manejar el botón de atrás para salir del video
//                    BackHandler {
//                        navController.popBackStack()
//                    }
                }
            }

            // Efecto de una sola vez para disparar la carga inicial
            LaunchedEffect(Unit) {
                chatViewModel.loadInitialChats()
            }
        }
        else -> {
            // Si no está autenticado, mostramos el flujo de Login
            LoginScreen(authViewModel)
        }
    }
}

class AppLifecycleObserver(private val client: Client) : DefaultLifecycleObserver {
    override fun onStop(owner: LifecycleOwner) {
        // La app pasó a segundo plano (el usuario apagó la TV o salió)
        Log.d("LIFECYCLE", "App en segundo plano. TDLib sigue vivo pero en pausa.")
    }

    override fun onDestroy(owner: LifecycleOwner) {
        // La app se va a cerrar definitivamente
        Log.d("LIFECYCLE", "Cerrando TDLib para evitar corrupción de archivos...")
        client.send(TdApi.Close()) {
            Log.d("LIFECYCLE", "Cliente de Telegram cerrado correctamente.")
        }
    }
}