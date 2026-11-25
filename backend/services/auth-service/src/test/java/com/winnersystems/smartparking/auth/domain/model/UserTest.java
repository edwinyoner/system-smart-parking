package com.winnersystems.smartparking.auth.domain.model;

import com.winnersystems.smartparking.auth.domain.exception.ProtectedUserException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas de dominio de User: restauración y protección de cuentas ADMIN.
 */
class UserTest {

   private static User user(String roleName, boolean deleted) {
      User user = new User("Ana", "Pérez", "ana@x.com", "hash");
      user.setId(1L);
      Role role = new Role(roleName, "d");
      role.setId(1L);
      user.setRoles(new HashSet<>(Set.of(role)));
      if (deleted) {
         user.markAsDeleted(99L);
      }
      return user;
   }

   @Test
   @DisplayName("restaurar un usuario que no está eliminado lanza IllegalStateException")
   void restoreNotDeletedFails() {
      assertThatThrownBy(() -> user("OPERADOR", false).restore())
            .isInstanceOf(IllegalStateException.class);
   }

   @Test
   @DisplayName("restaurar limpia deletedAt y deletedBy y reactiva la cuenta")
   void restoreClearsDeletion() {
      User user = user("OPERADOR", true);
      user.restore();
      assertThat(user.getDeletedAt()).isNull();
      assertThat(user.getDeletedBy()).isNull();
      assertThat(user.getStatus()).isTrue();
   }

   @Test
   @DisplayName("solo quien entró como ADMIN restaura a un ADMIN")
   void onlyActiveAdminRestoresAdmin() {
      assertThatThrownBy(() -> user("ADMIN", true).ensureCanBeRestoredBy("OPERADOR"))
            .isInstanceOf(ProtectedUserException.class);
      assertThatCode(() -> user("ADMIN", true).ensureCanBeRestoredBy("ADMIN"))
            .doesNotThrowAnyException();
   }

   @Test
   @DisplayName("AUTORIDAD puede restaurar a un OPERADOR")
   void authorityRestoresOperator() {
      assertThatCode(() -> user("OPERADOR", true).ensureCanBeRestoredBy("AUTORIDAD"))
            .doesNotThrowAnyException();
   }
}
