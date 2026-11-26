import 'package:flutter/material.dart';

import '../../../../app/theme/app_theme.dart';
import '../../domain/entities/parking.dart';

/// Disponibles, ocupados y total (disponibles + ocupados), igual que en los paneles LED,
/// con una barra de ocupación.
class SpaceCountsView extends StatelessWidget {
  const SpaceCountsView({super.key, required this.counts, this.compact = false});

  final SpaceCounts counts;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            _Count(label: 'Disponibles', value: counts.available, color: AppColors.available, compact: compact),
            _Count(label: 'Ocupados', value: counts.occupied, color: AppColors.occupied, compact: compact),
            _Count(label: 'Total', value: counts.total, color: AppColors.primary, compact: compact),
          ],
        ),
        const SizedBox(height: 8),
        ClipRRect(
          borderRadius: BorderRadius.circular(999),
          child: LinearProgressIndicator(
            value: counts.total == 0 ? 0 : counts.occupiedRatio,
            minHeight: 6,
            color: AppColors.occupied,
            backgroundColor: AppColors.available.withValues(alpha: 0.35),
            semanticsLabel: 'Ocupación',
          ),
        ),
      ],
    );
  }
}

class _Count extends StatelessWidget {
  const _Count({required this.label, required this.value, required this.color, required this.compact});

  final String label;
  final int value;
  final Color color;
  final bool compact;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Expanded(
      child: Column(
        children: [
          Text(
            '$value',
            style: (compact ? theme.textTheme.titleLarge : theme.textTheme.headlineMedium)
                ?.copyWith(color: color, fontWeight: FontWeight.bold),
          ),
          Text(label, style: theme.textTheme.bodySmall),
        ],
      ),
    );
  }
}
