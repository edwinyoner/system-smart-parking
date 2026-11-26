import '../entities/parking.dart';

/// Información pública de los parqueos. Lanza un `Failure` (core/errors) si falla.
abstract interface class ParkingRepository {
  /// Parqueos publicados, por nombre.
  Future<List<ParkingSummary>> getParkings();

  /// Detalle con zonas y horario.
  Future<ParkingDetail> getParking(int id);

  /// Espacios por zona.
  Future<SpaceMap> getSpaceMap(int id);
}
