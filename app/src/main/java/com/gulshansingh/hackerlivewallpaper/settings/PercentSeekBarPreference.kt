package com.gulshansingh.hackerlivewallpaper.settings

import android.content.Context
import android.util.AttributeSet

class PercentSeekBarPreference(context: Context, attrs: AttributeSet) : SeekBarPreference(context, attrs) {

    override fun transform(value: Int): String {
        return value.toString() + "%"
    }
}
