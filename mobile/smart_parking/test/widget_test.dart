import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_parking/app/app.dart';
import 'package:smart_parking/core/errors/failures.dart';
import 'package:smart_parking/features/parking/presentation/providers/parking_providers.dart';

import 'features/parking/fakes.dart';

Widget appWith(FakeParkingRepository repo) => ProviderScope(
      overrides: [parkingRepositoryProvider.overrideWithValue(repo)],
      child: const App(),
    );

void main() {
  testWidgets('el inicio lista los parqueos con disponibles, ocupados y total', (tester) async {
    await tester.pumpWidget(appWith(FakeParkingRepository()));
    await tester.pumpAndSettle();

    expect(find.text('Estacionamientos'), findsOneWidget);
    expect(find.text('Parqueo San Martín'), findsOneWidget);
    expect(find.text('Parqueo Las Banderas'), findsOneWidget);
    expect(find.text('Disponibles'), findsNWidgets(2));
    expect(find.text('Lleno'), findsOneWidget);
    expect(find.text('S/ 2.50 por hora'), findsOneWidget);
  });

  testWidgets('filtro "con espacios disponibles" y búsqueda sin tildes', (tester) async {
    await tester.pumpWidget(appWith(FakeParkingRepository()));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Con espacios disponibles'));
    await tester.pumpAndSettle();
    expect(find.text('Parqueo Las Banderas'), findsNothing);

    await tester.tap(find.text('Con espacios disponibles'));
    await tester.enterText(find.byType(TextField), 'san martin');
    await tester.pumpAndSettle();
    expect(find.text('Parqueo San Martín'), findsOneWidget);
    expect(find.text('Parqueo Las Banderas'), findsNothing);
  });

  testWidgets('detalle y cuadrícula de espacios', (tester) async {
    await tester.pumpWidget(appWith(FakeParkingRepository()));
    await tester.pumpAndSettle();

    await tester.tap(find.text('Parqueo San Martín'));
    await tester.pumpAndSettle();
    expect(find.text('Zona A'), findsOneWidget);
    // El horario está al final: la lista solo construye lo visible
    await tester.scrollUntilVisible(find.text('Mañana · 06:00 - 11:59'), 200,
        scrollable: find.byType(Scrollable).first);
    expect(find.textContaining('Sin costo si sales antes de 5 min'), findsOneWidget);
    expect(find.text('Mañana · 06:00 - 11:59'), findsOneWidget);
    await tester.scrollUntilVisible(find.text('Ver espacios'), -200, scrollable: find.byType(Scrollable).first);

    await tester.tap(find.text('Ver espacios'));
    await tester.pumpAndSettle();
    expect(find.text('A-01'), findsOneWidget);
    expect(find.text('A-03'), findsOneWidget);
    expect(find.text('No disponible'), findsOneWidget); // leyenda
  });

  testWidgets('sin conexión muestra el mensaje y permite reintentar', (tester) async {
    final repo = FakeParkingRepository(failure: const NetworkFailure());
    await tester.pumpWidget(appWith(repo));
    await tester.pumpAndSettle();

    expect(find.text('Sin conexión o tiempo de espera agotado'), findsOneWidget);
    final before = repo.listCalls;
    await tester.tap(find.text('Reintentar'));
    await tester.pumpAndSettle();
    expect(repo.listCalls, greaterThan(before));
  });
}
