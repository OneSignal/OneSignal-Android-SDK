package com.onesignal.onesignal.impl

import com.onesignal.onesignal.IOneSignal
import com.onesignal.user.User
import com.onesignal.user.UserAnonymous
import com.onesignal.user.UserKnown
import com.onesignal.user.Identity

class OneSignalImp : IOneSignal {

    private var _user: User? = null

    // Following  priority order is used until we have an instance
    //   1. _user - Already set internal instance
    //   2. From local storage - restore user from last time the app opened
    //   3. Create new - Create a brand new UserAnonymous
    override val user: User
        get() {
            // TODO: This should check local storage to see if there was a user assign before
            val user = _user ?: UserAnonymous()
            _user = user
            return user
        }

    override fun init(appId: String) {
        TODO()
    }

    override fun switchUser(identityKnown: Identity.Known): UserKnown {
        TODO("Not yet implemented")
    }

    // This accepts UserIdentity.Anonymous?, so therefore UserAnonymous? might be null
    override fun switchUser(identityAnonymous: Identity.Anonymous?): UserAnonymous? {
        TODO("Not yet implemented")
    }
}
