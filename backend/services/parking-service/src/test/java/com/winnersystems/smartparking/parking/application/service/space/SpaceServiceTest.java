package com.winnersystems.smartparking.parking.application.service.space;

import com.winnersystems.smartparking.parking.application.dto.command.CreateSpaceCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateSpaceCommand;
import com.winnersystems.smartparking.parking.application.dto.query.SpaceDto;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.Space;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SpaceServiceTest {

   private SpacePersistencePort spaces;
   private ZonePersistencePort zones;
   private ParkingPersistencePort parkings;
   private TransactionPersistencePort transactions;
   private SpaceService service;
   private Zone zone;
   private Parking parking;

   @BeforeEach
   void setUp() {
      spaces = mock(SpacePersistencePort.class);
      zones = mock(ZonePersistencePort.class);
      parkings = mock(ParkingPersistencePort.class);
      transactions = mock(TransactionPersistencePort.class);
      CurrentUserPort currentUser = () -> Optional.of(9L);
      service = new SpaceService(spaces, zones, parkings, transactions, currentUser);

      parking = Parking.create("Parqueo San Martín", "PSM", null, "Jr. San Martín", null, null, 1L);
      parking.setId(1L);
      zone = Zone.create(1L, "Zona Norte", "NORTE", null, null, null, null, null, 1L);
      zone.setId(10L);
      when(zones.findById(10L)).thenReturn(Optional.of(zone));
      when(zones.findAllByIdsIncludingDeleted(any())).thenReturn(List.of(zone));
      when(parkings.findById(1L)).thenReturn(Optional.of(parking));
      when(parkings.findAllByIdsIncludingDeleted(any())).thenReturn(List.of(parking));
      when(spaces.findExistingCodes(anyLong(), any())).thenReturn(Set.of());
      when(spaces.findBySensorIds(any())).thenReturn(List.of());
      AtomicLong ids = new AtomicLong(100);
      when(spaces.saveAll(any())).thenAnswer(inv -> {
         List<Space> list = inv.getArgument(0);
         list.forEach(s -> s.setId(ids.incrementAndGet()));
         return list;
      });
      when(spaces.save(any())).thenAnswer(inv -> inv.getArgument(0));
   }

   private static CreateSpaceCommand cmd(String code, String sensor) {
      return new CreateSpaceCommand(10L, code, "PERPENDICULAR", null, null, null, sensor, false);
   }

   private Space existing(Long id) {
      Space s = Space.create(10L, "A-001", "PARALLEL", null, null, null, null, false, 1L);
      s.setId(id);
      return s;
   }

   @Test
   @DisplayName("lote: crea todos juntos con datos de zona y parqueo")
   void batch() {
      List<SpaceDto> created = service.createSpaces(List.of(cmd("a-001", null), cmd("a-002", "S-1")));

      assertThat(created).extracting(SpaceDto::code).containsExactly("A-001", "A-002");
      assertThat(created.get(0).zoneName()).isEqualTo("Zona Norte");
      assertThat(created.get(0).parkingName()).isEqualTo("Parqueo San Martín");
      assertThat(created.get(0).receivesVehicles()).isTrue();
      assertThat(created.get(1).hasSensor()).isTrue();
      verify(spaces, times(1)).saveAll(any());
      verify(zones, times(1)).findById(10L);
   }

   @Test
   @DisplayName("lote: repetidos en la petición, existentes en la zona o sensor en uso: no se crea ninguno")
   void batchConflicts() {
      assertThatThrownBy(() -> service.createSpaces(List.of(cmd("A-001", null), cmd("a-001", null))))
            .isInstanceOf(ParkingDomainException.class).hasMessageContaining("repetidos");

      when(spaces.findExistingCodes(eq(10L), any())).thenReturn(Set.of("A-002"));
      assertThatThrownBy(() -> service.createSpaces(List.of(cmd("A-001", null), cmd("A-002", null))))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("A-002");

      when(spaces.findExistingCodes(eq(10L), any())).thenReturn(Set.of());
      Space other = existing(5L);
      other.update("PARALLEL", null, null, null, "S-1", false);
      when(spaces.findBySensorIds(any())).thenReturn(List.of(other));
      assertThatThrownBy(() -> service.createSpaces(List.of(cmd("A-009", "S-1"))))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("S-1");

      verify(spaces, never()).saveAll(any());
   }

   @Test
   @DisplayName("lote: vacío, demasiado grande o zona inexistente")
   void batchLimits() {
      assertThatThrownBy(() -> service.createSpaces(List.of())).isInstanceOf(ParkingDomainException.class);
      List<CreateSpaceCommand> many = IntStream.rangeClosed(1, SpaceService.MAX_BATCH + 1)
            .mapToObj(i -> cmd("A-" + i, null)).toList();
      assertThatThrownBy(() -> service.createSpaces(many)).hasMessageContaining(String.valueOf(SpaceService.MAX_BATCH));
      when(zones.findById(99L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.createSpace(new CreateSpaceCommand(99L, "A1", "PARALLEL", null, null, null, null, false)))
            .isInstanceOf(ResourceNotFoundException.class);
   }

   @Test
   @DisplayName("editar: su propio sensor no choca consigo mismo; el estado igual al actual no se toca")
   void update() {
      Space s = existing(5L);
      s.update("PARALLEL", null, null, null, "S-1", false);
      when(spaces.findById(5L)).thenReturn(Optional.of(s));
      when(spaces.findBySensorIds(any())).thenReturn(List.of(s));

      SpaceDto dto = service.updateSpace(5L, new UpdateSpaceCommand("DIAGONAL", "Junto a la rampa", 2.5, 5.0, "S-1", true, "AVAILABLE"));
      assertThat(dto.type()).isEqualTo("DIAGONAL");
      assertThat(dto.updatedBy()).isEqualTo(9L);
   }

   @Test
   @DisplayName("eliminar o liberar a mano con un vehículo registrado dentro: 409")
   void vehicleInside() {
      Space s = existing(5L);
      when(spaces.findById(5L)).thenReturn(Optional.of(s));
      when(transactions.existsActiveBySpaceId(5L)).thenReturn(true);
      assertThatThrownBy(() -> service.deleteSpace(5L)).isInstanceOf(ResourceConflictException.class);
      assertThatThrownBy(() -> service.markAsAvailable(5L)).isInstanceOf(ResourceConflictException.class);

      when(transactions.existsActiveBySpaceId(5L)).thenReturn(false);
      s.occupy();
      assertThat(service.markAsAvailable(5L).status()).isEqualTo(Space.STATUS_AVAILABLE);
      service.deleteSpace(5L);
      assertThat(s.isDeleted()).isTrue();
   }

   @Test
   @DisplayName("restaurar exige su zona vigente")
   void restore() {
      Space s = existing(5L);
      s.markAsDeleted(1L);
      when(spaces.findDeletedById(5L)).thenReturn(Optional.of(s));
      when(zones.findById(10L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.restoreSpace(5L)).hasMessageContaining("Restaura primero la zona");

      when(zones.findById(10L)).thenReturn(Optional.of(zone));
      assertThat(service.restoreSpace(5L).status()).isEqualTo(Space.STATUS_AVAILABLE);
   }

   @Test
   @DisplayName("disponibles: vacío si la zona o el parqueo no están activos")
   void available() {
      when(spaces.findAvailableByZoneId(10L)).thenReturn(List.of(existing(5L)));
      assertThat(service.listAvailableByZone(10L)).hasSize(1);

      zone.setInMaintenance();
      assertThat(service.listAvailableByZone(10L)).isEmpty();
      zone.activate();
      parking.deactivate();
      assertThat(service.listAvailableByZone(10L)).isEmpty();
   }

   @Test
   @DisplayName("filtros de estado o tipo desconocidos: 400")
   void filters() {
      assertThatThrownBy(() -> service.listAllSpaces(0, 20, null, "LIBRE", null, null, null, false))
            .isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> service.listAllSpaces(0, 20, null, null, "CIRCULAR", null, null, false))
            .isInstanceOf(ParkingDomainException.class);
   }
}
