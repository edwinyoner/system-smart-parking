import 'package:flutter/material.dart';

/// Paleta del sistema (la misma del panel web Angular).
abstract final class AppColors {
  static const navy = Color(0xFF0B1F3A);
  static const primary = Color(0xFF1F3B5C);
  static const border = Color(0xFFA7B1BE);
  static const background = Color(0xFFE6E9EF);
  static const text = Color(0xFF2B2F36);

  // Estados de los espacios (mismos que el mapa de ocupación del panel web)
  static const available = Color(0xFF198754);
  static const occupied = Color(0xFFDC3545);
  static const unavailable = Color(0xFF6C757D);
  static const warning = Color(0xFFF57C00);
}

abstract final class AppTheme {
  static ThemeData get light {
    final scheme = ColorScheme.fromSeed(
      seedColor: AppColors.primary,
      primary: AppColors.primary,
      surface: Colors.white,
    );
    return ThemeData(
      useMaterial3: true,
      colorScheme: scheme,
      scaffoldBackgroundColor: AppColors.background,
      appBarTheme: const AppBarTheme(
        backgroundColor: AppColors.navy,
        foregroundColor: Colors.white,
        centerTitle: false,
      ),
      cardTheme: CardThemeData(
        color: Colors.white,
        elevation: 0,
        margin: EdgeInsets.zero,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
          side: const BorderSide(color: Color(0xFFD5DAE1)),
        ),
      ),
      navigationBarTheme: NavigationBarThemeData(
        indicatorColor: AppColors.primary.withValues(alpha: 0.15),
      ),
      textTheme: ThemeData.light().textTheme.apply(
            bodyColor: AppColors.text,
            displayColor: AppColors.text,
          ),
    );
  }
}
