package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.documenttype;

import com.winnersystems.smartparking.parking.application.dto.query.DocumentTypeDto;
import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.port.input.documenttype.*;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.documenttype.mapper.DocumentTypeRestMapper;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Los @PreAuthorize de tipos de documento exigen el permiso correcto,
 * incluido document-types.restore para la papelera.
 */
@SpringJUnitConfig(DocumentTypeRestAdapterSecurityTest.Config.class)
class DocumentTypeRestAdapterSecurityTest {

   @Configuration
   @EnableMethodSecurity
   static class Config {
      @Bean CreateDocumentTypeUseCase create() { return mock(CreateDocumentTypeUseCase.class); }
      @Bean UpdateDocumentTypeUseCase update() { return mock(UpdateDocumentTypeUseCase.class); }
      @Bean DeleteDocumentTypeUseCase delete() { return mock(DeleteDocumentTypeUseCase.class); }
      @Bean GetDocumentTypeUseCase get() { return mock(GetDocumentTypeUseCase.class); }
      @Bean ToggleDocumentTypeStatusUseCase toggle() { return mock(ToggleDocumentTypeStatusUseCase.class); }
      @Bean RestoreDocumentTypeUseCase restore() {
         RestoreDocumentTypeUseCase restore = mock(RestoreDocumentTypeUseCase.class);
         when(restore.restoreDocumentType(anyLong())).thenReturn(new DocumentTypeDto(1L, "DNI", "DNI", null, "1",
               8, 8, true, "8 dígitos", true, 0, 0, null, null, null, null, null, null));
         return restore;
      }
      @Bean ListDocumentTypesUseCase list() {
         ListDocumentTypesUseCase list = mock(ListDocumentTypesUseCase.class);
         when(list.listAllDocumentTypes(anyInt(), anyInt(), any(), any(), anyBoolean()))
               .thenReturn(PagedResponse.of(List.of()));
         return list;
      }
      @Bean DocumentTypeRestMapper mapper() { return new DocumentTypeRestMapper(); }
      @Bean DocumentTypeRestAdapter adapter(CreateDocumentTypeUseCase c, UpdateDocumentTypeUseCase u,
                                            DeleteDocumentTypeUseCase d, GetDocumentTypeUseCase g,
                                            ListDocumentTypesUseCase l, ToggleDocumentTypeStatusUseCase t,
                                            RestoreDocumentTypeUseCase r, DocumentTypeRestMapper m) {
         return new DocumentTypeRestAdapter(c, u, d, g, l, t, r, m);
      }
   }

   @Autowired
   DocumentTypeRestAdapter adapter;

   private static void login(String... authorities) {
      SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("user", null, authorities));
   }

   @AfterEach
   void clear() {
      SecurityContextHolder.clearContext();
   }

   @Test
   @DisplayName("read lista; sin delete no elimina; sin restore no ve la papelera")
   void permissions() {
      login("document-types.read", "document-types.delete");
      adapter.list(0, 10, null, null, false);
      adapter.delete(1L);
      assertThatThrownBy(() -> adapter.list(0, 10, null, null, true)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.restore(1L)).isInstanceOf(AccessDeniedException.class);
      assertThatThrownBy(() -> adapter.toggleStatus(1L)).isInstanceOf(AccessDeniedException.class);

      login("document-types.read", "document-types.restore");
      adapter.list(0, 10, null, null, true);
      adapter.restore(1L);
   }

   @Test
   @DisplayName("/active solo exige sesión (formularios de entrada y salida)")
   void active() {
      assertThatThrownBy(() -> adapter.listActive()).isInstanceOf(AuthenticationCredentialsNotFoundException.class);
      login("transactions.create");
      adapter.listActive();
   }
}
