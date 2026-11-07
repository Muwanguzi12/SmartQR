import 'package:flutter_test/flutter_test.dart';
import 'package:smartqr/main.dart';

void main() {
  testWidgets('SmartQR home screen renders', (tester) async {
    await tester.pumpWidget(const SmartQrApp());
    expect(find.text('SmartQR'), findsOneWidget);
    expect(find.text('Open camera scanner'), findsOneWidget);
    expect(find.text('Scan from'), findsOneWidget);
  });
}
