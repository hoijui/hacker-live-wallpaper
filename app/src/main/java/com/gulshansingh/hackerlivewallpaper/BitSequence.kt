package com.gulshansingh.hackerlivewallpaper

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.BlurMaskFilter
import android.graphics.BlurMaskFilter.Blur
import android.graphics.Canvas
import android.graphics.Paint
import android.preference.PreferenceManager

import com.gulshansingh.hackerlivewallpaper.settings.CharacterSetPreference
import com.gulshansingh.hackerlivewallpaper.thirdparty.ArrayDeque

import java.util.Random
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit

import com.gulshansingh.hackerlivewallpaper.SettingsActivity.Companion.KEY_BIT_COLOR
import com.gulshansingh.hackerlivewallpaper.SettingsActivity.Companion.KEY_CHANGE_BIT_SPEED
import com.gulshansingh.hackerlivewallpaper.SettingsActivity.Companion.KEY_ENABLE_DEPTH
import com.gulshansingh.hackerlivewallpaper.SettingsActivity.Companion.KEY_FALLING_SPEED
import com.gulshansingh.hackerlivewallpaper.SettingsActivity.Companion.KEY_NUM_BITS
import com.gulshansingh.hackerlivewallpaper.SettingsActivity.Companion.KEY_TEXT_SIZE

/**
 * A class that stores a list of bits. The first bit is removed and a new bit is
 * appended at a fixed interval. Calling the draw method of displays the bit
 * sequence vertically on the screen. Every time a bit is changed, the position
 * of the sequence on the screen will be shifted downward. Moving past the
 * bottom of the screen will cause the sequence to be placed above the screen
 *
 * @author Gulshan Singh
 */
class BitSequence(x: Int) {

    /** The bits this sequence stores  */
    private val bits = ArrayDeque<String>()

    /** A variable used for all operations needing random numbers  */
    private val r = Random()

    /** The scheduled operation for changing a bit and shifting downwards  */
    private var future: ScheduledFuture<*>? = null

    /** The position to draw the sequence at on the screen  */
    internal var x: Float = 0.toFloat()
    internal var y: Float = 0.toFloat()

    /** True when the BitSequence should be paused  */
    private var pause = false

    /** Describes the style of the sequence  */
    private val style = Style()
    private var curChar = 0

    /**
     * A runnable that changes the bit, moves the sequence down, and reschedules
     * its execution
     */
    private val changeBitRunnable = Runnable {
        changeBit()
        y += style.fallingSpeed.toFloat()
        if (y > height) {
            reset()
        }
    }

    private val nextBit: String
        get() {
            val s = Character.toString(charSet!![curChar])
            curChar = (curChar + 1) % charSet!!.length
            return s
        }

    private class Style {

        var textSize: Int = 0
        var fallingSpeed: Int = 0
        var maskFilter: BlurMaskFilter? = null

        var paint = Paint()

        init {
            paint.color = color
        }

        fun createPaint() {
            paint.textSize = textSize.toFloat()
            paint.maskFilter = maskFilter
        }

        private class PreferenceUtility(context: Context) {
            private val preferences: SharedPreferences = PreferenceManager
                    .getDefaultSharedPreferences(context)
            private val res: Resources = context.resources

            fun getInt(key: String, defaultId: Int): Int {
                return preferences.getInt(key, res.getInteger(defaultId))
            }

            fun getDouble(key: String, defaultId: Int): Double {
                return preferences.getInt(key,
                        res.getInteger(defaultId)).toDouble()
            }

            fun getBoolean(key: String, defaultVal: Boolean): Boolean {
                return preferences.getBoolean(key, defaultVal)
            }
        }

        companion object {
            /** The default speed at which bits should be changed  */
            private const val DEFAULT_CHANGE_BIT_SPEED = 100

            /** The maximum alpha a bit can have  */
            private const val MAX_ALPHA = 240

            var changeBitSpeed: Int = 0
            var numBits: Int = 0
            private var color: Int = 0
            var defaultTextSize: Int = 0
            var defaultFallingSpeed: Int = 0
            var depthEnabled: Boolean = false

            var alphaIncrement: Int = 0
            var initialY: Int = 0

            fun initParameters(context: Context) {
                val sp = PreferenceManager
                        .getDefaultSharedPreferences(context)
                val charSetName = sp.getString("character_set_name", "Binary")
                isRandom = true
                if (charSetName == "Binary") {
                    charSet = CharacterSetPreference.BINARY_CHAR_SET
                } else if (charSetName == "Matrix") {
                    charSet = CharacterSetPreference.MATRIX_CHAR_SET
                } else if (charSetName == "Custom (random characters)") {
                    charSet = sp.getString("custom_character_set", "")
                    if (charSet!!.isEmpty()) {
                        throw RuntimeException("Character set length can't be 0")
                    }
                } else if (charSetName == "Custom (exact text)") {
                    isRandom = false
                    charSet = sp.getString("custom_character_string", "")
                    if (charSet!!.isEmpty()) {
                        throw RuntimeException("Character set length can't be 0")
                    }
                } else {
                    if (charSetName != "Custom") { // Legacy character set
                        throw RuntimeException("Invalid character set " + charSetName!!)
                    } else {
                        sp.edit().putString("character_set_name", "Custom (random characters)")
                                .commit()
                        charSet = sp.getString("custom_character_set", "")
                        if (charSet!!.isEmpty()) {
                            throw RuntimeException("Character set length can't be 0")
                        }
                    }
                }
                symbols = charSet!!.split("(?!^)".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                val preferences = PreferenceUtility(context)

                numBits = if (isRandom) {
                    preferences.getInt(KEY_NUM_BITS,
                            R.integer.default_num_bits)
                } else {
                    charSet!!.length
                }
                color = preferences
                        .getInt(KEY_BIT_COLOR, R.color.default_bit_color)
                defaultTextSize = preferences.getInt(KEY_TEXT_SIZE,
                        R.integer.default_text_size)

                val changeBitSpeedMultiplier = 100 / preferences.getDouble(
                        KEY_CHANGE_BIT_SPEED, R.integer.default_change_bit_speed)
                val fallingSpeedMultiplier = preferences.getDouble(
                        KEY_FALLING_SPEED, R.integer.default_falling_speed) / 100

                changeBitSpeed = (DEFAULT_CHANGE_BIT_SPEED * changeBitSpeedMultiplier).toInt()
                defaultFallingSpeed = (defaultTextSize * fallingSpeedMultiplier).toInt()

                depthEnabled = preferences.getBoolean(KEY_ENABLE_DEPTH, true)

                alphaIncrement = MAX_ALPHA / numBits
                initialY = -1 * defaultTextSize * numBits
            }
        }
    }

    /**
     * Resets the sequence by repositioning it, resetting its visual
     * characteristics, and rescheduling the thread
     */
    private fun reset() {
        y = Style.initialY.toFloat()
        setDepth()
        style.createPaint()
        scheduleThread()
    }

    private fun setDepth() {
        if (!Style.depthEnabled) {
            style.textSize = Style.defaultTextSize
            style.fallingSpeed = Style.defaultFallingSpeed
        } else {
            val factor = r.nextDouble() * (1 - .8) + .8
            style.textSize = (Style.defaultTextSize * factor).toInt()
            style.fallingSpeed = (Style.defaultFallingSpeed * Math.pow(
                    factor, 4.0)).toInt()

            when {
                factor > .93 -> style.maskFilter = regularFilter
                factor in .87 .. .93 -> style.maskFilter = slightBlurFilter
                else -> style.maskFilter = blurFilter
            }
        }
    }

    init {
        curChar = 0
        for (i in 0 until Style.numBits) {
            if (isRandom) {
                bits.add(getRandomBit(r))
            } else {
                // TODO: Disable numBits in settings if custom is selected
                bits.addFirst(nextBit)
            }
        }
        this.x = x.toFloat()
        reset()
    }

    /**
     * Pauses the BitSequence by cancelling the ScheduledFuture
     */
    fun pause() {
        if (!pause) {
            if (future != null) {
                future!!.cancel(true)
            }
            pause = true
        }
    }

    fun stop() {
        pause()
    }

    /**
     * Unpauses the BitSequence by scheduling BitSequences on the screen to
     * immediately start, and scheduling BitSequences off the screen to start
     * after some delay
     */
    fun unpause() {
        if (pause) {
            if (y <= Style.initialY + style.textSize || y > height) {
                scheduleThread()
            } else {
                scheduleThread(0)
            }
            pause = false
        }
    }

    /**
     * Schedules the changeBitRunnable with the specified delay, cancelling the
     * previous scheduled future
     *
     * @param delay
     * the delay in milliseconds, less than 6000 milliseconds by default
     */
    private fun scheduleThread(delay: Int = r.nextInt(6000)) {
        if (future != null)
            future!!.cancel(true)
        future = scheduler.scheduleAtFixedRate(changeBitRunnable, delay.toLong(),
                Style.changeBitSpeed.toLong(), TimeUnit.MILLISECONDS)
    }

    /** Shifts the bits back by one and adds a new bit to the end  */
    @Synchronized
    private fun changeBit() {
        if (isRandom) {
            bits.removeFirst()
            bits.addLast(getRandomBit(r))
        }
    }

    /**
     * Gets a new random bit
     *
     * @param r
     * the [Random] object to use
     * @return A new random bit as a [String]
     */
    private fun getRandomBit(r: Random): String {
        return symbols!![r.nextInt(symbols!!.size)]
    }

    /**
     * Draws this BitSequence on the screen
     *
     * @param canvas
     * the [Canvas] on which to draw the BitSequence
     */
    @Synchronized
    fun draw(canvas: Canvas) {
        // TODO Can the get and set alphas be optimized?
        val paint = style.paint
        var bitY = y
        paint.alpha = Style.alphaIncrement
        for (i in bits.indices) {
            canvas.drawText(bits.get(i), x, bitY, paint)
            bitY += style.textSize.toFloat()
            paint.alpha = paint.alpha + Style.alphaIncrement
        }
    }

    companion object {

        /** The Mask to use for blurred text  */
        private val blurFilter = BlurMaskFilter(3f,
                Blur.NORMAL)

        /** The Mask to use for slightly blurred text  */
        private val slightBlurFilter = BlurMaskFilter(
                2f, Blur.NORMAL)

        /** The Mask to use for regular text  */
        private val regularFilter: BlurMaskFilter? = null

        /** The height of the screen  */
        private var height: Int = 0

        private val scheduler = Executors
                .newSingleThreadScheduledExecutor()

        /** The characters to use in the sequence  */
        private var symbols: Array<String>? = null
        private var charSet: String? = null
        private var isRandom = true

        /**
         * Configures any BitSequences parameters requiring the application context
         *
         * @param context
         * the application context
         */
        fun configure(context: Context) {
            Style.initParameters(context)
        }

        /**
         * Configures the BitSequence based on the display
         *
         * @param width
         * the width of the screen
         * @param height
         * the height of the screen
         */
        fun setScreenDim(width: Int, height: Int) {
            this.height = height
        }

        /**
         * Gets the width the BitSequence would be on the screen
         *
         * @return the width of the BitSequence
         */
        val width: Float
            get() {
                val paint = Paint()
                paint.textSize = Style.defaultTextSize.toFloat()
                return paint.measureText("0")
            }
    }
}
