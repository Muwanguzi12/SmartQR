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

    if (lower.startsWith('https://') || lower.startsWith('http://')) {
      final uri = Uri.tryParse(value);
      final host = uri?.host.toLowerCase() ?? '';
      if (host.contains('wa.me') || host.contains('whatsapp.com')) {
        return DestinationInfo(title: 'WhatsApp', subtitle: 'Open this WhatsApp link', kind: 'WhatsApp link', value: value, isWeb: true);
      }
      if (host.contains('youtube.com') || host == 'youtu.be') {
        return DestinationInfo(title: 'YouTube', subtitle: 'Open this video or channel', kind: 'Video link', value: value, isWeb: true);
      }
      if (host.contains('tiktok.com')) {
        return DestinationInfo(title: 'TikTok', subtitle: 'Open this TikTok link', kind: 'Social link', value: value, isWeb: true);
      }
      if (host.contains('instagram.com')) {
        return DestinationInfo(title: 'Instagram', subtitle: 'Open this Instagram link', kind: 'Social link', value: value, isWeb: true);
      }
      if (host.contains('google.com') || host.contains('goo.gl')) {
        return DestinationInfo(title: 'Google', subtitle: 'Open this Google link', kind: 'Web link', value: value, isWeb: true);
      }
      if (host.contains('chatgpt.com') || host.contains('openai.com')) {
        return DestinationInfo(title: 'ChatGPT', subtitle: 'Open this ChatGPT link', kind: 'AI link', value: value, isWeb: true);
      }
      return DestinationInfo(title: host.isEmpty ? 'Website' : host, subtitle: 'Review the website before opening', kind: 'Web link', value: value, isWeb: true);
    }

    if (lower.startsWith('tel:')) return DestinationInfo(title: 'Phone number', subtitle: value.substring(4), kind: 'Phone', value: value);
    if (lower.startsWith('mailto:')) return DestinationInfo(title: 'Email', subtitle: value.substring(7), kind: 'Email', value: value);
    if (lower.startsWith('wifi:')) return DestinationInfo(title: 'Wi-Fi network', subtitle: 'Wi-Fi connection details', kind: 'Wi-Fi', value: value);
    if (lower.startsWith('upi:') || lower.startsWith('pay:') || lower.contains('payment')) {
      return DestinationInfo(title: 'Payment information', subtitle: 'Check the payment details carefully', kind: 'Payment', value: value);
    }
    return DestinationInfo(title: 'Scanned content', subtitle: value, kind: 'Text / code', value: value);
  }
}
