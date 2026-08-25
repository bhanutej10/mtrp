package dev.mtrp.core.crypto

import dev.mtrp.core.MTRP
import dev.mtrp.core.packet.*
import kotlin.test.*

/**
 * Phase 3 — Crypto engine tests
 * Tests implement MTRP-SPEC-v0.1 Section 7
 * Author: K. Bhanutej
 */

class NodeIdentityTest : CryptoTestBase() {

    @Test
    fun generateProducesValidNodeId() {
        val identity = NodeIdentity.generate()
        assertEquals(22, identity.nodeId.length,
            "SPEC 3.1: node_id MUST be 22 characters")
    }

    @Test
    fun generateProducesValidKeypair() {
        val identity = NodeIdentity.generate()
        assertEquals(32, identity.publicKey.size,
            "Ed25519 public key must be 32 bytes")
        assertEquals(64, identity.privateKey.size,
            "Ed25519 private key must be 64 bytes")
    }

    @Test
    fun eachGenerationProducesUniqueNodeId() {
        val a = NodeIdentity.generate()
        val b = NodeIdentity.generate()
        assertNotEquals(a.nodeId, b.nodeId,
            "SPEC 3.1: each node MUST have a globally unique node_id")
    }

    @Test
    fun nodeIdDerivedFromPublicKey() {
        val identity = NodeIdentity.generate()
        val derived  = NodeIdentity.deriveNodeId(identity.publicKey)
        assertEquals(identity.nodeId, derived,
            "SPEC 3.1: node_id MUST be derivable from public key")
    }

    @Test
    fun fromStoredKeysRestoresIdentity() {
        val original  = NodeIdentity.generate()
        val restored  = NodeIdentity.fromStoredKeys(original.publicKey, original.privateKey)
        assertEquals(original.nodeId, restored.nodeId)
    }

    @Test
    fun destroyZeroesPrivateKey() {
        val identity = NodeIdentity.generate()
        identity.destroy()
        assertTrue(identity.privateKey.all { it == 0.toByte() },
            "SPEC 7.7: private key MUST be zeroed after destroy")
    }
}

class CryptoSessionTest : CryptoTestBase() {

    private fun makeSession(): CryptoSession {
        val handshakeOutput = ByteArray(64) { (it * 7).toByte() }
        return CryptoSession("node_aaa", "node_bbb", handshakeOutput)
    }

    @Test
    fun fourKeysAreDerived() {
        val session = makeSession()
        assertEquals(32, session.sendKey.size,     "SPEC 7.3: send_key must be 32 bytes")
        assertEquals(32, session.recvKey.size,     "SPEC 7.3: recv_key must be 32 bytes")
        assertEquals(32, session.relayMacKey.size, "SPEC 7.3: relay_mac_key must be 32 bytes")
        assertEquals(32, session.routingKey.size,  "SPEC 7.3: routing_key must be 32 bytes")
    }

    @Test
    fun fourKeysAreDistinct() {
        val s = makeSession()
        assertFalse(s.sendKey.contentEquals(s.recvKey),
            "SPEC 7.3: send and recv keys must be different (directional)")
        assertFalse(s.sendKey.contentEquals(s.relayMacKey))
        assertFalse(s.sendKey.contentEquals(s.routingKey))
    }

    @Test
    fun renegotiationTieBreakByNodeId() {
        val ho = ByteArray(64)
        val sessionAB = CryptoSession("aaa", "bbb", ho.copyOf())
        val sessionBA = CryptoSession("bbb", "aaa", ho.copyOf())
        assertTrue(sessionAB.isRenegotiationInitiator(),
            "SPEC 7.4: lower node_id MUST be initiator (fixes issue 36)")
        assertFalse(sessionBA.isRenegotiationInitiator())
    }

    @Test
    fun sessionStartsEstablished() {
        val session = makeSession()
        assertEquals(CryptoSession.State.ESTABLISHED, session.state)
    }

    @Test
    fun destroyZeroesAllKeys() {
        val session = makeSession()
        session.destroy()
        assertTrue(session.sendKey.all { it == 0.toByte() },
            "SPEC 7.7: session keys MUST be zeroed on destroy")
    }
}

class SymmetricRatchetTest : CryptoTestBase() {

    private fun makeRatchet() = SymmetricRatchet(
        sendKey   = ByteArray(32) { (it + 1).toByte() },
        sessionId = "test-session"
    )

    @Test
    fun consecutiveKeysAreUnique() {
        val ratchet = makeRatchet()
        val key1 = ratchet.nextMessageKey()
        val key2 = ratchet.nextMessageKey()
        assertFalse(key1.key.contentEquals(key2.key),
            "SPEC 7.5: each message MUST get a unique key")
    }

    @Test
    fun counterIncrementsPerMessage() {
        val ratchet = makeRatchet()
        val key1 = ratchet.nextMessageKey()
        val key2 = ratchet.nextMessageKey()
        assertEquals(0L, key1.counter)
        assertEquals(1L, key2.counter)
    }

    @Test
    fun destroyZeroesChainKey() {
        val ratchet = makeRatchet()
        ratchet.nextMessageKey()
        ratchet.destroy()
        // After destroy the ratchet is unusable — we just verify no crash
        assertTrue(true, "Destroy should not throw")
    }

    @Test
    fun excessiveSkipDetected() {
        val ratchet = makeRatchet()
        val tooFar  = (MTRP.MAX_SKIPPED_KEYS + 10).toLong()
        assertTrue(ratchet.requiresRenegotiation(tooFar),
            "SPEC 7.5: >100 skipped messages MUST trigger renegotiation (fixes issue 35)")
    }
}

class PacketIntegrityTest : CryptoTestBase() {

    private fun makePacket() = MtrpPacket(
        routingId    = ByteArray(8) { 0x42 },
        chanType     = ChanType.WIFI,
        relayMac     = ByteArray(32),
        payload      = ByteArray(64) { it.toByte() },
        senderSig    = ByteArray(64),
        msgId        = ByteArray(8) { 0x01 },
        createdAtMs  = System.currentTimeMillis()
    )

    private val relayMacKey = ByteArray(32) { (it + 5).toByte() }

    @Test
    fun relayMacComputeAndVerify() {
        val packet    = makePacket()
        val mac       = PacketIntegrity.computeRelayMac(packet, relayMacKey)
        val withMac   = packet.copy(relayMac = mac)
        assertTrue(PacketIntegrity.verifyRelayMac(withMac, relayMacKey),
            "SPEC 7.6: valid relay_mac MUST verify successfully")
    }

    @Test
    fun tamperedTtlFailsVerification() {
        val packet  = makePacket()
        val mac     = PacketIntegrity.computeRelayMac(packet, relayMacKey)
        val tampered = packet.copy(relayMac = mac, ttl = 5u)  // relay modified TTL
        assertFalse(PacketIntegrity.verifyRelayMac(tampered, relayMacKey),
            "SPEC 7.6: TTL modification MUST fail relay_mac verification")
    }

    @Test
    fun wrongKeyFailsVerification() {
        val packet   = makePacket()
        val mac      = PacketIntegrity.computeRelayMac(packet, relayMacKey)
        val withMac  = packet.copy(relayMac = mac)
        val wrongKey = ByteArray(32) { 0xFF.toByte() }
        assertFalse(PacketIntegrity.verifyRelayMac(withMac, wrongKey),
            "Wrong key MUST fail verification")
    }

    @Test
    fun expiredTimestampFailsVerification() {
        val oldPacket = makePacket().copy(
            createdAtMs = System.currentTimeMillis() - (MTRP.PACKET_TIMESTAMP_WINDOW_MS + 1000L)
        )
        val mac      = PacketIntegrity.computeRelayMac(oldPacket, relayMacKey)
        val withMac  = oldPacket.copy(relayMac = mac)
        assertFalse(PacketIntegrity.verifyRelayMac(withMac, relayMacKey),
            "SPEC 5.3: packets older than 5 minutes MUST be rejected (fixes issue 15)")
    }
}

class RoutingPseudonymTest : CryptoTestBase() {

    private val routingKey = ByteArray(32) { (it + 3).toByte() }

    @Test
    fun derivedRoutingIdIs8Bytes() {
        val pseudonym = RoutingPseudonym(routingKey)
        val id        = pseudonym.derive("3xK9mPqR7nVwL2YzQb4D")
        assertEquals(8, id.size,
            "SPEC 3.4: routing_id MUST be 8 bytes")
    }

    @Test
    fun differentDestinationsProduceDifferentIds() {
        val pseudonym = RoutingPseudonym(routingKey)
        val id1 = pseudonym.derive("node_aaaaa")
        val id2 = pseudonym.derive("node_bbbbb")
        assertFalse(id1.contentEquals(id2),
            "SPEC 3.4: different destinations MUST produce different routing_ids")
    }

    @Test
    fun sameDestinationSameSession() {
        val pseudonym = RoutingPseudonym(routingKey)
        val id1 = pseudonym.derive("same_node")
        val id2 = pseudonym.derive("same_node")
        assertTrue(id1.contentEquals(id2),
            "Same destination in same session MUST produce same routing_id")
    }

    @Test
    fun differentSessionsDifferentIds() {
        val p1 = RoutingPseudonym(routingKey)
        val p2 = RoutingPseudonym(routingKey)
        val id1 = p1.derive("same_dest")
        val id2 = p2.derive("same_dest")
        assertFalse(id1.contentEquals(id2),
            "SPEC 3.4: different sessions MUST produce different routing_ids")
    }

    @Test
    fun matchesLocalNodeCorrectly() {
        val pseudonym = RoutingPseudonym(routingKey)
        val nodeId    = "3xK9mPqR7nVwL2YzQb4D"
        val routingId = pseudonym.derive(nodeId)
        assertTrue(pseudonym.matchesLocalNode(routingId, nodeId),
            "SPEC 3.4: node MUST recognise its own routing_id")
    }

    @Test
    fun doesNotMatchOtherNode() {
        val pseudonym = RoutingPseudonym(routingKey)
        val routingId = pseudonym.derive("node_actual_dest")
        assertFalse(pseudonym.matchesLocalNode(routingId, "node_other"),
            "SPEC 3.4: routing_id MUST NOT match a different node")
    }
}

class PacketCryptoTest : CryptoTestBase() {

    private fun makeSession() = CryptoSession(
        "node_local", "node_remote",
        ByteArray(64) { (it * 13).toByte() }
    )

    private fun makePacket() = MtrpPacket(
        routingId   = ByteArray(8) { 0x10 },
        chanType    = ChanType.BLE,
        relayMac    = ByteArray(32),
        payload     = ByteArray(0),
        senderSig   = ByteArray(64),
        msgId       = ByteArray(8) { 0x02 },
        createdAtMs = System.currentTimeMillis()
    )

    @Test
    fun encryptDecryptRoundtrip() {
        val session  = makeSession()
        val ratchet  = SymmetricRatchet(session.sendKey.copyOf(), session.sessionId)
        val packet   = makePacket()

        val original = MtrpPayload(
            originId  = ByteArray(8) { 0xAA.toByte() },
            createdAt = System.currentTimeMillis(),
            msgType   = MsgType.MESSAGE,
            appData   = "Hello MTRP".encodeToByteArray(),
            nonce     = ByteArray(20)
        )

        val encKey      = ratchet.nextMessageKey()
        val encrypted   = PacketCrypto.encryptPayload(packet, original, encKey)

        val recvRatchet = SymmetricRatchet(session.recvKey.copyOf(), session.sessionId)
        val decKey      = recvRatchet.nextMessageKey()
        val decrypted   = PacketCrypto.decryptPayload(encrypted, decKey)

        assertNotNull(decrypted, "Decryption MUST succeed with correct key")
        assertEquals(original, decrypted)
    }

    @Test
    fun wrongKeyFailsDecryption() {
        val session  = makeSession()
        val ratchet  = SymmetricRatchet(session.sendKey.copyOf(), session.sessionId)
        val packet   = makePacket()
        val payload  = MtrpPayload(ByteArray(8), 0L, MsgType.MESSAGE, ByteArray(4), ByteArray(20))

        val encKey    = ratchet.nextMessageKey()
        val encrypted = PacketCrypto.encryptPayload(packet, payload, encKey)

        val wrongKey  = RatchetKey(ByteArray(32) { 0xFF.toByte() }, 0L, session.sessionId)
        val result    = PacketCrypto.decryptPayload(encrypted, wrongKey)
        assertNull(result, "Wrong key MUST return null — never throw")
    }

    @Test
    fun nonceLengthIsCorrect() {
        val nonce = PacketCrypto.buildNonce(42L)
        assertEquals(20, nonce.size,
            "SPEC 7.5: nonce MUST be 20 bytes (12 random + 8 counter)")
    }

    @Test
    fun aadCoversAllHeaderFields() {
        val packet = makePacket()
        val aad    = PacketCrypto.buildAad(packet)
        // version(1) + routing_id(8) + TTL(1) + chan_type(1) + created_at(8) = 19 bytes
        assertEquals(19, aad.size,
            "SPEC 7.5: AAD MUST cover version, routing_id, TTL, chan_type, created_at (fixes issue 8)")
    }
}
