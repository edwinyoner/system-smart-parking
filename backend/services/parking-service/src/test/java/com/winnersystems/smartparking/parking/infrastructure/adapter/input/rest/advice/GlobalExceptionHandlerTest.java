package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.advice;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Un permiso denegado (@PreAuthorize) responde 403, no 500.
 */
class GlobalExceptionHandlerTest {

   @RestController
   static class DeniedController {
      @GetMapping("/denegado")
      String denied() {
         throw new AccessDeniedException("Access Denied");
      }
   }

   @Test
   @DisplayName("AccessDeniedException: 403 con mensaje en español")
   void accessDeniedIs403() throws Exception {
      MockMvc mvc = MockMvcBuilders.standaloneSetup(new DeniedController())
            .setControllerAdvice(new GlobalExceptionHandler()).build();
      mvc.perform(get("/denegado"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.status").value(403))
            .andExpect(jsonPath("$.message").value("No tienes permiso para realizar esta acción"));
   }
}
