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
| `report [regex]` | Per route: each attempt, the gap since the previous one, and the gap expected |
| `reset-log` | Forget recorded attempts, for example between scenarios |
| `tap <action> [args]` | Use the demo: `add-tag <key> <value>`, `add-alias <label> <id>`, `add-email <email>`, `login <external id>`, `logout`, `toggle-push`, `allow-notifications` |
| `install` | Build the demo against the local SDK and install it |
| `fresh` / `relaunch` | Clear the demo's data and relaunch / kill and relaunch |
| `background` / `foreground` | Press Home / bring the demo back |
| `network-flap [s]` | Turn the device's Wi-Fi and mobile data off for `s` seconds (default 10) |
| `logs` | Stream the demo's OperationRepo and HttpClient logs |

Routes: `create-user`, `fetch-user`, `update-user` (tags, properties, session data), `identity`
(add alias and login's Identify User share it), `remove-alias`, `create-sub`, `update-sub`,
`delete-sub`, `transfer-sub`, `identity-by-sub`, `custom-events`, `all`.

## Reading `report`

```
POST apps/<app>/users
    #     time status     body      gap         expected  note
    1     0.0s   408* b8398c6a
    2    15.0s   408* b8398c6a    15.0s       15.0-21.0s  backoff #1
    3    45.3s   201  b8398c6a    30.3s       30.0-36.0s  backoff #2
```

What the SDK should do:

- **Backoff per operation**: after the Nth retryable failure an operation waits N × 15s
  (`opRepoDefaultFailRetryBackoff`), capped at 1h, with no jitter. `HttpClient` does not re-send on
  its own. Retryable is anything but 400, 401, 402, 403, 404, 409 and 410.
- **Retry-After** is the minimum wait for that operation, capped at 1h. Foreground and network
  return do not cut it short. Other routes are not held. A 429 without Retry-After waits 60s.
- **Other records keep going**: while one operation waits, operations on other records are sent.
  Operations on the same record wait behind it, so order is kept.
- **Foreground and network return** end the backoff. The next attempt goes out after the queue's
  usual 5s batching wait (`opRepoExecutionInterval`), so `report` allows up to 6s of slack, and
  flags shorter gaps as EARLY with a note.
- Backoff state is in memory. After a relaunch, queued operations are sent right away.

## Scenarios

Run `reset-log` before each one. Unless noted, the request should succeed after the last failure,
and nothing should need a relaunch.

1. **Backoff**: `fail create-user 408 --times 2`, then `fresh`. Gaps of 15s and 30s, then 201.
2. **Other records are not blocked**: `fail update-user 408`, `tap add-tag k v`, then after the
   first failure `tap toggle-push`. The subscription PATCH goes out while the tag keeps retrying.
3. **Retry-After**: `fail update-user 429 --times 1 --retry-after 40`, then `tap add-tag k v`.
   One retry after about 40s. `background` then `foreground` during the wait does not cut it short.
4. **Foreground ends the backoff**: `fail update-user 408`, `tap add-tag k v`, wait for two
   failures, then `background` and `foreground`. An attempt goes out about 5s after foreground.
5. **Network return**: as in 4, but `network-flap 5` instead. An attempt goes out shortly after the
   network is back.
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
12. **Identity Verification**: repeat 1, 2 and 6 with an app that requires JWT.

## Troubleshooting

- `tap` types nothing: newer emulator images show a stylus handwriting prompt on text fields.
  `install` turns it off (`settings put secure stylus_handwriting_enabled 0`).
- Every SDK request fails with status 0 right after `start`: the installed demo was not built by
  `install`, so it does not trust the mitmproxy CA.
- The emulator's clock can drift from the Mac's, so compare logcat times with `report` carefully.
