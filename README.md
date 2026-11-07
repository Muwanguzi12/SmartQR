# SmartQR

SmartQR is a lightweight Android QR and barcode scanner built with Flutter.

## Initial features
- Scan QR codes with the camera.
- Decode QR codes and barcodes from saved images and screenshots.
- Preview destinations before opening them.
- Copy decoded content.
- Open supported links with Android's installed apps when available, or use a browser.
- Keep a local scan history.
- Request Android screen-capture consent before attempting to scan the currently displayed screen.

## Safety and privacy
- Camera and screen capture are activated only after the user chooses the relevant action.
- Screen capture uses Android's MediaProjection consent prompt; SmartQR does not silently capture the screen.
- Unknown web destinations are previewed before opening.
- Scan history is stored on the device. No SmartQR backend is required.

## Development
This repository contains the Flutter application source. Install Flutter stable, then run:

```bash
flutter pub get
flutter run
```

For a release APK:

```bash
flutter build apk --release
```

## Android screen scanning
The Android implementation uses MediaProjection through a MethodChannel. Android displays its standard permission prompt. The user must approve each capture session. Screen capture is not available on iOS through this Android-specific implementation.

## Current status
The initial project foundation is being assembled. Validate dependency versions with your installed Flutter SDK before a production release.
