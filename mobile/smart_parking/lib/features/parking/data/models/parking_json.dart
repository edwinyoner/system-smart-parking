import '../../domain/entities/parking.dart';

/// Convierte el JSON de parking-service (`/public/parkings/...`) en entidades.
/// Los campos que falten se toman como vacíos para no romper la app si el backend cambia.
abstract final class ParkingJson {
  static ParkingSummary summary(Map<String, dynamic> j) => ParkingSummary(
        id: _int(j['id']),
        name: j['name'] as String? ?? '',
        code: j['code'] as String? ?? '',
        address: j['address'] as String?,
        latitude: _doubleOrNull(j['latitude']),
        longitude: _doubleOrNull(j['longitude']),
        operational: j['operational'] as bool? ?? false,
        openNow: j['openNow'] as bool? ?? false,
        counts: _counts(j),
        currentShift: _map(j['currentShift'], shift),
        currentRate: _map(j['currentRate'], rate),
      );

  static ParkingDetail detail(Map<String, dynamic> j) => ParkingDetail(
        parking: summary(j['parking'] as Map<String, dynamic>),
        description: j['description'] as String?,
        zones: _list(j['zones'], zone),
        schedule: _list(j['schedule'], (s) => ScheduleItem(
              shift: shift(s['shift'] as Map<String, dynamic>),
              rate: rate(s['rate'] as Map<String, dynamic>),
            )),
        generatedAt: _date(j['generatedAt']),
      );

  static SpaceMap spaceMap(Map<String, dynamic> j) => SpaceMap(
        parkingId: _int(j['parkingId']),
        name: j['name'] as String? ?? '',
        operational: j['operational'] as bool? ?? false,
        openNow: j['openNow'] as bool? ?? false,
        zones: _list(j['zones'], (z) => ZoneSpaces(
              code: z['code'] as String? ?? '',
              name: z['name'] as String? ?? '',
              operational: z['operational'] as bool? ?? false,
              available: _int(z['available']),
              occupied: _int(z['occupied']),
              unavailable: _int(z['unavailable']),
              spaces: _list(z['spaces'], (c) => SpaceCell(
                    code: c['code'] as String? ?? '',
                    type: c['type'] as String?,
                    status: spaceStatus(c['status'] as String?),
                  )),
            )),
        generatedAt: _date(j['generatedAt']),
      );

  static ZoneAvailability zone(Map<String, dynamic> j) => ZoneAvailability(
        code: j['code'] as String? ?? '',
        name: j['name'] as String? ?? '',
        operational: j['operational'] as bool? ?? false,
        counts: _counts(j),
      );

  static ShiftInfo shift(Map<String, dynamic> j) => ShiftInfo(
        name: j['name'] as String? ?? '',
        startTime: j['startTime'] as String? ?? '',
        endTime: j['endTime'] as String? ?? '',
        crossesMidnight: j['crossesMidnight'] as bool? ?? false,
      );

  static RateInfo rate(Map<String, dynamic> j) => RateInfo(
        name: j['name'] as String? ?? '',
        amountPerHour: _doubleOrNull(j['amountPerHour']) ?? 0,
        currency: j['currency'] as String? ?? 'PEN',
        billingUnitMinutes: _int(j['billingUnitMinutes'], 60),
        graceMinutes: _int(j['graceMinutes']),
        toleranceMinutes: _int(j['toleranceMinutes']),
        minimumMinutes: _int(j['minimumMinutes']),
      );

  static SpaceStatus spaceStatus(String? value) => switch (value) {
        'AVAILABLE' => SpaceStatus.available,
        'OCCUPIED' => SpaceStatus.occupied,
        _ => SpaceStatus.unavailable,
      };

  /// Total = disponibles + ocupados (`usableSpaces`), igual que en los paneles.
  static SpaceCounts _counts(Map<String, dynamic> j) {
    final available = _int(j['availableSpaces']);
    final occupied = _int(j['occupiedSpaces']);
    return SpaceCounts(
      available: available,
      occupied: occupied,
      total: _int(j['usableSpaces'], available + occupied),
    );
  }

  static int _int(Object? v, [int fallback = 0]) => v is num ? v.toInt() : fallback;

  static double? _doubleOrNull(Object? v) => v is num ? v.toDouble() : null;

  static DateTime _date(Object? v) => (v is String ? DateTime.tryParse(v) : null) ?? DateTime.now();

  static T? _map<T>(Object? v, T Function(Map<String, dynamic>) f) =>
      v is Map<String, dynamic> ? f(v) : null;

  static List<T> _list<T>(Object? v, T Function(Map<String, dynamic>) f) =>
      v is List ? v.whereType<Map<String, dynamic>>().map(f).toList() : <T>[];
}
