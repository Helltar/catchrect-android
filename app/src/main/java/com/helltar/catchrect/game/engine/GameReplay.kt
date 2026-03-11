package com.helltar.catchrect.game.engine

data class ReplayInput(val tick: Int, val platformX: Float)

data class GameReplay(
    val seed: Long,
    val viewportWidth: Int,
    val viewportHeight: Int,
    val safeTopInset: Int,
    val safeBottomInset: Int,
    val inputs: List<ReplayInput>,
    val totalTicks: Int,
    val score: Int
)
