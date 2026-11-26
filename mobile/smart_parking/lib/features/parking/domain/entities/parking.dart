/// Entidades públicas de un parqueo (lo que el conductor puede ver sin cuenta).
/// No dependen de Flutter, Dio ni JSON: la capa data las construye.
library;

/// Estado del parqueo para el conductor (mismas reglas que los paneles LED).
enum ParkingState {
  /// Atiende y tiene espacios disponibles.
  open,

  /// Atiende, pero sin espacios disponibles.
  full,

  /// No atiende a esta hora (sin turno vigente).
  closed,

  /// En mantenimiento o fuera de servicio.
  outOfService;

  /// Misma prioridad que en iot-service: primero si opera, luego si atiende, luego si hay espacio.
  static ParkingState from({
    required bool operational,
    required bool openNow,
    required int available,
  }) {
    if (!operational) return ParkingState.outOfService;
    if (!openNow) return ParkingState.closed;
    if (available == 0) return ParkingState.full;
    return ParkingState.open;
  }
}

/// Turno vigente o del horario.
class ShiftInfo {
  const ShiftInfo({
    required this.name,
    required this.startTime,
    required this.endTime,
    required this.crossesMidnight,
  });

  final String name;
  final String startTime; // HH:mm
  final String endTime; // HH:mm
  final bool crossesMidnight;
}

/// Tarifa de un turno: cobro por hora o fracción.
class RateInfo {
  const RateInfo({
    required this.name,
    required this.amountPerHour,
    required this.currency,
    required this.billingUnitMinutes,
    required this.graceMinutes,
    required this.toleranceMinutes,
    required this.minimumMinutes,
  });

  final String name;
  final double amountPerHour;
  final String currency; // PEN
  final int billingUnitMinutes;
  final int graceMinutes;
  final int toleranceMinutes;
  final int minimumMinutes;
}

/// Cantidades que se muestran en la app y en los paneles.
/// [total] = disponibles + ocupados (sin mantenimiento ni fuera de servicio).
class SpaceCounts {
  const SpaceCounts({
    required this.available,
    required this.occupied,
    required this.total,
  });

  final int available;
  final int occupied;
  final int total;

  /// Porcentaje ocupado (0 a 1) para la barra.
  double get occupiedRatio => total == 0 ? 0 : occupied / total;
}

/// Un parqueo del listado y del mapa.
class ParkingSummary {
  const ParkingSummary({
    required this.id,
    required this.name,
    required this.code,
    required this.address,
    required this.latitude,
    required this.longitude,
    required this.operational,
    required this.openNow,
    required this.counts,
    required this.currentShift,
    required this.currentRate,
  });

  final int id;
  final String name;
  final String code;
  final String? address;
  final double? latitude;
  final double? longitude;
  final bool operational;
  final bool openNow;
  final SpaceCounts counts;
  final ShiftInfo? currentShift;
  final RateInfo? currentRate;

  bool get hasLocation => latitude != null && longitude != null;

  ParkingState get state => ParkingState.from(
        operational: operational,
        openNow: openNow,
        available: counts.available,
      );
}

/// Disponibilidad de una zona.
class ZoneAvailability {
  const ZoneAvailability({
    required this.code,
    required this.name,
    required this.operational,
    required this.counts,
  });

  final String code;
  final String name;
  final bool operational;
  final SpaceCounts counts;
}

/// Un turno del horario con su tarifa.
class ScheduleItem {
  const ScheduleItem({required this.shift, required this.rate});

  final ShiftInfo shift;
  final RateInfo rate;
}

/// Detalle de un parqueo.
class ParkingDetail {
  const ParkingDetail({
    required this.parking,
    required this.description,
    required this.zones,
    required this.schedule,
    required this.generatedAt,
  });

  final ParkingSummary parking;
  final String? description;
  final List<ZoneAvailability> zones;
  final List<ScheduleItem> schedule;
  final DateTime generatedAt;
}

/// Estado público de un espacio: no se dice por qué no está disponible ni quién lo ocupa.
enum SpaceStatus { available, occupied, unavailable }

/// Un espacio en el mapa (sin placa ni estadía).
class SpaceCell {
  const SpaceCell({required this.code, required this.type, required this.status});

  final String code;
  final String? type;
  final SpaceStatus status;
}

/// Espacios de una zona.
class ZoneSpaces {
  const ZoneSpaces({
    required this.code,
    required this.name,
    required this.operational,
    required this.available,
    required this.occupied,
    required this.unavailable,
    required this.spaces,
  });

  final String code;
  final String name;
  final bool operational;
  final int available;
  final int occupied;
  final int unavailable;
  final List<SpaceCell> spaces;
}

/// Mapa de espacios de un parqueo.
class SpaceMap {
  const SpaceMap({
    required this.parkingId,
    required this.name,
    required this.operational,
    required this.openNow,
    required this.zones,
    required this.generatedAt,
  });

  final int parkingId;
  final String name;
  final bool operational;
  final bool openNow;
  final List<ZoneSpaces> zones;
  final DateTime generatedAt;
}
