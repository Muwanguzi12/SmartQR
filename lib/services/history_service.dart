import 'dart:convert';
import 'package:shared_preferences/shared_preferences.dart';
import '../models/scan_entry.dart';

class HistoryService {
  static const _key = 'smartqr_scan_history';

  Future<List<ScanEntry>> load() async {
    final prefs = await SharedPreferences.getInstance();
    final raw = prefs.getString(_key);
    if (raw == null) return [];
    try {
      final decoded = jsonDecode(raw) as List<dynamic>;
      return decoded
          .whereType<Map<String, dynamic>>()
          .map(ScanEntry.fromJson)
          .toList()
        ..sort((a, b) => b.scannedAt.compareTo(a.scannedAt));
    } catch (_) {
      return [];
    }
  }

  Future<void> add(ScanEntry entry) async {
    final entries = await load();
    entries.removeWhere((item) => item.value == entry.value);
    entries.insert(0, entry);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(
      _key,
      jsonEncode(entries.take(100).map((item) => item.toJson()).toList()),
    );
  }

  Future<void> clear() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.remove(_key);
  }
}
