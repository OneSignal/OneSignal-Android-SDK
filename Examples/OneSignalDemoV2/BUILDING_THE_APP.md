# OneSignal Sample App V2 - Build Guide

This document contains all the prompts and requirements needed to build the OneSignal Sample App V2 from scratch. Give these prompts to an AI assistant or follow them manually to recreate the app.

---

## Phase 1: Initial Setup

### Prompt 1.1 - Project Foundation

```
Build a sample Android app with:
- MVVM architecture with Android View Binding
- Kotlin Coroutines for background threading (Dispatchers.IO, Dispatchers.Main)
- Gradle Kotlin DSL with buildSrc for type-safe dependency management
- Support for Google FCM and Huawei HMS product flavors (matching existing OneSignalDemo setup)
- Package name: com.onesignal.sdktest (must match google-services.json and agconnect-services.json)
- All dialogs should have EMPTY input fields (for Appium testing - test framework enters values)
```

### Prompt 1.2 - OneSignal Code Organization

```
Centralize all OneSignal SDK calls in a single OneSignalRepository.kt class:

User operations:
- loginUser(externalUserId: String)
- logoutUser()

Alias operations:
- addAlias(label: String, id: String)
- removeAlias(label: String)
- removeAliases(labels: Collection<String>)

Email operations:
- addEmail(email: String)
- removeEmail(email: String)

SMS operations:
- addSms(smsNumber: String)
- removeSms(smsNumber: String)

Tag operations:
- addTag(key: String, value: String)
- removeTag(key: String)
- removeTags(keys: Collection<String>)
- getTags(): Map<String, String>

Trigger operations:
- addTrigger(key: String, value: String)
- removeTrigger(key: String)

Outcome operations:
- sendOutcome(name: String)
- sendUniqueOutcome(name: String)
- sendOutcomeWithValue(name: String, value: Float)

Track Event:
- trackEvent(name: String, value: String?)

Push subscription:
- getPushSubscriptionId(): String?
- isPushEnabled(): Boolean
- setOptedIn(optedIn: Boolean)

In-App Messages:
- setInAppMessagesPaused(paused: Boolean)
- isInAppMessagesPaused(): Boolean

Location:
- setLocationShared(shared: Boolean)
- isLocationShared(): Boolean
- promptLocation()

Privacy consent:
- setConsentGiven(given: Boolean)
- isConsentGiven(): Boolean

Notification sending (via REST API):
- sendNotification(type: NotificationType): Boolean
- sendCustomNotification(title: String, body: String): Boolean
```

### Prompt 1.3 - SDK Observers

```
In MainApplication.kt, set up OneSignal listeners:
- IInAppMessageLifecycleListener (onWillDisplay, onDidDisplay, onWillDismiss, onDidDismiss)
- IInAppMessageClickListener
- INotificationClickListener
- INotificationLifecycleListener (with preventDefault() for async display testing)
- IUserStateObserver

In MainViewModel.kt, implement observers:
- IPushSubscriptionObserver - react to push subscription changes
- IPermissionObserver - react to notification permission changes
- IUserStateObserver - reload data when user changes (login/logout)
```

---

## Phase 2: UI Sections

### Section Order (top to bottom)

1. **App Section**
2. **Aliases Section**
3. **Push Section**
4. **Send Push Notification Section** ← moved up
5. **Emails Section**
6. **SMS Section**
7. **Tags Section**
8. **Outcome Events Section**
9. **In-App Messaging Section**
10. **Send In-App Message Section** ← moved here
11. **Triggers Section**
12. **Track Event Section**
13. **Location Section**
14. **Next Activity Button**

### Prompt 2.1 - App Section

```
App Section layout:

1. App ID display (readonly TextView showing the OneSignal App ID)

2. Sticky guidance banner below App ID:
   - Text: "Add your own App ID and REST API Key, then rebuild to fully test all functionality."
   - Link text: "Get your keys at onesignal.com" (clickable, opens browser)
   - Light background color to stand out

3. Privacy Consent toggle switch:
   - Label: "Privacy Consent"
   - Description: "Grant or revoke privacy consent"
   - SwitchCompat control
   - NOT a blocking overlay - user can interact with app regardless of state

4. LOGIN USER button:
   - Shows "LOGIN USER" when no user is logged in
   - Shows "SWITCH USER" when a user is logged in
   - Opens dialog with empty "External User Id" field

5. External ID display (only visible when logged in):
   - Shows current external user ID below login button

6. LOGOUT USER button
```

### Prompt 2.2 - Aliases Section

```
Aliases Section:
- Section title: "Aliases" with info icon (ℹ️) for tooltip
- RecyclerView showing key-value pairs
- Each item shows: Label | ID with long-press to delete
- Filter out "external_id" and "onesignal_id" from display (these are special)
- "No Aliases Added" text when empty
- ADD ALIAS button → dialog with empty Key and Value fields
- REMOVE ALL ALIASES button:
  - Only visible when at least one alias exists
  - Red background color
  - Removes all displayed aliases at once
```

### Prompt 2.3 - Push Section

```
Push Section:
- Section title: "Push" with info icon for tooltip
- Push Subscription ID display (readonly, ellipsize middle)
- Enabled toggle switch (controls optIn/optOut)
- PROMPT PUSH button:
  - Only visible when notification permission is NOT granted
  - Requests notification permission when clicked
  - Hide after permission is granted
```

### Prompt 2.4 - Send Push Notification Section

```
Send Push Notification Section (placed right after Push Section):
- Section title: "Send Push Notification" with info icon for tooltip
- Three buttons in a card:
  1. SIMPLE - sends basic notification with title/body
  2. WITH IMAGE - sends notification with big picture
  3. CUSTOM - opens dialog for custom title and body

Tooltip should explain each button type.
```

### Prompt 2.5 - Emails Section

```
Emails Section:
- Section title: "Emails" with info icon for tooltip
- RecyclerView showing email addresses
- Each item shows email with long-press to delete
- "No Emails Added" text when empty
- ADD EMAIL button → dialog with empty email field
- Collapse behavior when >5 items:
  - Show first 5 items
  - Show "X more available" text (clickable)
  - Expand to show all when clicked
```

### Prompt 2.6 - SMS Section

```
SMS Section:
- Section title: "SMSs" with info icon for tooltip
- RecyclerView showing phone numbers
- Each item shows phone number with long-press to delete
- "No SMSs Added" text when empty
- ADD SMS button → dialog with empty SMS field
- Collapse behavior when >5 items (same as Emails)
```

### Prompt 2.7 - Tags Section

```
Tags Section:
- Section title: "Tags" with info icon for tooltip
- RecyclerView showing key-value pairs
- Each item shows: Key | Value with long-press to delete
- "No Tags Added" text when empty
- ADD TAG button → dialog with empty Key and Value fields
- REMOVE ALL TAGS button:
  - Only visible when at least one tag exists
  - Red background color
```

### Prompt 2.8 - Outcome Events Section

```
Outcome Events Section:
- Section title: "Outcome Events" with info icon for tooltip
- SEND OUTCOME button → opens dropdown dialog with 3 options:
  1. Normal Outcome → shows name input field
  2. Unique Outcome → shows name input field
  3. Outcome with Value → shows name and value (float) input fields
```

### Prompt 2.9 - In-App Messaging Section

```
In-App Messaging Section:
- Section title: "In-App Messaging" with info icon for tooltip
- Pause In-App Messages toggle switch:
  - Label: "Pause In-App Messages"
  - Description: "Toggle in-app message display"
```

### Prompt 2.10 - Send In-App Message Section

```
Send In-App Message Section (placed right after In-App Messaging):
- Section title: "Send In-App Message" with info icon for tooltip
- Four FULL-WIDTH buttons (not a grid):
  1. TOP BANNER
  2. BOTTOM BANNER
  3. CENTER MODAL
  4. FULL SCREEN
- Button styling:
  - RED background color (#E9444E or similar)
  - WHITE text
  - WHITE icon on the RIGHT side of the text
  - Full width of the card

Tooltip should explain each IAM type.
```

### Prompt 2.11 - Triggers Section

```
Triggers Section:
- Section title: "Triggers" with info icon for tooltip
- RecyclerView showing key-value pairs
- Each item shows: Key | Value with long-press to delete
- "No Triggers Added" text when empty
- ADD TRIGGER button → dialog with empty Key and Value fields
```

### Prompt 2.12 - Track Event Section

```
Track Event Section:
- Section title: "Track Event" with info icon for tooltip
- TRACK EVENT button → opens dialog with:
  - Event Name field (required)
  - Event Value field (optional)
- Calls OneSignal.User.trackEvent(name, properties)
```

### Prompt 2.13 - Location Section

```
Location Section:
- Section title: "Location" with info icon for tooltip
- Location Shared toggle switch:
  - Label: "Location Shared"
  - Description: "Share device location with OneSignal"
- PROMPT LOCATION button
```

---

## Phase 3: View User API Integration

### Prompt 3.1 - REST API Client

```
Create OneSignalApiClient.kt (or add to existing OneSignalNotificationSender.kt):

Add fetchUser() method:
- Endpoint: GET https://api.onesignal.com/apps/{app_id}/users/by/onesignal_id/{onesignal_id}
- Header: Authorization: Key {REST_API_KEY}
- REST API key should be stored in BuildConfig (sourced from strings.xml placeholder)

Response parsing:
- identity map → aliases (but filter out "external_id" and "onesignal_id")
- properties.tags map → tags
- subscriptions array → filter by "type" field:
  - type="Email" → extract "token" as email address
  - type="SMS" → extract "token" as phone number
```

### Prompt 3.2 - Data Loading Flow

```
Loading indicator overlay:
- Add ProgressBar overlay to activity_main.xml
- Add isLoading LiveData to MainViewModel

On cold start:
- Check if OneSignal.User.onesignalId is not null
- If exists: show loading → call fetchUser() → populate UI → hide loading
- If null: just show empty state

On login (LOGIN USER / SWITCH USER):
- Show loading indicator
- Call OneSignal.login(externalUserId)
- Call fetchUser() to get updated data
- Hide loading indicator

On logout:
- Call OneSignal.logout()
- Clear local lists (aliases, emails, sms)
- Call fetchUser() for the new device-scoped user

On onUserStateChange callback:
- Call fetchUser() to sync with server state
- Update UI with new data

Note: getTags() from SDK still works, but aliases/emails/sms must come from API.
```

---

## Phase 4: Info Tooltips

### Prompt 4.1 - Tooltip JSON Content

```
Create app/src/main/assets/tooltip_content.json:

{
  "aliases": {
    "title": "Aliases",
    "description": "Aliases are alternative identifiers for the user. Use them to reference users by your own IDs (e.g., database ID, username)."
  },
  "push": {
    "title": "Push Subscription", 
    "description": "The push subscription ID for this device. Used to send targeted push notifications."
  },
  "emails": {
    "title": "Email Subscriptions",
    "description": "Email addresses associated with this user for email messaging campaigns."
  },
  "sms": {
    "title": "SMS Subscriptions",
    "description": "Phone numbers associated with this user for SMS messaging."
  },
  "tags": {
    "title": "Tags",
    "description": "Key-value data attached to the user for segmentation and personalization."
  },
  "triggers": {
    "title": "Triggers",
    "description": "Local triggers that control when in-app messages are displayed."
  },
  "outcomes": {
    "title": "Outcomes",
    "description": "Track user actions and conversions attributed to OneSignal messaging."
  },
  "inAppMessaging": {
    "title": "In-App Messaging",
    "description": "Display rich messages inside your app based on user behavior and segments."
  },
  "location": {
    "title": "Location",
    "description": "Share device location for location-based targeting and analytics."
  },
  "trackEvent": {
    "title": "Track Event",
    "description": "Track custom events with optional properties for analytics and segmentation."
  },
  "sendPushNotification": {
    "title": "Send Push Notification",
    "description": "Test push notifications by sending them to this device.",
    "options": [
      {"name": "Simple", "description": "Basic push notification with title and body text only."},
      {"name": "With Image", "description": "Push notification with a large image attachment (big picture style)."},
      {"name": "Custom", "description": "Create a custom notification with your own title and message content."}
    ]
  },
  "sendInAppMessage": {
    "title": "Send In-App Message",
    "description": "Test in-app message display formats. These trigger local IAMs for testing UI layouts.",
    "options": [
      {"name": "Top Banner", "description": "Slides in from the top of the screen. Best for non-intrusive alerts and quick info."},
      {"name": "Bottom Banner", "description": "Slides in from the bottom of the screen. Great for prompts and soft CTAs."},
      {"name": "Center Modal", "description": "Centered popup dialog. Ideal for important announcements requiring user attention."},
      {"name": "Full Screen", "description": "Takes over the entire screen. Best for onboarding, promotions, or rich media content."}
    ]
  }
}
```

### Prompt 4.2 - Tooltip Helper

```
Create TooltipHelper.kt:

object TooltipHelper {
    private var tooltips: Map<String, TooltipData> = emptyMap()
    
    fun init(context: Context) {
        // Load tooltip_content.json from assets
        // Parse JSON into tooltips map
    }
    
    fun getTooltip(key: String): TooltipData?
    
    fun showTooltip(context: Context, key: String) {
        // Show AlertDialog with tooltip title, description, and options if present
    }
}

data class TooltipData(
    val title: String,
    val description: String,
    val options: List<TooltipOption>? = null
)

data class TooltipOption(
    val name: String,
    val description: String
)
```

### Prompt 4.3 - Tooltip UI Integration

```
For each section header in activity_main.xml:
- Add an ImageButton with info icon (ic_info or similar) next to the section title
- On click, call TooltipHelper.showTooltip(context, "sectionKey")

Example layout for section header:
<LinearLayout orientation="horizontal">
    <TextView text="@string/aliases" />
    <ImageButton 
        android:id="@+id/btn_info_aliases"
        android:src="@drawable/ic_info"
        android:background="?selectableItemBackgroundBorderless" />
</LinearLayout>
```

---

## Phase 5: Testing Values (Appium Compatibility)

```
All dialog input fields should be EMPTY by default.
The test automation framework (Appium) will enter these values:

- Login Dialog: External User Id = "test"
- Add Alias Dialog: Key = "Test", Value = "Value"
- Add Email Dialog: Email = "test@onesignal.com"
- Add SMS Dialog: SMS = "123-456-5678"
- Add Tag Dialog: Key = "Test", Value = "Value"
- Add Trigger Dialog: Key = "trigger_key", Value = "trigger_value"
- Outcome Dialog: Name = "test_outcome", Value = "1.5"
- Track Event Dialog: Name = "test_event", Value = "test_value"
- Custom Notification Dialog: Title = "Test Title", Body = "Test Body"
```

---

## Key Files Structure

```
Examples/OneSignalDemoV2/
├── buildSrc/
│   └── src/main/kotlin/
│       ├── Versions.kt          # Version constants
│       ├── Dependencies.kt      # Dependency strings
│       └── Plugins.kt           # Plugin IDs
├── app/
│   ├── src/main/
│   │   ├── assets/
│   │   │   └── tooltip_content.json
│   │   ├── java/com/onesignal/sdktest/
│   │   │   ├── application/
│   │   │   │   └── MainApplication.kt
│   │   │   ├── data/
│   │   │   │   ├── model/
│   │   │   │   │   ├── NotificationType.kt
│   │   │   │   │   └── InAppMessageType.kt
│   │   │   │   ├── network/
│   │   │   │   │   └── OneSignalApiClient.kt
│   │   │   │   └── repository/
│   │   │   │       └── OneSignalRepository.kt
│   │   │   ├── ui/
│   │   │   │   ├── main/
│   │   │   │   │   ├── MainActivity.kt
│   │   │   │   │   └── MainViewModel.kt
│   │   │   │   ├── splash/
│   │   │   │   │   └── SplashActivity.kt
│   │   │   │   └── secondary/
│   │   │   │       └── SecondaryActivity.kt
│   │   │   └── util/
│   │   │       ├── SharedPreferenceUtil.kt
│   │   │       └── TooltipHelper.kt
│   │   └── res/
│   │       ├── layout/
│   │       │   ├── activity_main.xml
│   │       │   ├── dialog_login.xml
│   │       │   ├── dialog_add_pair.xml
│   │       │   ├── dialog_single_input.xml
│   │       │   ├── dialog_outcome.xml
│   │       │   ├── item_pair.xml
│   │       │   └── item_single.xml
│   │       └── values/
│   │           ├── strings.xml
│   │           └── colors.xml
│   └── src/huawei/
│       └── java/com/onesignal/sdktest/notification/
│           └── HmsMessageServiceAppLevel.kt
├── google-services.json
├── agconnect-services.json
└── BUILDING_THE_APP.md (this file)
```

---

## Configuration

### strings.xml Placeholders

```xml
<!-- Replace with your own OneSignal App ID -->
<string name="onesignal_app_id">YOUR_APP_ID_HERE</string>

<!-- Replace with your own REST API Key for View User API -->
<string name="onesignal_rest_api_key">YOUR_REST_API_KEY_HERE</string>
```

### Package Name

The package name MUST be `com.onesignal.sdktest` to work with the existing:
- `google-services.json` (Firebase configuration)
- `agconnect-services.json` (Huawei configuration)

If you change the package name, you must also update these files with your own Firebase/Huawei project configuration.

---

## Summary

This app demonstrates all OneSignal Android SDK features:
- User management (login/logout, aliases)
- Push notifications (subscription, sending)
- Email and SMS subscriptions
- Tags for segmentation
- Triggers for in-app message targeting
- Outcomes for conversion tracking
- Event tracking
- In-app messages (display and testing)
- Location sharing
- Privacy consent management

The app is designed to be:
1. **Testable** - Empty dialogs for Appium automation
2. **Comprehensive** - All SDK features demonstrated
3. **Clean** - MVVM architecture with centralized OneSignal code
4. **Cross-platform ready** - Tooltip content in JSON for sharing across wrappers
