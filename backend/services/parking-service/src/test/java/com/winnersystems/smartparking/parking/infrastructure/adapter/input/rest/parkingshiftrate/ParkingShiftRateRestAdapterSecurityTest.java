package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate;

import com.winnersystems.smartparking.parking.application.port.input.parkingshiftrate.ConfigureParkingShiftRatesUseCase;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.dto.request.ConfigureParkingShiftRatesRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.parkingshiftrate.mapper.ParkingShiftRateRestMapper;
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
import static org.mockito.Mockito.mock;

/** Leer los horarios exige parkings.read; cambiarlos parkings.update */
@SpringJUnitConfig(ParkingShiftRateRestAdapterSecurityTest.Config.class)
class ParkingShiftRateRestAdapterSecurityTest {

   @Configuration
   @EnableMethodSecurity
   static class Config {
      @Bean ConfigureParkingShiftRatesUseCase useCase() { return mock(ConfigureParkingShiftRatesUseCase.class); }
      @Bean ParkingShiftRateRestMapper mapper() { return new ParkingShiftRateRestMapper(); }
      @Bean ParkingShiftRateRestAdapter adapter(ConfigureParkingShiftRatesUseCase u, ParkingShiftRateRestMapper m) {
         return new ParkingShiftRateRestAdapter(u, m);
      }
   }

   @Autowired
   ParkingShiftRateRestAdapter adapter;

   private static void login(String... authorities) {
      SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", null, authorities));
   }

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   @Test
   @DisplayName("parkings.read lee pero no cambia; parkings.update cambia")
   void permissions() {
      login("parkings.read");
      adapter.getParkingShiftRates(1L);
      ConfigureParkingShiftRatesRequest req = new ConfigureParkingShiftRatesRequest(List.of());
      assertThatThrownBy(() -> adapter.configureShiftRates(1L, req)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.toggleShiftRateStatus(1L)).isInstanceOf(AccessDeniedException.class);
      login("parkings.update");
      adapter.configureShiftRates(1L, req);
      adapter.deleteShiftRateConfig(1L);
      assertThatThrownBy(() -> adapter.getParkingShiftRates(1L)).isInstanceOf(AccessDeniedException.class);
   }

   @Test
   @DisplayName("¿atiende ahora? solo exige sesión")
   void current() {
      login("transactions.create");
      try {
         adapter.getCurrentSchedule(1L);
      } catch (NullPointerException ignored) {
         // el caso de uso simulado devuelve null; aquí solo importa que pase la seguridad
      }
   }
}
