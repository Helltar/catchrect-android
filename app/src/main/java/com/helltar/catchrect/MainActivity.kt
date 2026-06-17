package com.helltar.catchrect

import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.ForegroundColorSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.helltar.catchrect.game.engine.GameReplay
import com.helltar.catchrect.game.view.CatchRectSurfaceView
import com.helltar.catchrect.network.LeaderboardApi
import com.helltar.catchrect.network.LeaderboardEntry
import com.helltar.catchrect.network.PendingSubmissionStore
import com.helltar.catchrect.network.SubmitScoreRequest
import com.helltar.catchrect.network.toSubmitScoreRequest
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import java.util.UUID
import kotlin.math.max

class MainActivity : ComponentActivity() {

    private lateinit var gameView: CatchRectSurfaceView
    private lateinit var menuView: LinearLayout
    private var isGameScreenVisible = false
    private var isSoundEnabled = true
    private var isOledBackground = false
    private var isLeaderboardLoading = false
    private var isScoreSubmitting = false
    private var bestScoreBadge: TextView? = null
    private var pendingSubmitButton: TextView? = null

    private val prefs by lazy { getSharedPreferences("settings", Context.MODE_PRIVATE) }
    private val displayTypeface: Typeface by lazy { Typeface.create("sans-serif-medium", Typeface.BOLD) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        isSoundEnabled = prefs.getBoolean(PREF_SOUND_ENABLED, true)
        isOledBackground = prefs.getBoolean(PREF_OLED_BACKGROUND, false)

        gameView = CatchRectSurfaceView(this)
        gameView.setSoundEnabled(isSoundEnabled)
        gameView.setOledBackground(isOledBackground)
        gameView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        gameView.visibility = View.GONE

        gameView.onGameOver = { replay ->
            val bestScore = prefs.getInt(PREF_BEST_SCORE, 0)
            gameView.setLeaderboardButtonVisible(false)
            gameView.setSubmitButtonVisible(replay.score > bestScore)
        }

        gameView.onSubmitScore = { replay ->
            showSubmitDialog(replay)
        }

        gameView.onLeaderboardClick = {
            showLeaderboard()
        }

        menuView = createMenuView()

        val root = FrameLayout(this).apply {
            addView(gameView)
            addView(menuView)
        }

        setContentView(root)

        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.navigationBars()
            )
            val gestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures())
            val topInset = max(bars.top, gestures.top)
            val bottomInset = max(bars.bottom, gestures.bottom)

            gameView.updateSafeInsets(topInset = topInset, bottomInset = bottomInset)
            menuView.setPadding(0, topInset + dp(24), 0, bottomInset + dp(24))
            insets
        }

        ViewCompat.requestApplyInsets(root)

        onBackPressedDispatcher.addCallback(this) {
            if (isGameScreenVisible) {
                showMenu()
            } else {
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (isGameScreenVisible) {
            gameView.resumeGame()
        } else {
            refreshPendingSubmissionButton()
        }
    }

    override fun onPause() {
        gameView.pauseGame()
        super.onPause()
    }

    private fun showMenu() {
        isGameScreenVisible = false
        gameView.pauseGame()
        gameView.visibility = View.GONE
        menuView.visibility = View.VISIBLE
        refreshBestScoreBadge()
        refreshPendingSubmissionButton()
    }

    private fun startGame() {
        isGameScreenVisible = true
        menuView.visibility = View.GONE
        gameView.visibility = View.VISIBLE
        gameView.setSubmitButtonVisible(false)
        gameView.setLeaderboardButtonVisible(false)
        gameView.restartGame()
        gameView.resumeGame()
    }

    private fun showSubmitDialog(replay: GameReplay) {
        val savedName = prefs.getString(PREF_PLAYER_NAME, "") ?: ""
        val bgColor = Color.rgb(16, 20, 24)
        val fieldColor = Color.rgb(40, 50, 65)
        val accentColor = Color.rgb(67, 160, 71)

        val title = TextView(this).apply {
            text = getString(R.string.submit_score_title, replay.score)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = displayTypeface
            gravity = Gravity.CENTER
            setPadding(0, dp(24), 0, dp(20))
        }

        val inputBg = GradientDrawable().apply {
            setColor(fieldColor)
            cornerRadius = dp(10).toFloat()
        }

        val input = EditText(this).apply {
            hint = getString(R.string.nickname_hint)
            setHintTextColor(Color.argb(100, 255, 255, 255))
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setText(savedName)
            isSingleLine = true
            maxLines = 1
            background = inputBg
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }

        val submitBtn = createMenuButton(getString(R.string.submit)) {}.apply {
            val shape = (background as RippleDrawable).getDrawable(0) as GradientDrawable
            shape.setColor(accentColor)
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(20)
        }

        val cancelBtn = createMenuButton(getString(R.string.cancel)) {}.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = dp(12)
        }

        val containerBackground = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(24).toFloat()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = containerBackground
            setPadding(dp(32), 0, dp(32), dp(24))
            addView(title)
            addView(
                input, LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(submitBtn)
            addView(cancelBtn)
        }

        var pendingSubmissionName: String? = null

        val dialog =
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
                .setView(container)
                .create()

        submitBtn.setOnClickListener {
            val name = normalizePlayerName(input.text.toString())

            if (name.isEmpty() || name.length > 32) {
                Toast.makeText(this, getString(R.string.name_length_error), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            prefs.edit { putString(PREF_PLAYER_NAME, name) }

            pendingSubmissionName = name
            dialog.dismiss()
        }

        cancelBtn.setOnClickListener { dialog.dismiss() }
        dialog.setOnDismissListener {
            pendingSubmissionName?.let { name ->
                pendingSubmissionName = null
                submitScore(name, replay)
            }
        }

        showCenteredDialog(dialog)
        input.requestFocus()
        input.selectAll()
    }

    private fun showSubmissionFailedDialog() {
        showGameMessageDialog(
            title = getString(R.string.submission_failed_title),
            message = getString(R.string.submission_failed_message),
            accentColor = Color.rgb(198, 76, 72)
        )
    }

    private fun showSubmissionSavedDialog() {
        showGameMessageDialog(
            title = getString(R.string.submission_saved_title),
            message = getString(R.string.submission_saved_message),
            accentColor = Color.rgb(214, 168, 48)
        )
    }

    private fun submitScore(playerName: String, replay: GameReplay) {
        runSubmission(replay.toSubmitScoreRequest(playerName, getOrCreateLocalPlayerId()), fromMenu = false)
    }

    private fun retryPendingSubmission() {
        val request = PendingSubmissionStore.load(this)
        if (request == null) {
            refreshPendingSubmissionButton()
            return
        }
        runSubmission(request, fromMenu = true)
    }

    /**
     * Submits [request] and, on anything other than a clean accept, keeps the run
     * on disk so it can be retried later from the menu instead of being lost when
     * the player leaves the game. A deterministic 4xx rejection is not retryable,
     * so it is never saved (and a doomed retry of it is dropped).
     */
    private fun runSubmission(request: SubmitScoreRequest, fromMenu: Boolean) {
        if (isScoreSubmitting) return

        isScoreSubmitting = true
        val loadingDialog = createLoadingDialog(getString(R.string.submitting_score))
        showCenteredDialog(loadingDialog)

        lifecycleScope.launch {
            try {
                val status = LeaderboardApi.instance.submitScore(request)

                when {
                    status == HttpStatusCode.OK -> {
                        prefs.edit {
                            putInt(PREF_BEST_SCORE, max(prefs.getInt(PREF_BEST_SCORE, 0), request.score))
                        }
                        PendingSubmissionStore.clearIfNotBetterThan(this@MainActivity, request.score)

                        if (loadingDialog.isShowing) loadingDialog.dismiss()

                        showGameMessageDialog(
                            title = getString(R.string.score_submitted_title),
                            message = getString(R.string.score_submitted_message),
                            accentColor = Color.rgb(67, 160, 71),
                            onDismiss = {
                                if (!fromMenu) {
                                    gameView.setSubmitButtonVisible(false)
                                    gameView.setLeaderboardButtonVisible(true)
                                }
                            }
                        )
                    }

                    status.value in 400..499 -> {
                        // Verification/validation rejection — retrying never helps.
                        if (fromMenu) PendingSubmissionStore.clear(this@MainActivity)
                        if (loadingDialog.isShowing) loadingDialog.dismiss()
                        showSubmissionFailedDialog()
                    }

                    else -> {
                        PendingSubmissionStore.saveIfBetter(this@MainActivity, request)
                        if (loadingDialog.isShowing) loadingDialog.dismiss()
                        showSubmissionSavedDialog()
                    }
                }
            } catch (e: Exception) {
                PendingSubmissionStore.saveIfBetter(this@MainActivity, request)
                if (loadingDialog.isShowing) loadingDialog.dismiss()
                showSubmissionSavedDialog()
            } finally {
                if (loadingDialog.isShowing) loadingDialog.dismiss()
                isScoreSubmitting = false
                refreshPendingSubmissionButton()
            }
        }
    }

    private fun showLeaderboard() {
        if (isLeaderboardLoading) return

        isLeaderboardLoading = true
        val loadingDialog = createLoadingDialog(getString(R.string.loading_leaderboard))
        showCenteredDialog(loadingDialog)

        lifecycleScope.launch {
            try {
                val entries = LeaderboardApi.instance.getLeaderboard(getOrCreateLocalPlayerId())
                if (loadingDialog.isShowing) {
                    loadingDialog.dismiss()
                }
                showLeaderboardDialog(entries)
            } catch (e: Exception) {
                if (loadingDialog.isShowing) {
                    loadingDialog.dismiss()
                }
                val errorMessage = getString(R.string.unknown_error)
                Toast.makeText(
                    this@MainActivity,
                    getString(R.string.failed_with_reason, errorMessage),
                    Toast.LENGTH_SHORT
                ).show()
            } finally {
                if (loadingDialog.isShowing) {
                    loadingDialog.dismiss()
                }
                isLeaderboardLoading = false
            }
        }
    }

    private fun showLeaderboardDialog(entries: List<LeaderboardEntry>) {
        val bgColor = Color.rgb(16, 20, 24)
        val panelColor = Color.rgb(26, 31, 38)
        val rowColor = Color.rgb(35, 43, 54)
        val rowOutline = Color.rgb(56, 68, 84)

        val panelBackground = GradientDrawable().apply {
            setColor(panelColor)
            cornerRadius = dp(20).toFloat()
        }

        val listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        val animatedRows = mutableListOf<View>()

        if (entries.isEmpty()) {
            listContainer.addView(
                TextView(this).apply {
                    text = getString(R.string.no_scores_yet)
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    gravity = Gravity.CENTER
                    setPadding(dp(16), dp(40), dp(16), dp(40))
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        } else {
            entries.forEachIndexed { index, entry ->
                val row = createLeaderboardRow(
                    rank = index + 1,
                    entry = entry,
                    rowColor = rowColor,
                    rowOutline = rowOutline,
                    isPlayerBest = entry.isOwnedByPlayer
                )
                listContainer.addView(row)
                animatedRows += row
            }
        }

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                listContainer,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        val subtitle = if (entries.isEmpty()) {
            getString(R.string.leaderboard_empty_subtitle)
        } else {
            resources.getQuantityString(R.plurals.leaderboard_best_runs, entries.size, entries.size)
        }

        val header = createDialogHeader(getString(R.string.leaderboard_title), subtitle)

        val body = FrameLayout(this).apply {
            background = panelBackground
            addView(
                scrollView,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            )
        }

        val okButton = createDialogActionButton(getString(R.string.ok)) {}

        val containerBackground = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(24).toFloat()
        }

        val maxContainerHeight = (resources.displayMetrics.heightPixels * 0.85).toInt()

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = containerBackground
            clipToOutline = true
            outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
            setPadding(dp(20), 0, dp(20), dp(20))
            addView(header)
            addView(
                body,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    0,
                    1f
                )
            )
            addView(okButton.apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(16)
            })
        }

        val wrapper = FrameLayout(this).apply {
            addView(
                container,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    height = maxContainerHeight
                }
            )
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(wrapper)
            .create()

        okButton.setOnClickListener { dialog.dismiss() }
        showCenteredDialog(dialog)
        animatedRows.take(3).forEachIndexed { index, row ->
            row.alpha = 0f
            row.translationY = dp(18).toFloat()
            row.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(index * 70L)
                .setDuration(220L)
                .start()
        }
    }

    private fun showAboutDialog() {
        val bgColor = Color.rgb(16, 20, 24)
        val panelColor = Color.rgb(26, 31, 38)
        val linkColor = Color.rgb(100, 160, 255)

        val panelBackground = GradientDrawable().apply {
            setColor(panelColor)
            cornerRadius = dp(20).toFloat()
        }

        fun createLinkButton(text: String, url: String): TextView {
            return TextView(this).apply {
                this.text = text
                setTextColor(linkColor)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(8))
                setOnClickListener {
                    startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
                }
            }
        }

        val header = createDialogHeader(getString(R.string.about_title))

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = panelBackground
            setPadding(dp(24), dp(24), dp(24), dp(24))

            addView(TextView(context).apply {
                text = buildWordmark()
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                typeface = displayTypeface
                gravity = Gravity.CENTER
                setPadding(0, 0, 0, dp(16))
            })

            addView(createLinkButton(getString(R.string.website_label), "https://helltar.com"))
            addView(createLinkButton(getString(R.string.github_label), "https://github.com/Helltar/catchrect-android"))

            val playerId = getOrCreateLocalPlayerId()
            addView(TextView(context).apply {
                text = getString(R.string.player_id_label, playerId)
                setTextColor(Color.argb(120, 255, 255, 255))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, 0)
                setOnClickListener {
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Player ID", playerId))
                    Toast.makeText(context, getString(R.string.player_id_copied), Toast.LENGTH_SHORT).show()
                }
            })
        }

        val okButton = createDialogActionButton(getString(R.string.ok)) {}

        val containerBackground = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(24).toFloat()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = containerBackground
            setPadding(dp(20), 0, dp(20), dp(20))
            addView(header)
            addView(
                body,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
            addView(okButton.apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(16)
            })
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(container)
            .create()

        okButton.setOnClickListener { dialog.dismiss() }
        showCenteredDialog(dialog)
    }

    private fun showGameMessageDialog(
        title: String,
        message: String,
        accentColor: Int,
        onDismiss: (() -> Unit)? = null
    ) {
        val bgColor = Color.rgb(16, 20, 24)
        val panelColor = Color.rgb(26, 31, 38)
        val panelBackground = GradientDrawable().apply {
            setColor(panelColor)
            cornerRadius = dp(20).toFloat()
        }

        val titleView = TextView(this).apply {
            text = title
            setTextColor(accentColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = displayTypeface
            gravity = Gravity.CENTER
        }

        val messageView = TextView(this).apply {
            text = message
            setTextColor(Color.argb(220, 255, 255, 255))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            setLineSpacing(0f, 1.1f)
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = panelBackground
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(titleView)
            addView(
                messageView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(12)
                }
            )
        }

        val containerBackground = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(24).toFloat()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = containerBackground
            setPadding(dp(20), 0, dp(20), dp(20))
            addView(
                body,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(20)
                }
            )
        }

        val dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(container)
            .create()

        dialog.setOnDismissListener { onDismiss?.invoke() }
        body.setOnClickListener { dialog.dismiss() }
        container.setOnClickListener { dialog.dismiss() }
        showCenteredDialog(dialog)

        body.alpha = 0f
        body.translationY = dp(16).toFloat()
        body.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(180L)
            .start()
    }

    private fun showCenteredDialog(dialog: AlertDialog) {
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            setGravity(Gravity.CENTER)
            val screenWidth = resources.displayMetrics.widthPixels
            val dialogWidth = (screenWidth * 0.88).toInt().coerceAtMost(dp(420))
            setLayout(dialogWidth, android.view.WindowManager.LayoutParams.WRAP_CONTENT)
        }
    }

    private fun createLoadingDialog(message: String): AlertDialog {
        val bgColor = Color.rgb(16, 20, 24)
        val panelColor = Color.rgb(26, 31, 38)
        val accentColor = Color.rgb(214, 168, 48)

        val panelBackground = GradientDrawable().apply {
            setColor(panelColor)
            cornerRadius = dp(20).toFloat()
        }

        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(accentColor)
        }

        val messageView = TextView(this).apply {
            text = message
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            gravity = Gravity.CENTER
        }

        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = panelBackground
            setPadding(dp(24), dp(24), dp(24), dp(24))
            addView(progressBar)
            addView(
                messageView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp(16)
                }
            )
        }

        val containerBackground = GradientDrawable().apply {
            setColor(bgColor)
            cornerRadius = dp(24).toFloat()
        }

        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = containerBackground
            setPadding(dp(20), dp(20), dp(20), dp(20))
            addView(
                body,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        return AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_NoActionBar)
            .setView(container)
            .create()
            .apply {
                setCancelable(false)
                setCanceledOnTouchOutside(false)
            }
    }

    private fun createMenuView(): LinearLayout {
        val rootLayoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL

            addView(TextView(context).apply {
                text = buildWordmark()
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 46f)
                typeface = displayTypeface
                letterSpacing = 0.04f
                gravity = Gravity.CENTER
                setShadowLayer(dp(20).toFloat(), 0f, 0f, Color.rgb(41, 98, 255))
                // Bottom padding leaves room for the glow so it isn't clipped by the view bounds.
                setPadding(0, dp(20), 0, dp(20))
            })

            // Accent bar echoing the paddle that catches the falling squares.
            addView(View(context).apply {
                background = GradientDrawable().apply {
                    setColor(Color.rgb(41, 98, 255))
                    cornerRadius = dp(3).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(dp(72), dp(6)).apply {
                    topMargin = dp(6)
                }
            })

            bestScoreBadge = TextView(context).apply {
                setTextColor(Color.rgb(214, 168, 48))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                typeface = displayTypeface
                letterSpacing = 0.08f
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    setColor(Color.argb(28, 214, 168, 48))
                    cornerRadius = dp(12).toFloat()
                    setStroke(dp(1), Color.argb(120, 214, 168, 48))
                }
                setPadding(dp(16), dp(7), dp(16), dp(7))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(18) }
            }
            addView(bestScoreBadge)

            addView(createMenuButton(getString(R.string.play), accent = true) { startGame() }.apply {
                setPadding(dp(24), dp(20), dp(24), dp(20))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(34)
            })

            addView(createMenuButton(getString(R.string.leaderboard_title)) { showLeaderboard() }.apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(14)
            })

            pendingSubmitButton = createMenuButton(getString(R.string.submit_pending_button, 0)) {
                retryPendingSubmission()
            }.apply {
                (layoutParams as LinearLayout.LayoutParams).topMargin = dp(14)
                visibility = View.GONE
            }
            addView(pendingSubmitButton)

            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    dp(260),
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    topMargin = dp(14)
                }

                addView(
                    createToggleChip(getString(R.string.setting_sound), isSoundEnabled) { chip ->
                        isSoundEnabled = !isSoundEnabled
                        gameView.setSoundEnabled(isSoundEnabled)
                        prefs.edit { putBoolean(PREF_SOUND_ENABLED, isSoundEnabled) }
                        styleToggleChip(chip, isSoundEnabled)
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                )

                addView(
                    createToggleChip(getString(R.string.setting_pure_black), isOledBackground) { chip ->
                        isOledBackground = !isOledBackground
                        gameView.setOledBackground(isOledBackground)
                        prefs.edit { putBoolean(PREF_OLED_BACKGROUND, isOledBackground) }
                        styleToggleChip(chip, isOledBackground)
                        applyMenuBackground(menuView)
                    },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        marginStart = dp(12)
                    }
                )
            })

            addView(TextView(context).apply {
                text = getString(R.string.about)
                setTextColor(Color.argb(150, 255, 255, 255))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
                typeface = displayTypeface
                letterSpacing = 0.03f
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setPadding(dp(20), dp(14), dp(20), dp(14))
                setOnClickListener { showAboutDialog() }
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { topMargin = dp(18) }
            })
        }

        refreshBestScoreBadge()
        refreshPendingSubmissionButton()

        val scrollView = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            addView(
                content,
                FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }

        return LinearLayout(this).apply {
            layoutParams = rootLayoutParams
            orientation = LinearLayout.VERTICAL
            applyMenuBackground(this)
            addView(
                scrollView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.MATCH_PARENT
                )
            )
        }
    }

    private fun createMenuButton(label: String, accent: Boolean = false, onClick: () -> Unit): TextView {
        val btnColor = if (accent) Color.rgb(41, 98, 255) else Color.rgb(40, 50, 65)
        val rippleColor = if (accent) Color.rgb(90, 140, 255) else Color.rgb(70, 85, 110)

        val shape = GradientDrawable().apply {
            setColor(btnColor)
            cornerRadius = dp(14).toFloat()
        }

        val ripple = RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            shape,
            null
        )

        return TextView(this).apply {
            text = label
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            typeface = displayTypeface
            letterSpacing = 0.03f
            gravity = Gravity.CENTER
            background = ripple
            isClickable = true
            isFocusable = true
            setPadding(dp(24), dp(16), dp(24), dp(16))
            layoutParams =
                LinearLayout.LayoutParams(dp(260), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }
            setOnClickListener { onClick() }
        }
    }

    private fun buildWordmark(): CharSequence {
        val name = getString(R.string.app_name)
        val splitAt = name.indexOf("Rect").let { if (it >= 0) it else name.length }
        return SpannableString(name).apply {
            setSpan(
                ForegroundColorSpan(Color.rgb(120, 160, 255)),
                splitAt,
                name.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    private fun refreshBestScoreBadge() {
        val badge = bestScoreBadge ?: return
        val best = prefs.getInt(PREF_BEST_SCORE, 0)
        if (best > 0) {
            badge.text = getString(R.string.menu_best_badge, best)
            badge.visibility = View.VISIBLE
        } else {
            badge.visibility = View.GONE
        }
    }

    private fun refreshPendingSubmissionButton() {
        val button = pendingSubmitButton ?: return
        val pending = PendingSubmissionStore.load(this)
        if (pending != null) {
            button.text = getString(R.string.submit_pending_button, pending.score)
            button.visibility = View.VISIBLE
        } else {
            button.visibility = View.GONE
        }
    }

    private fun createToggleChip(
        feature: String,
        isActive: Boolean,
        onToggle: (TextView) -> Unit
    ): TextView {
        val chip = TextView(this).apply {
            text = feature
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            typeface = displayTypeface
            letterSpacing = 0.02f
            gravity = Gravity.CENTER
            maxLines = 1
            isClickable = true
            isFocusable = true
            setPadding(dp(12), dp(13), dp(12), dp(13))
        }
        styleToggleChip(chip, isActive)
        chip.setOnClickListener { onToggle(chip) }
        return chip
    }

    private fun styleToggleChip(chip: TextView, isActive: Boolean) {
        val shape = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            if (isActive) {
                setColor(Color.rgb(28, 48, 92))
                setStroke(dp(2), Color.rgb(41, 98, 255))
            } else {
                setColor(Color.rgb(40, 50, 65))
                setStroke(dp(2), Color.rgb(56, 68, 84))
            }
        }
        chip.background = RippleDrawable(
            ColorStateList.valueOf(Color.rgb(70, 85, 110)),
            shape,
            null
        )
        chip.setTextColor(if (isActive) Color.WHITE else Color.argb(150, 255, 255, 255))
    }

    private fun createDialogHeader(title: CharSequence, subtitle: CharSequence? = null): LinearLayout {
        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            typeface = displayTypeface
            gravity = Gravity.CENTER
        }

        // Same paddle accent bar as the main menu, tying the dialogs to it.
        val accentBar = View(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.rgb(41, 98, 255))
                cornerRadius = dp(3).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(5)).apply {
                topMargin = dp(12)
            }
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(24), dp(24), dp(18))
            addView(titleView)
            addView(accentBar)
            subtitle?.let {
                addView(TextView(this@MainActivity).apply {
                    text = it
                    setTextColor(Color.argb(180, 255, 255, 255))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                    gravity = Gravity.CENTER
                    setPadding(0, dp(12), 0, 0)
                })
            }
        }
    }

    private fun createDialogActionButton(label: String, onClick: () -> Unit): LinearLayout {
        val btnColor = Color.rgb(40, 50, 65)
        val rippleColor = Color.rgb(70, 85, 110)

        val shape = GradientDrawable().apply {
            setColor(btnColor)
            cornerRadius = dp(14).toFloat()
        }

        val ripple = RippleDrawable(
            ColorStateList.valueOf(rippleColor),
            shape,
            null
        )

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = ripple
            isClickable = true
            isFocusable = true
            setPadding(dp(24), dp(16), dp(24), dp(16))
            layoutParams =
                LinearLayout.LayoutParams(dp(260), LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                }

            addView(TextView(context).apply {
                text = label
                setTextColor(Color.WHITE)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = displayTypeface
                letterSpacing = 0.03f
                gravity = Gravity.CENTER
                includeFontPadding = false
            })

            setOnClickListener { onClick() }
        }
    }

    private fun createLeaderboardRow(
        rank: Int,
        entry: LeaderboardEntry,
        rowColor: Int,
        rowOutline: Int,
        isPlayerBest: Boolean
    ): LinearLayout {
        val highlightColor = Color.rgb(67, 160, 71)
        val rankBadge = GradientDrawable().apply {
            setColor(rankBadgeColor(rank))
            cornerRadius = dp(12).toFloat()
        }

        val scoreBadge = GradientDrawable().apply {
            setColor(Color.rgb(50, 61, 76))
            cornerRadius = dp(14).toFloat()
        }

        val rowBackground = GradientDrawable().apply {
            setColor(if (isPlayerBest) Color.rgb(40, 62, 48) else rowColor)
            cornerRadius = dp(16).toFloat()
            setStroke(dp(2), if (isPlayerBest) highlightColor else rowOutline)
        }

        val rankView = TextView(this).apply {
            text = rankBadgeText(rank)
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            gravity = Gravity.CENTER
            background = rankBadge
        }

        val nameView = TextView(this).apply {
            text = entry.playerName
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            setOnClickListener {
                Toast.makeText(context, entry.playerName, Toast.LENGTH_SHORT).show()
            }
        }

        val nameRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(
                nameView,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }

        val scoreView = TextView(this).apply {
            text = "${entry.score}"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER
            background = scoreBadge
            setPadding(dp(14), dp(8), dp(14), dp(8))
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = rowBackground
            setPadding(dp(14), dp(12), dp(14), dp(12))
            addView(
                rankView,
                LinearLayout.LayoutParams(dp(36), dp(36))
            )
            addView(
                nameRow,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(12)
                    marginEnd = dp(12)
                }
            )
            addView(scoreView)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10)
            }
        }
    }

    private fun rankBadgeText(rank: Int): String = when (rank) {
        1 -> "1"
        2 -> "2"
        3 -> "3"
        else -> rank.toString()
    }

    private fun rankBadgeColor(rank: Int): Int = when (rank) {
        1 -> Color.rgb(214, 168, 48)
        2 -> Color.rgb(128, 143, 164)
        3 -> Color.rgb(171, 113, 72)
        else -> Color.rgb(70, 92, 118)
    }

    private fun soundLabel() = if (isSoundEnabled) {
        getString(R.string.sound_on)
    } else {
        getString(R.string.sound_off)
    }

    private fun oledLabel() = if (isOledBackground) {
        getString(R.string.pure_black_on)
    } else {
        getString(R.string.pure_black_off)
    }

    private fun applyMenuBackground(view: View) {
        if (isOledBackground) {
            view.background = null
            view.setBackgroundColor(Color.BLACK)
        } else {
            view.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                intArrayOf(Color.rgb(26, 32, 48), Color.rgb(15, 18, 26), Color.rgb(9, 11, 16))
            ).apply { setDither(true) }
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun normalizePlayerName(name: String): String = name.trim().replace(Regex("\\s+"), " ")

    private fun getOrCreateLocalPlayerId(): String {
        val existing = prefs.getString(PREF_LOCAL_PLAYER_ID, null)
        if (!existing.isNullOrBlank()) return existing

        val generated = UUID.randomUUID().toString()
        prefs.edit { putString(PREF_LOCAL_PLAYER_ID, generated) }
        return generated
    }

    companion object {
        private const val PREF_SOUND_ENABLED = "sound_enabled"
        private const val PREF_PLAYER_NAME = "player_name"
        private const val PREF_BEST_SCORE = "best_score"
        private const val PREF_LOCAL_PLAYER_ID = "local_player_id"
        private const val PREF_OLED_BACKGROUND = "oled_background"
    }
}
