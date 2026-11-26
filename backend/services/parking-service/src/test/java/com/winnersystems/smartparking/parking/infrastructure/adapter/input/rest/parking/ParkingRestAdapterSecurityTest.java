package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.ParkingDto;
import com.winnersystems.smartparking.parking.application.port.input.parking.*;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parking.mapper.ParkingRestMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Cada endpoint de parqueos exige su permiso parkings.* */
@SpringJUnitConfig(ParkingRestAdapterSecurityTest.Config.class)
class ParkingRestAdapterSecurityTest {

   private static final ParkingDto DTO = new ParkingDto(1L, "P", "PSM", null, "Dir", null, null, null, null,
         0, 0, 0, 0, 0, 0.0, "ACTIVE", true, null, null, null, null, null, null);

   @Configuration
   @EnableMethodSecurity
   static class Config {
      @Bean CreateParkingUseCase create() { return mock(CreateParkingUseCase.class); }
      @Bean UpdateParkingUseCase update() { return mock(UpdateParkingUseCase.class); }
      @Bean DeleteParkingUseCase delete() { return mock(DeleteParkingUseCase.class); }
      @Bean RestoreParkingUseCase restore() {
         RestoreParkingUseCase r = mock(RestoreParkingUseCase.class);
         when(r.restoreParking(any())).thenReturn(DTO);
         return r;
      }
      @Bean GetParkingUseCase get() {
         GetParkingUseCase g = mock(GetParkingUseCase.class);
         when(g.getParkingById(any())).thenReturn(DTO);
         return g;
      }
      @Bean ToggleParkingStatusUseCase toggle() {
         ToggleParkingStatusUseCase t = mock(ToggleParkingStatusUseCase.class);
         when(t.toggleParkingStatus(any())).thenReturn(DTO);
         return t;
      }
      @Bean ListParkingsUseCase list() {
         ListParkingsUseCase l = mock(ListParkingsUseCase.class);
         when(l.listAllParkings(anyInt(), anyInt(), any(), any(), anyBoolean())).thenReturn(PagedResponse.of(List.of()));
         return l;
      }
      @Bean ParkingRestMapper mapper() { return new ParkingRestMapper(); }
      @Bean ParkingRestAdapter adapter(CreateParkingUseCase c, UpdateParkingUseCase u, DeleteParkingUseCase d,
                                       RestoreParkingUseCase r, GetParkingUseCase g, ListParkingsUseCase l,
                                       ToggleParkingStatusUseCase t, ParkingRestMapper m) {
         return new ParkingRestAdapter(c, u, d, r, g, l, t, m);
      }
   }

   @Autowired
   ParkingRestAdapter adapter;

   private static void login(String... authorities) {
      SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", null, authorities));
   }

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   @Test
   @DisplayName("leer exige parkings.read; la papelera exige además parkings.restore")
   void read() {
      login("parkings.read");
      adapter.listAllParkings(0, 20, null, null, false);
      adapter.getParkingById(1L);
      assertThatThrownBy(() -> adapter.listAllParkings(0, 20, null, null, true)).isInstanceOf(AccessDeniedException.class);

      login("parkings.read", "parkings.restore");
      adapter.listAllParkings(0, 20, null, null, true);
      adapter.restoreParking(1L);
   }

   @Test
   @DisplayName("sin el permiso de cada acción: 403")
   void writeNeedsPermission() {
      login("parkings.read");
      assertThatThrownBy(() -> adapter.deleteParking(1L)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.toggleStatus(1L)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.restoreParking(1L)).isInstanceOf(AccessDeniedException.class);

      login("parkings.update");
      adapter.toggleStatus(1L);
      assertThatThrownBy(() -> adapter.getParkingById(1L)).isInstanceOf(AccessDeniedException.class);

      login("parkings.delete");
      adapter.deleteParking(1L);
   }

   @Test
   @DisplayName("los activos (selectores) solo exigen sesión")
   void activeNeedsSession() {
      assertThatThrownBy(() -> adapter.listActiveParkings()).isInstanceOf(Exception.class);
      login("transactions.create");
      adapter.listActiveParkings();
   }
}
