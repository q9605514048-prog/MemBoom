package local.accountvault.android

sealed class ConflictDecision {
    data object KeepLocal : ConflictDecision()
    data class AcceptServer(val conflictId: String) : ConflictDecision()
}

object ConflictResolution {
    fun find(snapshot: VaultSnapshot, conflictId: String): VaultConflict? =
        snapshot.conflicts.firstOrNull { it.id == conflictId }

    fun keepLocal(): ConflictDecision = ConflictDecision.KeepLocal

    fun acceptServer(conflictId: String): ConflictDecision {
        require(conflictId.isNotBlank()) { "Conflict id is required" }
        return ConflictDecision.AcceptServer(conflictId)
    }

    fun acceptServerVersion(queue: SyncQueue, resolve: () -> Long): Boolean = try {
        resolve()
        queue.clear()
        true
    } catch (_: Exception) {
        false
    }
}
