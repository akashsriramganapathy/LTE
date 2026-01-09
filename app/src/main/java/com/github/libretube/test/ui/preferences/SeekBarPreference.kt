package com.github.libretube.test.ui.preferences

import android.content.Context
import android.util.AttributeSet
import android.widget.SeekBar
import android.widget.TextView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.github.libretube.test.R

class SeekBarPreference(context: Context, attrs: AttributeSet) : Preference(context, attrs) {

    private var mProgress = 0
    private var mMax = 10
    private var mMin = 1
    private var mSeekBar: SeekBar? = null
    private var mValueTextView: TextView? = null

    init {
        layoutResource = R.layout.preference_seekbar
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        mSeekBar = holder.findViewById(R.id.seekbar) as SeekBar?
        mValueTextView = holder.findViewById(R.id.seekbar_value) as TextView?

        mSeekBar?.max = mMax - mMin
        mSeekBar?.progress = mProgress - mMin
        mValueTextView?.text = mProgress.toString()

        mSeekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = progress + mMin
                // Only update the text view while dragging
                mValueTextView?.text = value.toString()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {}

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val value = (mSeekBar?.progress ?: 0) + mMin
                if (callChangeListener(value)) {
                    mProgress = value
                    persistInt(value)
                }
            }
        })
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        mProgress = getPersistedInt(defaultValue as? Int ?: mMin)
    }

    override fun onGetDefaultValue(a: android.content.res.TypedArray, index: Int): Any {
        return a.getInt(index, mMin)
    }
}
