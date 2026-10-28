package local.accountvault.android.features

import local.accountvault.android.LocalVault
import local.accountvault.android.VaultField
import local.accountvault.android.features.editor.AccountEntryService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class AccountEntryServiceTest {
    @Test fun savesOfflineWithoutRequiringASyncPassword() {
        val vault = LocalVault()
        var persisted = ""
        val editor = AccountEntryService(vault) { persisted = vault.snapshot() }
        val account = editor.add("  Игры  ", "  Steam  ", listOf(VaultField("Пароль", "keep spaces !")))
        assertEquals("Игры", account.category)
        assertEquals("Steam", account.name)
        assertEquals("keep spaces !", account.fields.single().value)
        assertEquals(account, LocalVault.fromSnapshot(persisted).accounts.single())
    }

    @Test fun rejectsBlankCategoryAndNameBeforeWriting() {
        val vault = LocalVault()
        var writes = 0
        val editor = AccountEntryService(vault) { writes++ }
        assertFailsWith<IllegalArgumentException> { editor.add(" ", "Steam", emptyList()) }
        assertFailsWith<IllegalArgumentException> { editor.add("Игры", " ", emptyList()) }
        assertEquals(0, writes)
        assertEquals(0, vault.accounts.size)
    }

    @Test fun failedPersistenceKeepsExistingAccountsAndRollsBackTheNewOne() {
        val vault = LocalVault().apply { addAccount("Работа", "Mail", emptyList()) }
        val before = vault.snapshot()
        val editor = AccountEntryService(vault) { error("Disk full") }
        assertFailsWith<IllegalStateException> { editor.add("Игры", "Steam", emptyList()) }
        assertEquals(before, vault.snapshot())
    }
}
