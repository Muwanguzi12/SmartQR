import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:image_picker/image_picker.dart';
import '../services/barcode_service.dart';
import '../services/action_router.dart';
import '../services/floating_detector_service.dart';
import '../services/destination_service.dart';
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
  final _screenCapture = ScreenCaptureService();
  bool _busy = false;

  @override
  void initState() {
    super.initState();
  }

  Future<void> _showResult(String value, {String type = 'QR / barcode'}) async {
    if (!mounted || value.trim().isEmpty) return;
    final info = DestinationService.classify(value.trim());
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

  Future<void> _enableFloatingDetector() async {
    final enabled = await FloatingDetectorService.enable();
    if (mounted) _message(enabled ? 'Floating SmartQR detector is ready.' : 'Allow SmartQR to display over other apps, then try again.');
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

