# MTRP — Multi Transport Relay Protocol

**Author:** K. Bhanutej
**Repository:** [github.com/bhanutej10/mtrp](https://github.com/bhanutej10/mtrp)

---

## What is MTRP?

MTRP is an open mesh messaging protocol designed for resilient communication when standard internet infrastructure is unavailable. It defines how devices discover each other, route messages through intermediate relay nodes, fall back across transport channels, and maintain end-to-end encryption regardless of which channel carries the packet.

The protocol is transport-agnostic. A single message may travel over WiFi for one hop, Bluetooth for the next, and SMS for the last, without the application or the user needing to know. The SDK handles all of this automatically.

This repository contains the MTRP protocol specification and its Kotlin Multiplatform reference implementation. Anyone can read the specification and build a conforming MTRP node in any language.

---

## How a Message Travels

When a message is sent, MTRP selects the best available channel based on a four-dimension adaptive scoring formula. If that channel fails or has no path to the destination, it falls back to the next available channel. Intermediate devices relay the encrypted packet without any action from their owners.

A typical multi-hop path looks like this:

```
Sender → WiFi → Node A → Bluetooth LE → Node B → SMS → Receiver
```

Node A and Node B are relay nodes. Their owners are unaware of relay activity. The relay service runs as a background process, verifies each packet's integrity, decrements the TTL, and forwards it. The payload is encrypted and unreadable to relay nodes.

---

## Transport Channel Stack

MTRP attempts channels in the following priority order. Lower number means higher priority.

| Priority | Channel | Max Payload | Internet Required | Relay Allowed |
|---|---|---|---|---|
| 1 | WiFi | 65536 bytes | Yes | Yes |
| 2 | Mobile Data | 65536 bytes | Yes | Yes |
| 3 | WiFi Direct | 65536 bytes | No | Yes |
| 4 | Bluetooth LE | 512 bytes | No | Yes |
| 5 | SMS | 140 bytes | No | Origin node only |
| 6 | LoRa | 50 bytes | No | Yes |
| 7 | Nostr Relay | 65536 bytes | Yes | Yes |
| 8 | Store and Forward | 65536 bytes | No | Yes |

SMS uses the 2G signalling channel rather than the data channel. A device with no data connection but a faint 2G signal can still send via SMS. Relay nodes are not permitted to use SMS as that would consume the relay owner's SMS quota without consent. Only the origin node of a message may use SMS.

---

## Security Design

The security architecture was defined before any implementation work began. A full threat model identified 48 attack vectors across identity, handshake, encryption, routing, relay, storage, and timing layers. All 48 are addressed in the protocol specification.

```
Identity:           Ed25519 keypair, 22-character node ID with approximately 128 bits of entropy
Handshake:          Noise XX pattern with post-handshake identity binding and KCI protection
Encryption:         XChaCha20-Poly1305 via libsodium
Forward secrecy:    Symmetric ratchet, fresh key per message, previous key deleted after use
Relay integrity:    HMAC-SHA256 relay_mac, verified by relay nodes before forwarding
Sender proof:       Ed25519 sender_sig, verified only by the destination
Privacy:            Per-session routing pseudonym, relay nodes never see the real destination
Crypto library:     libsodium exclusively, no alternatives permitted
```

There is no algorithm negotiation in MTRP. XChaCha20-Poly1305 is the only permitted encryption algorithm. A node that attempts to negotiate a different algorithm is non-conforming and will be rejected. This eliminates the entire class of downgrade attacks that have historically affected negotiation-based protocols such as TLS.

XChaCha20-Poly1305 was chosen over AES-256-GCM because it is significantly faster in software on low-end Android devices without hardware AES acceleration. These are precisely the devices most likely to be used in offline mesh scenarios.

---

## Adaptive Channel Scoring

Channel selection uses a four-dimension scoring formula. The channel with the lowest total score is selected.

```
score = W_speed × speed_score + W_power × power_score
      + W_reach × reach_score + W_reliable × reliability_score
```

The weights shift dynamically based on context. When battery is above 50%, the formula weights speed at 40%. When battery drops below 20%, the power weight rises to 50% and speed drops to 15%, causing the system to favour Bluetooth and LoRa over WiFi automatically. Battery thresholds are randomised by a small margin to prevent timing-based inference of battery state by an observer.

The reliability dimension is adaptive. It tracks per-channel failure rates over a rolling window of 100 send attempts. Channels that have been failing are scored worse and deprioritised without any manual configuration.

---

## Silent Relay

The relay service runs as an Android foreground service. Android 8 and above require a visible notification for foreground services, so a notification exists, but it is configured with minimum visibility: empty text, smallest icon, PRIORITY_MIN, hidden on the lock screen.

The relay service:

- Does not log relay events to any persistent storage
- Does not expose relay activity in the application UI
- Processes each packet in memory and discards it immediately after forwarding
- Cannot decrypt the packets it relays
- Uses passive Bluetooth scanning at SCAN_MODE_LOW_POWER, approximately 0.5 to 1.5 milliamps
- Operates on a 3-second duty cycle with approximately 10% active time
- Disables automatically when battery is critically low via setRequiresBatteryNotLow
- Does not activate WiFi solely for relay purposes

---

## Platform Support

| Platform | Status | Available Channels |
|---|---|---|
| Android | Reference implementation | All 8 channels |
| Linux desktop | Planned after Phase 5 | WiFi, Nostr, LoRa via USB, Store and Forward |
| Windows desktop | Planned after Phase 5 | WiFi, Nostr, LoRa via USB, Store and Forward |

A desktop node acts as a fixed relay. A device that is always on and connected to power, with optional LoRa USB module attached, extends the mesh range considerably for nearby mobile nodes.

---

## Protocol Specification

The full specification is at [`spec/MTRP-SPEC-v0.1.md`](spec/MTRP-SPEC-v0.1.md). It defines:

- Exact byte layout of every packet field
- Routing algorithm (AODV-hybrid with multi-metric scoring)
- Complete encryption stack with precise key derivation steps
- Channel fallback rules
- What relay nodes are and are not permitted to see
- Store-and-forward queue behaviour and retry scheduling
- Group messaging via sealed sender pattern
- Conformance requirements for independent implementations

The SDK implements the specification. Where any conflict exists between implementation and specification, the specification takes precedence.

---

## Technology Stack

| Layer | Technology |
|---|---|
| Language | Kotlin Multiplatform |
| Android UI | Jetpack Compose |
| Desktop UI | Compose Desktop, planned Phase 5 |
| Networking | Ktor, WebSocket and HTTP |
| Database | SQLDelight with SQLCipher |
| Cryptography | libsodium via kalium KMP wrapper |
| Wire format | Protocol Buffers |
| Dependency injection | Koin |
| Relay server | Python FastAPI with WebSocket |

---

## Repository Structure

```
mtrp/
├── spec/
│   └── MTRP-SPEC-v0.1.md          protocol specification
├── sdk/
│   ├── core/                       shared KMP logic, routing, crypto, channel manager
│   ├── transport-ble/              Bluetooth LE transport, Android
│   ├── transport-internet/         WiFi, cellular data, Nostr relay
│   ├── transport-sms/              SMS transport, Android
│   ├── transport-wifidirect/       WiFi Direct P2P, Android
│   └── transport-lora/             LoRa via USB serial module
├── app/
│   ├── androidMain/                Android demo application
│   └── desktopMain/                desktop relay node, Phase 5 onwards
└── server/
    └── mtrp_relay/                 Python FastAPI relay server
```

---

## Build

```bash
./gradlew build
./gradlew :sdk:core:testDebugUnitTest
```

---

## Phase Progress

| Phase | Description | Status |
|---|---|---|
| 0 | Project scaffold, KMP module structure, build passing, smoke tests | Complete |
| 1 | Protocol specification, MTRP-SPEC-v0.1, full security design | Complete |
| 2 | Packet format and codec, protobuf schema, PacketCodec, FragmentAssembler | Complete |
| 3 | Crypto engine, libsodium integration, Noise XX handshake, symmetric ratchet | Complete |
| 4 | Routing engine, RouteTable, MeshRouter, Deduplicator | Complete |
| 5 | Internet transport, Ktor WebSocket, Nostr relay | Complete |
| 6 | BLE transport, Android GATT server and client | Complete |
| 7 | SMS transport, SmsManager and BroadcastReceiver | Complete |
| 8 | Store and forward queue, SQLCipher, retry scheduler | Complete |
| 9 | Channel manager and public SDK API | |
| 10 | WiFi Direct transport | |
| 11 | LoRa gateway, Python and meshtastic-python | |
| 12 | Publishing open specification and SDK on Maven Central | |

---

## Author

K. Bhanutej
[@bhanutej10](https://github.com/bhanutej10)

---

MTRP is an open protocol. The specification is public. Anyone may implement a conforming MTRP node in any language.

