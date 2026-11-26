package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment;

import com.winnersystems.smartparking.parking.application.port.input.operatorassignment.ManageOperatorAssignmentsUseCase;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment.dto.request.AssignOperatorRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.operatorassignment.mapper.OperatorAssignmentRestMapper;
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

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Protegido por permisos operator-assignments.*, no por nombre de rol */
@SpringJUnitConfig(OperatorAssignmentRestAdapterSecurityTest.Config.class)
class OperatorAssignmentRestAdapterSecurityTest {

   @Configuration
   @EnableMethodSecurity
   static class Config {
      @Bean ManageOperatorAssignmentsUseCase useCase() {
         ManageOperatorAssignmentsUseCase u = mock(ManageOperatorAssignmentsUseCase.class);
         when(u.finish(any())).thenReturn(Optional.empty());
         return u;
      }
      @Bean OperatorAssignmentRestMapper mapper() { return new OperatorAssignmentRestMapper(); }
      @Bean OperatorAssignmentRestAdapter adapter(ManageOperatorAssignmentsUseCase u, OperatorAssignmentRestMapper m) {
         return new OperatorAssignmentRestAdapter(u, m);
      }
   }

   @Autowired
   OperatorAssignmentRestAdapter adapter;

   private static void login(String... authorities) {
      SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("x", null, authorities));
   }

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   @Test
   @DisplayName("un rol llamado ADMIN sin el permiso no pasa; el permiso sí")
   void byPermission() {
      AssignOperatorRequest req = new AssignOperatorRequest(1L, 1L, null, 1L, null, null);
      login("ROLE_ADMIN", "parkings.update");
      assertThatThrownBy(() -> adapter.list(1L, null, null, null)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.finish(1L)).isInstanceOf(AccessDeniedException.class);
      login("operator-assignments.read");
      adapter.list(1L, null, null, null);
      assertThatThrownBy(() -> adapter.assign(req)).isInstanceOf(AccessDeniedException.class);
      login("operator-assignments.delete");
      adapter.finish(1L);
   }

   @Test
   @DisplayName("mis asignaciones solo exige sesión")
   void mine() {
      login("transactions.entry");
      adapter.listMine(null);
   }
}
