# BotHub for Android

Control your [BotHub](https://github.com/Kljub/BothubV3) bots from the phone,
and confirm your BotHub sign-ins with **BotHub Guard**.

- **Bots**: live status, start, stop, restart, number of servers.
- **Bot details**: the log in your language, modules and plugins on and off.
- **BotHub Guard** (like Steam Guard): approve or deny a sign-in that waits
  for your phone, or type the 6-digit code the app shows (works offline).
- **Notifications**: choose the events now; push comes with a later BotHub
  version.
- **Updates**: the app checks the GitHub releases of this repo every 6 hours
  (and on demand) and installs a new version after you confirm. Only
  published releases count, never drafts or pre-releases.

## Pairing

1. BotHub dashboard → Settings → **Mobile App** → **Show QR code**.
2. In the app: **Scan QR code** (or open the `bothub://pair?…` link), confirm
   the phone's name.

No password is typed on the phone. The app makes a P-256 key in the Android
Keystore (StrongBox when the phone has it); BotHub gets only its public key.
Every request carries the device token, the time and an ECDSA signature over
`bothub-mobile:<time>:<METHOD>:<path?query>`, so a copied token alone is
useless and a request cannot be replayed. Unpairing (app or dashboard) ends
the access at once; the app then makes a new key.

BotHub Guard is switched on per phone in the dashboard: Settings → Security →
BotHub Guard.

## Project

| Module | What it is |
|---|---|
| `core` | Pure Kotlin: API client, request signing, pairing links, Guard codes (TOTP), update check. Tested on the JVM. |
| `app` | Android app (Jetpack Compose, Material 3): keystore signer, encrypted storage, screens, updater. |

The API the app uses is described in [docs/API.md](docs/API.md).

## Build and test (Docker)

Docker Desktop has no KVM, so there is no emulator; everything else runs in
a container:

```sh
docker build -t bothub-android docker
docker run --rm -v "$PWD:/app" -v bothub-gradle:/root/.gradle -w /app bothub-android \
  ./gradlew :core:test :app:testDebugUnitTest :app:assembleDebug
```

- `core` tests: signing (verified like the BotHub gateway), pairing links,
  RFC 6238 Guard codes, update check, the client against a mock server.
- `app` tests: Compose screens with Robolectric.
- **End-to-end**: the app's client against a real BotHub in Docker (sign-in,
  QR code, pairing, signed calls, Guard approval, Guard code, unpairing):

```sh
docker run --rm -e BOTHUB_E2E_URL=http://host.docker.internal:8095 \
  -e BOTHUB_E2E_USER=<user> -e BOTHUB_E2E_PASSWORD=<password> \
  -v "$PWD:/app" -v bothub-gradle:/root/.gradle -w /app bothub-android \
  ./gradlew :core:test --tests '*EndToEnd*'
```

The debug APK (`app/build/outputs/apk/debug/app-debug.apk`) installs on any
phone with USB debugging (`adb install`).

## Releases

```sh
scripts/release.sh 1.0.0 [notes.md]
```

builds and tests in Docker, signs with the release key and publishes the
GitHub release `v1.0.0` with `BotHub-1.0.0.apk`. The key lives outside the
repo (`../BotHubAndroitApp-signing`). **Keep a backup of it**: Android installs
an update only when it is signed with the same key; without it, users have to
uninstall and install again.

## License

PolyForm Noncommercial 1.0.0, like BotHub.
