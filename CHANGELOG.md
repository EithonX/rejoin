# Changelog

## 2.4.0-beta4

- Illustrate connection states and animate setup between saved Wi-Fi, login and auto sign-in.
- Refine the launcher mark, typography and screen shapes.
- Build signed APKs and publish versioned releases from GitHub Actions.

## 2.4.0-beta3

- Rebuild the interface with Jetpack Compose Material 3 and add native screen tests.
- Move the app to `com.eithonx.rejoin` and replace the launcher icon.
- Add a manual signed APK workflow and Windows keystore instructions.

## 2.4.0-beta2

- Separate internet status from auto-reconnect readiness and show the relevant action on Home.
- Guide setup through Wi-Fi access, saving a login and turning on auto-reconnect.
- Open the campus sign-in page when a manual reconnect fails.
- Update the launcher icon, app palette and saved-network and login screens.
- Add a Gradle wrapper and documented local signing for release APKs.

## 2.4.0 beta — dark-first interaction redesign

- Replaced the old switch-style auto-recovery interaction with one dedicated screen and explicit Enable / Pause actions.
- Switched all activities and Android dialogs to a consistent dark palette.
- Added a native connection-state glyph and simplified Home to one primary decision.
- Added fast, resumable three-step onboarding; previously configured users skip it automatically.
- Reworked Settings navigation with vector back controls and consistently sized touch targets.
- Moved diagnostic network actions out of Settings into a separate troubleshooting screen.
- Made saved-login management one tappable row per account with contextual actions.
- Preserved the verified FortiGate login adapter, account vault and Android-managed recovery scheduler.


## 2.2.0 beta — background reliability and focused home

- Replaced a fragile chain of one-shot maintenance jobs with a persistent Android periodic job and a separate Wi-Fi-available check.
- Added last-background-run diagnostics and checks for background location, JobScheduler, and battery restrictions.
- Redesigned Home around a single connection state and context-sensitive action, with simpler navigation and less promotional content.
- Added a guided path to Android/Motorola settings when unattended recovery needs permission.
- Preserved the manually verified FortiGate login adapter, encrypted multi-account profiles, and conservative no-login-on-unknown-state behavior.


## 2.1.0 beta — profile management and onboarding

- Added multiple trusted Wi-Fi access points with exact SSID/BSSID matching and friendly labels.
- Added an encrypted multi-login vault; each Wi-Fi uses the default login unless another is explicitly assigned.
- Migrates existing single-account and single-router data from earlier betas.
- Added account editing, optional router assignments, saved-password confirmation and deletion flows.
- Redesigned home interactions to prioritize the user's actual connection state.
- Added the maintainer's GitHub identity, privacy/security documentation and policy tests.

## 2.0.0 beta — Rejoin

- New native visual identity and separate home, settings and activity experiences.
- Android-managed adaptive background verification and Wi-Fi availability events.
- Kept the manually validated FortiGate login adapter.

## 0.9.0 — campus authentication prototype

- Verified actual FortiGate login on a connected Android device.
- Added independent logout, login and DNS-free connectivity tests.

Versions remain beta until cross-device reliability and secure transport are independently validated.
