# MTRP — Multi Transport Relay Protocol
## Specification v0.1 — DRAFT

**Author:** K. Bhanutej
**Status:** Draft
**Version:** 0.1
**Created:** 2025
**Repository:** https://github.com/Bhanutej/mtrp

---

## Abstract

MTRP (Multi Transport Relay Protocol) is an open, transport-agnostic,
secure mesh messaging protocol designed for resilient communication
across environments where any single channel may be unavailable.

MTRP automatically selects the best transport channel from a
priority-ordered stack using a 4-dimension adaptive scoring formula,
supports unlimited multi-hop relay through intermediate nodes, and
guarantees end-to-end encryption using XChaCha20-Poly1305 with a
symmetric forward-secrecy ratchet — regardless of which channel
carries the packet.

---

## 1. Introduction

### 1.1 Problem Statement

Existing messaging systems depend on a single communication path.
When that path fails (disasters, remote areas, network outages,
infrastructure failure), communication stops entirely.

MTRP solves this with a multi-channel fallback stack and mesh routing.
If the best channel is unavailable, the protocol silently falls back
to the next. Messages hop through intermediate relay nodes to extend
reach beyond direct radio range. Relay nodes operate silently — their
users are unaware of relay activity.

### 1.2 Design Goals

1. **Resilience** — deliver if any viable path exists
2. **Transport agnosticism** — no dependency on any single channel
3. **Security** — E2E encrypted, forward secret, relay-blind
4. **Decentralisation** — no central server required
5. **Openness** — any developer may implement a conforming node
6. **Minimal power** — relay nodes use duty cycling, passive scanning
7. **Silent relay** — relay users see no indication of relay activity
8. **Platform independence** — Android and desktop (Linux/Windows)

### 1.3 Non-Goals

- MTRP does not guarantee delivery order
- MTRP does not guarantee real-time delivery
- MTRP does not define UI or UX
- MTRP does not manage contacts beyond node IDs
- MTRP does not provide anonymity against a global adversary

### 1.4 Terminology

The key words MUST, MUST NOT, SHOULD, SHOULD NOT, and MAY are
interpreted as described in RFC 2119.

**Node** — any device running a conforming MTRP implementation.
**Origin node** — the node that created and first sent a packet.
**Relay node** — a node forwarding a packet it is not the destination of.
**Destination node** — the node a packet is addressed to.
**Transport** — a single communication channel.
**Transport stack** — the ordered list of transports a node supports.
**Hop** — one relay step between adjacent nodes.
**TTL** — Time To Live; maximum remaining hops for a packet.
**msg_id** — globally unique packet identifier.
**node_id** — globally unique node identifier (22-char BASE58).
**routing_id** — per-session pseudonymous routing identifier.
**chain_key** — current ratchet state key for deriving message keys.
**message_key** — single-use key derived per message from chain_key.
**relay_mac** — HMAC relay nodes verify before forwarding.
**sender_sig** — Ed25519 signature destination verifies for origin proof.

---

## 2. Security Model

### 2.1 Threat Model

MTRP is designed to resist the following adversaries:

**Passive relay node** — observes header fields and traffic patterns.
Cannot read payload content.

**Active malicious relay** — attempts to modify, drop, replay, or
inject packets in addition to passive observation.

**Network observer** — monitors radio spectrum without participating.
Can observe packet sizes, timing, and frequency.

**Compromised node** — attacker has stolen a node's Ed25519 private
key. Can impersonate that node until revocation propagates.

**Protocol attacker** — exploits routing protocol mechanics via
Sybil attacks, black hole attacks, RREQ flooding, route poisoning,
wormhole attacks, and session state machine attacks.

### 2.2 Security Properties

- **Payload confidentiality** — relay nodes cannot read message content
- **Origin hiding** — relay nodes cannot determine the real sender
- **Destination hiding** — relay nodes see routing_id, not node_id
- **Relay integrity** — relay nodes cannot modify packets undetected
- **Forward secrecy** — past messages safe if current keys are leaked
- **Replay prevention** — replayed packets detected and dropped
- **Route integrity** — routing table entries are authenticated

### 2.3 Known Limitations (v0.1)

- No protection against a global adversary monitoring all nodes
- No full break-in recovery (planned for v0.2 via Double Ratchet)
- Physical radio jamming is outside the protocol scope
- Rooted Android device key storage cannot be guaranteed secure

---

## 3. Node Identity

### 3.1 Node ID Generation

Every node MUST generate a globally unique node_id on first launch:

```
keypair = Ed25519_generate_keypair()          // libsodium
node_id = BASE58( SHA256(keypair.public_key) )[0:22]
```

- 22 BASE58 characters (~128 bits of entropy)
- Generated once; persisted in secure local storage
- MUST NOT change unless user explicitly resets the node
- On rooted Android: implementations MUST warn the user that
  key security cannot be guaranteed by the Android Keystore

**Example:** `3xK9mPqR7nVwL2YzQb4D`

### 3.2 Key Pair Storage

- **Android:** Android Keystore (hardware-backed where available)
- **Desktop Linux:** libsecret / OS keychain
- **Desktop Windows:** Windows Credential Manager
- Private key: MUST NOT be exported, transmitted, or logged
- Public key: shared freely during peer discovery and handshake

### 3.3 Key Revocation


When a node detects or suspects key compromise, it MUST broadcast
a REVOKE packet:

```
revoke = {
    msg_type:  0x06 (REVOKE),
    node_id:   compromised_node_id,
    timestamp: unix_ms,
    sig:       Ed25519_Sign(private_key, SHA512(node_id || timestamp))
}
```

All receiving nodes MUST:
1. Verify sig against the node_id's public key
2. If valid: add to local revocation list (persisted to disk)
3. Refuse to route packets from or to revoked node_id
4. Rebroadcast the REVOKE on all channels (TTL = 10)

### 3.4 Routing ID — Session Pseudonym


Relay nodes MUST NOT see the real dest_id. Packets carry
a per-session routing_id:

```
routing_id = HMAC_SHA256(
    key  = routing_key,        // derived in Section 7.3
    data = dest_node_id || session_nonce
)[0:8]
```

The destination computes the same routing_id and recognises
its own packets. Relay nodes see only the pseudonym, which
rotates every session — preventing social graph construction.

---

## 4. Transport Channels and Priority

### 4.1 Channel Priority Stack


A conforming node MUST attempt channels in this exact priority order.
Lower number = higher priority = tried first.

| Pri | Channel      | Max Payload | Latency  | Internet | Relay Allowed |
|-----|--------------|-------------|----------|----------|---------------|
| 1   | WiFi         | 65536 bytes | ~20ms    | Yes      | Yes           |
| 2   | Cellular     | 65536 bytes | ~80ms    | Yes      | Yes           |
| 3   | WiFi Direct  | 65536 bytes | ~25ms    | No       | Yes           |
| 4   | BLE 5.x      | 512 bytes   | ~150ms   | No       | Yes           |
| 5   | SMS          | 140 bytes   | ~3000ms  | No       | **NO**        |
| 6   | LoRa         | 50 bytes    | ~1000ms  | No       | Yes           |
| 7   | Nostr Relay  | 65536 bytes | ~500ms   | Yes      | Yes           |
| 8   | Store & Fwd  | 65536 bytes | indefinite | No     | Yes           |

**SMS restriction :**
SMS MUST only be used by the origin node of a packet.
Relay nodes MUST NOT use SMS to forward packets.
This is enforced at the ChannelType level via `relayAllowed = false`
— not a runtime check that can be bypassed.
A relay node whose only available channel is SMS MUST queue the
packet in store-and-forward and wait for a relay-allowed channel.

### 4.2 Platform Channel Availability

| Channel      | Android | Desktop (Linux/Win) |
|--------------|---------|---------------------|
| WiFi         | YES     | YES                 |
| Cellular     | YES     | NO                  |
| WiFi Direct  | YES     | NO                  |
| BLE          | YES     | Partial (native lib)|
| SMS          | YES     | NO                  |
| LoRa (USB)   | YES     | YES                 |
| Nostr        | YES     | YES                 |
| Store & Fwd  | YES     | YES                 |

### 4.3 Adaptive 4-Dimension Scoring Formula


Channel selection uses a 4-dimension score. **Lower score = better.**

```
score = W_speed    × speed_score
      + W_power    × power_score
      + W_reach    × reach_score
      + W_reliable × reliability_score
```

Weights MUST be normalised to sum to 1.0 after context adjustment.

**Dynamic weights (shift based on context):**

| Condition              | W_speed | W_power | W_reach | W_reliable |
|------------------------|---------|---------|---------|------------|
| battery > 50%          | 0.40    | 0.20    | 0.20    | 0.20       |
| battery 20–50%         | 0.25    | 0.35    | 0.20    | 0.20       |
| battery < 20%          | 0.15    | 0.50    | 0.20    | 0.15       |
| battery < 5%           | 0.10    | 0.60    | 0.20    | 0.10       |
| peer_count == 0        | —       | —       | +0.15   | —          |
| avg_retries > 3        | —       | —       | —       | +0.25      |

**Battery thresholds are randomised** to prevent timing oracle attacks

```
threshold_low  = 20% + random_int(-3, +3)%
threshold_crit = 5%  + random_int(-1, +1)%
```

**Speed score:**
```
speed_score = 0.6 × clamp(latency_ms / 5000, 0, 1)
            + 0.4 × (1 - channel.bandwidthClass / 4)
```

**Power score:**
```
urgency     = battery>50%→1.0 | 20-50%→1.5 | 10-20%→3.0 | <5%→6.0
power_score = clamp(channel.powerIndex × urgency, 0, 1)
```

powerIndex per channel (0=most power hungry, 1=least):
WiFi=0.2, Cellular=0.4, WiFi_Direct=0.3, BLE=0.9,
SMS=0.7, LoRa=0.95, Nostr=0.2, Queued=1.0

**Reach score:**
```
reach_score = 0.5 × clamp(hop_count / 10, 0, 1)
            + 0.3 × (1 - clamp(peer_count / 10, 0, 1))
            + 0.2 × (1 - channel.offlineReachBonus)
```

offlineReachBonus per channel (higher = reaches more offline nodes):
WiFi=0.0, Cellular=0.0, WiFi_Direct=0.3, BLE=0.3,
SMS=0.5, LoRa=0.6, Nostr=0.0, Queued=0.0

**Reliability score (adaptive — learns from history):**
```
reliability_score = 0.5 × rolling_failure_rate
                  + 0.3 × clamp(avg_retry_count / 5, 0, 1)
                  + 0.2 × clamp(ms_since_last_success / 600000, 0, 1)
```

rolling_failure_rate = failed / total over last 100 attempts (sliding window).

Implementations MUST measure latency locally.
MUST NOT trust peer-reported latency from beacon packets.
battery_pct from peer beacons MUST contribute ≤5% to scoring.

Two separate channel selection functions:
- `bestForSend()` — includes SMS (origin node)
- `bestForRelay()` — excludes SMS (relay nodes)

### 4.4 Scoring Fixed Execution Time


The scoring function MUST execute in a minimum fixed time of 5ms
regardless of how many channels are evaluated:

```kotlin
suspend fun bestTransport(packet: MtrpPacket, isRelay: Boolean): Transport? {
    val start = System.nanoTime()
    val result = computeScore(packet, isRelay)
    val elapsedMs = (System.nanoTime() - start) / 1_000_000L
    if (elapsedMs < 5L) delay(5L - elapsedMs)
    return result
}
```

### 4.5 Transport Interface Contract

Every transport MUST expose:

```kotlin
interface Transport {
    val type: ChannelType
    val maxPayloadBytes: Int
    val relayAllowed: Boolean
    val status: StateFlow
    val peers: StateFlow>
    suspend fun start()
    suspend fun stop()
    suspend fun send(packet: MtrpPacket, to: Peer): Result
    val incoming: Flow
    suspend fun estimatedLatencyMs(): Long   // locally measured only
    fun signalStrength(): Int                // hardware-reported only
    fun isAvailable(): Boolean
}
```

---

## 5. Packet Format

### 5.1 Packet Structure

```
┌─────────────────────────────────────────────────────────────────────┐
│                        MTRP PACKET v0.1                             │
├─────────┬────────────┬─────┬──────────┬───────────┬────────────────┬──────────┬──────┤
│ version │ routing_id │ TTL │ chan_type │ relay_mac │    payload     │sender_sig│ pad  │
│ 1 byte  │  8 bytes   │ 1B  │  1 byte  │ 32 bytes  │   variable     │ 64 bytes │ var  │
└─────────┴────────────┴─────┴──────────┴───────────┴────────────────┴──────────┴──────┘
```

Fixed header: 43 bytes. Minimum total: 64 bytes (smallest bucket).

### 5.2 Field Definitions


**version** (1 byte)
Protocol version byte. Currently 0x01.
Implementations MUST drop unknown version bytes silently.

**routing_id** (8 bytes)
Per-session pseudonymous destination identifier (Section 3.4).
MUST NOT contain the real dest node_id.

**TTL** (1 byte, unsigned)
Set to 10 by origin. Each relay MUST decrement as unsigned.
MUST drop if TTL ≤ 0 — handles signed/unsigned edge case.

**chan_type** (1 byte)
0x01=WIFI 0x02=CELLULAR 0x03=WIFI_DIRECT
0x04=BLE  0x05=SMS      0x06=LORA  0x07=NOSTR

**relay_mac** (32 bytes)
HMAC-SHA256 over:
```
relay_mac_input = version || routing_id || TTL || chan_type
                || packet_created_at_ms || fragment_index || fragment_total
```
Relay nodes MUST verify using constant-time `libsodium crypto_verify_32`
before forwarding.
`packet_created_at_ms` closes replay window to 5 minutes.
Covers all relay-modifiable fields .
Invalid relay_mac MUST be dropped silently — no error response.

**payload** (variable)
XChaCha20-Poly1305 encrypted application data.
Padded to nearest bucket: 64 / 256 / 512 / 2048 bytes.
Relay nodes cannot decrypt this field.

**sender_sig** (64 bytes)
Ed25519 signature over SHA512(packet excluding sender_sig field).
Only destination verifies — relay nodes MUST NOT attempt verification.
Provides cryptographic proof of origin.

**pad** (variable)
Random bytes to pad to fixed bucket size.
MUST be random — MUST NOT be zero-filled.

### 5.3 Packet Timestamps


Every packet MUST include `packet_created_at_ms` in relay_mac input.
Relay nodes MUST reject packets where `packet_created_at_ms` is more
than 5 minutes before or after the relay node's current clock.
Handshake messages MUST include timestamps. Both parties MUST reject
handshake messages older than 30 seconds.

### 5.4 Fixed-Size Buckets


All packets MUST be padded to one of: 64, 256, 512, 2048 bytes.
Padding MUST be random bytes — MUST NOT be zero-filled.
Bucket = smallest bucket >= actual payload size.

For LoRa (50-byte max): fragment and pad each fragment to exactly
50 bytes. To prevent fragment count leakage, nodes
SHOULD add 0–2 random dummy fragments containing only random bytes.

### 5.5 Decrypted Payload Structure

```
┌───────────┬──────────────┬───────────┬──────────────────┐
│ origin_id │  created_at  │ msg_type  │    app_data      │
│ 8 bytes   │  8 bytes     │ 1 byte    │  variable        │
└───────────┴──────────────┴───────────┴──────────────────┘
```

msg_type values:
0x01=MESSAGE 0x02=RREQ 0x03=RREP 0x04=ACK
0x05=BEACON  0x06=REVOKE

Application MUST warn user if message created_at is >1 hour before
delivery.

### 5.6 Fragmentation


When payload > transport maxPayloadBytes:

```
fragment_aad  = msg_id || fragment_index || fragment_total || routing_id
fragment_data = slice of encrypted payload
```

Each fragment has its own relay_mac covering fragment_aad.
Fragments MUST be reassembled within 60 seconds.

Per-origin fragment buffer limit:
- Max 3 incomplete reassemblies per origin_id
- Max 20 total incomplete reassemblies across all origins

---

## 6. Routing Algorithm

### 6.1 Overview

- Proactive beaconing for neighbour discovery (30s interval)
- On-demand RREQ/RREP flooding for route discovery (AODV-inspired)
- 4-dimension adaptive scoring for best-path selection
- LRU deduplication cache (msg_id only)
- Multi-route table: 3 ranked routes per destination

### 6.2 Neighbour Discovery

Every node MUST broadcast a beacon every 30 seconds on all transports:

```json
{
  "node_id":    "3xK9mPqR7nVwL2YzQb4D",
  "version":    "0.1",
  "transports": ["BLE", "WIFI_DIRECT"],
  "platform":   "android",
  "battery_pct": 72,
  "timestamp":  1700000000000
}
```

Receiving nodes MUST:
- Record sender node_id, transport, locally-measured RSSI, timestamp
- MUST NOT trust beacon-reported latency for scoring
- Use battery_pct as advisory signal only (≤5% weight)
- Expire entries after 90 seconds (3 missed beacons)
- Rotate BLE advertisement MAC address every 15 minutes

New session handshakes SHOULD be delayed random(0ms, 500ms) to
prevent session establishment timing leakage .

### 6.3 Route Table Structure


Each node maintains route tables with up to 3 ranked entries per
destination and a separate neighbour table.

**Route entry:**
```
RouteEntry {
    dest_id:              String
    routing_id:           String        // current session pseudonym
    next_hop:             String        // immediate neighbour
    hop_count:            Int
    channel:              ChannelType
    avg_latency_ms:       Long          // locally measured
    rssi_to_next_hop:     Int           // hardware-reported
    success_count:        Long
    failure_count:        Long
    consecutive_failures: Int
    last_success_ms:      Long
    score:                Float
    rank:                 Int           // 1=primary 2=backup 3=backup
    full_path:            List
    path_channels:        List
    expires_ms:           Long          // 30s TTL, refreshed on ACK
    rrep_sig_verified:    Boolean       // MUST be true to use route
}
```

**Neighbour entry:**
```
NeighbourEntry {
    node_id:    String
    channel:    ChannelType
    rssi:       Int           // hardware-reported, not peer-reported
    last_beacon_ms: Long
    transports: List
    platform:   String
    battery_pct: Int?         // advisory only
    expires_ms: Long          // 90s
}
```

Route table hard cap: 500 entries.
When full, evict highest-score (worst) + oldest entries.

### 6.4 Route Discovery

**RREQ rate limiting:**
```
Max 5 RREQs per origin_id per minute
Global max 20 RREQs per minute across all origins
Excess: drop silently
Global limit exceeded: 10-second RREQ cooldown
```

**RREQ flood:**
1. Origin broadcasts RREQ: dest_id, origin_id, rreq_id, path_record[]
2. Each relay: check dedup cache, append self to path_record, rebroadcast
3. Destination: send RREP signed with destination's Ed25519 key

**RREP signing:**
```
rrep_content = dest_id || origin_id || path_record
             || total_latency_ms || timestamp
rrep_sig     = Ed25519_Sign(dest_private_key, SHA512(rrep_content))
```
Origin MUST verify rrep_sig before caching any route.
RREP without valid signature MUST be discarded.

**Hop validation:**
A node MUST NOT accept a RREP claiming hop_count=1 to a destination
unless that destination is in the local neighbour table.
Claims shorter than physically possible MUST be rejected.

**Sybil rate limiting:**
Max 50 new node_ids per minute per transport.
Unknown node_ids receive reliability_score penalty +0.3 until
they have ≥5 successful interactions.

### 6.5 Deduplication


Every node MUST maintain a seen-message LRU cache:
- Deduplication key: msg_id ONLY (not msg_id + chan_type)
- Any packet with a seen msg_id dropped regardless of other fields
- Capacity: 10,000 entries
- Entry TTL: 5 minutes
- relay_mac verification happens BEFORE dedup check

### 6.6 Route Failure and Recovery

**Consecutive failure tracking:**
```
consecutive_failures >= 3 → score_penalty += 0.5
consecutive_failures >= 5 → blacklist(next_hop, 5 minutes)
```

**ACK timeout failover (<50ms):**
1. ACK timeout (5s) → mark route stale
2. Promote rank=2 route → retry immediately
3. All routes exhausted → new RREQ flood
4. RREQ fails → store-and-forward queue

**Partition detection :**
Zero successful ACKs from any peer for >5 minutes →
enter PARTITION_SUSPECTED state → broadcast alert beacon all channels.

---

## 7. Encryption and Security

### 7.1 Cryptographic Algorithm Specification


MTRP MUST use ONLY these algorithms. No negotiation permitted.
No alternatives allowed. Any implementation using different algorithms
is non-conforming and MUST be rejected by conforming nodes.

```
Key generation:    Ed25519   (libsodium crypto_sign_keypair)
Key exchange:      X25519    (via Noise XX pattern)
Encryption:        XChaCha20-Poly1305  (libsodium)
MAC:               HMAC-SHA256         (libsodium)
Hash:              SHA-512             (libsodium)
KDF:               HKDF-SHA256         (libsodium)
Constant-time cmp: libsodium crypto_verify_32
Library:           libsodium EXCLUSIVELY
                   (kalium KMP wrapper on Android)
```

### 7.2 Noise XX Handshake with Post-Handshake Binding


**Noise XX pattern:**
```
→ e
← e, ee, s, es
→ s, se
```

All handshake messages MUST include timestamp.
Both parties MUST reject handshakes older than 30 seconds .

**Post-handshake identity binding:**
After Noise XX completes, both parties MUST verify:
```
assert SHA256(their_static_pubkey) == their_claimed_node_id_decoded
```
Failure → silent session termination.
Prevents MITM where attacker substitutes their own keys.

**Post-handshake KCI protection:**
Each party MUST send a confirmation:
```
confirmation = Ed25519_Sign(
    private_key,
    SHA512(noise_handshake_transcript || local_node_id || remote_node_id)
)
```
Both parties send and verify before session is ESTABLISHED.
Prevents Key Compromise Impersonation attacks.

### 7.3 Directional Key Derivation


After handshake, derive four keys from handshake output:

```
send_key      = HKDF(handshake_output, salt="mtrp-send-v1",    info=local||remote,  len=32)
recv_key      = HKDF(handshake_output, salt="mtrp-recv-v1",    info=remote||local,  len=32)
relay_mac_key = HKDF(handshake_output, salt="mtrp-relay-v1",   info=local||remote,  len=32)
routing_key   = HKDF(handshake_output, salt="mtrp-routing-v1", info=local||remote,  len=32)
```

Separate directional keys prevent reflection attacks.
relay_mac_key derivation is precisely defined — no implementation
ambiguity.
routing_key derives routing_id pseudonyms (Section 3.4).
Keys are transport-agnostic — session survives channel switches. Packet nonce includes session_id to prevent
cross-session nonce reuse.

### 7.4 Session State Machine


```
States:
  IDLE
  HANDSHAKE_WAIT_MSG2
  HANDSHAKE_WAIT_MSG3
  CONFIRMING
  ESTABLISHED
  RENEGOTIATING

Transitions:
  IDLE + recv msg1                       → HANDSHAKE_WAIT_MSG3
  IDLE + want to send                    → HANDSHAKE_WAIT_MSG2 (send msg1)
  HANDSHAKE_WAIT_MSG2 + recv msg2        → HANDSHAKE_WAIT_MSG3 (send msg3)
  HANDSHAKE_WAIT_MSG3 + recv msg3        → CONFIRMING (exchange confirmations)
  CONFIRMING + confirmations verified    → ESTABLISHED
  ESTABLISHED + 1000 msgs or 24h        → RENEGOTIATING
  RENEGOTIATING + tie-break win          → initiate new handshake
  RENEGOTIATING + tie-break lose         → respond to peer's handshake

Any message arriving in wrong state → silently drop + reset to IDLE
```

**Half-open session limit:**
Max 10 simultaneous half-open sessions.
New requests beyond limit MUST be silently dropped.
Half-open sessions expire after 10 seconds.

**Simultaneous renegotiation tie-break:**
```
if local_node_id < remote_node_id (lexicographic):
    I am initiator → proceed with my handshake
else:
    I am responder → discard mine, respond to peer's
```

### 7.5 Symmetric Ratchet with Forward Secrecy


**Ratchet initialisation:**
```
chain_key_0 = HKDF(send_key, salt="mtrp-chain-init", info=session_id, len=32)
```

**Per-message key derivation:**
```
message_key_n = HKDF(chain_key_{n-1}, salt="mtrp-msg-key",   len=32)
chain_key_n   = HKDF(chain_key_{n-1}, salt="mtrp-chain-key", len=32)
ZERO_MEMORY(chain_key_{n-1})     ← MUST delete immediately
ZERO_MEMORY(message_key_n)       ← MUST delete after encrypt/decrypt
```

Compromising chain_key_n does NOT reveal keys 0 to n-1 (forward secrecy).

**Nonce construction:**
```
nonce = random_12_bytes || session_counter_8_bytes (big-endian)
```
Hybrid nonce: counter ensures uniqueness even if random part collides.
Counter resets on session renegotiation.
Nonce is per-session (not per-channel) — survives channel switches.

**Encryption:**
```
ciphertext = XChaCha20_Poly1305_Encrypt(
    key   = message_key_n,
    nonce = random_12_bytes || counter_8_bytes,
    data  = plaintext_payload,
    aad   = version || routing_id || TTL || chan_type || created_at_ms
)
```

AAD covers all relay-visible header fields.
Any relay modification of these fields causes decryption failure.

**Out-of-order message handling:**
```
skipped_key_cache: Map<(session_id, counter), message_key>
max_skipped_keys:  100 per session
entry_ttl:         5 minutes
```

If >100 messages skipped, session MUST renegotiate.

**Ratchet crash recovery:**
Ratchet state is memory-only — MUST NOT persist to disk.
On session loss: restart handshake. Store-and-forward messages
queue during the gap and deliver after new session established.

**Known limitation:**
MTRP v0.1 symmetric ratchet provides partial forward secrecy only.
It does NOT provide break-in recovery. Full Double Ratchet planned
for MTRP v0.2.

### 7.6 Dual Integrity — relay_mac + sender_sig


**relay_mac — relay-verifiable:**
```
relay_mac = HMAC_SHA256(
    key  = relay_mac_key,         // precisely defined in Section 7.3
    data = version || routing_id || TTL || chan_type
         || packet_created_at_ms || fragment_index || fragment_total
)
```
Relay nodes MUST verify using `libsodium crypto_verify_32` before forwarding.

**sender_sig — destination-verifiable:**
```
sender_sig = Ed25519_Sign(
    private_key,
    SHA512(version || routing_id || TTL || chan_type || relay_mac || payload)
)
```
Only destination verifies against origin's public key.

### 7.7 Memory Security


All key material MUST be ByteArray — MUST NOT be String.

```kotlin
// Immediately after use:
messageKey.fill(0)
chainKey.fill(0)
ephemeralKey.fill(0)
```

JVM does not guarantee GC timing. Explicit zeroing is mandatory.

### 7.8 Payload Padding


Payloads MUST pad to fixed buckets: 64 / 256 / 512 / 2048 bytes.
Padding MUST be random — MUST NOT be zero-filled.

### 7.9 What Relay Nodes See

| Field         | Relay sees? | Notes                            |
|---------------|-------------|----------------------------------|
| version       | YES         | Protocol version byte            |
| routing_id    | YES         | Pseudonym — not real dest_id     |
| TTL           | YES         | For hop management               |
| chan_type      | YES         | Informational                    |
| relay_mac     | YES         | For integrity verification       |
| payload       | NO          | XChaCha20-Poly1305 encrypted     |
| origin_id     | NO          | Inside encrypted payload         |
| real dest_id  | NO          | Pseudonymised as routing_id      |
| app_data      | NO          | Inside encrypted payload         |
| sender_sig    | YES (bytes) | Cannot verify — no origin pubkey |
| full path     | NO          | Each relay sees only ±1 hops     |

---

## 8. Store and Forward

### 8.1 Queue Entry

```
QueueEntry {
    msg_id:        String
    routing_id:    String
    payload:       ByteArray   // encrypted, ready to send
    created_at_ms: Long
    retry_count:   Int
    next_retry_ms: Long
    expires_ms:    Long        // created_at_ms + 48 hours
    origin_id:     String      // for per-source rate limiting
}
```

**Database encryption:**
Queue MUST be stored in SQLCipher-encrypted database.
```
db_key = HKDF(biometric_or_pin_secret, salt="mtrp-db-key-v1", len=32)
```

**Per-source queue limit:**
Max 10 queued messages per origin_id. Prevents queue poisoning.

**Old message warning:**
Application MUST warn user when delivered message created_at is
more than 1 hour before delivery time.

### 8.2 Retry Schedule

```
delay = min(2^retry_count seconds, 3600)
Attempt 0: immediate
Attempt 1: 1s → Attempt 2: 2s → Attempt 3: 4s → ... → cap 1hr
```

### 8.3 Queue Limits

- Max total: 1000 messages
- Max per origin: 10 messages
- Expiry: 48 hours from created_at_ms
- Full queue: evict oldest first

---

## 9. Silent Relay

### 9.1 Relay Invisibility


A conforming relay MUST:
- NOT display any UI indication of relay activity
- NOT log relay events to persistent storage
- NOT write packet contents or metadata to any file, database, or log
- Process each packet in memory only — discard immediately after forward
- Show only the mandatory Android foreground service notification

**Minimum-visibility notification:**
```kotlin
NotificationCompat.Builder(context, CHANNEL_ID)
    .setSmallIcon(R.drawable.ic_dot)
    .setContentTitle("MTRP")
    .setContentText("")
    .setPriority(NotificationCompat.PRIORITY_MIN)
    .setVisibility(NotificationCompat.VISIBILITY_SECRET)
    .setOngoing(true)
    .build()
```

### 9.2 Relay Processing


```kotlin
fun onPacketReceived(raw: ByteArray) {
    val packet = PacketCodec.decodeHeader(raw)

    // Timestamp check (5-minute window)
    if (abs(currentMs() - packet.createdAtMs) > 300_000) return

    // Constant-time relay_mac verify
    if (!verifyRelayMac(packet)) return

    // Deduplication (msg_id only)
    if (seenMessages.contains(packet.msgId)) return
    seenMessages.add(packet.msgId)

    // TTL check (unsigned)
    if (packet.ttl.toUByte() <= 0u) return
    packet.ttl--

    // No broadcast relay
    if (packet.routingId.all { it == 0xFF.toByte() }) return

    // Per-source rate limit
    if (rateLimiter.isExceeded(packet.prevHopId)) return

    // Add jitter 
    delay(Random.nextLong(0, 50))

    // Forward via best relay-allowed channel
    channelManager.bestForRelay(packet)?.send(packet)

    // Zero packet from memory
    raw.fill(0)
}
```

**No broadcast relay:**
MUST NOT forward routing_id = 0xFF×8.

**Per-source relay rate limit:**
Max 60 relay packets per minute per previous-hop node_id.

### 9.3 Duty Cycling and Power


**Normal mode (battery >30%):**
```
Cycle: Wake(50ms) → Scan(200ms) → Process(10ms) → Forward(50ms) → Sleep(2690ms)
Active duty: ~10% of 3-second cycle
```

**Low battery (10–30%):**
Active duty: ~5% of 6-second cycle

**Critical (battery <10%):**
Relay DISABLED entirely via `setRequiresBatteryNotLow()`

**BLE MUST use passive scanning (SCAN_MODE_LOW_POWER, ~0.5–1.5mA):**
```kotlin
ScanSettings.Builder()
    .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
    .build()
```

**WiFi relay rule:**
MUST NOT activate WiFi solely for relay.
MAY use WiFi for relay only if device is already connected.

**WorkManager scheduling:**
```kotlin
PeriodicWorkRequestBuilder(3, TimeUnit.SECONDS)
    .setConstraints(
        Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .build()
    ).build()
```

**Partial wake lock:**
```kotlin
powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "mtrp:relay")
    .acquire(10 * 60 * 1000L)  // 10-minute auto-release
```

### 9.4 Relay Knowledge Boundaries

**Each relay node ONLY knows:**
- Immediate previous hop (who sent the packet to them)
- Immediate next hop (who they forward to)
- Packet size bucket (64/256/512/2048 bytes)
- Approximate receive time (with ≤50ms jitter added)

**Each relay node does NOT know:**
- Real origin node
- Real destination node
- Total number of hops
- Any other relay in the path
- Message content

---

## 10. Group Messaging


Group messaging uses the sealed sender pattern:

```
1. Generate random 256-bit group_message_key
2. Encrypt message once:
   ciphertext = XChaCha20_Poly1305(group_message_key, nonce, plaintext)
3. For each member, wrap key to their public key:
   key_for_A = libsodium_box(pubkey_A, group_message_key)
   key_for_B = libsodium_box(pubkey_B, group_message_key)
4. Send: ciphertext + all wrapped keys
5. Each recipient unwraps their copy, decrypts
```

Large groups (>20 members): pre-shared group_key via QR or NFC,
rotated every 24 hours.

---

## 11. Serialisation


All packet encoding MUST use Protocol Buffers (protobuf) exclusively.
JSON, XML, YAML, and any text-based formats are PROHIBITED for
packet encoding. Protobuf has no code evaluation path.

---

## 12. Conformance Requirements

### 12.1 Minimum Conforming Implementation (MUST)

- [ ] node_id — 22-char BASE58, Ed25519 (Section 3.1)
- [ ] Key revocation — REVOKE packet (Section 3.3)
- [ ] routing_id pseudonym — per-session (Section 3.4)
- [ ] MTRP packet format v0.1 (Section 5)
- [ ] Fixed-size buckets — 64/256/512/2048 bytes (Section 5.4)
- [ ] Noise XX + post-handshake identity binding (Section 7.2)
- [ ] KCI confirmation — Ed25519 transcript signature (Section 7.2)
- [ ] libsodium/kalium ONLY — no other crypto libraries (Section 7.1)
- [ ] XChaCha20-Poly1305 encryption (Section 7.5)
- [ ] Symmetric ratchet — per-message key derivation (Section 7.5)
- [ ] Memory zeroing — ByteArray.fill(0) after key use (Section 7.7)
- [ ] Directional key derivation — 4 keys (Section 7.3)
- [ ] relay_mac — exact HKDF derivation + constant-time verify (Section 7.6)
- [ ] sender_sig — Ed25519 over full packet (Section 7.6)
- [ ] AAD — version+routing_id+TTL+chan_type+created_at (Section 7.5)
- [ ] Dedup — msg_id only, 10k LRU, 5min TTL (Section 6.5)
- [ ] TTL unsigned decrement, drop if TTL ≤ 0 (Section 5.2)
- [ ] Timestamp window — 5min relay_mac, 30s handshake (Section 5.3)
- [ ] RREQ rate limiting (Section 6.4)
- [ ] RREP signature verification (Section 6.4)
- [ ] Hop count validation (Section 6.4)
- [ ] Black hole detection + blacklisting (Section 6.6)
- [ ] Sybil rate limiting (Section 6.4)
- [ ] SMS relay prohibited — relayAllowed=false (Section 4.1)
- [ ] Store-and-forward — SQLCipher encrypted DB (Section 8.1)
- [ ] Protobuf serialisation only (Section 11)
- [ ] Session state machine — 6 states (Section 7.4)
- [ ] Half-open session limit — max 10 (Section 7.4)
- [ ] Simultaneous renegotiation tie-break (Section 7.4)
- [ ] Skipped key cache — max 100, 5min TTL (Section 7.5)
- [ ] Max ratchet skip = 100, then renegotiate (Section 7.5)
- [ ] Silent relay — no UI, no logs (Section 9.1)
- [ ] Passive BLE scan — SCAN_MODE_LOW_POWER (Section 9.3)
- [ ] Duty cycling — max 10% active in normal mode (Section 9.3)
- [ ] No broadcast relay (Section 9.2)
- [ ] Per-source relay rate limit — 60/min (Section 9.2)
- [ ] Forward jitter — random 0–50ms (Section 9.2)
- [ ] BLE MAC rotation — every 15 minutes (Section 6.2)
- [ ] Scoring fixed time — minimum 5ms execution (Section 4.4)
- [ ] Fragment per-origin buffer limit — max 3 (Section 5.6)
- [ ] Route table hard cap — 500 entries (Section 6.3)
- [ ] Per-source queue limit — max 10 (Section 8.1)
- [ ] Randomised battery thresholds (Section 4.3)

### 12.2 Full Conforming Implementation (SHOULD)

- [ ] All 8 transport channels (Section 4.1)
- [ ] 4-dimension adaptive scoring (Section 4.3)
- [ ] Multi-route table — 3 ranked routes per destination (Section 6.3)
- [ ] LoRa dummy fragment padding (Section 5.4)
- [ ] Group messaging — sealed sender (Section 10)
- [ ] Partition detection (Section 6.6)
- [ ] Session handshake delay jitter (Section 6.2)

### 12.3 Planned for v0.2

- Full Double Ratchet with break-in recovery
- Onion routing full anonymisation
- Cover traffic and timing noise
- BLE mesh via Bluetooth Mesh standard

---

## Appendix A — Packet Field Reference

| Field       | Size     | Type      | Relay sees? | Notes                  |
|-------------|----------|-----------|-------------|------------------------|
| version     | 1 byte   | UByte     | YES         | 0x01 currently         |
| routing_id  | 8 bytes  | ByteArray | YES         | Pseudonym, not node_id |
| TTL         | 1 byte   | UByte     | YES         | Unsigned               |
| chan_type   | 1 byte   | UByte     | YES         | See Section 4.1        |
| relay_mac   | 32 bytes | ByteArray | YES         | HMAC-SHA256            |
| payload     | variable | ByteArray | NO          | XChaCha20-Poly1305     |
| sender_sig  | 64 bytes | ByteArray | YES (raw)   | Ed25519, dest-verified |
| pad         | variable | ByteArray | YES (size)  | Random, bucket-padded  |

---

## Appendix B — Error Codes

| Code | Name                   | Description                              |
|------|------------------------|------------------------------------------|
| E001 | INVALID_RELAY_MAC      | relay_mac verification failed            |
| E002 | TTL_EXPIRED            | TTL ≤ 0 after decrement                  |
| E003 | DUPLICATE_PACKET       | msg_id in dedup cache                    |
| E004 | NO_ROUTE               | No viable path found                     |
| E005 | QUEUE_FULL             | Store-and-forward at capacity            |
| E006 | PAYLOAD_TOO_LARGE      | After fragmentation still exceeds limit  |
| E007 | HANDSHAKE_FAILED       | Noise XX or confirmation failed          |
| E008 | MSG_EXPIRED            | Message exceeded 48-hour TTL            |
| E009 | FRAGMENT_TIMEOUT       | Reassembly timed out (60s)               |
| E010 | UNKNOWN_CHAN_TYPE      | Unrecognised chan_type byte              |
| E011 | IDENTITY_BIND_FAILED   | Post-handshake pubkey→node_id mismatch   |
| E012 | RREP_SIG_INVALID       | RREP signature verification failed       |
| E013 | NODE_REVOKED           | Source node_id on revocation list        |
| E014 | INVALID_SENDER_SIG     | sender_sig verification failed           |
| E015 | TIMESTAMP_REJECTED     | Packet timestamp outside 5-minute window |
| E016 | RATELIMIT_RREQ         | RREQ rate limit exceeded                 |
| E017 | RATELIMIT_RELAY        | Per-source relay rate limit exceeded     |
| E018 | SESSION_STATE_ERROR    | Message arrived in wrong session state   |
| E019 | RATCHET_SKIP_OVERFLOW  | >100 messages skipped — renegotiate      |
| E020 | ALGORITHM_REJECTED     | Non-conforming algorithm detected        |

---

## Appendix D — Changelog

| Version | Date | Author      | Changes       |
|---------|------|-------------|---------------|
| 0.1     | 2025 | K. Bhanutej | Initial draft |

---

*MTRP v0.1 — Multi Transport Relay Protocol*
*Author: K. Bhanutej*
*This document is open. Anyone may implement a conforming MTRP node.*
