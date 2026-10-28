package local.accountvault.android.features

import local.accountvault.android.LocalVault
import local.accountvault.android.VaultField
import local.accountvault.android.features.backup.VaultBackup
import local.accountvault.android.features.security.AppLockVerifier
import kotlin.test.*

class BackupAndLockTest {
    @Test fun actualWindowsExportRestoresOnAndroid() {
        // Fixture exported by MemBoom Windows Core; contains synthetic QA data only.
        val json=javaClass.getResource("/windows-backup-v1.json")!!.readText()
        val account=VaultBackup.restore(json,"QaBackup!123".toCharArray()).accounts.single()
        assertEquals("Игры",account.category)
        assertEquals("Desktop QA",account.name)
        assertEquals("qa@example.test",account.fields.first().value)
        assertEquals("test-only-value",account.fields.last().value)
        assertFails { VaultBackup.restore(json,"Wrong!123".toCharArray()) }
    }
    @Test fun desktopFavoritesAndLegacyLocalSnapshotsArePreserved() {
        val json="""[{"name":"Игры","accounts":[{"id":"a","name":"Steam","favorite":true,"fields":[]}]}]"""
        val vault=LocalVault.fromPortableJson(json)
        assertTrue(LocalVault.fromSnapshot(vault.snapshot()).accounts.single().favorite)
        assertTrue(LocalVault.fromPortableJson(vault.toPortableJson()).accounts.single().favorite)
        assertFalse(LocalVault.fromSnapshot(vault.snapshot().substringBeforeLast('|')).accounts.single().favorite)
    }
    @Test fun appLockAcceptsOnlyTheCorrectPasswordAndRejectsDamagedRecords() {
        val record = AppLockVerifier.create("a1!b".toCharArray())
        assertTrue(AppLockVerifier.verify(record, "a1!b".toCharArray()))
        assertFalse(AppLockVerifier.verify(record, "wrong!".toCharArray()))
        assertFalse(AppLockVerifier.verify("damaged", "a1!b".toCharArray()))
    }
    @Test fun encryptedBackupRestoresAllFieldsWithoutPlaintextSecrets() {
        val vault=LocalVault().apply { addAccount("Игры","Steam",listOf(VaultField("Пароль","private!"),VaultField("Код","ABC"))) }
        val backup=VaultBackup.export(vault,"a1!b".toCharArray())
        assertFalse(backup.contains("private!"))
        assertEquals(vault.accounts, VaultBackup.restore(backup,"a1!b".toCharArray()).accounts)
        assertFails { VaultBackup.restore(backup,"wrong!".toCharArray()) }
    }
    @Test fun mergingDoesNotReplaceLocalSecretsAndRepeatedImportDoesNotDuplicate() {
        val original=LocalVault().apply { addAccount("Игры","Steam",listOf(VaultField("Пароль","local"))) }
        val incoming=LocalVault.fromSnapshot(original.snapshot())
        incoming.updateAccount(incoming.accounts.single().id,"Игры","Steam",listOf(VaultField("Пароль","remote")))
        assertEquals(1,VaultBackup.merge(original,incoming))
        assertEquals(2,original.accounts.size)
        assertEquals("local",original.accounts.first().fields.single().value)
        assertEquals(0,VaultBackup.merge(original,incoming))
        assertEquals(2,original.accounts.map { it.id }.distinct().size)
    }
    @Test fun portableImportSupportsTheWebsitesArrayRootAndLegacyObjectRoot() {
        val array="""[{"id":"c","name":"Игры","accounts":[{"id":"a","name":"Steam","fields":[{"label":"Логин","value":"test"}]}]}]"""
        assertEquals(LocalVault.fromPortableJson("{\"categories\":$array}").accounts,LocalVault.fromPortableJson(array).accounts)
    }
}
