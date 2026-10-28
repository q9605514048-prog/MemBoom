package local.accountvault.android

import com.google.gson.JsonParser
import local.accountvault.android.features.backup.VaultBackup
import local.accountvault.android.features.notes.NoteCodec

object SyncPayload {
    fun received(json:String,local:LocalVault):LocalVault {
        val incoming=LocalVault.fromPortableJson(json)
        VaultBackup.merge(incoming,LocalVault(notes=local.notes.toMutableList()))
        return incoming
    }
    fun upload(local:LocalVault,remoteJson:String?):String {
        val root=if(remoteJson==null)com.google.gson.JsonObject() else JsonParser.parseString(remoteJson).let{if(it.isJsonObject)it.asJsonObject.deepCopy() else com.google.gson.JsonObject()}
        val combined=LocalVault.fromSnapshot(local.snapshot())
        if(remoteJson!=null)VaultBackup.merge(combined,LocalVault(notes=LocalVault.fromPortableJson(remoteJson).notes))
        val portable=JsonParser.parseString(local.toBackupJson()).asJsonObject
        root.add("categories",portable.get("categories"))
        root.add("notes",NoteCodec.encode(combined.notes))
        return root.toString()
    }
}
