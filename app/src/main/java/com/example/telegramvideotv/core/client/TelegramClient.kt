package com.example.telegramvideotv.core.client

import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi

object TelegramClient {
    var client: Client? = null

    fun optimize(maxSize: Long = 200 * 1024 * 1024L) {
        val optimizeFunction = TdApi.OptimizeStorage(
            maxSize, 0, 1, 0,
            arrayOf(TdApi.FileTypeVideo()), LongArray(0), LongArray(0), false, 0
        )
        client?.send(optimizeFunction) {}
    }
}
