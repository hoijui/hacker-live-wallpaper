package com.gulshansingh.hackerlivewallpaper.settings

import android.content.Context
import android.preference.DialogPreference
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.view.View
import android.widget.SeekBar
import android.widget.TextView

import com.gulshansingh.hackerlivewallpaper.R
import com.gulshansingh.hackerlivewallpaper.Refreshable

abstract class SeekBarPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs), Refreshable {

    protected var currentVal: Int = 0

    /** The value the preference could possibly be once the user presses ok  */
    protected var possibleVal: Int = 0

    protected var maxVal = 100
    protected var minVal = 0

    private var defaultVal = 0

    init {

        val preferences = PreferenceManager.getDefaultSharedPreferences(context)

        var a = context.obtainStyledAttributes(attrs, R.styleable.SeekBarPreference)

        for (i in 0 until a.indexCount) {
            val attr = a.getIndex(i)
            when (attr) {
                R.styleable.SeekBarPreference_android_key -> key = a.getString(attr)
                R.styleable.SeekBarPreference_android_defaultValue -> defaultVal = a.getInteger(attr, defaultVal)
            }
        }
        a.recycle()

        currentVal = preferences.getInt(key, defaultVal)
        possibleVal = currentVal

        a = context
                .obtainStyledAttributes(attrs, R.styleable.SeekBarPreference)

        for (i in 0 until a.indexCount) {
            val attr = a.getIndex(i)
            when (attr) {
                R.styleable.SeekBarPreference_mymin -> minVal = a.getInteger(R.styleable.SeekBarPreference_mymin,
                        minVal)
                R.styleable.SeekBarPreference_mymax -> maxVal = a.getInteger(R.styleable.SeekBarPreference_mymax,
                        maxVal)
            }
        }
        a.recycle()

        // The seek bar must start at 0, so we have to scale max downward
        // and account for this later on
        maxVal -= minVal

        dialogLayoutResource = R.layout.preference_dialog_number_picker
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
        summary = transform(currentVal)
        dialogIcon = null
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        if (positiveResult) {
            if (key != null) {
                currentVal = possibleVal
                summary = transform(currentVal)
                persistInt(currentVal)
            }
        }
    }

    override fun refresh(context: Context) {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        currentVal = preferences.getInt(key, defaultVal)
        summary = transform(currentVal)
    }

    protected abstract fun transform(value: Int): String

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        val seekBar = view.findViewById(R.id.preference_seek_bar) as SeekBar
        seekBar.max = maxVal
        seekBar.progress = currentVal - minVal

        val progressView = view
                .findViewById(R.id.preference_seek_bar_progress) as TextView
        progressView.text = transform(currentVal)
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {

            override fun onProgressChanged(seekBar: SeekBar, progress: Int,
                                           fromUser: Boolean) {
                possibleVal = progress + minVal
                progressView.text = transform(possibleVal)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {}

            override fun onStopTrackingTouch(seekBar: SeekBar) {}
        })
    }
}
