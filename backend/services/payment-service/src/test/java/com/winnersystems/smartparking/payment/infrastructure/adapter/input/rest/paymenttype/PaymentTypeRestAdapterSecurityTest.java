package com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.paymenttype;

import com.winnersystems.smartparking.payment.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.payment.application.dto.query.PaymentTypeDto;
import com.winnersystems.smartparking.payment.application.port.input.paymenttype.*;
import com.winnersystems.smartparking.payment.domain.model.ConfirmationMode;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;
import com.winnersystems.smartparking.payment.infrastructure.adapter.input.rest.paymenttype.mapper.PaymentTypeRestMapper;
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

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Los @PreAuthorize de tipos de pago exigen el permiso correcto, incluido payment-types.restore */
@SpringJUnitConfig(PaymentTypeRestAdapterSecurityTest.Config.class)
class PaymentTypeRestAdapterSecurityTest {

   private static final PaymentTypeDto DTO = new PaymentTypeDto(1L, "QR", "QR", null, ConfirmationMode.AUTOMATIC,
         false, Set.of(PaymentChannel.MOBILE_APP), true, 0, null, null, null, null, null, null);

   @Configuration
   @EnableMethodSecurity
   static class Config {
      @Bean CreatePaymentTypeUseCase create() { return mock(CreatePaymentTypeUseCase.class); }
      @Bean UpdatePaymentTypeUseCase update() { return mock(UpdatePaymentTypeUseCase.class); }
      @Bean DeletePaymentTypeUseCase delete() { return mock(DeletePaymentTypeUseCase.class); }
      @Bean GetPaymentTypeUseCase get() { return mock(GetPaymentTypeUseCase.class); }
      @Bean TogglePaymentTypeStatusUseCase toggle() {
         TogglePaymentTypeStatusUseCase toggle = mock(TogglePaymentTypeStatusUseCase.class);
         when(toggle.togglePaymentTypeStatus(anyLong())).thenReturn(DTO);
         return toggle;
      }
      @Bean RestorePaymentTypeUseCase restore() {
         RestorePaymentTypeUseCase restore = mock(RestorePaymentTypeUseCase.class);
         when(restore.restorePaymentType(anyLong())).thenReturn(DTO);
         return restore;
      }
      @Bean ListPaymentTypesUseCase list() {
         ListPaymentTypesUseCase list = mock(ListPaymentTypesUseCase.class);
         when(list.listAllPaymentTypes(anyInt(), anyInt(), any(), any(), anyBoolean())).thenReturn(PagedResponse.of(List.of()));
         when(list.listActivePaymentTypes(any())).thenReturn(List.of(DTO));
         return list;
      }
      @Bean PaymentTypeRestMapper mapper() { return new PaymentTypeRestMapper(); }
      @Bean PaymentTypeRestAdapter adapter(CreatePaymentTypeUseCase c, UpdatePaymentTypeUseCase u,
                                           DeletePaymentTypeUseCase d, GetPaymentTypeUseCase g,
                                           ListPaymentTypesUseCase l, TogglePaymentTypeStatusUseCase t,
                                           RestorePaymentTypeUseCase r, PaymentTypeRestMapper m) {
         return new PaymentTypeRestAdapter(c, u, d, g, l, t, r, m);
      }
   }

   @Autowired
   PaymentTypeRestAdapter adapter;

   private static void login(String... authorities) {
      SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", null, authorities));
   }

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   @Test
   @DisplayName("read lista; sin update no cambia el estado; sin restore no ve la papelera")
   void permissions() {
      login("payment-types.read", "payment-types.delete");
      adapter.list(0, 10, null, null, false);
      adapter.delete(1L);
      assertThatThrownBy(() -> adapter.list(0, 10, null, null, true)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.restore(1L)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.toggleStatus(1L)).isInstanceOf(AccessDeniedException.class);

      login("payment-types.read", "payment-types.restore", "payment-types.update");
      adapter.list(0, 10, null, null, true);
      adapter.restore(1L);
      adapter.toggleStatus(1L);
   }

   @Test
   @DisplayName("/active solo exige sesión")
   void active() {
      assertThatThrownBy(() -> adapter.listActive(null)).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
      login("transactions.update");
      adapter.listActive(PaymentChannel.OPERATOR);
   }
}
