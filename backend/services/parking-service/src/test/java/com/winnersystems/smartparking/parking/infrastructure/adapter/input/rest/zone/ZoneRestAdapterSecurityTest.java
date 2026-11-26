package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.zone;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.ZoneDto;
import com.winnersystems.smartparking.parking.application.port.input.zone.*;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.zone.mapper.ZoneRestMapper;
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

/** Cada endpoint de zonas exige su permiso zones.* */
@SpringJUnitConfig(ZoneRestAdapterSecurityTest.Config.class)
class ZoneRestAdapterSecurityTest {

   private static final ZoneDto DTO = new ZoneDto(1L, 1L, "P", "PSM", "Zona", "N", null, null,
         0, 0, 0, 0, 0.0, null, null, false, List.of(), 0, "ACTIVE", true, null, null, null, null, null, null);

   @Configuration
   @EnableMethodSecurity
   static class Config {
      @Bean CreateZoneUseCase create() { return mock(CreateZoneUseCase.class); }
      @Bean UpdateZoneUseCase update() { return mock(UpdateZoneUseCase.class); }
      @Bean DeleteZoneUseCase delete() { return mock(DeleteZoneUseCase.class); }
      @Bean RestoreZoneUseCase restore() {
         RestoreZoneUseCase r = mock(RestoreZoneUseCase.class);
         when(r.restoreZone(any())).thenReturn(DTO);
         return r;
      }
      @Bean GetZoneUseCase get() {
         GetZoneUseCase g = mock(GetZoneUseCase.class);
         when(g.getZoneById(any())).thenReturn(DTO);
         return g;
      }
      @Bean ToggleZoneStatusUseCase toggle() {
         ToggleZoneStatusUseCase t = mock(ToggleZoneStatusUseCase.class);
         when(t.toggleZoneStatus(any())).thenReturn(DTO);
         return t;
      }
      @Bean ListZonesUseCase list() {
         ListZonesUseCase l = mock(ListZonesUseCase.class);
         when(l.listAllZones(anyInt(), anyInt(), any(), any(), any(), anyBoolean())).thenReturn(PagedResponse.of(List.of()));
         return l;
      }
      @Bean ZoneRestMapper mapper() { return new ZoneRestMapper(); }
      @Bean ZoneRestAdapter adapter(CreateZoneUseCase c, UpdateZoneUseCase u, DeleteZoneUseCase d,
                                    RestoreZoneUseCase r, GetZoneUseCase g, ListZonesUseCase l,
                                    ToggleZoneStatusUseCase t, ZoneRestMapper m) {
         return new ZoneRestAdapter(c, u, d, r, g, l, t, m);
      }
   }

   @Autowired
   ZoneRestAdapter adapter;

   private static void login(String... authorities) {
      SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", null, authorities));
   }

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   @Test
   @DisplayName("leer exige zones.read; la papelera además zones.restore")
   void read() {
      login("zones.read");
      adapter.listAllZones(0, 20, null, null, null, false);
      adapter.getZoneById(1L);
      adapter.listByParking(1L);
      assertThatThrownBy(() -> adapter.listAllZones(0, 20, null, null, null, true)).isInstanceOf(AccessDeniedException.class);

      login("zones.read", "zones.restore");
      adapter.listAllZones(0, 20, null, null, null, true);
      adapter.restoreZone(1L);
   }

   @Test
   @DisplayName("las zonas de un parqueo también se ven con parkings.read (detalle del parqueo)")
   void byParkingWithParkingRead() {
      login("parkings.read");
      adapter.listByParking(1L);
      assertThatThrownBy(() -> adapter.getZoneById(1L)).isInstanceOf(AccessDeniedException.class);
   }

   @Test
   @DisplayName("escribir exige el permiso de cada acción")
   void write() {
      login("zones.read");
      assertThatThrownBy(() -> adapter.toggleStatus(1L)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.deleteZone(1L)).isInstanceOf(AccessDeniedException.class);
      login("zones.update");
      adapter.toggleStatus(1L);
      login("zones.delete");
      adapter.deleteZone(1L);
   }

   @Test
   @DisplayName("las zonas activas (selectores) solo exigen sesión")
   void active() {
      login("transactions.create");
      adapter.listActiveZones(1L);
   }
}
