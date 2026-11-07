import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:url_launcher/url_launcher.dart';
import 'destination_service.dart';

class ActionRouter {
  static const _native = MethodChannel('app.smartqr/actions');

  static Future<void> execute(BuildContext context, DestinationInfo info) async {
    final value = info.value;
    Uri? uri;
    switch (info.kind) {
      case 'Website':
        uri = Uri.tryParse(value);
        break;
      case 'Phone':
        uri = Uri(scheme: 'tel', path: value.replaceFirst(RegExp(r'^tel:', caseSensitive: false), ''));
        break;
      case 'Email':
        uri = Uri.tryParse(value);
        break;
      case 'SMS':
        uri = Uri.tryParse(value.replaceFirst(RegExp(r'^smsto:', caseSensitive: false), 'sms:'));
        break;
      case 'Location':
        uri = Uri.tryParse(value);
        break;
      case 'Wi-Fi':
        await _wifi(context, value);
        return;
      case 'Contact':
        await _contact(context, value);
        return;
      default:
        await Clipboard.setData(ClipboardData(text: value));
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Content copied to clipboard')),
          );
        }
        return;
    }

    if (uri == null) return;
    try {
      final opened = await launchUrl(uri, mode: LaunchMode.externalApplication);
      if (!opened && context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('No compatible app could open this content.')),
        );
      }
    } catch (_) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Could not open this destination.')),
        );
      }
    }
  }

  static Future<void> _wifi(BuildContext context, String payload) async {
    final fields = <String, String>{};
    for (final part in payload.substring(5).split(';')) {
      final i = part.indexOf(':');
      if (i > 0) fields[part.substring(0, i).toUpperCase()] = part.substring(i + 1);
    }
    final ssid = fields['S'] ?? '';
    final password = fields['P'] ?? '';
    if (password.isNotEmpty) {
      await Clipboard.setData(ClipboardData(text: password));
    }
    try {
      await _native.invokeMethod('openWifiSettings');
    } on PlatformException {
      // Fall through to the platform settings URI below.
      await launchUrl(Uri.parse('android.settings.WIFI_SETTINGS'),
          mode: LaunchMode.externalApplication).catchError((_) => false);
    } on MissingPluginException {
      await launchUrl(Uri.parse('android.settings.WIFI_SETTINGS'),
          mode: LaunchMode.externalApplication).catchError((_) => false);
    }
    if (context.mounted) {
      showDialog<void>(context: context, builder: (ctx) => AlertDialog(
        title: Text(ssid.isEmpty ? 'Wi-Fi network' : 'Connect to $ssid'),
        content: Text(password.isEmpty
            ? 'Choose this network in Wi-Fi settings.'
            : 'The network password has been copied. Select this network in Wi-Fi settings and paste the password if Android asks for it.'),
        actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Done'))],
      ));
    }
  }

  static Future<void> _contact(BuildContext context, String payload) async {
    await Clipboard.setData(ClipboardData(text: payload));
    if (context.mounted) {
      showDialog<void>(context: context, builder: (ctx) => AlertDialog(
        title: const Text('Contact card detected'),
        content: const Text('The contact data has been copied. Review it before importing it into your contacts.'),
        actions: [TextButton(onPressed: () => Navigator.pop(ctx), child: const Text('Close'))],
      ));
    }
  }
}
