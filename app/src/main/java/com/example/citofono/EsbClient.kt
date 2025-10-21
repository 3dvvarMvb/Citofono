package com.example.citofono

import android.util.Log
import kotlinx.coroutines.*
import org.json.JSONObject
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Cliente NDJSON (una línea = un JSON) para el BUS.
 * Protocolo:
 *  - REGISTER: {"type":"REGISTER","client_id":..., "kind":"client"}\n
 *  - ACK: {"type":"REGISTER_ACK","status":"success"}\n
 *  - REQUEST con header.correlationId; respuesta lleva el mismo header.correlationId
 */
class EsbClient(
    private val host: String = NetConfig.BUS_HOST,
    private val port: Int = NetConfig.BUS_PORT,
    private val clientId: String = "android-client-" + UUID.randomUUID().toString().takeLast(6),
    private val connectTimeoutMs: Int = 4_000,
    private val soTimeoutMs: Int = 20_000
) {
    interface Listener {
        fun onEvent(topic: String, event: JSONObject) {}
        fun onDeliveryAck(target: String) {}
        fun onError(message: String) {}
        fun onMessage(raw: JSONObject) {}
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var socket: Socket? = null
    private var reader: BufferedReader? = null
    private var writer: BufferedWriter? = null
    private var listenJob: Job? = null

    private val pending = ConcurrentHashMap<String, CompletableDeferred<JSONObject>>()
    @Volatile private var isRegistered = false

    var listener: Listener? = null

    suspend fun connectAndRegister(kind: String = "client", service: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            close()

            val s = Socket()
            s.soTimeout = soTimeoutMs
            s.tcpNoDelay = true
            s.connect(InetSocketAddress(host, port), connectTimeoutMs)

            socket = s
            reader = BufferedReader(InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8))
            writer = BufferedWriter(OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8))

            // REGISTER
            val reg = JSONObject()
                .put("type", "REGISTER")
                .put("client_id", clientId)
                .put("kind", kind).apply {
                    service?.let { put("service", it) }
                }

            sendJsonLine(reg)

            val ack = readJsonLine() ?: return@withContext false
            isRegistered = (ack.optString("type") == "REGISTER_ACK" && ack.optString("status") == "success")

            if (isRegistered) {
                listenJob = scope.launch { listenLoop() }
            }
            isRegistered
        } catch (e: Exception) {
            Log.e("EsbClient", "connect error: ${e.message}", e)
            false
        }
    }

    fun isConnected(): Boolean = isRegistered && (socket?.isConnected == true)

    fun close() {
        try { listenJob?.cancel() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { writer?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        isRegistered = false
        pending.values.forEach { it.completeExceptionally(IllegalStateException("Disconnected")) }
        pending.clear()
    }

    // ---------------- API ----------------

    suspend fun request(
        service: String,
        action: String? = null,
        body: JSONObject = JSONObject(),
        timeoutMs: Long = 5_000
    ): JSONObject = withContext(Dispatchers.IO) {
        ensureConnected()
        val corr = UUID.randomUUID().toString()
        val header = JSONObject().put("service", service).put("correlationId", corr)
        if (action != null) header.put("action", action)

        val msg = JSONObject()
            .put("type", "REQUEST")
            .put("header", header)
            .put("payload", body)

        val deferred = CompletableDeferred<JSONObject>()
        pending[corr] = deferred
        sendJsonLine(msg)

        try {
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            pending.remove(corr)
        }
    }

    suspend fun subscribe(topic: String): Boolean = withContext(Dispatchers.IO) {
        ensureConnected()
        val msg = JSONObject()
            .put("type", "SUBSCRIBE")
            .put("body", JSONObject().put("topic", topic))
        sendJsonLine(msg)
        true
    }

    suspend fun publish(topic: String, event: JSONObject): Boolean = withContext(Dispatchers.IO) {
        ensureConnected()
        val msg = JSONObject()
            .put("type", "PUBLISH")
            .put("body", JSONObject().put("topic", topic).put("event", event))
        sendJsonLine(msg)
        true
    }

    // --------------- internals ---------------

    private suspend fun ensureConnected() {
        if (!isConnected()) {
            val ok = connectAndRegister("client", null)
            if (!ok) error("No conectado al BUS")
        }
    }

    private fun sendJsonLine(obj: JSONObject) {
        val w = writer ?: return
        synchronized(w) {
            w.write(obj.toString())
            w.write("\n")
            w.flush()
        }
    }

    private fun readJsonLine(): JSONObject? {
        val r = reader ?: return null
        return try {
            val line = r.readLine() ?: return null
            if (line.isBlank()) return null
            JSONObject(line)
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun listenLoop() {
        try {
            while (true) {
                val msg = readJsonLine() ?: break
                handleIncoming(msg)
            }
        } catch (e: Exception) {
            Log.e("EsbClient", "listen error: ${e.message}", e)
        } finally {
            // Reconexión con backoff simple
            close()
            var delayMs = 500L
            repeat(6) { // ~ hasta ~30s
                try {
                    if (connectAndRegister("client", null)) return
                } catch (_: Exception) {}
                Thread.sleep(delayMs)
                delayMs = (delayMs * 2).coerceAtMost(8000L)
            }
        }
    }

    private fun handleIncoming(msg: JSONObject) {
        val header = msg.optJSONObject("header")
        val corr = header?.optString("correlationId")

        if (!corr.isNullOrEmpty()) {
            pending[corr]?.complete(msg)
            return
        }

        when (msg.optString("type")) {
            "EVENT" -> {
                val topic = msg.optString("topic")
                val event = msg.optJSONObject("event") ?: JSONObject()
                listener?.onEvent(topic, event)
            }
            "DELIVERY_ACK" -> listener?.onDeliveryAck(msg.optString("target", ""))
            "ERROR" -> listener?.onError(msg.optString("message", "Unknown error"))
            else -> listener?.onMessage(msg)
        }
    }
}
