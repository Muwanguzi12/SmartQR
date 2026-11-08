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

  static List<String> _unfoldContactLines(String payload) {
    final physical = payload.replaceAll('\r\n', '\n').replaceAll('\r', '\n').split('\n');
    final logical = <String>[];
    for (final line in physical) {
      if (logical.isNotEmpty && (line.startsWith(' ') || line.startsWith('\t'))) {
        logical[logical.length - 1] += line.substring(1);
      } else if (logical.isNotEmpty && logical.last.endsWith('=')) {
        logical[logical.length - 1] =
            logical.last.substring(0, logical.last.length - 1) + line;
      } else {
        logical.add(line);
      }
    }
    return logical;
  }

  static int _propertySeparator(String line) {
    var quoted = false;
    var escaped = false;
    for (var i = 0; i < line.length; i++) {
      final char = line[i];
      if (escaped) {
        escaped = false;
        continue;
      }
      if (char == r'\') {
        escaped = true;
        continue;
      }
      if (char == '"') quoted = !quoted;
      if (char == ':' && !quoted) return i;
    }
    return -1;
  }

  static String _unescapeContactValue(String value) {
    return value
        .replaceAll(RegExp(r'\\[nN]'), '\n')
        .replaceAll(r'\,', ',')
        .replaceAll(r'\;', ';')
        .replaceAll(r'\\', r'\')
        .trim();
  }

  static Map<String, String> _parseContact(String payload) {
    final fields = <String, List<String>>{};
    if (payload.trimLeft().toUpperCase().startsWith('BEGIN:VCARD')) {
      for (final line in _unfoldContactLines(payload)) {
        final separator = _propertySeparator(line);
        if (separator <= 0) continue;
        final property = line.substring(0, separator);
        final key = property.split(';').first.split('.').last.toUpperCase();
        if (key == 'BEGIN' || key == 'END' || key == 'VERSION') continue;
        var value = line.substring(separator + 1);
        final params = property.toUpperCase();
        if (params.contains('ENCODING=QUOTED-PRINTABLE')) {
          final bytes = <int>[];
          for (var i = 0; i < value.length; i++) {
            if (value[i] == '=' && i + 2 < value.length) {
              final byte = int.tryParse(value.substring(i + 1, i + 3), radix: 16);
              if (byte != null) {
                bytes.add(byte);
                i += 2;
                continue;
              }
            }
            bytes.addAll(latin1.encode(value[i]));
          }
          value = params.contains('CHARSET=UTF-8')
              ? utf8.decode(bytes, allowMalformed: true)
              : latin1.decode(bytes, allowInvalid: true);
        }
        value = _unescapeContactValue(value);
        if (value.isNotEmpty) fields.putIfAbsent(key, () => []).add(value);
      }
      final structured = (fields['N']?.first ?? '').split(';');
      final fallbackName = [
        if (structured.length > 1) structured[1],
        if (structured.isNotEmpty) structured[0],
        if (structured.length > 2) structured[2],
      ].where((part) => part.trim().isNotEmpty).join(' ');
      return {
        'name': fields['FN']?.first ?? fallbackName,
        'phone': fields['TEL']?.first ?? '',
        'email': fields['EMAIL']?.first ?? '',
        'company': (fields['ORG']?.first ?? '').replaceAll(';', ' '),
        'title': fields['TITLE']?.first ?? '',
      };
    }

    final body = payload.replaceFirst(RegExp(r'^MECARD:', caseSensitive: false), '');
    for (final part in body.split(';')) {
      final separator = part.indexOf(':');
      if (separator > 0) {
        final key = part.substring(0, separator).toUpperCase();
        fields.putIfAbsent(key, () => []).add(
          _unescapeContactValue(part.substring(separator + 1)),
        );
      }
    }
    return {
      'name': fields['N']?.first ?? '',
      'phone': fields['TEL']?.first ?? '',
      'email': fields['EMAIL']?.first ?? '',
      'company': '',
      'title': '',
    };
  }

  static Future<void> _contact(BuildContext context, String payload) async {
    final fields = _parseContact(payload);
    final hasContactData = fields.values.any((value) => value.trim().isNotEmpty);
    if (!hasContactData) {
      await Clipboard.setData(ClipboardData(text: payload));
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(content: Text('Contact details copied. This contact format could not be fully read.')),
        );
      }
      return;
    }
    try {
      await _native.invokeMethod('createContact', fields);
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
