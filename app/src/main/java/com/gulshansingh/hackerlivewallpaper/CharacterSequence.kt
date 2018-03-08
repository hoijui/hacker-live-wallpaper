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
 * A class that stores a list of characters. The first character is removed and a new character is
 * appended at a fixed interval. Calling the draw method of displays the character
 * sequence vertically on the screen. Every time a character is changed, the position
 * of the sequence on the screen will be shifted downward. Moving past the
 * bottom of the screen will cause the sequence to be placed above the screen
 *
 * @author Gulshan Singh
 */
class CharacterSequence(x: Int) {

    /** The characters this sequence stores  */
    private val characters = ArrayDeque<String>()

    /** A variable used for all operations needing random numbers  */
    private val r = Random()

    /** The scheduled operation for changing a character and shifting downwards  */
    private var future: ScheduledFuture<*>? = null

    /** The position to draw the sequence at on the screen  */
    internal var x: Float = 0.toFloat()
    internal var y: Float = 0.toFloat()

    /** True when the CharacterSequence should be paused  */
    private var pause = false

    /** Describes the style of the sequence  */
    private val style = Style()
    private var curChar = 0

    /**
     * A runnable that changes the character, moves the sequence down, and reschedules
     * its execution
     */
    private val changeCharacterRunnable = Runnable {
        changeCharacter()
        y += style.fallingSpeed.toFloat()
        if (y > height) {
            reset()
        }
    }

    private val nextCharacter: String
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
            /** The default speed at which characters should be changed  */
            private const val DEFAULT_CHANGE_BIT_SPEED = 100

            /** The maximum alpha a character can have  */
            private const val MAX_ALPHA = 240

            var changeCharacterSpeed: Int = 0
            var numCharacters: Int = 0
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

                numCharacters = if (isRandom) {
                    preferences.getInt(KEY_NUM_BITS,
                            R.integer.default_num_characters)
                } else {
                    charSet!!.length
                }
                color = preferences
                        .getInt(KEY_BIT_COLOR, R.color.default_character_color)
                defaultTextSize = preferences.getInt(KEY_TEXT_SIZE,
                        R.integer.default_text_size)

                val changeCharacterSpeedMultiplier = 100 / preferences.getDouble(
                        KEY_CHANGE_BIT_SPEED, R.integer.default_change_character_speed)
                val fallingSpeedMultiplier = preferences.getDouble(
                        KEY_FALLING_SPEED, R.integer.default_falling_speed) / 100

                changeCharacterSpeed = (DEFAULT_CHANGE_BIT_SPEED * changeCharacterSpeedMultiplier).toInt()
                defaultFallingSpeed = (defaultTextSize * fallingSpeedMultiplier).toInt()

                depthEnabled = preferences.getBoolean(KEY_ENABLE_DEPTH, true)

                alphaIncrement = MAX_ALPHA / numCharacters
                initialY = -1 * defaultTextSize * numCharacters
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
        for (i in 0 until Style.numCharacters) {
            if (isRandom) {
                characters.add(getRandomCharacter(r))
            } else {
                // TODO: Disable numCharacters in settings if custom is selected
                characters.addFirst(nextCharacter)
            }
        }
        this.x = x.toFloat()
        reset()
    }

    /**
     * Pauses the CharacterSequence by cancelling the ScheduledFuture
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
     * Unpauses the CharacterSequence by scheduling CharacterSequences on the screen to
     * immediately start, and scheduling CharacterSequences off the screen to start
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
     * Schedules the changeCharacterRunnable with the specified delay, cancelling the
     * previous scheduled future
     *
     * @param delay
     * the delay in milliseconds, less than 6000 milliseconds by default
     */
    private fun scheduleThread(delay: Int = r.nextInt(6000)) {
        if (future != null)
            future!!.cancel(true)
        future = scheduler.scheduleAtFixedRate(changeCharacterRunnable, delay.toLong(),
                Style.changeCharacterSpeed.toLong(), TimeUnit.MILLISECONDS)
    }

    /** Shifts the characters back by one and adds a new character to the end  */
    @Synchronized
    private fun changeCharacter() {
        if (isRandom) {
            characters.removeFirst()
            characters.addLast(getRandomCharacter(r))
        }
    }

    /**
     * Gets a new random character
     *
     * @param r
     * the [Random] object to use
     * @return A new random character as a [String]
     */
    private fun getRandomCharacter(r: Random): String {
        return symbols!![r.nextInt(symbols!!.size)]
    }

    /**
     * Draws this CharacterSequence on the screen
     *
     * @param canvas
     * the [Canvas] on which to draw the CharacterSequence
     */
    @Synchronized
    fun draw(canvas: Canvas) {
        // TODO Can the get and set alphas be optimized?
        val paint = style.paint
        var characterY = y
        paint.alpha = Style.alphaIncrement
        for (i in characters.indices) {
            canvas.drawText(characters.get(i), x, characterY, paint)
            characterY += style.textSize.toFloat()
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
         * Configures any CharacterSequences parameters requiring the application context
         *
         * @param context
         * the application context
         */
        fun configure(context: Context) {
            Style.initParameters(context)
        }

        /**
         * Configures the CharacterSequence based on the display
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
         * Gets the width the CharacterSequence would be on the screen
         *
         * @return the width of the CharacterSequence
         */
        val width: Float
            get() {
                val paint = Paint()
                paint.textSize = Style.defaultTextSize.toFloat()
                return paint.measureText("0")
            }
    }
}
