package com.winnersystems.smartparking.parking.application.service.infraction;

import com.winnersystems.smartparking.parking.application.dto.command.ChangeInfractionFineCommand;
import com.winnersystems.smartparking.parking.application.dto.command.RegisterInfractionCommand;
import com.winnersystems.smartparking.parking.application.dto.query.InfractionDto;
import com.winnersystems.smartparking.parking.application.dto.query.InfractionFilter;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.application.service.transaction.OperatorDutyChecker;
import com.winnersystems.smartparking.parking.domain.exception.OperatorNotOnDutyException;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InfractionServiceTest {

   static final ZoneId PERU = ZoneId.of("America/Lima");
   static final Clock CLOCK = Clock.fixed(LocalDateTime.of(2026, 11, 27, 10, 0).atZone(PERU).toInstant(), PERU);

   private final Map<Long, Infraction> stored = new HashMap<>();
   private final Map<String, Vehicle> vehicles = new HashMap<>();
   private OperatorDutyChecker dutyChecker;
   private UserDirectoryPort users;
   private InfractionService service;

   @BeforeEach
   void setUp() {
      InfractionPersistencePort infractionPort = mock(InfractionPersistencePort.class);
      AtomicLong ids = new AtomicLong(0);
      when(infractionPort.save(any())).thenAnswer(i -> {
         Infraction inf = i.getArgument(0);
         if (inf.getId() == null) {
            inf.setId(ids.incrementAndGet());
         }
         stored.put(inf.getId(), inf);
         return inf;
      });
      when(infractionPort.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(stored.get((Long) i.getArgument(0))));

      ParkingPersistencePort parkingPort = mock(ParkingPersistencePort.class);
      Parking parking = Parking.create("Parqueo San Martín", "PSM", null, "Jr. San Martín", null, null, 1L);
      parking.setId(1L);
      when(parkingPort.findById(1L)).thenReturn(Optional.of(parking));
      when(parkingPort.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(parking));

      ZonePersistencePort zonePort = mock(ZonePersistencePort.class);
      Zone north = Zone.create(1L, "Zona Norte", "NORTE", null, null, null, null, null, 1L);
      north.setId(10L);
      Zone other = Zone.create(2L, "Zona A", "A", null, null, null, null, null, 1L);
      other.setId(20L);
      when(zonePort.findById(10L)).thenReturn(Optional.of(north));
      when(zonePort.findById(20L)).thenReturn(Optional.of(other));
      when(zonePort.findByIdIncludingDeleted(10L)).thenReturn(Optional.of(north));

      SpacePersistencePort spacePort = mock(SpacePersistencePort.class);
      Space a01 = Space.create(10L, "A-01", "PERPENDICULAR", null, null, null, null, false, 1L);
      a01.setId(100L);
      Space b01 = Space.create(11L, "B-01", "PERPENDICULAR", null, null, null, null, false, 1L);
      b01.setId(110L);
      when(spacePort.findById(100L)).thenReturn(Optional.of(a01));
      when(spacePort.findById(110L)).thenReturn(Optional.of(b01));
      when(spacePort.findByIdIncludingDeleted(100L)).thenReturn(Optional.of(a01));

      VehiclePersistencePort vehiclePort = mock(VehiclePersistencePort.class);
      AtomicLong vehicleIds = new AtomicLong(500);
      when(vehiclePort.findByPlateNumber(anyString())).thenAnswer(i -> Optional.ofNullable(vehicles.get((String) i.getArgument(0))));
      when(vehiclePort.save(any())).thenAnswer(i -> {
         Vehicle v = i.getArgument(0);
         if (v.getId() == null) {
            v.setId(vehicleIds.incrementAndGet());
         }
         vehicles.put(v.getLicensePlate(), v);
         return v;
      });
      when(vehiclePort.findById(anyLong())).thenAnswer(i -> vehicles.values().stream()
            .filter(v -> v.getId().equals(i.getArgument(0))).findFirst());
      Vehicle known = new Vehicle("XYZ-789");
      known.setId(7L);
      vehicles.put("XYZ-789", known);

      TransactionPersistencePort transactionPort = mock(TransactionPersistencePort.class);
      Transaction stay = mock(Transaction.class);
      when(stay.getId()).thenReturn(900L);
      when(stay.getParkingId()).thenReturn(1L);
      when(stay.getZoneId()).thenReturn(10L);
      when(stay.getSpaceId()).thenReturn(100L);
      when(stay.getVehicleId()).thenReturn(7L);
      when(stay.getCustomerId()).thenReturn(null);
      when(transactionPort.findById(900L)).thenReturn(Optional.of(stay));

      CurrentUserPort currentUser = mock(CurrentUserPort.class);
      when(currentUser.requireCurrentUserId()).thenReturn(3L);
      users = mock(UserDirectoryPort.class);
      when(users.findUser(3L)).thenReturn(Optional.of(new UserDirectoryPort.UserSummary(3L, "Ana Operadora", "a@b.c", true)));
      dutyChecker = mock(OperatorDutyChecker.class);

      service = new InfractionService(infractionPort, parkingPort, zonePort, spacePort, transactionPort, vehiclePort,
            mock(CustomerPersistencePort.class), users, currentUser, dutyChecker, CLOCK, 7);
   }

   private RegisterInfractionCommand command(Long zoneId, Long spaceId, Long transactionId, String plate, String fine) {
      return new RegisterInfractionCommand(1L, zoneId, spaceId, transactionId, plate, "WRONG_SPACE", "MINOR",
            "Ocupa un espacio reservado", null, fine == null ? null : new BigDecimal(fine), null, null, null);
   }

   @Test
   @DisplayName("registra con código, crea el vehículo nuevo y copia el nombre de quien registra")
   void register() {
      InfractionDto dto = service.register(command(10L, 100L, null, "abc123", "50"));

      assertThat(dto.infractionCode()).isEqualTo("INF-2026-000001");
      assertThat(dto.plateNumber()).isEqualTo("ABC-123");
      assertThat(dto.parkingName()).isEqualTo("Parqueo San Martín");
      assertThat(dto.zoneCode()).isEqualTo("NORTE");
      assertThat(dto.spaceCode()).isEqualTo("A-01");
      assertThat(dto.detectedByName()).isEqualTo("Ana Operadora");
      assertThat(dto.amountDue()).isEqualByComparingTo("50");
      assertThat(dto.fineDueDate()).isEqualTo(LocalDate.of(2026, 12, 4));
      assertThat(dto.payable()).isTrue();
      assertThat(vehicles).containsKey("ABC-123");
      verify(dutyChecker).ensureOnDuty(eq(3L), any(), eq(10L), any());
   }

   @Test
   @DisplayName("con la estadía toma su zona y espacio; la placa debe coincidir")
   void fromStay() {
      InfractionDto dto = service.register(command(null, null, 900L, "XYZ789", null));
      assertThat(dto.zoneId()).isEqualTo(10L);
      assertThat(dto.spaceId()).isEqualTo(100L);
      assertThat(dto.transactionId()).isEqualTo(900L);
      assertThat(dto.vehicleId()).isEqualTo(7L);

      assertThatThrownBy(() -> service.register(command(null, null, 900L, "ABC123", null)))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("XYZ-789");
   }

   @Test
   @DisplayName("rechaza zona de otro parqueo, espacio de otra zona y a quien no está de turno")
   void rejects() {
      assertThatThrownBy(() -> service.register(command(20L, null, null, "ABC123", null)))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("zona");
      assertThatThrownBy(() -> service.register(command(10L, 110L, null, "ABC123", null)))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("espacio");

      doThrow(new OperatorNotOnDutyException("Parqueo San Martín"))
            .when(dutyChecker).ensureOnDuty(anyLong(), any(), any(), any());
      assertThatThrownBy(() -> service.register(command(10L, null, null, "ABC123", null)))
            .isInstanceOf(OperatorNotOnDutyException.class);
      assertThat(stored).isEmpty();
   }

   @Test
   @DisplayName("si auth no responde, se registra igual sin el nombre")
   void withoutUserName() {
      when(users.findUser(3L)).thenThrow(new RuntimeException("auth caído"));
      InfractionDto dto = service.register(command(10L, null, null, "ABC123", null));
      assertThat(dto.detectedByName()).isNull();
   }

   @Test
   @DisplayName("cambiar la multa, anular y filtros de fechas invertidas")
   void changeAndDismiss() {
      InfractionDto dto = service.register(command(10L, null, null, "ABC123", null));
      assertThat(dto.payable()).isFalse();

      InfractionDto withFine = service.changeFine(dto.id(), new ChangeInfractionFineCommand(new BigDecimal("30"), null));
      assertThat(withFine.payable()).isTrue();
      assertThat(withFine.displayStatus()).isEqualTo(Infraction.STATUS_PENDING);

      InfractionDto dismissed = service.dismiss(dto.id(), "Registro duplicado");
      assertThat(dismissed.displayStatus()).isEqualTo(Infraction.RESOLUTION_DISMISSED);
      assertThat(dismissed.editable()).isFalse();

      assertThatThrownBy(() -> service.list(new InfractionFilter(null, null, null, null,
            LocalDate.of(2026, 11, 20), LocalDate.of(2026, 11, 1)), PageRequest.of(0, 20)))
            .isInstanceOf(ParkingDomainException.class);
   }
}
