import 'package:flutter/material.dart';
import 'screens/home_screen.dart';

void main() {
  WidgetsFlutterBinding.ensureInitialized();
  runApp(const SmartQrApp());
}

class SmartQrApp extends StatelessWidget {
  const SmartQrApp({super.key});

  @override
  Widget build(BuildContext context) {
    const seed = Color(0xFF6757E8);
    return MaterialApp(
      title: 'SmartQR',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(seedColor: seed, brightness: Brightness.light),
        scaffoldBackgroundColor: const Color(0xFFF7F7FB),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFFF7F7FB),
          foregroundColor: Color(0xFF171725),
          elevation: 0,
          centerTitle: false,
        ),
        cardTheme: CardThemeData(
          color: Colors.white,
          elevation: 0,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(22)),
        ),
      ),
      home: const HomeScreen(),
    );
  }
}
