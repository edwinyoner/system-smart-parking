import 'package:flutter/material.dart';

import '../../../../app/theme/app_theme.dart';
import '../../domain/entities/parking.dart';

/// Texto y color de cada estado del parqueo.
extension ParkingStateView on ParkingState {
  String get label => switch (this) {
        ParkingState.open => 'Abierto',
        ParkingState.full => 'Lleno',
        ParkingState.closed => 'Cerrado',
        ParkingState.outOfService => 'Fuera de servicio',
      };

  Color get color => switch (this) {
        ParkingState.open => AppColors.available,
        ParkingState.full => AppColors.occupied,
        ParkingState.closed => AppColors.unavailable,
        ParkingState.outOfService => AppColors.warning,
      };

  IconData get icon => switch (this) {
        ParkingState.open => Icons.check_circle,
        ParkingState.full => Icons.block,
        ParkingState.closed => Icons.schedule,
        ParkingState.outOfService => Icons.build_circle,
      };
}

class ParkingStateBadge extends StatelessWidget {
  const ParkingStateBadge({super.key, required this.state});

  final ParkingState state;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: state.color.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(999),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(state.icon, size: 14, color: state.color),
          const SizedBox(width: 4),
          Text(
            state.label,
            style: TextStyle(color: state.color, fontWeight: FontWeight.w600, fontSize: 12),
          ),
        ],
      ),
    );
  }
}
