package com.onesignal.onesignal;

import com.onesignal.user.UserIdentity;

class TestJavaAPI {
   void test() {
      // Init OneSignal
      OneSignal.INSTANCE.init("123");

      // Can access user on OneSignal at any time
      System.out.println(OneSignal.INSTANCE.getUser());

      // OneSignal.user = (not allowed, use OneSignal.switchUser instead)

      // Example User Login
      //    - Create an Identity with External user id (with auth hash)
      UserIdentity.ExternalIdWithoutAuth userIdentity = new UserIdentity.ExternalIdWithoutAuth("myID");
      OneSignal.INSTANCE.switchUser(userIdentity);

      // Example 1 of Logout, you get generic push
      OneSignal.INSTANCE.switchUser(new UserIdentity.Anonymous());

      // Example 2 of Logout, device won't get an pushes there is no user.
      // JAVA API CON: The casting here is not very clean, this is simply just null in Kotlin
      OneSignal.INSTANCE.switchUser((UserIdentity.Anonymous)null);

      // Add tag example
      // JAVA API CON: "getTags() here is confusing. In Kotlin, this reads OneSignal.user.tags.add
      OneSignal.INSTANCE.getUser().getTags().add("key", "value");
   }
}
