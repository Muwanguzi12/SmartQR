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
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Open Wi-Fi settings to choose this network.')),
        );
      }
    } on MissingPluginException {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Wi-Fi settings are unavailable on this device.')),
        );
      }
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
    final fields = <String, String>{};
    if (payload.toUpperCase().startsWith('BEGIN:VCARD')) {
      for (final line in payload.split(RegExp(r'\\r?\\n'))) {
        final separator = line.indexOf(':');
        if (separator < 0) continue;
        final key = line.substring(0, separator).split(';').first.toUpperCase();
        final value = line.substring(separator + 1).trim();
        if (value.isNotEmpty) fields.putIfAbsent(key, () => value);
      }
    } else {
      final body = payload.replaceFirst(RegExp(r'^MECARD:', caseSensitive: false), '');
      for (final part in body.split(';')) {
        final separator = part.indexOf(':');
        if (separator > 0) fields[part.substring(0, separator).toUpperCase()] = part.substring(separator + 1);
      }
    }
    try {
      await _native.invokeMethod('createContact', {
        'name': fields['FN'] ?? fields['N'] ?? fields['N'] ?? '',
        'phone': fields['TEL'] ?? '',
        'email': fields['EMAIL'] ?? '',
        'company': fields['ORG'] ?? '',
      });
    } on PlatformException {
      await Clipboard.setData(ClipboardData(text: payload));
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Contact details copied. Import them from your contacts app.')),
        );
      }
    } on MissingPluginException {
      await Clipboard.setData(ClipboardData(text: payload));
    }
  }
}
