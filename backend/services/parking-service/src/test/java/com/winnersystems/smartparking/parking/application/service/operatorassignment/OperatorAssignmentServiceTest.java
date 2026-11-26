package com.winnersystems.smartparking.parking.application.service.operatorassignment;

import com.winnersystems.smartparking.parking.application.dto.command.AssignOperatorCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateOperatorAssignmentCommand;
import com.winnersystems.smartparking.parking.application.dto.query.OperatorAssignmentDto;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Asignaciones guardadas en una lista en memoria: el falso puerto aplica los mismos
 * filtros de fechas que las consultas JPQL.
 */
class OperatorAssignmentServiceTest {

   static final LocalDate TODAY = LocalDate.of(2026, 11, 26);

   private final List<OperatorAssignment> stored = new ArrayList<>();
   private final Map<Long, Shift> shifts = new HashMap<>();
   private final Map<Long, Parking> parkings = new HashMap<>();
   private final List<ParkingShiftRate> configs = new ArrayList<>();
   private UserDirectoryPort users;
   private OperatorAssignmentService service;

   @BeforeEach
   void setUp() {
      OperatorAssignmentPersistencePort port = mock(OperatorAssignmentPersistencePort.class);
      AtomicLong ids = new AtomicLong(100);
      when(port.save(any())).thenAnswer(i -> {
         OperatorAssignment a = i.getArgument(0);
         if (a.getId() == null) {
            a.setId(ids.incrementAndGet());
            stored.add(a);
         }
         return a;
      });
      when(port.findById(anyLong())).thenAnswer(i -> stored.stream().filter(a -> a.getId().equals(i.getArgument(0))).findFirst());
      doAnswer(i -> stored.removeIf(a -> a.getId().equals(i.getArgument(0)))).when(port).deleteById(anyLong());
      when(port.findAll(any(), any(), any())).thenAnswer(i -> stored.stream()
            .filter(a -> i.getArgument(0) == null || a.getParkingId().equals(i.getArgument(0)))
            .filter(a -> i.getArgument(1) == null || Objects.equals(a.getZoneId(), i.getArgument(1)))
            .filter(a -> i.getArgument(2) == null || a.getOperatorId().equals(i.getArgument(2)))
            .toList());
      when(port.findByOperatorOverlapping(anyLong(), any(), any())).thenAnswer(i -> stored.stream()
            .filter(a -> a.getOperatorId().equals(i.getArgument(0)) && a.overlapsDates(i.getArgument(1), i.getArgument(2)))
            .toList());
      when(port.findByParkingShiftOverlapping(anyLong(), anyLong(), any(), any())).thenAnswer(i -> stored.stream()
            .filter(a -> a.getParkingId().equals(i.getArgument(0)) && a.getShiftId().equals(i.getArgument(1))
                  && a.overlapsDates(i.getArgument(2), i.getArgument(3)))
            .toList());

      ParkingPersistencePort parkingPort = mock(ParkingPersistencePort.class);
      parking(1L, "Parqueo San Martín");
      parking(2L, "Parqueo Las Banderas");
      when(parkingPort.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(parkings.get((Long) i.getArgument(0))));
      when(parkingPort.findByIdIncludingDeleted(anyLong())).thenAnswer(i -> Optional.ofNullable(parkings.get((Long) i.getArgument(0))));
      when(parkingPort.findAllByIdsIncludingDeleted(any())).thenAnswer(i -> List.copyOf(parkings.values()));

      ZonePersistencePort zonePort = mock(ZonePersistencePort.class);
      Zone north = Zone.create(1L, "Zona Norte", "NORTE", null, null, null, null, null, 1L);
      north.setId(10L);
      Zone south = Zone.create(1L, "Zona Sur", "SUR", null, null, null, null, null, 1L);
      south.setId(11L);
      Zone other = Zone.create(2L, "Zona A", "A", null, null, null, null, null, 1L);
      other.setId(20L);
      Map<Long, Zone> zones = Map.of(10L, north, 11L, south, 20L, other);
      when(zonePort.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(zones.get((Long) i.getArgument(0))));
      when(zonePort.findByIdIncludingDeleted(anyLong())).thenAnswer(i -> Optional.ofNullable(zones.get((Long) i.getArgument(0))));
      when(zonePort.findAllByIdsIncludingDeleted(any())).thenAnswer(i -> List.copyOf(zones.values()));

      ShiftPersistencePort shiftPort = mock(ShiftPersistencePort.class);
      shift(1L, "06:00", "13:59");
      shift(2L, "14:00", "21:59");
      shift(3L, "12:00", "17:59");
      when(shiftPort.findById(anyLong())).thenAnswer(i -> Optional.ofNullable(shifts.get((Long) i.getArgument(0))));
      when(shiftPort.findByIdIncludingDeleted(anyLong())).thenAnswer(i -> Optional.ofNullable(shifts.get((Long) i.getArgument(0))));

      ParkingShiftRatePersistencePort configPort = mock(ParkingShiftRatePersistencePort.class);
      configs.add(ParkingShiftRate.create(1L, 1L, 50L, true, 1L));
      configs.add(ParkingShiftRate.create(1L, 2L, 50L, true, 1L));
      configs.add(ParkingShiftRate.create(2L, 3L, 50L, true, 1L));
      configs.add(ParkingShiftRate.create(2L, 1L, 50L, false, 1L));
      when(configPort.findByParkingId(anyLong())).thenAnswer(i -> configs.stream()
            .filter(c -> c.getParkingId().equals(i.getArgument(0))).toList());

      users = mock(UserDirectoryPort.class);
      when(users.findUser(anyLong())).thenAnswer(i -> {
         Long id = i.getArgument(0);
         return id > 90 ? Optional.empty() : Optional.of(new UserDirectoryPort.UserSummary(id, "Operador " + id, "op@x.pe", id != 8L));
      });

      // 26/11/2026 15:30 en Lima: dentro del turno 2 (14:00-21:59)
      Clock clock = Clock.fixed(Instant.parse("2026-11-26T20:30:00Z"), ZoneId.of("America/Lima"));
      service = new OperatorAssignmentService(port, parkingPort, zonePort, shiftPort, configPort, users,
            () -> Optional.of(9L), clock);
   }

   private void parking(Long id, String name) {
      Parking p = Parking.create(name, "P" + id, null, "Huaraz", null, null, 1L);
      p.setId(id);
      parkings.put(id, p);
   }

   private void shift(Long id, String from, String to) {
      Shift s = Shift.create("Turno " + id, "T" + id, LocalTime.parse(from), LocalTime.parse(to), null, true, 1L);
      s.setId(id);
      shifts.put(id, s);
   }

   private OperatorAssignmentDto assign(long operator, long parking, Long zone, long shift) {
      return service.assign(new AssignOperatorCommand(operator, parking, zone, shift, null, null));
   }

   @Test
   @DisplayName("asignar: copia el nombre, calcula si está de turno ahora")
   void assignOk() {
      OperatorAssignmentDto dto = assign(5, 1, null, 2);
      assertThat(dto.operatorName()).isEqualTo("Operador 5");
      assertThat(dto.scope()).isEqualTo("PARKING");
      assertThat(dto.status()).isEqualTo(OperatorAssignment.STATUS_ACTIVE);
      assertThat(dto.onDutyNow()).isTrue();
      assertThat(dto.createdBy()).isEqualTo(9L);
      assertThat(assign(6, 1, 10L, 1).onDutyNow()).isFalse();
   }

   @Test
   @DisplayName("datos inválidos: usuario inexistente o desactivado, zona de otro parqueo, turno donde no atiende")
   void invalid() {
      assertThatThrownBy(() -> assign(99, 1, null, 1)).isInstanceOf(ResourceNotFoundException.class);
      assertThatThrownBy(() -> assign(8, 1, null, 1)).hasMessageContaining("desactivado");
      assertThatThrownBy(() -> assign(5, 1, 20L, 1)).isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> assign(5, 2, null, 1)).hasMessageContaining("no atiende");
      assertThatThrownBy(() -> assign(5, 1, null, 3)).hasMessageContaining("no atiende");
   }

   @Test
   @DisplayName("en un turno: un operador de parqueo o uno por zona, no ambos")
   void parkingOrZones() {
      assign(5, 1, 10L, 1);
      assign(6, 1, 11L, 1);
      assertThatThrownBy(() -> assign(7, 1, 10L, 1)).hasMessageContaining("ya atiende esta zona");
      assertThatThrownBy(() -> assign(7, 1, null, 1)).hasMessageContaining("operadores por zona");

      assign(7, 1, null, 2);
      assertThatThrownBy(() -> assign(5, 1, 10L, 2)).hasMessageContaining("todo el parqueo");
   }

   @Test
   @DisplayName("el mismo operador no puede estar en dos sitios a la vez")
   void sameOperator() {
      assign(5, 1, null, 1);
      // Parqueo 2, turno 12:00-17:59: se cruza con 06:00-13:59
      assertThatThrownBy(() -> assign(5, 2, null, 3)).hasMessageContaining("se cruza");
      // Turno de la tarde en el mismo parqueo: no se cruza
      assertThat(assign(5, 1, 10L, 2).scope()).isEqualTo("ZONE");
   }

   @Test
   @DisplayName("fechas que no se cruzan sí permiten repetir zona y turno")
   void consecutiveDates() {
      service.assign(new AssignOperatorCommand(5L, 1L, 10L, 1L, TODAY, TODAY.plusDays(9)));
      assertThatThrownBy(() -> service.assign(new AssignOperatorCommand(6L, 1L, 10L, 1L, TODAY.plusDays(9), null)))
            .isInstanceOf(ResourceConflictException.class);
      assertThat(service.assign(new AssignOperatorCommand(6L, 1L, 10L, 1L, TODAY.plusDays(10), null)).status())
            .isEqualTo(OperatorAssignment.STATUS_PENDING);
   }

   @Test
   @DisplayName("cambiar fechas revalida los cruces sin chocar consigo misma")
   void update() {
      OperatorAssignmentDto first = service.assign(new AssignOperatorCommand(5L, 1L, 10L, 1L, TODAY, TODAY.plusDays(9)));
      service.assign(new AssignOperatorCommand(6L, 1L, 10L, 1L, TODAY.plusDays(10), null));

      assertThat(service.update(first.id(), new UpdateOperatorAssignmentCommand(null, TODAY.plusDays(5))).endDate())
            .isEqualTo(TODAY.plusDays(5));
      assertThatThrownBy(() -> service.update(first.id(), new UpdateOperatorAssignmentCommand(null, null)))
            .isInstanceOf(ResourceConflictException.class);
   }

   @Test
   @DisplayName("finalizar una que empieza hoy la cancela; listar exige parqueo u operador")
   void finishAndList() {
      OperatorAssignmentDto dto = assign(5, 1, null, 1);
      assertThat(service.finish(dto.id())).isEmpty();
      assertThat(stored).isEmpty();

      assign(5, 1, null, 2);
      service.assign(new AssignOperatorCommand(6L, 1L, 10L, 1L, TODAY.plusDays(3), null));
      assertThat(service.list(1L, null, null, null)).hasSize(2);
      assertThat(service.list(1L, null, null, "pending")).extracting(OperatorAssignmentDto::operatorId).containsExactly(6L);
      assertThat(service.list(null, null, 5L, null)).hasSize(1);
      assertThatThrownBy(() -> service.list(null, null, null, null)).isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> service.list(1L, null, null, "LIBRE")).isInstanceOf(ParkingDomainException.class);
      assertThat(service.listMine(null)).isEmpty();
   }
}
