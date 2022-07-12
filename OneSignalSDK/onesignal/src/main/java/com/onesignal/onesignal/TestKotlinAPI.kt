package com.onesignal.onesignal

import com.onesignal.onesignal.core.OneSignal
import com.onesignal.onesignal.notification.IPermissionChangedHandler
import com.onesignal.onesignal.notification.IPermissionStateChanges
import com.onesignal.onesignal.core.user.IUserIdentityConflictResolver
import com.onesignal.onesignal.core.user.IUserManager
import com.onesignal.onesignal.core.user.Identity

// This is just a quick example to test what the new API would like in Kotlin
// (should be moved into it's own project)
class TestKotlinAPI {
    suspend fun test() {
        // Tell OneSignal the App Context
//        OneSignal.initWithContext(context)

        // Init OneSignal with the AppId
        OneSignal.setAppId("123")

        // Can access user on OneSignal at any time
        print(OneSignal.user)

        // OneSignal.user = (not allowed, use OneSignal.switchUser instead)

        // Example User Login
        //    - Create an Identity with External user id (with auth hash)
        OneSignal.login(Identity.Known("myID"))

        OneSignal.login(Identity.Known("myID", "myIDAuthHash"))

        // Example 1 of Logout, you get generic push
        OneSignal.login(Identity.Anonymous())

        // Example 2 of Logout, device won't get an pushes there is no user.
        //OneSignal.loginAsync(null)

        // Add tag example
        OneSignal.user.setTag("key", "value")
                      .setTag("key2", "value2")
                      .setAlias("facebook", "myfacebookid")
                      .setTrigger("level", 1)
                      .addEmailSubscription("user@company.co")
                      .addSmsSubscription("+8451111111")

        // Set the user conflict resolver - anonymous class
        OneSignal.setUserConflictResolver(object : IUserIdentityConflictResolver {
            override fun resolve(local: IUserManager, remote: IUserManager) : IUserManager {
                // resolve
                return remote
            }
        })
        // Set the user conflict resolver - anonymous function
        OneSignal.setUserConflictResolver(fun(local: IUserManager, remote: IUserManager): IUserManager {
            return remote
        })
        // Set the user conflict resolver - lambda
        OneSignal.setUserConflictResolver { local, remote -> remote }

        // Add a push permission handler -> anonymous function
        OneSignal.notifications.addPushPermissionHandler(fun(stateChanges: IPermissionStateChanges?) {

        })

        // Add a push permission handler -> lambda
        OneSignal.notifications.addPushPermissionHandler { stateChanges ->

        }
        // Add a push permission handler -> anonymous class
        OneSignal.notifications.addPushPermissionHandler(object : IPermissionChangedHandler {
            override fun onPermissionChanged(stateChanges: IPermissionStateChanges?) {
            }
        })
    }
}
