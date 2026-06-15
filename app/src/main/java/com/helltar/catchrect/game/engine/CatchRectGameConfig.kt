package com.helltar.catchrect.game.engine

data class CatchRectGameConfig(
    val platformWidthFactor: Float = 120f / 360f,
    val platformHeightFactor: Float = 22f / 360f,
    val platformBottomMarginFactor: Float = 32f / 360f,
    val platformWidthShrinkPerStepPx: Float = 3f,
    val scorePerShrinkStep: Int = 15,
    val minPlatformWidthFactor: Float = 0.7f,
    val minSpawnDelaySeconds: Float = 0.25f,
    val baseSpawnDelaySeconds: Float = 1.0f,
    val spawnDelayDecreasePerStep: Float = 0.06f,
    val scorePerSpawnStep: Int = 15,
    val minCubeSizePx: Int = 26,
    val maxCubeSizePxExclusive: Int = 42,
    val baseCubeSpeedPxPerSecond: Float = 350f,
    val cubeSpeedPerStep: Float = 32f,
    val scorePerSpeedStep: Int = 15,
    val comboTierTwoStreak: Int = 5,
    val comboTierThreeStreak: Int = 10,
    val comboTierFiveStreak: Int = 20,
    val slowMotionDurationSeconds: Float = 5f,
    val slowMotionSpeedFactor: Float = 0.45f,
    val platformSlowDurationSeconds: Float = 4f,
    val platformSlowSpeedFactor: Float = 0.55f
) {
    fun basePlatformWidthPx(viewportWidth: Int): Float = clampWidth(viewportWidth) * platformWidthFactor

    fun platformHeightPx(viewportWidth: Int): Float = clampWidth(viewportWidth) * platformHeightFactor

    fun platformBottomMarginPx(viewportWidth: Int): Float = clampWidth(viewportWidth) * platformBottomMarginFactor

    private fun clampWidth(viewportWidth: Int): Int = viewportWidth.coerceAtMost(MAX_REFERENCE_WIDTH)

    companion object {
        const val FIXED_DT: Float = 1f / 120f
        private const val MAX_REFERENCE_WIDTH = 1200
    }
}
