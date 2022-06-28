package com.onesignal.onesignal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.onesignal.onesignal.notification.IPermissionChangedHandler;
import com.onesignal.onesignal.notification.IPermissionStateChanges;
import com.onesignal.onesignal.user.IUserIdentityConflictResolver;
import com.onesignal.onesignal.user.IUserManager;
import com.onesignal.onesignal.user.Identity;

import kotlin.coroutines.Continuation;
import kotlin.coroutines.CoroutineContext;
import kotlin.coroutines.EmptyCoroutineContext;

class TestJavaAPI {
   void test() {
      OneSignal.initWithContext(null, Continue.none());

      // Init OneSignal
      OneSignal.setAppId("123", Continue.none());

      // Can access user on OneSignal at any time
      System.out.println(OneSignal.getUser());

      // OneSignal.user = (not allowed, use OneSignal.switchUser instead)

      // Example User Login
      //    - Create an Identity with External user id (with auth hash)
      OneSignal.login(new Identity.Known("myID"), Continue.with(result -> {

      }));

      OneSignal.login(new Identity.Known("myID", "myIDAuthHash"), Continue.with(result -> {

      }));

      OneSignal.login(new Identity.Known("myID", "myIDAuthHash"), new Continuation<IUserManager>() {
         @NonNull
         @Override
         public CoroutineContext getContext() {
            return EmptyCoroutineContext.INSTANCE;
         }

         @Override
         public void resumeWith(@NonNull Object o) {

         }
      });

      // Example 1 of Logout, you get generic push
      OneSignal.login(new Identity.Anonymous(), Continue.none());

      // Example 2 of Logout, device won't get an pushes there is no user.
      // JAVA API CON: The casting here is not very clean, this is simply just null in Kotlin
      // OneSignal.switchUser((Identity.Anonymous)null);

      // Add tag example
      // JAVA API CON: "getTags() here is confusing. In Kotlin, this reads OneSignal.user.tags.add
      OneSignal.getUser().setTag("key", "value")
                         .setTag("key2", "value2")
                         .setAlias("facebook", "myfacebookid")
                         .setTrigger("level", 1)
                         .addEmailSubscription("user@company.co")
                         .addSmsSubscription("+8451111111");

      // Set the user conflict resolver - anonymous class
      OneSignal.setUserConflictResolver(new IUserIdentityConflictResolver() {
         @NonNull
         @Override
         public IUserManager resolve(@NonNull IUserManager local, @NonNull IUserManager remote) {
            return remote;
         }
      });
      // Set the user conflict resolver - lambda
      OneSignal.setUserConflictResolver((IUserIdentityConflictResolver) (local, remote) -> {
         return remote;
      });

      // Add a push permission handler -> anonymous class
      OneSignal.getNotifications().addPushPermissionHandler(new IPermissionChangedHandler() {
         @Override
         public void onPermissionChanged(@Nullable IPermissionStateChanges stateChanges) {

         }
      });

      // Add a push permission handler -> lambda
      OneSignal.getNotifications().addPushPermissionHandler(stateChanges -> {

      });
   }
}
