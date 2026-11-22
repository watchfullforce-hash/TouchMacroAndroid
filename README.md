# Steam Card Farmer Android prototype

AMOLED black Android app shell, Kotlin + Jetpack Compose. Package: com.steamcardfarmer.mobile. Minimum Android 8.0.

## Build APK
Push to main or run the GitHub Actions workflow named **Build Steam Card Farmer APK**. Download the artifact **Steam-Card-Farmer-APK** from the completed run.

## Current limitations
This APK is a UI prototype. It does not yet authenticate or link Steam accounts, farm games, detect card drops, read inventory, sell cards, run background sessions, or support multiple accounts. Steam sign-in currently opens the Steam-hosted page only; secure OpenID callback validation is not implemented. Never enter Steam passwords, Steam Guard codes, or session cookies into this app.

The actual no-PC farming protocol still requires technical and security validation. Do not treat the prototype as a functioning farmer or expect card drops.
