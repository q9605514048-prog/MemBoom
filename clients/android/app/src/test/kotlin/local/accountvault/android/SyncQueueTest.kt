package local.accountvault.android

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import javax.crypto.spec.SecretKeySpec
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class SyncQueueTest {
    @Test
    fun meguminThemeUsesDarkMagicalPalette() {
        assertEquals("#101118", AndroidThemePolicy.background)
        assertEquals("#E84855", AndroidThemePolicy.magicRed)
        assertEquals("#FFC857", AndroidThemePolicy.magicGold)
    }

    @Test
    fun masterPasswordAcceptsFourAsciiCharactersOnly() {
        assertEquals(true, MasterPasswordSession.isValid("a1!b".toCharArray()))
        assertEquals(false, MasterPasswordSession.isValid("a1!".toCharArray()))
        assertEquals(false, MasterPasswordSession.isValid("ab c".toCharArray()))
        assertEquals(false, MasterPasswordSession.isValid("пароль".toCharArray()))
    }

    @Test
    fun offlineChangesAreQueuedAndDeliveredOnce() {
        val queue = SyncQueue()
        queue.enqueue("revision-1", "ciphertext-1")
        val first = queue.takeNext()
        assertEquals("ciphertext-1", first?.ciphertext)
        queue.markDelivered(first!!)
        assertEquals(0, queue.size())
    }

    @Test
    fun latestPortableSnapshotRemainsQueuedAfterMultipleOfflineChanges() {
        val queue = SyncQueue()
        val first = PortableVaultCrypto.pack(PortableVaultCrypto.encrypt("first", "master-password-123".toCharArray()))
        val latestEnvelope = PortableVaultCrypto.encrypt("latest", "master-password-123".toCharArray())
        queue.enqueue("revision-1", first)
        queue.enqueue("revision-2", PortableVaultCrypto.pack(latestEnvelope))

        val queued = PortableVaultCrypto.unpack(queue.takeNext()!!.ciphertext)

        assertEquals("latest", PortableVaultCrypto.decrypt(queued, "master-password-123".toCharArray()))
        assertEquals(1, queue.size())
    }

    @Test
    fun newFullVaultSnapshotCoalescesOlderQueuedSnapshots() {
        val queue = SyncQueue()
        queue.enqueue("revision-1", "snapshot-1")
        queue.enqueue("revision-2", "snapshot-2")

        assertEquals(1, queue.size())
        assertEquals("snapshot-2", queue.takeNext()?.ciphertext)
    }

    @Test
    fun retryKeepsAnUnsentChange() {
        val queue = SyncQueue()
        queue.enqueue("revision-1", "ciphertext-1")
        val item = queue.takeNext()!!
        queue.retry(item)
        assertEquals(1, queue.size())
    }

    @Test
    fun queuedChangesCanBeRestoredAfterProcessRestart() {
        val original = SyncQueue().apply { enqueue("revision-1", "ciphertext|with|separators") }

        val restored = SyncQueue.fromSnapshot(original.snapshot())

        assertEquals(1, restored.size())
        assertEquals("ciphertext|with|separators", restored.takeNext()?.ciphertext)
    }

    @Test
    fun encryptedSnapshotRejectsWrongKey() {
        val queue = SyncQueue().apply { enqueue("revision-1", "secret-envelope") }
        val key = SecretKeySpec(ByteArray(32) { 7 }, "AES")
        val wrongKey = SecretKeySpec(ByteArray(32) { 8 }, "AES")
        val encrypted = EncryptedQueueSnapshot.encrypt(queue.snapshot(), key)

        assertEquals(queue.snapshot(), EncryptedQueueSnapshot.decrypt(encrypted, key))
        assertFailsWith<Exception> { EncryptedQueueSnapshot.decrypt(encrypted, wrongKey) }
    }

    @Test
    fun syncPutBodyContainsOnlyRevisionAndEnvelope() {
        val body = VaultSyncRequest.putBody(4, "salt", "nonce", "ciphertext")

        assertEquals("{\"revision\":4,\"envelope\":{\"version\":1,\"salt\":\"salt\",\"nonce\":\"nonce\",\"ciphertext\":\"ciphertext\"}}", body)
        assertEquals(false, body.contains("password"))
        assertEquals(false, body.contains("accounts"))
    }

    @Test
    fun httpClientPolicyDisablesRedirects() {
        assertEquals(false, VaultSyncClient.followRedirects)
    }

    @Test
    fun remoteHttpEndpointIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            VaultSyncClient("http://example.com", "device-token")
        }
        VaultSyncClient("http://127.0.0.1:8787", "device-token")
    }

    @Test
    fun successfulSyncRemovesOnlyDeliveredChange() {
        val queue = SyncQueue().apply { enqueue("revision-1", "ciphertext-1") }
        val coordinator = SyncCoordinator(queue) { SyncResult.Saved }

        assertEquals(SyncResult.Saved, coordinator.syncNext())
        assertEquals(0, queue.size())
    }

    @Test
    fun localVersionCanBeRetriedAgainstFreshServerRevisionAfterConflict() {
        val queue = SyncQueue().apply { enqueue("local", "encrypted-local") }
        var revision = 4L
        val sentRevisions = mutableListOf<Long>()
        val send = { change: PendingChange ->
            sentRevisions.add(revision)
            if (sentRevisions.size == 1) SyncResult.Conflict else SyncResult.Saved
        }

        assertEquals(SyncResult.Conflict, SyncCoordinator(queue, send).syncNext())
        revision = 5
        assertEquals(SyncResult.Saved, SyncCoordinator(queue, send).syncNext())

        assertEquals(listOf(4L, 5L), sentRevisions)
        assertEquals(0, queue.size())
    }

    @Test
    fun conflictKeepsChangeQueuedForResolution() {
        val queue = SyncQueue().apply { enqueue("revision-1", "ciphertext-1") }
        val coordinator = SyncCoordinator(queue) { SyncResult.Conflict }

        assertEquals(SyncResult.Conflict, coordinator.syncNext())
        assertEquals(1, queue.size())
    }

    @Test
    fun syncSettingsRoundTripPreservesEndpointAndToken() {
        val settings = SyncSettings("http://127.0.0.1:8787", "token|with-specials")
        assertEquals(settings, SyncSettings.decode(settings.encode()))
    }

    @Test
    fun vaultRevisionIsReadFromServerResponse() {
        assertEquals(7L, VaultRevision.parse("{\"revision\":7,\"envelope\":null,\"conflicts\":[]}"))
        assertFailsWith<IllegalArgumentException> { VaultRevision.parse("{\"revision\":\"bad\"}") }
    }

    @Test
    fun serverSnapshotParserReadsEncryptedEnvelopeAndConflicts() {
        val json = """
            {"revision":7,"envelope":{"version":1,"salt":"AAECAwQFBgcICQoLDA0ODw==","nonce":"EBESExQVFhcYGRob","ciphertext":"22mB/VRpIh6xhbUgIfn/sm48dTMHsiTNk5zfXLZCVA=="},"conflicts":[{"id":"conf-1","revision":6,"envelope":{"version":1,"salt":"AAECAwQFBgcICQoLDA0ODw==","nonce":"EBESExQVFhcYGRob","ciphertext":"22mB/VRpIh6xhbUgIfn/sm48dTMHsiTNk5zfXLZCVA=="}}]}
        """.trimIndent()

        val snapshot = VaultRevision.parseSnapshot(json)

        assertEquals(7, snapshot.revision)
        assertEquals("22mB/VRpIh6xhbUgIfn/sm48dTMHsiTNk5zfXLZCVA==", snapshot.envelope?.ciphertext)
        assertEquals("conf-1", snapshot.conflicts.single().id)
        assertEquals(6, snapshot.conflicts.single().revision)
    }

    @Test
    fun serverSnapshotParserRejectsMalformedEnvelope() {
        assertFailsWith<IllegalArgumentException> {
            VaultRevision.parseSnapshot("{\"revision\":1,\"envelope\":{\"version\":1},\"conflicts\":[]}")
        }
        assertFailsWith<IllegalArgumentException> {
            VaultRevision.parseSnapshot("{\"revision\":1,\"envelope\":{\"version\":1,\"salt\":\"AA==\",\"nonce\":\"AA==\",\"ciphertext\":\"AA==\"},\"conflicts\":[]}")
        }
    }

    @Test
    fun serverSnapshotParserRejectsMalformedConflictInsteadOfSkippingIt() {
        assertFailsWith<IllegalArgumentException> {
            VaultRevision.parseSnapshot("{\"revision\":1,\"envelope\":null,\"conflicts\":[{\"id\":\"x\"}]}")
        }
    }

    @Test
    fun serverEnvelopeIsValidatedBeforePortableDecrypt() {
        assertFailsWith<IllegalArgumentException> {
            PortableVaultCrypto.decrypt(VaultEnvelope(2, "AA==", "AA==", "AA=="), "master-password-123".toCharArray())
        }
        assertFailsWith<IllegalArgumentException> {
            PortableVaultCrypto.decrypt(VaultEnvelope(1, "AA==", "AA==", "AA=="), "master-password-123".toCharArray())
        }
    }

    @Test
    fun serverSnapshotParserAcceptsEmptyVault() {
        val snapshot = VaultRevision.parseSnapshot("{\"revision\":0,\"envelope\":null,\"conflicts\":[]}")

        assertEquals(0, snapshot.revision)
        assertEquals(null, snapshot.envelope)
        assertEquals(0, snapshot.conflicts.size)
    }

    @Test
    fun portableCryptoDecryptsWebCryptoFixture() {
        val envelope = VaultEnvelope(
            1,
            "AAECAwQFBgcICQoLDA0ODw==",
            "EBESExQVFhcYGRob",
            "22mB/VRpIh6xhbUgIfn/sm48dTMHsiTNk5zfXLZCVA=="
        )

        assertEquals(
            "{\"accounts\":[]}",
            PortableVaultCrypto.decrypt(envelope, "master-password-123".toCharArray())
        )
    }

    @Test
    fun portableCryptoRoundTripsAndUsesWebsiteEnvelopeMetadata() {
        val envelope = PortableVaultCrypto.encrypt("{\"accounts\":[]}", "master-password-123".toCharArray())

        assertEquals(1, envelope.version)
        assertEquals(16, java.util.Base64.getDecoder().decode(envelope.salt).size)
        assertEquals(12, java.util.Base64.getDecoder().decode(envelope.nonce).size)
        assertEquals("{\"accounts\":[]}", PortableVaultCrypto.decrypt(envelope, "master-password-123".toCharArray()))
    }

    @Test
    fun portableEnvelopePackingPreservesStandardBase64Values() {
        val envelope = PortableVaultCrypto.encrypt("roundtrip", "master-password-123".toCharArray())

        assertEquals(envelope, PortableVaultCrypto.unpack(PortableVaultCrypto.pack(envelope)))
    }

    @Test
    fun localVaultCanBeRestoredFromPortableServerEnvelopeWithoutReplacingOnFailure() {
        val local = LocalVault().apply { addAccount("Local", "Keep", emptyList()) }
        val original = local.snapshot()
        val remote = LocalVault().apply { addAccount("Remote", "Accept", emptyList()) }
        val envelope = PortableVaultCrypto.encrypt(remote.snapshot(), "master-password-123".toCharArray())

        val decrypted = PortableVaultCrypto.decrypt(envelope, "master-password-123".toCharArray())
        val restored = LocalVault.fromSnapshot(decrypted)
        assertEquals("Local", LocalVault.fromSnapshot(original).accounts.single().category)
        assertEquals("Remote", restored.accounts.single().category)
        assertFailsWith<Exception> {
            PortableVaultCrypto.decrypt(envelope, "incorrect-password".toCharArray())
        }
        assertEquals("Local", local.accounts.single().category)
    }

    @Test
    fun queuedPortableEnvelopeCanBeSentAsServerFields() {
        val original = PortableVaultCrypto.encrypt("vault", "master-password-123".toCharArray())
        val restored = PortableVaultCrypto.unpack(PortableVaultCrypto.pack(original))
        val body = VaultSyncRequest.putBody(5, restored.version, restored.salt, restored.nonce, restored.ciphertext)

        assertEquals(true, body.contains("\"version\":1"))
        assertEquals(true, body.contains("\"salt\":\"${original.salt}\""))
        assertEquals(true, body.contains("\"nonce\":\"${original.nonce}\""))
        assertEquals(true, body.contains("\"ciphertext\":\"${original.ciphertext}\""))
        assertEquals(false, body.contains("vault\""))
    }

    @Test
    fun portableCryptoRejectsWrongMasterPassword() {
        val envelope = PortableVaultCrypto.encrypt("private vault", "correct-horse".toCharArray())

        assertFailsWith<Exception> {
            PortableVaultCrypto.decrypt(envelope, "wrong-horse".toCharArray())
        }
    }

    @Test
    fun encryptedSnapshotCanBecomeServerEnvelopeWithoutCleartext() {
        val key = SecretKeySpec(ByteArray(32) { 3 }, "AES")
        val encrypted = EncryptedQueueSnapshot.encrypt("secret-value", key)
        val envelope = EncryptedQueueSnapshot.toEnvelope(encrypted)

        assertEquals(1, envelope.version)
        assertEquals(false, envelope.ciphertext.contains("secret-value"))
        assertEquals(encrypted.substringBefore('.'), envelope.nonce)
    }

    @Test
    fun deviceRegistrationRequestContainsOnlyDeviceName() {
        val body = VaultSyncRequest.registerDeviceBody("Tecno Spark 20 Pro")

        assertEquals("{\"name\":\"Tecno Spark 20 Pro\"}", body)
    }

    @Test
    fun deviceRegistrationResponseReadsIdAndToken() {
        val registration = DeviceRegistration.parse("{\"deviceId\":\"d1\",\"deviceToken\":\"token-123\"}")

        assertEquals("d1", registration.deviceId)
        assertEquals("token-123", registration.deviceToken)
    }

    @Test
    fun conflictResolutionRequestSelectsWholeServerEnvelope() {
        val body = VaultSyncRequest.resolveBody("conf-1")

        assertEquals("{\"conflictId\":\"conf-1\"}", body)
    }

    @Test
    fun acceptingServerConflictClearsQueueOnlyAfterServerResolutionSucceeds() {
        val queue = SyncQueue().apply { enqueue("revision-1", "encrypted-local") }
        queue.takeNext()!!

        val result = ConflictResolution.acceptServerVersion(queue) { throw IllegalStateException("server unavailable") }

        assertEquals(false, result)
        assertEquals(1, queue.size())
        assertEquals("encrypted-local", queue.takeNext()?.ciphertext)
    }

    @Test
    fun acceptingServerConflictClearsQueueAfterServerResolutionSucceeds() {
        val queue = SyncQueue().apply { enqueue("revision-1", "encrypted-local") }
        queue.takeNext()!!

        val result = ConflictResolution.acceptServerVersion(queue) { 7L }

        assertEquals(true, result)
        assertEquals(0, queue.size())
    }

    @Test
    fun conflictChoiceKeepsLocalVersionWithoutServerResolution() {
        val decision = ConflictResolution.keepLocal()

        assertEquals(ConflictDecision.KeepLocal, decision)
    }

    @Test
    fun conflictResolutionFindsOnlyRequestedServerConflict() {
        val snapshot = VaultSnapshot(3, null, listOf(
            VaultConflict("a", 1, VaultEnvelope(1, "s", "n", "c")),
            VaultConflict("b", 2, VaultEnvelope(1, "s", "n", "d"))
        ))

        assertEquals("b", ConflictResolution.find(snapshot, "b")?.id)
        assertEquals(null, ConflictResolution.find(snapshot, "missing"))
    }

    @Test
    fun conflictChoiceAcceptsWholeServerVersion() {
        val decision = ConflictResolution.acceptServer("conf-1")

        assertEquals(ConflictDecision.AcceptServer("conf-1"), decision)
    }

    @Test
    fun localChoiceKeepsPendingEncryptedChange() {
        val queue = SyncQueue()
        queue.enqueue("revision-1", "encrypted-local")
        val change = queue.takeNext()!!

        assertEquals(SyncResult.Conflict, SyncCoordinator(queue) { SyncResult.Conflict }.syncNext())
        assertEquals(1, queue.size())
        assertEquals(change.ciphertext, queue.takeNext()?.ciphertext)
    }

    @Test
    fun localVaultRejectsMalformedRecordInsteadOfDroppingIt() {
        assertFailsWith<IllegalArgumentException> {
            LocalVault.fromSnapshot("valid|broken")
        }
    }

    @Test
    fun localVaultRejectsMalformedFieldInsteadOfPartiallyRestoring() {
        assertFailsWith<IllegalArgumentException> {
            LocalVault.fromSnapshot("a|b|c|broken-field")
        }
    }

    @Test
    fun scrollableScreenFillsAvailableViewport() {
        assertEquals(true, AndroidLayoutPolicy.scrollable)
        assertEquals(true, AndroidLayoutPolicy.fillViewport)
    }

    @Test
    fun clipboardClearDelayIsBoundedForCopiedSecrets() {
        assertEquals(true, ClipboardPolicy.clearDelayMs in 1_000L..120_000L)
    }

    @Test
    fun masterPasswordTextIsClearedAfterUse() {
        val password = "master-password-123"

        assertEquals("", MasterPasswordSession.clearAfterUse(password))
    }

    @Test
    fun shortMasterPasswordIsRejectedBeforePortableEncryption() {
        assertFailsWith<IllegalArgumentException> {
            MasterPasswordSession.requireValid("a1!")
        }
    }

    @Test
    fun localVaultDisplayMasksPasswordButKeepsOtherFieldsVisible() {
        val password = VaultField("Пароль", "secret")
        val login = VaultField("Почта", "player@example.com")

        assertEquals("••••••", VaultDisplay.visibleValue(password))
        assertEquals("••••••", VaultDisplay.visibleValue(VaultField("Пароль Steam", "secret-2")))
        assertEquals("••••••", VaultDisplay.visibleValue(VaultField("API token", "secret-3")))
        assertEquals("player@example.com", VaultDisplay.visibleValue(login))
    }

    @Test
    fun localVaultDisplayListsCategoryAccountAndEachField() {
        val vault = LocalVault().apply {
            addAccount("Игры", "Steam", listOf(VaultField("Почта", "player@example.com"), VaultField("Пароль", "secret")))
        }

        val display = VaultDisplay.format(vault)

        assertEquals(true, display.contains("Игры / Steam"))
        assertEquals(true, display.contains("Почта: player@example.com"))
        assertEquals(true, display.contains("Пароль: ••••••"))
        assertEquals(false, display.contains("Пароль: secret"))
    }

    @Test
    fun emulatorHostAliasIsAllowedForLocalDevelopment() {
        VaultSyncClient("http://10.0.2.2:8788", "device-token")
    }

    @Test
    fun emulatorHostAliasUsesLocalDevelopmentPolicy() {
        val uri = java.net.URI("http://10.0.2.2:8788")
        assertEquals("10.0.2.2", uri.host)
        VaultSyncClient("http://10.0.2.2:8788", "device-token")
    }

    @Test
    fun remoteHttpRemainsRejected() {
        assertFailsWith<IllegalArgumentException> {
            VaultSyncClient("http://192.168.1.20:8788", "device-token")
        }
    }

    @Test
    fun homePcSupportsHttpButOtherSchemesAndLookalikesDoNot() {
        VaultSyncClient("http://192.168.3.2:8788", "device-token")
        assertFailsWith<IllegalArgumentException> { VaultSyncClient("ftp://192.168.3.2:8788", "device-token") }
        assertFailsWith<IllegalArgumentException> { VaultSyncClient("http://192.168.3.2.evil.test:8788", "device-token") }
    }

    @Test
    fun websiteCategoryAccountPayloadIsParsedWithoutLosingFields() {
        val websitePayload = """{"categories":[{"id":"cat-1","name":"Игры","icon":"★","accounts":[{"id":"acc-1","name":"Steam","fields":[{"id":"f1","label":"Логин","value":"player"},{"id":"f2","label":"Пароль","value":"secret"}]}]}]}"""

        val vault = LocalVault.fromPortableJson(websitePayload)

        assertEquals(1, vault.accounts.size)
        assertEquals("Игры", vault.accounts.single().category)
        assertEquals("Steam", vault.accounts.single().name)
        assertEquals(listOf(VaultField("Логин", "player"), VaultField("Пароль", "secret")), vault.accounts.single().fields)
        val restored = LocalVault.fromPortableJson(vault.toPortableJson())
        assertEquals(vault.accounts, restored.accounts)
        val websiteCategoryShape = Regex("\\\"categories\\\"\\s*:\\s*\\[.*\\\"name\\\"\\s*:\\s*\\\"Игры\\\".*\\\"accounts\\\"\\s*:\\s*\\[.*\\\"name\\\"\\s*:\\s*\\\"Steam\\\".*\\\"fields\\\"\\s*:", RegexOption.DOT_MATCHES_ALL)
        assertEquals(true, websiteCategoryShape.containsMatchIn(vault.toPortableJson()))
    }

    @Test
    fun localVaultRoundTripPreservesCategoriesAccountsAndCustomFields() {
        val vault = LocalVault().apply {
            addAccount("Игры", "Steam", listOf(
                VaultField("Почта", "player@example.com"),
                VaultField("Пароль", "secret-value"),
                VaultField("Код", "ABC-123")
            ))
        }

        val restored = LocalVault.fromSnapshot(vault.snapshot())
        val account = restored.accounts.single()

        assertEquals("Игры", account.category)
        assertEquals("Steam", account.name)
        assertEquals("secret-value", account.fields[1].value)
        assertEquals(3, account.fields.size)
    }

    @Test
    fun editingAccountReplacesItsContentsWithoutChangingItsIdOrOtherAccounts() {
        val vault = LocalVault().apply {
            addAccount("Игры", "Steam", listOf(VaultField("Пароль", "old")))
            addAccount("Почта", "Mail", listOf(VaultField("Логин", "keep")))
        }
        val editedId = vault.accounts.first().id

        val edited = vault.updateAccount(editedId, "Игры", "Steam 2", listOf(VaultField("Пароль", "new")))

        assertEquals(true, edited)
        assertEquals(2, vault.accounts.size)
        assertEquals(editedId, vault.accounts.first().id)
        assertEquals("Steam 2", vault.accounts.first().name)
        assertEquals("new", vault.accounts.first().fields.single().value)
        assertEquals("Mail", vault.accounts.last().name)
    }

    @Test
    fun editedAccountCanBeQueuedAsTheLatestPortableSnapshot() {
        val vault = LocalVault().apply {
            addAccount("Игры", "Steam", listOf(VaultField("Пароль", "old")))
        }
        val queue = SyncQueue()
        val account = vault.accounts.single()

        vault.updateAccount(account.id, "Игры", "Steam", listOf(VaultField("Пароль", "new")))
        queue.enqueue("edited", vault.toPortableJson())

        assertEquals(1, queue.size())
        assertEquals("new", LocalVault.fromPortableJson(queue.takeNext()!!.ciphertext).accounts.single().fields.single().value)
    }

    @Test
    fun localVaultSearchMatchesCategoryNameAndFieldValues() {
        val vault = LocalVault().apply {
            addAccount("Игры", "Steam", listOf(VaultField("Почта", "player@example.com")))
            addAccount("Почта", "Gmail", listOf(VaultField("Логин", "reader@example.com")))
        }

        assertEquals(1, vault.search("steam").size)
        assertEquals("Gmail", vault.search("reader@example.com").single().name)
        assertEquals("Steam", vault.search("player@example.com").single().name)
        assertEquals(2, vault.search("").size)
    }

    @Test
    fun latestSnapshotQueueCoalescesEditedAndDeletedVaultState() {
        val vault = LocalVault().apply {
            addAccount("Игры", "Steam", listOf(VaultField("Пароль", "old")))
            addAccount("Почта", "Mail", listOf(VaultField("Логин", "keep")))
        }
        val queue = SyncQueue()
        val editedId = vault.accounts.first().id
        vault.updateAccount(editedId, "Игры", "Steam", listOf(VaultField("Пароль", "new")))
        queue.enqueue("edit", vault.toPortableJson())
        vault.removeAccount(editedId)
        queue.enqueue("delete", vault.toPortableJson())

        val pending = queue.takeNext()!!
        val restored = LocalVault.fromPortableJson(pending.ciphertext)
        assertEquals(1, restored.accounts.size)
        assertEquals("Mail", restored.accounts.single().name)
        assertEquals(1, queue.size())
    }

    @Test
    fun localVaultCanRemoveOneAccountWithoutRemovingOthers() {
        val vault = LocalVault().apply {
            addAccount("Игры", "Steam", emptyList())
            addAccount("Почта", "Gmail", emptyList())
        }

        val removed = vault.removeAccount(vault.accounts.first().id)

        assertEquals(true, removed)
        assertEquals(listOf("Gmail"), vault.accounts.map { it.name })
    }

    @Test
    fun syncTaskReportsFailureToCompletionCallback() {
        val worker = Executors.newSingleThreadExecutor()
        val failure = CountDownLatch(1)
        var received: Throwable? = null

        SyncTaskRunner(worker).execute({ error("network failure") }) {
            received = it
            failure.countDown()
        }

        assertEquals(true, failure.await(2, TimeUnit.SECONDS))
        assertEquals("network failure", received?.message)
        worker.shutdownNow()
    }

    @Test
    fun syncTaskRunsOnWorkerExecutor() {
        val worker = java.util.concurrent.Executors.newSingleThreadExecutor()
        val completed = java.util.concurrent.CountDownLatch(1)
        var taskThread: Thread? = null
        try {
            SyncTaskRunner(worker).execute({ taskThread = Thread.currentThread() }) { _ -> completed.countDown() }
            assertEquals(true, completed.await(5, java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(false, Thread.currentThread() === taskThread)
        } finally {
            worker.shutdownNow()
        }
    }
}
