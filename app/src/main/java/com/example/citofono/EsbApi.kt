// app/src/main/java/com/example/citofono/EsbApi.kt
package com.example.citofono

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object EsbApi {
    private val client = EsbClient(
        host = NetConfig.BUS_HOST,
        port = NetConfig.BUS_PORT
    )

    private suspend fun ensureConnected() {
        if (!client.isConnected()) {
            val ok = client.connectAndRegister(kind = "client")
            if (!ok) error("No se pudo conectar al BUS")
        }
    }

    /** Desencapsula { payload: {...} } y/o { data: {...} } */
    private fun unwrapPayload(resp: JSONObject): JSONObject {
        val payload = resp.optJSONObject("payload") ?: resp
        return payload.optJSONObject("data") ?: payload
    }

    // ---------------- Contacts ----------------
    suspend fun searchContactsByDepto(depto: String, limit: Int = 10): JSONArray =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject()
                .put("departamento", depto)
                .put("limit", limit)
            val resp = client.request(service = "Contactos", action = "search", body = body, timeoutMs = 10_000)
            val data = unwrapPayload(resp)
            data.optJSONArray("contacts") ?: JSONArray()
        }

    suspend fun searchContactsFallback(term: String, limit: Int = 10): JSONArray =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject()
                .put("q", term)
                .put("limit", limit)
            val resp = client.request(service = "Contactos", action = "search", body = body, timeoutMs = 10_000)
            val data = unwrapPayload(resp)
            data.optJSONArray("contacts") ?: JSONArray()
        }

    // ---------------- Calls ----------------
    suspend fun recordCall(
        destination: String,
        status: String = "attempted",
        durationSec: Int? = null,
        callerId: String = "android-device"
    ): JSONObject = withContext(Dispatchers.IO) {
        ensureConnected()
        val body = JSONObject().apply {
            put("destination", destination)
            put("callerId", callerId)
            put("status", status)
            durationSec?.let { put("duration", it) }
        }
        val resp = client.request(service = "Llamadas", action = "record", body = body, timeoutMs = 8_000)
        unwrapPayload(resp)
    }

    // ---------------- Auth ----------------
    suspend fun authCreateUser(username: String, password: String, role: String = "user"): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject()
                .put("username", username)
                .put("password", password)
                .put("role", role)
            val resp = client.request(service = "Autenticacion", action = "create_user", body = body, timeoutMs = 10_000)
            unwrapPayload(resp)
        }

    suspend fun authLogin(username: String, password: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject()
                .put("username", username)
                .put("password", password)
            // Puedes usar "authenticate_user" (status/session) o "login" (token/userType/userId)
            val resp = client.request(service = "Autenticacion", action = "authenticate_user", body = body, timeoutMs = 10_000)
            unwrapPayload(resp)
        }

    suspend fun authValidate(sessionId: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("session_id", sessionId)
            val resp = client.request(service = "Autenticacion", action = "validate_session", body = body, timeoutMs = 8_000)
            unwrapPayload(resp)
        }

    suspend fun authLogout(sessionId: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("session_id", sessionId)
            val resp = client.request(service = "Autenticacion", action = "logout", body = body, timeoutMs = 8_000)
            unwrapPayload(resp)
        }

    // ---------------- Admin ----------------
    suspend fun adminVerify(username: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("username", username)
            val resp = client.request(service = "Administracion", action = "verify_admin", body = body, timeoutMs = 8_000)
            unwrapPayload(resp)
        }

    suspend fun adminGetUser(username: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("username", username)
            val resp = client.request(service = "Administracion", action = "get_user_info", body = body, timeoutMs = 8_000)
            unwrapPayload(resp)
        }

    suspend fun adminUpdateUser(username: String, role: String? = null, active: Boolean? = null, newPassword: String? = null): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("username", username)
            role?.let { body.put("role", it) }
            active?.let { body.put("active", it) }
            newPassword?.let { body.put("password", it) }
            val resp = client.request(service = "Administracion", action = "admin_update_user", body = body, timeoutMs = 10_000)
            unwrapPayload(resp)
        }

    suspend fun adminDeleteUser(username: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("username", username)
            val resp = client.request(service = "Administracion", action = "admin_delete_user", body = body, timeoutMs = 8_000)
            unwrapPayload(resp)
        }
}
