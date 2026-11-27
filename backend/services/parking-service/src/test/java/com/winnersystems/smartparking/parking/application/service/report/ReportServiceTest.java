package com.winnersystems.smartparking.parking.application.service.report;

import com.winnersystems.smartparking.parking.application.dto.query.*;
import com.winnersystems.smartparking.parking.application.dto.query.ReportDto.*;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ReportServiceTest {

   static final ZoneId PERU = ZoneId.of("America/Lima");
   static final Clock CLOCK = Clock.fixed(LocalDateTime.of(2026, 11, 27, 10, 0).atZone(PERU).toInstant(), PERU);
   static final LocalDate YESTERDAY = LocalDate.of(2026, 11, 26);

   private TransactionPersistencePort transactions;
   private ReportService service;

   static LocalDateTime at(int day, int hour, int minute) {
      return LocalDateTime.of(2026, 11, day, hour, minute);
   }

   static StayWindow stay(long id, long zone, long vehicle, LocalDateTime in, LocalDateTime out, String status) {
      return new StayWindow(id, 1L, zone, vehicle, in, out, status);
   }

   @BeforeEach
   void setUp() {
      transactions = mock(TransactionPersistencePort.class);
      when(transactions.findStayWindows(any(), any(), any())).thenReturn(List.of(
            stay(1, 10, 1, at(25, 22, 0), at(26, 8, 30), "COMPLETED"),   // venía del día anterior
            stay(2, 10, 2, at(26, 8, 0), at(26, 9, 30), "COMPLETED"),
            stay(3, 20, 1, at(26, 18, 0), null, "ACTIVE"),                // sigue dentro
            stay(4, 20, 3, at(26, 8, 15), null, "CANCELLED"),             // error de registro
            stay(5, 10, 2, at(26, 12, 0), at(26, 12, 30), "COMPLETED")    // el mismo vehículo, otra vez
      ));

      InfractionPersistencePort infractions = mock(InfractionPersistencePort.class);
      when(infractions.countRegisteredBetween(any(), any(), any())).thenReturn(List.of(
            new InfractionPersistencePort.ZoneCount(1L, 10L, 2), new InfractionPersistencePort.ZoneCount(1L, null, 1)));

      ParkingPersistencePort parkings = mock(ParkingPersistencePort.class);
      Parking psm = Parking.create("Parqueo San Martín", "PSM", null, "Jr. San Martín", null, null, 1L);
      psm.setId(1L);
      when(parkings.findAllActive()).thenReturn(List.of(psm));
      when(parkings.findByIdIncludingDeleted(1L)).thenReturn(java.util.Optional.of(psm));

      ZonePersistencePort zones = mock(ZonePersistencePort.class);
      Zone north = Zone.create(1L, "Zona Norte", "NORTE", null, null, null, null, null, 1L);
      north.setId(10L);
      Zone south = Zone.create(1L, "Zona Sur", "SUR", null, null, null, null, null, 1L);
      south.setId(20L);
      when(zones.findByParkingId(1L)).thenReturn(List.of(south, north));

      ParkingCapacityPort capacity = mock(ParkingCapacityPort.class);
      when(capacity.capacityOf(any())).thenReturn(Map.of(1L, new ParkingCapacity(2, 30, 29, 1)));
      when(capacity.capacityOfZones(any())).thenReturn(Map.of(
            10L, new ZoneCapacity(20, 20, 0), 20L, new ZoneCapacity(10, 9, 1)));

      service = new ReportService(transactions, infractions, parkings, zones, capacity, CLOCK);
   }

   @Test
   @DisplayName("diario: totales del parqueo y de cada zona, contando lo que venía de antes")
   void daily() {
      DailyReport r = service.daily(YESTERDAY, null);

      assertThat(r.until()).isEqualTo(at(27, 0, 0));
      ParkingDay p = r.parkings().get(0);
      assertThat(p.totalSpaces()).isEqualTo(30);
      DaySummary s = p.summary();
      assertThat(s.entries()).isEqualTo(3);
      assertThat(s.exits()).isEqualTo(3);
      assertThat(s.vehicles()).isEqualTo(2);          // vehículos 1 y 2 (el 3 fue cancelado)
      assertThat(s.stays()).isEqualTo(4);
      assertThat(s.insideAtClose()).isEqualTo(1);
      assertThat(s.cancelled()).isEqualTo(1);
      assertThat(s.averageStayMinutes()).isEqualTo(250);   // (630 + 90 + 30) / 3
      assertThat(s.infractions()).isEqualTo(3);
      assertThat(p.peakHour()).isEqualTo(8);
      assertThat(p.peakOccupied()).isEqualTo(2);

      // Zonas por código: NORTE, SUR
      assertThat(p.zones()).extracting(ZoneDay::zoneCode).containsExactly("NORTE", "SUR");
      DaySummary north = p.zones().get(0).summary();
      assertThat(north.entries()).isEqualTo(2);
      assertThat(north.exits()).isEqualTo(3);
      assertThat(north.vehicles()).isEqualTo(2);
      assertThat(north.infractions()).isEqualTo(2);
      DaySummary south = p.zones().get(1).summary();
      assertThat(south.vehicles()).isEqualTo(1);
      assertThat(south.insideAtClose()).isEqualTo(1);
      assertThat(south.cancelled()).isEqualTo(1);
      assertThat(south.averageStayMinutes()).isNull();
      assertThat(p.zones().get(1).totalSpaces()).isEqualTo(10);

      assertThat(r.totals().vehicles()).isEqualTo(2);
   }

   @Test
   @DisplayName("ocupación por hora: lo de antes cuenta al empezar; las horas de hoy que no llegan van en cero")
   void occupancy() {
      OccupancyReport r = service.occupancy(YESTERDAY, 1L, null);
      assertThat(r.capacity()).isEqualTo(30);
      assertThat(r.hours()).hasSize(24);
      assertThat(r.hours().get(0).occupiedAtStart()).isEqualTo(1);
      HourSlot eight = r.hours().get(8);
      assertThat(eight.occupiedAtStart()).isEqualTo(2);    // el 1 y el 2, que entra justo a las 08:00
      assertThat(eight.maxOccupied()).isEqualTo(2);
      assertThat(eight.exits()).isEqualTo(1);
      assertThat(r.hours().get(12).maxOccupied()).isEqualTo(1);
      assertThat(r.hours().get(23).occupiedAtStart()).isEqualTo(1);
      assertThat(r.peakHour()).isEqualTo(8);
      assertThat(r.zones()).hasSize(2);

      OccupancyReport today = service.occupancy(LocalDate.of(2026, 11, 27), 1L, null);
      assertThat(today.hours().get(9).future()).isFalse();
      assertThat(today.hours().get(10).future()).isTrue();
   }

   @Test
   @DisplayName("ocupación de una zona; zona ajena, fecha futura y sin parqueo se rechazan")
   void occupancyZoneAndErrors() {
      OccupancyReport south = service.occupancy(YESTERDAY, 1L, 20L);
      assertThat(south.zoneName()).isEqualTo("Zona Sur");
      assertThat(south.capacity()).isEqualTo(10);
      assertThat(south.entries()).isEqualTo(1);
      assertThat(south.hours().get(8).maxOccupied()).isZero();   // la cancelada no cuenta

      assertThatThrownBy(() -> service.occupancy(YESTERDAY, 1L, 99L)).isInstanceOf(ResourceNotFoundException.class);
      assertThatThrownBy(() -> service.daily(LocalDate.of(2026, 11, 28), null)).isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> service.occupancy(YESTERDAY, null, null)).isInstanceOf(ParkingDomainException.class);
   }
}
