package com.onesignal.onesignal

import com.onesignal.onesignal.impl.OneSignalImp
import com.onesignal.user.User
import com.onesignal.user.UserAnonymous
import com.onesignal.user.UserIdentified
import com.onesignal.user.Identity

// This is singleton class that is designed to make OneSignal easy to use.
//    - No instance management is required from the app developer.
// This is a wrapper around an instance of OneSignalImp, no logic lives in this class
public object OneSignal : IOneSignal {
    private val oneSignal: OneSignalImp by lazy {
        OneSignalImp()
    }

    override val user: User
        get() = oneSignal.user

    override fun init(appId: String) {
        oneSignal.init(appId)
    }

    // Call switchUser with the following value when:
    //    User Logs in - Call with ExternalIdWithoutAuth or ExternalIdWithAuthHash
    //    User Logs out - Call with null (no push) or with UserIdentity.Anonymous (generic push)

    override fun switchUser(identityKnown: Identity.Known): UserIdentified {
        return oneSignal.switchUser(identityKnown);
    }

    override fun switchUser(identityAnonymous: Identity.Anonymous?): UserAnonymous? {
        return oneSignal.switchUser(identityAnonymous);
    }
}
