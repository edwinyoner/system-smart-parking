import 'package:flutter/material.dart';

import '../../../../core/errors/failures.dart';

/// Mensaje de error con botón de reintento.
class ErrorView extends StatelessWidget {
  const ErrorView({super.key, required this.error, required this.onRetry});

  final Object error;
  final VoidCallback onRetry;

  @override
  Widget build(BuildContext context) {
    final message = error is Failure ? (error as Failure).message : 'Ocurrió un error inesperado';
    final icon = error is NetworkFailure ? Icons.wifi_off : Icons.error_outline;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 48, color: Theme.of(context).colorScheme.outline),
            const SizedBox(height: 12),
            Text(message, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh), label: const Text('Reintentar')),
          ],
        ),
      ),
    );
  }
}

/// "Actualizado a las 10:32:15" (la app consulta cada 12 s).
class UpdatedAt extends StatelessWidget {
  const UpdatedAt({super.key, required this.time, this.refreshing = false});

  final DateTime time;
  final bool refreshing;

  @override
  Widget build(BuildContext context) {
    String two(int n) => n.toString().padLeft(2, '0');
    final text = 'Actualizado a las ${two(time.hour)}:${two(time.minute)}:${two(time.second)}';
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(refreshing ? Icons.sync : Icons.circle, size: 10, color: Colors.green),
        const SizedBox(width: 6),
        Text(text, style: Theme.of(context).textTheme.bodySmall),
      ],
    );
  }
}
