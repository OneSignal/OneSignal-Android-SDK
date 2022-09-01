package com.onesignal.notification.internal.badges

interface IBadgeCountUpdater {
    /**
     * Update the badge by determining the current count from the
     * state of the application.
     */
    fun update()

    /**
     * Update the badge by using the count provided.
     */
    fun updateCount(count: Int)
}
