package local.accountvault.android

import java.util.Base64

data class SyncSettings(val endpoint: String, val token: String) {
    fun encode(): String {
        val endpointPart = Base64.getUrlEncoder().withoutPadding().encodeToString(endpoint.toByteArray(Charsets.UTF_8))
        val tokenPart = Base64.getUrlEncoder().withoutPadding().encodeToString(token.toByteArray(Charsets.UTF_8))
        return "$endpointPart.$tokenPart"
    }

    companion object {
        fun decode(value: String): SyncSettings {
            val parts = value.split('.', limit = 2)
            require(parts.size == 2) { "Invalid sync settings" }
            return SyncSettings(
                String(Base64.getUrlDecoder().decode(parts[0]), Charsets.UTF_8),
                String(Base64.getUrlDecoder().decode(parts[1]), Charsets.UTF_8)
            )
        }
    }
}
