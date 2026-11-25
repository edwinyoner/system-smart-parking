package com.winnersystems.smartparking.parking.application.service.shift;

import com.winnersystems.smartparking.parking.application.dto.command.CreateShiftCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateShiftCommand;
import com.winnersystems.smartparking.parking.application.dto.query.ShiftDto;
import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingShiftRatePersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ShiftPersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Shift;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class ShiftServiceTest {

   private static final Long USER = 20L;

   private ShiftPersistencePort shifts;
   private ParkingShiftRatePersistencePort rates;
   private ShiftService service;

   @BeforeEach
   void setUp() {
      shifts = mock(ShiftPersistencePort.class);
      rates = mock(ParkingShiftRatePersistencePort.class);
      CurrentUserPort currentUser = mock(CurrentUserPort.class);
      when(currentUser.requireCurrentUserId()).thenReturn(USER);
      when(rates.findParkingNamesUsingShift(anyLong())).thenReturn(List.of());
      when(shifts.save(any())).thenAnswer(inv -> {
         Shift s = inv.getArgument(0);
         if (s.getId() == null) s.setId(1L);
         return s;
      });
      service = new ShiftService(shifts, rates, currentUser);
   }

   private static Shift existing(Long id) {
      Shift s = Shift.create("Mañana", "MORNING", LocalTime.of(6, 0), LocalTime.of(13, 59), null, true, 1L);
      s.setId(id);
      return s;
   }

   @Test
   @DisplayName("crear: activo por defecto, código en mayúsculas y auditoría del usuario")
   void create() {
      ShiftDto dto = service.createShift(new CreateShiftCommand(
            "Noche", "night", null, LocalTime.of(20, 0), LocalTime.of(5, 59), null));

      ArgumentCaptor<Shift> saved = ArgumentCaptor.forClass(Shift.class);
      verify(shifts).save(saved.capture());
      assertThat(saved.getValue().getCode()).isEqualTo("NIGHT");
      assertThat(saved.getValue().getCreatedBy()).isEqualTo(USER);
      assertThat(dto.status()).isTrue();
      assertThat(dto.crossesMidnight()).isTrue();
   }

   @Test
   @DisplayName("crear: código usado, también por un turno eliminado, responde conflicto")
   void duplicateCode() {
      Shift deleted = existing(3L);
      deleted.markAsDeleted(1L);
      when(shifts.findByCodeIncludingDeleted("MORNING")).thenReturn(Optional.of(deleted));

      assertThatThrownBy(() -> service.createShift(new CreateShiftCommand(
            "Mañana", "morning", null, LocalTime.of(6, 0), LocalTime.of(13, 59), true)))
            .isInstanceOf(ResourceConflictException.class)
            .hasMessageContaining("eliminado");
      verify(shifts, never()).save(any());
   }

   @Test
   @DisplayName("eliminar: turno en uso responde conflicto con los parqueos")
   void deleteInUse() {
      when(shifts.findById(1L)).thenReturn(Optional.of(existing(1L)));
      when(rates.findParkingNamesUsingShift(1L)).thenReturn(List.of("Las Banderas", "San Martín"));

      assertThatThrownBy(() -> service.deleteShift(1L))
            .isInstanceOf(ResourceConflictException.class)
            .hasMessageContaining("los parqueos Las Banderas y San Martín");
      verify(shifts, never()).save(any());
   }

   @Test
   @DisplayName("eliminar: sin uso, soft delete con deletedBy")
   void deleteUnused() {
      Shift s = existing(1L);
      when(shifts.findById(1L)).thenReturn(Optional.of(s));

      service.deleteShift(1L);

      assertThat(s.isDeleted()).isTrue();
      assertThat(s.getDeletedBy()).isEqualTo(USER);
      verify(shifts).save(s);
   }

   @Test
   @DisplayName("desactivar un turno en uso está permitido")
   void deactivateInUse() {
      when(shifts.findById(1L)).thenReturn(Optional.of(existing(1L)));
      when(rates.findParkingNamesUsingShift(1L)).thenReturn(List.of("San Martín"));

      ShiftDto dto = service.deactivateShift(1L);

      assertThat(dto.status()).isFalse();
      assertThat(dto.updatedBy()).isEqualTo(USER);
      assertThat(dto.usedByParkings()).containsExactly("San Martín");
   }

   @Test
   @DisplayName("actualizar: horario y auditoría; turno inexistente responde 404")
   void update() {
      when(shifts.findById(1L)).thenReturn(Optional.of(existing(1L)));
      ShiftDto dto = service.updateShift(1L, new UpdateShiftCommand(
            "Mañana larga", null, LocalTime.of(5, 0), LocalTime.of(13, 59), null));
      assertThat(dto.startTime()).isEqualTo(LocalTime.of(5, 0));
      assertThat(dto.updatedBy()).isEqualTo(USER);

      when(shifts.findById(9L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.getShiftById(9L)).isInstanceOf(ResourceNotFoundException.class);
   }
}
