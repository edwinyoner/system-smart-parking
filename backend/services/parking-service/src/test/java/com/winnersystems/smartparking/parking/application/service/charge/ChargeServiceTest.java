package com.winnersystems.smartparking.parking.application.service.charge;

import com.winnersystems.smartparking.parking.application.dto.command.SettleChargeCommand;
import com.winnersystems.smartparking.parking.application.dto.query.ChargeDto;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChargeServiceTest {

   private static final ZoneId LIMA = ZoneId.of("America/Lima");
   private static final LocalDateTime ENTRY = LocalDateTime.of(2026, 11, 25, 8, 0);
   private static final Clock CLOCK = Clock.fixed(ENTRY.plusMinutes(40).atZone(LIMA).toInstant(), LIMA);

   private TransactionPersistencePort transactions;
   private InfractionPersistencePort infractions;
   private ChargeService service;
   private Transaction stay;
   /** Liquidaciones guardadas, por ID de pago */
   private final Map<Long, ChargeSettlement> settlements = new LinkedHashMap<>();

   @BeforeEach
   void setUp() {
      transactions = mock(TransactionPersistencePort.class);
      infractions = mock(InfractionPersistencePort.class);
      VehiclePersistencePort vehicles = mock(VehiclePersistencePort.class);
      ParkingPersistencePort parkings = mock(ParkingPersistencePort.class);
      RatePersistencePort rates = mock(RatePersistencePort.class);
      ChargeSettlementPersistencePort settlementPort = mock(ChargeSettlementPersistencePort.class);

      stay = Transaction.registerEntry(new Transaction.EntryData(1L, 2L, 3L, 4L, 5L, 6L, 7L, "12345678",
            Transaction.METHOD_MANUAL, 9L, null, null, null), ENTRY);
      stay.setId(15L);
      stay.setEntryTime(ENTRY);
      when(transactions.findById(15L)).thenReturn(Optional.of(stay));
      when(transactions.findById(99L)).thenReturn(Optional.empty());

      Vehicle vehicle = new Vehicle("ABC-123");
      when(vehicles.findById(1L)).thenReturn(Optional.of(vehicle));
      Parking parking = new Parking();
      parking.setName("San Martín");
      when(parkings.findByIdIncludingDeleted(3L)).thenReturn(Optional.of(parking));
      when(rates.findByIdIncludingDeleted(6L)).thenReturn(Optional.of(
            Rate.create("Estándar", null, new BigDecimal("2.50"), new Rate.BillingRules(60, 0, 5, 5), true, 1L)));

      when(settlementPort.findByPaymentId(any())).thenAnswer(inv -> Optional.ofNullable(settlements.get(inv.<Long>getArgument(0))));
      when(settlementPort.save(any())).thenAnswer(inv -> {
         ChargeSettlement s = inv.getArgument(0);
         settlements.put(s.getPaymentId(), s);
         return s;
      });
      when(settlementPort.findBySource(any(), any())).thenAnswer(inv -> settlements.values().stream()
            .filter(s -> s.getConcept() == inv.getArgument(0) && s.getSourceId().equals(inv.getArgument(1))).toList());

      service = new ChargeService(transactions, infractions, vehicles, parkings, rates, settlementPort, CLOCK, 15);
   }

   private SettleChargeCommand settle(Long paymentId, String status, String amount, String refunded) {
      return new SettleChargeCommand(ChargeConcept.PARKING_STAY, 15L, paymentId, status,
            new BigDecimal(amount), new BigDecimal(refunded), ENTRY.plusMinutes(40));
   }

   @Test
   @DisplayName("estadía activa: cuánto se debe, con placa y parqueo en la descripción")
   void stayCharge() {
      ChargeDto c = service.getCharge(ChargeConcept.PARKING_STAY, 15L);
      assertThat(c.payable()).isTrue();
      assertThat(c.amountDue()).isEqualByComparingTo("2.50");
      assertThat(c.plateNumber()).isEqualTo("ABC-123");
      assertThat(c.description()).isEqualTo("Estadía ABC-123 · San Martín");
      assertThatThrownBy(() -> service.getCharge(ChargeConcept.PARKING_STAY, 99L))
            .isInstanceOf(ResourceNotFoundException.class);
   }

   @Test
   @DisplayName("pagada: ya no es cobrable y puede salir")
   void paidStay() {
      service.settleCharge(settle(42L, "COMPLETED", "2.50", "0"));
      assertThat(stay.getPaymentStatus()).isEqualTo(Transaction.PAYMENT_STATUS_PAID);
      ChargeDto c = service.getCharge(ChargeConcept.PARKING_STAY, 15L);
      assertThat(c.payable()).isFalse();
      assertThat(c.reason()).contains("puede salir");
   }

   @Test
   @DisplayName("el mismo aviso dos veces se registra una sola vez")
   void idempotent() {
      service.settleCharge(settle(42L, "COMPLETED", "2.50", "0"));
      service.settleCharge(settle(42L, "COMPLETED", "2.50", "0"));
      verify(transactions, times(1)).save(any());
      assertThat(settlements).hasSize(1);
   }

   @Test
   @DisplayName("dos pagos suman; una devolución resta y vuelve a deber")
   void sumAndRefund() {
      service.settleCharge(settle(41L, "COMPLETED", "1.00", "0"));
      assertThat(stay.getPaymentStatus()).isEqualTo(Transaction.PAYMENT_STATUS_PENDING);
      service.settleCharge(settle(42L, "COMPLETED", "1.50", "0"));
      assertThat(stay.getPaidAmount()).isEqualByComparingTo("2.50");
      assertThat(stay.getPaymentStatus()).isEqualTo(Transaction.PAYMENT_STATUS_PAID);

      service.settleCharge(settle(42L, "REFUNDED", "1.50", "1.50"));
      assertThat(stay.getPaidAmount()).isEqualByComparingTo("1.00");
      assertThat(stay.getPaymentStatus()).isEqualTo(Transaction.PAYMENT_STATUS_PENDING);
   }

   @Test
   @DisplayName("estado no admitido o pago de otro cobro: se rechaza")
   void invalid() {
      assertThatThrownBy(() -> service.settleCharge(settle(42L, "PENDING", "2.50", "0")))
            .isInstanceOf(ParkingDomainException.class);
      service.settleCharge(settle(42L, "COMPLETED", "2.50", "0"));

      Infraction fine = new Infraction();
      fine.setId(7L);
      fine.setInfractionCode("INF-7");
      fine.assignFine(new BigDecimal("50"));
      when(infractions.findById(7L)).thenReturn(Optional.of(fine));
      assertThatThrownBy(() -> service.settleCharge(new SettleChargeCommand(ChargeConcept.FINE, 7L, 42L, "COMPLETED",
            new BigDecimal("50"), BigDecimal.ZERO, ENTRY)))
            .isInstanceOf(ResourceConflictException.class);
   }

   @Test
   @DisplayName("multa: cobrable por lo que falta; pagada, se resuelve")
   void fine() {
      Infraction fine = new Infraction();
      fine.setId(7L);
      fine.setInfractionCode("INF-7");
      fine.setVehicleId(1L);
      fine.assignFine(new BigDecimal("50"));
      when(infractions.findById(7L)).thenReturn(Optional.of(fine));

      ChargeDto c = service.getCharge(ChargeConcept.FINE, 7L);
      assertThat(c.payable()).isTrue();
      assertThat(c.amountDue()).isEqualByComparingTo("50");
      assertThat(c.description()).isEqualTo("Multa INF-7 · ABC-123");

      service.settleCharge(new SettleChargeCommand(ChargeConcept.FINE, 7L, 50L, "COMPLETED",
            new BigDecimal("50"), BigDecimal.ZERO, ENTRY));
      assertThat(fine.isFinePaid()).isTrue();
      assertThat(service.getCharge(ChargeConcept.FINE, 7L).payable()).isFalse();
   }
}
