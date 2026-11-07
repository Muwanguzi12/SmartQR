# SmartQR

SmartQR is a lightweight Android QR and barcode scanner built with Flutter. It is a standalone project and does not depend on Necxa.

## Features in this first implementation
- Scan QR codes and barcodes with the camera.
- Decode codes from gallery images and screenshots.
- Request Android screen-capture consent to scan the currently displayed screen.
- Preview decoded content before opening it.
- Recognize common destinations such as WhatsApp, Google, ChatGPT, TikTok, Instagram and YouTube.
- Hand web links to Android so verified App Links can open their installed app; otherwise Android can use a browser.
- Copy scan results.
- Keep the last 100 scans in local device storage.

## Privacy and safety
- Camera scanning starts only after the user opens the camera scanner.
- Gallery access is initiated by the user.
- Screen scanning requests Android's standard MediaProjection permission. SmartQR does not silently capture the screen.
- Web destinations are shown for review before the user chooses Open.
- Scan history is stored locally with SharedPreferences. No SmartQR backend is configured.

## Set up locally
1. Install the current stable Flutter SDK and Android Studio.
2. Clone this repository.
3. From the repository root, run `flutter create . --platforms=android` once to generate the platform wrapper files (including the Gradle wrapper) for your installed Flutter SDK. Keep the SmartQR Dart source and Android Kotlin implementation.
4. Run:

```bash
flutter pub get
flutter analyze
flutter test
flutter run
```

Build a release APK with:

```bash
flutter build apk --release
```

## Android screen scanning note
The Android implementation requests explicit MediaProjection consent and captures one frame into the app's cache directory for on-device barcode decoding. Validate this flow on the Android versions and device manufacturers you intend to support, especially Android 14 and newer, where foreground-service requirements for screen projection are stricter. Captured images are temporary and are not uploaded.

## Initial limitations
- The screen-capture bridge is Android-specific.
- A scanned Wi-Fi QR is displayed for review/copy; automatic joining is not implemented.
- Payment payloads are preview-only. SmartQR does not initiate payments.
- App opening relies on Android's installed-app link handling; a matching app is not guaranteed for every URL.
- The project has not yet been built or tested on a physical Android device.
