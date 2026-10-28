package local.accountvault.android.features.security

import local.accountvault.android.PortableVaultCrypto

object AppSession { @Volatile var unlocked = false }

object AppLockVerifier {
    private const val MARKER = "MemBoom app lock v1"
    fun create(password: CharArray): String = PortableVaultCrypto.pack(PortableVaultCrypto.encrypt(MARKER,password))
    fun verify(record: String,password: CharArray): Boolean = try {
        PortableVaultCrypto.decrypt(PortableVaultCrypto.unpack(record),password)==MARKER
    } catch (_: Exception) { false }
}
