# Security policy

## Supported portal

Rejoin authenticates a user's own authorized captive-portal session. The current adapter targets Chandigarh University’s FortiGate portal.

**Known high-risk dependency:** The currently observed CU FortiGate login endpoint is unencrypted HTTP. Passwords are encrypted at rest on the device but **not in transit** to that endpoint. Anyone able to intercept that traffic may potentially obtain credentials. Do not suggest or implement certificate-verification bypasses.

Trusted Wi-Fi entries use SSID and BSSID matching to reduce accidental submission. An access point's broadcast identifiers can be spoofed; they are not a cryptographic authentication mechanism.

## Protections

- Android Keystore AES-GCM local credential storage; no Android backup
- Explicit opt-in before automated password submission
- Per-router account selection and a conservative no-login-on-unknown-state rule
- Bounded sockets, single-flight recovery, retry cooldown and no unrestricted HTTP login redirects
- No passwords, login tokens or session cookies in event history

## Reporting

If you discover a vulnerability, contact [EithonX](https://github.com/EithonX) privately rather than posting sensitive details publicly. GitHub's private vulnerability reporting may also be used if the repository owner enables it. Do not submit real usernames, passwords, cookies, session tokens or unredacted device logs.

## Signed APK distribution

Before publishing a signed APK for public use, obtain a campus-approved TLS-protected login flow; review authentication and credential lifetimes; audit the source, exported components and dependency inventory; test on multiple Android versions; add stable release signing and a trusted update path; and review applicable institutional network policies.
