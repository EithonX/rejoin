# Privacy

Rejoin runs on your phone. It has no analytics, ads, or developer-operated account service.

## On your phone

Rejoin keeps the Wi-Fi names and access point identifiers you save, your login and network preferences, recent connection summaries, and the state it needs to schedule checks. Saved usernames and passwords are encrypted using Android Keystore. Android may require location permission to show the connected Wi-Fi's identity. Rejoin uses that identity to check whether the connection matches a saved network.

## Network requests

- **Campus sign-in.** If you opt in to HTTP sign-in, Rejoin sends your saved username and password to Chandigarh University’s FortiGate portal over the selected Wi-Fi during manual and automatic sign-in. The portal uses HTTP. Someone who can intercept the traffic can read the credentials as they are sent.
- **Connection checks.** Rejoin contacts Cloudflare to check internet access. Some captive portal checks contact Google connectivity endpoints. These requests carry standard network information such as your IP address and do not include saved credentials.
- **Updates.** Rejoin checks GitHub Releases at most once a day when opened, and when you request a check. The request does not contain your saved Wi-Fi details or login. An APK downloads only when you choose to install an update, and Rejoin verifies it before handing it to Android.

## Delete saved data

Use **Settings → Saved logins** or **Settings → Saved Wi-Fi** to remove individual entries. **Settings → Troubleshooting** can clear all saved logins. Clear Rejoin’s storage in Android settings or uninstall the app to remove its local data.

For privacy questions, contact [EithonX](https://github.com/EithonX).
