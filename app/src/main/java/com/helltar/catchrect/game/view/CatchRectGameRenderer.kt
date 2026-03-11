package com.helltar.catchrect.game.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.TypedValue
import com.helltar.catchrect.R
import com.helltar.catchrect.game.engine.CatchRectGameEngine
import com.helltar.catchrect.game.model.CubeType

class CatchRectGameRenderer(context: Context) {

    private val resources = context.resources
    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = sp(18f)
        textAlign = Paint.Align.CENTER
    }

    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(190, 0, 0, 0) }
    private val platformPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(229, 57, 53) }
    private val greenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(67, 160, 71) }
    private val buttonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(41, 98, 255) }
    private val submitButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(67, 160, 71) }
    private val leaderboardButtonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(214, 168, 48) }

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

    @Volatile
    var showSubmitButton = false

    @Volatile
    var showLeaderboardButton = false

    @Volatile
    var oledBackground = false

    fun isRestartButtonHit(x: Float, y: Float): Boolean = restartButtonRect.contains(x, y)
    fun isSubmitButtonHit(x: Float, y: Float): Boolean = submitButtonRect.contains(x, y)
    fun isLeaderboardButtonHit(x: Float, y: Float): Boolean = leaderboardButtonRect.contains(x, y)

    fun draw(canvas: Canvas, engine: CatchRectGameEngine) {
        canvas.drawColor(if (oledBackground) Color.BLACK else Color.rgb(16, 20, 24))

        val hudY = engine.safeTopInset + dp(28f)
        val leftX = engine.viewportWidth * 0.25f
        val rightX = engine.viewportWidth * 0.75f
        canvas.drawText(resources.getString(R.string.game_hud_score, engine.score), leftX, hudY, textPaint)
        canvas.drawText(resources.getString(R.string.game_hud_lives, engine.lives), rightX, hudY, textPaint)

        canvas.drawRect(
            engine.platformLeft,
            engine.platformTop,
            engine.platformRight,
            engine.platformBottom,
            platformPaint
        )

        for (cube in engine.cubes) {
            val cubePaint = when (cube.type) {
                CubeType.WHITE -> whitePaint
                CubeType.RED, CubeType.RED_FAST -> redPaint
                CubeType.GREEN -> greenPaint
            }
            canvas.drawRect(cube.x, cube.y, cube.x + cube.size, cube.y + cube.size, cubePaint)
        }

        if (engine.isGameOver) {
            drawGameOver(canvas, engine)
        } else {
            restartButtonRect.setEmpty()
            submitButtonRect.setEmpty()
            leaderboardButtonRect.setEmpty()
        }
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

    private fun dp(value: Float): Float = value * density

    private fun sp(value: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, scaledDensity)
}
