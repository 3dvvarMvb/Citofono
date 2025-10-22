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

    /** Desencapsula { payload:{...} } y/o { data:{...} } */
    private fun unwrapPayload(resp: JSONObject): JSONObject {
        val payload = resp.optJSONObject("payload") ?: resp
        return payload.optJSONObject("data") ?: payload
    }

    // ---------------- Contactos ----------------
    suspend fun searchContactsByDepto(depto: String, limit: Int = 10): JSONArray =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject()
                .put("departamento", depto)
                .put("nombre", "")
                .put("limit", limit)
            val resp = client.request(service = "Contactos", action = "search", body = body, timeoutMs = 10_000)
            val data = unwrapPayload(resp)
            data.optJSONArray("contacts") ?: JSONArray()
        }

    suspend fun searchContactsFallback(term: String, limit: Int = 10): JSONArray =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("q", term).put("limit", limit)
            val resp = client.request(service = "Contactos", action = "search", body = body, timeoutMs = 10_000)
            val data = unwrapPayload(resp)
            data.optJSONArray("contacts") ?: JSONArray()
        }

    suspend fun contactosCreate(nombre: String, departamento: String, telefono: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject()
                .put("nombre", nombre)
                .put("departamento", departamento)
                .put("telefono", telefono)
            val resp = client.request("Contactos", "create_contact", body, 10_000)
            unwrapPayload(resp)
        }

    suspend fun contactosUpdate(contactId: String, updates: JSONObject): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject()
                .put("id", contactId)
                .put("updates", updates)
            val resp = client.request("Contactos", "update_contact", body, 10_000)
            unwrapPayload(resp)
        }

    suspend fun contactosSearchExactly(depto: String, limit: Int = 1): JSONArray =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject()
                .put("departamento", depto)
                .put("nombre", "")
                .put("limit", limit)
            val resp = client.request("Contactos", "search", body, 10_000)
            val data = unwrapPayload(resp)
            data.optJSONArray("contacts") ?: JSONArray()
        }

    suspend fun contactosImport(fileContentBase64: String, format: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject()
                .put("fileContent", fileContentBase64)
                .put("format", format.lowercase()) // "csv" o "xlsx"
            val resp = client.request("Contactos", "import", body, 20_000)
            unwrapPayload(resp) // { importedCount, inserted, updated, skipped, errors }
        }

    suspend fun contactosExportXlsx(): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("format", "xlsx")
            val resp = client.request("Contactos", "export", body, 12_000)
            unwrapPayload(resp) // { fileContent, count }
        }

    // ---------------- Registro de llamadas ----------------
    suspend fun recordCall(
        destination: String,
        status: String = "attempted",
        durationSec: Int? = null,
        callerId: String = "android-device",
        depto: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        ensureConnected()
        val body = JSONObject().apply {
            put("destination", destination)
            put("callerId", callerId)
            put("status", status)
            durationSec?.let { put("duration", it) }
            depto?.let { put("depto", it) }
        }
        val resp = client.request(service = "RegistroLlamadas", action = "record", body = body, timeoutMs = 8_000)
        unwrapPayload(resp)
    }

    /** Listado de registros de llamadas (para la consola admin). */
    suspend fun callsList(
        depto: String? = null,
        status: String? = null,
        dateFrom: String? = null, // "YYYY-MM-DD"
        dateTo: String? = null,   // "YYYY-MM-DD"
        limit: Int = 100
    ): JSONArray = withContext(Dispatchers.IO) {
        ensureConnected()
        val body = JSONObject().apply {
            depto?.let { put("depto", it) }
            status?.let { put("status", it) }
            dateFrom?.let { put("from", it) }
            dateTo?.let { put("to", it) }
            put("limit", limit)
        }
        val resp = client.request("RegistroLlamadas", "list", body, 10_000)
        val data = unwrapPayload(resp)
        data.optJSONArray("items") ?: JSONArray()
    }

    // ---------------- Autenticación ----------------
    suspend fun authCreateUser(username: String, password: String, role: String = "user"): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("username", username).put("password", password).put("role", role)
            val resp = client.request("Autenticacion", "create_user", body, 10_000)
            unwrapPayload(resp)
        }

    suspend fun authLogin(username: String, password: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("username", username).put("password", password)
            val resp = client.request("Autenticacion", "login", body, 10_000)
            unwrapPayload(resp)
        }

    suspend fun authValidate(sessionId: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("session_id", sessionId)
            val resp = client.request("Autenticacion", "validate_session", body, 8_000)
            unwrapPayload(resp)
        }

    suspend fun authLogout(sessionIdOrToken: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("token", sessionIdOrToken)
            val resp = client.request("Autenticacion", "logout", body, 8_000)
            unwrapPayload(resp)
        }

    // ---------------- Administración ----------------
    suspend fun adminVerify(username: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("username", username)
            val resp = client.request("Administracion", "verify_admin", body, 12_000)
            unwrapPayload(resp)
        }

    suspend fun adminGetUser(username: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("username", username)
            val resp = client.request(
                service = "Administracion",
                action = "get_user_info",
                body = body,
                timeoutMs = 12_000
            )
            unwrapPayload(resp)
        }

    suspend fun adminUpdateUser(
        username: String,
        role: String? = null,
        active: Boolean? = null,
        newPassword: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        ensureConnected()
        val body = JSONObject().put("username", username).apply {
            role?.let { put("role", it) }
            active?.let { put("active", it) }
            newPassword?.let { put("password", it) }
        }
        val resp = client.request(
            service = "Administracion",
            action = "admin_update_user",
            body = body,
            timeoutMs = 15_000
        )
        unwrapPayload(resp)
    }

    suspend fun adminDeleteUser(username: String): JSONObject =
        withContext(Dispatchers.IO) {
            ensureConnected()
            val body = JSONObject().put("username", username)
            val resp = client.request(
                service = "Administracion",
                action = "admin_delete_user",
                body = body,
                timeoutMs = 12_000
            )
            unwrapPayload(resp)
        }

    // ---------------- Mensajería ----------------
    suspend fun messagesList(
        fromDate: String? = null,
        toDate: String? = null,
        user: String? = null,
        limit: Int = 1000
    ): JSONArray = withContext(Dispatchers.IO) {
        ensureConnected()
        val body = JSONObject().apply {
            fromDate?.let { put("from", it) }
            toDate?.let { put("to", it) }
            user?.let { put("user", it) }
            put("limit", limit)
        }
        val resp = client.request(
            service = "Mensajeria",
            action = "get_all_messages",
            body = body,
            timeoutMs = 15_000
        )
        val data = unwrapPayload(resp)
        // El servicio puede retornar "messages", "items" o directamente un array
        data.optJSONArray("messages")
            ?: data.optJSONArray("items")
            ?: data.optJSONArray("data")
            ?: JSONArray()
    }
    // En tu objeto EsbApi
// Versión tipada para la consola de admin
suspend fun callsList(
    query: String? = null,
    fromMillis: Long? = null,
    toMillis: Long? = System.currentTimeMillis(),
    limit: Int = 200,
    skip: Int = 0
): List<CallLog> = withContext(Dispatchers.IO) {
    ensureConnected()
    val body = JSONObject().apply {
        query?.let { put("q", it) }
        fromMillis?.let { put("from", it) }
        toMillis?.let { put("to", it) }
        put("limit", limit)
        put("skip", skip)
    }
    val resp = client.request(
        service = "RegistroLlamadas",
        action = "list",
        body = body,
        timeoutMs = 10_000
    )
    val data = unwrapPayload(resp)
    val arr = data.optJSONArray("items") ?: data.optJSONArray("data") ?: JSONArray()

    val out = ArrayList<CallLog>(arr.length())
    for (i in 0 until arr.length()) {
        val o = arr.optJSONObject(i) ?: continue

        val tsMs = when {
            o.has("tsMillis")   -> o.optLong("tsMillis")
            o.has("timestamp")  -> normalizeToMillis(o.opt("timestamp"))
            o.has("ts")         -> normalizeToMillis(o.opt("ts"))
            o.has("created_at") -> normalizeToMillis(o.opt("created_at"))
            else -> parseFechaHoraToMillis(
                o.optString("fecha", ""),
                o.optString("hora", "")
            )
        }

        out += CallLog(
            id = o.optString("_id", o.optString("id", "$i")),
            tsMillis = tsMs,
            caller = o.optString("caller", ""),
            depto = o.optString("depto", o.optString("departamento", "")),
            durationSec = o.optInt("durationSec", o.optInt("duration", o.optInt("duracion", 0))),
            status = o.optString("status", "unknown"),
            destination = o.optString("destination", o.optString("telefono", ""))
        )
    }
    out.sortedByDescending { it.tsMillis }
}

// ---- helpers de fechas (sin javax.xml.bind) ----
private fun normalizeToMillis(v: Any?): Long = when (v) {
    is Number -> {
        val n = v.toLong()
        if (n < 10_000_000_000L) n * 1000 else n // segundos→ms si es chico
    }
    is String -> {
        v.toLongOrNull()?.let { return if (it < 10_000_000_000L) it * 1000 else it }
        parseDateMulti(v)
    }
    else -> 0L
}

private fun parseFechaHoraToMillis(fecha: String?, hora: String?): Long {
    if (fecha.isNullOrBlank()) return 0L
    val pat = if (hora.isNullOrBlank()) "yyyy-MM-dd" else "yyyy-MM-dd HH:mm"
    return try {
        val sdf = java.text.SimpleDateFormat(pat, java.util.Locale.US)
        // Usar la zona horaria del dispositivo
        sdf.timeZone = java.util.TimeZone.getDefault()
        sdf.parse(listOfNotNull(fecha, hora).joinToString(" "))?.time ?: 0L
    } catch (_: Exception) { 0L }
}

private fun parseDateMulti(s: String): Long {
    val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    for (p in patterns) {
        try {
            val sdf = java.text.SimpleDateFormat(p, java.util.Locale.US)
            // Usar la zona horaria del dispositivo
            sdf.timeZone = java.util.TimeZone.getDefault()
            return sdf.parse(s)?.time ?: continue
        } catch (_: Exception) { /* next */ }
    }
    return 0L
}

}
