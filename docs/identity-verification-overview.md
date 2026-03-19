# Identity Verification in the OneSignal Android SDK

## What is Identity Verification?

Identity verification prevents impersonation by requiring a server-generated JWT (JSON Web Token) to accompany API requests that act on a user. When enabled, the SDK attaches a `Bearer` token to every outgoing HTTP request, and the OneSignal backend rejects any request that carries an invalid or missing token.

The feature is **controlled server-side**: the OneSignal dashboard sets a flag (`jwt_required`) that the SDK fetches at startup via the remote params endpoint. The SDK stores this in `ConfigModel.useIdentityVerification`.

---

## End-to-End Flow

```
┌──────────┐   login(externalId, jwt)   ┌──────────────┐
│  App     │ ─────────────────────────► │  SDK         │
│  Code    │                            │              │
│          │ ◄── onUserJwtInvalidated ──│  OperationRepo│
│          │                            │              │
│          │   updateUserJwt(id, jwt)   │              │
│          │ ─────────────────────────► │              │
└──────────┘                            └──────┬───────┘
                                               │
                                   Authorization: Bearer <jwt>
                                               │
                                               ▼
                                        ┌──────────────┐
                                        │  OneSignal   │
                                        │  Backend     │
                                        └──────────────┘
```

### Step by step

1. **App fetches remote params** — The SDK calls `android_params.js`. The response contains `"jwt_required": true/false`, which sets `ConfigModel.useIdentityVerification`.

2. **App logs in with a JWT** — The app calls `OneSignal.login(externalId, jwtBearerToken)`. The JWT is stored in `IdentityModel.jwtToken`.

3. **Operations are stamped** — When any operation is enqueued into `OperationRepo`, the current JWT and external ID are copied onto the operation (`operationJwt`, `operationExternalId`). This means each operation remembers *which user* created it, even if the active user changes later.

4. **Queue filtering** — Before executing, `OperationRepo.getNextOps()` applies two filters when identity verification is on:
   - **Discards anonymous operations**: operations with no `operationExternalId` that require JWT are dropped.
   - **Blocks JWT-less operations**: operations that require JWT but have `operationJwt == null` are skipped (they sit in the queue waiting for a token).

5. **HTTP request** — The executor passes the JWT to the backend service, which sets the `Authorization: Bearer <jwt>` header via `HttpClient`.

6. **Alias selection** — When identity verification is enabled and the operation has an external ID, executors identify the user by `external_id` instead of `onesignal_id`:
   ```kotlin
   if (_configModelStore.model.useIdentityVerification && operationExternalId != null) {
       Pair(IdentityConstants.EXTERNAL_ID, operationExternalId!!)
   } else {
       Pair(IdentityConstants.ONESIGNAL_ID, onesignalId)
   }
   ```

---

## What Happens on a 401/403 (Unauthorized)

When the backend returns **401 or 403**, the executor reports `FAIL_UNAUTHORIZED`. The `OperationRepo` then:

1. **Clears the JWT** on the failed operations (`operationJwt = null`) and re-queues them at the front.
2. **Increments a retry counter** (`unauthorizedRetries`) on each operation.
3. **Fires `IUserJwtInvalidatedListener`** so the app can fetch a fresh token from its own backend.
4. If an operation exceeds **3 unauthorized retries**, it is **dropped permanently**.

The operations sit in the queue with a null JWT, effectively paused, until the app provides a new token.

### Recovery

When the app calls `OneSignal.updateUserJwt(externalId, newToken)`:

- Every queued operation matching that `externalId` gets the new JWT.
- The retry counter resets to 0.
- The operation queue is woken up to resume processing.

---

## Startup Behavior (`IdentityVerificationService`)

On app cold start, the `IdentityVerificationService` listens for the config model to hydrate from cached remote params. Once hydrated:

- If identity verification is **on** and no JWT is cached, it logs a message and **does not enqueue** the login operation. The SDK waits for the app to call `login(externalId, jwt)`.
- If identity verification is **off**, or a JWT **is** cached, it enqueues a `LoginUserOperation` automatically to re-establish the session.

---

## Public API

### Login with JWT

```kotlin
OneSignal.login("user_123", jwtToken)
```

Both the external ID and JWT are required when identity verification is enabled. Calling `login` without a JWT when verification is on will log a warning and block the login.

### Update an Expired JWT

```kotlin
OneSignal.updateUserJwt("user_123", freshJwtToken)
```

Call this after receiving the invalidation callback (or proactively when you know the token is about to expire).

### Listen for JWT Invalidation

```kotlin
OneSignal.addUserJwtInvalidatedListener { event ->
    val externalId = event.externalId
    // Fetch a new JWT from your server, then:
    OneSignal.updateUserJwt(externalId, newJwt)
}
```

### Operations That Skip JWT

Most operations require JWT when identity verification is on. The exception is `UpdateSubscriptionOperation`, which overrides `requiresJwt` to `false` — subscription updates (e.g. push token refresh) can proceed without a valid JWT.

---

## Key Classes

| Class | Responsibility |
|---|---|
| `ConfigModel` | Stores `useIdentityVerification` (from `jwt_required` remote param) |
| `IdentityModel` | Stores the current user's `externalId` and `jwtToken` |
| `Operation` | Base class; carries `operationJwt`, `operationExternalId`, and `requiresJwt` |
| `OperationRepo` | Stamps JWT on enqueue, filters queue, handles 401 retry logic, fires invalidation callbacks |
| `IdentityVerificationService` | Bootstrap service that gates the initial login operation on JWT availability |
| `HttpClient` | Attaches `Authorization: Bearer` header when JWT is present |
| `IUserJwtInvalidatedListener` | Callback interface the app implements to be notified of expired tokens |

---

## Summary

- Identity verification is a **server-controlled** feature toggled by `jwt_required` in remote params.
- The JWT is **stamped per-operation** at enqueue time, tying each operation to a specific user.
- On **401/403**, operations are paused and the app is notified via `IUserJwtInvalidatedListener`.
- The app recovers by calling `updateUserJwt`, which re-arms all queued operations for that user.
- After **3 failed retries**, an operation is permanently dropped.
- `UpdateSubscriptionOperation` is exempt from JWT requirements so push token updates are never blocked.
