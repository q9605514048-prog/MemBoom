package local.accountvault.android

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object PortableVaultCrypto {
    private const val ITERATIONS = 310_000
    private const val KEY_BITS = 256
    private const val NONCE_BYTES = 12
    private const val SALT_BYTES = 16
    private const val TAG_BITS = 128

    fun encrypt(plaintext: String, password: CharArray): VaultEnvelope {
        require(MasterPasswordSession.isValid(password)) { "Master password must contain at least 4 ASCII letters, digits, or symbols" }
        val salt = ByteArray(SALT_BYTES).also(SecureRandom()::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, nonce))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return VaultEnvelope(1, encode(salt), encode(nonce), encode(ciphertext))
    }

    fun decrypt(envelope: VaultEnvelope, password: CharArray): String {
        validate(envelope)
        require(MasterPasswordSession.isValid(password)) { "Master password must contain at least 4 ASCII letters, digits, or symbols" }
        val salt = decode(envelope.salt)
        val nonce = decode(envelope.nonce)
        val ciphertext = decode(envelope.ciphertext)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, deriveKey(password, salt), GCMParameterSpec(TAG_BITS, nonce))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun validate(envelope: VaultEnvelope) {
        require(envelope.version == 1) { "Unsupported encryption envelope" }
        val salt = decode(envelope.salt)
        val nonce = decode(envelope.nonce)
        val ciphertext = decode(envelope.ciphertext)
        require(salt.size == SALT_BYTES && nonce.size == NONCE_BYTES && ciphertext.size >= 16) { "Invalid encryption envelope" }
    }

    fun pack(envelope: VaultEnvelope): String {
        validate(envelope)
        return listOf(envelope.version.toString(), envelope.salt, envelope.nonce, envelope.ciphertext).joinToString(".")
    }

    fun unpack(packed: String): VaultEnvelope {
        val parts = packed.split('.', limit = 4)
        require(parts.size == 4) { "Invalid portable vault envelope" }
        return VaultEnvelope(parts[0].toIntOrNull() ?: throw IllegalArgumentException("Invalid envelope version"), parts[1], parts[2], parts[3])
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun encode(bytes: ByteArray): String = Base64.getEncoder().encodeToString(bytes)

    private fun decode(value: String): ByteArray = try {
        Base64.getDecoder().decode(value)
    } catch (_: IllegalArgumentException) {
        throw IllegalArgumentException("Invalid encryption envelope encoding")
    }
}
