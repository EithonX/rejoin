# Build a signed Rejoin APK

Use JDK 17, Android SDK 35 and Build Tools 35.0.0. The Gradle wrapper pins the build version.

## Identify the signing key

For an update to an existing APK, use the same signing certificate and a higher `versionCode`. Compare the certificate SHA-256 fingerprints before replacing an installed copy.

```sh
apksigner verify --print-certs previous.apk
keytool -list -v -keystore /path/to/rejoin.jks -alias your-key-alias
```

For a first release without an existing signing identity, create a dedicated release key using Android Studio’s **Generate Signed Bundle / APK** flow. Keep an encrypted backup of the keystore and its passwords outside the repository. Uninstalling a differently signed build to install the release APK erases its saved Wi-Fi and logins.

## Build locally

Set the following environment variables in your shell or secret manager. Use an absolute path for the keystore.

| Variable | Value |
| --- | --- |
| `REJOIN_KEYSTORE_PATH` | Path to the release keystore |
| `REJOIN_KEYSTORE_PASSWORD` | Keystore password |
| `REJOIN_KEY_ALIAS` | Alias of the signing key |
| `REJOIN_KEY_PASSWORD` | Signing key password |

```sh
./gradlew --no-daemon :app:testDebugUnitTest :app:assembleRelease
apksigner verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk
```

With all four variables unset, Gradle creates an unsigned APK for build validation. With only some variables set, it reports the missing names. Run `apksigner` from your Android SDK’s `build-tools/35.0.0` directory if it is missing from your PATH.

## Test before publishing

- Upgrade an existing install and check that saved Wi-Fi, default logins and router-specific assignments survive.
- Test setup on a clean install, denied or approximate Location permission, manual reconnect and the fallback sign-in page on campus Wi-Fi.
- Turn on auto-reconnect and verify a real session expiry while the phone is idle. Check a second device or Android version if available.
- Inspect the signing fingerprint and verify that the APK installs as an update to the intended release channel.
