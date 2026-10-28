package local.accountvault.android.features.notes

import local.accountvault.android.LocalVault

class NoteService(private val vault:LocalVault,private val persist:()->Unit) {
    fun save(note:VaultNote) = change {
        val index=vault.notes.indexOfFirst{it.id==note.id}
        val normalized=note.copy(title=note.title.trim(),links=note.links.map{it.normalized()})
        if(index<0)vault.notes.add(normalized) else vault.notes[index]=normalized
    }
    fun remove(id:String)=change { vault.notes.removeAll{it.id==id};Unit }
    private fun change(action:()->Unit){
        val before=vault.notes.toList()
        try{action();NoteCodec.validate(vault.notes);persist()}
        catch(error:Exception){vault.notes.clear();vault.notes.addAll(before);throw error}
    }
}
