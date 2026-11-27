package com.winnersystems.smartparking.parking.application.service.charge;

import com.winnersystems.smartparking.parking.application.dto.command.SettleChargeCommand;
import com.winnersystems.smartparking.parking.application.dto.query.ChargeDto;
import com.winnersystems.smartparking.parking.application.port.input.charge.GetChargeUseCase;
import com.winnersystems.smartparking.parking.application.port.input.charge.SettleChargeUseCase;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Cobros que payment-service consulta y liquida (endpoints /internal/charges con token de servicio).
 *
 * parking-service es el dueño de la deuda: calcula cuánto se debe con la tarifa y lo ya pagado.
 * payment-service es el dueño del pago: avisa cuándo se pagó o se devolvió.
 */
@Slf4j
@Service
@Transactional
public class ChargeService implements GetChargeUseCase, SettleChargeUseCase {

   /** Estados de payment-service que llegan como aviso */
   private static final Set<String> SETTLEMENT_STATUSES = Set.of("COMPLETED", "PARTIALLY_REFUNDED", "REFUNDED");

   private final TransactionPersistencePort transactionPort;
   private final InfractionPersistencePort infractionPort;
   private final VehiclePersistencePort vehiclePort;
   private final ParkingPersistencePort parkingPort;
   private final RatePersistencePort ratePort;
   private final ChargeSettlementPersistencePort settlementPort;
   private final Clock clock;
   private final int exitWindowMinutes;

   public ChargeService(
         TransactionPersistencePort transactionPort,
         InfractionPersistencePort infractionPort,
         VehiclePersistencePort vehiclePort,
         ParkingPersistencePort parkingPort,
         RatePersistencePort ratePort,
         ChargeSettlementPersistencePort settlementPort,
         Clock clock,
         @Value("${parking.payment.exit-window-minutes:15}") int exitWindowMinutes) {
      this.transactionPort = transactionPort;
      this.infractionPort = infractionPort;
      this.vehiclePort = vehiclePort;
      this.parkingPort = parkingPort;
      this.ratePort = ratePort;
      this.settlementPort = settlementPort;
      this.clock = clock;
      this.exitWindowMinutes = exitWindowMinutes;
   }

   // ========================= CONSULTA =========================

   @Override
   @Transactional(readOnly = true)
   public ChargeDto getCharge(ChargeConcept concept, Long sourceId) {
      return concept == ChargeConcept.FINE ? fineCharge(sourceId) : stayCharge(sourceId);
   }

   private ChargeDto stayCharge(Long transactionId) {
      Transaction t = loadTransaction(transactionId);
      String plate = plateOf(t.getVehicleId());
      String parkingName = parkingNameOf(t.getParkingId());
      String description = "Estadía " + (plate != null ? plate : "#" + t.getId())
            + (parkingName != null ? " · " + parkingName : "");

      if (!t.isActive()) {
         String reason = t.isCancelled() ? "La estadía fue anulada" : "El vehículo ya salió";
         return new ChargeDto(ChargeConcept.PARKING_STAY, t.getId(), BigDecimal.ZERO, t.getCurrency(), description,
               plate, t.getParkingId(), parkingName, false, reason);
      }
      BigDecimal due = t.amountDueAt(LocalDateTime.now(clock), loadRate(t.getRateId()), exitWindowMinutes);
      if (due.signum() == 0) {
         String reason = t.getPaidAmount().signum() > 0
               ? "La estadía ya está pagada: el vehículo puede salir"
               : "Sin costo: el vehículo sale dentro del tiempo de gracia";
         return new ChargeDto(ChargeConcept.PARKING_STAY, t.getId(), BigDecimal.ZERO, t.getCurrency(), description,
               plate, t.getParkingId(), parkingName, false, reason);
      }
      return new ChargeDto(ChargeConcept.PARKING_STAY, t.getId(), due, t.getCurrency(), description, plate,
            t.getParkingId(), parkingName, true, null);
   }

   private String parkingNameOf(Long parkingId) {
      return parkingPort.findByIdIncludingDeleted(parkingId).map(p -> p.getName()).orElse(null);
   }

   private ChargeDto fineCharge(Long infractionId) {
      Infraction i = loadInfraction(infractionId);
      // La infracción guarda su placa; las antiguas sin copia la toman del vehículo
      String plate = i.getPlateNumber() != null ? i.getPlateNumber() : plateOf(i.getVehicleId());
      String description = "Multa " + i.getInfractionCode() + (plate != null ? " · " + plate : "");
      String parkingName = parkingNameOf(i.getParkingId());
      if (!i.isFinePayable()) {
         String reason = !i.hasFine() ? "La infracción no tiene multa por pagar"
               : i.isFinePaid() ? "La multa ya está pagada"
               : !i.isPending() ? "La infracción está cerrada" : "La multa no se puede pagar";
         return new ChargeDto(ChargeConcept.FINE, i.getId(), BigDecimal.ZERO, "PEN", description, plate,
               i.getParkingId(), parkingName, false, reason);
      }
      return new ChargeDto(ChargeConcept.FINE, i.getId(), i.getFineAmountDue(), "PEN", description, plate,
            i.getParkingId(), parkingName, true, null);
   }

   // ========================= LIQUIDACIÓN =========================

   @Override
   public void settleCharge(SettleChargeCommand c) {
      if (!SETTLEMENT_STATUSES.contains(c.status())) {
         throw new ParkingDomainException("Estado de pago no admitido: " + c.status());
      }
      // Antes de registrar nada: el origen debe existir
      Transaction transaction = c.concept() == ChargeConcept.PARKING_STAY ? loadTransaction(c.sourceId()) : null;
      Infraction infraction = c.concept() == ChargeConcept.FINE ? loadInfraction(c.sourceId()) : null;

      ChargeSettlement settlement = settlementPort.findByPaymentId(c.paymentId())
            .orElseGet(() -> ChargeSettlement.of(c.concept(), c.sourceId(), c.paymentId()));
      if (settlement.getConcept() != c.concept() || !settlement.getSourceId().equals(c.sourceId())) {
         throw new ResourceConflictException("El pago " + c.paymentId() + " ya está registrado para otro cobro");
      }
      if (!settlement.update(c.status(), c.amount(), c.refundedAmount(), c.paidAt())) {
         log.debug("Aviso repetido del pago {}: sin cambios", c.paymentId());
         return;
      }
      settlementPort.save(settlement);

      // Total cubierto por todos los pagos de este cobro (neto de devoluciones)
      List<ChargeSettlement> all = settlementPort.findBySource(c.concept(), c.sourceId());
      BigDecimal net = all.stream().map(ChargeSettlement::getNetAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
      ChargeSettlement last = all.stream()
            .filter(s -> s.getNetAmount().signum() > 0 && s.getPaidAt() != null)
            .max(Comparator.comparing(ChargeSettlement::getPaidAt))
            .orElse(null);
      LocalDateTime lastPaidAt = last == null ? null : last.getPaidAt();

      if (transaction != null) {
         transaction.applySettlement(net, lastPaidAt, loadRate(transaction.getRateId()));
         transactionPort.save(transaction);
         log.info("Estadía {}: pagado S/ {} ({}) por el pago {}", transaction.getId(), net,
               transaction.getPaymentStatus(), c.paymentId());
      } else {
         infraction.applyFineSettlement(net, lastPaidAt, last == null ? null : last.getPaymentId());
         infractionPort.save(infraction);
         log.info("Multa {}: pagado S/ {} (pagada={}) por el pago {}", infraction.getId(), net,
               infraction.isFinePaid(), c.paymentId());
      }
   }

   // ========================= AUXILIARES =========================

   private Transaction loadTransaction(Long id) {
      return transactionPort.findById(id).orElseThrow(() -> new ResourceNotFoundException("Estadía", id));
   }

   private Infraction loadInfraction(Long id) {
      return infractionPort.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Multa", id));
   }

   /** Incluye eliminadas: la estadía conserva su tarifa */
   private Rate loadRate(Long rateId) {
      return ratePort.findByIdIncludingDeleted(rateId)
            .orElseThrow(() -> new ResourceNotFoundException("Tarifa", rateId));
   }

   private String plateOf(Long vehicleId) {
      return vehicleId == null ? null : vehiclePort.findById(vehicleId).map(Vehicle::getLicensePlate).orElse(null);
   }
}
