package com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.role;

import com.winnersystems.smartparking.auth.application.service.role.RoleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * El parámetro deleted=true lleva a la papelera; sin él, al listado normal.
 * MockMvc en modo standalone: prueba el mapeo HTTP sin levantar Spring completo.
 */
class RoleRestAdapterTest {

   private RoleService service;
   private MockMvc mvc;

   @BeforeEach
   void setUp() {
      service = mock(RoleService.class);
      mvc = MockMvcBuilders.standaloneSetup(new RoleRestAdapter(service)).build();
   }

   @Test
   @DisplayName("GET /roles?deleted=true lista la papelera")
   void deletedGoesToTrash() throws Exception {
      mvc.perform(get("/roles").param("deleted", "true"));
      verify(service).listRoles(null, null, true, 0, 10);
   }

   @Test
   @DisplayName("GET /roles?status=true es el listado normal")
   void normalListing() throws Exception {
      mvc.perform(get("/roles").param("status", "true"));
      verify(service).listRoles(null, true, false, 0, 10);
   }
}
