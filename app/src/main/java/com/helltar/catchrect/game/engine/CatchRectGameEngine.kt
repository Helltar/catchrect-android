package com.helltar.catchrect.game.engine

import com.helltar.catchrect.game.model.CubeType
import com.helltar.catchrect.game.model.FallingCube
import java.security.SecureRandom
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

class CatchRectGameEngine(private val config: CatchRectGameConfig, initialSeed: Long = SecureRandom().nextLong()) {

    private val fallingCubes = mutableListOf<FallingCube>()
    private val caughtCubeEvents = mutableListOf<CaughtCubeEvent>()

    private val replayInputs = mutableListOf<ReplayInput>()
    private var tickCount = 0
    private var lastRecordedX = Float.NaN

    var score: Int = 0
        private set
    var lives: Int = 3
        private set

    var combo: Int = 0
        private set

    var bestCombo: Int = 0
        private set

    var caughtWhiteCount: Int = 0
        private set

    var blockedHitCount: Int = 0
        private set

    var powerUpsUsed: Int = 0
        private set

    var livesGainedCount: Int = 0
        private set

    private var slowMotionTicksRemaining: Int = 0
    private var platformSlowTicksRemaining: Int = 0
    private var shieldTicksRemaining: Int = 0
    private var controlInvertTicksRemaining: Int = 0

    val comboMultiplier: Int
        get() = when {
            combo >= config.comboTierFiveStreak -> 5
            combo >= config.comboTierThreeStreak -> 3
            combo >= config.comboTierTwoStreak -> 2
            else -> 1
        }

    val isShieldActive: Boolean
        get() = shieldTicksRemaining > 0

    val shieldSecondsRemaining: Float
        get() = shieldTicksRemaining * CatchRectGameConfig.FIXED_DT

    val isSlowMotionActive: Boolean
        get() = slowMotionTicksRemaining > 0

    val slowMotionSecondsRemaining: Float
        get() = slowMotionTicksRemaining * CatchRectGameConfig.FIXED_DT

    val isPlatformSlowActive: Boolean
        get() = platformSlowTicksRemaining > 0

    val platformSlowSecondsRemaining: Float
        get() = platformSlowTicksRemaining * CatchRectGameConfig.FIXED_DT

    val isControlInvertActive: Boolean
        get() = controlInvertTicksRemaining > 0

    val controlInvertSecondsRemaining: Float
        get() = controlInvertTicksRemaining * CatchRectGameConfig.FIXED_DT

    val platformMovementFactor: Float
        get() = if (isPlatformSlowActive) config.platformSlowSpeedFactor else 1f

    val survivalSeconds: Float
        get() = tickCount * CatchRectGameConfig.FIXED_DT

    @Volatile
    var isGameOver: Boolean = false
        private set

    var viewportWidth: Int = 0
        private set
    var viewportHeight: Int = 0
        private set
    var safeTopInset: Int = 0
        private set
    var safeBottomInset: Int = 0
        private set

    var platformX: Float = 0f
        private set

    var platformLeft: Float = 0f; private set
    var platformTop: Float = 0f; private set
    var platformRight: Float = 0f; private set
    var platformBottom: Float = 0f; private set

    private var spawnAccumulatorSeconds = 0f
    private var random = Random(initialSeed)

    var seed: Long = initialSeed
        private set

    val cubes: List<FallingCube>
        get() = fallingCubes

    fun updateViewport(width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        clampPlatform()
        if (platformX == 0f && width > 0) {
            centerPlatform()
        }
    }

    fun updateSafeInsets(top: Int, bottom: Int) {
        safeTopInset = top
        safeBottomInset = bottom
    }

    fun centerPlatform() {
        if (viewportWidth <= 0) return
        platformX = ((viewportWidth - currentPlatformWidthPx()) / 2f).coerceAtLeast(0f)
    }

    fun movePlatformByCenter(centerX: Float) {
        val width = currentPlatformWidthPx()
        val maxX = (viewportWidth - width).coerceAtLeast(0f)
        platformX = (centerX - width / 2f).coerceIn(0f, maxX)
    }

    fun movePlatformBy(deltaX: Float) {
        val maxX = (viewportWidth - currentPlatformWidthPx()).coerceAtLeast(0f)
        platformX = (platformX + deltaX).coerceIn(0f, maxX)
    }

    fun movePlatformTowardCenter(centerX: Float, maxDeltaX: Float): Boolean {
        val width = currentPlatformWidthPx()
        val maxX = (viewportWidth - width).coerceAtLeast(0f)
        val targetX = (centerX - width / 2f).coerceIn(0f, maxX)
        val delta = targetX - platformX
        val step = maxDeltaX.coerceAtLeast(0f)
        if (kotlin.math.abs(delta) <= step) {
            platformX = targetX
            return true
        }
        platformX = (platformX + delta.coerceIn(-step, step)).coerceIn(0f, maxX)
        return false
    }

    fun update() {
        if (viewportWidth <= 0 || viewportHeight <= 0 || isGameOver) return

        val dt = CatchRectGameConfig.FIXED_DT
        val roundedX = platformX.toInt().toFloat()
        if (roundedX != lastRecordedX) {
            replayInputs += ReplayInput(tickCount, roundedX)
            lastRecordedX = roundedX
        }
        tickCount++
        clampPlatform()

        val motionScale = if (isSlowMotionActive) config.slowMotionSpeedFactor else 1f
        if (slowMotionTicksRemaining > 0) {
            slowMotionTicksRemaining--
        }
        if (platformSlowTicksRemaining > 0) {
            platformSlowTicksRemaining--
        }
        if (shieldTicksRemaining > 0) {
            shieldTicksRemaining--
        }
        if (controlInvertTicksRemaining > 0) {
            controlInvertTicksRemaining--
        }

        val spawnDelaySeconds = max(
            config.minSpawnDelaySeconds,
            config.baseSpawnDelaySeconds - (score / config.scorePerSpawnStep) * config.spawnDelayDecreasePerStep
        )

        spawnAccumulatorSeconds += dt
        while (spawnAccumulatorSeconds >= spawnDelaySeconds) {
            spawnCube()
            spawnAccumulatorSeconds -= spawnDelaySeconds
        }

        updatePlatformRect()

        val pLeft = platformLeft
        val pTop = platformTop
        val pRight = platformRight
        val pBottom = platformBottom

        var writeIdx = 0
        for (readIdx in fallingCubes.indices) {
            val cube = fallingCubes[readIdx]
            cube.y += cube.speed * dt * motionScale

            val cubeRight = cube.x + cube.size
            val cubeBottom = cube.y + cube.size

            val isCaught = cubeRight >= pLeft &&
                    cube.x <= pRight &&
                    cubeBottom >= pTop &&
                    cube.y <= pBottom

            if (isCaught) {
                caughtCubeEvents += CaughtCubeEvent(
                    type = cube.type,
                    x = cube.x + cube.size / 2f,
                    y = pTop
                )
                when (cube.type) {
                    CubeType.WHITE -> {
                        combo += 1
                        bestCombo = max(bestCombo, combo)
                        caughtWhiteCount += 1
                        score += comboMultiplier
                    }

                    CubeType.RED, CubeType.RED_FAST -> {
                        combo = 0
                        if (isShieldActive) {
                            blockedHitCount += 1
                        } else {
                            lives -= 1
                        }
                    }

                    CubeType.GREEN -> {
                        lives += 1
                        livesGainedCount += 1
                    }
                    CubeType.SHIELD -> {
                        shieldTicksRemaining = shieldDurationTicks()
                        powerUpsUsed += 1
                    }

                    CubeType.SLOW_MOTION -> {
                        slowMotionTicksRemaining = slowMotionDurationTicks()
                        powerUpsUsed += 1
                    }

                    CubeType.PLATFORM_SLOW -> platformSlowTicksRemaining = platformSlowDurationTicks()
                    CubeType.INVERT_CONTROL -> controlInvertTicksRemaining = controlInvertDurationTicks()
                }
                continue
            }

            if (cube.y > viewportHeight + cube.size) {
                if (cube.type == CubeType.WHITE) {
                    combo = 0
                }
                continue
            }

            if (writeIdx != readIdx) {
                fallingCubes[writeIdx] = fallingCubes[readIdx]
            }
            writeIdx++
        }

        if (writeIdx < fallingCubes.size) {
            fallingCubes.subList(writeIdx, fallingCubes.size).clear()
        }

        if (lives <= 0) {
            isGameOver = true
        }
    }

    fun drainCaughtCubeEvents(action: (type: CubeType, x: Float, y: Float) -> Unit) {
        if (caughtCubeEvents.isEmpty()) return
        for (i in caughtCubeEvents.indices) {
            val event = caughtCubeEvents[i]
            action(event.type, event.x, event.y)
        }
        caughtCubeEvents.clear()
    }

    fun getReplayData(): GameReplay = GameReplay(
        seed = seed,
        viewportWidth = viewportWidth,
        viewportHeight = viewportHeight,
        safeTopInset = safeTopInset,
        safeBottomInset = safeBottomInset,
        inputs = replayInputs.toList(),
        totalTicks = tickCount,
        score = score,
        bestCombo = bestCombo,
        caughtWhiteCount = caughtWhiteCount,
        blockedHitCount = blockedHitCount,
        powerUpsUsed = powerUpsUsed
    )

    fun restart() {
        fallingCubes.clear()
        caughtCubeEvents.clear()
        replayInputs.clear()
        tickCount = 0
        lastRecordedX = Float.NaN
        score = 0
        lives = 3
        combo = 0
        bestCombo = 0
        caughtWhiteCount = 0
        blockedHitCount = 0
        powerUpsUsed = 0
        livesGainedCount = 0
        slowMotionTicksRemaining = 0
        platformSlowTicksRemaining = 0
        shieldTicksRemaining = 0
        controlInvertTicksRemaining = 0
        isGameOver = false
        spawnAccumulatorSeconds = 0f
        reseed()
        centerPlatform()
    }

    private fun updatePlatformRect() {
        val platformWidth = currentPlatformWidthPx()
        val platformHeight = config.platformHeightPx(viewportWidth)
        val platformY =
            viewportHeight - safeBottomInset - platformHeight - config.platformBottomMarginPx(viewportWidth)
        platformLeft = platformX
        platformTop = platformY
        platformRight = platformX + platformWidth
        platformBottom = platformY + platformHeight
    }

    private fun spawnCube() {
        val redBonus = ((score / 50) * 0.01f).coerceAtMost(0.10f)
        val whiteChance = 0.69f - redBonus
        val redChance = 0.12f + redBonus * 0.6f
        val fastRedChance = 0.07f + redBonus * 0.4f
        val greenChance = 0.03f
        val shieldChance = 0.025f
        val chance = random.nextFloat()
        val type = when {
            chance < whiteChance -> CubeType.WHITE
            chance < whiteChance + redChance -> CubeType.RED
            chance < whiteChance + redChance + fastRedChance -> CubeType.RED_FAST
            chance < whiteChance + redChance + fastRedChance + greenChance -> CubeType.GREEN
            chance < whiteChance + redChance + fastRedChance + greenChance + shieldChance -> CubeType.SHIELD
            chance < whiteChance + redChance + fastRedChance + greenChance + shieldChance + 0.025f -> CubeType.SLOW_MOTION
            chance < whiteChance + redChance + fastRedChance + greenChance + shieldChance + 0.025f + 0.02f -> CubeType.PLATFORM_SLOW
            else -> CubeType.INVERT_CONTROL
        }

        val size = if (type == CubeType.RED_FAST) {
            random.nextInt(config.minCubeSizePx - 8, config.minCubeSizePx).coerceAtLeast(14)
                .toFloat()
        } else {
            random.nextInt(config.minCubeSizePx, config.maxCubeSizePxExclusive).toFloat()
        }

        val maxX = (viewportWidth - size).coerceAtLeast(0f)
        val x = random.nextFloat() * maxX

        val speedFactor = when (type) {
            CubeType.WHITE -> 1f
            CubeType.RED -> 1.1f
            CubeType.RED_FAST -> 2.2f
            CubeType.GREEN -> 0.9f
            CubeType.SHIELD -> 0.95f
            CubeType.SLOW_MOTION -> 0.85f
            CubeType.PLATFORM_SLOW -> 1.15f
            CubeType.INVERT_CONTROL -> 1f
        }

        val speed =
            (config.baseCubeSpeedPxPerSecond + (score / config.scorePerSpeedStep) * config.cubeSpeedPerStep) * speedFactor
        fallingCubes += FallingCube(type = type, x = x, y = -size, size = size, speed = speed)
    }

    private fun clampPlatform() {
        val maxX = (viewportWidth - currentPlatformWidthPx()).coerceAtLeast(0f)
        platformX = platformX.coerceIn(0f, maxX)
    }

    private fun currentPlatformWidthPx(): Float {
        val reducedWidth =
            config.basePlatformWidthPx(viewportWidth) - (score / config.scorePerShrinkStep) * config.platformWidthShrinkPerStepPx
        val minWidth = config.basePlatformWidthPx(viewportWidth) * config.minPlatformWidthFactor
        return reducedWidth.coerceAtLeast(minWidth)
    }

    private fun slowMotionDurationTicks(): Int =
        (config.slowMotionDurationSeconds / CatchRectGameConfig.FIXED_DT).roundToInt()

    private fun shieldDurationTicks(): Int =
        (config.shieldDurationSeconds / CatchRectGameConfig.FIXED_DT).roundToInt()

    private fun controlInvertDurationTicks(): Int =
        (config.controlInvertDurationSeconds / CatchRectGameConfig.FIXED_DT).roundToInt()

    private fun platformSlowDurationTicks(): Int =
        (config.platformSlowDurationSeconds / CatchRectGameConfig.FIXED_DT).roundToInt()

    private fun reseed() {
        seed = SecureRandom().nextLong()
        random = Random(seed)
    }

    private class CaughtCubeEvent(val type: CubeType, val x: Float, val y: Float)
}
