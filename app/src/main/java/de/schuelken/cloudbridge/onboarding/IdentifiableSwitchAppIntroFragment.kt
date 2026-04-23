package de.schuelken.cloudbridge.onboarding

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.annotation.FontRes
import androidx.appcompat.widget.SwitchCompat
import ca.pkay.rcloneexplorer.R
import com.github.appintro.AppIntroBaseFragment
import com.github.appintro.AppIntroFragment
import com.github.appintro.SlidePolicy
import com.github.appintro.model.SliderPage

open class IdentifiableSwitchAppIntroFragment : AppIntroBaseFragment(), SlidePolicy,
    CompoundButton.OnCheckedChangeListener {

    var slideLeaveCallback: SlideLeaveInterface? = null
    var switchCallback: SlideSwitchCallback? = null

    // If user should be allowed to leave this slide. This fails open
    override val isPolicyRespected: Boolean
        get() = slideLeaveCallback?.allowSlideLeave(slideId) ?: true

    override fun onUserIllegallyRequestedNextPage() {
        slideLeaveCallback?.onSlideLeavePrevented(slideId)
    }

    override val layoutId: Int get() = R.layout.appintro_fragment_intro_switch

    var slideId: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<SwitchCompat>(R.id.description).setOnCheckedChangeListener(this)
    }


    companion object {


        /**
         * Generates a new instance for [IdentifiableSwitchAppIntroFragment]
         *
         * @param title CharSequence which will be the slide title
         * @param description CharSequence which will be the slide description
         * @param imageDrawable @DrawableRes (Integer) the image that will be
         *                             displayed, obtained from Resources
         * @param backgroundColorRes @ColorRes (Integer) custom background color
         * @param titleColorRes @ColorRes (Integer) custom title color
         * @param descriptionColorRes @ColorRes (Integer) custom description color
         * @param titleTypefaceFontRes @FontRes (Integer) custom title typeface obtained
         *                             from Resources
         * @param descriptionTypefaceFontRes @FontRes (Integer) custom description typeface obtained
         *                             from Resources
         * @param backgroundDrawable @DrawableRes (Integer) custom background drawable
         *
         * @return An [AppIntroFragment] created instance
         */
        @JvmOverloads
        @JvmStatic
        fun createInstance(
            title: CharSequence? = null,
            description: CharSequence? = null,
            @DrawableRes imageDrawable: Int = 0,
            @ColorRes backgroundColorRes: Int = 0,
            @ColorRes titleColorRes: Int = 0,
            @ColorRes descriptionColorRes: Int = 0,
            @FontRes titleTypefaceFontRes: Int = 0,
            @FontRes descriptionTypefaceFontRes: Int = 0,
            @DrawableRes backgroundDrawable: Int = 0,
            id: String,
            callback: SlideLeaveInterface,
            switchCallback: SlideSwitchCallback
        ): IdentifiableSwitchAppIntroFragment {
            return createInstance(
                SliderPage(
                    title = title,
                    description = description,
                    imageDrawable = imageDrawable,
                    backgroundColorRes = backgroundColorRes,
                    titleColorRes = titleColorRes,
                    descriptionColorRes = descriptionColorRes,
                    titleTypefaceFontRes = titleTypefaceFontRes,
                    descriptionTypefaceFontRes = descriptionTypefaceFontRes,
                    backgroundDrawable = backgroundDrawable
                ), id, callback, switchCallback
            )
        }

        /**
         * Generates an [AppIntroFragment] from a given [SliderPage]
         *
         * @param sliderPage the [SliderPage] object which contains all attributes for
         * the current slide
         *
         * @return An [AppIntroFragment] created instance
         */
        @JvmStatic
        fun createInstance(sliderPage: SliderPage, id: String, callback: SlideLeaveInterface, switchCallback: SlideSwitchCallback): IdentifiableSwitchAppIntroFragment {
            val slide = IdentifiableSwitchAppIntroFragment()
            slide.arguments = sliderPage.toBundle()
            slide.slideId = id
            slide.slideLeaveCallback = callback
            slide.switchCallback = switchCallback
            return slide
        }
    }

    override fun onCheckedChanged(buttonView: CompoundButton?, isChecked: Boolean) {
        switchCallback?.switchChanged(slideId, isChecked)
    }
}
