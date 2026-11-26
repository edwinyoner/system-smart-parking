package com.winnersystems.smartparking.parking.application.service.publicinfo;

import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingCapacity;
import com.winnersystems.smartparking.parking.application.dto.query.PublicParkingDto;
import com.winnersystems.smartparking.parking.application.dto.query.ZoneCapacity;
import com.winnersystems.smartparking.parking.application.port.output.ParkingCapacityPort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ZonePersistencePort;
import com.winnersystems.smartparking.parking.application.service.parkingshiftrate.ParkingScheduleResolver;
import com.winnersystems.smartparking.parking.application.service.parkingshiftrate.ParkingScheduleResolver.Resolved;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.ParkingShiftRate;
import com.winnersystems.smartparking.parking.domain.model.Rate;
import com.winnersystems.smartparking.parking.domain.model.Shift;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicParkingServiceTest {

   private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 26, 10, 0);
   private static final ZoneId LIMA = ZoneId.of("America/Lima");

   private final ParkingPersistencePort parkingPort = mock(ParkingPersistencePort.class);
   private final ZonePersistencePort zonePort = mock(ZonePersistencePort.class);
   private final ParkingCapacityPort capacityPort = mock(ParkingCapacityPort.class);
   private final ParkingScheduleResolver resolver = mock(ParkingScheduleResolver.class);

   private PublicParkingService service;
   private Parking parking;
   private Resolved morning;

   @BeforeEach
   void setUp() {
      service = new PublicParkingService(parkingPort, zonePort, capacityPort, resolver,
            Clock.fixed(NOW.atZone(LIMA).toInstant(), LIMA));
      parking = parking(1L, "Parqueo San Martín", "PSM");

      Shift shift = Shift.create("Mañana", "M", LocalTime.of(6, 0), LocalTime.of(11, 59), null, true, 1L);
      shift.setId(1L);
      Rate rate = Rate.create("Estándar", null, new BigDecimal("2.50"), new Rate.BillingRules(60, 0, 5, 5), true, 1L);
      rate.setId(50L);
      morning = new Resolved(ParkingShiftRate.create(1L, 1L, 50L, true, 1L), shift, rate);

      Zone a = zone(10L, "A");
      Zone b = zone(11L, "B");
      b.changeStatus(Zone.STATUS_MAINTENANCE);
      when(parkingPort.findById(1L)).thenReturn(Optional.of(parking));
      when(zonePort.findByParkingId(1L)).thenReturn(List.of(b, a));
      when(capacityPort.capacityOf(anyCollection())).thenReturn(Map.of(1L, new ParkingCapacity(2, 8, 3, 4)));
      when(capacityPort.capacityOfZones(anyCollection())).thenReturn(Map.of(
            10L, new ZoneCapacity(5, 3, 2), 11L, new ZoneCapacity(3, 1, 2)));
      when(resolver.resolve(eq(1L), any())).thenReturn(Optional.of(morning));
      when(resolver.usableConfigs(1L)).thenReturn(List.of(morning));
   }

   @Test
   void disponibilidadParaPanelesCuentaSoloZonasOperativas() {
      PublicParkingDto.Availability a = service.getAvailability(1L);

      assertThat(a.openNow()).isTrue();
      assertThat(a.totalSpaces()).isEqualTo(8);
      assertThat(a.availableSpaces()).isEqualTo(3);
      // Zonas ordenadas por código; la zona en mantenimiento publica 0 libres
      assertThat(a.zones()).extracting(PublicParkingDto.ZoneAvailability::code).containsExactly("A", "B");
      assertThat(a.zones()).extracting(PublicParkingDto.ZoneAvailability::availableSpaces).containsExactly(3, 0);
   }

   @Test
   void fueraDeHorarioPublicaCeroLibres() {
      when(resolver.resolve(eq(1L), any())).thenReturn(Optional.empty());

      PublicParkingDto.Availability a = service.getAvailability(1L);

      assertThat(a.openNow()).isFalse();
      assertThat(a.availableSpaces()).isZero();
      assertThat(a.zones()).allMatch(z -> z.availableSpaces() == 0);
   }

   @Test
   void parqueoEnMantenimientoSePublicaSinLibres() {
      parking.changeStatus(Parking.STATUS_MAINTENANCE);

      PublicParkingDto.Availability a = service.getAvailability(1L);

      assertThat(a.operational()).isFalse();
      assertThat(a.status()).isEqualTo("MAINTENANCE");
      assertThat(a.availableSpaces()).isZero();
   }

   @Test
   void parqueoDesactivadoNoSePublica() {
      parking.changeStatus(Parking.STATUS_INACTIVE);

      assertThatThrownBy(() -> service.getAvailability(1L)).isInstanceOf(ResourceNotFoundException.class);
      assertThatThrownBy(() -> service.getParking(1L)).isInstanceOf(ResourceNotFoundException.class);
   }

   @Test
   void detalleConHorarioYTarifaVigente() {
      PublicParkingDto.ParkingDetail d = service.getParking(1L);

      assertThat(d.parking().currentShift().startTime()).isEqualTo("06:00");
      assertThat(d.parking().currentRate().amountPerHour()).isEqualByComparingTo("2.50");
      assertThat(d.schedule()).hasSize(1);
      assertThat(d.schedule().get(0).shift().endTime()).isEqualTo("11:59");
   }

   @Test
   void listadoOmiteDesactivados() {
      Parking inactive = parking(2L, "Parqueo Cerrado", "PCE");
      inactive.changeStatus(Parking.STATUS_INACTIVE);
      when(parkingPort.findAll(any(), any(), any(), eq(false)))
            .thenReturn(new PageResult<>(List.of(parking, inactive), 0, 100, 2));
      when(resolver.resolve(eq(2L), any())).thenReturn(Optional.empty());

      List<PublicParkingDto.ParkingCard> cards = service.listParkings();

      assertThat(cards).extracting(PublicParkingDto.ParkingCard::code).containsExactly("PSM");
      assertThat(cards.get(0).availableSpaces()).isEqualTo(3);
   }

   @Test
   void parqueoInexistente() {
      when(parkingPort.findById(9L)).thenReturn(Optional.empty());

      assertThatThrownBy(() -> service.getAvailability(9L)).isInstanceOf(ResourceNotFoundException.class);
   }

   private static Parking parking(Long id, String name, String code) {
      Parking p = Parking.create(name, code, null, "Jr. San Martín", null, null, 1L);
      p.setId(id);
      return p;
   }

   private static Zone zone(Long id, String code) {
      Zone z = Zone.create(1L, "Zona " + code, code, null, null, null, null, null, 1L);
      z.setId(id);
      return z;
   }
}
