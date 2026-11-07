import 'package:google_mlkit_barcode_scanning/google_mlkit_barcode_scanning.dart';

class BarcodeService {
  final BarcodeScanner _scanner = BarcodeScanner(
    formats: const [BarcodeFormat.all],
  );

  Future<List<Barcode>> scanImage(String path) async {
    final image = InputImage.fromFilePath(path);
    return _scanner.processImage(image);
  }

  Future<void> dispose() => _scanner.close();
}
