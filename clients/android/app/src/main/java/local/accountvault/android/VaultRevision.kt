package local.accountvault.android

data class VaultSnapshot(
    val revision: Long,
    val envelope: VaultEnvelope?,
    val conflicts: List<VaultConflict>
)

data class VaultConflict(
    val id: String,
    val revision: Long,
    val envelope: VaultEnvelope
)

object VaultRevision {
    private val revisionPattern = Regex("\\\"revision\\\"\\s*:\\s*(\\d+)")
    private val envelopePattern = Regex(
        "\\\"version\\\"\\s*:\\s*(\\d+)\\s*,\\s*\\\"salt\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"\\s*,\\s*\\\"nonce\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"\\s*,\\s*\\\"ciphertext\\\"\\s*:\\s*\\\"([^\\\"]*)\\\""
    )
    private val conflictPattern = Regex(
        "\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"\\s*,\\s*\\\"revision\\\"\\s*:\\s*(\\d+)\\s*,\\s*\\\"envelope\\\"\\s*:\\s*\\{([^{}]*)\\}"
    )

    fun parse(json: String): Long = parseSnapshot(json).revision

    fun parseSnapshot(json: String): VaultSnapshot {
        val revision = revisionPattern.find(json)?.groupValues?.get(1)?.toLongOrNull()
        require(revision != null && revision >= 0) { "Invalid vault revision" }
        val envelopeValue = when {
            Regex("\\\"envelope\\\"\\s*:\\s*null").containsMatchIn(json) -> null
            Regex("\\\"envelope\\\"\\s*:\\s*\\{").containsMatchIn(json) -> {
                parseEnvelope(envelopePattern.find(json)?.groupValues ?: invalid("Invalid vault envelope"))
            }
            else -> invalid("Missing vault envelope")
        }
        val conflictsSection = Regex("\\\"conflicts\\\"\\s*:\\s*\\[(.*)]\\s*}", RegexOption.DOT_MATCHES_ALL)
            .find(json)?.groupValues?.get(1) ?: invalid("Missing conflicts")
        val conflicts = conflictPattern.findAll(conflictsSection).map { match ->
            val groups = match.groupValues
            VaultConflict(groups[1], groups[2].toLongOrNull() ?: invalid("Invalid conflict revision"), parseEnvelope(envelopePattern.find(groups[3])?.groupValues ?: invalid("Invalid conflict envelope")))
        }.toList()
        require(conflictsSection.count { it == '{' } == conflicts.size * 2) { "Invalid conflict list" }
        return VaultSnapshot(revision, envelopeValue, conflicts)
    }

    private fun invalid(message: String): Nothing = throw IllegalArgumentException(message)

    private fun parseEnvelope(groups: List<String>): VaultEnvelope {
        require(groups.size == 5) { "Invalid vault envelope" }
        val envelope = VaultEnvelope(groups[1].toIntOrNull() ?: invalid("Invalid envelope version"), groups[2], groups[3], groups[4])
        PortableVaultCrypto.validate(envelope)
        return envelope
    }
}
