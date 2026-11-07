class DestinationInfo {
  const DestinationInfo({
    required this.title,
    required this.subtitle,
    required this.kind,
    required this.value,
    this.isWeb = false,
  });

  final String title;
  final String subtitle;
  final String kind;
  final String value;
  final bool isWeb;
}

class DestinationService {
  static DestinationInfo classify(String raw) {
    final value = raw.trim();
    final lower = value.toLowerCase();

    if (lower.startsWith('wifi:')) {
      final fields = <String, String>{};
      for (final part in value.substring(5).split(';')) {
        final separator = part.indexOf(':');
        if (separator > 0) {
          fields[part.substring(0, separator).toUpperCase()] =
              part.substring(separator + 1);
        }
      }
      final ssid = fields['S'] ?? 'Unknown network';
      return DestinationInfo(
        title: ssid,
        subtitle: fields['H'] == 'true'
            ? 'Hidden Wi-Fi network'
            : 'Wi-Fi network · ${fields['T'] ?? 'secured'}',
        kind: 'Wi-Fi',
        value: value,
      );
    }

    if (lower.startsWith('tel:')) {
      return DestinationInfo(title: 'Phone number', subtitle: value.substring(4),
          kind: 'Phone', value: value);
    }
    if (lower.startsWith('mailto:')) {
      return DestinationInfo(title: 'Email', subtitle: value.substring(7),
          kind: 'Email', value: value);
    }
    if (lower.startsWith('smsto:') || lower.startsWith('sms:')) {
      return DestinationInfo(title: 'Text message', subtitle: value,
          kind: 'SMS', value: value);
    }
    if (lower.startsWith('geo:')) {
      return DestinationInfo(title: 'Map location', subtitle: value.substring(4),
          kind: 'Location', value: value);
    }
    if (lower.startsWith('begin:vcard') || lower.startsWith('mecard:')) {
      return DestinationInfo(title: 'Contact card', subtitle: 'Contact details are ready to review',
          kind: 'Contact', value: value);
    }
    if (lower.startsWith('http://') || lower.startsWith('https://')) {
      final uri = Uri.tryParse(value);
      final host = uri?.host.toLowerCase() ?? '';
      return DestinationInfo(
        title: host.isEmpty ? 'Website' : host,
        subtitle: 'Review the destination before opening',
        kind: 'Website',
        value: value,
        isWeb: true,
      );
    }
    if (lower.startsWith('www.')) {
      return DestinationInfo(title: 'Website', subtitle: 'Review the destination before opening',
          kind: 'Website', value: 'https://$value', isWeb: true);
    }
    return DestinationInfo(title: 'Scanned content', subtitle: 'Text detected',
        kind: 'Text', value: value);
  }
}
