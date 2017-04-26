package com.onesignal;

public class OSPermissionSubscriptionState {
   OSSubscriptionState subscriptionStatus;
   OSPermissionState permissionStatus;
   
   public OSPermissionState getPermissionStatus() {
      return permissionStatus;
   }
   
   public OSSubscriptionState getSubscriptionStatus() {
      return subscriptionStatus;
   }
}
