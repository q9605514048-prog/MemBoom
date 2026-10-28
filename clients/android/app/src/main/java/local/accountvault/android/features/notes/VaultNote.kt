package local.accountvault.android.features.notes

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import java.time.Instant
import java.util.UUID

data class VaultNote(val id:String=UUID.randomUUID().toString(),val title:String,val content:String,val pinned:Boolean=false,val updatedAt:String=Instant.now().toString(),val links:List<NoteFileLink> = emptyList())

object NoteCodec {
    fun encode(notes:List<VaultNote>)=JsonArray().apply { notes.forEach { n->add(JsonObject().apply {
        addProperty("Id",n.id);addProperty("Title",n.title);addProperty("Content",n.content);addProperty("Pinned",n.pinned);addProperty("UpdatedAt",n.updatedAt)
        add("Links",JsonArray().apply{n.links.forEach{link->add(JsonObject().apply{addProperty("Name",link.name);addProperty("Url",link.url)})}})
    }) } }
    fun decode(array:JsonArray):List<VaultNote> {
        val result=array.map { element->
            val n=element.asJsonObject
            val links=if(n.has("Links"))n.getAsJsonArray("Links").map{val link=it.asJsonObject;NoteFileLink(link.get("Name").asString,link.get("Url").asString).normalized()}else emptyList()
            VaultNote(n.get("Id").asString,n.get("Title").asString,n.get("Content").asString,n.get("Pinned")?.asBoolean?:false,n.get("UpdatedAt").asString,links)
        }
        validate(result);return result
    }
    fun validate(notes:List<VaultNote>) {
        require(notes.map{it.id}.distinct().size==notes.size)
        notes.forEach { require(it.id.isNotBlank()&&it.title.isNotBlank()&&it.title.length<=200&&it.content.length<=500000);Instant.parse(it.updatedAt);NoteFileLink.validateList(it.links) }
    }
}
