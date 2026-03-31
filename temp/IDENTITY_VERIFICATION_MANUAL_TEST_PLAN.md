# Identity Verification (JWT) Manual Test Plan

## Table of Contents

- [Prerequisites](#prerequisites)
- [Migration Paths Under Test](#migration-paths-under-test)
- [How to Prepare Each Migration Path](#how-to-prepare-each-migration-path)
- [Section 1: Startup and Initialization](#section-1-startup-and-initialization)
- [Section 2: Login with JWT](#section-2-login-with-jwt-iv-on)
- [Section 3: Multi-User Login Sequences](#section-3-multi-user-login-sequences-iv-on)
- [Section 4: Logout](#section-4-logout-iv-on)
- [Section 5: User Data Operations](#section-5-user-data-operations-iv-on)
- [Section 6: In-App Messages](#section-6-in-app-messages-iv-on)
- [Section 7: Caching, Persistence, and Retry](#section-7-caching-persistence-and-retry-iv-on)
- [Section 8: Migration Paths](#section-8-migration-paths)
- [Section 9: IV Toggle (Dashboard Changes)](#section-9-iv-toggle-dashboard-changes)
- [Section 10: Edge Cases and Error Handling](#section-10-edge-cases-and-error-handling)
- [Section 11: IV OFF Regression](#section-11-iv-off-regression)
- [Testing Checklist Summary](#testing-checklist-summary)

---

## Prerequisites

### Tools
- Android device or emulator
- OneSignal Dashboard access with ability to toggle Identity Verification (JWT) on/off
- A JWT generation tool or server endpoint to produce valid/invalid/expired JWTs for test external IDs
- Network proxy (e.g., Charles Proxy) or `adb logcat` with `LogLevel.VERBOSE` to inspect SDK network requests and logs
- The demo app (`Examples/demo`) built from the `feat/identity_verification_5.8` branch

### Dashboard Setup
- OneSignal app configured with a REST API key (for the demo app's notification sending)
- Ability to toggle **Identity Verification** on and off in dashboard settings
- At least one In-App Message configured (for Section 6 tests)

### Key Terminology
- **IV** = Identity Verification (the JWT feature)
- **IV ON** = `jwt_required: true` in remote params, `useIdentityVerification == true` in ConfigModel
- **IV OFF** = `jwt_required: false` in remote params, `useIdentityVerification == false` in ConfigModel
- **IV unknown** = Remote params haven't arrived yet, `useIdentityVerification == null`
- **HYDRATE** = The moment remote params are fetched and applied to ConfigModel
- **Sink user** = The local-only anonymous user created on logout when IV is ON (never sent to backend)

### How to Verify with the Demo App
- **Login**: Tap "Login" button -> enter External User ID and JWT token -> confirm
- **Logout**: Tap "Logout" button
- **Update JWT**: Tap "Update JWT" button -> enter External User ID and JWT token -> confirm
- **JWT Invalidated Callback**: Watch the log view at the top of the demo app for "JWT invalidated for externalId: ..." messages
- **Add Tags/Aliases/Email/SMS**: Use the corresponding sections in the demo app
- **Network Requests**: Use `adb logcat | grep -i "OneSignal"` with `LogLevel.VERBOSE` or a network proxy

### Log Messages to Watch For
- `"Identity verification is enabled"` -- logged on HYDRATE when IV turns on
- `"JWT invalidated for externalId: ..."` -- logged when `onUserJwtInvalidated` fires
- `"Authorization: Bearer ..."` -- in HTTP request headers when IV is on
- `"Removing operations without externalId"` -- when anonymous ops are purged
- `"hasValidJwtIfRequired"` -- when ops are gated on JWT availability
- `"FAIL_UNAUTHORIZED"` -- when a 401 response is received

---

## Migration Paths Under Test

Every scenario should be considered across these four starting states:

| Path | Description |
|------|-------------|
| **New Install** | Fresh app install, no prior data in SharedPreferences |
| **v4 Player Model** | App was on SDK v4 (legacy player ID stored). Upgrade to this branch |
| **v5 (no IV)** | App was on v5 `main` branch (no JWT feature). Has existing anonymous or identified user. Upgrade to this branch |
| **JWT Beta** | App was on the previous `feat/identity_verification` beta branch (JWT stored as singleton on `IdentityModel`). Upgrade to this branch |

---

## How to Prepare Each Migration Path

### New Install
1. Uninstall the demo app completely (or clear all app data)
2. Build and install the `feat/identity_verification_5.8` branch

### v4 Player Model
1. Build and install the demo app from a v4 SDK tag (e.g., `4.x.x`)
2. Open the app, let it register a player
3. Verify a legacy player ID is stored (visible in logcat)
4. WITHOUT uninstalling, build and install the `feat/identity_verification_5.8` branch over the top

### v5 (no IV)
1. Build and install the demo app from the `main` branch (v5, no JWT feature)
2. Open the app, either leave as anonymous user OR login with an externalId (depending on the test)
3. Let the user sync to backend
4. WITHOUT uninstalling, build and install the `feat/identity_verification_5.8` branch over the top

### JWT Beta
1. Build and install the demo app from the previous `feat/identity_verification` beta branch
2. Open the app, login with JWT
3. Optionally create the multi-user stuck state (login as userA with expired JWT, then login as userB)
4. WITHOUT uninstalling, build and install the `feat/identity_verification_5.8` branch over the top

---

## Section 1: Startup and Initialization

These test the critical window between `initWithContext` and remote params arriving, where `useIdentityVerification == null`.

### Test 1.1: New install, IV ON on dashboard

**Precondition**: Fresh install. IV is ON in dashboard.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Uninstall app, build and install from `feat/identity_verification_5.8` | Clean install |
| 2 | Open app | `initWithContext` is called. Logcat shows anonymous `LoginUserOperation` enqueued |
| 3 | Immediately tap "Add Tag" and add key="test", value="1" | Tag op enqueued locally |
| 4 | Wait for remote params to arrive (watch logcat for "Identity verification is enabled") | HYDRATE fires with IV=true |
| 5 | Check logcat for "Removing operations without externalId" | Anonymous `LoginUserOperation` and the tag op are purged |
| 6 | Verify in OneSignal Dashboard: no new user was created | No anonymous user on backend |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 1.2: New install, IV OFF on dashboard

**Precondition**: Fresh install. IV is OFF in dashboard.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Uninstall app, build and install | Clean install |
| 2 | Open app | `initWithContext` called, anonymous `LoginUserOperation` enqueued |
| 3 | Immediately add a tag (key="test", value="1") | Tag op enqueued |
| 4 | Wait for remote params | HYDRATE fires with IV=false |
| 5 | Check logcat | Anonymous user creation request sent, tag request sent |
| 6 | Verify in dashboard | Anonymous user exists with the tag |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 1.3: New install, IV ON, no internet at startup

**Precondition**: Fresh install. IV is ON in dashboard. Device in airplane mode.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Enable airplane mode | No internet |
| 2 | Uninstall app, install from `feat/identity_verification_5.8` | Clean install |
| 3 | Open app | `initWithContext` called. Anonymous op enqueued. Remote params cannot be fetched |
| 4 | Tap Login -> enter externalId="alice", JWT=valid token | `LoginUserOperation` for alice enqueued, JWT stored in `JwtTokenStore` |
| 5 | Disable airplane mode | Internet restored |
| 6 | Wait for remote params to arrive | HYDRATE with IV=true. Anonymous ops purged |
| 7 | Check logcat for alice's `LoginUserOperation` executing with Authorization header | User "alice" created on backend |
| 8 | Verify in dashboard | User "alice" exists |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 1.4: Cold start (returning user, IV ON)

**Precondition**: Previously logged in as "alice" with valid JWT. IV is ON.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login as "alice" with valid JWT. Confirm user created on backend | Setup complete |
| 2 | Force-kill the app | App terminated |
| 3 | Reopen the app | `initWithContext` called. Persisted ops reload. JwtTokenStore loaded from SharedPreferences |
| 4 | Wait for HYDRATE | IV=true confirmed. `forceExecuteOperations()` called |
| 5 | Check that "alice" is still the current user (externalId shown in UI) | User identity persisted correctly |
| 6 | Add a tag | Tag sent to backend with Authorization header for alice |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 1.5: Cold start, IV ON, JWT expired in store

**Precondition**: Logged in as "alice" with a JWT that will expire. IV is ON.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login as "alice" with a short-lived JWT. Verify user created | Setup complete |
| 2 | Wait for the JWT to expire (or use a pre-expired token from step 1) | JWT is now invalid |
| 3 | Force-kill the app | App terminated |
| 4 | Reopen the app | Persisted ops and JWT loaded |
| 5 | Wait for HYDRATE | Ops attempt to execute with expired JWT |
| 6 | Check logcat for 401 response and "JWT invalidated" | `onUserJwtInvalidated("alice")` fires |
| 7 | Check demo app log view | "JWT invalidated for externalId: alice" appears |
| 8 | Tap "Update JWT" -> enter externalId="alice", JWT=new valid token | JWT updated in store |
| 9 | Check logcat | Pending ops retry with new JWT and succeed |

**Result**: [ ] PASS / [ ] FAIL

---

## Section 2: Login with JWT (IV ON)

**Precondition for all tests in this section**: IV is ON in dashboard. Fresh install unless stated otherwise.

### Test 2.1: Login with valid JWT

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Open app (fresh install) | App initializes |
| 2 | Wait for HYDRATE (IV=true) | Anonymous ops purged |
| 3 | Tap Login -> externalId="alice", JWT=valid token | Login called |
| 4 | Check logcat for HTTP request | `POST /users` or `GET /users/by/external_id/alice` with `Authorization: Bearer <jwt>` |
| 5 | Verify in dashboard | User "alice" exists with push subscription |
| 6 | Check demo app UI | ExternalId shows "alice" |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 2.2: Login with invalid/expired JWT

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Open app (fresh install), wait for HYDRATE | IV=true |
| 2 | Tap Login -> externalId="alice", JWT=expired/invalid token | Login called |
| 3 | Check logcat | `LoginUserOperation` executes, backend returns 401 |
| 4 | Check for callback | `onUserJwtInvalidated("alice")` fires. Demo app log shows "JWT invalidated for externalId: alice" |
| 5 | Verify in dashboard | User "alice" NOT created |
| 6 | Check that ops are re-queued and paused (no more requests until JWT updated) | No repeated 401 requests |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 2.3: Login then update JWT

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Perform Test 2.2 (login with expired JWT, callback fires) | Setup: alice with invalid JWT, ops paused |
| 2 | Tap "Update JWT" -> externalId="alice", JWT=valid token | JWT updated in store, `forceExecuteOperations()` called |
| 3 | Check logcat | Ops retry with new JWT. `LoginUserOperation` succeeds |
| 4 | Verify in dashboard | User "alice" now exists |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 2.4: Same-user re-login (JWT refresh)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login as "alice" with valid JWT. Wait for user creation | Alice exists on backend |
| 2 | Tap Login again -> externalId="alice", JWT=new valid token | Login called for same user |
| 3 | Check logcat | No new `LoginUserOperation`. Only JWT updated in store + `forceExecuteOperations()` |
| 4 | Check demo app UI | ExternalId still shows "alice". No loading spinner for user switch |
| 5 | Add a tag | Tag sent with the new JWT in Authorization header |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 2.5: Login without JWT when IV is ON

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Open app (fresh install), wait for HYDRATE (IV=true) | Setup |
| 2 | Call login with externalId="alice" but no JWT (leave JWT field empty in login dialog) | Login called without JWT |
| 3 | Check logcat | `LoginUserOperation` enqueued but gated (no valid JWT in store) |
| 4 | Verify `onUserJwtInvalidated("alice")` fires | Demo app log shows invalidation message |
| 5 | Tap "Update JWT" -> externalId="alice", JWT=valid token | JWT provided |
| 6 | Check logcat | Ops unblock and execute |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 2.6: Login with JWT when IV is OFF

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Set IV OFF in dashboard | IV disabled |
| 2 | Open app (fresh install), wait for HYDRATE (IV=false) | Anonymous user created normally |
| 3 | Tap Login -> externalId="alice", JWT=valid token | Login called with JWT |
| 4 | Check logcat | Login proceeds via `onesignal_id`-based URLs (NOT `external_id`). NO `Authorization: Bearer` header sent |
| 5 | Verify in dashboard | User "alice" exists (created via standard flow) |
| 6 | Verify JWT is stored (it will be used later if IV is turned on) | Check logcat for "putJwt" or similar storage log |

**Result**: [ ] PASS / [ ] FAIL

---

## Section 3: Multi-User Login Sequences (IV ON)

These test the core design change: per-user JWT in `JwtTokenStore` instead of singleton.

### Test 3.1: Rapid user switching

**Precondition**: Fresh install. IV ON.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Open app, wait for HYDRATE | IV=true, anonymous ops purged |
| 2 | Login as "alice" with valid jwtA | Alice's `LoginUserOperation` enqueued |
| 3 | Add tag key="alice_tag", value="1" | Tag op enqueued with externalId="alice" |
| 4 | Login as "bob" with valid jwtB | Bob's `LoginUserOperation` enqueued. Alice's ops still in queue |
| 5 | Add tag key="bob_tag", value="2" | Tag op enqueued with externalId="bob" |
| 6 | Login as "alice" with valid jwtA2 | JWT refresh for alice. No new user switch if alice was previous user before bob |
| 7 | Add tag key="alice_tag2", value="3" | Tag op enqueued with externalId="alice" |
| 8 | Wait for all ops to process | Check logcat: each op uses correct JWT from JwtTokenStore |
| 9 | Verify in dashboard | alice has tags "alice_tag" and "alice_tag2". bob has tag "bob_tag" |
| 10 | Check demo app | Current user is alice (last login). Push subscription belongs to alice |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 3.2: Multi-user with invalid JWT for one user

**Precondition**: Fresh install. IV ON. (Matches existing spreadsheet row 10)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login as "userA" with invalid JWT | Ops enqueued, will fail with 401 |
| 2 | Add tag key="tagA1", value="1" | Tag for userA enqueued |
| 3 | Login as "userB" with invalid JWT | Ops enqueued for userB |
| 4 | Add tag key="tagB1", value="2" | Tag for userB enqueued |
| 5 | Login as "userA" with invalid JWT | JWT refresh for userA (still invalid) |
| 6 | Add tag key="tagA2", value="3" | Another tag for userA |
| 7 | Login as "userB" with VALID JWT | JWT refresh for userB (now valid) |
| 8 | Wait for processing | userB's ops succeed: user created + tagB1 sent. userA's ops get 401, `onUserJwtInvalidated("userA")` fires |
| 9 | Verify in dashboard | userB exists with tagB1. userA does NOT exist yet |
| 10 | Verify current user is userB | Demo app shows externalId="userB" |
| 11 | Force-kill and reopen app | Cold start |
| 12 | Tap "Update JWT" -> externalId="userA", JWT=valid token | userA's JWT updated |
| 13 | Wait for processing | userA's ops execute: user created + tagA1 + tagA2 sent |
| 14 | Verify in dashboard | userA exists with both tags. userB still has its tag |
| 15 | Verify current user is still userB | Push subscription belongs to userB |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 3.3: One user's 401 does not block another

**Precondition**: Fresh install. IV ON.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login as "alice" with expired JWT | Alice's ops enqueued |
| 2 | Add tag for alice | Tag enqueued for alice |
| 3 | Login as "bob" with valid JWT | Bob's ops enqueued |
| 4 | Add tag for bob | Tag enqueued for bob |
| 5 | Wait for processing | Bob's ops proceed and succeed. Alice's ops get 401, are re-queued |
| 6 | Check callbacks | `onUserJwtInvalidated("alice")` fires. No invalidation for bob |
| 7 | Verify in dashboard | Bob exists with tag. Alice does NOT exist yet |

**Result**: [ ] PASS / [ ] FAIL

---

## Section 4: Logout (IV ON)

### Test 4.1: Logout with IV ON

**Precondition**: Logged in as "alice" with valid JWT. IV ON. User exists on backend.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Tap Logout | Logout called |
| 2 | Check logcat | `createAndSwitchToNewUser(suppressBackendOperation=true)` -- local-only sink user created |
| 3 | Check logcat | Push subscription opted out locally (`isDisabledInternally = true`) |
| 4 | Check logcat | NO `LoginUserOperation` enqueued for the anonymous sink user |
| 5 | Check demo app | ExternalId shows empty/null. Push opt-in shows OFF |
| 6 | Wait 30 seconds | No network requests sent for anonymous user |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 4.2: Logout then add data

**Precondition**: Perform Test 4.1 (logged out state with IV ON).

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Add tag key="sink_tag", value="1" | Tag written to local sink user |
| 2 | Add email "test@test.com" | Email written to local sink user |
| 3 | Check logcat | No network requests for tag or email. Ops suppressed by IV+anonymous checks |
| 4 | Wait 30 seconds | No backend calls for any of this data |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 4.3: Logout then login

**Precondition**: Perform Test 4.2 (logged out with data on sink user).

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Tap Login -> externalId="bob", JWT=valid token | Login called |
| 2 | Check logcat | Sink user replaced entirely by bob. `LoginUserOperation` for bob enqueued and executes |
| 3 | Verify in dashboard | User "bob" exists. No "sink_tag" or "test@test.com" on bob's profile |
| 4 | Check demo app | ExternalId shows "bob" |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 4.4: Logout, background, reopen, then login (IAM test)

**Precondition**: Logged in as "alice" with valid JWT. IV ON. At least one IAM configured in dashboard.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Tap Logout | Logged out, sink user created |
| 2 | Press Home to background the app | App backgrounded |
| 3 | Wait at least 60 seconds | Enough time for new session threshold |
| 4 | Reopen the app | New session triggered |
| 5 | Wait 15 seconds | No IAM fetch request in logcat (anonymous user, IV ON) |
| 6 | Login as "alice" with valid JWT | User re-authenticated |
| 7 | Check logcat | IAM fetch request sent with Authorization header for alice |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 4.5: Logout with IV OFF

**Precondition**: IV OFF. Logged in as "alice".

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Tap Logout | Standard v5 logout |
| 2 | Check logcat | New anonymous user created. `LoginUserOperation` enqueued for anonymous user |
| 3 | Wait for processing | Anonymous user created on backend. Push subscription transferred |
| 4 | Verify in dashboard | New anonymous user exists |

**Result**: [ ] PASS / [ ] FAIL

---

## Section 5: User Data Operations (IV ON)

**Precondition for all**: IV ON. Logged in as "alice" with valid JWT. User exists on backend.

### Test 5.1: Add aliases

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Tap "Add Alias" -> label="my_alias", id="123" | Alias add called |
| 2 | Check logcat for HTTP request | URL contains `/users/by/external_id/alice/identity` (NOT `onesignal_id`). `Authorization: Bearer` header present |
| 3 | Verify in dashboard | Alias "my_alias:123" on alice's profile |

**Result**: [ ] PASS / [ ] FAIL

### Test 5.2: Remove aliases

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Remove alias "my_alias" from Test 5.1 | Alias remove called |
| 2 | Check logcat | DELETE request to `/users/by/external_id/alice/identity/my_alias`. Auth header present |
| 3 | Verify in dashboard | Alias removed |

**Result**: [ ] PASS / [ ] FAIL

### Test 5.3: Add tags

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Add tag key="color", value="blue" | Tag add called |
| 2 | Check logcat | PATCH request to `/users/by/external_id/alice`. Auth header present |
| 3 | Verify in dashboard | Tag "color:blue" on alice |

**Result**: [ ] PASS / [ ] FAIL

### Test 5.4: Add email/SMS subscriptions

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Add email "alice@test.com" | Email subscription add called |
| 2 | Check logcat | POST to create subscription with Auth header |
| 3 | Add SMS "+15551234567" | SMS subscription add called |
| 4 | Check logcat | POST to create subscription with Auth header |
| 5 | Verify in dashboard | Email and SMS subscriptions on alice's profile |

**Result**: [ ] PASS / [ ] FAIL

### Test 5.5: All operations while JWT is invalid

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login as "alice" with expired JWT | Ops queued, 401 received, callback fires |
| 2 | Add tag key="pending_tag", value="1" | Tag op queued, gated (no valid JWT) |
| 3 | Add alias label="pending_alias", id="456" | Alias op queued, gated |
| 4 | Add email "pending@test.com" | Email op queued, gated |
| 5 | Check logcat | No HTTP requests for these ops (all waiting for valid JWT) |
| 6 | Tap "Update JWT" -> externalId="alice", JWT=valid token | JWT updated, `forceExecuteOperations()` |
| 7 | Check logcat | All queued ops flush: user created, tag sent, alias sent, email sent |
| 8 | Verify in dashboard | Alice exists with tag, alias, and email |

**Result**: [ ] PASS / [ ] FAIL

---

## Section 6: In-App Messages (IV ON)

### Test 6.1: IAM fetch with JWT

**Precondition**: IV ON. Logged in as "alice" with valid JWT. IAM configured in dashboard for alice's segment.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Background the app for 60+ seconds, then reopen (trigger new session) | Session started |
| 2 | Check logcat for IAM fetch request | URL contains `/users/by/external_id/alice/subscriptions/.../iams`. `Authorization: Bearer` header present |
| 3 | Verify IAM displays correctly | Message appears in app |

**Result**: [ ] PASS / [ ] FAIL

### Test 6.2: IAM fetch skipped for anonymous user

**Precondition**: IV ON. Fresh install, no login.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Open app (fresh install) | HYDRATE with IV=true, anonymous ops purged |
| 2 | Background for 60+ seconds, reopen | New session triggered |
| 3 | Check logcat | NO IAM fetch request (anonymous user doesn't exist on backend) |

**Result**: [ ] PASS / [ ] FAIL

### Test 6.3: IAM fetch with expired JWT

**Precondition**: IV ON. Logged in as "alice" but JWT has expired.

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login as "alice" with a JWT that will expire soon. Wait for it to expire | JWT now invalid |
| 2 | Background for 60+ seconds, reopen | New session, IAM fetch attempted |
| 3 | Check logcat | IAM fetch fails with 401. `onUserJwtInvalidated("alice")` fires |
| 4 | Update JWT with valid token | JWT refreshed |
| 5 | Background and reopen again | New session |
| 6 | Check logcat | IAM fetch succeeds with new JWT |

**Result**: [ ] PASS / [ ] FAIL

---

## Section 7: Caching, Persistence, and Retry (IV ON)

### Test 7.1: Offline queueing

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Open app (fresh install), wait for HYDRATE (IV=true) | Setup |
| 2 | Enable airplane mode | No internet |
| 3 | Login as "alice" with valid JWT | JWT stored. `LoginUserOperation` enqueued. HTTP fails (no network) |
| 4 | Add tag key="offline_tag", value="1" | Tag op enqueued |
| 5 | Add email "offline@test.com" | Email op enqueued |
| 6 | Force-kill the app | Ops persisted to disk |
| 7 | Disable airplane mode | Internet restored |
| 8 | Reopen the app | Persisted ops loaded. JWT still in JwtTokenStore |
| 9 | Wait for HYDRATE and processing | All ops execute with JWT: user created, tag sent, email added |
| 10 | Verify in dashboard | Alice exists with tag and email |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 7.2: Expired JWT in cache

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login as "alice" with valid JWT. Verify user created | Setup |
| 2 | Update JWT: tap "Update JWT" -> externalId="alice", JWT=expired token | Expired JWT now in store |
| 3 | Add tags and aliases | Ops enqueued |
| 4 | Force-kill the app | Ops and expired JWT persisted |
| 5 | Reopen the app | Ops loaded, JWT loaded |
| 6 | Wait for processing | Ops try with expired JWT, get 401. `onUserJwtInvalidated("alice")` fires |
| 7 | Tap "Update JWT" -> externalId="alice", JWT=new valid token | JWT updated |
| 8 | Wait for processing | Ops retry and succeed |
| 9 | Verify in dashboard | Tags and aliases on alice's profile |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 7.3: JwtTokenStore pruning on cold start

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Login as "alice" with valid JWT | Alice's JWT stored |
| 2 | Add a tag for alice | Op queued for alice |
| 3 | Login as "bob" with valid JWT | Bob's JWT stored |
| 4 | Wait for all ops to complete | Both users created on backend |
| 5 | Force-kill the app | State persisted |
| 6 | Reopen the app | `loadSavedOperations()` runs, `pruneToExternalIds()` called |
| 7 | Check logcat | JwtTokenStore only contains entries for externalIds with pending ops + current identity. No stale entries from old users |

**Result**: [ ] PASS / [ ] FAIL

---

## Section 8: Migration Paths

### 8A: New Install

#### Test 8A.1: New install, IV ON

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Fresh install. Open app | Anonymous `LoginUserOperation` enqueued, held by IV=null gate |
| 2 | Wait for HYDRATE (IV=true) | Anonymous op purged. Log: "Removing operations without externalId" |
| 3 | Verify no user created on backend | Dashboard shows no new anonymous user |
| 4 | Login as "alice" with JWT | User created on backend |

**Result**: [ ] PASS / [ ] FAIL

#### Test 8A.2: New install, IV OFF

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Fresh install. Open app | Anonymous `LoginUserOperation` enqueued |
| 2 | Wait for HYDRATE (IV=false) | Anonymous user created on backend normally |
| 3 | Verify in dashboard | Standard v5 anonymous user exists |

**Result**: [ ] PASS / [ ] FAIL

---

### 8B: v4 Player Model Migration

#### Test 8B.1: v4 -> this branch, IV OFF

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Install v4 SDK demo app. Open, let player register | Legacy player ID stored |
| 2 | Upgrade to `feat/identity_verification_5.8` (install over) | Migration path triggered |
| 3 | Open app | `LoginUserFromSubscriptionOperation` enqueued. Held until HYDRATE |
| 4 | Wait for HYDRATE (IV=false) | Migration op proceeds: legacy player linked to new v5 user |
| 5 | Verify in dashboard | User has push subscription linked from legacy player |

**Result**: [ ] PASS / [ ] FAIL

#### Test 8B.2: v4 -> this branch, IV ON

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Install v4 SDK demo app. Open, let player register | Legacy player ID stored |
| 2 | Turn IV ON in dashboard | IV enabled |
| 3 | Upgrade to `feat/identity_verification_5.8` (install over) | Migration path triggered |
| 4 | Open app | `LoginUserFromSubscriptionOperation` enqueued (externalId=null). Held until HYDRATE |
| 5 | Wait for HYDRATE (IV=true) | `IdentityVerificationService` purges the op (externalId=null). Legacy player ID cleared |
| 6 | Check logcat | Executor safety net: `FAIL_NORETRY` if somehow reached. Purge message logged |
| 7 | Login as "alice" with JWT | New user created on backend |
| 8 | Verify in dashboard | Alice exists. Legacy player is NOT linked (migration was purged) |

**Result**: [ ] PASS / [ ] FAIL

#### Test 8B.3: v4 -> this branch, IV ON, no internet then login

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Install v4 app, let player register. Turn IV ON in dashboard | Setup |
| 2 | Enable airplane mode | No internet |
| 3 | Upgrade to `feat/identity_verification_5.8`, open app | Migration op enqueued. No HYDRATE possible |
| 4 | Login as "alice" with valid JWT | Alice's op enqueued, JWT stored |
| 5 | Disable airplane mode | Internet restored |
| 6 | Wait for HYDRATE (IV=true) | Legacy migration op purged. Alice's op executes with JWT |
| 7 | Verify in dashboard | Alice exists. No legacy player linkage |

**Result**: [ ] PASS / [ ] FAIL

---

### 8C: v5 (no IV) Migration

#### Test 8C.1: v5 (anonymous user) -> this branch, IV OFF

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Install main branch demo app. Open, let anonymous user sync | Anonymous user on backend |
| 2 | Upgrade to `feat/identity_verification_5.8` | Migration |
| 3 | Open app. Wait for HYDRATE (IV=false) | Normal startup. Existing anonymous user continues |
| 4 | Verify | No behavioral change from standard v5 |

**Result**: [ ] PASS / [ ] FAIL

#### Test 8C.2: v5 (anonymous user) -> this branch, IV ON

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Install main branch demo app. Open, let anonymous user sync | Anonymous user on backend |
| 2 | Turn IV ON in dashboard | IV enabled |
| 3 | Upgrade to `feat/identity_verification_5.8`, open app | Anonymous ops purged on HYDRATE |
| 4 | Wait for HYDRATE | SDK in "logged out" state. No anonymous user creation attempted |
| 5 | Login as "alice" with JWT | New user created on backend |

**Result**: [ ] PASS / [ ] FAIL

#### Test 8C.3: v5 (identified user) -> this branch, IV ON

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Install main branch demo app. Login as "alice" (no JWT). Verify user on backend | Identified user exists |
| 2 | Turn IV ON in dashboard | IV enabled |
| 3 | Upgrade to `feat/identity_verification_5.8`, open app | HYDRATE fires |
| 4 | Check logcat | `IdentityVerificationService` detects externalId="alice" but no JWT in JwtTokenStore |
| 5 | Check callback | `onUserJwtInvalidated("alice")` fires. Demo app log shows it |
| 6 | Tap "Update JWT" -> externalId="alice", JWT=valid token | JWT provided |
| 7 | Check logcat | Ops resume with JWT. Requests now include Authorization header |

**Result**: [ ] PASS / [ ] FAIL

#### Test 8C.4: v5 (identified user) -> this branch, IV OFF

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Install main branch demo app. Login as "alice". Verify user on backend | Identified user |
| 2 | IV remains OFF | No IV |
| 3 | Upgrade to `feat/identity_verification_5.8`, open app | Normal startup |
| 4 | Verify | Standard v5 behavior. No JWT required. No Authorization headers |

**Result**: [ ] PASS / [ ] FAIL

---

### 8D: JWT Beta Branch Migration

#### Test 8D.1: Beta -> this branch, logged in user, IV ON

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Install beta branch demo app. Login as "alice" with JWT | Beta stores JWT on singleton IdentityModel |
| 2 | Upgrade to `feat/identity_verification_5.8` (install over) | Migration |
| 3 | Open app | Persisted ops from beta loaded. Beta ops lack `externalId` field (loaded as null) |
| 4 | Wait for HYDRATE (IV=true) | Ops with null externalId purged by IVS or skipped by OperationRepo |
| 5 | Check logcat | Stale `jwt_token` key on IdentityModel is harmless (not read) |
| 6 | Check callback | `IdentityVerificationService` detects: externalId="alice" + no JWT in new JwtTokenStore -> `onUserJwtInvalidated("alice")` fires |
| 7 | Tap "Update JWT" or re-login as "alice" with JWT | Fresh JWT provided |
| 8 | Check logcat | Ops execute with new JWT. User synced to backend |

**Result**: [ ] PASS / [ ] FAIL

#### Test 8D.2: Beta -> this branch, multi-user stuck state

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Install beta branch. Login as "userA" with expired JWT (ops stuck with 401) | Beta's singleton JWT bug: userA's 401 blocks everything |
| 2 | Login as "userB" on beta (overwrites singleton JWT) | Beta may be in inconsistent state |
| 3 | Upgrade to `feat/identity_verification_5.8` | Migration |
| 4 | Open app. Wait for HYDRATE (IV=true) | All stuck beta ops have null externalId -> purged. Clean slate |
| 5 | Login as "userA" with valid JWT | Fresh user creation for userA |
| 6 | Login as "userB" with valid JWT | Fresh user creation for userB |
| 7 | Verify both users on dashboard | Both exist independently |

**Result**: [ ] PASS / [ ] FAIL

---

## Section 9: IV Toggle (Dashboard Changes)

### Test 9.1: IV OFF -> IV ON (between app sessions)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | IV OFF. Login as "alice" (no JWT). User exists on backend | Setup |
| 2 | Close the app (force kill) | App terminated |
| 3 | Turn IV ON in dashboard | IV now enabled |
| 4 | Reopen app | HYDRATE with IV=true |
| 5 | Check logcat | Alice has externalId but no JWT in store. `onUserJwtInvalidated("alice")` fires |
| 6 | Verify ops are gated | No backend requests until JWT provided |
| 7 | Tap "Update JWT" -> externalId="alice", JWT=valid token | JWT provided |
| 8 | Check logcat | Ops resume with JWT Authorization headers |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 9.2: IV ON -> IV OFF

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | IV ON. Login as "alice" with JWT. User exists | Setup |
| 2 | Close the app | App terminated |
| 3 | Turn IV OFF in dashboard | IV disabled |
| 4 | Reopen app | HYDRATE with IV=false |
| 5 | Check logcat | All ops proceed without JWT gating. No Authorization headers. URLs use `onesignal_id` instead of `external_id` |
| 6 | Add a tag | Tag sent without auth header, via onesignal_id URL |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 9.3: Pre-provision JWT before IV ON

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | IV OFF | IV disabled |
| 2 | Login as "alice" with valid JWT | JWT stored unconditionally in JwtTokenStore. Login proceeds normally without auth header |
| 3 | Verify user on backend via standard flow | Alice exists (created via onesignal_id) |
| 4 | Close app | App terminated |
| 5 | Turn IV ON in dashboard | IV enabled |
| 6 | Reopen app | HYDRATE with IV=true |
| 7 | Check logcat | Stored JWT immediately available. No `onUserJwtInvalidated` callback |
| 8 | Add a tag | Request uses `external_id` URL with Authorization header from pre-provisioned JWT |

**Result**: [ ] PASS / [ ] FAIL

---

## Section 10: Edge Cases and Error Handling

### Test 10.1: Callback contains correct externalId

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | IV ON. Login as "alice" with expired JWT | 401 received |
| 2 | Check `onUserJwtInvalidated` event | `event.externalId` == "alice" (the user whose JWT failed, which IS the current user) |
| 3 | Login as "bob" with valid JWT, then update alice's JWT to expired | Bob current, alice has pending ops with bad JWT |
| 4 | Check callback | `event.externalId` == "alice" (NOT "bob", the current user) |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 10.2: Rapid login/logout cycles

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | IV ON. Fresh install, wait for HYDRATE | Setup |
| 2 | Login "a" with jwt -> logout -> login "b" with jwt -> logout -> login "c" with jwt (rapidly) | Multiple user switches |
| 3 | Wait for all ops to settle | Only "c" should have active ops that need to execute |
| 4 | Check demo app | Current user is "c" |
| 5 | Verify in dashboard | User "c" exists. No leaked data from "a" or "b" sink users on "c"'s profile |
| 6 | Check that users "a" and "b" exist on backend (their LoginUserOps executed before logout purged sink data) | Depends on timing -- they may or may not exist |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 10.3: updateUserJwt for non-current user

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | IV ON. Login as "alice" with expired JWT (ops stuck). Then login as "bob" with valid JWT | Bob is current user. Alice has pending ops with bad JWT |
| 2 | Tap "Update JWT" -> externalId="alice", JWT=valid token | Alice's JWT updated |
| 3 | Check logcat | Alice's pending ops (from earlier) now execute with the new JWT |
| 4 | Check demo app | Current user remains "bob" |
| 5 | Verify in dashboard | Both alice and bob exist with correct data |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 10.4: No internet at startup, login, kill, internet on, reopen

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Enable airplane mode. Fresh install | No internet |
| 2 | Open app | `initWithContext` called. Anonymous op enqueued. No HYDRATE possible |
| 3 | Login as "alice" with valid JWT | Alice's `LoginUserOperation` enqueued. JWT stored |
| 4 | Force-kill the app | Ops persisted |
| 5 | Disable airplane mode | Internet restored |
| 6 | Reopen app | Persisted ops loaded. HYDRATE arrives (IV=true). Anonymous ops purged |
| 7 | Check logcat | Alice's ops execute with JWT |
| 8 | Verify in dashboard | Alice exists |

**Result**: [ ] PASS / [ ] FAIL

---

### Test 10.5: Delete user on server, then new session (IV ON)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | IV ON. Login as "alice" with JWT. Verify user on backend | Setup |
| 2 | Delete user "alice" via OneSignal Dashboard or API | User removed from backend |
| 3 | Background app 60+ seconds, reopen | New session triggered |
| 4 | Check logcat | Session-related ops for alice may fail with an error. App should not crash |
| 5 | Check app behavior | SDK handles error gracefully |

**Result**: [ ] PASS / [ ] FAIL

---

## Section 11: IV OFF Regression

Ensure all existing v5 behavior remains unchanged when IV is OFF. These are NOT new tests -- they are the standard v5 test suite that must still pass.

### Test 11.1: Anonymous user creation on startup

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | IV OFF. Fresh install. Open app | Anonymous user created on backend |
| 2 | Verify in dashboard | User exists with push subscription |

**Result**: [ ] PASS / [ ] FAIL

### Test 11.2: Login with externalId (no JWT)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | IV OFF. Login as "alice" (no JWT) | User created/identified on backend via standard flow |
| 2 | Verify | No Authorization headers. onesignal_id-based URLs |

**Result**: [ ] PASS / [ ] FAIL

### Test 11.3: Logout creates new anonymous user on backend

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | IV OFF. Logged in as "alice". Tap Logout | Standard logout |
| 2 | Verify | New anonymous user created on backend. Push subscription transferred |

**Result**: [ ] PASS / [ ] FAIL

### Test 11.4: Tags, aliases, email/SMS

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | IV OFF. Logged in. Add tags, aliases, email, SMS | Standard operations |
| 2 | Verify in dashboard | All data on user's profile. No auth headers. onesignal_id URLs |

**Result**: [ ] PASS / [ ] FAIL

### Test 11.5: IAM fetching

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | IV OFF. IAM configured. Trigger new session | IAM fetched |
| 2 | Verify | IAM displays. No auth headers. onesignal_id-based URL |

**Result**: [ ] PASS / [ ] FAIL

### Test 11.6: Cached requests (offline/online)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | IV OFF. Airplane mode. Add tags, aliases | Ops queued |
| 2 | Disable airplane mode | Ops flush and succeed |

**Result**: [ ] PASS / [ ] FAIL

### Test 11.7: v4 -> v5 migration (IV OFF)

| Step | Action | Expected Result |
|------|--------|-----------------|
| 1 | Install v4, register player. Upgrade to this branch. IV OFF | Standard migration |
| 2 | Verify | `LoginUserFromSubscriptionOperation` succeeds. Legacy player linked to new user |

**Result**: [ ] PASS / [ ] FAIL

---

## Testing Checklist Summary

For each migration path (New Install, v4, v5 no-IV, Beta), verify:

| Check | New Install | v4 | v5 (no IV) | Beta |
|-------|:-----------:|:--:|:----------:|:----:|
| IV ON: No anonymous user created on backend | [ ] | [ ] | [ ] | [ ] |
| IV ON: Login with valid JWT creates user | [ ] | [ ] | [ ] | [ ] |
| IV ON: Login with invalid JWT fires callback | [ ] | [ ] | [ ] | [ ] |
| IV ON: updateUserJwt unblocks pending ops | [ ] | [ ] | [ ] | [ ] |
| IV ON: Logout creates local-only sink user, push disabled | [ ] | [ ] | [ ] | [ ] |
| IV ON: Multi-user JWT isolation (A's bad JWT doesn't block B) | [ ] | [ ] | [ ] | [ ] |
| IV ON: Cold start restores ops and JWTs correctly | [ ] | [ ] | [ ] | [ ] |
| IV ON: IAM fetch uses external_id + JWT | [ ] | [ ] | [ ] | [ ] |
| IV OFF: Identical to current v5 behavior (no regressions) | [ ] | [ ] | [ ] | [ ] |
| Migration-specific: Correct handling of legacy data | N/A | [ ] | [ ] | [ ] |
