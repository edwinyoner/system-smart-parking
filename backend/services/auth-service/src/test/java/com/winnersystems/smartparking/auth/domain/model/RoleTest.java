package com.winnersystems.smartparking.auth.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reglas de dominio de Role: restauración desde la papelera.
 */
class RoleTest {

   @Test
   @DisplayName("restaurar un rol que no está eliminado lanza IllegalStateException")
   void restoreNotDeletedFails() {
      assertThatThrownBy(() -> new Role("JEFE", "d").restore())
            .isInstanceOf(IllegalStateException.class);
   }

   @Test
   @DisplayName("restaurar limpia deletedAt y reactiva el rol")
   void restoreClearsDeletion() {
      Role role = new Role("JEFE", "d");
      role.markAsDeleted(1L);
      role.restore();
      assertThat(role.getDeletedAt()).isNull();
      assertThat(role.isActive()).isTrue();
   }
}
