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

/**
 * DataSource "tipo streaming" para ExoPlayer respaldado por TDLib.
 *
 * Estrategia (patrón streaming de servicios de vídeo):
 * - ExoPlayer tira del vídeo llamando a [read] con trozos pequeños; NUNCA
 *   solicitamos el archivo completo.
 * - TDLib solo mantiene una "ventana deslizante" acotada de
 *   [WINDOW_SIZE] bytes a partir del offset que el reproductor esté
 *   leyendo, pedida con `DownloadFile(offset, limit)`.
 * - Solicitamos exactamente UN rango de descarga por DataSpec abierto
 *   (y ventanas nuevas solo al avanzar de ventana), con retries locales
 *   que NO piden rangos extra a TDLib.
 * - [close] solo cierra la transferencia (transferEnded); el borrado de
 *   partes en disco lo gestiona onDispose del player con DeleteFile.
 */
@UnstableApi
class TdLibDataSource(
    private val client: Client?,
    private val fileId: Int,
    private val size: Long
) : BaseDataSource(true) {

    private var bytesRead = 0L
    private var opened = false

    // Ventana de descarga activa [windowBase, windowEnd)
    private var windowBase = -1L
    private var windowEnd = -1L

    override fun open(dataSpec: DataSpec): Long {
        if (dataSpec.position < 0 || dataSpec.position >= size) {
            throw IOException("Posición fuera de rango de TDLib: position=${dataSpec.position}, size=$size")
        }
        bytesRead = dataSpec.position
        windowBase = -1L
        windowEnd = -1L
        opened = true
        transferStarted(dataSpec)
        Log.d(TAG, "Abriendo DataSource en offset=$bytesRead (streaming)")
        // Precalentamos el rango inicial del DataSpec (offset 0 en arranque,
        // o el offset del seek en re-aperturas) SIN bajar el archivo entero.
        if (client != null) requestWindow(bytesRead)
        return if (dataSpec.length < 0) size - bytesRead else dataSpec.length
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
        if (readLength <= 0) return 0
        if (!opened) throw IOException("read() sin open()")
        if (client == null) throw IOException("Client de TDLib no disponible")
        if (bytesRead >= size) return -1 // C.RESULT_END_OF_INPUT

        val chunkToRead = minOf(readLength, MAX_READ_CHUNK)
        val deadlineNanos = System.nanoTime() + MAX_READ_WAIT_MS * 1_000_000
        var lastLogAt = System.nanoTime()

        while (true) {
            // Solo pedimos ventana nueva si salimos de la anterior:
            // esto hace UNA petición de rango por ventana, no una por intento.
            if (bytesRead >= windowEnd) requestWindow(bytesRead)

            val result = readPartBlocking(chunkToRead, POLL_INTERVAL_MS)

            val chunk = result.data
            if (chunk != null && chunk.isNotEmpty()) {
                System.arraycopy(chunk, 0, buffer, offset, chunk.size)
                bytesRead += chunk.size
                return chunk.size
            }

            if (result.isFatal) {
                throw IOException("Error crítico de TDLib: ${result.message}")
            }

            // "There is not enough downloaded bytes..." es NORMAL mientras la
            // ventana está en descarga: esperamos con poll como el búfer de
            // un player de streaming (patrón Netflix) en vez de fallar al
            // instante, que era lo que disparaba el overlay de error.
            if (System.nanoTime() >= deadlineNanos) {
                throw IOException(
                    "No llegaron datos de TDLib tras $MAX_READ_WAIT_MS ms " +
                        "esperando la ventana (offset=$bytesRead)"
                )
            }
            if (System.nanoTime() - lastLogAt > LOG_THROTTLE_NANOS) {
                Log.d(TAG, "Esperando ventana en offset=$bytesRead: ${result.message}")
                lastLogAt = System.nanoTime()
            }
            Thread.sleep(POLL_INTERVAL_MS)
        }
    }

    override fun getUri(): Uri {
        return "telegram://video/$fileId".toUri()
    }

    override fun close() {
        // SOLO cerramos la transferencia. NO llamamos a DeleteFile aquí:
        // este método se ejecuta también en cada seek (cierre/reapertura de
        // DataSpec) y borrar las partes descargadas obligaría a re-descargarlas.
        if (opened) {
            opened = false
            transferEnded()
            Log.d(TAG, "Transferencia cerrada en offset=$bytesRead")
        }
    }

    /**
     * Pide a TDLib una ventana acotada de [WINDOW_SIZE] desde [offset].
     * Se envía una única vez por ventana; TDLib descarga ese rango en
     * segundo plano y [read] simplemente espera con backoff los datos.
     */
    private fun requestWindow(offset: Long) {
        val from = offset
        val to = minOf(offset + WINDOW_SIZE, size)
        if (from >= to) {
            windowBase = from
            windowEnd = from
            return
        }
        windowBase = from
        windowEnd = to
        Log.d(TAG, "Ventana de streaming: rango [$from, $to) (${to - from} bytes)")
        client?.send(TdApi.DownloadFile(fileId, PRIORITY, from, to - from, false)) { obj ->
            when (obj) {
                is TdApi.Error -> {
                    // 200 = "Canceled by another downloadFile request": TDLib solo
                    // mantiene UNA solicitud activa por archivo y la última
                    // gana. Si la suplanta una ventana NUESTRA más reciente,
                    // el poll de read() seguirá cubriendo el rango necesario.
                    val benign = obj.code == 200 && obj.message.contains("another downloadFile")
                    if (benign) {
                        Log.d(TAG, "Ventana [$from, $to) suplantada por una solicitud más reciente (benigno)")
                    } else {
                        Log.e(TAG, "DownloadFile(window) error: ${obj.code} ${obj.message}")
                    }
                }
                else -> Log.d(TAG, "Descarga de ventana [$from, $to) iniciada")
            }
        }
    }

    private fun readPartBlocking(readLength: Int, awaitMs: Long): ReadResult {
        val lock = CountDownLatch(1)
        var resultData: ByteArray? = null
        var message: String? = null
        var fatal = false

        client?.send(TdApi.ReadFilePart(fileId, bytesRead, readLength.toLong())) { obj ->
            when (obj) {
                is TdApi.Data -> resultData = obj.data
                is TdApi.Error -> {
                    val errorMessage = "${obj.code} ${obj.message}"
                    // 404 (FILE_NOT_FOUND) y 500 son fallas que no se recuperan leyendo
                    fatal = obj.code == 404 || obj.code == 500
                    // "not enough downloaded bytes" es la respuesta NORMAL de
                    // TDLib mientras la ventana aún se descarga: no es error,
                    // el poll de read() seguirá esperando a que llegue.
                    if (errorMessage.contains("not enough downloaded bytes")) {
                        Log.d(TAG, "Ventana aún descargando (normal): $errorMessage")
                    } else {
                        Log.e(TAG, "ReadFilePart error: $errorMessage")
                    }
                    message = errorMessage
                }
                else -> {
                    message = "Respuesta inesperada de ReadFilePart: ${obj?.javaClass?.simpleName}"
                    Log.e(TAG, "ReadFilePart respuesta inválida: $message")
                }
            }
            lock.countDown()
        }

        if (!lock.await(awaitMs, TimeUnit.MILLISECONDS)) {
            message = "timeout esperando ReadFilePart"
        }
        return ReadResult(resultData, fatal, message)
    }

    private data class ReadResult(val data: ByteArray?, val isFatal: Boolean, val message: String?)

    private companion object {
        const val TAG = "TdLibDataSource"
        // Ventana deslizante acotada: ~equivalente a unos pocos chunks HTTP
        // de un servicio de streaming; mantiene el uso de disco ligado al
        // ritmo de reproducción, no al tamaño completo del archivo.
        const val WINDOW_SIZE = 16L * 1024 * 1024 // 16 MB
        const val PRIORITY = 32
        const val MAX_READ_CHUNK = 2 * 1024 * 1024 // tope por llamada ReadFilePart
        // Poll al estilo búfer de streaming: mientras la ventana no llegue,
        // read() espera hasta MAX_READ_WAIT_MS antes de reportar error.
        const val POLL_INTERVAL_MS = 250L
        const val MAX_READ_WAIT_MS = 30_000L
        const val LOG_THROTTLE_NANOS = 4_000_000_000L // log de espera cada ~4 s
    }
}
