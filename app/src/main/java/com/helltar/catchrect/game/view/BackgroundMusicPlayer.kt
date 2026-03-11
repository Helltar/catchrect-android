package com.helltar.catchrect.game.view

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.min
import kotlin.math.sin

class BackgroundMusicPlayer {

    @Volatile
    var muted: Boolean = false

    @Volatile
    var score: Int = 0

    private val sampleRate = 22050
    private val playing = AtomicBoolean(false)
    private var thread: Thread? = null

    private val baseBpm = 128
    private val maxBpm = 180
    private val scorePerBpmStep = 10
    private val bpmPerStep = 1

    fun start() {
        if (playing.getAndSet(true)) return
        score = 0
        thread = Thread({
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setSampleRate(sampleRate)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(bufferSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            track.play()

            var position = 0L

            while (playing.get()) {
                val bpm = currentBpm()
                val beatSamples = 60.0 * sampleRate / bpm
                val barSamples = (beatSamples * 4).toInt()
                val buffer = ShortArray(barSamples)

                generateBar(buffer, barSamples, beatSamples, position)

                if (muted) {
                    buffer.fill(0)
                }

                try {
                    track.write(buffer, 0, barSamples)
                } catch (_: Exception) {
                    break
                }
                position += barSamples
            }

            track.stop()
            track.release()
        }, "BackgroundMusicThread").also { it.start() }
    }

    fun stop() {
        if (!playing.getAndSet(false)) return
        thread?.join(500)
        thread = null
    }

    private fun currentBpm(): Int {
        return min(baseBpm + (score / scorePerBpmStep) * bpmPerStep, maxBpm)
    }

    private fun generateBar(buffer: ShortArray, barSamples: Int, beatSamples: Double, position: Long) {
        val kickFreq = 55.0
        val hihatDur = (sampleRate * 0.03).toInt()
        val kickDur = (sampleRate * 0.12).toInt()
        val bassDur = (sampleRate * 0.15).toInt()

        // bass notes per beat (simple pattern)
        val bassNotes = doubleArrayOf(82.41, 98.0, 110.0, 98.0) // E2, G2-ish, A2, G2-ish

        for (i in 0 until barSamples) {
            val globalSample = position + i
            val posInBar = i.toDouble()
            val beat = (posInBar / beatSamples).toInt()
            val posInBeat = (posInBar - beat * beatSamples).toInt()

            var sample = 0.0

            // Kick on every beat
            if (posInBeat < kickDur) {
                val t = posInBeat.toDouble() / sampleRate
                val env = 1.0 - posInBeat.toDouble() / kickDur
                val freqSweep = kickFreq + 150.0 * env * env
                sample += sin(2.0 * Math.PI * freqSweep * t) * env * env * 0.45
            }

            // Hi-hat on off-beats (between beats)
            val halfBeat = (beatSamples / 2).toInt()
            val posInHalfBeat = ((posInBar - beat * beatSamples) - halfBeat).toInt()
            if (posInHalfBeat in 0 until hihatDur) {
                val env = 1.0 - posInHalfBeat.toDouble() / hihatDur
                // noise-like hi-hat via high freq
                val noise = ((globalSample * 7919L % 65536L) / 32768.0 - 1.0)
                sample += noise * env * env * 0.12
            }

            // Also light hi-hat on beat for a bit of texture
            if (posInBeat < hihatDur) {
                val env = 1.0 - posInBeat.toDouble() / hihatDur
                val noise = ((globalSample * 13L % 65536L) / 65536.0 / 32768.0 - 1.0)
                sample += noise * env * env * 0.06
            }

            // Bass synth
            if (posInBeat < bassDur) {
                val t = posInBeat.toDouble() / sampleRate
                val env = 1.0 - posInBeat.toDouble() / bassDur
                val freq = bassNotes[beat % bassNotes.size]
                sample += sin(2.0 * Math.PI * freq * t) * env * 0.3
            }

            // Clamp and convert
            val clamped = sample.coerceIn(-1.0, 1.0)
            buffer[i] = (clamped * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
