package local.accountvault.android.features

import local.accountvault.android.*
import local.accountvault.android.features.notes.*
import local.accountvault.android.features.backup.VaultBackup
import kotlin.test.*

class NotesTest {
    @Test fun actualWindowsEncryptedNotesBackupOpensOnPhone() {
        val json=javaClass.getResource("/windows-notes-v1.json")!!.readText()
        val vault=VaultBackup.restore(json,"QaBackup!123".toCharArray())
        assertEquals("Общая заметка",vault.notes.single().title)
        assertEquals("Windows → Android → сайт\nВторая строка",vault.notes.single().content)
        assertTrue(vault.notes.single().pinned)
        assertEquals(1,vault.accounts.size)
    }
    @Test fun notesSurviveSnapshotsAndEncryptedBackups() {
        val vault=LocalVault();NoteService(vault){}.save(VaultNote(title="Идея",content="Строка 1\nСтрока 2",pinned=true))
        assertEquals(vault.notes,LocalVault.fromSnapshot(vault.snapshot()).notes)
        assertEquals(vault.notes,VaultBackup.restore(VaultBackup.export(vault,"QaNotes!123".toCharArray()),"QaNotes!123".toCharArray()).notes)
        assertFalse(vault.toPortableJson().contains("Строка 1"))
        assertTrue(LocalVault.fromSnapshot("").notes.isEmpty())
    }
    @Test fun persistenceFailureRollsBackNoteEdits() {
        val vault=LocalVault();val note=VaultNote(title="Старая",content="keep");vault.notes.add(note)
        assertFails{NoteService(vault){error("disk")}.save(note.copy(content="new"))}
        assertEquals("keep",vault.notes.single().content)
        assertFails{NoteService(vault){}.save(note.copy(title=" "))}
        NoteService(vault){}.remove(note.id);assertTrue(vault.notes.isEmpty())
    }
    @Test fun importRetainsBothConflictingNotesAndSkipsDuplicates() {
        val local=LocalVault();local.notes.add(VaultNote(id="same",title="Название",content="local"))
        val incoming=LocalVault();incoming.notes.add(local.notes.single().copy(content="remote"))
        assertEquals(1,VaultBackup.merge(local,incoming));assertEquals(0,VaultBackup.merge(local,incoming));assertEquals(2,local.notes.size)
    }
    @Test fun windowsNoteFormatIsAcceptedAndSearchIsCaseInsensitive() {
        val vault=LocalVault.fromPortableJson("""{"categories":[],"notes":[{"Id":"qa","Title":"Планы","Content":"Список ИДЕЙ","Pinned":true,"UpdatedAt":"2026-10-26T00:00:00+00:00"}]}""")
        assertEquals("qa",vault.searchNotes("идей").single().id)
        assertTrue(vault.notes.single().pinned)
    }
}
