# Changelog

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
