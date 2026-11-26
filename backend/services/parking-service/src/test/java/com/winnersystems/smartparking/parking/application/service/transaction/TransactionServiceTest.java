package com.winnersystems.smartparking.parking.application.service.transaction;

import com.winnersystems.smartparking.parking.application.dto.command.CancelTransactionCommand;
import com.winnersystems.smartparking.parking.application.dto.command.RecordEntryCommand;
import com.winnersystems.smartparking.parking.application.dto.command.RecordExitCommand;
import com.winnersystems.smartparking.parking.application.dto.query.TransactionDetailDto;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.application.service.parkingshiftrate.ParkingScheduleResolver;
import com.winnersystems.smartparking.parking.domain.exception.*;
import com.winnersystems.smartparking.parking.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Random;

import static com.winnersystems.smartparking.parking.application.service.transaction.TransactionFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Entrada y salida manual, y cancelación, con los puertos simulados.
 */
class TransactionServiceTest {

   private TransactionPersistencePort transactions;
   private VehiclePersistencePort vehicles;
   private CustomerPersistencePort customers;
   private SpacePersistencePort spaces;
   private ParkingScheduleResolver schedule;
   private OperatorAssignmentPersistencePort assignments;
   private FakeUser user;
   private TransactionService service;

   private Parking parking;
   private Zone zone;
   private Space free1;
   private Space free2;
   private final Rate rate = rate(50L);
   private final Shift morning = shift(1L, "06:00", "11:59");

   @BeforeEach
   void setUp() {
      transactions = mock(TransactionPersistencePort.class);
      vehicles = mock(VehiclePersistencePort.class);
      customers = mock(CustomerPersistencePort.class);
      spaces = mock(SpacePersistencePort.class);
      schedule = mock(ParkingScheduleResolver.class);
      assignments = mock(OperatorAssignmentPersistencePort.class);
      ParkingPersistencePort parkings = mock(ParkingPersistencePort.class);
      ZonePersistencePort zones = mock(ZonePersistencePort.class);
      RatePersistencePort rates = mock(RatePersistencePort.class);
      ShiftPersistencePort shifts = mock(ShiftPersistencePort.class);
      user = new FakeUser();

      parking = parking(1L);
      zone = zone(10L, 1L);
      free1 = space(100L, 10L);
      free2 = space(101L, 10L);

      when(parkings.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(parking));
      when(zones.findByIdIncludingDeleted(10L)).thenReturn(Optional.of(zone));
      when(rates.findByIdIncludingDeleted(50L)).thenReturn(Optional.of(rate));
      when(shifts.findById(1L)).thenReturn(Optional.of(morning));
      when(schedule.resolve(eq(1L), any())).thenReturn(Optional.of(new ParkingScheduleResolver.Resolved(null, morning, rate)));
      when(spaces.findAvailableByZoneId(10L)).thenReturn(List.of(free1, free2));
      when(spaces.findByIdIncludingDeleted(100L)).thenReturn(Optional.of(free1));
      when(spaces.findByIdIncludingDeleted(101L)).thenReturn(Optional.of(free2));
      when(vehicles.findByPlateNumber(any())).thenReturn(Optional.empty());
      when(vehicles.save(any())).thenAnswer(inv -> {
         Vehicle v = inv.getArgument(0);
         if (v.getId() == null) v.setId(500L);
         return v;
      });
      when(vehicles.findById(500L)).thenAnswer(inv -> Optional.of(vehicle(500L, "ABC-123")));
      when(transactions.save(any())).thenAnswer(inv -> {
         Transaction t = inv.getArgument(0);
         if (t.getId() == null) t.setId(900L);
         return t;
      });
      // El operador está de turno en todo el parqueo
      when(assignments.findAll(1L, null, OPERATOR)).thenReturn(List.of(
            OperatorAssignment.create(OPERATOR, "Ana Ruiz", 1L, null, 1L, TODAY, null, TODAY, 9L)));

      OperatorDutyChecker duty = new OperatorDutyChecker(user, assignments, shifts);
      service = new TransactionService(transactions, vehicles, customers,
            mock(CustomerVehiclePersistencePort.class), parkings, spaces, zones, rates, schedule,
            mock(DocumentTypePersistencePort.class), user, duty, new Random(1), CLOCK, 15);
   }

   private RecordEntryCommand entry(String plate, Long spaceId) {
      return new RecordEntryCommand(plate, 1L, 10L, spaceId, null, null, null, null, null, null, null);
   }

   @Test
   @DisplayName("entrada solo con placa: normaliza la placa, toma un espacio libre al azar y no crea cliente")
   void manualEntryOnlyPlate() {
      TransactionDetailDto dto = service.recordEntry(entry("abc 123", null));

      assertThat(dto.vehicle().plateNumber()).isEqualTo("ABC-123");
      assertThat(dto.customer()).isNull();
      assertThat(dto.entryMethod()).isEqualTo(Transaction.METHOD_MANUAL);
      assertThat(dto.entryOperator().id()).isEqualTo(OPERATOR);
      assertThat(dto.space().id()).isIn(100L, 101L);
      Space taken = dto.space().id() == 100L ? free1 : free2;
      assertThat(taken.isOccupied()).isTrue();
      verifyNoInteractions(customers);
   }

   @Test
   @DisplayName("sin turno en ese parqueo no registra (403) y no guarda nada")
   void notOnDuty() {
      when(assignments.findAll(1L, null, OPERATOR)).thenReturn(List.of());
      assertThatThrownBy(() -> service.recordEntry(entry("ABC-123", null)))
            .isInstanceOf(OperatorNotOnDutyException.class);
      verify(transactions, never()).save(any());
      verify(spaces, never()).save(any());
   }

   @Test
   @DisplayName("con transactions.any-parking registra sin turno")
   void anyParking() {
      when(assignments.findAll(1L, null, OPERATOR)).thenReturn(List.of());
      user.authorities.add(OperatorDutyChecker.ANY_PARKING_PERMISSION);
      assertThat(service.recordEntry(entry("ABC-123", null)).id()).isEqualTo(900L);
   }

   @Test
   @DisplayName("zona llena, fuera de horario o vehículo dentro: 409")
   void conflicts() {
      when(spaces.findAvailableByZoneId(10L)).thenReturn(List.of());
      assertThatThrownBy(() -> service.recordEntry(entry("ABC-123", null)))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("llena");

      when(spaces.findAvailableByZoneId(10L)).thenReturn(List.of(free1));
      when(schedule.resolve(eq(1L), any())).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.recordEntry(entry("ABC-123", null)))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("no atiende");

      when(schedule.resolve(eq(1L), any())).thenReturn(Optional.of(new ParkingScheduleResolver.Resolved(null, morning, rate)));
      when(vehicles.findByPlateNumber("ABC-123")).thenReturn(Optional.of(vehicle(500L, "ABC-123")));
      when(transactions.findActiveByVehicleId(500L)).thenReturn(Optional.of(
            activeStay(1L, 500L, 1L, 10L, 100L, 50L, NOW.minusHours(1))));
      assertThatThrownBy(() -> service.recordEntry(entry("ABC-123", null)))
            .isInstanceOf(VehicleAlreadyInsideException.class);
   }

   @Test
   @DisplayName("parqueo que exige documento: sin documento no entra")
   void documentRequired() {
      parking.changeDriverDocumentPolicy(true);
      assertThatThrownBy(() -> service.recordEntry(entry("ABC-123", null)))
            .isInstanceOf(ParkingDomainException.class);
      verify(transactions, never()).save(any());
   }

   @Test
   @DisplayName("espacio indicado de otra zona: rechazado")
   void spaceFromOtherZone() {
      when(spaces.findById(300L)).thenReturn(Optional.of(space(300L, 99L)));
      assertThatThrownBy(() -> service.recordEntry(entry("ABC-123", 300L)))
            .isInstanceOf(ParkingDomainException.class).hasMessageContaining("no pertenece");
   }

   @Test
   @DisplayName("salida sin pagar: 409 y el espacio sigue ocupado")
   void exitRequiresPayment() {
      free1.occupy();
      Transaction stay = activeStay(1L, 500L, 1L, 10L, 100L, 50L, NOW.minusHours(2));
      when(transactions.findActiveByPlateNumber("ABC-123")).thenReturn(Optional.of(stay));

      assertThatThrownBy(() -> service.recordExit(new RecordExitCommand(null, "abc123", null, null, null)))
            .isInstanceOf(PaymentRequiredException.class);
      assertThat(stay.isActive()).isTrue();
      assertThat(free1.isOccupied()).isTrue();
   }

   @Test
   @DisplayName("salida dentro de la gracia: sale sin pagar y libera el espacio")
   void exitWithinGrace() {
      free1.occupy();
      Transaction stay = activeStay(1L, 500L, 1L, 10L, 100L, 50L, NOW.minusMinutes(3));
      when(transactions.findById(1L)).thenReturn(Optional.of(stay));

      TransactionDetailDto dto = service.recordExit(new RecordExitCommand(1L, null, null, null, null));
      assertThat(dto.status()).isEqualTo(Transaction.STATUS_COMPLETED);
      assertThat(dto.exitOperator().id()).isEqualTo(OPERATOR);
      assertThat(free1.isOccupied()).isFalse();
   }

   @Test
   @DisplayName("cancelar: exige motivo y libera el espacio")
   void cancelReleasesSpace() {
      free1.occupy();
      Transaction stay = activeStay(1L, 500L, 1L, 10L, 100L, 50L, NOW.minusMinutes(30));
      when(transactions.findById(1L)).thenReturn(Optional.of(stay));

      assertThatThrownBy(() -> service.cancelTransaction(1L, new CancelTransactionCommand(" ")))
            .isInstanceOf(IllegalArgumentException.class);
      service.cancelTransaction(1L, new CancelTransactionCommand("Placa equivocada"));
      assertThat(stay.getStatus()).isEqualTo(Transaction.STATUS_CANCELLED);
      assertThat(free1.isOccupied()).isFalse();
   }
}
