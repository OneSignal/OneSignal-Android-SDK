package com.onesignal.onesignal

import com.onesignal.user.User
import com.onesignal.user.UserAnonymous
import com.onesignal.user.UserIdentified
import com.onesignal.user.UserIdentity

public interface IOneSignal {
    val user: User
    fun init(appId: String)
    fun switchUser(identityIdentified: UserIdentity.Identified): UserIdentified
    fun switchUser(identityAnonymous: UserIdentity.Anonymous?): UserAnonymous?
}
