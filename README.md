<p align="center">
  <img src="docs/assets/rejoin.svg" alt="Rejoin for Android. Automatic Wi-Fi sign-in." width="100%">
</p>

Rejoin signs you back into Wi-Fi when your portal session expires. It uses saved logins only on networks you choose.

## Install

Download `Rejoin.apk` from a signed [release](https://github.com/EithonX/rejoin/releases). Rejoin runs on Android 6.0 and newer. The universal APK includes ARMv7, ARM64, x86, and x86-64.

The current adapter supports Chandigarh University’s FortiGate portal. Its login uses unencrypted HTTP. Rejoin encrypts saved passwords on your phone and asks before sending them to the portal. [Privacy](PRIVACY.md) · [Security](SECURITY.md)

## Set up

1. Connect to Wi-Fi and save the network in Rejoin.
2. Enter the username and password you use on its sign-in page.
3. Turn on **Auto sign-in** and grant background location access if Android asks for it.

Manage saved networks and logins in **Settings**. Open **Settings → About** to check for an update.

## Build

Use JDK 17 and Android SDK 35. On Windows, run `.\gradlew.bat` in place of `./gradlew`.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

The [build workflow](https://github.com/EithonX/rejoin/actions/workflows/build-debug-apk.yml) also provides an optimized, test-signed APK.

[Architecture](docs/architecture.md) · [Changelog](CHANGELOG.md) · [Contributing](CONTRIBUTING.md)
