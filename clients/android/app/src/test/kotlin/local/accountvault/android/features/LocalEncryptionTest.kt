package local.accountvault.android.features

import local.accountvault.android.EncryptedQueueSnapshot
import local.accountvault.android.PortableVaultCrypto
import java.util.Base64
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class LocalEncryptionTest {
    @Test fun encryptionUsesFreshProviderNoncesAndRemainsReadableAfterRestart() {
        val key = SecretKeySpec(ByteArray(32) { 7 }, "AES")
        val first = EncryptedQueueSnapshot.encrypt("local snapshot", key)
        val second = EncryptedQueueSnapshot.encrypt("local snapshot", key)
        assertEquals(12, Base64.getUrlDecoder().decode(first.substringBefore('.')).size)
        assertNotEquals(first.substringBefore('.'), second.substringBefore('.'))
        assertEquals("local snapshot", EncryptedQueueSnapshot.decrypt(first, key))
        assertEquals("local snapshot", EncryptedQueueSnapshot.decrypt(second, key))
    }

    @Test fun fourCharacterMasterPasswordWorksForPortableSyncEncryption() {
        val envelope = PortableVaultCrypto.encrypt("snapshot", "a1!b".toCharArray())
        assertEquals("snapshot", PortableVaultCrypto.decrypt(envelope, "a1!b".toCharArray()))
    }
}
