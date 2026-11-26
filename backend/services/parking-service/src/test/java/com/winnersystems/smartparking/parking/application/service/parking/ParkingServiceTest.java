package com.winnersystems.smartparking.parking.application.service.parking;

import com.winnersystems.smartparking.parking.application.dto.command.CreateParkingCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateParkingCommand;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingCapacity;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingDto;
import com.winnersystems.smartparking.parking.application.port.output.*;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
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

class ParkingServiceTest {

   private ParkingPersistencePort parkings;
   private ParkingCapacityPort capacity;
   private TransactionPersistencePort transactions;
   private UserDirectoryPort users;
   private ParkingService service;

   @BeforeEach
   void setUp() {
      parkings = mock(ParkingPersistencePort.class);
      capacity = mock(ParkingCapacityPort.class);
      transactions = mock(TransactionPersistencePort.class);
      users = mock(UserDirectoryPort.class);
      CurrentUserPort currentUser = () -> Optional.of(9L);
      service = new ParkingService(parkings, capacity, transactions, users, currentUser);

      when(parkings.save(any())).thenAnswer(inv -> {
         Parking p = inv.getArgument(0);
         if (p.getId() == null) p.setId(1L);
         return p;
      });
      when(parkings.findByCodeIncludingDeleted(any())).thenReturn(Optional.empty());
      when(capacity.capacityOf(any())).thenReturn(Map.of());
   }

   private static Parking existing(Long id) {
      Parking p = Parking.create("Parqueo San Martín", "PSM", null, "Jr. San Martín", null, null, 1L);
      p.setId(id);
      return p;
   }

   private static CreateParkingCommand create(Long managerId) {
      return new CreateParkingCommand("Parqueo San Martín", "psm", null, "Jr. San Martín", null, null, managerId);
   }

   @Test
   @DisplayName("crear guarda el código en mayúsculas, el autor y el nombre del responsable de auth-service")
   void createWithManager() {
      when(users.findUser(5L)).thenReturn(Optional.of(new UserDirectoryPort.UserSummary(5L, "Ana Ríos", "a@x.pe", true)));

      ParkingDto dto = service.createParking(create(5L));

      assertThat(dto.code()).isEqualTo("PSM");
      assertThat(dto.managerName()).isEqualTo("Ana Ríos");
      assertThat(dto.createdBy()).isEqualTo(9L);
      assertThat(dto.totalSpaces()).isZero();
      verify(parkings).findByCodeIncludingDeleted("PSM");
   }

   @Test
   @DisplayName("código repetido: 409, y si está en la papelera sugiere restaurar")
   void duplicateCode() {
      when(parkings.findByCodeIncludingDeleted("PSM")).thenReturn(Optional.of(existing(2L)));
      assertThatThrownBy(() -> service.createParking(create(null)))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("Ya existe");

      Parking deleted = existing(2L);
      deleted.markAsDeleted(1L);
      when(parkings.findByCodeIncludingDeleted("PSM")).thenReturn(Optional.of(deleted));
      assertThatThrownBy(() -> service.createParking(create(null)))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("papelera");
      verify(parkings, never()).save(any());
   }

   @Test
   @DisplayName("responsable inexistente (404) o desactivado (409)")
   void invalidManager() {
      when(users.findUser(5L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.createParking(create(5L))).isInstanceOf(ResourceNotFoundException.class);

      when(users.findUser(5L)).thenReturn(Optional.of(new UserDirectoryPort.UserSummary(5L, "Ana", "a@x.pe", false)));
      assertThatThrownBy(() -> service.createParking(create(5L))).isInstanceOf(ResourceConflictException.class);
   }

   @Test
   @DisplayName("editar sin cambiar el responsable no consulta auth-service; null lo quita")
   void updateManager() {
      Parking p = existing(1L);
      p.assignManager(5L, "Ana Ríos");
      when(parkings.findById(1L)).thenReturn(Optional.of(p));

      service.updateParking(1L, new UpdateParkingCommand("Nuevo", null, "Dir", null, null, 5L, "MAINTENANCE"));
      verifyNoInteractions(users);
      assertThat(p.getName()).isEqualTo("Nuevo");
      assertThat(p.getStatus()).isEqualTo(Parking.STATUS_MAINTENANCE);
      assertThat(p.getUpdatedBy()).isEqualTo(9L);

      service.updateParking(1L, new UpdateParkingCommand("Nuevo", null, "Dir", null, null, null, null));
      assertThat(p.hasManager()).isFalse();
      assertThat(p.getStatus()).isEqualTo(Parking.STATUS_MAINTENANCE);
   }

   @Test
   @DisplayName("no se elimina con vehículos dentro; sin ellos, eliminación lógica con autor")
   void delete() {
      Parking p = existing(1L);
      when(parkings.findById(1L)).thenReturn(Optional.of(p));
      when(transactions.countActiveByParkingId(1L)).thenReturn(3L);
      assertThatThrownBy(() -> service.deleteParking(1L))
            .isInstanceOf(ResourceConflictException.class).hasMessageContaining("3 vehículos");

      when(transactions.countActiveByParkingId(1L)).thenReturn(0L);
      service.deleteParking(1L);
      assertThat(p.isDeleted()).isTrue();
      assertThat(p.getDeletedBy()).isEqualTo(9L);
   }

   @Test
   @DisplayName("restaurar solo desde la papelera")
   void restore() {
      when(parkings.findDeletedById(1L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.restoreParking(1L)).isInstanceOf(ResourceNotFoundException.class);

      Parking p = existing(1L);
      p.markAsDeleted(1L);
      when(parkings.findDeletedById(1L)).thenReturn(Optional.of(p));
      ParkingDto dto = service.restoreParking(1L);
      assertThat(dto.deletedAt()).isNull();
      assertThat(dto.operational()).isTrue();
   }

   @Test
   @DisplayName("la lista calcula la capacidad de toda la página en una sola llamada")
   void listUsesCapacity() {
      Parking a = existing(1L);
      Parking b = existing(2L);
      when(parkings.findAll(any(), eq("san"), eq("ACTIVE"), eq(false)))
            .thenReturn(PageResult.of(List.of(a, b), 0, 20, 2));
      when(capacity.capacityOf(List.of(1L, 2L))).thenReturn(Map.of(1L, new ParkingCapacity(2, 40, 10, 25)));

      PagedResponse<ParkingDto> page = service.listAllParkings(0, 20, "san", "active", false);

      ParkingDto first = page.content().get(0);
      assertThat(first.totalZones()).isEqualTo(2);
      assertThat(first.availableSpaces()).isEqualTo(10);
      assertThat(first.occupiedSpaces()).isEqualTo(25);
      assertThat(first.unavailableSpaces()).isEqualTo(5);
      assertThat(first.occupancyPercentage()).isEqualTo(62.5);
      assertThat(page.content().get(1).totalSpaces()).isZero();
      verify(capacity, times(1)).capacityOf(any());
   }

   @Test
   @DisplayName("filtro de estado desconocido: 400")
   void invalidStatusFilter() {
      assertThatThrownBy(() -> service.listAllParkings(0, 20, null, "ABIERTO", false))
            .isInstanceOf(ParkingDomainException.class);
   }

   @Test
   @DisplayName("parqueo inexistente: 404")
   void notFound() {
      when(parkings.findById(8L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.getParkingById(8L)).isInstanceOf(ResourceNotFoundException.class);
      assertThatThrownBy(() -> service.toggleParkingStatus(8L)).isInstanceOf(ResourceNotFoundException.class);
   }

   @Test
   @DisplayName("alternar estado: activo pasa a inactivo y cualquier otro a activo")
   void toggle() {
      Parking p = existing(1L);
      when(parkings.findById(1L)).thenReturn(Optional.of(p));
      assertThat(service.toggleParkingStatus(1L).status()).isEqualTo(Parking.STATUS_INACTIVE);
      service.setInMaintenance(1L);
      assertThat(service.toggleParkingStatus(1L).status()).isEqualTo(Parking.STATUS_ACTIVE);
   }
}
