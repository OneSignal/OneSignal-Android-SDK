package com.onesignal.user

open class User<T>(
    val identity: T
) where T : Identity {

    val tags: Tags
        get() {
            TODO()
        }
}
