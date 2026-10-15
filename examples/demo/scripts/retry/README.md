# Retry test harness

Injects failures into the SDK's requests from the demo app, so OperationRepo retries and backoff can
be watched and checked. A [mitmproxy](https://mitmproxy.org) addon fails only requests that carry
the SDK's `SDK-Version` header. The demo's own REST calls and all other traffic pass through. Only
`onesignal.com` traffic is decrypted.

```bash
brew install mitmproxy mobile-dev-inc/tap/maestro
cd examples/demo/scripts/retry
./retry-test.sh help
```

[Maestro](https://maestro.mobile.dev) drives the demo's UI (`tap`) through the flows in `flows/`.

## Setup

1. Start an emulator, or connect a device. With more than one, pick it with
   `RETRY_TEST_SERIAL=<adb serial>`.
2. `./retry-test.sh install` builds the demo against the local SDK source and installs it. The
   debug build trusts the mitmproxy CA (`-PRETRY_TEST_CA`), so nothing has to be installed on the
   device. If a demo signed with another key is already installed, `adb uninstall
   com.onesignal.example` first.
3. `./retry-test.sh start` starts mitmproxy on port 8899 and sets the device's global HTTP proxy.
   An emulator reaches it at `10.0.2.2`, a physical device through `adb reverse`.
4. `./retry-test.sh fresh` clears the demo's data so the SDK starts as a new install.
5. In another terminal, `./retry-test.sh watch` follows each SDK request.
6. When done, **`./retry-test.sh stop`** clears the device's proxy. Until then the whole device
   goes through the proxy.

## Commands

| Command | Does |
| --- | --- |
| `fail <route> <status> [--times N] [--retry-after S] [--add]` | Fail matching requests. Status `0` drops the connection, like being offline. Without `--times` it fails until `clear`. `--add` keeps earlier rules. Applies right away. |
| `clear` / `rules` | Remove or show the rules |
| `report [regex]` | Per operation: each attempt, the gap since the previous one, and the gap expected. Also lists any SDK request sent during a Retry-After |
| `reset-log` | Forget recorded attempts, for example between scenarios |
| `tap <action> [args]` | Use the demo: `add-tag <key> <value>`, `add-alias <label> <id>`, `add-email <email>`, `login <external id> [jwt]`, `logout`, `toggle-push`, `allow-notifications`. With `RETRY_TEST_IV_KEY` set, `login` signs a JWT when none is given |
| `jwt <external id> [label=id] [email:addr] [sms:number]` | Print an Identity Verification JWT, valid for an hour, that claims those aliases and subscriptions |
| `install` | Build the demo against the local SDK and install it |
| `fresh` / `relaunch` | Clear the demo's data and relaunch / kill and relaunch |
| `background` / `foreground` | Press Home / bring the demo back |
| `network-flap [s]` | Turn the device's Wi-Fi and mobile data off for `s` seconds (default 10). Turns back on only what was on, even after Ctrl-C. Not over wireless adb |
| `logs` | Stream the demo's OperationRepo and HttpClient logs |

Routes: `create-user`, `fetch-user`, `update-user` (tags, properties, session data), `identity`
(add alias and login's Identify User share it), `remove-alias`, `create-sub`, `update-sub`,
`delete-sub`, `transfer-sub`, `identity-by-sub`, `custom-events`, `all`.

## Reading `report`

```
POST apps/<app>/users body=b8398c6a
    #     time status     body      gap         expected  note
    1     0.0s   408* b8398c6a
    2     3.9s   408* b8398c6a     3.9s        2.5-10.0s  backoff #1
    3    11.2s   201  b8398c6a     7.3s        5.0-15.0s  backoff #2
```

Attempts are grouped per record (full path), and by body for routes without an id in the path
(Create User, custom events).

What the SDK should do:

- **Backoff per request**: after the Nth retryable failure the request waits 5s × 2^(N-1), capped at
  300s (`opRepoBaseFailRetryBackoff`, `opRepoMaxFailRetryBackoff`), times a jitter of 0.5 to 1.
  Operations batched into one request share the wait. `HttpClient` does not re-send on its own.
  Success is 200, 201, 202 and 304. Retryable is anything else but 400, 401, 402, 403, 404, 409
  and 410, which end the request. With Identity Verification a 401 or 403 instead holds it, and
  its retry count, until a new JWT is set. `report` flags the same body sent again after one of
  those, which is only fine after a 401 or 403 once a new JWT is set.
- **Retry-After** is the minimum wait for that request, capped at 1h. `HttpClient` also holds
  every SDK request, on any route, until it passes, and `report` lists any that weren't. Foreground
  and network return do not cut it short. A 429 without Retry-After, or with an unparsable one,
  waits 60s.
- **Other records keep going**: while one request backs off, operations on other records are sent
  (outside a Retry-After). Operations on the same record wait behind it, so order is kept.
- **Foreground, and network return while in the foreground,** end the backoff, and the attempt goes
  out right away without the queue's 5s batching wait. `report` flags those gaps as EARLY with a
  note. A network return in the background does nothing; the timed retry or the next foreground
  picks it up.
- `report` allows 5s of slack past the window (`opRepoExecutionInterval`), since an enqueue near
  the end of a backoff can add the batching wait.
- Backoff state is in memory. After a relaunch, queued operations are sent right away.

## Scenarios

Run `reset-log` before each one. Unless noted, the request should succeed after the last failure,
and nothing should need a relaunch.

1. **Backoff**: `fail create-user 408 --times 3`, then `fresh`. Gaps within 2.5-5s, 5-10s and
   10-20s (plus slack), then 201.
2. **Other records are not blocked**: `fail update-user 408`, `tap add-tag k v`, then after the
   first failure `tap toggle-push`. The subscription PATCH goes out while the tag keeps retrying.
3. **Retry-After**: `fail update-user 429 --times 1 --retry-after 40`, then `tap add-tag k v`.
   One retry after about 40s. `background` then `foreground` during the wait does not cut it short,
   and a `tap toggle-push` during the wait is not sent until it ends.
4. **Foreground ends the backoff**: `fail update-user 408`, `tap add-tag k v`, wait for four
   failures (a 20-40s wait), then `background` and `foreground`. An attempt goes out right after
   foreground.
5. **Network return**: as in 4, but `network-flap 5` with the demo in the foreground. An attempt
   goes out right after the network is back. With the demo in the background, the flap changes
   nothing.
6. **Order is kept**: `fail create-user 408 --times 2`, then `fresh` and `tap login <id>` during
   the failures. Identify User (PATCH `.../identity`) is not sent until Create User succeeds.
7. **Fetch User**: `fail fetch-user 408 --times 2`, then `tap login <id>`. Fetch User retries in
   the same run.
8. **Kill during backoff**: `fail update-user 408`, `tap add-tag k v`, wait for two failures,
   `clear`, then `relaunch`. The cached request is sent right after launch.
9. **Not retried**: `fail update-user 400 --times 1`, then `tap add-tag k v`. One attempt.
10. **Offline**: `fail all 0`, then `tap add-tag k v`, `tap add-alias l i` and `tap add-email e`.
    Wait for a few failures, then `clear`. Everything goes out on its next attempt.
11. **Login/logout while failing**: `fail all 408`, `tap login b`, `tap logout`, `tap login c`,
    then `clear`. Requests for each user go out in order.
12. **Identity Verification**: needs an app with Identity Verification on and its ES256 private
    key. Ask the SDK team for both and keep the key outside the repo. Set
    `RETRY_TEST_IV_APP_ID=<app id>` and `RETRY_TEST_IV_KEY=<path to the key>`, then `install` again.
    `tap login <id>` now signs a JWT. No anonymous Create User is sent, so in 1 and 6 the Create
    User comes from the login, and 6 checks that a `tap add-tag` during the failures waits for it.
    Repeat 1, 2, 6 and 10. In 10, log in with a JWT that claims the alias and email:
    `tap login <id> "$(./retry-test.sh jwt <id> l=i email:e)"`.

## Troubleshooting

- `tap` types nothing: newer emulator images show a stylus handwriting prompt on text fields.
  `install` turns it off (`settings put secure stylus_handwriting_enabled 0`).
- Every SDK request fails with status 0 right after `start`: the installed demo was not built by
  `install`, so it does not trust the mitmproxy CA.
- The emulator's clock can drift from the Mac's, so compare logcat times with `report` carefully.
