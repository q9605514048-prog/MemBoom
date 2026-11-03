package local.accountvault.android.features

import local.accountvault.android.*
import local.accountvault.android.ui.*
import local.accountvault.android.features.notes.VaultNote
import kotlin.test.*

class AndroidPresentationTest {
    private fun account(id: String, category: String, name: String, favorite: Boolean = false) =
        VaultAccount(id, category, name, listOf(VaultField("Login", "$id@example.test"), VaultField("Password", "Synthetic!123")), favorite)

    @Test fun searchCategoryAndFavoriteFiltersComposeWithoutMutatingTheVault() {
        val vault = LocalVault(mutableListOf(account("a", "Work", "Mail", true), account("b", "Games", "Mail", true), account("c", "Work", "Mail")))
        val before = vault.snapshot()
        assertEquals(listOf("a"), AccountFilter("Work", true).apply(vault, "example.test").map { it.id })
        assertTrue(AccountFilter("Work", true).apply(vault, "missing").isEmpty())
        assertEquals(before, vault.snapshot())
        assertEquals(3, AccountFilter().apply(vault, "").size)
    }

    @Test fun sortingHasDeterministicTiesAndFavoritesDoNotDropOtherRecords() {
        val vault = LocalVault(mutableListOf(account("z", "B", "Same"), account("a", "B", "Same"), account("f", "A", "Zulu", true)))
        assertEquals(listOf("a", "z", "f"), AccountFilter().apply(vault, "").map { it.id })
        assertEquals(listOf("f", "a", "z"), AccountFilter(sort = AccountSort.CATEGORY).apply(vault, "").map { it.id })
        assertEquals(listOf("f", "a", "z"), AccountFilter(sort = AccountSort.FAVORITES).apply(vault, "").map { it.id })
    }

    @Test fun eachNewDetailIsMaskedAndRevealDoesNotChangeTheStoredValue() {
        val field = VaultField("API Token", "Synthetic!123")
        val disclosure = FieldDisclosure(field)
        assertTrue(disclosure.secret)
        assertFalse(disclosure.displayValue.contains(field.value))
        disclosure.toggle(); assertEquals(field.value, disclosure.displayValue)
        disclosure.hide(); assertFalse(disclosure.revealed)
        assertEquals("••••••••", FieldDisclosure(field).displayValue)
        assertEquals("Synthetic!123", field.value)
    }

    @Test fun customSecretNamesAreDetectedInBothLanguagesAndBlankFieldsStayBlank() {
        listOf("Пароль", "Запасной ключ", "password", "Passcode", "Secret", "Token", "private KEY", "PIN", "Код восстановления", "Моё поле").forEach { assertTrue(FieldPresentation.isSecret(it), it) }
        assertFalse(FieldPresentation.isSecret("Почта / логин"))
        val ordinary = FieldDisclosure(VaultField("Login", "synthetic@example.test"))
        ordinary.toggle(); assertFalse(ordinary.revealed)
        assertEquals("synthetic@example.test", ordinary.displayValue)
        assertEquals("", FieldDisclosure(VaultField("Password", "")).displayValue)
    }

    @Test fun compactRowsOnlyPreviewAnIdentifiedNonSecretLogin() {
        val record = account("a", "Work", "Demo").copy(fields = listOf(VaultField("Password", "Synthetic!123"), VaultField("Secret login", "hidden"), VaultField("Почта", "preview@example.test")))
        assertEquals("preview@example.test", FieldPresentation.loginPreview(record))
        assertNull(FieldPresentation.loginPreview(record.copy(fields = record.fields.take(2))))
        assertNull(FieldPresentation.loginPreview(record.copy(fields = listOf(VaultField("Comment", "not a login")))))
    }

    @Test fun favoritePersistsThroughExistingFormatsAndFailedWriteRollsBack() {
        val original = account("a", "Work", "Demo")
        val vault = LocalVault(mutableListOf(original))
        var persisted = ""
        assertTrue(AccountFavoriteAction.toggle(vault, "a") { persisted = vault.snapshot() })
        assertTrue(LocalVault.fromSnapshot(persisted).accounts.single().favorite)
        assertTrue(LocalVault.fromPortableJson(vault.toPortableJson()).accounts.single().favorite)
        assertEquals(original.fields, vault.accounts.single().fields)
        val before = vault.snapshot()
        assertFailsWith<IllegalStateException> { AccountFavoriteAction.toggle(vault, "a") { error("Synthetic write failure") } }
        assertEquals(before, vault.snapshot())
        assertFalse(AccountFavoriteAction.toggle(vault, "missing") { fail("Missing record must not write") })
    }

    @Test fun noteSortingPreservesPinnedPriorityAndLinkedFileRecords() {
        val notes = listOf(
            VaultNote(id="a", title="Zulu", content="Synthetic", pinned=true, updatedAt="2026-01-01T00:00:00Z"),
            VaultNote(id="b", title="Beta", content="Synthetic", updatedAt="2026-01-03T00:00:00Z"),
            VaultNote(id="c", title="Alpha", content="Synthetic", updatedAt="2026-01-02T00:00:00Z")
        )
        assertEquals(listOf("a", "b", "c"), NoteListPresentation.select(notes,false,false).map { it.id })
        assertEquals(listOf("a", "c", "b"), NoteListPresentation.select(notes,false,true).map { it.id })
        assertEquals(listOf(notes[0]), NoteListPresentation.select(notes,true,true))
        assertEquals("Zulu", notes[0].title)
    }
}
