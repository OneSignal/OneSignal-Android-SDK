package com.onesignal.onesignal

import com.onesignal.onesignal.notification.IPermissionChangedHandler
import com.onesignal.onesignal.notification.IPermissionStateChanges
import com.onesignal.onesignal.user.IUserIdentityConflictResolver
import com.onesignal.onesignal.user.IUserManager
import com.onesignal.user.Identity

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
        val identity = Identity.ExternalId("myID")
        OneSignal.switchUser(identity)

        // Example 1 of Logout, you get generic push
        OneSignal.switchUser(Identity.Anonymous())

        // Example 2 of Logout, device won't get an pushes there is no user.
        OneSignal.switchUser(null)

        // Add tag example
        OneSignal.user.tags.add("key", "value")

        // using anonymous class --> Cannot use lambda without something else? See propLambdaUserConflictResolver, or could make a fun instead of property
        OneSignal.userConflictResolver = object : IUserIdentityConflictResolver {
            override fun resolve(local: IUserManager, remote: IUserManager) : IUserManager {
                // resolve
                return remote
            }
        }

//        OneSignal.setFuncIntfUserConflictResolver(object : IUserIdentityConflictResolver {
//            override fun resolve(local: IUserManager, remote: IUserManager) : IUserManager {
//                // resolve
//                return remote
//            }
//        })
//
//        OneSignal.setFuncLambdaUserConflictResolver { local, remote ->  remote };
//
//        OneSignal.propLambdaUserConflictResolver = { local, remote -> remote }

        // Using lambda
        OneSignal.notifications.addPushPermissionHandler(fun(stateChanges: IPermissionStateChanges) {

        })

        // Using anonymous class
        OneSignal.notifications.addPushPermissionHandler(object : IPermissionChangedHandler {
            override fun onOSPermissionChanged(stateChanges: IPermissionStateChanges?) {
                TODO("Not yet implemented")
            }
        })


    }
}
