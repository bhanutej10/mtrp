package dev.mtrp.core.routing

import dev.mtrp.core.ChannelType
import dev.mtrp.core.MTRP
import dev.mtrp.core.packet.currentTimeMs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * MTRP-SPEC-v0.1 Section 4.3 — Adaptive 4-Dimension Channel Scoring
 *
 * Scores available channels. Lower score = better = chosen first.
 * Weights shift dynamically based on battery level.
 * Battery thresholds are randomised to prevent timing inference.
 * Minimum execution time of 5ms enforced to prevent scoring timing analysis.
 *
 * score = W_speed × speed_score
 *       + W_power × power_score
 *       + W_reach × reach_score
 *       + W_reliable × reliability_score
 *
 * Author: K. Bhanutej
 */
data class ChannelContext(
    val channel:          ChannelType,
    val latencyMs:        Long,       // locally measured
    val failureRate:      Float,      // rolling rate from RouteTable
    val avgRetryCount:    Float,
    val msSinceSuccess:   Long,
    val peerCount:        Int,
    val hopCount:         Int
)

class ChannelScorer {

    // Randomised battery thresholds — prevent timing oracle
    private val thresholdLow  = 20 + Random.nextInt(-3, 4)
    private val thresholdCrit = 5  + Random.nextInt(-1, 2)

    /**
     * Score a channel. Lower is better.
     * SPEC 4.4: minimum execution time of 5ms enforced by MeshRouter before returning.
     */
    fun score(
        ctx:        ChannelContext,
        batteryPct: Int
    ): Float {
        val (wSpeed, wPower, wReach, wReliable) = weights(batteryPct, ctx)
        val speedScore    = speedScore(ctx)
        val powerScore    = powerScore(ctx.channel, batteryPct)
        val reachScore    = reachScore(ctx)
        val reliableScore = reliabilityScore(ctx)
        return wSpeed * speedScore + wPower * powerScore +
               wReach * reachScore + wReliable * reliableScore
    }

    /**
     * Score all available channels and return them sorted best first.
     * Channels with relayAllowed=false are excluded when isRelay=true.
     */
    fun rank(
        candidates: List<ChannelContext>,
        batteryPct: Int,
        isRelay:    Boolean
    ): List<ChannelContext> {
        return candidates
            .filter { !isRelay || it.channel.relayAllowed }
            .sortedBy { score(it, batteryPct) }
    }

    // ── Weight derivation ─────────────────────────────────────────

    private data class Weights(
        val speed: Float, val power: Float,
        val reach: Float, val reliable: Float
    )

    private fun weights(batteryPct: Int, ctx: ChannelContext): Weights {
        var wSpeed = when {
            batteryPct > 50              -> 0.40f
            batteryPct > thresholdLow    -> 0.25f
            batteryPct > thresholdCrit   -> 0.15f
            else                         -> 0.10f
        }
        var wPower = when {
            batteryPct > 50              -> 0.20f
            batteryPct > thresholdLow    -> 0.35f
            batteryPct > thresholdCrit   -> 0.50f
            else                         -> 0.60f
        }
        var wReach    = 0.20f
        var wReliable = 0.20f

        // Context adjustments
        if (ctx.peerCount == 0) { wReach    += 0.15f }
        if (ctx.avgRetryCount > 3f) { wReliable += 0.25f }

        // Normalise to sum 1.0
        val total = wSpeed + wPower + wReach + wReliable
        return Weights(wSpeed/total, wPower/total, wReach/total, wReliable/total)
    }

    // ── Dimension scores ──────────────────────────────────────────

    private fun speedScore(ctx: ChannelContext): Float {
        val latencyNorm = clamp(ctx.latencyMs / 5000f, 0f, 1f)
        val bwNorm      = 1f - ctx.channel.bandwidthClass / 4f
        return 0.6f * latencyNorm + 0.4f * bwNorm
    }

    private fun powerScore(channel: ChannelType, batteryPct: Int): Float {
        val urgency = when {
            batteryPct > 50            -> 1.0f
            batteryPct > thresholdLow  -> 1.5f
            batteryPct > thresholdCrit -> 3.0f
            else                       -> 6.0f
        }
        return clamp(channel.powerIndex * urgency, 0f, 1f)
    }

    private fun reachScore(ctx: ChannelContext): Float {
        val hopNorm  = clamp(ctx.hopCount / 10f, 0f, 1f)
        val peerNorm = 1f - clamp(ctx.peerCount / 10f, 0f, 1f)
        val offline  = 1f - ctx.channel.offlineReachBonus
        return 0.5f * hopNorm + 0.3f * peerNorm + 0.2f * offline
    }

    private fun reliabilityScore(ctx: ChannelContext): Float {
        val retryNorm   = clamp(ctx.avgRetryCount / 5f, 0f, 1f)
        val recencyNorm = clamp(ctx.msSinceSuccess / 600_000f, 0f, 1f)
        return 0.5f * ctx.failureRate + 0.3f * retryNorm + 0.2f * recencyNorm
    }

    private fun clamp(v: Float, lo: Float, hi: Float) = max(lo, min(hi, v))
}
