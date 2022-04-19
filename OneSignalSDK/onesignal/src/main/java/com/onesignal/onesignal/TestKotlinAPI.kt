package com.onesignal.onesignal

import com.onesignal.user.UserIdentity

// This is just a quick example to test what the new API would like in Kotlin
// (should be moved into it's own project)
class TestKotlinAPI {
    fun test() {
        // Init OneSignal
        OneSignal.init("123")

        // Can access user on OneSignal at any time
        print(OneSignal.user)

        // OneSignal.user = (not allowed, use OneSignal.switchUser instead)

        // Example User Login
        //    - Create an Identity with External user id (with auth hash)
        val userIdentity = UserIdentity.ExternalIdWithoutAuth("myID")
        OneSignal.switchUser(userIdentity)

        // Example 1 of Logout, you get generic push
        OneSignal.switchUser(UserIdentity.Anonymous())

        // Example 2 of Logout, device won't get an pushes there is no user.
        OneSignal.switchUser(null)

        // Add tag example
        OneSignal.user.tags.add("key", "value")
    }
}
