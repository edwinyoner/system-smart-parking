import 'package:smart_parking/core/errors/failures.dart';
import 'package:smart_parking/features/parking/data/models/parking_json.dart';
import 'package:smart_parking/features/parking/domain/entities/parking.dart';
import 'package:smart_parking/features/parking/domain/repositories/parking_repository.dart';

/// Respuestas con la forma real de parking-service (/public/parkings/...).
const sanMartinJson = <String, dynamic>{
  'id': 1,
  'name': 'Parqueo San Martín',
  'code': 'PSM',
  'address': 'Jr. San Martín, Huaraz',
  'latitude': -9.527,
  'longitude': -77.528,
  'status': 'ACTIVE',
  'operational': true,
  'openNow': true,
  'totalSpaces': 5,
  'availableSpaces': 3,
  'occupiedSpaces': 1,
  'usableSpaces': 4,
  'occupancyPercentage': 20.0,
  'currentShift': {'name': 'Mañana', 'startTime': '06:00', 'endTime': '11:59', 'crossesMidnight': false},
  'currentRate': {
    'name': 'Tarifa Estándar',
    'amountPerHour': 2.5,
    'currency': 'PEN',
    'billingUnitMinutes': 60,
    'graceMinutes': 5,
    'toleranceMinutes': 5,
    'minimumMinutes': 0,
  },
};

const banderasJson = <String, dynamic>{
  'id': 2,
  'name': 'Parqueo Las Banderas',
  'code': 'PLB',
  'address': 'Av. Las Banderas',
  'latitude': -9.53,
  'longitude': -77.53,
  'status': 'ACTIVE',
  'operational': true,
  'openNow': true,
  'availableSpaces': 0,
  'occupiedSpaces': 3,
  'usableSpaces': 3,
  'currentShift': null,
  'currentRate': null,
};

const detailJson = <String, dynamic>{
  'parking': sanMartinJson,
  'description': 'Frente a la plaza',
  'zones': [
    {'id': 1, 'code': 'A', 'name': 'Zona A', 'operational': true, 'totalSpaces': 5, 'availableSpaces': 3, 'occupiedSpaces': 1, 'usableSpaces': 4},
  ],
  'schedule': [
    {'shift': sanMartinShift, 'rate': sanMartinRate},
  ],
  'generatedAt': '2026-11-26T10:00:00',
};

const sanMartinShift = {'name': 'Mañana', 'startTime': '06:00', 'endTime': '11:59', 'crossesMidnight': false};
const sanMartinRate = {
  'name': 'Tarifa Estándar',
  'amountPerHour': 2.5,
  'currency': 'PEN',
  'billingUnitMinutes': 60,
  'graceMinutes': 5,
  'toleranceMinutes': 5,
  'minimumMinutes': 0,
};

const spacesJson = <String, dynamic>{
  'parkingId': 1,
  'code': 'PSM',
  'name': 'Parqueo San Martín',
  'operational': true,
  'openNow': true,
  'totalSpaces': 3,
  'availableSpaces': 1,
  'zones': [
    {
      'code': 'A',
      'name': 'Zona A',
      'operational': true,
      'available': 1,
      'occupied': 1,
      'unavailable': 1,
      'spaces': [
        {'code': 'A-01', 'type': 'PERPENDICULAR', 'status': 'AVAILABLE'},
        {'code': 'A-02', 'type': 'PERPENDICULAR', 'status': 'OCCUPIED'},
        {'code': 'A-03', 'type': 'PERPENDICULAR', 'status': 'UNAVAILABLE'},
      ],
    },
  ],
  'generatedAt': '2026-11-26T10:00:00',
};

/// Repositorio en memoria (sin red). Con [failure] lanza ese error.
class FakeParkingRepository implements ParkingRepository {
  FakeParkingRepository({this.failure});

  final Failure? failure;
  int listCalls = 0;

  @override
  Future<List<ParkingSummary>> getParkings() async {
    listCalls++;
    if (failure != null) throw failure!;
    return [ParkingJson.summary(sanMartinJson), ParkingJson.summary(banderasJson)];
  }

  @override
  Future<ParkingDetail> getParking(int id) async {
    if (failure != null) throw failure!;
    return ParkingJson.detail(detailJson);
  }

  @override
  Future<SpaceMap> getSpaceMap(int id) async {
    if (failure != null) throw failure!;
    return ParkingJson.spaceMap(spacesJson);
  }
}
