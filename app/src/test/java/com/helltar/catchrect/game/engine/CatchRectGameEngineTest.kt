package com.helltar.catchrect.game.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CatchRectGameEngineTest {

    private fun stationaryEngine(seed: Long): CatchRectGameEngine =
        CatchRectGameEngine(CatchRectGameConfig(), initialSeed = seed).apply {
            updateViewport(VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
            updateSafeInsets(0, 0)
            movePlatformByCenter(VIEWPORT_WIDTH / 2f) // platformX settles at 360
        }

    /**
     * Locks the deterministic output of the engine for two fixed scenarios. The
     * server's [ReplayVerifier] re-simulates the identical (seed, single input at
     * x=360, totalTicks) replay and must arrive at the same score — see the server
     * test `ReplayVerifierGoldenTest`. If these numbers change, the client and
     * server engines have drifted and every honest replay would fail verification.
     *
     * The stationary paddle survives the full cap on one seed and runs out of lives
     * early on the other; both the score and the recorded tick count are locked.
     */
    @Test
    fun `engine reproduces the cross-engine golden replays`() {
        // seed to (score, totalTicks)
        val goldens = listOf(
            2L to (15 to 6589),
            11L to (15 to 7200)
        )
        for ((seed, expected) in goldens) {
            val (expectedScore, expectedTicks) = expected
            val engine = stationaryEngine(seed)
            repeat(TOTAL_TICKS) { engine.update() }

            val replay = engine.getReplayData()
            assertEquals("seed $seed score", expectedScore, replay.score)
            assertEquals("seed $seed totalTicks", expectedTicks, replay.totalTicks)
            assertEquals(
                "seed $seed inputs",
                listOf(ReplayInput(tick = 0, platformX = 360f)),
                replay.inputs
            )
        }
    }

    /**
     * The shield is a timed invulnerability window (not a one-shot charge): once a
     * SHIELD cube is caught it stays active for [CatchRectGameConfig.shieldDurationSeconds]
     * and absorbs every red caught during that window, then expires on its own.
     */
    @Test
    fun `timed shield activates for its full duration, blocks reds, then expires`() {
        val config = CatchRectGameConfig()
        val engine = CatchRectGameEngine(config, initialSeed = 11L).apply {
            updateViewport(VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
            updateSafeInsets(0, 0)
            movePlatformByCenter(VIEWPORT_WIDTH / 2f)
        }

        var sawActivation = false
        var secondsAtActivation = 0f
        var sawExpiry = false
        var wasActive = false

        repeat(TOTAL_TICKS) {
            engine.update()
            val active = engine.isShieldActive
            if (active && !wasActive) {
                sawActivation = true
                secondsAtActivation = engine.shieldSecondsRemaining
            }
            if (!active && wasActive) {
                sawExpiry = true
            }
            wasActive = active
        }

        assertTrue("shield should activate at least once", sawActivation)
        assertEquals(
            "shield starts at its full configured duration",
            config.shieldDurationSeconds,
            secondsAtActivation,
            0.001f
        )
        assertTrue("shield should expire on its own (timed, not permanent)", sawExpiry)
        // The active shield absorbed a red instead of costing a life.
        assertEquals("one red blocked by the timed shield", 1, engine.blockedHitCount)
    }

    /**
     * Catching an INVERT_CONTROL cube activates a timed window (the SurfaceView
     * mirrors touch input and flips key direction while it is active). The engine
     * only owns the timer: it activates for its full configured duration and then
     * expires on its own.
     */
    @Test
    fun `timed control inversion activates for its full duration then expires`() {
        val config = CatchRectGameConfig()
        val engine = CatchRectGameEngine(config, initialSeed = 11L).apply {
            updateViewport(VIEWPORT_WIDTH, VIEWPORT_HEIGHT)
            updateSafeInsets(0, 0)
            movePlatformByCenter(VIEWPORT_WIDTH / 2f)
        }

        var sawActivation = false
        var secondsAtActivation = 0f
        var sawExpiry = false
        var wasActive = false

        repeat(TOTAL_TICKS) {
            engine.update()
            val active = engine.isControlInvertActive
            if (active && !wasActive) {
                sawActivation = true
                secondsAtActivation = engine.controlInvertSecondsRemaining
            }
            if (!active && wasActive) {
                sawExpiry = true
            }
            wasActive = active
        }

        assertTrue("control inversion should activate at least once", sawActivation)
        assertEquals(
            "inversion starts at its full configured duration",
            config.controlInvertDurationSeconds,
            secondsAtActivation,
            0.001f
        )
        assertTrue("inversion should expire on its own", sawExpiry)
    }

    /**
     * The replay records only the integer-truncated paddle X, and the server
     * re-simulates catch detection from that integer. So the catch zone must be
     * built from the recorded integer X — not the full-precision platformX — or a
     * paddle resting at a fractional pixel makes the server diverge by boundary
     * flips and rejects honest replays (the "expected 1401, got 1404" failure).
     */
    @Test
    fun `catch zone is built from the recorded integer paddle X, not the float position`() {
        val engine = stationaryEngine(11L) // platformX settles at the integer 360
        // Nudge the paddle to a deliberately fractional position (360.7).
        engine.movePlatformBy(0.7f)
        engine.update()

        val replay = engine.getReplayData()
        val recordedX = replay.inputs.last().platformX
        assertEquals(
            "catch zone left edge must equal the recorded integer X",
            recordedX,
            engine.platformLeft,
            0f
        )
        assertEquals(
            "recorded X must be the truncated integer, not the fractional position",
            recordedX,
            kotlin.math.floor(recordedX),
            0f
        )
    }

    @Test
    fun `fresh engine and restart both start from a clean state`() {
        val engine = stationaryEngine(11L)
        repeat(2000) { engine.update() }
        engine.restart()

        assertEquals(0, engine.score)
        assertEquals(3, engine.lives)
        assertEquals(0, engine.combo)
        assertEquals(0, engine.blockedHitCount)
        assertEquals(0, engine.livesGainedCount)
        assertFalse(engine.isShieldActive)
        assertFalse(engine.isSlowMotionActive)
        assertFalse(engine.isPlatformSlowActive)
        assertFalse(engine.isControlInvertActive)
        assertFalse(engine.isGameOver)
    }

    private companion object {
        const val VIEWPORT_WIDTH = 1080
        const val VIEWPORT_HEIGHT = 2400
        const val TOTAL_TICKS = 7200
    }
}
