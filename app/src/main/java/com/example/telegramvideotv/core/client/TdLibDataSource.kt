package com.example.telegramvideotv.core.client

import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.BaseDataSource
import androidx.media3.datasource.DataSpec
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.core.net.toUri

@UnstableApi
class TdLibDataSource(
    private val client: Client?,
    private val fileId: Int,
    private val size: Long
) : BaseDataSource(true) {

    private var dataSpec: DataSpec? = null
    private var opened = false
    private var bytesRead = 0L

    private val OPTIMAL_BUFFER_SIZE = 2 * 1024 * 1024 // 2.097.152 bytes

    override fun open(dataSpec: DataSpec): Long {
        this.bytesRead = dataSpec.position
        Log.d("STREAM_DEBUG", "Abriendo DataSource en posición: ${dataSpec.position}")

        // IMPORTANTE: ExoPlayer necesita saber cuánto mide el video para crear el buffer.
        // Como vimos en tu log, mide 2739886094 bytes.
        val totalSize = 2739886094

        return size - dataSpec.position
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength <= 0) return 0

        val actualToRead = minOf(readLength, OPTIMAL_BUFFER_SIZE)

        var attempts = 0
        val maxAttempts = 20
        while (attempts < maxAttempts) {
            val lock = CountDownLatch(1)
            var resultData: ByteArray? = null
            var errorCode: Int? = null

            // 1. Intentamos leer la parte que pide ExoPlayer
            client?.send(TdApi.ReadFilePart(fileId, bytesRead, actualToRead.toLong())) { obj ->
                when (obj) {
                    is TdApi.Data -> resultData = obj.data
                    is TdApi.Error -> {
                        errorCode = obj.code
                        Log.e("STREAM_ERROR", "Error de TDLib: ${obj.message}")
                    }
                }
                lock.countDown()
            }
            lock.await()

            // Timeout de seguridad para la petición a TDLib
            val success = lock.await(5, TimeUnit.SECONDS)

            if (!success) {
                Log.w("STREAM_DEBUG", "Timeout esperando ReadFilePart")
                attempts++
                continue
            }

            // 2. Si tenemos los datos, los entregamos
            if (resultData != null && resultData.isNotEmpty()) {
                val length = resultData.size
                System.arraycopy(resultData, 0, buffer, offset, length)
                bytesRead += length.toLong()
                return length
            }

            // 3. SI NO HAY DATOS: Forzamos a Telegram a saltar a este offset específico
            attempts++

            // Si el error es falta de red (ej. error 502 o similar en TDLib)
            if (errorCode == 404 || errorCode == 500) {
                // Podrías lanzar una excepción para que ExoPlayer sepa que algo falló grave
                throw IOException("Error crítico de TDLib: $errorCode")
            }

            // MUY IMPORTANTE: Usamos bytesRead como offset en DownloadFile
            // Esto le dice a Telegram: "Deja de bajar el inicio, salta al byte $bytesRead"
            // En lugar de limit = 0 (descarga total), ponemos 15MB de buffer
            val bufferLimit = 15 * 1024 * 1024
            client?.send(TdApi.DownloadFile(fileId, 32, bytesRead, bufferLimit.toLong(), true)) { }

            Log.d("STREAM_DEBUG", "Solicitando a Telegram saltar al offset: $bytesRead (Intento $attempts)")

            Thread.sleep(500) // Bajamos a 500ms para que sea más rápido
        }

        // Si después de todos los intentos no hay datos, lanzamos error de lectura
        throw IOException("No se pudo obtener datos de Telegram tras varios intentos (Timeout/Red)")

//        return -1
    }

    override fun getUri(): Uri {
        return "telegram://video/$fileId".toUri()
    }

    override fun close() {
        Log.d("stream", "opened value: $opened")
        if (opened) {
            opened = false
            client?.send(TdApi.DeleteFile(fileId)) {
                Log.d("STREAM_DEBUG", "Archivo eliminado del disco al cerrar el reproductor")
            }
            transferEnded()
        }
    }
}
