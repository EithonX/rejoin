# Campus AutoConnect — 1.0 beta

Native Android app to monitor and restore the **user's own authenticated Chandigarh University network session** on one explicitly trusted Wi-Fi access point.

## Daily use

1. Select your own Wi-Fi router in the app (SSID and BSSID are stored locally).
2. Save your CU credentials using the on-device encrypted vault, after reviewing the campus HTTP warning.
3. Enable **Automatic recovery**. Keep one primary phone responsible for automatic recovery per shared router; other phones can use manual mode.
4. Use **Check now** for an independent IP-only internet check and **Repair connection** for a single user-triggered recovery attempt.

The dashboard shows connection status, a summary of the last check, the last successful recovery, and a short locally stored event history. Destructive logout testing, manual login, and the CU browser fallback live under **Advanced tools**.

## When checks happen

- **App in foreground:** Android Wi-Fi network callbacks indicate when network validation or captive-portal state changes. The app debounces events and only attempts repair on the trusted access point when automatic recovery is enabled.
- **App in background:** one persisted Android `JobScheduler` periodic task, minimum 15-minute interval with a 5-minute flex window. On supported Android devices, this task requires Wi-Fi.
- **When online:** do not log in again.
- **When portal is confirmed:** after a small randomized delay, re-check to avoid duplicate attempts, then submit the saved credentials once and independently verify public internet access.
- **When network is unknown/down:** do not send the password.
- **When login fails:** respect the existing 20-minute cooldown; do not hammer the campus gateway.

**Important:** Android can defer jobs during Doze or battery optimization. The app cannot promise instant recovery while the process is suspended. Background location permission may be required for trusted SSID/BSSID detection. The app never bypasses a university-configured hard session timeout.

## Security and privacy

- Android Keystore AES-GCM encryption is used for saved credentials. Backup is disabled.
- The current campus HTTP authentication endpoint does **not** encrypt credentials in transit. This is a risk even though credentials are encrypted on the phone. The app requires explicit consent. Do not distribute or reuse this debug build as a general public login client.
- The application makes no requests to analytics or app developer cloud services.
- Session tokens, cookies and passwords are never stored in logs or event history.
- Login is restricted to the known university gateway on the selected Wi-Fi access point.

## Building

A private GitHub Actions workflow compiles `app-debug.apk` via Gradle. Open Actions > Build Android APK > Artifacts and download `CampusAutoConnect-debug-apk`.

No Android Studio, Chromium or third-party UI libraries are required for end users. The application uses the Android framework UI and native network APIs.

## Status

The FortiGate login flow and manual sign-in were confirmed in earlier real-device tests; v1.0 beta's redesigned UI, event-driven foreground behavior and long-running background maintenance still require device testing.
