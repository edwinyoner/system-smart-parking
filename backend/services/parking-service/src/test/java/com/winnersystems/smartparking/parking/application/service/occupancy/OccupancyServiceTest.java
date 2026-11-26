package com.winnersystems.smartparking.parking.application.service.occupancy;

import com.winnersystems.smartparking.parking.application.dto.query.ActiveStayView;
import com.winnersystems.smartparking.parking.application.dto.query.OccupancyDto.ParkingMap;
import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.application.port.output.OperatorAssignmentPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingCapacityPort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.SpacePersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.TransactionPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ZonePersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ActionNotAllowedException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.OperatorAssignment;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.Space;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Mapa de ocupación: conteos por estado, zonas que reciben vehículos y placas solo con permiso */
class OccupancyServiceTest {

   private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 10, 0);
   private static final ZoneId LIMA = ZoneId.of("America/Lima");

   private final ParkingPersistencePort parkingPort = mock(ParkingPersistencePort.class);
   private final ZonePersistencePort zonePort = mock(ZonePersistencePort.class);
   private final SpacePersistencePort spacePort = mock(SpacePersistencePort.class);
   private final TransactionPersistencePort transactionPort = mock(TransactionPersistencePort.class);
   private final ParkingCapacityPort capacityPort = mock(ParkingCapacityPort.class);
   private final OperatorAssignmentPersistencePort assignmentPort = mock(OperatorAssignmentPersistencePort.class);
   private final CurrentUserPort currentUser = mock(CurrentUserPort.class);
   private OccupancyService service;

   @BeforeEach
   void setUp() {
      service = new OccupancyService(parkingPort, zonePort, spacePort, transactionPort, capacityPort, assignmentPort, currentUser,
            Clock.fixed(NOW.atZone(LIMA).toInstant(), LIMA));

      Parking parking = Parking.create("Parqueo San Martín", "PSM", null, "Jr. San Martín", null, null, 1L);
      parking.setId(1L);
      Zone zoneB = zone(11L, "B");
      Zone zoneA = zone(10L, "A");
      zoneB.changeStatus("MAINTENANCE");

      Space a1 = space(100L, 10L, "A-01");
      Space a2 = space(101L, 10L, "A-02");
      Space a3 = space(102L, 10L, "A-03");
      Space b1 = space(200L, 11L, "B-01");
      a2.occupy();
      a3.changeStatus(Space.STATUS_MAINTENANCE);

      when(parkingPort.findById(1L)).thenReturn(Optional.of(parking));
      // Por defecto ve todos los parqueos; las pruebas de la regla 8 lo quitan
      when(currentUser.hasAuthority("occupancy.view-all")).thenReturn(true);
      when(zonePort.findByParkingId(1L)).thenReturn(List.of(zoneB, zoneA));
      when(spacePort.findByParkingId(1L)).thenReturn(List.of(a1, a2, a3, b1));
      when(transactionPort.findActiveStaysByParkingId(1L))
            .thenReturn(List.of(new ActiveStayView(55L, 101L, "ABC-123", NOW.minusHours(1))));
   }

   @Test
   void cuentaPorEstadoYSoloLasZonasActivasRecibenVehiculos() {
      ParkingMap map = service.getParkingMap(1L);

      assertEquals(List.of("A", "B"), map.zones().stream().map(z -> z.code()).toList(), "zonas por código");
      assertEquals(4, map.counts().total());
      assertEquals(2, map.counts().available());
      assertEquals(1, map.counts().occupied());
      assertEquals(1, map.counts().maintenance());
      // B-01 está libre pero su zona está en mantenimiento: no cuenta para entrar
      assertEquals(1, map.availableForEntry());
      assertFalse(map.zones().get(1).operational());
      assertEquals(NOW, map.generatedAt());
   }

   @Test
   void conTransactionsViewMuestraLaPlacaDelEspacioOcupado() {
      when(currentUser.hasAuthority("transactions.view")).thenReturn(true);

      ParkingMap map = service.getParkingMap(1L);

      var a2 = map.zones().get(0).spaces().get(1);
      assertTrue(map.showsVehicles());
      assertEquals("ABC-123", a2.plateNumber());
      assertEquals(55L, a2.transactionId());
   }

   @Test
   void sinTransactionsViewNoConsultaNiMuestraPlacas() {
      ParkingMap map = service.getParkingMap(1L);

      assertFalse(map.showsVehicles());
      assertNull(map.zones().get(0).spaces().get(1).plateNumber());
      verify(transactionPort, never()).findActiveStaysByParkingId(anyLong());
   }

   @Test
   void sinViewAllSoloVeLasZonasDeSuAsignacionVigente() {
      when(currentUser.hasAuthority("occupancy.view-all")).thenReturn(false);
      when(currentUser.getCurrentUserId()).thenReturn(Optional.of(7L));
      when(assignmentPort.findAll(null, null, 7L)).thenReturn(List.of(assignment(1L, 10L, LocalDate.of(2026, 11, 1))));

      ParkingMap map = service.getParkingMap(1L);

      assertEquals(List.of("A"), map.zones().stream().map(z -> z.code()).toList());
      assertEquals(3, map.counts().total());
   }

   @Test
   void sinViewAllYSinAsignacionVigenteDa403() {
      when(currentUser.hasAuthority("occupancy.view-all")).thenReturn(false);
      when(currentUser.getCurrentUserId()).thenReturn(Optional.of(7L));
      // Empieza mañana: todavía no cuenta
      when(assignmentPort.findAll(null, null, 7L)).thenReturn(List.of(assignment(1L, null, LocalDate.of(2026, 11, 27))));

      assertThrows(ActionNotAllowedException.class, () -> service.getParkingMap(1L));
   }

   @Test
   void parqueoInexistenteDa404() {
      when(parkingPort.findById(9L)).thenReturn(Optional.empty());
      assertThrows(ResourceNotFoundException.class, () -> service.getParkingMap(9L));
   }

   private static OperatorAssignment assignment(Long parkingId, Long zoneId, LocalDate start) {
      OperatorAssignment a = new OperatorAssignment();
      a.setOperatorId(7L);
      a.setParkingId(parkingId);
      a.setZoneId(zoneId);
      a.setShiftId(1L);
      a.setStartDate(start);
      a.setEndDate(OperatorAssignment.OPEN_END);
      return a;
   }

   private static Zone zone(Long id, String code) {
      Zone z = Zone.create(1L, "Zona " + code, code, null, null, null, null, null, 1L);
      z.setId(id);
      return z;
   }

   private static Space space(Long id, Long zoneId, String code) {
      Space s = Space.create(zoneId, code, "PERPENDICULAR", null, null, null, null, false, 1L);
      s.setId(id);
      return s;
   }
}
