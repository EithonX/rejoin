# Privacy

Rejoin is designed as a local utility rather than a hosted service.

**Stored on your phone:** Trusted SSID/BSSID pairs and optional display names; your saved login labels and credentials (encrypted with Android Keystore); your preferred router-to-login assignments; short connection-event summaries; and scheduling state. Nothing in the interface needs your geographical coordinates, although Android may require location permission to reveal Wi-Fi identity.

**Network traffic:** To validate internet access Rejoin makes minimal checks against public internet endpoints, currently Cloudflare's IP-only trace endpoint. Cloudflare can observe connection metadata such as your public IP and request times. When you expressly enable or manually invoke login, your saved credentials are sent to the CU FortiGate endpoint on the selected Wi-Fi. That currently observed CU endpoint uses HTTP without TLS; Rejoin cannot guarantee confidentiality in transit.

**No Rejoin cloud:** There are no analytics SDKs, advertising integrations, telemetry uploads or developer-run authentication servers.

**Deletion:** Remove individual accounts in Saved logins, remove router profiles in Trusted Wi-Fi, or clear all saved logins in Advanced settings. Clearing the app's data or uninstalling also removes local app data and Keystore records.

**Other services:** Opening the maintainer's GitHub profile launches a browser under its own privacy policy.

Updated for Rejoin 2.1 beta.
