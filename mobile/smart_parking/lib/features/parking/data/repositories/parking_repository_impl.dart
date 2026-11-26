import 'package:dio/dio.dart';

import '../../../../core/errors/failures.dart';
import '../../../../core/network/dio_error_mapper.dart';
import '../../domain/entities/parking.dart';
import '../../domain/repositories/parking_repository.dart';
import '../datasources/public_parking_api.dart';
import '../models/parking_json.dart';

/// Implementación con la API pública. Traduce los errores de Dio a [Failure].
class ParkingRepositoryImpl implements ParkingRepository {
  const ParkingRepositoryImpl(this._api);

  final PublicParkingApi _api;

  @override
  Future<List<ParkingSummary>> getParkings() =>
      _guard(() async => (await _api.getParkings()).map(ParkingJson.summary).toList());

  @override
  Future<ParkingDetail> getParking(int id) =>
      _guard(() async => ParkingJson.detail(await _api.getParking(id)));

  @override
  Future<SpaceMap> getSpaceMap(int id) =>
      _guard(() async => ParkingJson.spaceMap(await _api.getSpaceMap(id)));

  Future<T> _guard<T>(Future<T> Function() call) async {
    try {
      return await call();
    } on DioException catch (e) {
      throw mapDioException(e);
    } on Failure {
      rethrow;
    } catch (_) {
      // JSON inesperado u otro error: no se muestran detalles técnicos al conductor
      throw const UnexpectedFailure();
    }
  }
}
