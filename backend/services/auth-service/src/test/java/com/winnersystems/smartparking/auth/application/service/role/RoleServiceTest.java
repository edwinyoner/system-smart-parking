package com.winnersystems.smartparking.auth.application.service.role;

import com.winnersystems.smartparking.auth.application.dto.query.RoleDto;
import com.winnersystems.smartparking.auth.application.port.output.PermissionPersistencePort;
import com.winnersystems.smartparking.auth.application.port.output.RolePersistencePort;
import com.winnersystems.smartparking.auth.application.port.output.UserPersistencePort;
import com.winnersystems.smartparking.auth.domain.exception.RoleNotFoundException;
import com.winnersystems.smartparking.auth.domain.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RoleService: papelera de roles y nombres de roles eliminados.
 */
class RoleServiceTest {

   private RolePersistencePort roles;
   private RoleService service;

   @BeforeEach
   void setUp() {
      roles = mock(RolePersistencePort.class);
      service = new RoleService(roles, mock(PermissionPersistencePort.class), mock(UserPersistencePort.class));
      when(roles.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
   }

   private static Role role(long id, String name, boolean deleted) {
      Role role = new Role(name, "d");
      role.setId(id);
      role.setCreatedAt(LocalDateTime.now());
      if (deleted) {
         role.markAsDeleted(1L);
      }
      return role;
   }

   @Test
   @DisplayName("restaurar: el rol vuelve activo y se guarda quién lo restauró")
   void restores() {
      when(roles.findDeletedById(5L)).thenReturn(Optional.of(role(5, "SUPERVISOR", true)));

      RoleDto dto = service.restoreRole(5L, 1L);

      assertThat(dto.status()).isTrue();
      assertThat(dto.deletedAt()).isNull();
      verify(roles).save(argThat(r -> Objects.equals(r.getUpdatedBy(), 1L)));
   }

   @Test
   @DisplayName("restaurar un rol no eliminado: IllegalStateException (409)")
   void restoreNotDeleted() {
      when(roles.findDeletedById(6L)).thenReturn(Optional.empty());
      when(roles.findById(6L)).thenReturn(Optional.of(role(6, "ACTIVO", false)));
      assertThatThrownBy(() -> service.restoreRole(6L, 1L)).isInstanceOf(IllegalStateException.class);
   }

   @Test
   @DisplayName("restaurar un rol inexistente: RoleNotFoundException (404)")
   void restoreNotFound() {
      when(roles.findDeletedById(7L)).thenReturn(Optional.empty());
      when(roles.findById(7L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.restoreRole(7L, 1L)).isInstanceOf(RoleNotFoundException.class);
   }

   @Test
   @DisplayName("papelera: solo eliminados, el más reciente primero")
   void trashSortedByDeletion() {
      Role older = role(8, "VIEJO", true);
      older.setDeletedAt(LocalDateTime.now().minusDays(2));
      Role newer = role(9, "NUEVO", true);
      when(roles.findAllDeleted()).thenReturn(List.of(older, newer));

      var page = service.listRoles(null, null, true, 0, 10);

      assertThat(page.content()).extracting(RoleDto::name).containsExactly("NUEVO", "VIEJO");
   }

   @Test
   @DisplayName("crear con el nombre de un rol eliminado sugiere restaurarlo")
   void nameOfDeletedRole() {
      when(roles.findByNameIncludingDeleted("VIEJO")).thenReturn(Optional.of(role(8, "VIEJO", true)));
      assertThatThrownBy(() -> service.createRole("viejo", "x", true, Set.of(1L), 1L))
            .hasMessageContaining("rol eliminado");
   }

   @Test
   @DisplayName("crear con un nombre en uso: 'ya está en uso'")
   void nameInUse() {
      when(roles.findByNameIncludingDeleted("USADO")).thenReturn(Optional.of(role(10, "USADO", false)));
      assertThatThrownBy(() -> service.createRole("usado", "x", true, Set.of(1L), 1L))
            .hasMessageContaining("ya está en uso");
   }
}
