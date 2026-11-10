<p align="center">
  <img src="docs/assets/rejoin.svg" alt="Rejoin for Android. Signs you back in when campus Wi-Fi expires." width="100%">
</p>

<p align="center">
  <a href="https://github.com/EithonX/captive/actions/workflows/build-debug-apk.yml"><img src="docs/assets/get-builds.svg" alt="Get beta builds from GitHub Actions" width="166" height="42"></a>
</p>

## Use Rejoin

1. Connect to your campus Wi-Fi and save it in Rejoin.
2. Save the campus login you already use.
3. Turn on **Auto-reconnect** if you want Rejoin to sign you back in automatically. You can also reconnect manually from Home.

One saved Wi-Fi and one login are enough to start. To use a different account on another router, open **Settings → Saved Wi-Fi**, select the network and choose **Change login**. Rejoin matches the specific saved access point, even if another router broadcasts the same name.

**Private beta · Android 8.0 or newer.** The sign-in adapter is built for Chandigarh University’s FortiGate portal. Credentials are encrypted on the device. The campus portal uses unencrypted HTTP, so your password can be exposed during sign-in. Rejoin asks before using it. [Privacy](PRIVACY.md) · [Security](SECURITY.md)

## Build

Use JDK 17 and Android SDK 35. Run `gradlew.bat` in place of `./gradlew` on Windows.

```sh
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

For a prebuilt APK, open a successful Actions run and download **Rejoin-debug-apk**. Debug APKs from different runs can have different signing certificates. See the [release guide](docs/releasing.md) before replacing an installed build that has saved data.

[Architecture](docs/architecture.md) · [Release builds](docs/releasing.md) · [Changelog](CHANGELOG.md) · [EithonX](https://github.com/EithonX)
