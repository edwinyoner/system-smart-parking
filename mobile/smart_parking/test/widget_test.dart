import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:smart_parking/app/app.dart';

void main() {
  testWidgets('La app arranca y muestra la pantalla de estacionamientos',
      (WidgetTester tester) async {
    // Monta la app dentro de ProviderScope, igual que en main.dart.
    await tester.pumpWidget(const ProviderScope(child: App()));

    // go_router resuelve la ruta inicial de forma asíncrona.
    await tester.pumpAndSettle();

    expect(find.text('Estacionamientos'), findsOneWidget);
  });
}