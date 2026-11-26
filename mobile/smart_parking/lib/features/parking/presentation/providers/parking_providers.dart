import 'dart:async';

import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../../core/network/dio_client.dart';
import '../../data/datasources/public_parking_api.dart';
import '../../data/repositories/parking_repository_impl.dart';
import '../../domain/entities/parking.dart';
import '../../domain/repositories/parking_repository.dart';

/// Cada cuánto se vuelve a consultar la disponibilidad (los paneles usan 10 s).
const refreshInterval = Duration(seconds: 12);

final parkingRepositoryProvider = Provider<ParkingRepository>(
  (ref) => ParkingRepositoryImpl(PublicParkingApi(ref.watch(dioProvider))),
);

/// Programa la siguiente consulta. Mientras se actualiza, la pantalla sigue mostrando
/// los datos anteriores (no parpadea). Si nadie escucha el provider, se descarta y el
/// temporizador se cancela: no se consulta en segundo plano.
void _refreshPeriodically(Ref ref) {
  final timer = Timer(refreshInterval, ref.invalidateSelf);
  ref.onDispose(timer.cancel);
}

/// Listado con la hora en que se consultó (para "Actualizado a las ...").
class ParkingList {
  const ParkingList(this.items, this.fetchedAt);

  final List<ParkingSummary> items;
  final DateTime fetchedAt;
}

final parkingsProvider = FutureProvider.autoDispose<ParkingList>((ref) async {
  _refreshPeriodically(ref);
  final items = await ref.watch(parkingRepositoryProvider).getParkings();
  return ParkingList(items, DateTime.now());
});

final parkingDetailProvider = FutureProvider.autoDispose.family<ParkingDetail, int>((ref, id) {
  _refreshPeriodically(ref);
  return ref.watch(parkingRepositoryProvider).getParking(id);
});

final spaceMapProvider = FutureProvider.autoDispose.family<SpaceMap, int>((ref, id) {
  _refreshPeriodically(ref);
  return ref.watch(parkingRepositoryProvider).getSpaceMap(id);
});
