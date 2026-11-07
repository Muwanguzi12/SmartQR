import 'package:flutter/services.dart';

class ScreenCaptureService {
  static const _channel = MethodChannel('app.smartqr/screen_capture');

  /// Requests Android's system MediaProjection permission and returns a
  /// temporary PNG path when a frame was captured. The user must approve.
  Future<String?> captureScreen() async {
    try {
      return await _channel.invokeMethod<String>('captureScreen');
    } on PlatformException {
      rethrow;
    } on MissingPluginException {
      throw PlatformException(
        code: 'NOT_AVAILABLE',
        message: 'Screen scanning is available in the Android app only.',
      );
    }
  }
}
