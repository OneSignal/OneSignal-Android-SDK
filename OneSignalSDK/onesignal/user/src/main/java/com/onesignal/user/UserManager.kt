package com.onesignal.user

import java.util.*

class UserManager {
    // The Active User - If an event happens (such a new session)
    //   this is the User that will be used.
    private var _user: User? = null

    // TODO: Clearing users out of the RAM cache probably isn't something we need to optimize for.
    //       We probably be better to keep them in RAM as long as possible to save on disk reads
    //       and network calls instead.
    private val identityMap = WeakHashMap<Identity.Known, UserIdentified>()

    // Following priority order is used until we have an instance
    //   1. _user - Already set internal instance
    //   2. From local storage - restore user from last time the app opened
    //   3. Create new - Create a brand new UserAnonymous
    val user: User
        get() {
            // TODO: This should check local storage to see if there was a user assign before
            val user = _user ?: UserAnonymous()
            _user = user
            return user
        }

    fun getUserBy(identity: Identity.Known): UserIdentified {
        return identityMap[identity] ?: UserIdentified(identity)
    }

    fun getUserBy(identity: Identity.Anonymous): UserAnonymous {
        val currentUser = user
        if (currentUser is UserAnonymous) return currentUser
        // TODO: Should give same instance
        return UserAnonymous()
    }

    fun switchUser(identityKnown: Identity.Known): UserIdentified {
        val originalUser = user
        if (originalUser is UserIdentified) {
            if (originalUser.identity == identityKnown) return originalUser
        }

        val currentUser = getUserBy(identityKnown)
        _user = currentUser
        return currentUser
    }

    /**
     * This overload is designed to handle the User logout cases:
     *   * Pass null so no User owns this "Device".
     *      - Subscriptions such as push and IAMs are owned by the device so therefore
     *        they will turned off.
     *   * Pass a UserIdentity.Anonymous instance to switch to an Anonymous user
     *      - You will get the same UserAnonymous if the active User was already Anonymous
     */
    fun switchUser(identityAnonymous: Identity.Anonymous?): UserAnonymous? {
        // null was passed in so this means we don't want an Active User.
        if (identityAnonymous == null) {
            _user = null
            // TODO: Unlink this user from this device, so push and IAMs are disabled.
            return null
        }

        val originalUser = user
        if (originalUser is UserAnonymous) return originalUser

        // TODO: Unlink originalUser from this device, so push and IAMs are moved to currentUser.
        val currentUser = UserAnonymous()
        _user = currentUser
        return currentUser
    }
}
