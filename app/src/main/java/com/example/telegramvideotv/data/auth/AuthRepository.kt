package com.example.telegramvideotv.data.auth

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

class AuthRepository() {
    private var client: Client? = null
    private val _authState = MutableStateFlow<AuthState>(AuthState.Idle)
    private var databasePath: String? = ""
    val authState: StateFlow<AuthState> = _authState

    private val updateHandler = Client.ResultHandler { tdObject ->
        when (tdObject.constructor) {
            TdApi.UpdateAuthorizationState.CONSTRUCTOR -> {
                val update = tdObject as TdApi.UpdateAuthorizationState
                handleStateChange(update.authorizationState)
            }
        }
    }

    fun initClient(clientInstance: Client) {
        // En la versión oficial, Client.create recibe:
        // 1. Un ResultHandler para actualizaciones (updates)
        // 2. Un ResultHandler para errores de actualización (opcional)
        // 3. Un ResultHandler para errores internos (opcional)
        client = clientInstance
    }

    fun initDatabasePath(path: String) {
        databasePath = path
    }
    fun handleStateChange(state: TdApi.AuthorizationState) {
        when (state.constructor) {
            TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> setParameters()
            TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> _authState.value = AuthState.WaitPhoneNumber
            TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> _authState.value = AuthState.WaitCode
            TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                applyTdlibSettings()
                _authState.value = AuthState.Authenticated
            }
        }
    }

    private fun setParameters() {
        val params = TdApi.SetTdlibParameters().apply {
            apiId = 31707328
            apiHash = "5b81e261ecda84f08a0db056ee0e0c6c"
            useMessageDatabase = false
            useChatInfoDatabase = false
            useFileDatabase = true
            systemLanguageCode = "es"
            deviceModel = "Android"
            applicationVersion = "1.0"
            databaseDirectory = databasePath
            filesDirectory = "$databasePath/files"
        }
        client?.send(params) {}
    }

    private fun applyTdlibSettings () {
        client?.send(TdApi.SetLogVerbosityLevel(1)) { } // Solo errores críticos

        // Parámetros de configuración
        val maxSizeBytes = 200 * 1024 * 1024L // 200 MB de límite (dejamos margen en la TV)
        val maxTtlSeconds = 3600               // 1 hora (si no se usa, se borra)
        val maxFileCount = 3                   // Máximo 3 videos en caché
        val immunityDelay = 60                 // 60 segundos de inmunidad
        val fileTypes = arrayOf(TdApi.FileTypeVideo()) // Solo aplicar a videos
        val chatIds = LongArray(0)             // Todos los chats
        val excludeChatIds = LongArray(0)      // Ninguno excluido
        val returnStats = false                // No necesitamos las estadísticas de vuelta
        val chatLimit = 0                      // Sin límite específico por chat

        val optimizeFunction = TdApi.OptimizeStorage(
            maxSizeBytes,
            maxTtlSeconds,
            maxFileCount,
            immunityDelay,
            fileTypes,
            chatIds,
            excludeChatIds,
            returnStats,
            chatLimit
        )

        client?.send(optimizeFunction) {
            Log.d("STORAGE", "Optimización configurada: Límite 400MB")
        }

        val settings = listOf(
            "storage_max_files_size" to 200 * 1024 * 1024L,
            "storage_max_database_size" to 50 * 1024 * 1024L,
            //"ignore_background_networking_restriction" to true
        )

        settings.forEach { (key, value) ->
            client?.send(TdApi.SetOption(key, TdApi.OptionValueInteger(value))) {}
        }
    }

    fun sendPhoneNumber(phoneNumber: String) {
        client?.send(TdApi.SetAuthenticationPhoneNumber(phoneNumber, null)) {

        }
    }

    fun sendCode(code: String) {
        client?.send(TdApi.CheckAuthenticationCode(code)) {

        }
    }
}
