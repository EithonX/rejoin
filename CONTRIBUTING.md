# Contributing

Thanks for improving Rejoin. Keep common actions easy to find and keep network checks bounded.

Before changing authentication, describe the actual portal behavior and write a reproducible test that does not use real credentials. Keep the verified campus adapter intact unless a documented incompatibility requires a change. New portal types belong in separate adapters.

Before submitting changes:

- Run `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- Confirm that trusted Wi-Fi matching and router-to-login selection fail closed.
- Test account migration, password retention, switching defaults, linked-account deletion and untrusted Wi-Fi.
- Keep passwords, cookies, session tokens and personal identifiers out of logs, issue reports and screenshots.
- Avoid unnecessary foreground services, tight polling and battery-optimization bypasses.

See [Security](SECURITY.md). Public contribution and licensing arrangements are not yet finalized.
