# Backup setup

Drive backup needs one OAuth client in Google Cloud. Do these steps once. The app code needs no key, client id or secret, and none belongs in the repository.

The sign-in flow is `AuthorizationClient` from `play-services-auth` with the scope `https://www.googleapis.com/auth/drive.file`. Google identifies the app by its package name and signing-key SHA-1, so the OAuth client is of type Android. This flow needs no Web client and no client secret.

## Steps

1. Open the Google Cloud console and create a project, for example "Atelier".
2. Open APIs and Services, then Library. Search for "Google Drive API" and press Enable.
3. Open APIs and Services, then OAuth consent screen. Choose the user type External.
4. Fill in the app name, a support email, a developer contact email, a home page link and a privacy policy link. Save. Publish app in step 6 stays disabled until all five are set.
5. On the Scopes step, add only `https://www.googleapis.com/auth/drive.file`. Google rates it non-sensitive, so no verification is needed.
6. Under Publishing status, press Publish app so the status is "In production". A project left in "Testing" drops the grant after 7 days, and backup stops until the user signs in again.
7. Open APIs and Services, then Credentials. Press Create credentials, then OAuth client ID, and choose the application type Android.
8. Enter the package name `com.atelier.app`.
9. Enter the SHA-1 fingerprint of the key that signs the build. For a debug build, read it from the debug keystore:

   ```
   keytool -list -v -keystore <debug keystore> -alias androiddebugkey -storepass android
   ```

   The debug keystore is `debug.keystore` in `~/.android/` or in `~/.config/.android/`, depending on the Android tooling. Copy the line that starts with `SHA1:`.
10. Press Create. The client can take a few minutes to take effect.
11. Install the app and open Settings, then Backup, then "Войти через Google". Pick the Google account and grant access to Drive.

## Signing key

The app is installed as the debug build, signed with the debug keystore. Keep a copy of that keystore outside the machine. A build signed with any other key cannot update the installed app without an uninstall, and its SHA-1 needs its own Android OAuth client in the same project.

## Check

- After sign-in, Settings shows the account email, and "Сделать копию сейчас" creates the folder "Atelier" in My Drive with `orders.db` and a `photos` folder.
- Without a matching client, sign-in shows "Вход через Google для этой версии приложения ещё не настроен". The app keeps working without backup.
