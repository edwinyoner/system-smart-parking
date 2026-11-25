package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.shift;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.ShiftDto;
import com.winnersystems.smartparking.parking.application.port.input.shift.*;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.shift.mapper.ShiftRestMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Los @PreAuthorize de turnos exigen el permiso correcto del rol activo.
 */
@SpringJUnitConfig(ShiftRestAdapterSecurityTest.Config.class)
class ShiftRestAdapterSecurityTest {

   @Configuration
   @EnableMethodSecurity
   static class Config {
      @Bean ToggleShiftStatusUseCase toggle() { return mock(ToggleShiftStatusUseCase.class); }
      @Bean CreateShiftUseCase create() { return mock(CreateShiftUseCase.class); }
      @Bean UpdateShiftUseCase update() { return mock(UpdateShiftUseCase.class); }
      @Bean DeleteShiftUseCase delete() { return mock(DeleteShiftUseCase.class); }
      @Bean ListShiftsUseCase list() {
         ListShiftsUseCase list = mock(ListShiftsUseCase.class);
         when(list.listAllShifts(anyInt(), anyInt(), any(), any(), anyBoolean())).thenReturn(PagedResponse.of(List.of()));
         return list;
      }
      @Bean RestoreShiftUseCase restore() { return mock(RestoreShiftUseCase.class); }
      @Bean GetShiftUseCase get() {
         GetShiftUseCase get = mock(GetShiftUseCase.class);
         when(get.getShiftById(anyLong())).thenReturn(new ShiftDto(1L, "Mañana", "MORNING", null,
               LocalTime.of(6, 0), LocalTime.of(13, 59), true, false, 8.0, List.of(), null, null, null, null, null, null));
         return get;
      }
      @Bean ShiftRestMapper mapper() { return new ShiftRestMapper(); }
      @Bean ShiftRestAdapter adapter(CreateShiftUseCase c, UpdateShiftUseCase u, DeleteShiftUseCase d,
                                     GetShiftUseCase g, ListShiftsUseCase l, ToggleShiftStatusUseCase t,
                                     RestoreShiftUseCase r, ShiftRestMapper m) {
         return new ShiftRestAdapter(c, u, d, g, l, t, r, m);
      }
   }

   @Autowired
   ShiftRestAdapter adapter;

   private static void login(String... authorities) {
      SecurityContextHolder.getContext().setAuthentication(
            new TestingAuthenticationToken("user", null, authorities));
   }

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   @Test
   @DisplayName("con shifts.read puede ver; sin shifts.delete no puede eliminar")
   void readButNotDelete() {
      login("shifts.read", "ROLE_OPERADOR");
      assertThat(adapter.getShiftById(1L).getBody().code()).isEqualTo("MORNING");
      assertThatThrownBy(() -> adapter.deleteShift(1L)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.toggleStatus(1L)).isInstanceOf(AccessDeniedException.class);
   }

   @Test
   @DisplayName("los turnos activos solo exigen sesión (los usan los formularios de parqueos)")
   void activeNeedsOnlySession() {
      login("ROLE_OPERADOR");
      assertThat(adapter.listActiveShifts().getStatusCode().is2xxSuccessful()).isTrue();
      assertThatThrownBy(() -> adapter.getShiftById(1L)).isInstanceOf(AccessDeniedException.class);
   }

   @Test
   @DisplayName("papelera y restaurar exigen shifts.restore (no basta shifts.delete)")
   void trashNeedsDelete() {
      login("shifts.read", "shifts.delete");
      assertThat(adapter.listAllShifts(0, 10, null, null, false).getStatusCode().is2xxSuccessful()).isTrue();
      assertThatThrownBy(() -> adapter.listAllShifts(0, 10, null, null, true)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.restoreShift(1L)).isInstanceOf(AccessDeniedException.class);

      login("shifts.read", "shifts.restore");
      adapter.listAllShifts(0, 10, null, null, true);
      adapter.restoreShift(1L);
   }

   @Test
   @DisplayName("sin autenticación se rechaza")
   void anonymous() {
      assertThatThrownBy(() -> adapter.listActiveShifts())
            .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
   }
}
