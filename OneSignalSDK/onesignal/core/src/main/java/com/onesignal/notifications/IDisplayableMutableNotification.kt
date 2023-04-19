package com.onesignal.notifications

/**
 * An [INotification] that is both an [IMutableNotification] and an [IDisplayableNotification].
 */
interface IDisplayableMutableNotification : IMutableNotification, IDisplayableNotification {
}
