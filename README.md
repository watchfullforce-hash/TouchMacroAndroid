# Steam Card Farmer Android

Kotlin + Jetpack Compose Android app, AMOLED theme. Package: com.steamcardfarmer.mobile.

## Automatic updates
The app checks the latest GitHub Release on startup. When a newer APK is published, it offers a download and hands the signed APK to Android's package installer. Android requires the user to approve installation. All update APKs must use the same private signing key.

## One-time release signing setup
Add these repository Actions secrets:
- SIGNING_KEY_BASE64
- SIGNING_KEY_PASSWORD
- SIGNING_KEY_ALIAS
- SIGNING_KEY_STORE_PASSWORD

The APK build workflow always creates a debug artifact. When all signing secrets exist, it also builds a signed release APK and publishes a versioned GitHub Release for the in-app updater.

## Current scope
The update framework is being introduced. Steam account sign-in still opens Steam's official login page; verified OpenID linking, card farming, inventory and market functions are not yet implemented. Never enter Steam credentials into this app.
