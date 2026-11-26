import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../features/parking/presentation/pages/parking_detail_page.dart';
import '../../features/parking/presentation/pages/parking_list_page.dart';
import '../../features/parking/presentation/pages/parking_spaces_page.dart';

/// Todas las rutas son públicas: la versión 1 de la app no tiene login.
final appRouterProvider = Provider<GoRouter>((ref) {
  return GoRouter(
    initialLocation: '/',
    routes: [
      GoRoute(
        path: '/',
        builder: (context, state) => const ParkingListPage(),
        routes: [
          GoRoute(
            path: 'parkings/:id',
            builder: (context, state) => ParkingDetailPage(parkingId: _id(state)),
            routes: [
              GoRoute(
                path: 'spaces',
                builder: (context, state) => ParkingSpacesPage(parkingId: _id(state)),
              ),
            ],
          ),
        ],
      ),
    ],
  );
});

int _id(GoRouterState state) => int.tryParse(state.pathParameters['id'] ?? '') ?? 0;
