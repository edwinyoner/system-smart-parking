import '../../domain/entities/parking.dart';

/// Textos de la tarifa para el conductor.
extension RateText on RateInfo {
  /// "S/ 2.50 por hora" o "S/ 1.00 cada 30 min"
  String get priceLabel {
    final symbol = currency == 'PEN' ? 'S/' : currency;
    final price = '$symbol ${amountPerHour.toStringAsFixed(2)}';
    return billingUnitMinutes == 60 ? '$price por hora' : '$price cada $billingUnitMinutes min';
  }

  /// Reglas de cobro en frases cortas.
  List<String> get rules => [
        if (graceMinutes > 0) 'Sin costo si sales antes de $graceMinutes min',
        billingUnitMinutes == 60 ? 'Se cobra por hora o fracción' : 'Se cobra cada $billingUnitMinutes min o fracción',
        if (toleranceMinutes > 0) '$toleranceMinutes min de tolerancia por fracción',
        if (minimumMinutes > 0) 'Cobro mínimo: $minimumMinutes min',
      ];
}

extension ShiftText on ShiftInfo {
  /// "Mañana · 06:00 - 11:59"
  String get label => '$name · $startTime - $endTime${crossesMidnight ? ' (día siguiente)' : ''}';
}
