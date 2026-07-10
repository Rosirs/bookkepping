# Release Checklist

## Build artifacts

- Signed release APK: `app/build/outputs/apk/release/app-release.apk`
- Signed release AAB: `app/build/outputs/bundle/release/app-release.aab`

## Verification command

```bash
ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:bundleRelease
```

## Signature verification

```bash
/home/robin/Android/Sdk/build-tools/34.0.0/apksigner verify --verbose app/build/outputs/apk/release/app-release.apk
```

Expected output includes `Verifies`, `Verified using v2 scheme (APK Signature Scheme v2): true`, and `Number of signers: 1`. For this minSdk 26 app, v1/v3/v4 signatures are not required.

Verify AAB signature:

```bash
/home/robin/apps/android-studio/jbr/bin/jarsigner -verify -verbose -certs app/build/outputs/bundle/release/app-release.aab
```

Expected output includes `jar verified.`

## Keystore

Local upload keystore path on this machine: `/home/robin/.gradle/bookkeeping-upload.jks`

Signing properties path on this machine: `/home/robin/.gradle/bookkeeping-signing.properties`

You can override the signing properties file with:

```bash
BOOKKEEPING_SIGNING_PROPERTIES=/path/to/signing.properties ANDROID_HOME=/home/robin/Android/Sdk ./gradlew :app:bundleRelease
```

Treat both files as sensitive. They are intentionally stored outside the project directory so a future git repository does not accidentally include upload-key material.

## Store upload notes

The generated AAB/APK is signed and technically uploadable. Actual store submission still requires account-specific steps outside the repository, such as screenshots, developer contact, privacy-policy URL, review questionnaire, and store console access.
