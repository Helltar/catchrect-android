package com.helltar.catchrect.network

import com.helltar.catchrect.game.engine.GameReplay
import com.helltar.catchrect.game.engine.ReplayInput
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class SubmitScoreRequest(
    val playerName: String,
    val playerId: String,
    val seed: Long,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val safeTopInset: Int,
    val safeBottomInset: Int,
    val inputs: List<ReplayInputPayload>,
    val totalTicks: Int,
    val score: Int
)

@Serializable
data class ReplayInputPayload(
    @SerialName("t") val tick: Int,
    @SerialName("x") val platformX: Int
)

@Serializable
data class LeaderboardEntry(
    val playerName: String,
    val score: Int,
    val isOwnedByPlayer: Boolean
)

fun GameReplay.toSubmitScoreRequest(playerName: String, playerId: String): SubmitScoreRequest =
    SubmitScoreRequest(
        playerName = playerName,
        playerId = playerId,
        seed = seed,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        safeTopInset = safeTopInset,
        safeBottomInset = safeBottomInset,
        inputs = inputs.map(ReplayInput::toPayload),
        totalTicks = totalTicks,
        score = score
    )

fun ReplayInput.toPayload(): ReplayInputPayload =
    ReplayInputPayload(
        tick = tick,
        platformX = platformX.toInt()
    )
