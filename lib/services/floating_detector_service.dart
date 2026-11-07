import 'dart:async';
import 'package:flutter/services.dart';

class FloatingDetectorService {
  static const _channel = MethodChannel('app.smartqr/overlay');
  static Future<void> Function()? _onScan;

  static void listenForScan(Future<void> Function() callback) {
    _onScan = callback;
    _channel.setMethodCallHandler((call) async {
      if (call.method == 'scanCurrentScreen') {
        await _onScan?.call();
      }
    });
    unawaited(_channel.invokeMethod<bool>('consumePendingFloatingScan').then((pending) async {
      if (pending == true) await _onScan?.call();
    }).catchError((_) {}));
  }

  static void stopListening() {
    _onScan = null;
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
