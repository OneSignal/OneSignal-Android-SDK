package com.onesignal.user.state

/**
 * A user state changed observer. Implement this interface and provide the implementation
 * to be notified when the user state has changed.
 */
interface IUserStateObserver {
    /**
     * Called when the user state this change handler was added to, has changed. A
     * user state can change when user has logged in or out
     *
     * @param state The user changed state.
     */
    fun onUserStateChange(state: UserChangedState)
}
