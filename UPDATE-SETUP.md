# One-time automatic update setup

1. Open repository Settings -> Secrets and variables -> Actions.
2. Create the four repository secrets listed below from the private signing bundle supplied separately:
   - SIGNING_KEY_BASE64 (entire contents of SIGNING_KEY_BASE64.txt)
   - SIGNING_KEY_PASSWORD (from SECRETS.txt)
   - SIGNING_KEY_ALIAS (from SECRETS.txt)
   - SIGNING_KEY_STORE_PASSWORD (from SECRETS.txt)
3. Do not commit the signing key or secret values to the repository or share them publicly.
4. After the secrets are saved, every push to main builds a debug APK and a signed release APK, then publishes a GitHub Release.
5. The installed app checks the latest release on startup. When an update exists, tap Download Update and approve Android's installer prompt. On first use, Android may ask you to allow this app to install unknown apps.

Keep a secure backup of the signing keystore and passwords. If they are lost, future APKs cannot update the installed app; users would need to uninstall and reinstall.
