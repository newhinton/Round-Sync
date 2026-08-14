package ca.pkay.rcloneexplorer.Activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.util.PermissionManager
import com.github.appintro.AppIntro2
import de.felixnuesse.extract.onboarding.IdentifiableAppIntroFragment
import de.felixnuesse.extract.onboarding.SlideLeaveInterface

class OnboardingActivity : AppIntro2(), SlideLeaveInterface {

    companion object {
        private const val intro_v1_12_0_completed = "intro_v1_12_0_completed"
        private const val intro_v2_5_2_completed = "intro_v2_5_2_completed"

        private const val latest_intro = intro_v2_5_2_completed

        private const val SLIDE_ID_WELCOME = "SLIDE_ID_WELCOME"
        private const val SLIDE_ID_COMMUNITY = "SLIDE_ID_COMMUNITY"
        private const val SLIDE_ID_PERMCHANGE = "SLIDE_ID_PERMCHANGE"
        private const val SLIDE_ID_STORAGE = "SLIDE_ID_STORAGE"
        private const val SLIDE_ID_NOTIFICATIONS = "SLIDE_ID_NOTIFICATIONS"
        private const val SLIDE_ID_BATTERY_OPTIMIZATION = "SLIDE_ID_BATTERY_OPTIMIZATION"
        private const val SLIDE_ID_ALARMS = "SLIDE_ID_ALARMS"
        private const val SLIDE_ID_SUCCESS = "SLIDE_ID_SUCCESS"

        fun completedIntro(context: Context): Boolean {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            return prefs.getBoolean(latest_intro, false)
        }
    }

    private var mPermissions = PermissionManager(this)
    private lateinit var mPreferences: SharedPreferences

    private var color = R.color.intro_color1

    override fun onResume() {
        super.onResume()
        setImmersiveMode()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setImmersiveMode()
        showStatusBar(true)

        mPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        isWizardMode = true
        isColorTransitionsEnabled = true
        isSystemBackButtonLocked = true

        val v1_12_0 = mPreferences.getBoolean(intro_v1_12_0_completed, false)

        if (!v1_12_0) {
            addSlide(
                IdentifiableAppIntroFragment.createInstance(
                    title = getString(R.string.intro_welcome_title),
                    description = getString(R.string.intro_welcome_description),
                    imageDrawable = R.drawable.app_logo,
                    backgroundColorRes = color,
                    id = SLIDE_ID_WELCOME,
                    callback = this
                ))
            switchColor()
            addSlide(
                IdentifiableAppIntroFragment.createInstance(
                    title = getString(R.string.intro_community_title),
                    description = getString(R.string.intro_community_description),
                    imageDrawable = R.drawable.undraw_the_world_is_mine,
                    backgroundColorRes = color,
                    id = SLIDE_ID_COMMUNITY,
                    callback = this
                ))
            switchColor()
        } else {
            addSlide(
                IdentifiableAppIntroFragment.createInstance(
                    title = getString(R.string.intro_permission_changed_title),
                    description = getString(R.string.intro_permission_changed_description),
                    imageDrawable = R.drawable.undraw_completion,
                    backgroundColorRes = color,
                    id = SLIDE_ID_PERMCHANGE,
                    callback = this
                ))
            switchColor()
        }

        if (!mPermissions.grantedStorage()) {
            addSlide(
                IdentifiableAppIntroFragment.createInstance(
                    title = getString(R.string.intro_storage_title),
                    description = getString(R.string.intro_storage_description),
                    imageDrawable = R.drawable.ic_intro_storage,
                    backgroundColorRes = color,
                    id = SLIDE_ID_STORAGE,
                    callback = this
                ))
            switchColor()
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!mPermissions.grantedNotifications()) {
                addSlide(
                    IdentifiableAppIntroFragment.createInstance(
                        title = getString(R.string.intro_notifications_title),
                        description = getString(R.string.intro_notifications_description),
                        imageDrawable = R.drawable.undraw_post_online,
                        backgroundColorRes = color,
                        id = SLIDE_ID_NOTIFICATIONS,
                        callback = this
                    ))
                switchColor()
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!mPermissions.grantedAlarms()) {
                addSlide(
                    IdentifiableAppIntroFragment.createInstance(
                        title = getString(R.string.intro_alarms_title),
                        description = getString(R.string.intro_alarms_description),
                        imageDrawable = R.drawable.undraw_time_management,
                        backgroundColorRes = color,
                        id = SLIDE_ID_ALARMS,
                        callback = this
                    ))
                switchColor()
            }
        }

        if (!mPermissions.grantedBatteryOptimizationExemption()) {
            addSlide(
                IdentifiableAppIntroFragment.createInstance(
                    title = getString(R.string.intro_battery_optimizations_title),
                    description = getString(R.string.intro_battery_optimizations_description),
                    imageDrawable = R.drawable.undraw_electricity,
                    backgroundColorRes = color,
                    id = SLIDE_ID_BATTERY_OPTIMIZATION,
                    callback = this
                ))
            switchColor()
        }

        addSlide(
            IdentifiableAppIntroFragment.createInstance(
                title = getString(R.string.intro_success),
                description = getString(R.string.intro_successful_setup),
                imageDrawable = R.drawable.undraw_sync,
                backgroundColorRes = color,
                id = SLIDE_ID_SUCCESS,
                callback = this
            ))
        switchColor()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        PreferenceManager.getDefaultSharedPreferences(this)
            .edit()
            .putBoolean(intro_v1_12_0_completed, true)
            .putBoolean(intro_v2_5_2_completed, true)
            .apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private var mAlarmsRequested = false
    private var mOptimizationRequested = false
    private var mNotificationsRequested = false

    override fun allowSlideLeave(id: String): Boolean {
        return when (id) {
            SLIDE_ID_BATTERY_OPTIMIZATION -> mOptimizationRequested
            SLIDE_ID_ALARMS -> mAlarmsRequested
            SLIDE_ID_NOTIFICATIONS -> mNotificationsRequested
            SLIDE_ID_STORAGE -> mPermissions.grantedStorage()
            else -> true
        }
    }

    @SuppressLint("InlinedApi")
    override fun onSlideLeavePrevented(id: String) {
        when (id) {
            SLIDE_ID_STORAGE -> mPermissions.requestStorage(this)
            SLIDE_ID_BATTERY_OPTIMIZATION -> {
                mPermissions.requestBatteryOptimizationException()
                mOptimizationRequested = true
            }
            SLIDE_ID_ALARMS -> {
                mPermissions.requestAlarms()
                mAlarmsRequested = true
            }
            SLIDE_ID_NOTIFICATIONS -> {
                notificationPermission?.launch(Manifest.permission.POST_NOTIFICATIONS)
                mNotificationsRequested = true
            }
            else -> {}
        }
    }

    private var notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        mPermissions.registerInitialRequestNotificationPermission(this)
    } else {
        null
    }

    private fun switchColor() {
        if (color == R.color.intro_color1) {
            color = R.color.intro_color2
        } else {
            color = R.color.intro_color1
        }
    }
}