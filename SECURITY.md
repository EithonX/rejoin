# Security policy

## Supported portal

Rejoin authenticates a user's own authorized captive-portal session. The current adapter targets Chandigarh University’s FortiGate portal.

The current CU FortiGate endpoint uses HTTP. Rejoin encrypts saved credentials on the device. Anyone able to intercept the HTTP request can read the credentials during sign-in. Use the portal only with a Wi-Fi account you are authorized to use.

Trusted Wi-Fi entries use SSID and BSSID matching to reduce accidental submission. An access point's broadcast identifiers can be spoofed; they are not a cryptographic authentication mechanism.

## Protections

- Android Keystore AES-GCM local credential storage; no Android backup
- Explicit opt-in before automated password submission
- Per-router account selection and a conservative no-login-on-unknown-state rule
- Bounded sockets, single-flight recovery, retry cooldown and no unrestricted HTTP login redirects
- No passwords, login tokens or session cookies in event history

## Reporting

If you discover a vulnerability, contact [EithonX](https://github.com/EithonX) privately rather than posting sensitive details publicly. GitHub's private vulnerability reporting may also be used if the repository owner enables it. Do not submit real usernames, passwords, cookies, session tokens or unredacted device logs.

## Release candidates

Signed release candidates are available for testing. Their release notes disclose the campus portal’s use of HTTP. A production release requires a campus-approved TLS-protected login flow and validation across supported Android versions.
