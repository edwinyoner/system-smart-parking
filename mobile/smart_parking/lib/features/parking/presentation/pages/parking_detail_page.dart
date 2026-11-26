import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../domain/entities/parking.dart';
import '../providers/parking_providers.dart';
import '../widgets/feedback_views.dart';
import '../widgets/parking_state_badge.dart';
import '../widgets/rate_text.dart';
import '../widgets/space_counts_view.dart';

/// Detalle de un parqueo: disponibilidad, zonas, horario, tarifa y cómo llegar.
class ParkingDetailPage extends ConsumerWidget {
  const ParkingDetailPage({super.key, required this.parkingId});

  final int parkingId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(parkingDetailProvider(parkingId));
    return Scaffold(
      appBar: AppBar(title: Text(async.value?.parking.name ?? 'Parqueo')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(parkingDetailProvider(parkingId))),
        data: (detail) => RefreshIndicator(
          onRefresh: () => ref.refresh(parkingDetailProvider(parkingId).future),
          child: _DetailBody(detail: detail, refreshing: async.isRefreshing),
        ),
      ),
    );
  }
}

class _DetailBody extends StatelessWidget {
  const _DetailBody({required this.detail, required this.refreshing});

  final ParkingDetail detail;
  final bool refreshing;

  @override
  Widget build(BuildContext context) {
    final p = detail.parking;
    final theme = Theme.of(context);
    final rate = p.currentRate ?? (detail.schedule.isNotEmpty ? detail.schedule.first.rate : null);

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Disponibilidad
        Card(
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    Expanded(child: Text('Ahora', style: theme.textTheme.titleMedium)),
                    ParkingStateBadge(state: p.state),
                  ],
                ),
                if (p.currentShift != null)
                  Text('Turno ${p.currentShift!.label}', style: theme.textTheme.bodySmall),
                const SizedBox(height: 16),
                SpaceCountsView(counts: p.counts),
                const SizedBox(height: 12),
                UpdatedAt(time: detail.generatedAt, refreshing: refreshing),
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        Row(
          children: [
            Expanded(
              child: FilledButton.icon(
                onPressed: () => context.push('/parkings/${p.id}/spaces'),
                icon: const Icon(Icons.grid_view),
                label: const Text('Ver espacios'),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: OutlinedButton.icon(
                onPressed: p.hasLocation ? () => _directions(context, p) : null,
                icon: const Icon(Icons.directions),
                label: const Text('Cómo llegar'),
              ),
            ),
          ],
        ),
        const SizedBox(height: 16),

        // Ubicación y descripción
        if ((p.address ?? '').isNotEmpty || (detail.description ?? '').isNotEmpty)
          _Section(
            title: 'Ubicación',
            icon: Icons.place_outlined,
            children: [
              if ((p.address ?? '').isNotEmpty) Text(p.address!),
              if ((detail.description ?? '').isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Text(detail.description!, style: theme.textTheme.bodySmall),
                ),
            ],
          ),

        // Zonas
        if (detail.zones.isNotEmpty)
          _Section(
            title: 'Zonas',
            icon: Icons.layers_outlined,
            children: [
              for (final z in detail.zones)
                ListTile(
                  contentPadding: EdgeInsets.zero,
                  dense: true,
                  title: Text(z.name),
                  subtitle: z.operational ? null : const Text('No disponible por ahora'),
                  trailing: Text(
                    '${z.counts.available} disponibles / ${z.counts.total}',
                    style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w600),
                  ),
                ),
            ],
          ),

        // Tarifa
        if (rate != null)
          _Section(
            title: 'Tarifa',
            icon: Icons.payments_outlined,
            children: [
              Text(rate.priceLabel, style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
              const SizedBox(height: 4),
              for (final r in rate.rules) Text('• $r'),
              const SizedBox(height: 8),
              Text(
                'Se paga antes de salir, con QR (Yape, Plin o banca móvil) o en efectivo.',
                style: theme.textTheme.bodySmall,
              ),
            ],
          ),

        // Horario
        _Section(
          title: 'Horario',
          icon: Icons.schedule,
          children: detail.schedule.isEmpty
              ? const [Text('Sin horario de atención publicado.')]
              : [
                  for (final s in detail.schedule)
                    ListTile(
                      contentPadding: EdgeInsets.zero,
                      dense: true,
                      title: Text(s.shift.label),
                      trailing: Text(s.rate.priceLabel),
                    ),
                ],
        ),
      ],
    );
  }

  /// Abre Google Maps (o el navegador) con la ruta hasta el parqueo.
  Future<void> _directions(BuildContext context, ParkingSummary p) async {
    final uri = Uri.parse('https://www.google.com/maps/dir/?api=1&destination=${p.latitude},${p.longitude}');
    final messenger = ScaffoldMessenger.of(context);
    if (!await launchUrl(uri, mode: LaunchMode.externalApplication)) {
      messenger.showSnackBar(const SnackBar(content: Text('No se pudo abrir el mapa')));
    }
  }
}

class _Section extends StatelessWidget {
  const _Section({required this.title, required this.icon, required this.children});

  final String title;
  final IconData icon;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.only(bottom: 12),
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                children: [
                  Icon(icon, size: 18, color: theme.colorScheme.primary),
                  const SizedBox(width: 8),
                  Text(title, style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold)),
                ],
              ),
              const SizedBox(height: 8),
              ...children,
            ],
          ),
        ),
      ),
    );
  }
}
