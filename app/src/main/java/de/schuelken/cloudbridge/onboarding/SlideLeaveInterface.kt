package de.schuelken.cloudbridge.onboarding

interface SlideLeaveInterface {

    fun allowSlideLeave(id: String): Boolean

    fun onSlideLeavePrevented(id: String)
}