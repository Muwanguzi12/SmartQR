import 'package:flutter/material.dart';
import 'package:image_picker/image_picker.dart';
import '../models/scan_entry.dart';
import '../services/barcode_service.dart';
import '../services/destination_service.dart';
import '../services/history_service.dart';
import '../services/screen_capture_service.dart';
import 'scanner_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _picker = ImagePicker();
  final _barcodeService = BarcodeService();
  final _history = HistoryService();
  final _screenCapture = ScreenCaptureService();
  List<ScanEntry> _entries = [];
  bool _busy = false;

  @override
  void initState() {
    super.initState();
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    final items = await _history.load();
    if (mounted) setState(() => _entries = items);
  }

  Future<void> _showResult(String value, {String type = 'QR / barcode'}) async {
    if (!mounted || value.trim().isEmpty) return;
    final clean = value.trim();
    await _history.add(ScanEntry(value: clean, type: type, scannedAt: DateTime.now()));
    await _loadHistory();
    if (!mounted) return;
    final info = DestinationService.classify(clean);
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (sheetContext) => _ResultSheet(info: info),
    );
  }

  Future<void> _scanGallery() async {
    final image = await _picker.pickImage(source: ImageSource.gallery, imageQuality: 100);
    if (image == null) return;
    await _decodeImage(image.path);
  }

  Future<void> _scanCurrentScreen() async {
    if (_busy) return;
    setState(() => _busy = true);
    try {
      final path = await _screenCapture.captureScreen();
      if (path != null) await _decodeImage(path);
    } catch (error) {
      if (mounted) _message(error.toString().replaceFirst('PlatformException(', ''));
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  Future<void> _decodeImage(String path) async {
    try {
      final codes = await _barcodeService.scanImage(path);
      final value = codes.map((code) => code.rawValue).whereType<String>().firstWhere(
            (item) => item.trim().isNotEmpty,
            orElse: () => '',
          );
      if (value.isEmpty) {
        if (mounted) _message('No QR code or barcode was found in that image.');
      } else {
        await _showResult(value);
      }
    } catch (error) {
      if (mounted) _message('Could not read this image: $error');
    }
  }

  void _message(String text) {
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(text)));
  }

  @override
  void dispose() {
    _barcodeService.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            Icon(Icons.qr_code_2_rounded, size: 30),
            SizedBox(width: 9),
            Text('SmartQR', style: TextStyle(fontWeight: FontWeight.w800)),
          ],
        ),
        actions: [
          IconButton(
            tooltip: 'Scan history',
            onPressed: () => _showHistory(),
            icon: const Icon(Icons.history_rounded),
          ),
        ],
      ),
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.fromLTRB(20, 12, 20, 28),
          children: [
            Container(
              padding: const EdgeInsets.all(22),
              decoration: BoxDecoration(
                gradient: const LinearGradient(
                  colors: [Color(0xFF6555E8), Color(0xFF8D68ED)],
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                ),
                borderRadius: BorderRadius.circular(28),
              ),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Text('Scan anything.', style: TextStyle(color: Colors.white, fontSize: 27, fontWeight: FontWeight.w800)),
                  const SizedBox(height: 6),
                  Text('QR codes on your screen, in photos, or in front of your camera.', style: TextStyle(color: Colors.white.withValues(alpha: .88), height: 1.4)),
                  const SizedBox(height: 22),
                  SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed: () => Navigator.of(context).push(MaterialPageRoute(
                        builder: (_) => ScannerScreen(onDetected: (value) => _showResult(value)),
                      )),
                      icon: const Icon(Icons.qr_code_scanner_rounded),
                      label: const Text('Open camera scanner'),
                      style: FilledButton.styleFrom(
                        backgroundColor: Colors.white,
                        foregroundColor: const Color(0xFF5142C9),
                        padding: const EdgeInsets.symmetric(vertical: 15),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            const SizedBox(height: 22),
            const Text('Scan from', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800)),
            const SizedBox(height: 12),
            Row(
              children: [
                Expanded(child: _ActionTile(icon: Icons.photo_library_outlined, title: 'Gallery', subtitle: 'Photos & screenshots', onTap: _scanGallery)),
                const SizedBox(width: 12),
                Expanded(child: _ActionTile(icon: Icons.phone_android_rounded, title: 'This screen', subtitle: 'With your permission', onTap: _busy ? null : _scanCurrentScreen)),
              ],
            ),
            if (_busy) const Padding(
              padding: EdgeInsets.only(top: 14),
              child: LinearProgressIndicator(),
            ),
            const SizedBox(height: 26),
            Row(
              children: [
                const Expanded(child: Text('Recent scans', style: TextStyle(fontSize: 18, fontWeight: FontWeight.w800))),
                TextButton(onPressed: _showHistory, child: const Text('See all')),
              ],
            ),
            if (_entries.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 22),
                child: Center(child: Text('Your recent scans will appear here.', style: TextStyle(color: Colors.black54))),
              )
            else
              ..._entries.take(4).map((entry) => _HistoryTile(entry: entry, onTap: () => _showResult(entry.value, type: entry.type))),
          ],
        ),
      ),
    );
  }

  void _showHistory() {
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder: (sheetContext) => SafeArea(
        child: SizedBox(
          height: MediaQuery.of(context).size.height * .72,
          child: Column(
            children: [
              Padding(
                padding: const EdgeInsets.all(18),
                child: Row(
                  children: [
                    const Expanded(child: Text('Scan history', style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold))),
                    TextButton(
                      onPressed: () async {
                        await _history.clear();
                        await _loadHistory();
                        if (sheetContext.mounted) Navigator.pop(sheetContext);
                      },
                      child: const Text('Clear'),
                    ),
                  ],
                ),
              ),
              Expanded(
                child: _entries.isEmpty
                    ? const Center(child: Text('No scans yet'))
                    : ListView.builder(
                        itemCount: _entries.length,
                        itemBuilder: (_, index) {
                          final entry = _entries[index];
                          return _HistoryTile(entry: entry, onTap: () {
                            Navigator.pop(sheetContext);
                            _showResult(entry.value, type: entry.type);
                          });
                        },
                      ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _ActionTile extends StatelessWidget {
  const _ActionTile({required this.icon, required this.title, required this.subtitle, required this.onTap});
  final IconData icon;
  final String title;
  final String subtitle;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) => InkWell(
        onTap: onTap,
        borderRadius: BorderRadius.circular(22),
        child: Ink(
          padding: const EdgeInsets.all(17),
          decoration: BoxDecoration(color: Colors.white, borderRadius: BorderRadius.circular(22)),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(icon, size: 27, color: Theme.of(context).colorScheme.primary),
            const SizedBox(height: 16),
            Text(title, style: const TextStyle(fontWeight: FontWeight.w800, fontSize: 16)),
            const SizedBox(height: 4),
            Text(subtitle, style: const TextStyle(color: Colors.black54, fontSize: 12)),
          ]),
        ),
      );
}

class _HistoryTile extends StatelessWidget {
  const _HistoryTile({required this.entry, required this.onTap});
  final ScanEntry entry;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) => ListTile(
        contentPadding: const EdgeInsets.symmetric(vertical: 3),
        leading: const CircleAvatar(backgroundColor: Color(0xFFECE9FF), child: Icon(Icons.qr_code_2_rounded, color: Color(0xFF6555E8))),
        title: Text(entry.value, maxLines: 1, overflow: TextOverflow.ellipsis),
        subtitle: Text(entry.type),
        onTap: onTap,
      );
}

class _ResultSheet extends StatelessWidget {
  const _ResultSheet({required this.info});
  final DestinationInfo info;

  Future<void> _copy(BuildContext context) async {
    await Clipboard.setData(ClipboardData(text: info.value));
    if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Copied to clipboard')));
  }

  Future<void> _open(BuildContext context) async {
    final uri = Uri.tryParse(info.value);
    if (uri == null) return;
    final canOpen = await canLaunchUrl(uri);
    if (!canOpen) {
      if (context.mounted) ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('No compatible app was found.')));
      return;
    }
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }

  @override
  Widget build(BuildContext context) {
    final canOpen = info.isWeb || info.kind == 'Phone' || info.kind == 'Email';
    return Container(
      padding: EdgeInsets.fromLTRB(22, 12, 22, MediaQuery.of(context).viewInsets.bottom + 24),
      decoration: const BoxDecoration(color: Colors.white, borderRadius: BorderRadius.vertical(top: Radius.circular(28))),
      child: SafeArea(
        child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
          Center(child: Container(width: 42, height: 4, decoration: BoxDecoration(color: Colors.black12, borderRadius: BorderRadius.circular(5)))),
          const SizedBox(height: 22),
          Text(info.kind.toUpperCase(), style: const TextStyle(color: Color(0xFF6555E8), fontWeight: FontWeight.w800, letterSpacing: 1)),
          const SizedBox(height: 8),
          Text(info.title, style: const TextStyle(fontSize: 23, fontWeight: FontWeight.w800)),
          const SizedBox(height: 8),
          Text(info.subtitle, style: const TextStyle(color: Colors.black54)),
          const SizedBox(height: 16),
          Container(
            width: double.infinity,
            padding: const EdgeInsets.all(14),
            decoration: BoxDecoration(color: const Color(0xFFF5F4FA), borderRadius: BorderRadius.circular(16)),
            child: SelectableText(info.value, style: const TextStyle(fontSize: 13, height: 1.4)),
          ),
          const SizedBox(height: 18),
          Row(children: [
            Expanded(child: OutlinedButton.icon(onPressed: () => _copy(context), icon: const Icon(Icons.copy_rounded), label: const Text('Copy'))),
            if (canOpen) ...[
              const SizedBox(width: 10),
              Expanded(child: FilledButton.icon(onPressed: () => _open(context), icon: const Icon(Icons.open_in_new_rounded), label: const Text('Open'))),
            ],
          ]),
          const SizedBox(height: 8),
          const Text('Check the destination before opening links or making payments.', style: TextStyle(fontSize: 11, color: Colors.black45)),
        ]),
      ),
    );
  }
}
