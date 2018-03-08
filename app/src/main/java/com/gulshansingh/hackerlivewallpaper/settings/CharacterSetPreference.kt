package com.gulshansingh.hackerlivewallpaper.settings

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
import android.os.Bundle
import android.preference.DialogPreference
import android.preference.PreferenceManager
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner

import com.gulshansingh.hackerlivewallpaper.R
import com.gulshansingh.hackerlivewallpaper.Refreshable

import java.util.Arrays

class CharacterSetPreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs), Refreshable {

    private var editText: EditText? = null
    private var spinner: Spinner? = null

    init {

        dialogLayoutResource = R.layout.preference_dialog_character_set
        setPositiveButtonText(android.R.string.ok)
        setNegativeButtonText(android.R.string.cancel)
        dialogIcon = null
    }

    override fun showDialog(state: Bundle) {
        super.showDialog(state)

        val characterSetName = sharedPreferences.getString("character_set_name", "Binary")
        updateEditText(characterSetName)
    }

    override fun onBindDialogView(view: View) {
        super.onBindDialogView(view)

        val sp = sharedPreferences
        val characterSetName = sp.getString("character_set_name", "Binary")

        val resources = view.context.resources
        val characterSets = Arrays.asList(*resources.getStringArray(R.array.character_sets))

        editText = view.findViewById(R.id.preference_character_set) as EditText
        editText!!.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable) {
                disablePosButton(s.length == 0)
            }
        })

        spinner = view.findViewById(R.id.preference_character_set_name) as Spinner
        spinner!!.setSelection(characterSets.indexOf(characterSetName))
        spinner!!.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View, position: Int, id: Long) {
                val characterSetName = spinner!!.selectedItem.toString()
                updateEditText(characterSetName)
            }

            override fun onNothingSelected(parent: AdapterView<*>) {}
        }
    }

    private fun disablePosButton(disable: Boolean) {
        val posButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
        if (disable) {
            posButton.isEnabled = false
        } else {
            posButton.isEnabled = true
        }
    }

    private fun updateEditText(characterSetName: String) {
        val characterSet: String?
        when (characterSetName) {
            "Binary" -> {
                characterSet = BINARY_CHAR_SET
                editText!!.isEnabled = false
            }
            "Matrix" -> {
                characterSet = MATRIX_CHAR_SET
                editText!!.isEnabled = false
            }
            "Custom (random characters)" -> {
                editText!!.isEnabled = true
                characterSet = sharedPreferences.getString("custom_character_set", "")
                disablePosButton(characterSet!!.length == 0)
            }
            "Custom (exact text)" -> {
                editText!!.isEnabled = true
                characterSet = sharedPreferences.getString("custom_character_string", "")
                disablePosButton(characterSet!!.length == 0)
            }
            else -> if (characterSetName != "Custom") { // Legacy charset name
                throw RuntimeException("Invalid character set $characterSetName")
            } else {
                sharedPreferences.edit().putString("character_set_name", "Custom (random characters)")
                        .commit()
                editText!!.isEnabled = true
                characterSet = sharedPreferences.getString("custom_character_set", "")
                disablePosButton(characterSet!!.length == 0)
            }
        }

        editText!!.setText(characterSet)
    }

    override fun refresh(context: Context) {
        val sp = PreferenceManager.getDefaultSharedPreferences(context)
        summary = "Character set is " + sp.getString("character_set_name", "Binary")!!
    }

    override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)
        if (positiveResult) {
            val editor = sharedPreferences.edit()
            val characterSetName = spinner!!.selectedItem.toString()
            editor.putString("character_set_name", characterSetName)
            if (characterSetName == "Custom (random characters)") {
                editor.putString("custom_character_set", editText!!.text.toString())
            } else if (characterSetName == "Custom (exact text)") {
                editor.putString("custom_character_string", editText!!.text.toString())
            }
            editor.commit()
            summary = "Character set is $characterSetName"
        }
    }

    companion object {

        val BINARY_CHAR_SET = "01"
        val MATRIX_CHAR_SET = "ｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ"
    }
}
