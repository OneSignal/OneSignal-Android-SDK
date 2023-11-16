# Android v5.0.0 Migration Guide
In this release, we are making a significant shift from a device-centered model to a user-centered model.  A user-centered model allows for more powerful omni-channel integrations within the OneSignal platform.

This migration guide will walk you through the Android SDK v5.0.0 changes as a result of this shift.

# Overview

Under the user-centered model, the concept of a "player" is being replaced with three new concepts: users, subscriptions, and aliases.


## Users

A user is a new concept which is meant to represent your end-user.   A user has zero or more subscriptions and can be uniquely identified by one or more aliases.  In addition to subscriptions a user can have **data tags** which allows for user attribution.


## Subscription

A subscription refers to the method in which an end-user can receive various communications sent by OneSignal, including push notifications, in app messages, SMS, and email.  In previous versions of the OneSignal platform, this was referred to as a “player”. A subscription is in fact identical to the legacy “player” concept.  Each subscription has a **subscription_id** (previously, player_id) to uniquely identify that communication channel.


## Aliases

Aliases are a concept evolved from [external user ids](https://documentation.onesignal.com/docs/external-user-ids) which allows the unique identification of a user within a OneSignal application.  Aliases are a key-value pair made up of an **alias label** (the key) and an **alias id** (the value).   The **alias label** can be thought of as a consistent keyword across all users, while the **alias id** is a value specific to each user for that particular label. The combined **alias label** and **alias id** provide uniqueness to successfully identify a user.

OneSignal uses a built-in **alias label** called `external_id` which supports existing use of [external user ids](https://documentation.onesignal.com/docs/external-user-ids). `external_id` is also used as the identification method when a user identifies themselves to the OneSignal SDK via the `OneSignal.login` method.  Multiple aliases can be created for each user to allow for your own application's unique identifier as well as identifiers from other integrated applications.


# Migration (v4 to v5)
## Build Changes

Open your App’s `build.gradle (Module: app)` file, add (or update) the following to your `dependencies` section.


    dependencies {
      implementation 'com.onesignal:OneSignal:5.0.0'
    }

The above statement will bring in the entire OneSignalSDK and is the desired state for most integrations.  For greater flexibility you can alternatively specify individual modules that make up the full SDK.  The possible modules are:

    `com.onesignal:core`: The required core module provided by the OneSignal SDK, this must be included.
    `com.onesignal:notifications:` Include to bring in notification based functionality.
    `com.onesignal:in-app-messages`: Include to bring in in app message functionality.
    `com.onesignal:location`: Include to bring in location-based functionality.


    dependencies {
      implementation 'com.onesignal:core:5.0.0'
      implementation 'com.onesignal:in-app-messages:5.0.0'
      implementation 'com.onesignal:notifications:5.0.0'
      implementation 'com.onesignal:location:5.0.0'
    }



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

**Java**

    OneSignal.initWithContext(this, ONESIGNAL_APP_ID);
    // requestPermission will show the native Android notification permission prompt.
    // We recommend removing the following code and instead using an In-App Message to prompt for notification permission.
    OneSignal.getNotifications().requestPermission(true, Continue.none());

**Kotlin**

    OneSignal.initWithContext(this, ONESIGNAL_APP_ID)
    // requestPermission will show the native Android notification permission prompt.
    // We recommend removing the following code and instead using an In-App Message to prompt for notification permission.
    OneSignal.Notifications.requestPermission(true)

If your integration is not user-centric, there is no additional startup code required.  A user is automatically created as part of the push subscription creation, both of which are only accessible from the current device and the OneSignal dashboard.

If your integration is user-centric, or you want the ability to identify as the same user on multiple devices, the OneSignal SDK should be called once the user has been identified:

**Java**

    OneSignal.login("USER_EXTERNAL_ID");

**Kotlin**

    OneSignal.login("USER_EXTERNAL_ID")

The `login` method will associate the device’s push subscription to the user that can be identified via alias  `externalId=USER_EXTERNAL_ID`.  If a user with the provided `externalId` does not exist, one will be created.  If a user does already exist, the user will be updated to include the current device’s push subscription.  Note that a device's push subscription will always be transferred to the currently logged in user, as they represent the current owners of that push subscription.

Once (or if) the user is no longer identifiable in your app (i.e. they logged out), the OneSignal SDK should be called:

**Java**

    OneSignal.logout();

**Kotlin**

    OneSignal.logout()

Logging out has the affect of reverting to a “device-scoped” user, which is the new owner of the device’s push subscription.


## Subscriptions

In previous versions of the SDK there was a player that could have zero or one email address, and zero or one phone number for SMS.  In the user-centered model there is a user with the current device’s **Push Subscription** along with the ability to have zero or **more** email subscriptions and zero or **more** SMS subscriptions.  A user can also have zero or more push subscriptions, one push subscription for each device the user is logged into via the OneSignal SDK.

**Push Subscription**
The current device’s push subscription can be retrieved via:

**Java**

    IPushSubscription pushSubscription = OneSignal.getUser().getPushSubscription();

**Kotlin**

    val pushSubscription = OneSignal.User.pushSubscription


If at any point you want the user to stop receiving push notifications on the current device (regardless of Android permission status) you can use the push subscription to opt out:

**Java**

    pushSubscription.optOut();

**Kotlin**

    pushSubscription.optOut()


To resume receiving of push notifications (driving the native permission prompt if OS permissions are not available), you can opt back in:

**Java**

    pushSubscription.optIn();

**Kotlin**

    pushSubscription.optIn()

To observe changes to the push subscription a class can implement the IPushSubscriptionObserver interface, and can then be added as an observer:

**Java**

    @Override
    public void onPushSubscriptionChange(@NonNull PushSubscriptionChangedState state) {
        ...
    }

    pushSubscription.addObserver(this);

**Kotlin**

    override fun onPushSubscriptionChange(state: PushSubscriptionChangedState) {
        ...
    }

    pushSubscription.addObserver(this)

If you would like to stop observing the subscription you can remove the observer:

**Java**

    pushSubscription.removeObserver(this);

**Kotlin**

    pushSubscription.removeObserver(this)

**Email/SMS Subscriptions**
Email and/or SMS subscriptions can be added or removed via:

**Java**

    // Add email subscription
    OneSignal.getUser().addEmail("customer@company.com")
    // Remove previously added email subscription
    OneSignal.getUser().removeEmail("customer@company.com")
    
    // Add SMS subscription
    OneSignal.getUser().addSms("+15558675309")
    // Remove previously added SMS subscription
    OneSignal.getUser().removeSms("+15558675309")

**Kotlin**

    // Add email subscription
    OneSignal.User.addEmail("customer@company.com")
    // Remove previously added email subscription
    OneSignal.User.removeEmail("customer@company.com")
    
    // Add SMS subscription
    OneSignal.User.addSms("+15558675309")
    // Remove previously added SMS subscription
    OneSignal.User.removeSms("+15558675309")



## Kotlin-Related Changes
****
The OneSignal SDK has been rewritten in Kotlin. This is typically transparent to Java code with one notable exception.  Kotlin provides [coroutines](https://kotlinlang.org/docs/coroutines-overview.html) for asynchronous/long running functions, allowing the caller to regain control once the long running function has completed.  Coroutines are functions with the `suspend` modifier, indicating they will suspend the current execution until the underlying function has completed. 

In Java this is surfaced on a method via an additional parameter to the method of type `Continuation`.  The `Continuation` is a callback mechanism which allows a Java function to gain control when execution has resumed.  If this concept is newer to your application codebase, OneSignal provides an optional java helper class to facilitate the callback model.  Method `com.onesignal.Continue.none()` can be used to indicate no callback is necessary:


    OneSignal.getNotifications().requestPermission(true, Continue.none());

`com.onesignal.Continue.with()` can be used to create a callback lambda expression, which is passed a `ContinueResult` containing information on the success of the underlying coroutine, it's return data, and/or the exception that was thrown:


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



## API Reference

Below is a comprehensive reference to the v5.0.0 OneSignal SDK.

**OneSignal**
The SDK is still accessible via a `OneSignal` static class, it provides access to higher level functionality and is a gateway to each subspace of the SDK.

| **Kotlin**                                                              | **Java**                                                                                                                                                                | **Description**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| ----------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `var consentRequired: Boolean`                                   | `boolean getConsentRequired()`<br>`void setConsentRequired(boolean value)`                                                                                | *Determines whether a user must consent to privacy prior to their user data being sent up to OneSignal.  This should be set to `true` prior to the invocation of [initWithContext] to ensure compliance.*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `var consentGiven: Boolean`                                           | `boolean getConsentGiven()`<br>`void setConsentGiven(boolean value)`                                                                                                | *Indicates whether privacy consent has been granted. This field is only relevant when the application has opted into data privacy protections. See [consentRequired].*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `fun initWithContext(context: Context, appId: String)`                  | `void initWithContext(Context context, String appId)`                                                                                                                   | *Initialize the OneSignal SDK.  This should be called during startup of the application.*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `fun login(externalId: String, jwtBearerToken: String? = null)`         | `void login(String externalId, Continuation<? super Unit> completion)`<br>`void login(String externalId, String jwtBearerToken, Continuation<? super Unit> completion)` | *Login to OneSignal under the user identified by the [externalId] provided. The act of logging a user into the OneSignal SDK will switch the [user] context to that specific user.*<br><br>- *If the [externalId] exists the user will be retrieved and the context set from that user information. If operations have already been performed under a guest user, they* ***will not*** *be applied to the now logged in user (they will be lost).*<br>- *If the [externalId] does not exist the user will be created and the context set from the current local state. If operations have already been performed under a guest user those operations* ***will*** *be applied to the newly created user.*<br><br>***Push Notifications and In App Messaging***<br>*Logging in a new user will automatically transfer push notification and in app messaging subscriptions from the current user (if there is one) to the newly logged in user.  This is because both Push and IAM are owned by the device.* |
| `fun logout()`                                                          | `void logout(Continuation<? super Unit> completion)`                                                                                                                    | *Logout the user previously logged in via [login]. The [user] property now references a new device-scoped user. A device-scoped user has no user identity that can later be retrieved, except through this device as long as the app remains installed and the app data is not cleared.*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                   |


**User Namespace**
The user name space is accessible via `OneSignal.User` (in Kotlin) or `OneSignal.getUser()` (in Java) and provides access to user-scoped functionality.

| **Kotlin**                                                                    | **Java**                                                                      | **Description**                                                                                                                                                                                                                          |
| ----------------------------------------------------------------------------- | ----------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `val pushSubscription: IPushSubscription`                                     | `IPushSubscription getPushSubscription()`                                     | *The push subscription associated to the current user.*                                                                                                                                                                                  |
| `fun setLanguage(value: String)`                                              | `void setLanguage(String value)`                                              | *Set the 2-character language either as a detected language or explicitly set for this user.*                                                                                                                                                |
| `fun pushSubscription.addChangeHandler(handler: ISubscriptionChangedHandler)` | `void pushSubscription.addChangeHandler(ISubscriptionChangedHandler handler)` | *Adds a change handler that will run whenever the push subscription has been changed.*                                                                                                                                                   |
| `fun addAlias(label: String, id: String)`                                     | `void addAlias(String label, String id)`                                      | *Set an alias for the current user.  If this alias already exists it will be overwritten.*                                                                                                                                               |
| `fun addAliases(aliases: Map<String, String>)`                                | `void addAliases(Map<String, String> aliases)`                                | S*et aliases for the current user. If any alias already exists it will be overwritten.*                                                                                                                                                  |
| `fun removeAlias(label: String)`                                              | `void removeAlias(String label)`                                              | *Remove an alias from the current user.*                                                                                                                                                                                                 |
| `fun removeAliases(labels: Collection<String>)`                               | `void removeAliases(Collection<String> labels)`                               | *Remove multiple aliases from the current user.*                                                                                                                                                                                         |
| `fun addEmail(email: String)`                                                 | `void addEmail(String email)`                                                 | *Add a new email subscription to the current user.*                                                                                                                                                                                      |
| `fun removeEmail(email: String)`                                              | `void removeEmail(String email)`                                              | *Remove an email subscription from the current user.*                                                                                                                                                                                    |
| `fun addSms(sms: String)`                                                     | `void addSms(String sms)`                                                     | *Add a new SMS subscription to the current user.*                                                                                                                                                                                        |
| `fun removeSms(sms: String)`                                                  | `void removeSms(String sms)`                                                  | *Remove an SMS subscription from the current user.*                                                                                                                                                                                      |
| `fun addTag(key: String, value: String)`                                      | `void addTag(String key, String value)`                                       | *Add a tag for the current user.  Tags are key:value pairs used as building blocks for targeting specific users and/or personalizing messages. If the tag key already exists, it will be replaced with the value provided here.*         |
| `fun addTags(tags: Map<String, String>)`                                      | `void addTags(Map<String, String> tags)`                                      | *Add multiple tags for the current user.  Tags are key:value pairs used as building blocks for targeting specific users and/or personalizing messages. If the tag key already exists, it will be replaced with the value provided here.* |
| `fun removeTag(key: String)`                                                  | `void removeTag(String key)`                                                  | *Remove the data tag with the provided key from the current user.*                                                                                                                                                                       |
| `fun removeTags(keys: Collection<String>)`                                    | `void removeTags(Collection<String> keys)`                                    | *Remove multiple tags from the current user.*                                                                                                                                                                                            |
| `fun getTags()`                                                               | `Map<String, String> getTags()`                                               | *Return a copy of all local tags from the current user.*   

**Session Namespace**
The session namespace is accessible via `OneSignal.Session` (in Kotlin) or `OneSignal.getSession()` (in Java) and provides access to session-scoped functionality.

| **Kotlin**                                            | **Java**                                             | **Description**                                                                          |
| ----------------------------------------------------- | ---------------------------------------------------- | ---------------------------------------------------------------------------------------- |
| `fun addOutcome(name: String)`                        | `void addOutcome(String name)`                       | *Add an outcome with the provided name, captured against the current session.*           |
| `fun addUniqueOutcome(name: String)`                  | `void addUniqueOutcome(String name)`                 | *Add a unique outcome with the provided name, captured against the current session.*     |
| `fun addOutcomeWithValue(name: String, value: Float)` | `void addOutcomeWithValue(String name, float value)` | *Add an outcome with the provided name and value, captured against the current session.* |


**Notifications Namespace**
The notification namespace is accessible via `OneSignal.Notifications` (in Kotlin) or `OneSignal.getNotifications()` (in Java) and provides access to notification-scoped functionality.

| **Kotlin**                                                                                           | **Java**                                                                                             | **Description**                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| ---------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------- | ----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `val permission: Boolean`                                                                            | `boolean getPermission()`                                                                            | *Whether this app has push notification permission.*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `fun suspend requestPermission(fallbackToSettings: Boolean): Boolean`                                | `void requestPermission(boolean fallbackToSettings, Continuation<? super Boolean> completion)`       | *Prompt the user for permission to push notifications.  This will display the native OS prompt to request push notification permission.  If the user enables, a push subscription to this device will be automatically added to the user.*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |
| `fun removeNotification(id: Int)`                                                                    | `void removeNotification(int id)`                                                                    | *Cancels a single OneSignal notification based on its Android notification integer ID. Use instead of Android's [android.app.NotificationManager.cancel], otherwise the notification will be restored when your app is restarted.*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `fun removeGroupedNotifications(group: String)`                                                      | `void removeGroupedNotifications(String group)`                                                      | *Cancels a group of OneSignal notifications with the provided group key. Grouping notifications is a OneSignal concept, there is no [android.app.NotificationManager] equivalent.*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `fun clearAllNotifications()`                                                                        | `void clearAllNotifications()`                                                                       | *Removes all OneSignal notifications from the Notification Shade. If you just use [android.app.NotificationManager.cancelAll], OneSignal notifications will be restored when your app is restarted.*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                        |
| `fun addPermissionObserver(observer: IPermissionObserver)`                                           | `void addPermissionObserver(IPermissionObserver observer)`                                           | *The [IPermissionObserver.onNotificationPermissionChange] method will be fired on the passed-in object when a notification permission setting changes. This happens when the user enables or disables notifications for your app from the system settings outside of your app. Disable detection is supported on Android 4.4+*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| `fun removePermissionObserver(observer: IPermissionObserver)`                                        | `void removePermissionObserver(IPermissionObserver observer)`                                        | *Remove a push permission observer that has been previously added.*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                         |
| `fun addForegroundLifecycleListener(listener: INotificationLifecycleListener)`                       | `void addForegroundLifecycleListener(INotificationLifecycleListener listener)`                       | *Adds a listener to run before whenever a notification lifecycle event occurs.*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| `fun removeForegroundLifecycleListener(listener: INotificationLifecycleListener)`                    | `void removeForegroundLifecycleListener(INotificationLifecycleListener listener)`                    | *Removes a notification lifecycle listener that has previously been added.*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| `fun addClickListener(listener: INotificationClickListener)`                                         | `void addClickListener(INotificationClickListener listener)`                                         | *Adds a listener that will run whenever a notification is clicked on by the user.*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                          |
| `fun removeClickListener(listener: INotificationClickListener)`                                      | `void removeClickListener(INotificationClickListener listener)`                                      | *Removes a click listener that has previously been added.*                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                  |

**Location Namespace**
The location namespace is accessible via `OneSignal.Location` (in Kotlin) or `OneSignal.getLocation()` (in Java) and provide access to location-scoped functionality.

| **Kotlin**                                 | **Java**                                                           | **Description**                                                                                                                                          |
| -------------------------------------------| -------------------------------------------------------------------| -------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `var isShared: Boolean`                    | `boolean isShared()`<br>`void setShared(boolean value)`            | *Whether location is currently shared with OneSignal.*                                                                                                   |
| `fun suspend requestPermission(): Boolean` | `void requestPermission(Continuation<? super Boolean> completion)` | *Use this method to manually prompt the user for location permissions. This allows for geotagging so you send notifications to users based on location.* |


**InAppMessages Namespace**
The In App Messages namespace is accessible via `OneSignal.InAppMessages` (in Kotlin) or `OneSignal.getInAppMessages()` (in Java) and provide access to in app messages-scoped functionality.

| **Kotlin**                                                                     | **Java**                                                                       | **Description**                                                                                                                                                                                                                                                                                                                                                                                                                                                                 |
| ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `var paused: Boolean`                                                          | `boolean getPaused()`<br>`void setPaused(boolean value)`                       | *Whether the In-app messaging is currently paused.  When set to `true` no IAM will be presented to the user regardless of whether they qualify for them. When set to 'false` any IAMs the user qualifies for will be presented to the user at the appropriate time.*                                                                                                                                                                                                            |
| `fun addTrigger(key: String, value: Any)`                                      | `void addTrigger(String key, Object value)`                                    | *Add a trigger for the current user.  Triggers are currently explicitly used to determine whether a specific IAM should be displayed to the user. See \[Triggers | OneSignal\](https://documentation.onesignal.com/docs/iam-triggers).*<br><br>*If the trigger key already exists, it will be replaced with the value provided here. Note that triggers are not persisted to the backend. They only exist on the local device and are applicable to the current user.*          |
| `fun addTriggers(triggers: Map<String, Any>)`                                  | `void addTriggers(Map<String, Object> triggers)`                               | *Add multiple triggers for the current user.  Triggers are currently explicitly used to determine whether a specific IAM should be displayed to the user. See \[Triggers | OneSignal\](https://documentation.onesignal.com/docs/iam-triggers).*<br><br>*If the trigger key already exists, it will be replaced with the value provided here.  Note that triggers are not persisted to the backend. They only exist on the local device and are applicable to the current user.* |
| `fun removeTrigger(key: String)`                                               | `void removeTrigger(String key)`                                               | *Remove the trigger with the provided key from the current user.*                                                                                                                                                                                                                                                                                                                                                                                                               |
| `fun removeTriggers(keys: Collection<String>)`                                 | `void removeTriggers(Collection<String> keys)`                                 | *Remove multiple triggers from the current user.*                                                                                                                                                                                                                                                                                                                                                                                                                               |
| `fun clearTriggers()`                                                          | `void clearTriggers()`                                                         | *Clear all triggers from the current user.*                                                                                                                                                                                                                                                                                                                                                                                                                                     |
| `fun addLifecycleListener(listener: IInAppMessageLifecycleListener)`           | `void addLifecycleListener(IInAppMessageLifecycleListener listener)`           | *Add a lifecycle listener that will run whenever an In App Message lifecycle event occurs.*                                                                                                                                                                                                                                                                                                                                                                                     |
| `fun removeLifecycleListener(listener: IInAppMessageLifecycleListener)`        | `void removeLifecycleListener(IInAppMessageLifecycleListener listener)`        | *Remove a lifecycle listener that has been previously added.*                                                                                                                                                                                                                                                                                                                                                                                                                   |
| `fun addClickListener(listener: IInAppMessageClickListener)`                   | `void addClickListener(IInAppMessageClickListener listener)`                   | *Add a click listener that will run whenever an In App Message is clicked on by the user.*                                                                                                                                                                                                                                                                                                                                                                                      |
| `fun removeClickListener(listener: IInAppMessageClickListener)`                | `void removeClickListener(IInAppMessageClickListener listener)`                | *Remove a click listener that has been previously added.*                                                                                                                                                                                                                                                                                                                                                                                                                       |


**Debug Namespace**
The debug namespace is accessible via `OneSignal.Debug` (in Kotlin) or `OneSignal.getDebug()` (in Java) and provide access to debug-scoped functionality.

| **Kotlin**                 | **Java**                                                           | **Description**                                                                                      |
| -------------------------- | ------------------------------------------------------------------ | ---------------------------------------------------------------------------------------------------- |
| `var logLevel: LogLevel`   | `LogLevel getLogLevel()`<br>`void setLogLevel(LogLevel value)`     | *The log level the OneSignal SDK should be writing to the Android log. Defaults to [LogLevel.WARN].* |
| `var alertLevel: LogLevel` | `LogLevel getAlertLevel()`<br>`void setAlertLevel(LogLevel value)` | *The log level the OneSignal SDK should be showing as a modal. Defaults to [LogLevel.NONE].*         |





# Limitations
- Changing app IDs is not supported.
- Any `User` namespace calls must be invoked **after** initialization. Example: `OneSignal.User.addTag("tag", "2")`
- In the SDK, the user state is only refreshed from the server when a new session is started (cold start or backgrounded for over 30 seconds) or when the user is logged in. This is by design.

# Known issues
- Identity Verification
    - We will be introducing Identity Verification using JWT in a follow up release
