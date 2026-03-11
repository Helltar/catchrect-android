package com.helltar.catchrect.game.engine

import com.helltar.catchrect.game.model.CubeType
import com.helltar.catchrect.game.model.FallingCube
import java.security.SecureRandom
import kotlin.math.max
import kotlin.random.Random

class CatchRectGameEngine(private val config: CatchRectGameConfig, initialSeed: Long = SecureRandom().nextLong()) {

    private val fallingCubes = mutableListOf<FallingCube>()
    private val caughtCubeEvents = mutableListOf<CubeType>()

    private val replayInputs = mutableListOf<ReplayInput>()
    private var tickCount = 0
    private var lastRecordedX = Float.NaN

    var score: Int = 0
        private set
    var lives: Int = 3
        private set

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
            cube.y += cube.speed * dt

            val cubeRight = cube.x + cube.size
            val cubeBottom = cube.y + cube.size

            val isCaught = cubeRight >= pLeft &&
                    cube.x <= pRight &&
                    cubeBottom >= pTop &&
                    cube.y <= pBottom

            if (isCaught) {
                caughtCubeEvents += cube.type
                when (cube.type) {
                    CubeType.WHITE -> score += 1
                    CubeType.RED, CubeType.RED_FAST -> lives -= 1
                    CubeType.GREEN -> lives += 1
                }
                continue
            }

            if (cube.y > viewportHeight + cube.size) {
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

    fun drainCaughtCubeEvents(action: (CubeType) -> Unit) {
        if (caughtCubeEvents.isEmpty()) return
        for (i in caughtCubeEvents.indices) {
            action(caughtCubeEvents[i])
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
        score = score
    )

    fun restart() {
        fallingCubes.clear()
        caughtCubeEvents.clear()
        replayInputs.clear()
        tickCount = 0
        lastRecordedX = Float.NaN
        score = 0
        lives = 3
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
        val redBonus = (score / 50) * 0.01f
        val chance = random.nextFloat()
        val type = when {
            chance < 0.79f - redBonus -> CubeType.WHITE
            chance < 0.92f -> CubeType.RED
            chance < 0.99f -> CubeType.RED_FAST
            else -> CubeType.GREEN
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

    private fun reseed() {
        seed = SecureRandom().nextLong()
        random = Random(seed)
    }
}
