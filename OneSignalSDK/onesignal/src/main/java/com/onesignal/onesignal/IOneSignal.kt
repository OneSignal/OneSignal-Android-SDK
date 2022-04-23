package com.onesignal.onesignal

import com.onesignal.user.User
import com.onesignal.user.UserAnonymous
import com.onesignal.user.UserIdentified
import com.onesignal.user.Identity

public interface IOneSignal {
    val user: User
    fun init(appId: String)
    fun switchUser(identityKnown: Identity.Known): UserIdentified
    fun switchUser(identityAnonymous: Identity.Anonymous?): UserAnonymous?
}
