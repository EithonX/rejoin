# How Rejoin works

Setup and build instructions are in the [README](../README.md).

## Wi-Fi identity

`WifiProbe.current()` selects an Android `Network` with the Wi-Fi transport, prioritizing `WifiInfo` from its `NetworkCapabilities`. SSID and BSSID must match an explicitly saved `TrustedNetworks.Profile`; cellular networks and unfamiliar access points are never authentication targets.

`TrustedNetworks` stores up to 32 entries. Profiles are identified by stable UUIDs and may have friendly display names. The old single-network preference is migrated when first read.

## Saved logins

`CredentialVault` uses Android Keystore AES-GCM to encrypt a local account collection. The legacy single-account ciphertext is migrated in place, using the existing Android Keystore alias. Each saved login has an internal UUID and a human-friendly label. The password is never returned to a UI text field after saving.

A router's `loginId` is optional. Without one, its login is the default saved account; if an explicitly selected login is unavailable, no account is submitted. Deleting a login returns linked routers to default **after confirmation** in the UI. Clearing every saved account resets assignments and disables automatic recovery.

## Detect, decide, recover

The main recovery path is:

1. Match the physical Wi-Fi connection to a saved profile.
2. Check public connectivity on that Wi-Fi using a small IP-only HTTP request. Use verified public HTTPS only if the first check is inconclusive.
3. If online, do nothing.
4. If the known CU captive portal is confirmed, check consent, available credentials and the per-router cooldown.
5. In automatic mode, wait a short random delay and recheck to avoid duplicate logins with other devices.
6. Use `FortinetLoginV2` to obtain a fresh portal form and submit the chosen saved credentials **once** to the known gateway.
7. Verify public connectivity before claiming success; ask Android to refresh its captive status where appropriate.

Uncertain connectivity never authorizes an unattended login. Only an explicit user action can request manual recovery outside the confirmed captive state.

## Background scheduling

`RecoveryScheduler` keeps a persistent periodic job (ID 417) and uses a one-shot job (ID 418) for early recovery. A passive Wi-Fi listener asks Android to wake Rejoin when it detects a captive portal. The early job checks for a saved network and login before scheduling and again before making a network request. It skips Wi-Fi that Android already validates.

Balanced requests a 30-minute fallback interval and Responsive requests 15 minutes. Android can defer these jobs, especially during Doze. Jobs use the Wi-Fi network Android assigns when available. `BackgroundHealth` shows the last OS-launched check and any background permission or scheduling issue.

The listener uses Android’s own portal detection. A FortiGate session can expire while Android still considers Wi-Fi online, so the periodic job does its own check. After a check, Rejoin renews the one-shot listener once Android stops reporting captive Wi-Fi.

## Security boundary

The tested adapter is restricted to the CU FortiGate gateway. This beta does **not** bypass captive authentication, skip TLS checks or attempt third-party sign-in. The campus login HTTP transport is still vulnerable to network interception; a general-purpose public release needs a verified secure portal and additional supported adapters.

Wi-Fi SSID/BSSID matching is not strong authentication against a malicious access point. Do not deploy this beta on networks you do not administer or have permission to use.

## Testing

`:app:testDebugUnitTest` tests recovery timing, exact router matching, default login selection and missing-account behavior. Manual on-device testing must additionally cover: migration after upgrade; Wi-Fi switches; app background and idle; reboot; wrong passwords; multiple accounts; deletion and reassignment; and campus gateway outages.

A clean build and passing unit tests are not evidence of overnight background reliability.
