package local.accountvault.android

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL

class VaultSyncClient(
    private val endpoint: String,
    private val deviceToken: String,
    private val connectionFactory: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection }
) {
    companion object { const val followRedirects = false }

    init {
        val uri = URI(endpoint)
        require(uri.scheme == "https" || (uri.scheme == "http" && isLocalServer(uri.host))) { "Remote synchronization requires HTTPS" }
        require(!deviceToken.contains('\n') && !deviceToken.contains('\r')) { "Invalid device token" }
    }

    fun registerDevice(name: String): DeviceRegistration {
        require(name.isNotBlank()) { "Device name is required" }
        val connection = open("$endpoint/api/devices", "POST")
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $deviceToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(VaultSyncRequest.registerDeviceBody(name).toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val body = readBody(connection, code)
        connection.disconnect()
        require(code in 200..299) { "Device registration failed: $code" }
        return DeviceRegistration.parse(body)
    }

    fun fetchSnapshot(): VaultSnapshot {
        val connection = open("$endpoint/api/vault", "GET")
        try {
            connection.setRequestProperty("Authorization", "Bearer $deviceToken")
            val code = connection.responseCode
            require(code in 200..299) { if(code==401) "Вход на сервер истёк. Войдите в аккаунт MemBoom повторно." else "Не удалось загрузить копию с сервера: HTTP $code" }
            return VaultRevision.parseSnapshot(readBody(connection, code))
        } finally { connection.disconnect() }
    }

    fun currentRevision(): Long = fetchSnapshot().revision

    fun resolve(conflictId: String): Long {
        val connection = open("$endpoint/api/vault/resolve", "POST")
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $deviceToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(VaultSyncRequest.resolveBody(conflictId).toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        val body = readBody(connection, code)
        connection.disconnect()
        require(code in 200..299) { "Conflict resolve failed: $code" }
        return VaultRevision.parse(body)
    }

    fun put(revision: Long, salt: String, nonce: String, ciphertext: String): SyncResult =
        put(revision, 1, salt, nonce, ciphertext)

    fun put(revision: Long, version: Int, salt: String, nonce: String, ciphertext: String): SyncResult {
        val connection = open("$endpoint/api/vault", "PUT")
        connection.doOutput = true
        connection.setRequestProperty("Authorization", "Bearer $deviceToken")
        connection.setRequestProperty("Content-Type", "application/json")
        connection.outputStream.use { it.write(VaultSyncRequest.putBody(revision, version, salt, nonce, ciphertext).toByteArray(Charsets.UTF_8)) }
        val code = connection.responseCode
        connection.disconnect()
        return when (code) { in 200..299 -> SyncResult.Saved; 409 -> SyncResult.Conflict; else -> SyncResult.Failed(code) }
    }

    private fun open(url: String, method: String): HttpURLConnection = connectionFactory(URL(url)).also {
        it.instanceFollowRedirects = followRedirects
        it.requestMethod = method
        it.connectTimeout = 15000
        it.readTimeout = 20000
    }

    private fun readBody(connection: HttpURLConnection, code: Int): String =
        (if (code in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() } ?: ""

    private fun isLocalServer(host: String?): Boolean = host == "127.0.0.1" || host == "localhost" || host == "::1" || host == "10.0.2.2" || host == "192.168.3.2"
}

sealed class SyncResult {
    data object Saved : SyncResult()
    data object Conflict : SyncResult()
    data class Failed(val statusCode: Int) : SyncResult()
}
