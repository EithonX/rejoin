# Sign and build Rejoin

Beta 3 introduces `com.eithonx.rejoin`. Android installs it alongside the old app, so Wi-Fi profiles and encrypted logins need to be set up again. Turn off auto sign-in in the old app if you keep both installed. Use the same release key for future updates to `com.eithonx.rejoin`.

## Create the key on Windows

Install a JDK and make sure `keytool` is available in PowerShell. Android Studio also includes one in `C:\Program Files\Android\Android Studio\jbr\bin`.

```powershell
$signingDir = Join-Path $env:USERPROFILE "RejoinSigning"
New-Item -ItemType Directory -Force $signingDir | Out-Null
$keystore = Join-Path $signingDir "rejoin-release.p12"
if (Test-Path $keystore) { throw "A Rejoin keystore already exists at $keystore" }

keytool -genkeypair -v -storetype PKCS12 -keystore "$keystore" -alias rejoin `
  -keyalg RSA -keysize 3072 -validity 10000
if ($LASTEXITCODE -ne 0) { throw "Key creation failed." }
keytool -list -v -keystore "$keystore" -alias rejoin
```

Enter the password when prompted. Keep a backup of the keystore and save the password in your password manager. PKCS#12 uses the same password for the keystore and key in this setup.

## Configure GitHub

Create an environment named `release` under **Settings → Environments**. Add these **environment secrets**.

| Name | Value |
| --- | --- |
| `REJOIN_KEYSTORE_BASE64` | Base64 of the keystore file |
| `REJOIN_KEYSTORE_PASSWORD` | The password chosen in `keytool` |
| `REJOIN_KEY_ALIAS` | `rejoin` |
| `REJOIN_KEY_PASSWORD` | The same PKCS#12 password |

With GitHub CLI authenticated, the following sends the file directly to the secret without printing it. You can set the other three secrets in GitHub’s environment settings.

```powershell
$encoded = [Convert]::ToBase64String([IO.File]::ReadAllBytes($keystore))
try {
    $encoded | gh secret set REJOIN_KEYSTORE_BASE64 --env release --repo EithonX/captive
    if ($LASTEXITCODE -ne 0) { throw "Couldn’t save the GitHub secret." }
} finally {
    Remove-Variable encoded
}
```

Base64 is reversible. Treat the encoded string like the keystore itself. Keep it out of commits, logs, and messages.

After the workflow is merged into the default branch, run **Actions → Build signed Rejoin APK → Run workflow**. Download **Rejoin-signed-apk** and compare the signer SHA-256 fingerprint from the workflow to `keytool -list -v`. The workflow builds an artifact for review. Publish it through GitHub Releases after testing.

## Build locally

With JDK 17 and Android SDK 35 installed, set the same password and alias variables plus `REJOIN_KEYSTORE_PATH` to the absolute path of the local key. Then run `.\gradlew.bat :app:assembleRelease`. The signed APK is at `app/build/outputs/apk/release/app-release.apk`. Verify it with `apksigner verify --print-certs` from Android SDK Build Tools 35.0.0.

Before publishing, test setup and manual sign-in on a clean install, automatic recovery after session expiry, and upgrade behavior on any earlier build using `com.eithonx.rejoin`. A GitHub debug APK uses a debug signing certificate, so use a release-signed build to test in-place release updates.
