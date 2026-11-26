import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';
import 'package:url_launcher/url_launcher.dart';

import '../../domain/entities/parking.dart';
import 'parking_state_badge.dart';

/// Centro de Huaraz: vista inicial si ningún parqueo tiene ubicación.
const huarazCenter = LatLng(-9.5278, -77.5278);

/// Mapa de OpenStreetMap con un marcador por parqueo (color según su estado y número de disponibles).
class ParkingMapView extends StatelessWidget {
  const ParkingMapView({super.key, required this.parkings, required this.onSelect});

  final List<ParkingSummary> parkings;
  final ValueChanged<ParkingSummary> onSelect;

  @override
  Widget build(BuildContext context) {
    final located = parkings.where((p) => p.hasLocation).toList();
    final points = located.map((p) => LatLng(p.latitude!, p.longitude!)).toList();

    return FlutterMap(
      options: MapOptions(
        initialCenter: points.isEmpty ? huarazCenter : points.first,
        initialZoom: 15,
        initialCameraFit: points.length > 1
            ? CameraFit.coordinates(coordinates: points, padding: const EdgeInsets.all(64), maxZoom: 17)
            : null,
      ),
      children: [
        TileLayer(
          urlTemplate: 'https://tile.openstreetmap.org/{z}/{x}/{y}.png',
          // La política de OpenStreetMap exige identificar la app
          userAgentPackageName: 'com.winnersystems.smart_parking',
        ),
        MarkerLayer(
          markers: [
            for (final p in located)
              Marker(
                point: LatLng(p.latitude!, p.longitude!),
                width: 64,
                height: 64,
                child: _ParkingMarker(parking: p, onTap: () => onSelect(p)),
              ),
          ],
        ),
        RichAttributionWidget(
          attributions: [
            TextSourceAttribution(
              'Colaboradores de OpenStreetMap',
              onTap: () => launchUrl(Uri.parse('https://www.openstreetmap.org/copyright')),
            ),
          ],
        ),
      ],
    );
  }
}

class _ParkingMarker extends StatelessWidget {
  const _ParkingMarker({required this.parking, required this.onTap});

  final ParkingSummary parking;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final color = parking.state.color;
    final receives = parking.state == ParkingState.open || parking.state == ParkingState.full;
    return Semantics(
      button: true,
      label: '${parking.name}: ${parking.counts.available} disponibles',
      child: GestureDetector(
        onTap: onTap,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: color,
                borderRadius: BorderRadius.circular(8),
                boxShadow: const [BoxShadow(blurRadius: 4, color: Colors.black26)],
              ),
              child: Text(
                receives ? '${parking.counts.available}' : '-',
                style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16),
              ),
            ),
            Icon(Icons.location_on, color: color, size: 28),
          ],
        ),
      ),
    );
  }
}
