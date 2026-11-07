# Thang Global Warehouse Manager — Android

Standalone Android app project stored separately from the Touch Macro app in this repository.

## Current build
- Black/red native home screen.
- Multiple saved account profiles.
- Credential storage via AndroidX EncryptedSharedPreferences (Android Keystore-backed).
- Embedded official Thang Global login/profile website.
- Best-effort auto-fill of the website login form for a selected saved profile.
- Attempts to open the Warehouse control on the profile page.
- Search field forwards a query to the website warehouse search input when detected.

## Not yet implemented / verified
- Reading all warehouse item names, icons and quantities into an app-side index.
- Searching multiple accounts simultaneously or sequentially and combining results.
- Reliable account switching/session reset.
These require inspecting the authenticated warehouse DOM/API and testing with a logged-in session. The current CHECK ALL ACCOUNTS button is deliberately a placeholder and must not be represented as a working multi-account scan.

## Build
GitHub Actions workflow builds a debug APK and uploads it as an artifact. Install only on your own device and test login/warehouse behaviour. The app has not been tested against the live authenticated website yet.
