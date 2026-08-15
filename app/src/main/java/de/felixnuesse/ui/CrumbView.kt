package de.felixnuesse.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.widget.TooltipCompat
import androidx.core.content.ContextCompat
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.databinding.CustomuiCrumbviewBinding


class CrumbView : LinearLayout {

    private var binding = CustomuiCrumbviewBinding.inflate(LayoutInflater.from(context), this, true)

    private var mTitle = ""
    private var mPath = ""

    private var mShowArrow = false
    private var mIsActive = true

    private var mTextDefaultColor = binding.title.currentTextColor


    constructor(context: Context?, attrs: AttributeSet?, defStyle: Int) : super(context, attrs, defStyle)  {}
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)  {}
    constructor(context: Context) : super(context) {}

    init {
        binding.icon.visibility = View.GONE
        binding.arrow.setColorFilter(mTextDefaultColor)
        binding.icon.setColorFilter(mTextDefaultColor)
    }

    fun setTitle(title: String) {
        mTitle = title
        binding.title.text = mTitle
        setActive(mIsActive)
    }

    fun setPath(path: String) {
        mPath = path
        setActive(mIsActive)
    }

    fun showArrow(showArrow: Boolean) {
        mShowArrow = showArrow
        if(mShowArrow) {
            binding.arrow.visibility = View.VISIBLE
        } else {
            binding.arrow.visibility = View.GONE
        }
    }

    fun showHome() {
        binding.icon.visibility = View.VISIBLE
        updateActiveState()
    }

    fun setActive(isActive: Boolean) {
        mIsActive = isActive
        updateActiveState()
    }

    fun getPath(): String {
        return mPath
    }


    private fun updateActiveState() {

        var textFieldPadding = 0

        if(mIsActive) {
            binding.root.setBackgroundResource(R.drawable.pill)
            binding.arrow.setColorFilter(mTextDefaultColor)
            binding.icon.setColorFilter(mTextDefaultColor)
            binding.title.setTextColor(mTextDefaultColor)
            TooltipCompat.setTooltipText(binding.root, mPath)
            binding.title.maxWidth = getPixelFromDp(99999) // allow it as big as possible
            textFieldPadding = getPixelFromDp(8)
        } else {
            binding.root.background = null
            val color = ContextCompat.getColor(context, R.color.textColorHighlight)
            binding.arrow.setColorFilter(color)
            binding.icon.setColorFilter(color)
            binding.title.setTextColor(color)
            TooltipCompat.setTooltipText(binding.root, null)
            binding.title.maxWidth = getPixelFromDp(90)
        }

        if(binding.icon.visibility == View.VISIBLE) {
            binding.title.setPadding(0, 0, textFieldPadding, 0)
        } else {
            binding.title.setPadding(textFieldPadding, 0, textFieldPadding, 0)
        }

    }

    private fun getPixelFromDp(dp: Int ): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}