package local.accountvault.android.features

import local.accountvault.android.LocalVault
import local.accountvault.android.features.backup.VaultBackup
import local.accountvault.android.features.notes.*
import kotlin.test.*

class NoteLinksTest {
    private val google=NoteFileLink("Договор", "https://drive.google.com/file/d/example/view?resourcekey=private-key")
    private val yandex=NoteFileLink("Чертёж", "https://disk.yandex.ru/d/example")

    @Test fun websiteLinksSurvivePhoneSnapshotAndEncryptedBackup() {
        val vault=LocalVault.fromPortableJson("""{"categories":[],"notes":[{"Id":"links","Title":"Файлы","Content":"Текст","Pinned":false,"UpdatedAt":"2026-10-27T00:00:00Z","Links":[{"Name":"Договор","Url":"${google.url}"},{"Name":"Чертёж","Url":"${yandex.url}"}]}]}""")
        assertEquals(listOf(google,yandex),vault.notes.single().links)
        assertEquals(vault.notes,LocalVault.fromSnapshot(vault.snapshot()).notes)
        val encrypted=VaultBackup.export(vault,"QaLinks!123".toCharArray())
        assertFalse(encrypted.contains("private-key"))
        assertEquals(vault.notes,VaultBackup.restore(encrypted,"QaLinks!123".toCharArray()).notes)
        assertEquals("Google Диск",google.provider);assertEquals("Яндекс Диск",yandex.provider)
        assertEquals("links",vault.searchNotes("чертёж").single().id)
    }
    @Test fun invalidFileLinksAreRejectedAndFailedSaveKeepsOldNote() {
        for(url in listOf("javascript:alert(1)","http://drive.google.com/x","https://drive.google.com.evil.test/x","https://u:p@disk.yandex.ru/d/x","https://disk.yandex.ru:8443/d/x","https://example.com/file"))assertFails{NoteFileLink("Файл",url).validate()}
        val vault=LocalVault();val note=VaultNote(title="Файлы",content="",links=listOf(google));NoteService(vault){}.save(note)
        assertFails{NoteService(vault){error("disk")}.save(note.copy(links=listOf(yandex)))}
        assertEquals(listOf(google),vault.notes.single().links)
        assertFails{NoteService(vault){}.save(note.copy(links=listOf(google,google)))}
        assertFails{NoteService(vault){}.save(note.copy(links=(0..20).map{google.copy(url=google.url+it)}))}
    }
    @Test fun backupMergePreservesVersionsWithDifferentLinksAndOldNotesStillOpen() {
        val local=LocalVault();local.notes.add(VaultNote(id="same",title="Файлы",content="Текст",links=listOf(google)))
        val incoming=LocalVault();incoming.notes.add(local.notes.single().copy(links=listOf(yandex)))
        assertEquals(1,VaultBackup.merge(local,incoming));assertEquals(0,VaultBackup.merge(local,incoming));assertEquals(2,local.notes.size)
        assertEquals(emptyList(),LocalVault.fromPortableJson("""{"categories":[],"notes":[{"Id":"old","Title":"Старая","Content":"Текст","Pinned":false,"UpdatedAt":"2026-10-27T00:00:00Z"}]}""").notes.single().links)
    }
}
