# Rejoin

**Stay connected, quietly.**

Rejoin is a native Android utility for a user's **own authorized campus captive-portal connection**. The first supported adapter uses the FortiGate authentication flow verified on Chandigarh University's network. It is not a generic bypass of network access rules.

## What works

- Manual FortiGate login through the user's explicitly trusted Wi-Fi; the v1 beta adapter was verified on a physical Android phone.
- One-tap connection checks and repair, with no credential submission if internet access is already verified.
- Android Keystore-backed encryption for credentials stored on the device; backup disabled.
- Event-driven monitoring while the app is visible, with duplicate-event suppression.
- A PendingIntent Wi-Fi availability callback registered for automatic mode; a receiver restores registration after device reboot or app update.
- Adaptive, one-shot `JobScheduler` maintenance instead of an always-running polling service.
- IP-only verification that does not require changing Android Private DNS, and does not silently fall back to mobile data.
- A small on-device activity history without credentials, tokens or cookies.

## One-time setup

1. Connect to **your own** Wi-Fi/router.
2. In Settings, select that Wi-Fi access point. This stores its SSID and BSSID, and the app checks both before authentication.
3. Save your own university credentials, after reading and accepting the campus HTTP transport warning.
4. Enable automatic recovery on the Home screen.
5. If Android doesn't permit Wi-Fi identity access in the background, review the app's location settings and choose background access where available.

For a group sharing one router, initially configure **one primary phone** for automatic recovery. Random delays reduce collisions but do not implement full cross-device coordination.

## Scheduling

| Condition | Balanced | Responsive |
|---|---|---|
| Internet confirmed healthy | ~30 min | ~15 min |
| Session just restored | ~25 min | ~15 min |
| Portal suspected | ~15 min | ~15 min |
| Verification inconclusive | ~20 min | ~15 min |
| Recovery failed | ~30 min | ~20 min |

These are **requested minimum delays**, not exact timers. Android may defer background jobs during Doze, app standby and battery optimization. A Wi-Fi-available event can bring a check forward. Silent upstream FortiGate expiry cannot be observed reliably without some network verification.

## Design

Name: **Rejoin**. The mark is a broken circular link reconnecting. The interface deliberately prioritizes one status and two actions, rather than showing diagnostics in the normal path.

Palette: porcelain `#F6F8F5`, botanical ink `#19302B`, jade `#1F6954`, mint `#D7EFE3`. All views and icons are native Android resources; no WebView, Compose runtime or analytics SDK.

## Security and limitations

**Important:** CU's observed HTTP captive portal does not encrypt credentials in transit. The on-device vault does not solve this transport risk. Rejoin requires opt-in, restricts the destination to the known campus gateway, never follows an arbitrary login POST destination, and does not disable TLS verification for public HTTPS checks.

The FortiGate adapter is currently campus-specific; a public, general-purpose version requires more portal types, independently verified secure authentication endpoints, signing and update infrastructure, broader device testing, and a security review. **Do not redistribute this debug APK as a public production app.**

Shizuku/root are neither needed nor requested. The app cannot bypass system power restrictions or force Android to display a connected status.

## Building

GitHub Actions runs `:app:testDebugUnitTest` and `:app:assembleDebug` with Java 17 and Android 35. Open **Actions → Build Android APK → Artifacts** to obtain the installable debug build.

The application ID `org.campus.autoconnect` is retained intentionally to support upgrades from earlier prototypes when Android signing keys match. GitHub-hosted debug builds may have different signing certificates between runs; if Android rejects an update, uninstalling the old app also clears its locally stored login settings. A future public release must use a privately held, stable release signing key.

The previous working implementation is preserved on the `backup/campus-autoconnect-v1` branch.

## Verification

The Android project and five scheduling-policy tests compile in GitHub Actions. The redesigned Rejoin UI, new background callback, and adaptive scheduler **still require real-device testing**. Do not confuse a green build with proof of overnight reliability.
