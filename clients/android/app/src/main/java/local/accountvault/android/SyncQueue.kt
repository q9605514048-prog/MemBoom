package local.accountvault.android

data class PendingChange(val id: String, val revision: String, val ciphertext: String)

class SyncQueue {
    private val items = ArrayDeque<PendingChange>()
    private var nextId = 1

    @Synchronized
    fun enqueue(revision: String, ciphertext: String) {
        items.clear()
        items.addLast(PendingChange(nextId++.toString(), revision, ciphertext))
    }

    @Synchronized
    fun takeNext(): PendingChange? = items.firstOrNull()

    @Synchronized
    fun markDelivered(change: PendingChange) {
        items.remove(change)
    }

    @Synchronized
    fun retry(change: PendingChange) {
        if (items.none { it.id == change.id }) items.addFirst(change)
    }

    @Synchronized
    fun clear() {
        items.clear()
    }

    @Synchronized
    fun size(): Int = items.size

    @Synchronized
    fun snapshot(): String = buildString {
        append(nextId)
        items.forEach { change ->
            append('\n')
            append(encode(change.id)).append('|')
            append(encode(change.revision)).append('|')
            append(encode(change.ciphertext))
        }
    }

    private fun encode(value: String): String =
        java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(value.toByteArray(Charsets.UTF_8))

    companion object {
        fun fromSnapshot(snapshot: String): SyncQueue {
            val queue = SyncQueue()
            val lines = snapshot.split('\n')
            queue.nextId = lines.firstOrNull()?.toIntOrNull()?.coerceAtLeast(1) ?: 1
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val parts = line.split('|')
                if (parts.size != 3) return@forEach
                val id = decode(parts[0])
                val revision = decode(parts[1])
                val ciphertext = decode(parts[2])
                if (id != null && revision != null && ciphertext != null) {
                    queue.items.addLast(PendingChange(id, revision, ciphertext))
                }
            }
            return queue
        }

        private fun decode(value: String): String? = try {
            String(java.util.Base64.getUrlDecoder().decode(value), Charsets.UTF_8)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
