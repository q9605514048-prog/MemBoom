package local.accountvault.android

object VaultSyncRequest {
    fun registerDeviceBody(name: String): String = "{\"name\":${quote(name)}}"

    fun resolveBody(conflictId: String): String = "{\"conflictId\":${quote(conflictId)}}"

    fun putBody(revision: Long, version: Int, salt: String, nonce: String, ciphertext: String): String {
        require(version == 1) { "Unsupported envelope version" }
        return "{\"revision\":$revision,\"envelope\":{" +
            "\"version\":$version," +
            "\"salt\":${quote(salt)}," +
            "\"nonce\":${quote(nonce)}," +
            "\"ciphertext\":${quote(ciphertext)}}}"
    }

    fun putBody(revision: Long, salt: String, nonce: String, ciphertext: String): String =
        putBody(revision, 1, salt, nonce, ciphertext)

    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }
}
