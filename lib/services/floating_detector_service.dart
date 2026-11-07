import 'package:flutter/services.dart';

class FloatingDetectorService {
  static const _channel = MethodChannel('app.smartqr/overlay');

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
