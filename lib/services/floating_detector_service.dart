import 'dart:async';
import 'package:flutter/services.dart';

class FloatingDetectorService {
  static const _channel = MethodChannel('app.smartqr/overlay');
  static Future<void> Function(String path)? _onCapturedScreen;

  static void listenForScan(Future<void> Function(String path) callback) {
    _onCapturedScreen = callback;
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'screenCaptureReady') {
        final path = call.arguments as String?;
        if (path != null && path.isNotEmpty) {
          await _onCapturedScreen?.call(path);
        }
      }
    });

    // Covers the case where Android finished capturing before Flutter resumed.
    unawaited(_channel.invokeMethod<String>('consumePendingCapturedScreen').then((path) async {
      if (path != null && path.isNotEmpty) {
        await _onCapturedScreen?.call(path);
      }
    }).catchError((_) {}));
  }

  static void stopListening() {
    _onCapturedScreen = null;
    _channel.setMethodCallHandler(null);
  }

  static Future<bool> enable() async {
    try {
      final allowed = await _channel.invokeMethod<bool>('hasOverlayPermission') ?? false;
      if (!allowed) {
        await _channel.invokeMethod('requestOverlayPermission');
        return false;
      }
      await _channel.invokeMethod('startFloatingDetector');
      return true;
    } on PlatformException {
      return false;
    } on MissingPluginException {
      return false;
    }
  }

  static Future<void> disable() async {
    await _channel.invokeMethod('stopFloatingDetector');
  }
}
