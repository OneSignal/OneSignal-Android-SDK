package com.onesignal.user

public open class User(
    val identity: UserIdentity
) {
    val tags: Tags
        get() {
            TODO()
        }
}
