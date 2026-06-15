package com.helltar.catchrect.game.view

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.SurfaceHolder
import android.view.SurfaceView
import com.helltar.catchrect.game.engine.CatchRectGameConfig
import com.helltar.catchrect.game.engine.CatchRectGameEngine
import com.helltar.catchrect.game.engine.GameReplay

class CatchRectSurfaceView(context: Context) : SurfaceView(context), SurfaceHolder.Callback, Runnable {

    @Volatile
    private var running = false
    private var renderThread: Thread? = null

    // Volatile input fields — written on UI thread, read on render thread
    @Volatile
    private var pendingTouchX = Float.NaN

    @Volatile
    private var pendingRestart = false

    @Volatile
    private var pendingTopInset = 0

    @Volatile
    private var pendingBottomInset = 0

    @Volatile
    private var keyDirection = 0 // -1 left, 0 none, 1 right

    // Engine and renderer are only accessed from the render thread (after init)
    private val engine = CatchRectGameEngine(config = CatchRectGameConfig())
    private val renderer = CatchRectGameRenderer(context)
    private val soundPlayer = GameSoundPlayer()
    private val musicPlayer = BackgroundMusicPlayer()
    private val mainHandler = Handler(Looper.getMainLooper())

    var onGameOver: ((GameReplay) -> Unit)? = null
    var onSubmitScore: ((GameReplay) -> Unit)? = null
    var onLeaderboardClick: (() -> Unit)? = null
    private var gameOverFired = false

    init {
        holder.addCallback(this)
        isFocusable = true
        isClickable = true
    }

    fun setSoundEnabled(enabled: Boolean) {
        soundPlayer.muted = !enabled
        musicPlayer.muted = !enabled
    }

    fun setSubmitButtonVisible(visible: Boolean) {
        renderer.showSubmitButton = visible
    }

    fun setLeaderboardButtonVisible(visible: Boolean) {
        renderer.showLeaderboardButton = visible
    }

    fun setOledBackground(enabled: Boolean) {
        renderer.oledBackground = enabled
    }

    fun updateSafeInsets(topInset: Int, bottomInset: Int) {
        pendingTopInset = topInset
        pendingBottomInset = bottomInset
    }

    fun resumeGame() {
        if (running) return
        running = true
        if (!engine.isGameOver) {
            musicPlayer.start()
        }
        renderThread = Thread(this, "CatchRectRenderThread").also { it.start() }
    }

    fun restartGame() {
        pendingRestart = true
    }

    fun pauseGame() {
        running = false
        musicPlayer.stop()
        renderThread?.join()
        renderThread = null
    }

    override fun onDetachedFromWindow() {
        pauseGame()
        musicPlayer.stop()
        soundPlayer.release()
        super.onDetachedFromWindow()
    }

    override fun surfaceCreated(holder: SurfaceHolder) {
        engine.updateViewport(width = width, height = height)
    }

    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
        engine.updateViewport(width = width, height = height)
    }

    override fun surfaceDestroyed(holder: SurfaceHolder) = Unit

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT -> {
                keyDirection = -1; return true
            }

            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                keyDirection = 1; return true
            }

            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_ENTER -> {
                if (engine.isGameOver) pendingRestart = true
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (keyDirection == -1) keyDirection = 0; return true
            }

            KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (keyDirection == 1) keyDirection = 0; return true
            }
        }
        return super.onKeyUp(keyCode, event)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (engine.isGameOver) {
                    if (renderer.isRestartButtonHit(event.x, event.y)) {
                        pendingRestart = true
                    } else if (renderer.isLeaderboardButtonHit(event.x, event.y)) {
                        mainHandler.post { onLeaderboardClick?.invoke() }
                    } else if (renderer.isSubmitButtonHit(event.x, event.y)) {
                        val replay = engine.getReplayData()
                        mainHandler.post { onSubmitScore?.invoke(replay) }
                    }
                } else {
                    pendingTouchX = event.x
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!engine.isGameOver) {
                    pendingTouchX = event.x
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    override fun run() {
        val targetFrameNanos = 1_000_000_000L / 120L
        var previous = System.nanoTime()

        while (running) {
            if (!holder.surface.isValid) {
                try {
                    Thread.sleep(1)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                continue
            }

            val frameStart = System.nanoTime()
            val dt = (frameStart - previous) / 1_000_000_000f
            previous = frameStart

            // Apply pending input from UI thread
            engine.updateSafeInsets(top = pendingTopInset, bottom = pendingBottomInset)

            if (pendingRestart) {
                pendingRestart = false
                engine.restart()
                gameOverFired = false
                musicPlayer.start()
            }

            val touchX = pendingTouchX
            if (!touchX.isNaN()) {
                if (engine.isPlatformSlowActive) {
                    val reached = engine.movePlatformTowardCenter(
                        centerX = touchX,
                        maxDeltaX = KEYBOARD_SPEED_PX_PER_SEC * engine.platformMovementFactor * dt
                    )
                    if (reached) {
                        pendingTouchX = Float.NaN
                    }
                } else {
                    pendingTouchX = Float.NaN
                    engine.movePlatformByCenter(touchX)
                }
            }

            val dir = keyDirection
            if (dir != 0) {
                engine.movePlatformBy(dir * KEYBOARD_SPEED_PX_PER_SEC * engine.platformMovementFactor * dt)
            }

            engine.update()
            musicPlayer.score = engine.score
            engine.drainCaughtCubeEvents { type, x, y ->
                soundPlayer.playCubeCatch(type)
                renderer.onCubeCaught(type, x, y)
            }
            renderer.update(engine, dt)

            if (engine.isGameOver && !gameOverFired) {
                gameOverFired = true
                musicPlayer.stop()
                val replay = engine.getReplayData()
                mainHandler.post { onGameOver?.invoke(replay) }
            }

            drawFrame()

            val elapsed = System.nanoTime() - frameStart
            val remaining = targetFrameNanos - elapsed
            if (remaining > 0L) {
                val sleepMs = remaining / 1_000_000L
                val sleepNs = (remaining % 1_000_000L).toInt()
                try {
                    Thread.sleep(sleepMs, sleepNs)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
            }
        }
    }

    private fun drawFrame() {
        val canvas = try {
            holder.lockHardwareCanvas() ?: return
        } catch (_: Exception) {
            return
        }

        try {
            renderer.draw(canvas, engine)
        } finally {
            holder.unlockCanvasAndPost(canvas)
        }
    }

    companion object {
        private const val KEYBOARD_SPEED_PX_PER_SEC = 900f
    }
}
