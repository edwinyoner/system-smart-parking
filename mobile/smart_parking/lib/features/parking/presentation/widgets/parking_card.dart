import 'package:flutter/material.dart';

import '../../domain/entities/parking.dart';
import 'parking_state_badge.dart';
import 'rate_text.dart';
import 'space_counts_view.dart';

/// Un parqueo en el listado: nombre, estado, disponibles, ocupados, total y tarifa vigente.
class ParkingCard extends StatelessWidget {
  const ParkingCard({super.key, required this.parking, required this.onTap});

  final ParkingSummary parking;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final receives = parking.state == ParkingState.open || parking.state == ParkingState.full;
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(parking.name, style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                        if (parking.address != null && parking.address!.isNotEmpty)
                          Padding(
                            padding: const EdgeInsets.only(top: 2),
                            child: Row(
                              children: [
                                const Icon(Icons.place_outlined, size: 14),
                                const SizedBox(width: 4),
                                Expanded(child: Text(parking.address!, style: theme.textTheme.bodySmall)),
                              ],
                            ),
                          ),
                      ],
                    ),
                  ),
                  const SizedBox(width: 8),
                  ParkingStateBadge(state: parking.state),
                ],
              ),
              const SizedBox(height: 16),
              SpaceCountsView(counts: parking.counts, compact: true),
              const SizedBox(height: 12),
              Row(
                children: [
                  Icon(Icons.payments_outlined, size: 16, color: theme.colorScheme.primary),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      receives && parking.currentRate != null
                          ? parking.currentRate!.priceLabel
                          : 'No atiende a esta hora',
                      style: theme.textTheme.bodyMedium,
                    ),
                  ),
                  const Icon(Icons.chevron_right),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}
