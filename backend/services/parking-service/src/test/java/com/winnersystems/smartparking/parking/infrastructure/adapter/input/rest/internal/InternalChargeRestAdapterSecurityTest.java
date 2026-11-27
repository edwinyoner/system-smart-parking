package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.internal;

import com.winnersystems.smartparking.parking.application.dto.query.ChargeDto;
import com.winnersystems.smartparking.parking.application.port.input.charge.GetChargeUseCase;
import com.winnersystems.smartparking.parking.application.port.input.charge.SettleChargeUseCase;
import com.winnersystems.smartparking.parking.domain.model.ChargeConcept;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.internal.dto.SettlementRequest;
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

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Solo un token de servicio con el permiso interno consulta o liquida un cobro */
@SpringJUnitConfig(InternalChargeRestAdapterSecurityTest.Config.class)
class InternalChargeRestAdapterSecurityTest {

   @Configuration
   @EnableMethodSecurity
   static class Config {
      @Bean GetChargeUseCase get() {
         GetChargeUseCase get = mock(GetChargeUseCase.class);
         when(get.getCharge(any(), any())).thenReturn(
               new ChargeDto(ChargeConcept.PARKING_STAY, 1L, BigDecimal.ONE, "PEN", null, null, 1L, null, true, null));
         return get;
      }
      @Bean SettleChargeUseCase settle() { return mock(SettleChargeUseCase.class); }
      @Bean InternalChargeRestAdapter adapter(GetChargeUseCase g, SettleChargeUseCase s) {
         return new InternalChargeRestAdapter(g, s);
      }
   }

   @Autowired
   InternalChargeRestAdapter adapter;

   private static final SettlementRequest REQUEST = new SettlementRequest(
         42L, "COMPLETED", BigDecimal.ONE, BigDecimal.ZERO, null, "QR", "QR", "OP1");

   private static void login(String... authorities) {
      SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", null, authorities));
   }

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   @Test
   @DisplayName("token de servicio: read consulta, settle liquida; cada uno exige el suyo")
   void serviceToken() {
      login("SERVICE_CLIENT", "internal.charges.read");
      adapter.getCharge(ChargeConcept.PARKING_STAY, 1L);
      assertThatThrownBy(() -> adapter.settle(ChargeConcept.PARKING_STAY, 1L, REQUEST)).isInstanceOf(AccessDeniedException.class);

      login("SERVICE_CLIENT", "internal.charges.settle");
      adapter.settle(ChargeConcept.PARKING_STAY, 1L, REQUEST);
   }

   @Test
   @DisplayName("un usuario, aunque sea ADMIN con todos sus permisos, no entra")
   void userDenied() {
      login("ROLE_ADMIN", "transactions.update", "payments.process", "internal.charges.settle");
      assertThatThrownBy(() -> adapter.settle(ChargeConcept.PARKING_STAY, 1L, REQUEST)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.getCharge(ChargeConcept.PARKING_STAY, 1L)).isInstanceOf(AccessDeniedException.class);
   }
}
