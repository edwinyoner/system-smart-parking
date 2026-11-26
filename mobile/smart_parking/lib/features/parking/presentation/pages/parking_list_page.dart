import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../../core/utils/text_normalizer.dart';
import '../../domain/entities/parking.dart';
import '../providers/parking_providers.dart';
import '../widgets/feedback_views.dart';
import '../widgets/parking_card.dart';
import '../widgets/parking_map_view.dart';

/// Inicio: parqueos de Huaraz en lista o en mapa, con su disponibilidad en vivo.
class ParkingListPage extends ConsumerStatefulWidget {
  const ParkingListPage({super.key});

  @override
  ConsumerState<ParkingListPage> createState() => _ParkingListPageState();
}

class _ParkingListPageState extends ConsumerState<ParkingListPage> {
  int _tab = 0; // 0 = lista, 1 = mapa
  String _query = '';
  bool _onlyAvailable = false;

  List<ParkingSummary> _filter(List<ParkingSummary> parkings) {
    final q = normalizeText(_query);
    return parkings.where((p) {
      if (_onlyAvailable && p.state != ParkingState.open) return false;
      if (q.isEmpty) return true;
      return normalizeText('${p.name} ${p.address ?? ''} ${p.code}').contains(q);
    }).toList();
  }

  void _open(ParkingSummary p) => context.push('/parkings/${p.id}');

  @override
  Widget build(BuildContext context) {
    final async = ref.watch(parkingsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('Estacionamientos'),
        actions: [
          IconButton(
            tooltip: 'Acerca de',
            icon: const Icon(Icons.info_outline),
            onPressed: () => _showAbout(context),
          ),
        ],
      ),
      body: async.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => ErrorView(error: e, onRetry: () => ref.invalidate(parkingsProvider)),
        data: (list) {
          final parkings = list.items;
          final filtered = _filter(parkings);
          return _tab == 0
              ? _ListTab(
                  parkings: filtered,
                  totalCount: parkings.length,
                  fetchedAt: list.fetchedAt,
                  query: _query,
                  onlyAvailable: _onlyAvailable,
                  refreshing: async.isRefreshing,
                  onQuery: (v) => setState(() => _query = v),
                  onOnlyAvailable: (v) => setState(() => _onlyAvailable = v),
                  onRefresh: () => ref.refresh(parkingsProvider.future),
                  onOpen: _open,
                )
              // El mapa se construye solo cuando se elige (no descarga mapas si no se usa)
              : ParkingMapView(parkings: filtered, onSelect: _open);
        },
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.list_alt), label: 'Lista'),
          NavigationDestination(icon: Icon(Icons.map_outlined), selectedIcon: Icon(Icons.map), label: 'Mapa'),
        ],
      ),
    );
  }

  void _showAbout(BuildContext context) {
    showAboutDialog(
      context: context,
      applicationName: 'Smart Parking Huaraz',
      applicationVersion: '1.0.0',
      children: const [
        Text(
          'Prototipo académico de tesis (UNASAM) desarrollado por Winner Systems. '
          'No es un servicio oficial de la Municipalidad Provincial de Huaraz.\n\n'
          'La disponibilidad se obtiene de cámaras y sensores y se actualiza cada pocos segundos. '
          'La app no pide datos personales ni crea cuentas.',
        ),
      ],
    );
  }
}

class _ListTab extends StatelessWidget {
  const _ListTab({
    required this.parkings,
    required this.totalCount,
    required this.fetchedAt,
    required this.query,
    required this.onlyAvailable,
    required this.refreshing,
    required this.onQuery,
    required this.onOnlyAvailable,
    required this.onRefresh,
    required this.onOpen,
  });

  final List<ParkingSummary> parkings;
  final int totalCount;
  final DateTime fetchedAt;
  final String query;
  final bool onlyAvailable;
  final bool refreshing;
  final ValueChanged<String> onQuery;
  final ValueChanged<bool> onOnlyAvailable;
  final Future<void> Function() onRefresh;
  final ValueChanged<ParkingSummary> onOpen;

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: onRefresh,
      child: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            decoration: const InputDecoration(
              hintText: 'Buscar por nombre o dirección',
              prefixIcon: Icon(Icons.search),
              filled: true,
              fillColor: Colors.white,
              border: OutlineInputBorder(),
              isDense: true,
            ),
            onChanged: onQuery,
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              FilterChip(
                label: const Text('Con espacios disponibles'),
                selected: onlyAvailable,
                onSelected: onOnlyAvailable,
              ),
              const Spacer(),
              UpdatedAt(time: fetchedAt, refreshing: refreshing),
            ],
          ),
          const SizedBox(height: 12),
          if (parkings.isEmpty)
            Padding(
              padding: const EdgeInsets.only(top: 48),
              child: Text(
                totalCount == 0 ? 'Todavía no hay parqueos publicados.' : 'Ningún parqueo coincide con la búsqueda.',
                textAlign: TextAlign.center,
              ),
            ),
          for (final p in parkings) ...[
            ParkingCard(parking: p, onTap: () => onOpen(p)),
            const SizedBox(height: 12),
          ],
        ],
      ),
    );
  }
}
