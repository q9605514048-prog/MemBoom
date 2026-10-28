package local.accountvault.android.features.backup

import com.google.gson.Gson
import com.google.gson.JsonParser
import local.accountvault.android.*
import java.util.UUID

object VaultBackup {
    const val MAX_BYTES = 8 * 1024 * 1024
    fun export(vault: LocalVault,password: CharArray): String {
        val json=Gson().toJson(PortableVaultCrypto.encrypt(vault.toBackupJson(),password))
        require(json.toByteArray(Charsets.UTF_8).size<=MAX_BYTES){"Backup is too large"};return json
    }
    fun restore(json: String,password: CharArray): LocalVault {
        require(json.toByteArray(Charsets.UTF_8).size<=MAX_BYTES) { "Backup is too large" }
        val root=JsonParser.parseString(json).asJsonObject
        val envelope=VaultEnvelope(root.get("version").asInt,root.get("salt").asString,root.get("nonce").asString,root.get("ciphertext").asString)
        return LocalVault.fromPortableJson(PortableVaultCrypto.decrypt(envelope,password))
    }
    fun merge(local: LocalVault,incoming: LocalVault): Int {
        var added=0
        incoming.accounts.forEach { account ->
            if(local.accounts.none { it.category==account.category && it.name==account.name && it.fields==account.fields }) {
                val id=if(local.accounts.any {it.id==account.id}) UUID.randomUUID().toString() else account.id
                local.accounts.add(account.copy(id=id)); added++
            }
        }
        incoming.notes.forEach { note->
            if(local.notes.none{it.title==note.title&&it.content==note.content&&it.pinned==note.pinned&&it.links==note.links}){
                local.notes.add(if(local.notes.any{it.id==note.id})note.copy(id=UUID.randomUUID().toString()) else note);added++
            }
        }
        return added
    }
}
