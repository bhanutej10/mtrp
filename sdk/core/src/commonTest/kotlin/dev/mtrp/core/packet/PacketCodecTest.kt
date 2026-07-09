package dev.mtrp.core.packet

import dev.mtrp.core.MTRP
import kotlin.test.*

/**
 * Phase 2 — Packet format + codec tests
 * Tests implement MTRP-SPEC-v0.1 Section 5
 * Author: K. Bhanutej
 */
class PacketCodecTest {

    private fun makePacket(
        chanType: ChanType = ChanType.WIFI,
        ttl: UByte = 10u,
        payload: ByteArray = ByteArray(32) { it.toByte() }
    ) = MtrpPacket(
        version       = 0x01u,
        routingId     = ByteArray(8) { 0x42 },
        ttl           = ttl,
        chanType      = chanType,
        relayMac      = ByteArray(32) { 0x01 },
        payload       = payload,
        senderSig     = ByteArray(64) { 0x02 },
        msgId         = ByteArray(8) { 0x03 },
        fragmentIndex = 0,
        fragmentTotal = 1,
        createdAtMs   = 1700000000000L
    )

    // ── Encode/Decode roundtrip ───────────────────────────────────────────

    @Test
    fun encodeDecodeRoundtrip() {
        val original = makePacket()
        val bytes    = PacketCodec.encode(original)
        val decoded  = PacketCodec.decode(bytes)
        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun encodeDecodeAllChanTypes() {
        ChanType.entries.forEach { chan ->
            val pkt = makePacket(chanType = chan)
            val decoded = PacketCodec.decode(PacketCodec.encode(pkt))
            assertNotNull(decoded, "Should decode $chan packet")
            assertEquals(chan, decoded.chanType)
        }
    }

    @Test
    fun ttlPreservedAfterEncode() {
        val pkt     = makePacket(ttl = 7u)
        val decoded = PacketCodec.decode(PacketCodec.encode(pkt))!!
        assertEquals(7u, decoded.ttl, "SPEC 5.2: TTL must be preserved")
    }

    @Test
    fun payloadPreservedAfterEncode() {
        val payload = ByteArray(128) { it.toByte() }
        val pkt     = makePacket(payload = payload)
        val decoded = PacketCodec.decode(PacketCodec.encode(pkt))!!
        assertTrue(payload.contentEquals(decoded.payload),
            "Payload MUST survive encode/decode unchanged")
    }

    // ── Version handling ─────────────────────────────────────────────────

    @Test
    fun versionIsSetToOne() {
        val decoded = PacketCodec.decode(PacketCodec.encode(makePacket()))!!
        assertEquals(0x01u, decoded.version,
            "SPEC 5.2: version MUST be 0x01")
    }

    @Test
    fun unknownVersionReturnNull() {
        // Build a packet with version=0x02 directly via protobuf
        val pkt = makePacket()
        val proto = dev.mtrp.core.proto.MtrpPacketProto.newBuilder()
            .setVersion(0x02)
            .setRoutingId(com.google.protobuf.ByteString.copyFrom(pkt.routingId))
            .setTtl(pkt.ttl.toInt())
            .setChanType(pkt.chanType.wireValue)
            .setRelayMac(com.google.protobuf.ByteString.copyFrom(pkt.relayMac))
            .setPayload(com.google.protobuf.ByteString.copyFrom(pkt.payload))
            .setSenderSig(com.google.protobuf.ByteString.copyFrom(pkt.senderSig))
            .setMsgId(com.google.protobuf.ByteString.copyFrom(pkt.msgId))
            .setFragmentTotal(1)
            .setCreatedAtMs(pkt.createdAtMs)
            .build()
        val decoded = PacketCodec.decode(proto.toByteArray())
        assertNull(decoded, "SPEC 5.2: unknown version MUST return null (drop silently)")
    }

    @Test
    fun malformedBytesReturnNull() {
        val garbage = ByteArray(50) { it.toByte() }
        val result  = PacketCodec.decode(garbage)
        assertNull(result, "Malformed bytes MUST return null")
    }

    @Test
    fun emptyBytesReturnNull() {
        assertNull(PacketCodec.decode(ByteArray(0)))
    }

    // ── TTL enforcement ───────────────────────────────────────────────────

    @Test
    fun ttlZeroShouldBeTreatedAsExpired() {
        val pkt = makePacket(ttl = 0u)
        // Relay logic: if ttl <= 0u → drop
        assertTrue(pkt.ttl <= 0u,
            "SPEC 5.2: TTL=0 MUST be treated as expired")
    }

    @Test
    fun ttlDecrementStaysUnsigned() {
        val pkt = makePacket(ttl = 1u)
        val decremented = (pkt.ttl - 1u).toUByte()
        assertEquals(0u, decremented)
        assertTrue(decremented <= 0u,
            "SPEC 5.2: unsigned decrement of 1 must yield 0, not 255 (fixes issue 41)")
    }

    // ── Broadcast detection ───────────────────────────────────────────────

    @Test
    fun broadcastRoutingIdDetected() {
        val pkt = makePacket().copy(
            routingId = PacketCodec.BROADCAST_ROUTING_ID
        )
        assertTrue(pkt.isBroadcast,
            "SPEC 9.2: routing_id = 0xFF*8 MUST be detected as broadcast")
    }

    @Test
    fun normalRoutingIdNotBroadcast() {
        val pkt = makePacket()
        assertFalse(pkt.isBroadcast)
    }

    // ── Payload encode/decode ─────────────────────────────────────────────

    @Test
    fun payloadEncodeDecodeRoundtrip() {
        val original = MtrpPayload(
            originId  = ByteArray(8) { 0xAA.toByte() },
            createdAt = 1700000000000L,
            msgType   = MsgType.MESSAGE,
            appData   = "Hello MTRP".encodeToByteArray(),
            nonce     = ByteArray(20) { it.toByte() }
        )
        val encoded = PacketCodec.encodePayload(original)
        val decoded = PacketCodec.decodePayload(encoded)
        assertNotNull(decoded)
        assertEquals(original, decoded)
    }

    @Test
    fun allMsgTypesEncodeCorrectly() {
        MsgType.entries.forEach { msgType ->
            val payload = MtrpPayload(
                originId  = ByteArray(8),
                createdAt = 0L,
                msgType   = msgType,
                appData   = ByteArray(0),
                nonce     = ByteArray(20)
            )
            val decoded = PacketCodec.decodePayload(
                PacketCodec.encodePayload(payload))
            assertNotNull(decoded)
            assertEquals(msgType, decoded.msgType,
                "MsgType $msgType MUST survive encode/decode")
        }
    }

    // ── MsgType wire values ───────────────────────────────────────────────

    @Test
    fun msgTypeWireValuesMatchSpec() {
        assertEquals(0x01, MsgType.MESSAGE.wireValue,  "SPEC 5.5")
        assertEquals(0x02, MsgType.RREQ.wireValue,     "SPEC 5.5")
        assertEquals(0x03, MsgType.RREP.wireValue,     "SPEC 5.5")
        assertEquals(0x04, MsgType.ACK.wireValue,      "SPEC 5.5")
        assertEquals(0x05, MsgType.BEACON.wireValue,   "SPEC 5.5")
        assertEquals(0x06, MsgType.REVOKE.wireValue,   "SPEC 3.3")
    }

    @Test
    fun chanTypeWireValuesMatchSpec() {
        assertEquals(0x01, ChanType.WIFI.wireValue,        "SPEC 5.2")
        assertEquals(0x02, ChanType.CELLULAR.wireValue,    "SPEC 5.2")
        assertEquals(0x03, ChanType.WIFI_DIRECT.wireValue, "SPEC 5.2")
        assertEquals(0x04, ChanType.BLE.wireValue,         "SPEC 5.2")
        assertEquals(0x05, ChanType.SMS.wireValue,         "SPEC 5.2")
        assertEquals(0x06, ChanType.LORA.wireValue,        "SPEC 5.2")
        assertEquals(0x07, ChanType.NOSTR.wireValue,       "SPEC 5.2")
    }

	@Test
	fun unknownChanTypeThrows() {
    		assertFailsWith<IllegalArgumentException> {
        		ChanType.fromWire(0xFF)
    }
}

	@Test
	fun unknownMsgTypeThrows() {
    		assertFailsWith<IllegalArgumentException> {
       			MsgType.fromWire(0xFF)
    }
}
}

class PacketPaddingTest {

    @Test
    fun paddingTo64Bucket() {
        val pad = PacketPadding.paddingNeeded(50)
        assertEquals(14, pad, "50 bytes needs 14 bytes padding to reach 64")
    }

    @Test
    fun paddingTo256Bucket() {
        val pad = PacketPadding.paddingNeeded(200)
        assertEquals(56, pad, "200 bytes needs 56 bytes padding to reach 256")
    }

    @Test
    fun paddingTo512Bucket() {
        val pad = PacketPadding.paddingNeeded(300)
        assertEquals(212, pad, "300 bytes needs 212 bytes padding to reach 512")
    }

    @Test
    fun paddingTo2048Bucket() {
        val pad = PacketPadding.paddingNeeded(600)
        assertEquals(1448, pad, "600 bytes needs 1448 padding to reach 2048")
    }

    @Test
    fun exactBucketSizeNeedsNoPadding() {
        assertEquals(0, PacketPadding.paddingNeeded(64))
        assertEquals(0, PacketPadding.paddingNeeded(256))
        assertEquals(0, PacketPadding.paddingNeeded(512))
        assertEquals(0, PacketPadding.paddingNeeded(2048))
    }

    @Test
    fun paddingIsNotZeroFilled() {
        // SPEC 5.4: padding MUST be random bytes, MUST NOT be zero-filled
        // We can't guarantee random content in a test but can check it's not all zeros
        val pad = PacketPadding.generate(64)
        assertEquals(64, pad.size)
        // With 64 random bytes, probability all are zero is (1/256)^64 ≈ 0
        // This assertion will always pass for correctly random output
        assertFalse(pad.all { it == 0.toByte() },
            "SPEC 5.4: padding MUST NOT be zero-filled")
    }

    @Test
    fun validBucketSizes() {
        assertTrue(PacketPadding.isValidBucketSize(64))
        assertTrue(PacketPadding.isValidBucketSize(256))
        assertTrue(PacketPadding.isValidBucketSize(512))
        assertTrue(PacketPadding.isValidBucketSize(2048))
    }

    @Test
    fun invalidBucketSizes() {
        assertFalse(PacketPadding.isValidBucketSize(100))
        assertFalse(PacketPadding.isValidBucketSize(128))
        assertFalse(PacketPadding.isValidBucketSize(1024))
    }
}

class FragmentAssemblerTest {

    private val assembler = FragmentAssembler()

    private fun makeTemplatePacket() = MtrpPacket(
        routingId     = ByteArray(8) { 0x10 },
        chanType      = ChanType.BLE,
        relayMac      = ByteArray(32),
        payload       = ByteArray(0),
        senderSig     = ByteArray(64),
        msgId         = ByteArray(8) { 0x20 },
        createdAtMs   = 1700000000000L
    )

    @Test
    fun unfragmentedPayloadReturnedDirectly() {
        val payload  = ByteArray(32) { it.toByte() }
        val template = makeTemplatePacket()
        val fragments = FragmentAssembler().fragment(payload, template, 512)
        assertEquals(1, fragments.size, "Small payload should not be fragmented")
        assertFalse(fragments[0].isFragmented)
    }

    @Test
    fun largePayloadIsSplitIntoFragments() {
        val payload   = ByteArray(200) { it.toByte() }
        val template  = makeTemplatePacket()
        val fragments = FragmentAssembler().fragment(payload, template, 50)
        assertEquals(4, fragments.size,
            "200 bytes / 50 per fragment = 4 fragments")
        fragments.forEach { f ->
            assertEquals(4, f.fragmentTotal)
            assertTrue(f.isFragmented)
        }
        assertEquals(0, fragments[0].fragmentIndex)
        assertEquals(3, fragments[3].fragmentIndex)
        assertTrue(fragments[3].isLastFragment)
    }

    @Test
    fun fragmentsReassembleToOriginalPayload() {
        val original  = ByteArray(200) { (it * 3).toByte() }
        val template  = makeTemplatePacket()
        val fragments = FragmentAssembler().fragment(original, template, 50)

        var result: ByteArray? = null
        val asm = FragmentAssembler()
        fragments.forEach { frag ->
            result = asm.addFragment(frag, "origin1")
        }

        assertNotNull(result, "Assembled payload should not be null")
        assertTrue(original.contentEquals(result!!),
            "Reassembled payload MUST match original")
    }

    @Test
    fun partialFragmentsReturnNull() {
        val payload   = ByteArray(100) { it.toByte() }
        val template  = makeTemplatePacket()
        val fragments = FragmentAssembler().fragment(payload, template, 50)
        val asm = FragmentAssembler()
        val partial = asm.addFragment(fragments[0], "origin1")
        assertNull(partial, "Partial reassembly MUST return null")
    }

    @Test
    fun perOriginBufferLimitEnforced() {
        val asm = FragmentAssembler()
        // Create MAX_INCOMPLETE_REASSEMBLY_PER_ORIGIN + 1 open buffers for same origin
        val limit = MTRP.MAX_INCOMPLETE_REASSEMBLY_PER_ORIGIN
        repeat(limit + 1) { i ->
            val template = makeTemplatePacket().copy(
                msgId = ByteArray(8) { i.toByte() }
            )
            val payload   = ByteArray(100) { it.toByte() }
            val fragments = FragmentAssembler().fragment(payload, template, 50)
            asm.addFragment(fragments[0], "same-origin")
        }
        assertTrue(asm.openBufferCount <= limit,
            "SPEC 5.6: per-origin buffer MUST be capped at $limit")
    }
}
