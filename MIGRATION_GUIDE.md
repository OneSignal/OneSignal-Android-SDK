# Android v5 Migration Guide
In this release, we are making a significant shift from a device-centered model to a user-centered model.  A user-centered model allows for more powerful omni-channel integrations within the OneSignal platform.

This migration guide will walk you through the Android SDK v5 changes as a result of this shift.

# Overview

Under the user-centered model, the concept of a "player" is being replaced with three new concepts: users, subscriptions, and aliases.


## Users

A user is a new concept which is meant to represent your end-user.   A user has zero or more subscriptions and can be uniquely identified by one or more aliases.  In addition to subscriptions a user can have **data tags** which allows for user attribution.


## Subscription

A subscription refers to the method in which an end-user can receive various communications sent by OneSignal, including push notifications, in app messages, SMS, and email.  In previous versions of the OneSignal platform, this was referred to as a “player”. A subscription is in fact identical to the legacy “player” concept.  Each subscription has a **subscription_id** (previously, player_id) to uniquely identify that communication channel.


## Aliases

Aliases are a concept evolved from [external user ids](https://documentation.onesignal.com/docs/users#external-id) which allows the unique identification of a user within a OneSignal application.  Aliases are a key-value pair made up of an **alias label** (the key) and an **alias id** (the value).   The **alias label** can be thought of as a consistent keyword across all users, while the **alias id** is a value specific to each user for that particular label. The combined **alias label** and **alias id** provide uniqueness to successfully identify a user.

OneSignal uses a built-in **alias label** called `external_id` which supports existing use of [external user ids](https://documentation.onesignal.com/docs/users#external-id). `external_id` is also used as the identification method when a user identifies themselves to the OneSignal SDK via the `OneSignal.login` method.  Multiple aliases can be created for each user to allow for your own application's unique identifier as well as identifiers from other integrated applications.


# Migration (v4 to v5)
## Build Changes

In Android Studio, open your `build.gradle.kts (Module: app)` or `build.gradle (Module: app)` file and update OneSignal in your `dependencies`.

```kotlin
// in app/build.gradle.kts

implementation("com.onesignal:OneSignal:[5.1.6, 5.1.99]")
```

```groovy
// in app/build.gradle

implementation 'com.onesignal:OneSignal:[5.1.6, 5.1.99]'
```

The above statement will bring in the entire OneSignalSDK and is the desired state for most integrations.  For greater flexibility you can alternatively specify individual modules that make up the full SDK.  The possible modules are:

- `com.onesignal:core`: The required core module provided by the OneSignal SDK, this must be included.
- `com.onesignal:notifications`: Include to bring in notification based functionality.
- `com.onesignal:in-app-messages`: Include to bring in in app message functionality.
- `com.onesignal:location`: Include to bring in location-based functionality.


## Code Modularization

The OneSignal SDK has been updated to be more modular in nature.  The SDK has been split into namespaces and functionality previously in the static `OneSignal` class has been moved to the appropriate namespace.  Some namespaces are only available if you include the associated module in your build (for simplicity, including module `com.onesignal:OneSignal` will automatically bring in all modules). The namespaces, their containing modules, and how to access them in code are as follows:

| Namespace     | Module                        | Kotlin                    | Java                           |
| ------------- | ----------------------------- | ------------------------- | ------------------------------ |
| User          | com.onesignal:core            | `OneSignal.User`          | `OneSignal.getUser()`          |
| Session       | com.onesignal:core            | `OneSignal.Session`       | `OneSignal.getSession()`       |
| Notifications | com.onesignal:notifications   | `OneSignal.Notifications` | `OneSignal.getNotifications()` |
| Location      | com.onesignal:location        | `OneSignal.Location`      | `OneSignal.getLocation()`      |
| InAppMessages | com.onesignal:in-app-messages | `OneSignal.InAppMessages` | `OneSignal.getInAppMessages()` |
| Debug         | com.onesignal:core            | `OneSignal.Debug`         | `OneSignal.getDebug()`         |



## Initialization

Initialization of the OneSignal SDK, although similar to past versions, has changed.  The target OneSignal application (`appId`) is now provided as part of initialization and cannot be changed post-initialization.  Previous versions of the OneSignal SDK had an explicit `setAppId` function, which is no longer available.  A typical initialization now looks similar to below

In your `ApplicationClass`, initialize OneSignal with the provided methods.

Replace `YOUR_APP_ID` with your OneSignal App ID found **Settings > [Keys & IDs](https://documentation.onesignal.com/docs/keys-and-ids)** in your OneSignal dashboard. If you don't have access to the OneSignal app, ask your [Team Members](https://documentation.onesignal.com/docs/manage-team-members) to invite you.

**Kotlin**

```kotlin
package com.your.package.name // Replace with your package name

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import com.onesignal.OneSignal
import com.onesignal.debug.LogLevel

class ApplicationClass : Application() {
   override fun onCreate() {
      super.onCreate()

      // Enable verbose logging for debugging (remove in production)
      OneSignal.Debug.logLevel = LogLevel.VERBOSE
      // Initialize with your OneSignal App ID
      OneSignal.initWithContext(this, "YOUR_APP_ID")
      // Use this method to prompt for push notifications.
      // We recommend removing this method after testing and instead use In-App Messages to prompt for notification permission.
      CoroutineScope(Dispatchers.IO).launch {
         OneSignal.Notifications.requestPermission(true)
      }
   }
}

```

**Java**

```java
package com.your.package.name // Replace with your package name

import android.app.Application;

import com.onesignal.Continue;
import com.onesignal.OneSignal;
import com.onesignal.debug.LogLevel;

public class ApplicationClass extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Enable verbose logging for debugging (remove in production)
        OneSignal.getDebug().setLogLevel(LogLevel.VERBOSE);
        // Initialize with your OneSignal App ID
        OneSignal.initWithContext(this, "YOUR_APP_ID");
        // Use this method to prompt for push notifications.
        // We recommend removing this method after testing and instead use In-App Messages to prompt for notification permission.
        OneSignal.getNotifications().requestPermission(false, Continue.none());

    }
}

```

If your integration is not user-centric, there is no additional startup code required.  A user is automatically created as part of the push subscription creation, both of which are only accessible from the current device and the OneSignal dashboard.

If your integration is user-centric, or you want the ability to identify as the same user on multiple devices, the OneSignal SDK should be called once the user has been identified: [`OneSignal.login("USER_EXTERNAL_ID")`](https://documentation.onesignal.com/docs/mobile-sdk-reference#login-external-id)

The `login` method will associate the device’s push subscription to the user that can be identified via alias  `external_id`.  If a user with the provided `external_id` does not exist, one will be created.  If a user does already exist, the user will be updated to include the current device’s push subscription.  Note that a device's push subscription will always be transferred to the currently logged in user, as they represent the current owners of that push subscription.

Once (or if) the user is no longer identifiable in your app (i.e. they logged out), the OneSignal SDK should be called: [`OneSignal.logout()`](https://documentation.onesignal.com/docs/mobile-sdk-reference#logout)

Logging out has the affect of reverting to a “device-scoped” user, which is the new owner of the device’s push subscription.


## Subscriptions

In previous versions of the SDK there was a player that could have zero or one email address, and zero or one phone number for SMS.  In the user-centered model there is a user with the current device’s **Push Subscription** along with the ability to have zero or **more** email subscriptions and zero or **more** SMS subscriptions.  A user can also have zero or more push subscriptions, one push subscription for each device the user is logged into via the OneSignal SDK.

**Push Subscription**

The current device’s push subscription can be retrieved via: [`OneSignal.User.pushSubscription`](https://documentation.onesignal.com/docs/mobile-sdk-reference#user-pushsubscription-id)


If at any point you want the user to stop receiving push notifications on the current device (regardless of Android permission status) you can use the push subscription to opt out: [`OneSignal.User.pushSubscription.optOut()`](https://documentation.onesignal.com/docs/mobile-sdk-reference#optout-%2C-optin-%2C-optedin)


To resume receiving of push notifications (driving the native permission prompt if OS permissions are not available), you can opt back in: [`OneSignal.User.pushSubscription.optIn()`](https://documentation.onesignal.com/docs/mobile-sdk-reference#optout-%2C-optin-%2C-optedin)


To observe changes to the push subscription a class can implement the IPushSubscriptionObserver interface, and can then be added as an observer: [`OneSignal.User.pushSubscription.addObserver(observer)`](https://documentation.onesignal.com/docs/mobile-sdk-reference#addobserver-push-subscription-changes)


**Email/SMS Subscriptions**

Email and/or SMS subscriptions can be added or removed via: [`OneSignal.User.addEmail("customer@company.com")`](https://documentation.onesignal.com/docs/mobile-sdk-reference#addemail-email) or [`OneSignal.User.addSms("+15558675309")`](https://documentation.onesignal.com/docs/mobile-sdk-reference#addsms-sms)


## Kotlin-Related Changes

The OneSignal SDK has been rewritten in Kotlin. This is typically transparent to Java code with one notable exception.  Kotlin provides [coroutines](https://kotlinlang.org/docs/coroutines-overview.html) for asynchronous/long running functions, allowing the caller to regain control once the long running function has completed.  Coroutines are functions with the `suspend` modifier, indicating they will suspend the current execution until the underlying function has completed. 

In Java this is surfaced on a method via an additional parameter to the method of type `Continuation`.  The `Continuation` is a callback mechanism which allows a Java function to gain control when execution has resumed.  If this concept is newer to your application codebase, OneSignal provides an optional java helper class to facilitate the callback model.  Method `com.onesignal.Continue.none()` can be used to indicate no callback is necessary:

```java
OneSignal.getNotifications().requestPermission(true, Continue.none());
```
`com.onesignal.Continue.with()` can be used to create a callback lambda expression, which is passed a `ContinueResult` containing information on the success of the underlying coroutine, it's return data, and/or the exception that was thrown:

```java
OneSignal.getNotifications().requestPermission(true, Continue.with(r -> {
    if (r.isSuccess()) {
      if (r.getData()) {
        // code to execute once requestPermission has completed successfully and the user has accepted permission.
      }
      else {
        // code to execute once requestPermission has completed successfully but the user has rejected permission.
      }
    }
    else {
      // code to execute once requestPermission has completed unsuccessfully, `r.getThrowable()` might have more information as to the reason for failure.
    }
}));
```


## API Reference

For a mapping of old v4 methods to v5 methods, see the [Mobile SDK Mapping guide](https://documentation.onesignal.com/docs/device-user-model-mobile-sdk-mapping).

Or visit the [v5 Mobile SDK reference](https://documentation.onesignal.com/docs/mobile-sdk-reference) for a full list of methods and their usage.


# Limitations
- Changing app IDs is not supported.
- Any `User` namespace calls must be invoked **after** initialization. Example: `OneSignal.User.addTag("tag", "2")`
- In the SDK, the user state is only refreshed from the server when a new session is started (cold start or backgrounded for over 30 seconds) or when the user is logged in. This is by design.

# Known issues
- Identity Verification
    - We will be introducing Identity Verification using JWT in a follow up release