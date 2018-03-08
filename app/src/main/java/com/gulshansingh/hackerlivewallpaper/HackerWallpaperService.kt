package com.gulshansingh.hackerlivewallpaper

import android.os.Handler
import android.preference.PreferenceManager
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder

import java.util.ArrayList

import com.gulshansingh.hackerlivewallpaper.SettingsActivity.Companion.KEY_BACKGROUND_COLOR

class HackerWallpaperService : WallpaperService() {

    private var r: Int = 0
    private var g: Int = 0
    private var b: Int = 0

    override fun onCreateEngine(): WallpaperService.Engine {
        return HackerWallpaperEngine()
    }

    inner class HackerWallpaperEngine : WallpaperService.Engine() {

        private val handler = Handler()
        private var visible = true

        /** The sequences to draw on the screen  */
        private val sequences = ArrayList<BitSequence>()

        private var width: Int = 0

        /**
         * The main runnable that is given to the Handler to draw the animation
         */
        private val drawRunnable = Runnable { draw() }

        /** Draws all of the bit sequences on the screen  */
        private fun draw() {
            if (visible) {
                // We can't have just one reset flag, because then the preview
                // would consume that flag and the actual wallpaper wouldn't be
                // reset
                if (previewReset && isPreview) {
                    previewReset = false
                    resetSequences(true)
                } else if (reset && !isPreview) {
                    reset = false
                    resetSequences(true)
                }
                val holder = surfaceHolder
                val c = holder.lockCanvas()
                try {
                    if (c != null) {
                        c.drawARGB(255, r, g, b)

                        for (i in sequences.indices) {
                            sequences[i].draw(c)
                        }
                    }
                } finally {
                    if (c != null) {
                        holder.unlockCanvasAndPost(c)
                    }
                }

                // Remove the runnable, and only schedule the next run if
                // visible
                handler.removeCallbacks(drawRunnable)

                handler.post(drawRunnable)
            } else {
                pause()
            }
        }

        // TODO: Not all of the sequences need to be cleared
        private fun resetSequences(clearAll: Boolean) {
            val preferences = PreferenceManager
                    .getDefaultSharedPreferences(applicationContext)
            val color = preferences.getInt(KEY_BACKGROUND_COLOR, 0)
            r = color shr 16 and 0xFF
            g = color shr 8 and 0xFF
            b = color shr 0 and 0xFF
            stop()
            val numSequences = (1.5 * width / BitSequence.width).toInt()

            var start = 0
            if (clearAll) {
                sequences.clear()
            } else {
                val size = sequences.size
                if (size > numSequences) {
                    sequences.subList(numSequences, size).clear()
                }
                start = size
            }
            for (i in start until numSequences) {
                sequences.add(BitSequence(
                        (i * BitSequence.width / 1.5).toInt()))
            }
            start()
        }

        private fun pause() {
            handler.removeCallbacks(drawRunnable)
            for (i in sequences.indices) {
                sequences[i].pause()
            }
        }

        private fun start() {
            handler.post(drawRunnable)
            for (i in sequences.indices) {
                sequences[i].unpause()
            }
        }

        private fun stop() {
            handler.removeCallbacks(drawRunnable)
            for (i in sequences.indices) {
                sequences[i].stop()
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            BitSequence.configure(applicationContext)
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            super.onSurfaceDestroyed(holder)
            pause()
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int,
                                      width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            this.width = width

            BitSequence.setScreenDim(width, height)

            // Initialize BitSequences
            resetSequences(false)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) {
                start()
            } else {
                pause()
            }
            this.visible = visible
        }
    }

    companion object {

        private var reset = false
        private var previewReset = false

        fun reset() {
            previewReset = true
            reset = true
        }
    }
}
