import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../app/theme/app_theme.dart';
import '../../domain/entities/parking.dart';
import '../providers/parking_providers.dart';
import '../widgets/feedback_views.dart';

/// Cuadrícula de espacios por zona: disponible, ocupado o no disponible (sin placas).
class ParkingSpacesPage extends ConsumerWidget {
  const ParkingSpacesPage({super.key, required this.parkingId});

  final int parkingId;

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final async = ref.watch(spaceMapProvider(parkingId));
    return Scaffold(
      appBar: AppBar(title: Text(async.value != null ? 'Espacios · ${async.value!.name}' : 'Espacios')),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(spaceMapProvider(parkingId))),
        data: (map) => RefreshIndicator(
          onRefresh: () => ref.refresh(spaceMapProvider(parkingId).future),
          child: ListView(
            padding: const EdgeInsets.all(16),
            children: [
              const _Legend(),
              const SizedBox(height: 8),
              if (!map.operational || !map.openNow)
                Card(
                  color: const Color(0xFFFFF3E0),
                  child: Padding(
                    padding: const EdgeInsets.all(12),
                    child: Text(
                      map.operational
                          ? 'El parqueo no atiende a esta hora: no recibe vehículos.'
                          : 'El parqueo está fuera de servicio por ahora.',
                    ),
                  ),
                ),
              for (final zone in map.zones) _ZoneGrid(zone: zone),
              if (map.zones.isEmpty) const Padding(padding: EdgeInsets.all(32), child: Text('Sin espacios publicados.')),
              UpdatedAt(time: map.generatedAt, refreshing: async.isRefreshing),
            ],
          ),
        ),
      ),
    );
  }
}

extension on SpaceStatus {
  Color get color => switch (this) {
        SpaceStatus.available => AppColors.available,
        SpaceStatus.occupied => AppColors.occupied,
        SpaceStatus.unavailable => AppColors.unavailable,
      };

  String get label => switch (this) {
        SpaceStatus.available => 'Disponible',
        SpaceStatus.occupied => 'Ocupado',
        SpaceStatus.unavailable => 'No disponible',
      };
}

class _Legend extends StatelessWidget {
  const _Legend();

  @override
  Widget build(BuildContext context) {
    return Wrap(
      spacing: 16,
      runSpacing: 4,
      children: [
        for (final s in SpaceStatus.values)
          Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Container(width: 14, height: 14, decoration: BoxDecoration(color: s.color, borderRadius: BorderRadius.circular(3))),
              const SizedBox(width: 6),
              Text(s.label, style: Theme.of(context).textTheme.bodySmall),
            ],
          ),
      ],
    );
  }
}

class _ZoneGrid extends StatelessWidget {
  const _ZoneGrid({required this.zone});

  final ZoneSpaces zone;

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
                  Expanded(child: Text(zone.name, style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.bold))),
                  Text('${zone.available} disponibles · ${zone.occupied} ocupados', style: theme.textTheme.bodySmall),
                ],
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [for (final cell in zone.spaces) _Cell(cell: cell)],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _Cell extends StatelessWidget {
  const _Cell({required this.cell});

  final SpaceCell cell;

  @override
  Widget build(BuildContext context) {
    return Semantics(
      label: 'Espacio ${cell.code}: ${cell.status.label}',
      child: Container(
        width: 64,
        height: 48,
        alignment: Alignment.center,
        decoration: BoxDecoration(
          color: cell.status.color.withValues(alpha: cell.status == SpaceStatus.unavailable ? 0.35 : 0.9),
          borderRadius: BorderRadius.circular(8),
        ),
        child: Text(
          cell.code,
          style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12),
        ),
      ),
    );
  }
}
