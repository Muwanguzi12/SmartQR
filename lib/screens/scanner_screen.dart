import 'package:flutter/material.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

class ScannerScreen extends StatefulWidget {
  const ScannerScreen({super.key, required this.onDetected});
  final ValueChanged<String> onDetected;

  @override
  State<ScannerScreen> createState() => _ScannerScreenState();
}

class _ScannerScreenState extends State<ScannerScreen> {
  bool _handled = false;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Scan QR code')),
      body: Stack(
        fit: StackFit.expand,
        children: [
          MobileScanner(
            onDetect: (capture) {
              if (_handled) return;
              final value = capture.barcodes
                  .map((barcode) => barcode.rawValue)
                  .whereType<String>()
                  .firstWhere((item) => item.trim().isNotEmpty, orElse: () => '');
              if (value.isEmpty) return;
              _handled = true;
              Navigator.of(context).pop();
              widget.onDetected(value);
            },
          ),
          IgnorePointer(
            child: Center(
              child: Container(
                width: 260,
                height: 260,
                decoration: BoxDecoration(
                  border: Border.all(color: Colors.white, width: 3),
                  borderRadius: BorderRadius.circular(26),
                ),
              ),
            ),
          ),
          Positioned(
            bottom: 42,
            left: 24,
            right: 24,
            child: Text(
              'Position the QR code inside the frame',
              textAlign: TextAlign.center,
              style: TextStyle(color: Colors.white, fontWeight: FontWeight.w700, shadows: [Shadow(color: Colors.black.withValues(alpha: .7), blurRadius: 8)]),
            ),
          ),
        ],
      ),
    );
  }
}
