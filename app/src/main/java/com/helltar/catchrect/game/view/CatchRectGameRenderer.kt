package com.helltar.catchrect.game.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.TypedValue
import com.helltar.catchrect.R
import com.helltar.catchrect.game.engine.CatchRectGameEngine
import com.helltar.catchrect.game.model.CubeType
import kotlin.math.ceil
import kotlin.math.sin
import kotlin.random.Random

class CatchRectGameRenderer(context: Context) {

    private val resources = context.resources
    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics

    private val random = Random(System.nanoTime())

    private val hudTypeface = Typeface.create("sans-serif-medium", Typeface.BOLD)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(20f)
        textAlign = Paint.Align.CENTER
        typeface = hudTypeface
        letterSpacing = 0.06f
    }
    private val scoreTextBaseSize = sp(20f)

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 0, 0, 0) }
    private val platformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val platformGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(41, 98, 255) }
    private val submitButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(67, 160, 71) }
    private val leaderboardButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 168, 48) }

    private val hudIconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val heartPath = buildHeartPath()
    private val shieldPath = buildShieldPath()
    private val statusPillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val statusTextPaint =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = sp(13f)
            textAlign = Paint.Align.CENTER
            typeface = hudTypeface
            letterSpacing = 0.03f
        }
    private val statusIconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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

    private val statPaint =
        Paint(textPaint).apply {
            color = Color.argb(220, 255, 255, 255)
            textSize = sp(15f)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.02f
        }

    private val statPanelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(36, 255, 255, 255)
    }

    private val statPanelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.argb(46, 255, 255, 255)
        strokeWidth = dp(1f)
    }

    private val statDividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(26, 255, 255, 255)
        strokeWidth = dp(1f)
    }

    private val statLabelPaint =
        Paint(statPaint).apply {
            color = Color.argb(160, 255, 255, 255)
            textAlign = Paint.Align.LEFT
            letterSpacing = 0.01f
        }

    private val statValuePaint =
        Paint(statPaint).apply {
            color = Color.WHITE
            textAlign = Paint.Align.RIGHT
            letterSpacing = 0.01f
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
        val count = when (type) {
            CubeType.RED_FAST, CubeType.SHIELD, CubeType.SLOW_MOTION -> 18
            else -> 14
        }
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
            CubeType.SHIELD -> triggerFlash(Color.rgb(38, 198, 218), 0.38f)
            CubeType.SLOW_MOTION -> triggerFlash(Color.rgb(171, 71, 188), 0.34f)
            CubeType.PLATFORM_SLOW -> triggerFlash(Color.rgb(245, 124, 0), 0.42f)
            CubeType.INVERT_CONTROL -> triggerFlash(Color.rgb(233, 30, 99), 0.42f)
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
        drawHudEntry(canvas, leftX, hudY, engine.score.toString(), 1f + 0.35f * scorePulse) { cx, cy, s ->
            drawScoreIcon(canvas, cx, cy, s)
        }
        drawHudEntry(canvas, rightX, hudY, engine.lives.toString(), 1f) { cx, cy, s ->
            drawHeartIcon(canvas, cx, cy, s)
        }
        drawStatusHud(canvas, engine, hudY + dp(32f))

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
        platformGlowPaint.color = if (engine.isShieldActive) {
            Color.argb(120, 38, 198, 218)
        } else if (engine.isPlatformSlowActive) {
            Color.argb(115, 245, 124, 0)
        } else if (engine.isControlInvertActive) {
            Color.argb(120, 233, 30, 99)
        } else {
            Color.argb(70, 120, 170, 255)
        }
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

        drawCubeSymbol(canvas, type, left, top, drawSize)
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

        canvas.drawText(resources.getString(R.string.game_over_title), centerX, centerY - dp(150f), titlePaint)
        canvas.drawText(resources.getString(R.string.final_score, engine.score), centerX, centerY - dp(112f), subtitlePaint)
        val panelBottom = drawRunStats(canvas, engine, centerX, centerY - dp(88f))

        val buttonW = dp(170f)
        val buttonH = dp(56f)
        val cornerR = dp(12f)

        restartButtonRect.set(
            centerX - buttonW / 2f,
            panelBottom + dp(22f),
            centerX + buttonW / 2f,
            panelBottom + dp(22f) + buttonH
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

    /** Draws the results card and returns its bottom Y so the buttons can flow below it. */
    private fun drawRunStats(canvas: Canvas, engine: CatchRectGameEngine, centerX: Float, topY: Float): Float {
        val rows = listOf(
            resources.getString(R.string.stat_label_time) to formatDuration(engine.survivalSeconds),
            resources.getString(R.string.stat_label_best_combo) to "×${engine.bestCombo}",
            resources.getString(R.string.stat_label_caught) to "${engine.caughtWhiteCount}",
            resources.getString(R.string.stat_label_powerups) to "${engine.powerUpsUsed}",
            resources.getString(R.string.stat_label_blocks) to "${engine.blockedHitCount}"
        )

        val panelWidth = dp(248f)
        val rowHeight = dp(30f)
        val verticalPad = dp(12f)
        val horizontalPad = dp(20f)
        val panelHeight = verticalPad * 2f + rowHeight * rows.size

        val left = centerX - panelWidth / 2f
        val right = centerX + panelWidth / 2f
        val cornerR = dp(16f)
        tmpRect.set(left, topY, right, topY + panelHeight)
        canvas.drawRoundRect(tmpRect, cornerR, cornerR, statPanelPaint)
        canvas.drawRoundRect(tmpRect, cornerR, cornerR, statPanelStrokePaint)

        val labelX = left + horizontalPad
        val valueX = right - horizontalPad
        val textOffset = (statLabelPaint.descent() + statLabelPaint.ascent()) / 2f
        for (i in rows.indices) {
            val rowTop = topY + verticalPad + rowHeight * i
            val rowCenterY = rowTop + rowHeight / 2f
            if (i > 0) {
                canvas.drawLine(labelX, rowTop, valueX, rowTop, statDividerPaint)
            }
            val baseline = rowCenterY - textOffset
            canvas.drawText(rows[i].first, labelX, baseline, statLabelPaint)
            canvas.drawText(rows[i].second, valueX, baseline, statValuePaint)
        }

        return topY + panelHeight
    }

    private fun formatDuration(seconds: Float): String {
        val totalSeconds = seconds.toInt().coerceAtLeast(0)
        val minutes = totalSeconds / 60
        val remainingSeconds = totalSeconds % 60
        return "$minutes:${remainingSeconds.toString().padStart(2, '0')}"
    }

    private fun drawHudEntry(
        canvas: Canvas,
        centerX: Float,
        baselineY: Float,
        number: String,
        scale: Float,
        drawIcon: (cx: Float, cy: Float, size: Float) -> Unit
    ) {
        textPaint.textSize = scoreTextBaseSize * scale
        val textWidth = textPaint.measureText(number)
        val iconSize = textPaint.textSize * 0.74f
        val gap = iconSize * 0.5f
        val totalWidth = iconSize + gap + textWidth
        val startX = centerX - totalWidth / 2f
        val iconCenterY = baselineY + (textPaint.descent() + textPaint.ascent()) / 2f

        drawIcon(startX + iconSize / 2f, iconCenterY, iconSize)
        canvas.drawText(number, startX + iconSize + gap + textWidth / 2f, baselineY, textPaint)

        textPaint.textSize = scoreTextBaseSize
    }

    private fun drawStatusHud(canvas: Canvas, engine: CatchRectGameEngine, centerY: Float) {
        val pills = ArrayList<StatusPill>(4)
        if (engine.combo > 1) {
            pills += StatusPill(
                icon = StatusIcon.COMBO,
                text = "x${engine.comboMultiplier} ${engine.combo}",
                color = Color.rgb(41, 98, 255)
            )
        }
        if (engine.isShieldActive) {
            pills += StatusPill(
                icon = StatusIcon.SHIELD,
                text = "${ceil(engine.shieldSecondsRemaining).toInt()}s",
                color = Color.rgb(38, 198, 218)
            )
        }
        if (engine.isSlowMotionActive) {
            pills += StatusPill(
                icon = StatusIcon.SLOW_MOTION,
                text = "${ceil(engine.slowMotionSecondsRemaining).toInt()}s",
                color = Color.rgb(171, 71, 188)
            )
        }
        if (engine.isPlatformSlowActive) {
            pills += StatusPill(
                icon = StatusIcon.PLATFORM_SLOW,
                text = "${ceil(engine.platformSlowSecondsRemaining).toInt()}s",
                color = Color.rgb(245, 124, 0)
            )
        }
        if (engine.isControlInvertActive) {
            pills += StatusPill(
                icon = StatusIcon.INVERT_CONTROL,
                text = "${ceil(engine.controlInvertSecondsRemaining).toInt()}s",
                color = Color.rgb(233, 30, 99)
            )
        }
        if (pills.isEmpty()) return

        val height = dp(28f)
        val gap = dp(8f)
        val iconSize = dp(14f)
        val horizontalPad = dp(10f)
        val iconGap = dp(6f)
        val widths = pills.map { pill ->
            horizontalPad * 2f + iconSize + iconGap + statusTextPaint.measureText(pill.text)
        }
        val totalWidth = widths.sum() + gap * (pills.size - 1)
        var left = (engine.viewportWidth - totalWidth) / 2f
        val baseline = centerY - (statusTextPaint.descent() + statusTextPaint.ascent()) / 2f

        for (i in pills.indices) {
            val pill = pills[i]
            val width = widths[i]
            tmpRect.set(left, centerY - height / 2f, left + width, centerY + height / 2f)
            statusPillPaint.color = withAlpha(pill.color, 74)
            canvas.drawRoundRect(tmpRect, height / 2f, height / 2f, statusPillPaint)

            val iconCenterX = left + horizontalPad + iconSize / 2f
            drawStatusIcon(canvas, pill.icon, iconCenterX, centerY, iconSize, Color.WHITE)

            val textX = iconCenterX + iconSize / 2f + iconGap + statusTextPaint.measureText(pill.text) / 2f
            statusTextPaint.color = Color.WHITE
            canvas.drawText(pill.text, textX, baseline, statusTextPaint)

            left += width + gap
        }
    }

    private fun drawScoreIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        val half = size / 2f
        val radius = size * 0.24f
        hudIconPaint.color = Color.rgb(236, 240, 245)
        tmpRect.set(cx - half, cy - half, cx + half, cy + half)
        canvas.drawRoundRect(tmpRect, radius, radius, hudIconPaint)
    }

    private fun drawHeartIcon(canvas: Canvas, cx: Float, cy: Float, size: Float) {
        hudIconPaint.color = Color.rgb(229, 57, 53)
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(size, size)
        canvas.translate(-0.5f, -0.5f)
        canvas.drawPath(heartPath, hudIconPaint)
        canvas.restore()
    }

    private fun drawCubeSymbol(canvas: Canvas, type: CubeType, left: Float, top: Float, size: Float) {
        when (type) {
            CubeType.SHIELD -> drawShieldIcon(canvas, left + size / 2f, top + size / 2f, size * 0.52f, Color.WHITE)
            CubeType.SLOW_MOTION -> drawSlowMotionIcon(canvas, left + size / 2f, top + size / 2f, size * 0.5f, Color.WHITE)
            CubeType.PLATFORM_SLOW -> drawPlatformSlowIcon(canvas, left + size / 2f, top + size / 2f, size * 0.56f, Color.WHITE)
            CubeType.INVERT_CONTROL -> drawInvertControlIcon(canvas, left + size / 2f, top + size / 2f, size * 0.56f, Color.WHITE)
            else -> Unit
        }
    }

    private fun drawStatusIcon(canvas: Canvas, icon: StatusIcon, cx: Float, cy: Float, size: Float, color: Int) {
        when (icon) {
            StatusIcon.COMBO -> drawComboIcon(canvas, cx, cy, size, color)
            StatusIcon.SHIELD -> drawShieldIcon(canvas, cx, cy, size, color)
            StatusIcon.SLOW_MOTION -> drawSlowMotionIcon(canvas, cx, cy, size, color)
            StatusIcon.PLATFORM_SLOW -> drawPlatformSlowIcon(canvas, cx, cy, size, color)
            StatusIcon.INVERT_CONTROL -> drawInvertControlIcon(canvas, cx, cy, size, color)
        }
    }

    private fun drawComboIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val half = size / 2f
        val radius = size * 0.18f
        statusIconPaint.style = Paint.Style.FILL
        statusIconPaint.color = withAlpha(color, 210)
        tmpRect.set(cx - half * 0.9f, cy - half * 0.25f, cx + half * 0.2f, cy + half * 0.85f)
        canvas.drawRoundRect(tmpRect, radius, radius, statusIconPaint)
        statusIconPaint.color = color
        tmpRect.set(cx - half * 0.2f, cy - half * 0.85f, cx + half * 0.9f, cy + half * 0.25f)
        canvas.drawRoundRect(tmpRect, radius, radius, statusIconPaint)
    }

    private fun drawShieldIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        statusIconPaint.style = Paint.Style.FILL
        statusIconPaint.color = withAlpha(color, 220)
        canvas.save()
        canvas.translate(cx - size / 2f, cy - size / 2f)
        canvas.scale(size, size)
        canvas.drawPath(shieldPath, statusIconPaint)
        canvas.restore()
    }

    private fun drawSlowMotionIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        statusIconPaint.color = withAlpha(color, 225)
        statusIconPaint.style = Paint.Style.STROKE
        statusIconPaint.strokeWidth = size * 0.11f
        canvas.drawCircle(cx, cy, size * 0.43f, statusIconPaint)
        canvas.drawLine(cx, cy, cx, cy - size * 0.25f, statusIconPaint)
        canvas.drawLine(cx, cy, cx + size * 0.22f, cy + size * 0.12f, statusIconPaint)
        statusIconPaint.style = Paint.Style.FILL
    }

    private fun drawPlatformSlowIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val half = size / 2f
        statusIconPaint.color = withAlpha(color, 225)
        statusIconPaint.style = Paint.Style.FILL
        tmpRect.set(cx - half * 0.72f, cy + half * 0.22f, cx + half * 0.72f, cy + half * 0.52f)
        canvas.drawRoundRect(tmpRect, size * 0.08f, size * 0.08f, statusIconPaint)

        statusIconPaint.style = Paint.Style.STROKE
        statusIconPaint.strokeWidth = size * 0.11f
        canvas.drawLine(cx, cy - half * 0.58f, cx, cy + half * 0.05f, statusIconPaint)
        canvas.drawLine(cx, cy + half * 0.05f, cx - half * 0.24f, cy - half * 0.18f, statusIconPaint)
        canvas.drawLine(cx, cy + half * 0.05f, cx + half * 0.24f, cy - half * 0.18f, statusIconPaint)
        statusIconPaint.style = Paint.Style.FILL
    }

    /** Two opposing horizontal arrows — the "controls swapped" glyph. */
    private fun drawInvertControlIcon(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int) {
        val half = size / 2f
        statusIconPaint.color = withAlpha(color, 225)
        statusIconPaint.style = Paint.Style.STROKE
        statusIconPaint.strokeWidth = size * 0.11f
        val len = half * 0.62f
        val head = half * 0.26f
        val topY = cy - half * 0.3f
        val botY = cy + half * 0.3f
        // top arrow points right
        canvas.drawLine(cx - len, topY, cx + len, topY, statusIconPaint)
        canvas.drawLine(cx + len, topY, cx + len - head, topY - head, statusIconPaint)
        canvas.drawLine(cx + len, topY, cx + len - head, topY + head, statusIconPaint)
        // bottom arrow points left
        canvas.drawLine(cx - len, botY, cx + len, botY, statusIconPaint)
        canvas.drawLine(cx - len, botY, cx - len + head, botY - head, statusIconPaint)
        canvas.drawLine(cx - len, botY, cx - len + head, botY + head, statusIconPaint)
        statusIconPaint.style = Paint.Style.FILL
    }

    /** Heart shape in a unit [0,1] box, centred so scaling around (0.5, 0.5) keeps it balanced. */
    private fun buildHeartPath(): Path = Path().apply {
        moveTo(0.5f, 0.92f)
        cubicTo(0.5f, 0.92f, 0.0f, 0.55f, 0.0f, 0.30f)
        cubicTo(0.0f, 0.10f, 0.27f, 0.05f, 0.5f, 0.25f)
        cubicTo(0.73f, 0.05f, 1.0f, 0.10f, 1.0f, 0.30f)
        cubicTo(1.0f, 0.55f, 0.5f, 0.92f, 0.5f, 0.92f)
        close()
    }

    private fun buildShieldPath(): Path = Path().apply {
        moveTo(0.5f, 0.02f)
        lineTo(0.88f, 0.18f)
        cubicTo(0.86f, 0.58f, 0.72f, 0.82f, 0.5f, 0.98f)
        cubicTo(0.28f, 0.82f, 0.14f, 0.58f, 0.12f, 0.18f)
        close()
    }

    private fun cubeColor(type: CubeType): Int = when (type) {
        CubeType.WHITE -> Color.rgb(236, 240, 245)
        CubeType.RED, CubeType.RED_FAST -> Color.rgb(229, 57, 53)
        CubeType.GREEN -> Color.rgb(67, 160, 71)
        CubeType.SHIELD -> Color.rgb(38, 198, 218)
        CubeType.SLOW_MOTION -> Color.rgb(171, 71, 188)
        CubeType.PLATFORM_SLOW -> Color.rgb(245, 124, 0)
        CubeType.INVERT_CONTROL -> Color.rgb(233, 30, 99)
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

    private class StatusPill(
        val icon: StatusIcon,
        val text: String,
        val color: Int
    )

    private enum class StatusIcon {
        COMBO,
        SHIELD,
        SLOW_MOTION,
        PLATFORM_SLOW,
        INVERT_CONTROL
    }
}
