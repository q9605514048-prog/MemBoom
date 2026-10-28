package local.accountvault.android

import java.nio.charset.StandardCharsets
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class VaultEnvelope(val version: Int, val salt: String, val nonce: String, val ciphertext: String)

object EncryptedQueueSnapshot {
    private const val TRANSFORMATION = "AES/GCM/NoPadding"
    private const val NONCE_SIZE = 12
    private const val TAG_BITS = 128

    fun encrypt(snapshot: String, key: SecretKey): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Android Keystore keys require the provider to generate the encryption IV.
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val nonce = cipher.iv
        check(nonce.size == NONCE_SIZE) { "Unexpected AES-GCM nonce size" }
        val ciphertext = cipher.doFinal(snapshot.toByteArray(StandardCharsets.UTF_8))
        return encode(nonce) + "." + encode(ciphertext)
    }

    fun decrypt(encrypted: String, key: SecretKey): String {
        val parts = encrypted.split('.', limit = 2)
        require(parts.size == 2) { "Invalid encrypted queue format" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, decode(parts[0])))
        return String(cipher.doFinal(decode(parts[1])), StandardCharsets.UTF_8)
    }

    fun toEnvelope(encrypted: String): VaultEnvelope {
        val parts = encrypted.split('.', limit = 2)
        require(parts.size == 2) { "Invalid encrypted queue format" }
        return VaultEnvelope(1, "android-keystore", parts[0], parts[1])
    }

    private fun encode(value: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(value)
    private fun decode(value: String): ByteArray = Base64.getUrlDecoder().decode(value)
}
