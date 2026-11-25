package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate;

import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.port.input.rate.*;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.rate.mapper.RateRestMapper;
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

/**
 * Los @PreAuthorize de tarifas exigen el permiso correcto, incluido rates.restore para la papelera.
 */
@SpringJUnitConfig(RateRestAdapterSecurityTest.Config.class)
class RateRestAdapterSecurityTest {

   @Configuration
   @EnableMethodSecurity
   static class Config {
      @Bean CreateRateUseCase create() { return mock(CreateRateUseCase.class); }
      @Bean UpdateRateUseCase update() { return mock(UpdateRateUseCase.class); }
      @Bean DeleteRateUseCase delete() { return mock(DeleteRateUseCase.class); }
      @Bean GetRateUseCase get() { return mock(GetRateUseCase.class); }
      @Bean ToggleRateStatusUseCase toggle() { return mock(ToggleRateStatusUseCase.class); }
      @Bean RestoreRateUseCase restore() { return mock(RestoreRateUseCase.class); }
      @Bean SimulateRateUseCase simulate() { return mock(SimulateRateUseCase.class); }
      @Bean ListRatesUseCase list() {
         ListRatesUseCase list = mock(ListRatesUseCase.class);
         when(list.listAllRates(anyInt(), anyInt(), any(), any(), anyBoolean())).thenReturn(PagedResponse.of(List.of()));
         return list;
      }
      @Bean RateRestMapper mapper() { return new RateRestMapper(); }
      @Bean RateRestAdapter adapter(CreateRateUseCase c, UpdateRateUseCase u, DeleteRateUseCase d, GetRateUseCase g,
                                    ListRatesUseCase l, ToggleRateStatusUseCase t, RestoreRateUseCase r,
                                    SimulateRateUseCase s, RateRestMapper m) {
         return new RateRestAdapter(c, u, d, g, l, t, r, s, m);
      }
   }

   @Autowired
   RateRestAdapter adapter;

   private static void login(String... authorities) {
      SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", null, authorities));
   }

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   @Test
   @DisplayName("rates.read lista; sin rates.delete no elimina; sin rates.restore no ve la papelera")
   void permissions() {
      login("rates.read", "rates.delete");
      adapter.listAllRates(0, 10, null, null, false);
      adapter.deleteRate(1L);
      assertThatThrownBy(() -> adapter.listAllRates(0, 10, null, null, true)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.restoreRate(1L)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.toggleStatus(1L)).isInstanceOf(AccessDeniedException.class);

      login("rates.read", "rates.restore");
      adapter.listAllRates(0, 10, null, null, true);
      adapter.restoreRate(1L);
   }

   @Test
   @DisplayName("las tarifas activas solo exigen sesión")
   void activeNeedsOnlySession() {
      login("ROLE_OPERADOR");
      adapter.listActiveRates();
      assertThatThrownBy(() -> adapter.getRateById(1L)).isInstanceOf(AccessDeniedException.class);
   }
}
