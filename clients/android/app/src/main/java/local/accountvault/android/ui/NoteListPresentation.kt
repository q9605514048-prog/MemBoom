package local.accountvault.android.ui

import local.accountvault.android.features.notes.VaultNote
import java.time.Instant
import java.util.Locale

object NoteListPresentation {
    fun select(notes: List<VaultNote>, pinnedOnly: Boolean, sortByTitle: Boolean): List<VaultNote> {
        val pinned = compareByDescending<VaultNote> { it.pinned }
        val order = if (sortByTitle) pinned.thenBy { it.title.lowercase(Locale.ROOT) }.thenBy { it.id }
            else pinned.thenByDescending { Instant.parse(it.updatedAt) }.thenBy { it.id }
        return notes.filter { !pinnedOnly || it.pinned }.sortedWith(order)
    }
}
