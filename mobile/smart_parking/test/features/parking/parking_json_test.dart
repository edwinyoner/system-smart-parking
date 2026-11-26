import 'package:flutter_test/flutter_test.dart';
import 'package:smart_parking/features/parking/data/models/parking_json.dart';
import 'package:smart_parking/features/parking/domain/entities/parking.dart';
import 'package:smart_parking/features/parking/presentation/widgets/rate_text.dart';

import 'fakes.dart';

void main() {
  test('el total es disponibles + ocupados, como en los paneles', () {
    final p = ParkingJson.summary(sanMartinJson);
    expect(p.counts.available, 3);
    expect(p.counts.occupied, 1);
    expect(p.counts.total, 4);
    expect(p.state, ParkingState.open);
    expect(p.currentRate!.priceLabel, 'S/ 2.50 por hora');
  });

  test('estados del parqueo con la misma prioridad que iot-service', () {
    expect(ParkingJson.summary(banderasJson).state, ParkingState.full);
    expect(ParkingState.from(operational: true, openNow: false, available: 3), ParkingState.closed);
    expect(ParkingState.from(operational: false, openNow: false, available: 3), ParkingState.outOfService);
  });

  test('detalle con zonas y horario', () {
    final d = ParkingJson.detail(detailJson);
    expect(d.zones.single.counts.total, 4);
    expect(d.schedule.single.shift.label, 'Mañana · 06:00 - 11:59');
    expect(d.schedule.single.rate.rules, contains('Sin costo si sales antes de 5 min'));
  });

  test('mapa de espacios con los tres estados públicos', () {
    final m = ParkingJson.spaceMap(spacesJson);
    expect(m.zones.single.spaces.map((s) => s.status),
        [SpaceStatus.available, SpaceStatus.occupied, SpaceStatus.unavailable]);
    // Un estado desconocido nunca se muestra como disponible
    expect(ParkingJson.spaceStatus('MAINTENANCE'), SpaceStatus.unavailable);
  });

  test('campos ausentes no rompen la lectura', () {
    final p = ParkingJson.summary(const {'id': 9, 'name': 'Sin datos'});
    expect(p.counts.total, 0);
    expect(p.hasLocation, isFalse);
    expect(p.state, ParkingState.outOfService);
  });
}
