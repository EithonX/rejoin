# Rejoin

**Campus Wi-Fi that reconnects itself.** A small, native Android app by [EithonX](https://github.com/EithonX).

Rejoin is meant to be configured once and mostly forgotten. It checks the selected Wi-Fi connection, recognizes when a supported captive portal asks for sign-in, and uses your own authorized credentials to restore the session.

> **Private beta, not a public production release.** The FortiGate adapter was verified with one CU installation; it is not a universal captive-portal login implementation. CU's observed login form uses **unencrypted HTTP**, meaning credentials are exposed in transit. This build keeps credentials encrypted at rest and requires explicit opt-in, but cannot fix the server's transport security.

## Use it

1. Open Rejoin. First-time users get a short three-step setup: **save the current Wi-Fi, save your login, and opt into auto-reconnect**.
2. A saved password is confirmed clearly; it is never displayed again.
3. Rejoin's Home screen shows the current connection state and only the relevant next action.
4. Change auto-reconnect on its own screen: **Enable auto-reconnect**, or **Pause** with confirmation. No ambiguous toggle.
5. If Android restricts background Wi-Fi checks, open **Settings → Auto-reconnect → Background access** to review the reason.

Existing users with saved Wi-Fi and credentials skip first-run setup. If you leave setup partway through, it resumes at the remaining step; choosing **Not now** doesn't force setup again at every launch.

That's it. For most people, **one saved Wi-Fi and one saved login** are all that's needed.

### Optional: more than one Wi-Fi or login

You can save up to 32 exact Wi-Fi access points, and up to 16 encrypted logins. Every Wi-Fi automatically uses your default login. For a particular router, open **Settings → Trusted Wi-Fi → Wi-Fi details → Change login** to override that default. An explicitly assigned account isn't silently replaced if it's missing; removing a linked account asks for confirmation and returns those routers to the default login.

Saving a new Wi-Fi requires connecting to it first. Rejoin matches both SSID and BSSID. It does **not** trust every access point merely because it broadcasts the same network name.

## What happens in the background?

Rejoin has no always-running polling service. It listens for Wi-Fi changes while open, registers a Wi-Fi availability callback, and uses a **persistent Android periodic job** to avoid losing future checks if the app process is killed. A separate one-shot check runs when Android detects a newly available Wi-Fi network. A small IP-only check is made only when needed; a failed or uncertain network check **doesn't** authorize sending a password.

| Setting | Healthy-network backup check |
| --- | --- |
| Balanced | Requested after about 30 minutes |
| Responsive | Requested after about 15 minutes |

Android may delay either check during Doze or battery restrictions. An upstream FortiGate timeout can be silent, so instant recovery can't be guaranteed. **Rejoin → Settings → Background access** reports the last actual background check and whether the app needs location/battery permissions. On supported Motorola phones, choose **Always allow** under Battery → Manage background apps when you want reliable unattended recovery; this remains the user's decision. See [technical notes](docs/architecture.md) for limitations.

## Security and privacy

- Credentials are encrypted on the phone with **Android Keystore AES-GCM**; backup is disabled.
- The app doesn't run an account server, collect analytics, or store authentication tokens in event history.
- Public connectivity tests contact Cloudflare's public IP endpoint; this is distinct from the campus login traffic.
- Portal submission is currently limited to the known CU FortiGate gateway, on an explicitly trusted Wi-Fi.
- SSID/BSSID matching limits accidental submissions but is **not cryptographic proof** of network identity. The existing HTTP form carries a credential-exposure risk.
- Delete saved logins from Settings. Clearing app data or uninstalling also removes the stored keys.

Read [Privacy](PRIVACY.md) and [Security](SECURITY.md) before using the beta on another network.

## Build and test

The repository uses Gradle, Kotlin and Android SDK 35. GitHub Actions runs JVM policy/profile tests and builds a debug APK.

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

Download `Rejoin-debug-apk` from the most recent successful [Actions run](https://github.com/EithonX/captive/actions) (requires access to the private repository).

**Signing:** hosted debug builds may use different signing keys across runs; Android may reject an update until the earlier build is removed. Removing it erases encrypted credentials and profile data. Stable release signing is intentionally not stored in this repository and should be configured privately before distribution.

## Project layout

- `MainActivity`: connection-first dark home screen with one contextual action
- `SetupActivity`: resumable three-step Wi-Fi and login setup
- `ProtectionActivity`: explicit auto-reconnect control, background access and check frequency
- `DiagnosticsActivity`: separate user-initiated network tests
- `SettingsActivity`, `NetworkDetailsActivity`, `LoginsActivity`: profile and account management
- `TrustedNetworks`: trusted access points and optional account assignments
- `CredentialVault`: encrypted logins with migration from the v1 single-credential format
- `SessionEngine`: conservative single-flight recovery
- `FortinetLoginV2`: working campus-specific authentication adapter
- `InternetVerifier`: Wi-Fi-bound, DNS-independent verification
- `RecoveryScheduler`: persistent periodic Android job plus one-shot Wi-Fi event checks
- `BackgroundHealth`: actionable checks for background location, JobScheduler and battery restrictions

The previously working builds are preserved on `backup/rejoin-v2` and `backup/campus-autoconnect-v1`.

## Maintainer

**[EithonX](https://github.com/EithonX)**

Bug reports should include the Rejoin version, Android version, behavior observed and **redacted** diagnostic messages. Never include passwords, cookies, `magic` tokens or device identifiers.

Licensing and publication decisions remain with the repository owner.
