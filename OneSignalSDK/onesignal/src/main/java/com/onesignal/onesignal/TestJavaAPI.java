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
      // Init OneSignal
      OneSignal.INSTANCE.init("123");

      // Can access user on OneSignal at any time
      System.out.println(OneSignal.INSTANCE.getUser());

      // OneSignal.user = (not allowed, use OneSignal.switchUser instead)

      // Example User Login
      //    - Create an Identity with External user id (with auth hash)
      Identity.ExternalId identity = new Identity.ExternalId("myID");
      OneSignal.INSTANCE.switchUser(identity);

      // Example 1 of Logout, you get generic push
      OneSignal.INSTANCE.switchUser(new Identity.Anonymous());

      // Example 2 of Logout, device won't get an pushes there is no user.
      // JAVA API CON: The casting here is not very clean, this is simply just null in Kotlin
      OneSignal.INSTANCE.switchUser((Identity.Anonymous)null);

      // Add tag example
      // JAVA API CON: "getTags() here is confusing. In Kotlin, this reads OneSignal.user.tags.add
      OneSignal.INSTANCE.getUser().getTags().add("key", "value");



//      OneSignal.loginAsync(new Identity.Known("external_id", "auth_hash"), Continue.with((result, throwable) -> {
//
//      }));

      // Using lambda wrapper
      OneSignal.loginAsync(new Identity.Known("external_id"), Continue.with((result) -> {

      }));

      OneSignal.loginAsync(new Identity.Known("external_id", "auth_hash"), Continue.with((result) -> {

      }));

      // Using anonymous class
      OneSignal.loginAsync(new Identity.Known("external_id", "auth_hash"), new Continuation<IUserManager>() {
         @NonNull
         @Override
         public CoroutineContext getContext() {
            return EmptyCoroutineContext.INSTANCE;
         }

         @Override
         public void resumeWith(@NonNull Object o) {

         }
      });

      // Using Lambda
      OneSignal.getNotifications().addPushPermissionHandler(stateChanges -> {

      });

      // Using anonymous class
      OneSignal.getNotifications().addPushPermissionHandler(new IPermissionChangedHandler() {
         @Override
         public void onOSPermissionChanged(@Nullable IPermissionStateChanges stateChanges) {

         }
      });


//
//      OneSignal.setFuncLambdaUserConflictResolver((local, remote) -> {
//         return remote;
//      });
//
//      OneSignal.setPropLambdaUserConflictResolver((local, remote) -> {
//         return remote;
//      });

      // Using lambda
      OneSignal.setUserConflictResolver((local, remote) -> {
         return remote;
      });

      // Using anonymous class
      OneSignal.setUserConflictResolver(new IUserIdentityConflictResolver() {
         @NonNull
         @Override
         public IUserManager resolve(@NonNull IUserManager local, @NonNull IUserManager remote) {
            return remote;
         }
      });
   }
}
