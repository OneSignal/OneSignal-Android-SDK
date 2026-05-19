# OneSignal Sample App - Build Guide

This document contains all the prompts and requirements needed to build the OneSignal Sample App V2 from scratch. Give these prompts to an AI assistant or follow them manually to recreate the app.

---

## Phase 1: Initial Setup

### Prompt 1.1 - Project Foundation

```
Build a sample Android app with:
- MVVM architecture with Jetpack Compose UI
- Kotlin Coroutines for background threading (Dispatchers.IO, Dispatchers.Main)
- Gradle Kotlin DSL with inline dependency versions (no buildSrc, so it works when included from the SDK project)
- Support for Google FCM and Huawei HMS product flavors (matching existing OneSignalDemo setup)
- Package name: com.onesignal.example (must match google-services.json and agconnect-services.json)
- All dialogs should have EMPTY input fields (for Appium / E2E testing - test framework enters values)
- All buttons, toggles, list items, and dialog inputs should expose snake_case `testTag`s
  matching the Capacitor demo (see Phase 9 - Cross-Platform E2E Parity)
- Material3 theming with OneSignal brand colors
- App name (in strings.xml): "OneSignal Demo"
- Top app bar: use CenterAlignedTopAppBar (Material3) with the OneSignal logo + "Sample App" text, centered horizontally
- buildFeatures.buildConfig = true and an E2E_MODE buildConfigField for E2E test runs
  (mirrors the Capacitor demo's VITE_E2E_MODE)
```

### Prompt 1.2 - OneSignal Code Organization

```
Centralize all OneSignal SDK calls in a single OneSignalRepository.kt class:

User operations:
- loginUser(externalUserId: String, jwtToken: String? = null)
- logoutUser()
- updateUserJwt(externalUserId: String, jwtToken: String)
- Identity Verification toggle in the UI controls whether API calls use external_id (+ cached JWT)
  or the OneSignal ID; persisted via SharedPreferences

Alias operations:
- addAlias(label: String, id: String)
- addAliases(aliases: Map<String, String>)  // Batch add

Email operations:
- addEmail(email: String)
- removeEmail(email: String)

SMS operations:
- addSms(smsNumber: String)
- removeSms(smsNumber: String)

Tag operations:
- addTag(key: String, value: String)
- addTags(tags: Map<String, String>)  // Batch add
- removeTag(key: String)
- removeTags(keys: Collection<String>)  // Batch remove
- getTags(): Map<String, String>

Trigger operations:
- addTrigger(key: String, value: String)
- addTriggers(triggers: Map<String, String>)  // Batch add
- removeTrigger(key: String)
- clearTriggers(keys: Collection<String>)

Outcome operations:
- sendOutcome(name: String)
- sendUniqueOutcome(name: String)
- sendOutcomeWithValue(name: String, value: Float)

Track Event:
- trackEvent(name: String, properties: Map<String, Any?>?)  // Properties as parsed JSON map

Push subscription:
- getPushSubscriptionId(): String?
- isPushEnabled(): Boolean
- setPushEnabled(enabled: Boolean)

In-App Messages:
- setInAppMessagesPaused(paused: Boolean)
- isInAppMessagesPaused(): Boolean

Location:
- setLocationShared(shared: Boolean)
- isLocationShared(): Boolean
- promptLocation()

Privacy consent:
- setConsentRequired(required: Boolean)
- getConsentRequired(): Boolean
- setPrivacyConsent(granted: Boolean)
- getPrivacyConsent(): Boolean

Notification sending (via REST API, delegated to OneSignalService):
- sendNotification(type: NotificationType): Boolean
- sendCustomNotification(title: String, body: String): Boolean
- fetchUser(onesignalId: String): UserData?
```

### Prompt 1.3 - OneSignalService (REST API Client)

```
Create OneSignalService.kt object for REST API calls:

Properties:
- appId: String (set from MainApplication)

Methods:
- setAppId(appId: String)
- getAppId(): String
- sendNotification(type: NotificationType): Boolean
- sendCustomNotification(title: String, body: String): Boolean
- fetchUser(onesignalId: String): UserData?

sendNotification endpoint:
- POST https://onesignal.com/api/v1/notifications
- Accept header: "application/vnd.onesignal.v1+json"
- Uses include_subscription_ids (not include_player_ids)
- Includes big_picture for image notifications

fetchUser endpoint:
- GET https://api.onesignal.com/apps/{app_id}/users/by/onesignal_id/{onesignal_id}
- NO Authorization header needed (public endpoint)
- Returns UserData with aliases, tags, emails, smsNumbers, externalId
```

### Prompt 1.4 - SDK Observers

```
In MainApplication.kt, set up OneSignal listeners:
- IInAppMessageLifecycleListener (onWillDisplay, onDidDisplay, onWillDismiss, onDidDismiss)
- IInAppMessageClickListener
- INotificationClickListener
- INotificationLifecycleListener (with preventDefault() for async display testing)
- IUserStateObserver (log when user state changes)
- After registering listeners, restore cached SDK states from SharedPreferences:
  - OneSignal.InAppMessages.paused = cached paused status
  - OneSignal.Location.isShared = cached location shared status

In MainViewModel.kt, implement observers:
- IPushSubscriptionObserver - react to push subscription changes
- IPermissionObserver - react to notification permission changes
- IUserStateObserver - call fetchUserDataFromApi() when user changes (login/logout)
- IUserJwtInvalidatedListener - surface JWT invalidation events via toast + log

fetchUserDataFromApi() uses a monotonic `fetchRequestSequence: Long` counter so that
stale REST responses (after rapid login/logout cycles) are dropped and never overwrite
fresher data. Mirrors the Capacitor demo's `requestSequenceRef`.
```

---

## Phase 2: UI Sections

### Section Order (top to bottom) - FINAL

1. **App Section** (App ID, Guidance Banner, Consent Required toggle, Privacy Consent toggle)
2. **User Section** (Identity Verification toggle, Status, External ID, Login/Logout/Update JWT)
3. **Push Section** (Push ID, Enabled Toggle, Auto-prompts permission on load)
4. **Send Push Notification Section** (Simple, With Image, Custom buttons)
5. **In-App Messaging Section** (Pause toggle)
6. **Send In-App Message Section** (Top Banner, Bottom Banner, Center Modal, Full Screen - with icons)
7. **Aliases Section** (Add/Add Multiple, read-only list with inline loading when empty)
8. **Emails Section** (Collapsible list >5 items, inline loading when empty)
9. **SMS Section** (Collapsible list >5 items, inline loading when empty)
10. **Tags Section** (Add/Add Multiple/Remove Selected, inline loading when empty)
11. **Outcome Events Section** (Send Outcome dialog with type selection)
12. **Triggers Section** (Add/Add Multiple/Remove Selected/Clear All - IN MEMORY ONLY)
13. **Track Event Section** (Track Event with JSON validation)
14. **Location Section** (Location Shared toggle, Prompt Location button)
15. **Next Screen Button**

### Prompt 2.1 - App Section

```
App Section layout (sectionKey = "app"):

1. App ID display (readonly Text showing the OneSignal App ID)
   - Wrapped in `maskValue(appId)` so the value renders as `***` when
     BuildConfig.E2E_MODE is true (so screenshots from automation don't leak it)
   - testTag = "app_id_value"

2. Sticky guidance banner below App ID:
   - Text: "Add your own App ID, then rebuild to fully test all functionality."
   - Link text: "Get your keys at onesignal.com" (clickable, opens browser)
   - Light background color to stand out

3. Consent card with up to two toggles:
   a. "Consent Required" toggle (always visible):
      - Label: "Consent Required"
      - Description: "Require consent before SDK processes data"
      - Sets OneSignal.consentRequired
      - testTag = "consent_required_toggle"
   b. "Privacy Consent" toggle (only visible when Consent Required is ON):
      - Label: "Privacy Consent"
      - Description: "Consent given for data collection"
      - Sets OneSignal.consentGiven
      - testTag = "privacy_consent_toggle"
      - Separated from the above toggle by a horizontal divider
   - NOT a blocking overlay - user can interact with app regardless of state
```

### Prompt 2.1b - User Section

```
User Section layout (sectionKey = "user"):

1. Identity Verification toggle:
   - Label: "Identity Verification"
   - Description: "Use external_id for API calls"
   - testTag = "identity_verification_toggle"
   - Persisted via SharedPreferenceUtil.cacheIdentityVerification(...)

2. Status row (read-only):
   - Label "Status" on the left, value on the right
   - When logged out: "Anonymous"
   - When logged in: "Logged In" with green styling (Color(0xFF2E7D32))
   - testTag = "user_status_value"

3. External ID row (read-only):
   - Label "External ID" on the left
   - Value: external user ID or "—" when logged out
   - testTag = "user_external_id_value"

4. LOGIN USER button (testTag = "login_user_button"):
   - Shows "LOGIN USER" when no user is logged in
   - Shows "SWITCH USER" when a user is logged in
   - Disabled while viewModel.isLoading is true
   - Opens LoginDialog: empty "External User Id" field
     (testTag = "login_user_id_input") and optional "JWT Token" field
     (testTag = "login_user_jwt_input"); confirm button uses
     `singleinput_confirm_button` for Capacitor parity

5. LOGOUT USER button (only when logged in):
   - testTag = "logout_user_button"
   - Disabled while viewModel.isLoading is true

6. UPDATE USER JWT button (always visible):
   - testTag = "update_user_jwt_button"
   - Opens PairInputDialog (External User Id + JWT Token) and calls
     viewModel.updateUserJwt(externalId, token)
```

### Prompt 2.2 - Push Section

```
Push Section:
- Section title: "Push" with info icon for tooltip
- Push Subscription ID display (readonly)
- Enabled toggle switch (controls optIn/optOut)
  - Disabled when notification permission is NOT granted
- Notification permission is automatically requested when MainActivity loads
- PROMPT PUSH button:
  - Only visible when notification permission is NOT granted (fallback if user denied)
  - Requests notification permission when clicked
  - Hidden once permission is granted
```

### Prompt 2.3 - Send Push Notification Section

```
Send Push Notification Section (placed right after Push Section):
- Section title: "Send Push Notification" with info icon for tooltip
- Three buttons:
  1. SIMPLE - sends basic notification with title/body
  2. WITH IMAGE - sends notification with big picture
     (use https://media.onesignal.com/automated_push_templates/ratings_template.png)
  3. CUSTOM - opens dialog for custom title and body

Tooltip should explain each button type.
```

### Prompt 2.4 - In-App Messaging Section

```
In-App Messaging Section (placed right after Send Push):
- Section title: "In-App Messaging" with info icon for tooltip
- Pause In-App Messages toggle switch:
  - Label: "Pause In-App Messages"
  - Description: "Toggle in-app message display"
```

### Prompt 2.5 - Send In-App Message Section

```
Send In-App Message Section (placed right after In-App Messaging):
- Section title: "Send In-App Message" with info icon for tooltip
- Four FULL-WIDTH buttons (not a grid):
  1. TOP BANNER - VerticalAlignTop icon, trigger: "iam_type" = "top_banner"
  2. BOTTOM BANNER - VerticalAlignBottom icon, trigger: "iam_type" = "bottom_banner"
  3. CENTER MODAL - CropSquare icon, trigger: "iam_type" = "center_modal"
  4. FULL SCREEN - Fullscreen icon, trigger: "iam_type" = "full_screen"
- Button styling:
  - RED background color (#E9444E)
  - WHITE text
  - Type-specific icon on LEFT side only (no right side icon)
  - Full width of the card
  - Left-aligned text and icon content (not centered)
  - UPPERCASE button text
- On click: adds trigger "iam_type" with the type's value and shows toast "Sent In-App Message: {type}"

Tooltip should explain each IAM type.
```

### Prompt 2.6 - Aliases Section

```
Aliases Section (placed after Send In-App Message):
- Section title: "Aliases" with info icon for tooltip
- Compose list showing key-value pairs (read-only, no delete icons)
- Each item shows: Label | ID
- Filter out "external_id" and "onesignal_id" from display (these are special)
- "No Aliases Added" text when empty
- ADD button -> PairInputDialog with empty Label and ID fields (single add)
- ADD MULTIPLE button -> MultiPairInputDialog (dynamic rows, add/remove)
- No remove/delete functionality (aliases are add-only from the UI)
```

### Prompt 2.7 - Emails Section

```
Emails Section:
- Section title: "Emails" with info icon for tooltip
- Compose list showing email addresses
- Each item shows email with delete icon
- "No Emails Added" text when empty
- ADD EMAIL button -> dialog with empty email field
- Collapse behavior when >5 items:
  - Show first 5 items
  - Show "X more" text (clickable)
  - Expand to show all when clicked
```

### Prompt 2.8 - SMS Section

```
SMS Section:
- Section title: "SMS" with info icon for tooltip
- Compose list showing phone numbers
- Each item shows phone number with delete icon
- "No SMS Added" text when empty
- ADD SMS button -> dialog with empty SMS field
- Collapse behavior when >5 items (same as Emails)
```

### Prompt 2.9 - Tags Section

```
Tags Section:
- Section title: "Tags" with info icon for tooltip
- Compose list showing key-value pairs
- Each item shows: Key | Value with delete icon
- "No Tags Added" text when empty
- ADD button -> PairInputDialog with empty Key and Value fields (single add)
- ADD MULTIPLE button -> MultiPairInputDialog (dynamic rows)
- REMOVE SELECTED button:
  - Only visible when at least one tag exists
  - Opens MultiSelectRemoveDialog with checkboxes
```

### Prompt 2.10 - Outcome Events Section

```
Outcome Events Section:
- Section title: "Outcome Events" with info icon for tooltip
- SEND OUTCOME button -> opens dialog with 3 radio options:
  1. Normal Outcome -> shows name input field
  2. Unique Outcome -> shows name input field
  3. Outcome with Value -> shows name and value (float) input fields
```

### Prompt 2.11 - Triggers Section (IN MEMORY ONLY)

```
Triggers Section:
- Section title: "Triggers" with info icon for tooltip
- Compose list showing key-value pairs
- Each item shows: Key | Value with delete icon
- "No Triggers Added" text when empty
- ADD button -> PairInputDialog with empty Key and Value fields (single add)
- ADD MULTIPLE button -> MultiPairInputDialog (dynamic rows)
- Two action buttons (only visible when triggers exist):
  - REMOVE SELECTED -> MultiSelectRemoveDialog with checkboxes
  - CLEAR ALL -> Removes all triggers at once

IMPORTANT: Triggers are stored IN MEMORY ONLY during the app session.
- triggersList is a mutableListOf<Pair<String, String>>() in MainViewModel
- Triggers are NOT persisted to SharedPreferences
- Triggers are cleared when the app is killed/restarted
- This is intentional - triggers are transient test data for IAM testing
```

### Prompt 2.12 - Track Event Section

```
Track Event Section:
- Section title: "Track Event" with info icon for tooltip
- TRACK EVENT button -> opens TrackEventDialog with:
  - "Event Name" label + empty input field (required, shows error if empty on submit)
  - "Properties (optional, JSON)" label + input field with placeholder hint {"key": "value"}
    - If non-empty and not valid JSON, shows "Invalid JSON format" error on the field
    - If valid JSON, parsed via JSONObject and converted to Map<String, Any?> for the SDK call
    - If empty, passes null
  - TRACK button disabled until name is filled AND JSON is valid (or empty)
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

### Prompt 2.14 - Secondary Screen

```
Secondary Screen (launched by "Next Screen" button at bottom of main screen):
- Screen title: "Secondary Screen"
- Page content: centered text "Secondary Screen" using headlineMedium style
- Simple screen, no additional functionality needed
```

---

## Phase 3: View User API Integration

### Prompt 3.1 - Data Loading Flow

```
Loading is inline per list section (no full-screen overlay). Mirrors the Capacitor demo.

- isLoading LiveData in MainViewModel
- Aliases / Emails / SMS / Tags sections receive `loading: Boolean = viewModel.isLoading`
- When a list is empty AND loading is true, the LoadingState composable
  (centered CircularProgressIndicator, testTag = "${sectionKey}_loading")
  replaces the EmptyState text in the same card.
- When a list has items, the items render normally — loading is silent.
- Login / Logout buttons are `enabled = !isLoading` instead of showing a spinner
  (matches Capacitor's "disabled while async" pattern on Live Activity update).
- IMPORTANT: Keep the 100ms render delay before clearing isLoading in
  fetchUserDataFromApi() so list state has time to settle before the spinner disappears.

Request sequencing (stale-result drop):
- MainViewModel keeps a monotonic `fetchRequestSequence: Long`.
- fetchUserDataFromApi() captures `val requestId = ++fetchRequestSequence` up front.
- After every suspend point, the function bails (`return@withContext`) if
  `requestId != fetchRequestSequence` so the older response can't overwrite newer state.
- Same guard wraps the catch branch and the final `_isLoading.value = false`.

On cold start:
- Check if OneSignal.User.onesignalId is not null
- If exists: call fetchUserDataFromApi() (which flips _isLoading on while running)
- If null: nothing to do; lists render their EmptyState text immediately

On login (LOGIN USER / SWITCH USER):
- Set _isLoading.value = true
- Call OneSignal.login(externalUserId, jwtToken)
- Clear old user data (aliases, emails, sms, triggers)
- Wait for onUserStateChange callback
- onUserStateChange calls fetchUserDataFromApi() (sequencing handles overlap)

On logout:
- Set _isLoading.value = true
- Call OneSignal.logout()
- Clear local lists (aliases, emails, sms, triggers)
- _isLoading.value = false at the end

On onUserStateChange callback:
- Call fetchUserDataFromApi() to sync with server state
- Update UI with new data (aliases, tags, emails, sms)

Note: REST API key is NOT required for fetchUser endpoint.
```

### Prompt 3.2 - UserData Model

```
data class UserData(
    val aliases: Map<String, String>,    // From identity object (filter out external_id, onesignal_id)
    val tags: Map<String, String>,        // From properties.tags object
    val emails: List<String>,             // From subscriptions where type="Email" -> token
    val smsNumbers: List<String>,         // From subscriptions where type="SMS" -> token
    val externalId: String?               // From identity.external_id
)
```

---

## Phase 4: Info Tooltips

### Prompt 4.1 - Tooltip Content (Remote)

```
Tooltip content is fetched at runtime from the sdk-shared repo. Do NOT bundle a local copy.

URL:
https://raw.githubusercontent.com/OneSignal/sdk-shared/main/demo/tooltip_content.json

This file is maintained in the sdk-shared repo and shared across all platform demo apps.
```

### Prompt 4.2 - Tooltip Helper

```
Create TooltipHelper.kt:

object TooltipHelper {
    private var tooltips: Map<String, TooltipData> = emptyMap()
    private var initialized = false

    private const val TOOLTIP_URL =
        "https://raw.githubusercontent.com/OneSignal/sdk-shared/main/demo/tooltip_content.json"

    fun init(context: Context) {
        if (initialized) return

        // IMPORTANT: Fetch on background thread to avoid blocking app startup
        CoroutineScope(Dispatchers.IO).launch {
            // Fetch tooltip_content.json from TOOLTIP_URL using HttpURLConnection
            // Parse JSON into tooltips map
            // On failure (no network, etc.), leave tooltips empty — tooltips are non-critical

            withContext(Dispatchers.Main) {
                // Update tooltips map on main thread
                initialized = true
            }
        }
    }

    fun getTooltip(key: String): TooltipData?
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

### Prompt 4.3 - Tooltip UI Integration (Compose)

```
For each section, pass an onInfoClick callback to SectionCard:
- SectionCard has an optional info icon that calls onInfoClick when tapped
- In MainScreen, wire onInfoClick to show a TooltipDialog composable
- TooltipDialog displays title, description, and options (if present)

Example in MainScreen.kt:
AliasesSection(
    ...,
    onInfoClick = { showTooltipDialog = "aliases" }
)

showTooltipDialog?.let { key ->
    val tooltip = TooltipHelper.getTooltip(key)
    if (tooltip != null) {
        TooltipDialog(
            title = tooltip.title,
            description = tooltip.description,
            options = tooltip.options?.map { it.name to it.description },
            onDismiss = { showTooltipDialog = null }
        )
    }
}
```

---

## Phase 5: Data Persistence & Initialization

### What IS Persisted (SharedPreferences)

```
SharedPreferenceUtil.kt stores:
- OneSignal App ID
- Consent required status
- Privacy consent status
- External user ID (for login state restoration)
- Location shared status
- In-app messaging paused status
```

### Initialization Flow

```
On app startup, state is restored in two layers:

1. MainApplication.kt restores SDK state from SharedPreferences cache BEFORE init:
   - OneSignal.consentRequired = SharedPreferenceUtil.getCachedConsentRequired(context)
   - OneSignal.consentGiven = SharedPreferenceUtil.getUserPrivacyConsent(context)
   - OneSignal.initWithContext(this, appId)
   Then AFTER init, restores remaining SDK state:
   - OneSignal.InAppMessages.paused = SharedPreferenceUtil.getCachedInAppMessagingPausedStatus(context)
   - OneSignal.Location.isShared = SharedPreferenceUtil.getCachedLocationSharedStatus(context)
   This ensures consent settings are in place before the SDK initializes.

2. MainViewModel.loadInitialState() reads UI state from the SDK (not SharedPreferences):
   - _consentRequired from repository.getConsentRequired() (reads OneSignal.consentRequired)
   - _privacyConsentGiven from repository.getPrivacyConsent() (reads OneSignal.consentGiven)
   - _inAppMessagesPaused from repository.isInAppMessagesPaused() (reads OneSignal.InAppMessages.paused)
   - _locationShared from repository.isLocationShared() (reads OneSignal.Location.isShared)
   - _externalUserId from OneSignal.User.externalId (empty string means no user logged in)
   - _appId from SharedPreferenceUtil (app-level config, no SDK getter)

This two-layer approach ensures:
- The SDK is configured with the user's last preferences before anything else runs
- The ViewModel reads the SDK's actual state as the source of truth for the UI
- The UI always reflects what the SDK reports, not stale cache values
```

### What is NOT Persisted (In-Memory Only)

```
MainViewModel holds in memory:
- triggersList: MutableList<Pair<String, String>>
  - Triggers are session-only
  - Cleared on app restart
  - Used for testing IAM trigger conditions

- aliasesList:
  - Populated from REST API on each session start
  - When user adds alias locally, added to list immediately (SDK syncs async)
  - Fetched fresh via fetchUserDataFromApi() on login/app start

- emailsList, smsNumbersList:
  - Populated from REST API on each session
  - Not cached locally
  - Fetched fresh via fetchUserDataFromApi()

- tagsList:
  - Can be read from SDK via getTags()
  - Also fetched from API for consistency
```

---

## Phase 6: Testing Values (Appium Compatibility)

```
All dialog input fields should be EMPTY by default.
The test automation framework (Appium) will enter these values:

- Login Dialog: External User Id = "test"
- Add Alias Dialog: Key = "Test", Value = "Value"
- Add Multiple Aliases Dialog: Key = "Test", Value = "Value" (first row; supports multiple rows)
- Add Email Dialog: Email = "test@onesignal.com"
- Add SMS Dialog: SMS = "123-456-5678"
- Add Tag Dialog: Key = "Test", Value = "Value"
- Add Multiple Tags Dialog: Key = "Test", Value = "Value" (first row; supports multiple rows)
- Add Trigger Dialog: Key = "trigger_key", Value = "trigger_value"
- Add Multiple Triggers Dialog: Key = "trigger_key", Value = "trigger_value" (first row; supports multiple rows)
- Outcome Dialog: Name = "test_outcome", Value = "1.5"
- Track Event Dialog: Name = "test_event", Properties = "{\"key\": \"value\"}"
- Custom Notification Dialog: Title = "Test Title", Body = "Test Body"
```

---

## Phase 7: Important Implementation Details

### Alias Management

```
Aliases are managed with a hybrid approach:

1. On app start/login: Fetched from REST API via fetchUserDataFromApi()
2. When user adds alias locally:
   - Call OneSignal.User.addAlias(label, id) - syncs to server async
   - Immediately add to local aliasesList (don't wait for API)
   - This ensures instant UI feedback while SDK syncs in background
3. On next app launch: Fresh data from API includes the synced alias
```

### Notification Permission

```
Notification permission is automatically requested when MainActivity loads:
- Call viewModel.promptPush() at end of onCreate()
- This ensures prompt appears after user sees the app UI
- PROMPT PUSH button remains as fallback if user initially denied
- Button hidden once permission is granted
- Keep Push "Enabled" toggle disabled until permission is granted
```

---

## Phase 8: Jetpack Compose Architecture

### Prompt 8.1 - Compose Setup

```
Enable Jetpack Compose in the project:

build.gradle.kts (app):
- buildFeatures { compose = true }
- composeOptions { kotlinCompilerExtensionVersion = "1.5.15" }

Dependencies (via BOM):
- composeBom = "2024.02.00"
- composeUi, composeUiGraphics, composeUiToolingPreview
- composeMaterial3
- composeMaterialIconsExtended (for IAM type icons)
- composeRuntime, composeRuntimeLivedata
- activityCompose
- lifecycleViewModelCompose, lifecycleRuntimeCompose
```

### Prompt 8.2 - Reusable Components

```
Create reusable Compose components in ui/components/:

SectionCard.kt:
- Card with title text and optional info icon
- Column content slot
- onInfoClick callback for tooltips
- `sectionKey: String?` parameter; when non-null, applies
  testTag("${sectionKey}_section") to the outer column and
  testTag("${sectionKey}_info_icon") to the info icon
- Info icon contentDescription is dynamic ("$title info")

ToggleRow.kt:
- Label, optional description, Switch
- Horizontal layout with space between
- Accepts `testTag: String?` and `contentDescription: String?` for the Switch

ActionButton.kt:
- PrimaryButton (filled, primary color background)
- OutlineButton (outlined)
- DestructiveButton (outlined, red accent)
- IconButton (icon + label, used in CTA rows)
- Full-width buttons for consistent styling
- All variants accept `testTag: String?` (applied via Modifier.applyTestTag)

ListComponents.kt:
- PairItem (key-value with delete icon) - dynamic testTags:
    ${sectionKey}_pair_key_$key, ${sectionKey}_pair_value_$key, ${sectionKey}_remove_$key
- SingleItem (single value with delete icon):
    ${sectionKey}_value_$value, ${sectionKey}_remove_$value
- EmptyState (centered "No items" text), testTag = "${sectionKey}_empty"
- LoadingState (centered CircularProgressIndicator), testTag = "${sectionKey}_loading"
- CollapsibleSingleList (shows 5, expandable) — accepts sectionKey + loading
- PairList (simple list of pairs) — accepts sectionKey + loading
- When list is empty and loading=true, render LoadingState in place of EmptyState

Dialogs.kt:
- SingleInputDialog (one text field) - accepts inputTestTag, confirmTestTag
- PairInputDialog (key-value fields, single pair) - accepts keyTestTag, valueTestTag, confirmTestTag
- MultiPairInputDialog (dynamic rows, add/remove, batch submit)
- MultiSelectRemoveDialog (checkboxes for batch remove)
- LoginDialog (External User Id input + optional JWT Token input)
- OutcomeDialog (radio + name/value inputs)
- TrackEventDialog (name + optional JSON properties)
- CustomNotificationDialog, TooltipDialog (tooltip_title / tooltip_description / tooltip_ok_button)

Note: there is no LoadingOverlay component. Loading is rendered inline per list
section via LoadingState, and async buttons disable themselves while loading.
```

### Prompt 8.3 - Reusable Multi-Pair Dialog (Compose)

```
Tags, Aliases, and Triggers all share a reusable MultiPairInputDialog composable
for adding multiple key-value pairs at once.

Behavior:
- Dialog opens with one empty key-value row
- "Add Row" button below the rows adds another empty row
- Each row has a remove button (hidden when only one row exists)
- "Add All" button is disabled until ALL key and value fields in every row are filled
- Validation runs on every text change and after row add/remove
- On "Add All" press, all rows are collected and submitted as a batch
- Batch operations use SDK bulk APIs (addAliases, addTags, addTriggers)

Used by:
- ADD MULTIPLE button (Aliases section) -> calls viewModel.addAliases(pairs)
- ADD MULTIPLE button (Tags section) -> calls viewModel.addTags(pairs)
- ADD MULTIPLE button (Triggers section) -> calls viewModel.addTriggers(pairs)
```

### Prompt 8.4 - Reusable Remove Multi Dialog (Compose)

```
Aliases, Tags, and Triggers share a reusable MultiSelectRemoveDialog composable
for selectively removing items from the current list.

Behavior:
- Accepts the current list of items as List<Pair<String, String>>
- Renders one Checkbox per item on the left with just the key as the label (not "key: value")
- User can check 0, 1, or more items
- "Remove (N)" button shows count of selected items, disabled when none selected
- On confirm, checked items' keys are collected as Collection<String> and passed to the callback

Used by:
- REMOVE SELECTED button (Tags section) -> calls viewModel.removeSelectedTags(keys)
- REMOVE SELECTED button (Triggers section) -> calls viewModel.removeSelectedTriggers(keys)
```

### Prompt 8.5 - Theme

```
Create OneSignal theme in ui/theme/Theme.kt:

Colors:
- OneSignalRed = #E54B4D (primary)
- OneSignalGreen = #34A853 (success)
- OneSignalGreenLight = #E6F4EA (success background)
- LightBackground = #F8F9FA
- CardBackground = White
- DividerColor = #E8EAED
- WarningBackground = #FFF8E1

OneSignalTheme composable:
- MaterialTheme with LightColorScheme
- Custom Typography with SemiBold weights
- Custom Shapes with rounded corners (8/12/16/24dp)
- Primary = OneSignalRed
- Surface variants for cards
```

### Prompt 8.6 - Log View (Appium-Ready)

```
Add collapsible log view at top of screen for debugging and Appium testing.

Files:
- util/LogManager.kt - Thread-safe pass-through logger
- ui/components/LogView.kt - Compose UI with test tags

LogManager Features:
- Pass-through to Android logcat AND UI display
- Thread-safe (posts to main thread for Compose state)
- Captures SDK logs via OneSignal.Debug.addLogListener
- API: LogManager.d/i/w/e(tag, message) mimics android.util.Log

LogView Features:
- Collapsible header (default expanded)
- 5-line height (~100dp)
- Color-coded by level (Debug=blue, Info=green, Warn=amber, Error=red)
- Clear button
- Auto-scroll to newest

Appium Test Tags:
| Tag | Description |
|-----|-------------|
| log_view_container | Main container |
| log_view_header | Clickable expand/collapse |
| log_view_count | Shows "(N)" log count |
| log_view_clear_button | Clear all logs |
| log_view_list | Scrollable LazyColumn |
| log_view_empty | "No logs yet" state |
| log_entry_N | Each log row (N=index) |
| log_entry_N_timestamp | Timestamp text |
| log_entry_N_level | D/I/W/E indicator |
| log_entry_N_message | Log message content |

SDK Log Integration (MainApplication):
OneSignal.Debug.addLogListener { event ->
    LogManager.log("SDK", event.entry, level)
}

Appium Example:
# Verify a log message exists
log_msg = driver.find_element(By.XPATH, "//*[@resource-id='log_entry_0_message']")
assert "Notification sent" in log_msg.text

# Scroll logs
log_list = driver.find_element(By.XPATH, "//*[@resource-id='log_view_list']")
driver.execute_script("mobile: scroll", {"element": log_list, "direction": "down"})
```

### Prompt 8.7 - Toast / Snackbar Messages

```
Feedback is split between user-visible Snackbars and silent log entries,
matching the Capacitor demo's surface.

Surface a Snackbar (toast) ONLY for these actions:
- Login:  "Logged in as: {userId}"
- Logout: "Logged out"
- Outcomes:
    - "Outcome sent: {name}"
    - "Unique outcome sent: {name}"
    - "Outcome with value sent: {name} = {value}"
- Track Event: "Event tracked: {name}"
- Location: "Location permission: {result}" (from promptLocation)
- IUserJwtInvalidatedListener: "User JWT invalidated"

Everything else (tag/alias/email/sms add/remove, push enable/disable,
in-app pause, send-push results, send-IAM, trigger ops, identity verification
toggle, etc.) is silent in the UI and only logged via LogManager.info(...).

Implementation:
- MainViewModel exposes toastMessage: LiveData<String?>
- MainScreen owns a SnackbarHostState inside Scaffold(snackbarHost = { SnackbarHost(...) })
- The Snackbar applies Modifier.testTag("snackbar_toast") for E2E parity
- A LaunchedEffect(toastMessage) calls snackbarHostState.showSnackbar(message)
  then viewModel.clearToast()
- MainActivity does NOT bridge messages through android.widget.Toast anymore
- All Snackbar messages are also written to LogManager.info()
```

---

## Phase 9 - Cross-Platform E2E Parity

The Android demo's E2E surface mirrors the Capacitor demo so the same Appium
scripts (or any test framework that targets resource-id / accessibility-id) can
drive both apps with one set of locators.

### Prompt 9.1 - testTag Naming Convention

```
Every interactive Composable applies a snake_case testTag. The naming mirrors
the Capacitor demo's `data-testid` values 1:1 so cross-platform locators stay
the same.

Conventions:
- Section wrappers:        ${sectionKey}_section
- Section info icon:       ${sectionKey}_info_icon
- Displayed values:        ${sectionKey}_value, ${sectionKey}_pair_key_$key, ${sectionKey}_pair_value_$key
- Remove buttons:          ${sectionKey}_remove_$key
- Empty states:            ${sectionKey}_empty
- Inline loading spinner:  ${sectionKey}_loading
- Specific buttons / toggles use stable names, e.g.:
    login_user_button, logout_user_button, update_user_jwt_button,
    prompt_push_button, push_enabled_toggle, push_id_value,
    consent_required_toggle, privacy_consent_toggle, identity_verification_toggle,
    iam_paused_toggle, send_iam_top_banner_button (per IAM type, lowercase),
    track_event_button, outcome_type_normal_radio, outcome_send_button,
    next_screen_button
- Dialog inputs use the dialog/section prefix:
    login_user_id_input, login_user_jwt_input,
    singleinput_confirm_button, pairinput_key_input, pairinput_value_input,
    pairinput_confirm_button, multipairinput_add_row_button,
    tooltip_title, tooltip_description, tooltip_ok_button
- Snackbar uses testTag = "snackbar_toast"
- The main scroll container uses testTag = "main_scroll_view"
```

### Prompt 9.2 - E2E_MODE & maskValue

```
Sensitive identifiers (App ID, Push Subscription ID) should be redacted in the
UI when the app runs under E2E automation, so screenshots and screen recordings
from CI runs don't leak per-tenant IDs.

build.gradle.kts:
- buildFeatures { buildConfig = true }
- defaultConfig:
    val e2eMode = (project.findProperty("E2E_MODE") as? String)?.toBoolean() ?: false
    buildConfigField("boolean", "E2E_MODE", e2eMode.toString())
- Enable per-run with: `./gradlew :app:installGmsDebug -PE2E_MODE=true`
- Mirrors the Capacitor demo's VITE_E2E_MODE flag.

util/MaskValue.kt:
    fun maskValue(value: String?): String =
        if (BuildConfig.E2E_MODE && !value.isNullOrEmpty()) "***" else value.orEmpty()

Apply maskValue(...) wherever an identifier is rendered in the UI
(currently: App ID display and Push Subscription ID display in MainScreen).
The masked value still flows through the SDK calls normally — only the
displayed string is redacted.
```

---

## Key Files Structure

```
examples/demo/
├── app/
│   ├── build.gradle.kts                       # namespace + applicationId = com.onesignal.example
│   │                                          # buildConfig = true, E2E_MODE buildConfigField
│   ├── src/main/
│   │   ├── java/com/onesignal/example/
│   │   │   ├── application/
│   │   │   │   └── MainApplication.kt         # SDK init, log listener, observers
│   │   │   ├── data/
│   │   │   │   ├── model/
│   │   │   │   │   ├── NotificationType.kt    # With bigPicture URL
│   │   │   │   │   └── InAppMessageType.kt    # With Material icons
│   │   │   │   ├── network/
│   │   │   │   │   └── OneSignalService.kt    # REST API client
│   │   │   │   └── repository/
│   │   │   │       └── OneSignalRepository.kt
│   │   │   ├── ui/
│   │   │   │   ├── components/                # Reusable Compose components
│   │   │   │   │   ├── SectionCard.kt         # title + info icon, sectionKey-driven testTags
│   │   │   │   │   ├── ToggleRow.kt           # Label + Switch (testTag aware)
│   │   │   │   │   ├── ActionButton.kt        # Primary/Outline/Destructive/Icon (testTag aware)
│   │   │   │   │   ├── ListComponents.kt      # PairList, SingleList, EmptyState, LoadingState
│   │   │   │   │   ├── LogView.kt             # Collapsible log viewer (Appium-ready)
│   │   │   │   │   └── Dialogs.kt             # All dialog composables (testTag aware)
│   │   │   │   ├── main/
│   │   │   │   │   ├── MainActivity.kt        # ComponentActivity with setContent
│   │   │   │   │   ├── MainScreen.kt          # Scaffold + SnackbarHost, maskValue display
│   │   │   │   │   ├── Sections.kt            # App / User / Push / ... section composables
│   │   │   │   │   └── MainViewModel.kt       # Batch ops + fetchRequestSequence
│   │   │   │   ├── secondary/
│   │   │   │   │   └── SecondaryActivity.kt   # Simple Compose screen
│   │   │   │   └── theme/
│   │   │   │       └── Theme.kt               # OneSignal Material3 theme
│   │   │   └── util/
│   │   │       ├── SharedPreferenceUtil.kt
│   │   │       ├── LogManager.kt              # Thread-safe pass-through logger
│   │   │       ├── MaskValue.kt               # E2E_MODE-aware redaction helper
│   │   │       └── TooltipHelper.kt           # Fetches tooltips from remote URL
│   │   └── res/
│   │       └── values/
│   │           ├── strings.xml
│   │           ├── colors.xml
│   │           └── styles.xml
│   └── src/huawei/
│       └── java/com/onesignal/example/notification/
│           └── HmsMessageServiceAppLevel.kt
├── google-services.json                       # package_name = com.onesignal.example
├── agconnect-services.json                    # package_name = com.onesignal.example
└── build.md (this file)
```

Note:

- All UI is Jetpack Compose (no XML layouts)
- Tooltip content is fetched from remote URL (not bundled locally)
- LogView at top of screen displays SDK and app logs for debugging/Appium testing
- There is no LoadingOverlay component — list sections render inline LoadingState

---

## Configuration

### strings.xml Placeholders

```xml
<!-- Replace with your own OneSignal App ID -->
<string name="onesignal_app_id">YOUR_APP_ID_HERE</string>
```

Note: REST API key is NOT required for the fetchUser endpoint.

### Package Name

The package name is `com.onesignal.example`, configured in both:

- `app/build.gradle.kts` (`namespace` and the `applicationId` on both `gms`
  and `huawei` product flavors)
- `app/google-services.json` and `app/agconnect-services.json` (`package_name` entries)

If you change the package name, you must update all four locations and supply
your own Firebase / Huawei project configuration files.

---

## Summary

This app demonstrates all OneSignal Android SDK features:

- User management (login/logout, aliases with batch add)
- Push notifications (subscription, sending with images, auto-permission prompt)
- Email and SMS subscriptions
- Tags for segmentation (batch add/remove support)
- Triggers for in-app message targeting (in-memory only, batch operations)
- Outcomes for conversion tracking
- Event tracking with JSON properties validation
- In-app messages (display testing with type-specific icons)
- Location sharing
- Privacy consent management

The app is designed to be:

1. **Testable** - Empty dialogs and snake_case testTags on every interactive element
   for Appium automation
2. **Capacitor-parity** - Same `data-testid` / `testTag` names, same section order,
   same toast/log split, same `snackbar_toast` tag, same E2E mode masking
3. **Comprehensive** - All SDK features demonstrated (including JWT login, JWT update,
   and identity verification)
4. **Clean** - MVVM architecture with Jetpack Compose UI
5. **Cross-platform ready** - Tooltip content in JSON for sharing across wrappers
6. **Session-based triggers** - Triggers stored in memory only, cleared on restart
7. **Responsive UI** - Inline per-section loading instead of a blocking overlay,
   buttons disable themselves while async work is in flight
8. **Race-safe** - Monotonic `fetchRequestSequence` drops stale REST responses
9. **Performant** - Tooltip JSON loaded on background thread
10. **Modern UI** - Material3 theming with reusable Compose components
11. **Batch Operations** - Add multiple items at once, select and remove multiple items
12. **Privacy-aware in CI** - `E2E_MODE` + `maskValue()` redact App ID / Push ID in
    screenshots and recordings
