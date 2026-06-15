package com.helltar.catchrect.game.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.util.TypedValue
import com.helltar.catchrect.R
import com.helltar.catchrect.game.engine.CatchRectGameEngine
import com.helltar.catchrect.game.model.CubeType
import kotlin.math.sin
import kotlin.random.Random

class CatchRectGameRenderer(context: Context) {

    private val resources = context.resources
    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics

    private val random = Random(System.nanoTime())

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(18f)
        textAlign = Paint.Align.CENTER
    }
    private val scoreTextBaseSize = sp(18f)

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 0, 0, 0) }
    private val platformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val platformGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(41, 98, 255) }
    private val submitButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(67, 160, 71) }
    private val leaderboardButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 168, 48) }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val particlePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val flashPaint = Paint()
    private val backgroundPaint = Paint().apply { isDither = true }

    // Static dither noise overlaid on the gradient to break up 8-bit colour banding.
    private val ditherPaint = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.OVERLAY)
        alpha = 24
        shader = buildDitherShader()
    }

    private val buttonTextPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sp(20f)
            textAlign = Paint.Align.CENTER
        }

    private val titlePaint =
        Paint(textPaint).apply {
            textSize = sp(30f)
            textAlign = Paint.Align.CENTER
        }

    private val subtitlePaint =
        Paint(textPaint).apply {
            textSize = sp(20f)
            textAlign = Paint.Align.CENTER
        }

    private val restartButtonRect = RectF()
    private val submitButtonRect = RectF()
    private val leaderboardButtonRect = RectF()
    private val tmpRect = RectF()

    // --- Cosmetic animation state (render thread only) ---
    private var elapsedSeconds = 0f
    private var backgroundShader: LinearGradient? = null
    private var backgroundShaderWidth = 0
    private var backgroundShaderHeight = 0
    private var backgroundShaderOled = false

    private val stars = ArrayList<Star>()
    private var starsViewportWidth = 0
    private var starsViewportHeight = 0

    private var platformShaderTop = Float.NaN
    private var platformShaderBottom = Float.NaN

    private val particles = ArrayList<Particle>()

    private var flashColor = Color.TRANSPARENT
    private var flashStrength = 0f
    private var scorePulse = 0f

    @Volatile
    var showSubmitButton = false

    @Volatile
    var showLeaderboardButton = false

    @Volatile
    var oledBackground = false

    fun isRestartButtonHit(x: Float, y: Float): Boolean = restartButtonRect.contains(x, y)
    fun isSubmitButtonHit(x: Float, y: Float): Boolean = submitButtonRect.contains(x, y)
    fun isLeaderboardButtonHit(x: Float, y: Float): Boolean = leaderboardButtonRect.contains(x, y)

    /** Spawns purely cosmetic feedback when a cube is caught. Does not touch game logic. */
    fun onCubeCaught(type: CubeType, x: Float, y: Float) {
        val baseColor = cubeColor(type)
        val count = if (type == CubeType.RED_FAST) 18 else 14
        repeat(count) {
            val angle = random.nextFloat() * Math.PI.toFloat() * 2f
            val speed = dp(60f) + random.nextFloat() * dp(180f)
            particles += Particle(
                x = x,
                y = y,
                vx = kotlin.math.cos(angle) * speed,
                vy = kotlin.math.sin(angle) * speed - dp(60f),
                life = 0.5f + random.nextFloat() * 0.4f,
                maxLife = 0f,
                size = dp(2f) + random.nextFloat() * dp(4f),
                color = baseColor
            ).also { it.maxLife = it.life }
        }
        when (type) {
            CubeType.RED, CubeType.RED_FAST -> triggerFlash(Color.rgb(229, 57, 53), 0.45f)
            CubeType.GREEN -> triggerFlash(Color.rgb(67, 160, 71), 0.4f)
            CubeType.WHITE -> scorePulse = 1f
        }
    }

    /** Advances cosmetic animations. Must be called once per frame before [draw]. */
    fun update(engine: CatchRectGameEngine, dtSeconds: Float) {
        val dt = dtSeconds.coerceIn(0f, 0.05f)
        elapsedSeconds += dt

        ensureStars(engine.viewportWidth, engine.viewportHeight)
        val height = engine.viewportHeight
        for (star in stars) {
            star.y += star.speed * dt
            if (height > 0 && star.y > height) {
                star.y = -star.radius
                star.x = random.nextFloat() * engine.viewportWidth
            }
        }

        var w = 0
        for (i in particles.indices) {
            val p = particles[i]
            p.life -= dt
            if (p.life <= 0f) continue
            p.vy += dp(420f) * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
            if (w != i) particles[w] = p
            w++
        }
        if (w < particles.size) particles.subList(w, particles.size).clear()

        if (flashStrength > 0f) flashStrength = (flashStrength - dt * 2.2f).coerceAtLeast(0f)
        if (scorePulse > 0f) scorePulse = (scorePulse - dt * 3.5f).coerceAtLeast(0f)
    }

    fun draw(canvas: Canvas, engine: CatchRectGameEngine) {
        drawBackground(canvas, engine)
        drawStars(canvas)

        val hudY = engine.safeTopInset + dp(28f)
        val leftX = engine.viewportWidth * 0.25f
        val rightX = engine.viewportWidth * 0.75f
        textPaint.textSize = scoreTextBaseSize * (1f + 0.35f * scorePulse)
        canvas.drawText(resources.getString(R.string.game_hud_score, engine.score), leftX, hudY, textPaint)
        textPaint.textSize = scoreTextBaseSize
        canvas.drawText(resources.getString(R.string.game_hud_lives, engine.lives), rightX, hudY, textPaint)

        drawPlatform(canvas, engine)

        for (cube in engine.cubes) {
            drawCube(canvas, cube.type, cube.x, cube.y, cube.size)
        }

        drawParticles(canvas)
        drawFlash(canvas, engine)

        if (engine.isGameOver) {
            drawGameOver(canvas, engine)
        } else {
            restartButtonRect.setEmpty()
            submitButtonRect.setEmpty()
            leaderboardButtonRect.setEmpty()
        }
    }

    private fun drawBackground(canvas: Canvas, engine: CatchRectGameEngine) {
        if (oledBackground) {
            canvas.drawColor(Color.BLACK)
            return
        }
        val w = engine.viewportWidth
        val h = engine.viewportHeight
        if (w <= 0 || h <= 0) {
            canvas.drawColor(Color.rgb(16, 20, 24))
            return
        }
        if (backgroundShader == null || backgroundShaderWidth != w || backgroundShaderHeight != h || backgroundShaderOled) {
            backgroundShader = LinearGradient(
                0f, 0f, 0f, h.toFloat(),
                intArrayOf(Color.rgb(26, 32, 48), Color.rgb(15, 18, 26), Color.rgb(9, 11, 16)),
                floatArrayOf(0f, 0.6f, 1f),
                Shader.TileMode.CLAMP
            )
            backgroundShaderWidth = w
            backgroundShaderHeight = h
            backgroundShaderOled = false
            backgroundPaint.shader = backgroundShader
        }
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), backgroundPaint)
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), ditherPaint)
    }

    private fun ensureStars(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (stars.isNotEmpty() && starsViewportWidth == width && starsViewportHeight == height) return
        starsViewportWidth = width
        starsViewportHeight = height
        stars.clear()
        val count = (width * height / (38_000f)).toInt().coerceIn(18, 60)
        repeat(count) {
            stars += Star(
                x = random.nextFloat() * width,
                y = random.nextFloat() * height,
                radius = dp(0.7f) + random.nextFloat() * dp(1.6f),
                speed = dp(8f) + random.nextFloat() * dp(26f),
                alpha = 30 + random.nextInt(60)
            )
        }
    }

    private fun drawStars(canvas: Canvas) {
        if (oledBackground || stars.isEmpty()) return
        for (star in stars) {
            val twinkle = 0.6f + 0.4f * sin(elapsedSeconds * 2f + star.x)
            starPaint.color = Color.argb((star.alpha * twinkle).toInt().coerceIn(0, 255), 255, 255, 255)
            canvas.drawCircle(star.x, star.y, star.radius, starPaint)
        }
    }

    private fun drawPlatform(canvas: Canvas, engine: CatchRectGameEngine) {
        val left = engine.platformLeft
        val top = engine.platformTop
        val right = engine.platformRight
        val bottom = engine.platformBottom
        if (right <= left) return
        val radius = (bottom - top) / 2f

        val glowPad = dp(6f)
        platformGlowPaint.color = Color.argb(70, 120, 170, 255)
        tmpRect.set(left - glowPad, top - glowPad, right + glowPad, bottom + glowPad)
        canvas.drawRoundRect(tmpRect, radius + glowPad, radius + glowPad, platformGlowPaint)

        if (platformPaint.shader == null || platformShaderTop != top || platformShaderBottom != bottom) {
            platformPaint.shader = LinearGradient(
                0f, top, 0f, bottom,
                Color.WHITE, Color.rgb(176, 200, 235),
                Shader.TileMode.CLAMP
            )
            platformShaderTop = top
            platformShaderBottom = bottom
        }
        tmpRect.set(left, top, right, bottom)
        canvas.drawRoundRect(tmpRect, radius, radius, platformPaint)
    }

    private fun drawCube(canvas: Canvas, type: CubeType, x: Float, y: Float, size: Float) {
        val base = cubeColor(type)
        val breathe = 1f + 0.045f * sin(elapsedSeconds * 4f + x * 0.05f)
        val drawSize = size * breathe
        val offset = (drawSize - size) / 2f
        val left = x - offset
        val top = y - offset
        val right = x + size + offset
        val bottom = y + size + offset
        val radius = drawSize * 0.24f

        // Motion trail for the fast red cube.
        if (type == CubeType.RED_FAST) {
            val trailStep = drawSize * 0.55f
            for (i in 1..3) {
                val ty = top - trailStep * i
                bodyPaint.color = withAlpha(base, 70 - i * 18)
                tmpRect.set(left, ty, right, ty + drawSize)
                canvas.drawRoundRect(tmpRect, radius, radius, bodyPaint)
            }
        }

        // Soft glow.
        val glowPad = drawSize * 0.18f
        glowPaint.color = withAlpha(base, 70)
        tmpRect.set(left - glowPad, top - glowPad, right + glowPad, bottom + glowPad)
        canvas.drawRoundRect(tmpRect, radius + glowPad, radius + glowPad, glowPaint)

        // Body.
        bodyPaint.color = base
        tmpRect.set(left, top, right, bottom)
        canvas.drawRoundRect(tmpRect, radius, radius, bodyPaint)

        // Top-left highlight.
        highlightPaint.color = Color.argb(70, 255, 255, 255)
        val inset = drawSize * 0.16f
        tmpRect.set(left + inset, top + inset, left + drawSize * 0.5f, top + drawSize * 0.5f)
        canvas.drawRoundRect(tmpRect, radius * 0.6f, radius * 0.6f, highlightPaint)
    }

    private fun drawParticles(canvas: Canvas) {
        for (p in particles) {
            val alpha = (255 * (p.life / p.maxLife)).toInt().coerceIn(0, 255)
            particlePaint.color = withAlpha(p.color, alpha)
            canvas.drawCircle(p.x, p.y, p.size, particlePaint)
        }
    }

    private fun drawFlash(canvas: Canvas, engine: CatchRectGameEngine) {
        if (flashStrength <= 0f) return
        val alpha = (110 * flashStrength).toInt().coerceIn(0, 255)
        flashPaint.color = withAlpha(flashColor, alpha)
        canvas.drawRect(0f, 0f, engine.viewportWidth.toFloat(), engine.viewportHeight.toFloat(), flashPaint)
    }

    private fun triggerFlash(color: Int, strength: Float) {
        flashColor = color
        flashStrength = strength.coerceIn(0f, 1f)
    }

    private fun drawGameOver(canvas: Canvas, engine: CatchRectGameEngine) {
        canvas.drawRect(
            0f,
            0f,
            engine.viewportWidth.toFloat(),
            engine.viewportHeight.toFloat(),
            overlayPaint
        )

        val centerX = engine.viewportWidth / 2f
        val centerY = engine.viewportHeight / 2f

        canvas.drawText(resources.getString(R.string.game_over_title), centerX, centerY - dp(50f), titlePaint)
        canvas.drawText(resources.getString(R.string.final_score, engine.score), centerX, centerY - dp(16f), subtitlePaint)

        val buttonW = dp(170f)
        val buttonH = dp(56f)
        val cornerR = dp(12f)

        restartButtonRect.set(
            centerX - buttonW / 2f,
            centerY + dp(10f),
            centerX + buttonW / 2f,
            centerY + dp(10f) + buttonH
        )
        canvas.drawRoundRect(restartButtonRect, cornerR, cornerR, buttonPaint)
        drawButtonText(canvas, resources.getString(R.string.restart), restartButtonRect)

        if (showSubmitButton) {
            submitButtonRect.set(
                centerX - buttonW / 2f,
                restartButtonRect.bottom + dp(16f),
                centerX + buttonW / 2f,
                restartButtonRect.bottom + dp(16f) + buttonH
            )
            canvas.drawRoundRect(submitButtonRect, cornerR, cornerR, submitButtonPaint)
            drawButtonText(canvas, resources.getString(R.string.submit_score_button), submitButtonRect)
        } else {
            submitButtonRect.setEmpty()
        }

        if (showLeaderboardButton) {
            leaderboardButtonRect.set(
                centerX - buttonW / 2f,
                restartButtonRect.bottom + dp(16f),
                centerX + buttonW / 2f,
                restartButtonRect.bottom + dp(16f) + buttonH
            )
            canvas.drawRoundRect(leaderboardButtonRect, cornerR, cornerR, leaderboardButtonPaint)
            drawButtonText(canvas, resources.getString(R.string.leaderboard_button), leaderboardButtonRect)
        } else {
            leaderboardButtonRect.setEmpty()
        }
    }

    private fun drawButtonText(canvas: Canvas, text: String, rect: RectF) {
        val textY = rect.centerY() - ((buttonTextPaint.descent() + buttonTextPaint.ascent()) / 2f)
        canvas.drawText(text, rect.centerX(), textY, buttonTextPaint)
    }

    private fun cubeColor(type: CubeType): Int = when (type) {
        CubeType.WHITE -> Color.rgb(236, 240, 245)
        CubeType.RED, CubeType.RED_FAST -> Color.rgb(229, 57, 53)
        CubeType.GREEN -> Color.rgb(67, 160, 71)
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        Color.argb(alpha.coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    /**
     * Builds a small tiled grey-noise texture. Drawn with OVERLAY blending, mid-grey (128)
     * is a no-op while the per-pixel deviations nudge each pixel up or down, dithering away
     * the visible colour banding of the dark background gradient. Generated once, reused.
     */
    private fun buildDitherShader(): BitmapShader {
        val size = 128
        val rnd = java.util.Random(0x5EED)
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            val v = 96 + rnd.nextInt(65) // 96..160, centred on mid-grey
            pixels[i] = Color.rgb(v, v, v)
        }
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(pixels, 0, size, 0, 0, size, size)
        return BitmapShader(bitmap, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
    }

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, scaledDensity)

    private class Star(
        var x: Float,
        var y: Float,
        val radius: Float,
        val speed: Float,
        val alpha: Int
    )

    private class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var life: Float,
        var maxLife: Float,
        val size: Float,
        val color: Int
    )
}
