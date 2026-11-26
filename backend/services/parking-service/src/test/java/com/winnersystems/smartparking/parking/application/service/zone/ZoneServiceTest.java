package com.winnersystems.smartparking.parking.application.service.zone;

import com.winnersystems.smartparking.parking.application.dto.command.CreateZoneCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateZoneCommand;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.ZoneCapacity;
import com.winnersystems.smartparking.parking.application.dto.query.ZoneDto;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ZoneServiceTest {

   private ZonePersistencePort zones;
   private ParkingPersistencePort parkings;
   private ParkingCapacityPort capacity;
   private TransactionPersistencePort transactions;
   private ZoneService service;
   private Parking parking;

   @BeforeEach
   void setUp() {
      zones = mock(ZonePersistencePort.class);
      parkings = mock(ParkingPersistencePort.class);
      capacity = mock(ParkingCapacityPort.class);
      transactions = mock(TransactionPersistencePort.class);
      CurrentUserPort currentUser = () -> Optional.of(9L);
      service = new ZoneService(zones, parkings, capacity, transactions, currentUser);

      parking = Parking.create("Parqueo San Martín", "PSM", null, "Jr. San Martín", null, null, 1L);
      parking.setId(1L);
      when(parkings.findById(1L)).thenReturn(Optional.of(parking));
      when(parkings.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(parking));
      when(parkings.findAllByIdsIncludingDeleted(any())).thenReturn(List.of(parking));
      when(zones.save(any())).thenAnswer(inv -> {
         Zone z = inv.getArgument(0);
         if (z.getId() == null) z.setId(10L);
         return z;
      });
      when(zones.findByParkingIdAndCodeIncludingDeleted(anyLong(), any())).thenReturn(Optional.empty());
      when(zones.findByParkingIdAndName(anyLong(), any())).thenReturn(Optional.empty());
      when(capacity.capacityOfZones(any())).thenReturn(Map.of());
   }

   private static Zone zone(Long id, String name, String code) {
      Zone z = Zone.create(1L, name, code, null, null, null, null, null, 1L);
      z.setId(id);
      return z;
   }

   private static CreateZoneCommand create(Long parkingId) {
      return new CreateZoneCommand(parkingId, "Zona Norte", "norte", null, null, null, null, List.of("CAM-01"));
   }

   @Test
   @DisplayName("crear dentro de un parqueo vigente, con autor y datos del parqueo")
   void create() {
      ZoneDto dto = service.createZone(create(1L));
      assertThat(dto.code()).isEqualTo("NORTE");
      assertThat(dto.parkingName()).isEqualTo("Parqueo San Martín");
      assertThat(dto.createdBy()).isEqualTo(9L);
      assertThat(dto.cameraIds()).containsExactly("CAM-01");
      assertThat(dto.operational()).isTrue();
      verify(zones).findByParkingIdAndCodeIncludingDeleted(1L, "NORTE");
   }

   @Test
   @DisplayName("parqueo inexistente o eliminado: 404")
   void parkingNotFound() {
      when(parkings.findById(2L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.createZone(create(2L))).isInstanceOf(ResourceNotFoundException.class);
   }

   @Test
   @DisplayName("código repetido en el parqueo (o en su papelera) y nombre repetido: 409")
   void duplicates() {
      when(zones.findByParkingIdAndCodeIncludingDeleted(1L, "NORTE")).thenReturn(Optional.of(zone(5L, "Otra", "NORTE")));
      assertThatThrownBy(() -> service.createZone(create(1L))).hasMessageContaining("ya tiene una zona con el código");

      Zone deleted = zone(5L, "Otra", "NORTE");
      deleted.markAsDeleted(1L);
      when(zones.findByParkingIdAndCodeIncludingDeleted(1L, "NORTE")).thenReturn(Optional.of(deleted));
      assertThatThrownBy(() -> service.createZone(create(1L))).hasMessageContaining("papelera");

      when(zones.findByParkingIdAndCodeIncludingDeleted(1L, "NORTE")).thenReturn(Optional.empty());
      when(zones.findByParkingIdAndName(1L, "Zona Norte")).thenReturn(Optional.of(zone(6L, "Zona Norte", "N2")));
      assertThatThrownBy(() -> service.createZone(create(1L)))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("llamada");
      verify(zones, never()).save(any());
   }

   @Test
   @DisplayName("editar conservando su propio nombre no choca consigo misma")
   void updateSameName() {
      Zone z = zone(10L, "Zona Norte", "NORTE");
      when(zones.findById(10L)).thenReturn(Optional.of(z));
      when(zones.findByParkingIdAndName(1L, "Zona Norte")).thenReturn(Optional.of(z));

      ZoneDto dto = service.updateZone(10L, new UpdateZoneCommand("Zona Norte", "Av. Luzuriaga", null, null, null, null, "INACTIVE"));
      assertThat(dto.address()).isEqualTo("Av. Luzuriaga");
      assertThat(dto.status()).isEqualTo(Zone.STATUS_INACTIVE);
      assertThat(dto.operational()).isFalse();
   }

   @Test
   @DisplayName("no se elimina con vehículos dentro")
   void delete() {
      Zone z = zone(10L, "Zona Norte", "NORTE");
      when(zones.findById(10L)).thenReturn(Optional.of(z));
      when(transactions.countActiveByZoneId(10L)).thenReturn(1L);
      assertThatThrownBy(() -> service.deleteZone(10L)).hasMessageContaining("1 vehículo dentro");

      when(transactions.countActiveByZoneId(10L)).thenReturn(0L);
      service.deleteZone(10L);
      assertThat(z.isDeleted()).isTrue();
      assertThat(z.getDeletedBy()).isEqualTo(9L);
   }

   @Test
   @DisplayName("restaurar exige que su parqueo no esté eliminado")
   void restore() {
      Zone z = zone(10L, "Zona Norte", "NORTE");
      z.markAsDeleted(1L);
      when(zones.findDeletedById(10L)).thenReturn(Optional.of(z));
      when(parkings.findById(1L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.restoreZone(10L)).hasMessageContaining("Restaura primero el parqueo");

      when(parkings.findById(1L)).thenReturn(Optional.of(parking));
      assertThat(service.restoreZone(10L).deletedAt()).isNull();
   }

   @Test
   @DisplayName("capacidad: los libres solo cuentan como disponibles si la zona está activa")
   void capacity() {
      Zone active = zone(10L, "Zona Norte", "NORTE");
      Zone maintenance = zone(11L, "Zona Sur", "SUR");
      maintenance.setInMaintenance();
      when(zones.findAll(any(), any(), any(), eq(1L), eq(false)))
            .thenReturn(PageResult.of(List.of(active, maintenance), 0, 20, 2));
      when(capacity.capacityOfZones(List.of(10L, 11L))).thenReturn(Map.of(
            10L, new ZoneCapacity(10, 6, 4),
            11L, new ZoneCapacity(5, 5, 0)));

      List<ZoneDto> list = service.listAllZones(0, 20, null, null, 1L, false).content();

      assertThat(list.get(0).availableSpaces()).isEqualTo(6);
      assertThat(list.get(0).occupancyPercentage()).isEqualTo(40.0);
      assertThat(list.get(1).availableSpaces()).isZero();
      assertThat(list.get(1).unavailableSpaces()).isEqualTo(5);
      verify(capacity, times(1)).capacityOfZones(any());
      verify(parkings, times(1)).findAllByIdsIncludingDeleted(any());
   }

   @Test
   @DisplayName("zonas de un parqueo inexistente: 404")
   void byParkingNotFound() {
      when(parkings.findById(3L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.listZonesByParking(3L)).isInstanceOf(ResourceNotFoundException.class);
   }
}
