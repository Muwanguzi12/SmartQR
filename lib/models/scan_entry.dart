class ScanEntry {
  const ScanEntry({
    required this.value,
    required this.type,
    required this.scannedAt,
  });

  final String value;
  final String type;
  final DateTime scannedAt;

  Map<String, Object> toJson() => {
        'value': value,
        'type': type,
        'scannedAt': scannedAt.toIso8601String(),
      };

  factory ScanEntry.fromJson(Map<String, dynamic> json) => ScanEntry(
        value: json['value'] as String? ?? '',
        type: json['type'] as String? ?? 'Unknown',
        scannedAt: DateTime.tryParse(json['scannedAt'] as String? ?? '') ?? DateTime.now(),
      );
}
