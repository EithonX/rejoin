# Campus AutoConnect Lab v0.1

A small, native Android read-only diagnostic prototype. It doesn't sign in to the university portal, collect passwords, or maintain sessions automatically.

It trusts only a manually approved SSID and BSSID, tests internet connectivity through that Wi-Fi connection and optionally schedules one check about every 30 minutes (subject to Android background limits).

## Build

GitHub Actions builds a debug-signed installable APK. Open Actions > Build Android APK; download the build artifact when the run succeeds. The app has not yet been tested on a physical device.

No third-party Android libraries or browser engines.
