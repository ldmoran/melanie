package com.melanie.billetera.data.remote

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

// ---------- REST ----------
data class DepositReq(val account: String, val amount: Double)
data class TransferReq(val from: String, val to: String, val amount: Double)
data class ServiceReq(val account: String, val contract: String, val amount: Double)

data class OpResponse(
    val ok: Boolean = true,
    val message: String? = null,
    val balance: Double = 0.0,
    val reference: String? = null,
    val service: String? = null,
)

interface WalletApi {
    @GET("api/balance/{account}") suspend fun balance(@Path("account") account: String): OpResponse
    @POST("api/deposit") suspend fun deposit(@Body b: DepositReq): OpResponse
    @POST("api/withdraw") suspend fun withdraw(@Body b: DepositReq): OpResponse
    @POST("api/transfer") suspend fun transfer(@Body b: TransferReq): OpResponse
    @POST("api/services/pay") suspend fun payService(@Body b: ServiceReq): OpResponse
}

fun normalizeBase(host: String): String {
    var h = host.trim()
    if (!h.startsWith("http")) h = "http://$h"
    if (!h.endsWith("/")) h += "/"
    return h
}

fun buildApi(host: String): WalletApi {
    val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()
    return Retrofit.Builder()
        .baseUrl(normalizeBase(host))
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(WalletApi::class.java)
}

/** Convierte cualquier error HTTP / de red en un mensaje legible. */
fun Throwable.friendlyMessage(): String = when (this) {
    is retrofit2.HttpException -> try {
        Gson().fromJson(response()?.errorBody()?.string(), OpResponse::class.java)?.message
    } catch (_: Exception) { null } ?: "Error del servidor (${code()})"
    is java.io.IOException -> "No se pudo conectar con el servidor"
    else -> message ?: "Error inesperado"
}

// ---------- WebSocket ----------
data class SocketEvent(
    val type: String? = null,
    val from: String? = null,
    val amount: Double = 0.0,
    val balance: Double = 0.0,
    val timestamp: Long = 0,
    val reference: String? = null,
)

/**
 * Mantiene el túnel WebSocket abierto en Dispatchers.IO y se reconecta solo.
 */
class WalletSocket(private val scope: CoroutineScope) {
    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected

    private val _events = MutableSharedFlow<SocketEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<SocketEvent> = _events

    private val client = OkHttpClient.Builder()
        .pingInterval(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()
    private val gson = Gson()
    private var job: Job? = null

    fun start(host: String, account: String) {
        stop()
        val url = normalizeBase(host).replaceFirst("http", "ws") + "ws?account=$account"
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val closed = CompletableDeferred<Unit>()
                val ws = client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
                    override fun onOpen(webSocket: WebSocket, response: Response) { _connected.value = true }
                    override fun onMessage(webSocket: WebSocket, text: String) {
                        runCatching { gson.fromJson(text, SocketEvent::class.java) }
                            .getOrNull()?.let { _events.tryEmit(it) }
                    }
                    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                        _connected.value = false; closed.complete(Unit)
                    }
                    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                        _connected.value = false; closed.complete(Unit)
                    }
                })
                try {
                    closed.await()
                } finally {
                    ws.cancel()
                }
                delay(3000) // reconexión
            }
        }
    }

    fun stop() {
        job?.cancel()
        _connected.value = false
    }
}
