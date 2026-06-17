package com.helltar.catchrect.network

import android.content.Context
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists a single unsent [SubmitScoreRequest] to disk so a hard-won score
 * survives a failed or interrupted submission (server unreachable, app closed)
 * and can be retried later from the menu. Only the best unsent run is kept.
 */
object PendingSubmissionStore {

    private const val FILE_NAME = "pending_submission.json"
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    /** Stores [request] only if it beats whatever unsent run is already saved. */
    fun saveIfBetter(context: Context, request: SubmitScoreRequest) {
        val existing = load(context)
        if (existing == null || request.score > existing.score) {
            runCatching { file(context).writeText(json.encodeToString(request)) }
        }
    }

    fun load(context: Context): SubmitScoreRequest? {
        val file = file(context)
        if (!file.exists()) return null
        return runCatching { json.decodeFromString<SubmitScoreRequest>(file.readText()) }.getOrNull()
    }

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    /**
     * Clears the saved run once a score of at least [score] is confirmed on the
     * server. A still-higher unsent run is kept so it can be submitted too.
     */
    fun clearIfNotBetterThan(context: Context, score: Int) {
        val existing = load(context) ?: return
        if (existing.score <= score) clear(context)
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}
