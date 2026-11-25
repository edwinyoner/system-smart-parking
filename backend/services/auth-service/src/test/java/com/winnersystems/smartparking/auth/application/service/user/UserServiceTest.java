package com.winnersystems.smartparking.auth.application.service.user;

import com.winnersystems.smartparking.auth.application.dto.command.CreateUserCommand;
import com.winnersystems.smartparking.auth.application.dto.command.UpdateUserCommand;
import com.winnersystems.smartparking.auth.application.dto.query.UserDto;
import com.winnersystems.smartparking.auth.application.port.output.EmailPort;
import com.winnersystems.smartparking.auth.application.port.output.PasswordEncoderPort;
import com.winnersystems.smartparking.auth.application.port.output.RolePersistencePort;
import com.winnersystems.smartparking.auth.application.port.output.TokenPersistencePort;
import com.winnersystems.smartparking.auth.application.port.output.UserPersistencePort;
import com.winnersystems.smartparking.auth.domain.exception.EmailAlreadyExistsException;
import com.winnersystems.smartparking.auth.domain.exception.ProtectedUserException;
import com.winnersystems.smartparking.auth.domain.exception.UserNotFoundException;
import com.winnersystems.smartparking.auth.domain.model.Role;
import com.winnersystems.smartparking.auth.domain.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * UserService con los puertos simulados (Mockito): no necesita base de datos ni Spring.
 */
class UserServiceTest {

   private UserPersistencePort users;
   private TokenPersistencePort tokens;
   private EmailPort email;
   private RolePersistencePort roles;
   private PasswordEncoderPort encoder;
   private UserService service;

   @BeforeEach
   void setUp() {
      users = mock(UserPersistencePort.class);
      tokens = mock(TokenPersistencePort.class);
      email = mock(EmailPort.class);
      roles = mock(RolePersistencePort.class);
      encoder = mock(PasswordEncoderPort.class);
      service = new UserService(users, roles, encoder, email, tokens);
      when(users.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
      when(users.findByEmailIncludingDeleted(anyString())).thenReturn(Optional.empty());
   }

   private static User user(long id, String mail, String roleName, boolean deleted) {
      User user = new User("Ana", "Pérez", mail, "hash");
      user.setId(id);
      user.verifyEmail();
      Role role = new Role(roleName, "d");
      role.setId(1L);
      user.setRoles(new HashSet<>(Set.of(role)));
      if (deleted) {
         user.markAsDeleted(99L);
      }
      return user;
   }

   @Nested
   @DisplayName("Crear usuario")
   class Create {

      @Test
      @DisplayName("guarda la contraseña hasheada y no crea token ni envía correo")
      void createsWithoutSendingEmail() {
         Role operator = new Role("OPERADOR", "d");
         operator.setId(3L);
         when(roles.findAllByIds(Set.of(3L))).thenReturn(List.of(operator));
         when(encoder.encode("Clave#2026")).thenReturn("hash-bcrypt");

         service.execute(new CreateUserCommand("Luis", "Rojas", "luis@x.pe", "Clave#2026",
               "987654321", Set.of(3L), null, "127.0.0.1", 1L, "ADMIN"));

         verify(users).save(argThat(u -> "hash-bcrypt".equals(u.getPassword())));
         // Las credenciales se envían aparte, cuando el administrador lo decide (resend-credentials)
         verifyNoInteractions(tokens, email);
      }
   }

   @Nested
   @DisplayName("Restaurar usuario eliminado")
   class Restore {

      @Test
      @DisplayName("id inexistente: UserNotFoundException (404)")
      void notFound() {
         when(users.findDeletedById(5L)).thenReturn(Optional.empty());
         when(users.findById(5L)).thenReturn(Optional.empty());
         assertThatThrownBy(() -> service.executeRestore(5L, 1L, "ADMIN"))
               .isInstanceOf(UserNotFoundException.class);
      }

      @Test
      @DisplayName("usuario no eliminado: IllegalStateException (409)")
      void notDeleted() {
         when(users.findDeletedById(6L)).thenReturn(Optional.empty());
         when(users.findById(6L)).thenReturn(Optional.of(user(6, "a@x.pe", "OPERADOR", false)));
         assertThatThrownBy(() -> service.executeRestore(6L, 1L, "ADMIN"))
               .isInstanceOf(IllegalStateException.class);
      }

      @Test
      @DisplayName("un OPERADOR no restaura a un ADMIN y no se guarda nada")
      void operatorCannotRestoreAdmin() {
         when(users.findDeletedById(7L)).thenReturn(Optional.of(user(7, "a@x.pe", "ADMIN", true)));
         assertThatThrownBy(() -> service.executeRestore(7L, 1L, "OPERADOR"))
               .isInstanceOf(ProtectedUserException.class);
         verify(users, never()).save(any());
      }

      @Test
      @DisplayName("restauración correcta: reactiva, revoca sesiones y avisa por correo")
      void restores() {
         when(users.findDeletedById(8L)).thenReturn(Optional.of(user(8, "ana@x.com", "OPERADOR", true)));

         UserDto dto = service.executeRestore(8L, 1L, "ADMIN");

         assertThat(dto.status()).isTrue();
         assertThat(dto.deletedAt()).isNull();
         verify(users).save(argThat(u -> u.getId() == 8L && Objects.equals(u.getUpdatedBy(), 1L)));
         verify(tokens).revokeRefreshTokensByUserId(8L);
         verify(email).sendAccountActivatedEmail(eq("ana@x.com"), anyString());
      }
   }

   @Nested
   @DisplayName("Cambiar el correo de un usuario")
   class ChangeEmail {

      private UpdateUserCommand command(long id, String mail, long actorId, String actorRole) {
         return new UpdateUserCommand(id, "Ana", "Pérez", mail, null, null, null, null, actorId, actorRole);
      }

      @Test
      @DisplayName("correo nuevo: queda sin verificar y se revocan sesiones y enlaces")
      void changesEmail() {
         when(users.findById(2L)).thenReturn(Optional.of(user(2, "ana@gmial.com", "OPERADOR", false)));

         UserDto dto = service.execute(command(2, "ana@gmail.com", 1, "ADMIN"));

         assertThat(dto.email()).isEqualTo("ana@gmail.com");
         assertThat(dto.emailVerified()).isFalse();
         verify(tokens).revokeRefreshTokensByUserId(2L);
         verify(tokens).deleteVerificationTokensByUserId(2L);
         verify(tokens).revokePasswordResetTokensByUserId(2L);
      }

      @Test
      @DisplayName("mismo correo: sigue verificado y no se revoca nada")
      void sameEmail() {
         when(users.findById(3L)).thenReturn(Optional.of(user(3, "luis@x.pe", "OPERADOR", false)));
         assertThat(service.execute(command(3, "luis@x.pe", 1, "ADMIN")).emailVerified()).isTrue();
         verifyNoInteractions(tokens);
      }

      @Test
      @DisplayName("solo cambian mayúsculas: es el mismo buzón y sigue verificado")
      void onlyCaseChanges() {
         when(users.findById(4L)).thenReturn(Optional.of(user(4, "rosa@x.pe", "OPERADOR", false)));
         UserDto dto = service.execute(command(4, "Rosa@x.pe", 1, "ADMIN"));
         assertThat(dto.email()).isEqualTo("Rosa@x.pe");
         assertThat(dto.emailVerified()).isTrue();
      }

      @Test
      @DisplayName("correo de otro usuario: EmailAlreadyExistsException (409)")
      void emailTaken() {
         when(users.findById(5L)).thenReturn(Optional.of(user(5, "a@x.pe", "OPERADOR", false)));
         when(users.findByEmailIncludingDeleted("ocupado@x.pe"))
               .thenReturn(Optional.of(user(9, "ocupado@x.pe", "OPERADOR", false)));
         assertThatThrownBy(() -> service.execute(command(5, "ocupado@x.pe", 1, "ADMIN")))
               .isInstanceOf(EmailAlreadyExistsException.class)
               .hasMessageContaining("ya está registrado");
      }

      @Test
      @DisplayName("correo de un usuario eliminado: el mensaje sugiere restaurarlo")
      void emailOfDeletedUser() {
         when(users.findById(5L)).thenReturn(Optional.of(user(5, "a@x.pe", "OPERADOR", false)));
         when(users.findByEmailIncludingDeleted("borrado@x.pe"))
               .thenReturn(Optional.of(user(10, "borrado@x.pe", "OPERADOR", true)));
         assertThatThrownBy(() -> service.execute(command(5, "borrado@x.pe", 1, "ADMIN")))
               .isInstanceOf(EmailAlreadyExistsException.class)
               .hasMessageContaining("usuario eliminado");
      }

      @Test
      @DisplayName("nadie cambia su propio correo desde la gestión de usuarios")
      void ownEmail() {
         when(users.findById(1L)).thenReturn(Optional.of(user(1, "yo@x.pe", "ADMIN", false)));
         assertThatThrownBy(() -> service.execute(command(1, "otro@x.pe", 1, "ADMIN")))
               .isInstanceOf(ProtectedUserException.class);
      }

      @Test
      @DisplayName("el correo de un ADMIN solo lo cambia quien entró como ADMIN")
      void adminEmail() {
         when(users.findById(6L)).thenReturn(Optional.of(user(6, "jefe@x.pe", "ADMIN", false)));
         assertThatThrownBy(() -> service.execute(command(6, "jefe2@x.pe", 1, "OPERADOR")))
               .isInstanceOf(ProtectedUserException.class);
         assertThatCode(() -> service.execute(command(6, "jefe2@x.pe", 1, "ADMIN")))
               .doesNotThrowAnyException();
      }

      @Test
      @DisplayName("sin ADMIN activo no se edita el nombre de un ADMIN y no se guarda nada")
      void nonAdminCannotEditAdminName() {
         when(users.findById(1L)).thenReturn(Optional.of(user(1, "admin@x.pe", "ADMIN", false)));
         UpdateUserCommand rename = new UpdateUserCommand(1L, "Otro", "Nombre", null, null, null, null, null, 20L, "AUTORIDAD");
         assertThatThrownBy(() -> service.execute(rename)).isInstanceOf(ProtectedUserException.class);
         verify(users, never()).save(any());
      }

      @Test
      @DisplayName("AUTORIDAD sí edita el nombre de un OPERADOR")
      void authorityEditsOperator() {
         when(users.findById(8L)).thenReturn(Optional.of(user(8, "op@x.pe", "OPERADOR", false)));
         UpdateUserCommand rename = new UpdateUserCommand(8L, "Luis", "Rojas", null, null, null, null, null, 20L, "AUTORIDAD");
         assertThat(service.execute(rename).firstName()).isEqualTo("Luis");
      }

      @Test
      @DisplayName("correo null: no cambia nada")
      void nullEmail() {
         when(users.findById(7L)).thenReturn(Optional.of(user(7, "null@x.pe", "OPERADOR", false)));
         assertThat(service.execute(command(7, null, 1, "ADMIN")).email()).isEqualTo("null@x.pe");
      }
   }
}
