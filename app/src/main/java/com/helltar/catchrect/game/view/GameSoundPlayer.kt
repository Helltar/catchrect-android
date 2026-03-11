package com.helltar.catchrect.game.view

import android.media.AudioManager
import android.media.ToneGenerator
import com.helltar.catchrect.game.model.CubeType
import java.util.concurrent.Executors

class GameSoundPlayer {

    @Volatile
    var muted: Boolean = false

    private val toneGenerator = ToneGenerator(AudioManager.STREAM_MUSIC, 85)
    private val soundExecutor = Executors.newSingleThreadExecutor()

    fun playCubeCatch(type: CubeType) {
        if (muted) return
        soundExecutor.execute {
            synchronized(toneGenerator) {
                when (type) {
                    CubeType.WHITE -> {
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_BEEP, 55)
                    }

                    CubeType.RED, CubeType.RED_FAST -> {
                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 120)
                        sleep(70)
                        toneGenerator.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 180)
                    }

                    CubeType.GREEN -> {
                        toneGenerator.startTone(ToneGenerator.TONE_PROP_ACK, 120)
                    }
                }
            }
        }
    }

    fun release() {
        soundExecutor.shutdownNow()
        toneGenerator.release()
    }

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        }
    }
}
