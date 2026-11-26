package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space;

import com.winnersystems.smartparking.parking.application.port.input.space.*;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.advice.GlobalExceptionHandler;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.space.mapper.SpaceRestMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Un elemento inválido del lote responde 400 indicando su posición, sin llegar al servicio */
class SpaceBatchValidationTest {

   @Test
   @DisplayName("lote con un código inválido en la posición 1: 400 con la clave [1].code")
   void invalidItem() throws Exception {
      CreateSpaceUseCase create = mock(CreateSpaceUseCase.class);
      when(create.createSpaces(any())).thenReturn(List.of(SpaceRestAdapterSecurityTest.DTO));
      SpaceRestAdapter adapter = new SpaceRestAdapter(create, mock(UpdateSpaceUseCase.class),
            mock(DeleteSpaceUseCase.class), mock(RestoreSpaceUseCase.class), mock(GetSpaceUseCase.class),
            mock(ListSpacesUseCase.class), mock(ToggleSpaceStatusUseCase.class), new SpaceRestMapper());
      MockMvc mvc = MockMvcBuilders.standaloneSetup(adapter)
            .setControllerAdvice(new GlobalExceptionHandler()).build();

      String body = """
            [{"zoneId": 1, "code": "A-001", "type": "PARALLEL"},
             {"zoneId": 1, "code": "A 002", "type": "PARALLEL"}]
            """;
      mvc.perform(post("/spaces/batch").contentType(MediaType.APPLICATION_JSON).content(body))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors['[1].code']").exists());
      verify(create, never()).createSpaces(any());

      String ok = """
            [{"zoneId": 1, "code": "A-001", "type": "PARALLEL"}]
            """;
      mvc.perform(post("/spaces/batch").contentType(MediaType.APPLICATION_JSON).content(ok))
            .andExpect(status().isCreated());
   }
}
