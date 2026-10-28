package local.accountvault.android.features

import local.accountvault.android.*
import local.accountvault.android.features.backup.VaultBackup
import local.accountvault.android.features.notes.VaultNote
import com.google.gson.JsonParser
import kotlin.test.*

class SyncNotesTest {
    @Test fun oldWebsiteAccountFieldsAreNotSilentlyDropped(){
        val vault=LocalVault.fromPortableJson("""[{"name":"Работа","accounts":[{"id":"old","name":"Сайт","login":"user","password":"private!","note":"Комментарий"}]}]""")
        assertEquals(listOf("user","private!","Комментарий"),vault.accounts.single().fields.map{it.value})
    }
    @Test fun sendingPhoneNotesPreservesWebsiteOnlyDataAndRemoteNotes(){
        val local=LocalVault();local.addAccount("Игры","Phone",listOf(VaultField("Пароль","secret")));local.notes.add(VaultNote(id="phone",title="Телефон",content="Текст"))
        val remote=LocalVault();remote.notes.add(VaultNote(id="web",title="Сайт",content="Текст"))
        val root=JsonParser.parseString(remote.toBackupJson()).asJsonObject
        root.add("library",JsonParser.parseString("""[{"id":"website-only","status":"hentai"}]"""))
        val sent=SyncPayload.upload(local,root.toString())
        val restored=LocalVault.fromPortableJson(sent)
        assertEquals(setOf("phone","web"),restored.notes.map{it.id}.toSet())
        assertEquals("Phone",restored.accounts.single().name)
        assertEquals(root.get("library"),JsonParser.parseString(sent).asJsonObject.get("library"))
        assertEquals(1,local.notes.size)
        assertEquals(2,LocalVault.fromPortableJson(SyncPayload.upload(local,sent)).notes.size)
    }
    @Test fun receivingNotesAndImportingBackupDoNotDiscardExistingNotes(){
        val local=LocalVault(notes=mutableListOf(VaultNote(id="phone",title="Телефон",content="Текст")))
        val remote=LocalVault(notes=mutableListOf(VaultNote(id="web",title="Сайт",content="Текст")))
        remote.addAccount("Работа","Web",listOf(VaultField("Логин","user")))
        val received=SyncPayload.received(remote.toBackupJson(),local)
        assertEquals(2,received.notes.size);assertEquals("Web",received.accounts.single().name)
        val restored=VaultBackup.restore(VaultBackup.export(remote,"qa!123".toCharArray()),"qa!123".toCharArray())
        assertEquals(2,VaultBackup.merge(local,restored));assertEquals(0,VaultBackup.merge(local,restored))
        assertEquals(2,local.notes.size);assertEquals(1,local.accounts.size)
    }
}
