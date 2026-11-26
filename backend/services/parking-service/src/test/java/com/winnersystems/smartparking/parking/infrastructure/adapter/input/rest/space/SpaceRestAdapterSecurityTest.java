package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.SpaceDto;
import com.winnersystems.smartparking.parking.application.port.input.space.*;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space.mapper.SpaceRestMapper;
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

/** Cada endpoint de espacios exige su permiso spaces.* */
@SpringJUnitConfig(SpaceRestAdapterSecurityTest.Config.class)
class SpaceRestAdapterSecurityTest {

   static final SpaceDto DTO = new SpaceDto(1L, 1L, "Zona", "N", 1L, "P", "PARALLEL", "Paralelo", "A-001",
         null, null, null, null, false, null, false, "AVAILABLE", true, null, null, null, null, null, null);

   @Configuration
   @EnableMethodSecurity
   static class Config {
      @Bean CreateSpaceUseCase create() {
         CreateSpaceUseCase c = mock(CreateSpaceUseCase.class);
         when(c.createSpaces(any())).thenReturn(List.of(DTO));
         return c;
      }
      @Bean UpdateSpaceUseCase update() { return mock(UpdateSpaceUseCase.class); }
      @Bean DeleteSpaceUseCase delete() { return mock(DeleteSpaceUseCase.class); }
      @Bean RestoreSpaceUseCase restore() {
         RestoreSpaceUseCase r = mock(RestoreSpaceUseCase.class);
         when(r.restoreSpace(any())).thenReturn(DTO);
         return r;
      }
      @Bean GetSpaceUseCase get() {
         GetSpaceUseCase g = mock(GetSpaceUseCase.class);
         when(g.getSpaceById(any())).thenReturn(DTO);
         return g;
      }
      @Bean ToggleSpaceStatusUseCase toggle() {
         ToggleSpaceStatusUseCase t = mock(ToggleSpaceStatusUseCase.class);
         when(t.markAsOccupied(any())).thenReturn(DTO);
         return t;
      }
      @Bean ListSpacesUseCase list() {
         ListSpacesUseCase l = mock(ListSpacesUseCase.class);
         when(l.listAllSpaces(anyInt(), anyInt(), any(), any(), any(), any(), any(), anyBoolean()))
               .thenReturn(PagedResponse.of(List.of()));
         return l;
      }
      @Bean SpaceRestMapper mapper() { return new SpaceRestMapper(); }
      @Bean SpaceRestAdapter adapter(CreateSpaceUseCase c, UpdateSpaceUseCase u, DeleteSpaceUseCase d,
                                     RestoreSpaceUseCase r, GetSpaceUseCase g, ListSpacesUseCase l,
                                     ToggleSpaceStatusUseCase t, SpaceRestMapper m) {
         return new SpaceRestAdapter(c, u, d, r, g, l, t, m);
      }
   }

   @Autowired
   SpaceRestAdapter adapter;

   private static void login(String... authorities) {
      SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", null, authorities));
   }

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   @Test
   @DisplayName("leer exige spaces.read; la papelera además spaces.restore; por zona también con zones.read")
   void read() {
      login("spaces.read");
      adapter.listAllSpaces(0, 20, null, null, null, null, null, false);
      adapter.getSpaceById(1L);
      assertThatThrownBy(() -> adapter.listAllSpaces(0, 20, null, null, null, null, null, true))
            .isInstanceOf(AccessDeniedException.class);
      login("zones.read");
      adapter.listByZone(1L);
      assertThatThrownBy(() -> adapter.getSpaceById(1L)).isInstanceOf(AccessDeniedException.class);
      login("spaces.restore", "spaces.read");
      adapter.restoreSpace(1L);
   }

   @Test
   @DisplayName("el lote exige spaces.bulk-create; ocupar exige spaces.update")
   void write() {
      login("spaces.create");
      assertThatThrownBy(() -> adapter.createBatch(List.of())).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.occupy(1L)).isInstanceOf(AccessDeniedException.class);
      login("spaces.bulk-create");
      adapter.createBatch(List.of());
      login("spaces.update");
      adapter.occupy(1L);
   }

   @Test
   @DisplayName("los disponibles de una zona (entrada de vehículos) solo exigen sesión")
   void available() {
      login("transactions.create");
      adapter.listAvailableByZone(1L);
   }
}
