package com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.user;

import com.winnersystems.smartparking.auth.application.dto.query.UserDto;
import com.winnersystems.smartparking.auth.application.port.input.user.*;
import com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.user.dto.response.UserResponse;
import com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.user.mapper.UserRestMapper;
import com.winnersystems.smartparking.auth.infrastructure.config.security.CustomUserDetails;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET /users/me devuelve los permisos vigentes del rol activo, para que el panel
 * oculte o muestre botones sin volver a iniciar sesión.
 */
class UserRestAdapterTest {

   @Test
   @DisplayName("GET /users/me: datos al mismo nivel y solo los permisos del rol activo, sin ROLE_")
   void meReturnsActivePermissions() throws Exception {
      GetUserUseCase getUser = mock(GetUserUseCase.class);
      UserRestMapper mapper = mock(UserRestMapper.class);
      UserDto dto = new UserDto(20L, "Rosa", "Díaz", "rosa@x.pe", null, null, true, true,
            Set.of("AUTORIDAD", "OPERADOR"), null, null, null);
      when(getUser.execute(20L)).thenReturn(dto);
      when(mapper.toResponse(any(UserDto.class))).thenReturn(new UserResponse(20L, "Rosa", "Díaz", "Rosa Díaz",
            "rosa@x.pe", null, null, true, true, Set.of(), null, null, null));

      UserRestAdapter adapter = new UserRestAdapter(mock(CreateUserUseCase.class), mock(UpdateUserUseCase.class),
            getUser, mock(ListUsersUseCase.class), mock(DeleteUserUseCase.class), mock(RestoreUserUseCase.class),
            mock(ResendCredentialsUseCase.class), mock(ListOperatorsUseCase.class), mapper);
      MockMvc mvc = MockMvcBuilders.standaloneSetup(adapter).build();

      // Tiene dos roles, pero entró como AUTORIDAD: los permisos de OPERADOR no cuentan
      Map<String, Set<GrantedAuthority>> byRole = Map.of(
            "AUTORIDAD", Set.of(new SimpleGrantedAuthority("ROLE_AUTORIDAD"),
                  new SimpleGrantedAuthority("users.read"), new SimpleGrantedAuthority("parkings.read")),
            "OPERADOR", Set.of(new SimpleGrantedAuthority("ROLE_OPERADOR"),
                  new SimpleGrantedAuthority("transactions.create")));
      CustomUserDetails principal = new CustomUserDetails(20L, "rosa@x.pe", "hash", true, true, true, true,
            byRole, Instant.now()).withActiveRole("AUTORIDAD");
      var authentication = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());

      mvc.perform(get("/users/me").principal(authentication))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("rosa@x.pe"))
            .andExpect(jsonPath("$.user").doesNotExist())
            .andExpect(jsonPath("$.activeRole").value("AUTORIDAD"))
            .andExpect(jsonPath("$.activePermissions.length()").value(2))
            .andExpect(jsonPath("$.activePermissions[0]").value("parkings.read"))
            .andExpect(jsonPath("$.activePermissions[1]").value("users.read"));
   }
}
