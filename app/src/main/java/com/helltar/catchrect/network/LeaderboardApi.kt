package com.helltar.catchrect.network

import com.helltar.catchrect.BuildConfig
import com.helltar.catchrect.game.engine.GameReplay
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.compression.ContentEncodingConfig
import io.ktor.client.plugins.compression.compress
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

class LeaderboardApi(private val client: HttpClient, private val baseUrl: String) {

    suspend fun submitScore(playerName: String, playerId: String, replay: GameReplay): HttpStatusCode {
        val response =
            client.post("$baseUrl/submit") {
                contentType(ContentType.Application.Json)
                compress("gzip")
                setBody(replay.toSubmitScoreRequest(playerName, playerId))
            }

        return response.status
    }

    suspend fun getLeaderboard(playerId: String): List<LeaderboardEntry> =
        client.get("$baseUrl/leaderboard") { parameter("playerId", playerId) }.body()

    companion object {
        private const val BASE_URL = BuildConfig.LEADERBOARD_BASE_URL
        private const val REQUEST_TIMEOUT_MS = 60_000L

        val instance: LeaderboardApi by lazy { create() }

        internal fun create(baseUrl: String = BASE_URL): LeaderboardApi =
            LeaderboardApi(client = createHttpClient(), baseUrl = baseUrl)

        private fun createHttpClient(): HttpClient =
            HttpClient(CIO) {
                expectSuccess = false

                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            explicitNulls = false
                        }
                    )
                }

                install(ContentEncoding) {
                    mode = ContentEncodingConfig.Mode.CompressRequest
                    gzip()
                }

                install(HttpTimeout) {
                    requestTimeoutMillis = REQUEST_TIMEOUT_MS
                    connectTimeoutMillis = REQUEST_TIMEOUT_MS
                    socketTimeoutMillis = REQUEST_TIMEOUT_MS
                }
            }
    }
}
