package com.example.telegramvideotv.data.users

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

/**
 * Info mínima de un remitente para pintar el header de una burbuja.
 * Los usuarios de Telegram usan ids positivos; los chats/canales
 * usan ids negativos, por eso la misma mpa sirve para ambos.
 */
data class SenderInfo(
    val id: Long,
    val name: String,
    val avatarFileId: Int? = null
)

/**
 * Caché de remitentes. Alimenta el nombre + avatar de cada burbuja del chat
 * sin bloquear la UI: mientras no llegue el dato, la UI muestra "Usuario"
 * y con la reactividad del StateFlow se repinta solo al llegar.
 */
class UserRepository {

    private val _usersMap = MutableStateFlow<Map<Long, SenderInfo>>(emptyMap())
    val usersMap: StateFlow<Map<Long, SenderInfo>> = _usersMap

    // Evita spamear GetUser para el mismo id antes de que conteste
    private val pendingLookups = mutableSetOf<Long>()

    var client: Client? = null

    fun initClient(client: Client) {
        this.client = client
    }

    /** Inserta o refresca un remitente */
    private fun upsert(info: SenderInfo) {
        _usersMap.value = _usersMap.value + (info.id to info)
        pendingLookups.remove(info.id)
    }

    /**
     * Recibe TdApi.UpdateUser (nombre o foto cambiaron).
     */
    fun handleUserUpdate(update: TdApi.UpdateUser) {
        val u = update.user
        val name = (u.firstName + " " + u.lastName).trim()
            .ifEmpty { "Usuario ${u.id}" }
        upsert(
            SenderInfo(
                id = u.id,
                name = name,
                avatarFileId = u.profilePhoto?.small?.id
            )
        )
    }

    /**
     * Recibe chats/canales como remitentes (mensajes posteados en canales,
     * admins con "Chat" como sender).
     */
    fun rememberChat(chat: TdApi.Chat) {
        upsert(
            SenderInfo(
                id = chat.id,
                name = chat.title,
                avatarFileId = chat.photo?.small?.id
            )
        )
    }

    /** Nombre listo para UI; "" si aún no lo conocemos */
    fun senderNameOf(senderId: Long): String =
        _usersMap.value[senderId]?.name ?: ""

    fun senderInfoOf(senderId: Long): SenderInfo? =
        _usersMap.value[senderId]

    /**
     * Dispara la recuperación del remitente si no está en caché.
     * Idempotente: los ids pendientes no se vuelven a pedir.
     */
    fun ensureSender(senderId: Long, outgoing: Boolean = false) {
        if (senderId == 0L || outgoing) return
        if (_usersMap.value.containsKey(senderId)) return
        if (!pendingLookups.add(senderId)) return
        val current = client ?: return
        if (senderId > 0) {
            // Usuario real de Telegram
            current.send(TdApi.GetUser(senderId)) { obj ->
                when (obj) {
                    is TdApi.User -> {
                        val name = (obj.firstName + " " + obj.lastName).trim()
                            .ifEmpty { "Usuario ${obj.id}" }
                        upsert(
                            SenderInfo(
                                id = obj.id,
                                name = name,
                                avatarFileId = obj.profilePhoto?.small?.id
                            )
                        )
                    }
                    is TdApi.Error -> {
                        Log.e(TAG, "GetUser($senderId) error: ${obj.code} ${obj.message}")
                        pendingLookups.remove(senderId)
                    }
                    else -> pendingLookups.remove(senderId)
                }
            }
        } else {
            // Chat/canal como remitente (id negativo)
            current.send(TdApi.GetChat(senderId)) { obj ->
                when (obj) {
                    is TdApi.Chat -> rememberChat(obj)
                    is TdApi.Error -> {
                        Log.e(TAG, "GetChat($senderId) error: ${obj.code} ${obj.message}")
                        pendingLookups.remove(senderId)
                    }
                    else -> pendingLookups.remove(senderId)
                }
            }
        }
    }

    private companion object {
        const val TAG = "UserRepository"
    }
}
