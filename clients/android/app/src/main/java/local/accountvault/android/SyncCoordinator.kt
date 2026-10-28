package local.accountvault.android

class SyncCoordinator(
    private val queue: SyncQueue,
    private val send: (PendingChange) -> SyncResult
) {
    fun syncNext(): SyncResult? {
        val change = queue.takeNext() ?: return null
        val result = try {
            send(change)
        } catch (_: Exception) {
            SyncResult.Failed(-1)
        }
        if (result == SyncResult.Saved) queue.markDelivered(change)
        return result
    }
}
