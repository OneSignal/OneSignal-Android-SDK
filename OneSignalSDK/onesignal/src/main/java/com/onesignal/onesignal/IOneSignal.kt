package com.onesignal.onesignal

import com.onesignal.user.User
import com.onesignal.user.UserAnonymous
import com.onesignal.user.UserKnown
import com.onesignal.user.Identity

interface IOneSignal {
    val user: User
    fun init(appId: String)
    fun switchUser(identityKnown: Identity.Known): UserKnown
    fun switchUser(identityAnonymous: Identity.Anonymous?): UserAnonymous?
}
