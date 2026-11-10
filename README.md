<p align="center">
  <img src="docs/assets/rejoin.svg" alt="Rejoin for Android. Automatic Wi-Fi sign-in." width="100%">
</p>

<p align="center">
  <a href="https://github.com/EithonX/captive/actions/workflows/build-debug-apk.yml"><img src="docs/assets/get-builds.svg" alt="Get beta builds from GitHub Actions" width="166" height="42"></a>
</p>

## Get started

1. Connect to Wi-Fi and save the network in Rejoin.
2. Enter the login you use on its sign-in page.
3. Turn on **Auto sign-in** if you want Rejoin to sign you back in when your session expires.

Manage networks and logins in **Settings**. One default login can cover several networks, or you can assign a different login to each one.

**Private beta · Android 8.0 or newer.** The current adapter supports Chandigarh University’s FortiGate portal. Rejoin encrypts saved passwords on your phone and asks permission before using the portal’s unencrypted HTTP sign-in. [Privacy](PRIVACY.md) · [Security](SECURITY.md)

## Build

Use JDK 17 and Android SDK 35. On Windows, use `gradlew.bat`.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Download **Rejoin-debug-apk** from a successful Actions run. Beta 3 uses the application ID `com.eithonx.rejoin` and installs separately from earlier betas. Set up your Wi-Fi and login again in the new app.

[Architecture](docs/architecture.md) · [Releases](https://github.com/EithonX/captive/releases) · [Changelog](CHANGELOG.md) · [EithonX](https://github.com/EithonX)
