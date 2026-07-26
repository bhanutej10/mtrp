package dev.mtrp.core.transport.internet

import dev.mtrp.core.ChannelType

/**
 * MTRP-SPEC-v0.1 Section 4.1 — Cellular transport (priority 2)
 *
 * Identical to WifiTransport in implementation — both use WebSocket
 * over the internet. The channel type differs so the scoring formula
 * can apply different powerIndex and bandwidthClass values.
 *
 * Author: K. Bhanutej
 */
class CellularTransport(
    serverUrl:    String,
    localNodeId:  String
) : WifiTransport(
    serverUrl    = serverUrl,
    localNodeId  = localNodeId,
    type         = ChannelType.CELLULAR
)
