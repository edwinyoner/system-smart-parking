package com.winnersystems.smartparking.auth.application.service.user;

import org.springframework.beans.factory.annotation.Value;
import com.winnersystems.smartparking.auth.application.dto.command.CreateUserCommand;
import com.winnersystems.smartparking.auth.application.dto.command.UpdateUserCommand;
import com.winnersystems.smartparking.auth.application.dto.query.OperatorDto;
import com.winnersystems.smartparking.auth.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.auth.application.dto.query.UserDto;
import com.winnersystems.smartparking.auth.application.dto.query.UserSearchCriteria;
import com.winnersystems.smartparking.auth.application.port.input.user.*;
import com.winnersystems.smartparking.auth.application.port.output.*;
import com.winnersystems.smartparking.auth.domain.exception.EmailAlreadyExistsException;
import com.winnersystems.smartparking.auth.domain.exception.UserNotFoundException;
import com.winnersystems.smartparking.auth.domain.model.Role;
import com.winnersystems.smartparking.auth.domain.model.User;
import com.winnersystems.smartparking.auth.domain.model.VerificationToken;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio de aplicación para gestión de usuarios.
 * Implementa los 8 casos de uso de user/.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService implements
      CreateUserUseCase,
      UpdateUserUseCase,
      GetUserUseCase,
      ListUsersUseCase,
      DeleteUserUseCase,
      RestoreUserUseCase,
      ResendCredentialsUseCase,
      ListOperatorsUseCase {

   private final UserPersistencePort userPersistencePort;
   private final RolePersistencePort rolePersistencePort;
   private final PasswordEncoderPort passwordEncoderPort;
   private final EmailPort emailPort;
   private final TokenPersistencePort tokenPersistencePort;

   /** URL del panel Angular, para los enlaces de los correos (email.base-url en auth-service.yml) */
   @Value("${email.base-url:http://localhost:4200}")
   private String frontendUrl;

   // ========== CREATE USER ==========

   @Override
   public UserDto execute(CreateUserCommand command) {
      // 1. Validar email único (también frente a usuarios eliminados: en la BD es único)
      ensureEmailIsAvailable(command.email());

      // 2. Validar que roles existen
      List<Role> roles = rolePersistencePort.findAllByIds(command.roleIds());
      if (roles.size() != command.roleIds().size()) {
         throw new IllegalArgumentException("Uno o más roles no existen");
      }
      ensureNewRolesAreActive(new HashSet<>(roles), Set.of());
      User.ensureRolesCanBeGrantedBy(command.actorActiveRole(), new HashSet<>(roles));

      // 3-4. Hashear la contraseña: nunca se guarda en texto plano
      String hashedPassword = passwordEncoderPort.encode(command.password());

      // 5. Crear usuario
      User user = new User(
            command.firstName(),
            command.lastName(),
            command.email(),
            hashedPassword
      );

      // 6. Asignar campos faltantes
      user.setPhoneNumber(command.phoneNumber());
      user.setCreatedBy(command.createdBy());
      user.setCreatedAt(LocalDateTime.now());

      // 7. Asignar roles
      user.setRoles(new HashSet<>(roles));

      // 8. Guardar usuario
      User savedUser = userPersistencePort.save(user);

      // 9. No se envía correo aquí: el administrador decide cuándo mandar las credenciales
      //    (POST /users/{id}/resend-credentials), y ese paso crea el token de verificación.

      return mapToDto(savedUser);
   }

   // ========== UPDATE USER ==========

   @Override
   public UserDto execute(UpdateUserCommand command) {
      // 1. Buscar usuario
      User user = userPersistencePort.findById(command.userId())
            .orElseThrow(() -> new UserNotFoundException(command.userId()));

      // 2. Actualizar estado. Va antes que los roles para evaluar el ADMIN original
      if (command.status() != null && command.status() != user.getStatus()) {
         user.ensureStatusCanBeChangedBy(command.updatedBy(), command.actorActiveRole());
         if (command.status()) {
            user.activate();
         } else {
            user.deactivate();
         }
      }

      // Actualizar campos si no son null
      if (command.firstName() != null) {
         user.setFirstName(command.firstName());
      }

      if (command.lastName() != null) {
         user.setLastName(command.lastName());
      }

      // Correo: solo si cambia. Queda sin verificar hasta que confirme el nuevo
      boolean emailChanged = command.email() != null
            && !command.email().equalsIgnoreCase(user.getEmail());
      if (emailChanged) {
         ensureEmailIsAvailable(command.email());
         user.changeEmail(command.email(), command.updatedBy(), command.actorActiveRole());
      } else if (command.email() != null && !command.email().equals(user.getEmail())) {
         user.setEmail(command.email()); // Solo cambian mayúsculas: mismo buzón, sigue verificado
      }

      if (command.phoneNumber() != null) {
         user.setPhoneNumber(command.phoneNumber());
      }

      if (command.profilePicture() != null) {
         user.setProfilePicture(command.profilePicture());
      }

      // 3. Actualizar roles si se especifican
      if (command.roleIds() != null) {
         List<Role> roles = rolePersistencePort.findAllByIds(command.roleIds());
         if (roles.size() != command.roleIds().size()) {
            throw new IllegalArgumentException("Uno o más roles no existen");
         }
         Set<Role> newRoles = new HashSet<>(roles);
         ensureNewRolesAreActive(newRoles, user.getRoles());

         // Validar solo si los roles cambian: reenviar los mismos no es un cambio
         if (!roleNames(newRoles).equals(roleNames(user.getRoles()))) {
            user.ensureRolesCanBeChangedBy(command.updatedBy(), command.actorActiveRole(), newRoles);
            user.setRoles(newRoles);
         }
      }

      // 4. Registrar auditoría
      user.setUpdatedBy(command.updatedBy());
      user.setUpdatedAt(LocalDateTime.now());

      // 5. Guardar
      User updated = userPersistencePort.save(user);

      // 6. Correo nuevo: cerrar sesiones e invalidar enlaces enviados al correo anterior
      if (emailChanged) {
         tokenPersistencePort.revokeRefreshTokensByUserId(user.getId());
         tokenPersistencePort.deleteVerificationTokensByUserId(user.getId());
         tokenPersistencePort.revokePasswordResetTokensByUserId(user.getId());
      }

      return mapToDto(updated);
   }

   /**
    * El correo no debe pertenecer a otro usuario, ni siquiera a uno eliminado.
    */
   private void ensureEmailIsAvailable(String email) {
      userPersistencePort.findByEmailIncludingDeleted(email).ifPresent(owner -> {
         throw new EmailAlreadyExistsException(email, owner.getDeletedAt() != null
               ? String.format("El email '%s' pertenece a un usuario eliminado. Restáuralo desde Eliminados o usa otro correo", email)
               : String.format("El email '%s' ya está registrado en el sistema", email));
      });
   }

   /** Un rol inactivo no se puede asignar; quien ya lo tiene lo conserva. */
   private void ensureNewRolesAreActive(Set<Role> newRoles, Set<Role> currentRoles) {
      newRoles.stream()
            .filter(role -> role.isInactive() && !roleNames(currentRoles).contains(role.getName()))
            .findFirst()
            .ifPresent(role -> {
               throw new IllegalArgumentException("El rol " + role.getName() + " está inactivo");
            });
   }

   private Set<String> roleNames(Set<Role> roles) {
      return roles.stream().map(Role::getName).collect(Collectors.toSet());
   }

   // ========== GET USER ==========

   @Override
   public UserDto execute(Long userId) {
      User user = userPersistencePort.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

      return mapToDto(user);
   }

   // ========== LIST USERS ==========

   @Override
   public PagedResponse<UserDto> execute(UserSearchCriteria criteria) {
      // Usar page y size del criteria
      int page = criteria.page();
      int size = criteria.size();

      // 1. Buscar usuarios con criterios
      List<User> users = userPersistencePort.findByCriteria(criteria, page, size);

      // 2. Contar total
      long total = userPersistencePort.countByCriteria(criteria);

      // 3. Mapear a DTOs
      List<UserDto> userDtos = users.stream()
            .map(this::mapToDto)
            .toList();

      // 4. Crear respuesta paginada
      return PagedResponse.of(userDtos, page, size, total);
   }

   // ========== DELETE USER (soft delete) ==========

   @Override
   public void executeDelete(Long userId, Long deletedBy, String actorActiveRole) {
      // 1. Buscar usuario
      User user = userPersistencePort.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));

      // 2. Validar protección y marcar como eliminado (también lo desactiva)
      user.ensureCanBeDeletedBy(deletedBy, actorActiveRole);
      user.markAsDeleted(deletedBy);

      // 3. Guardar
      userPersistencePort.save(user);

      // 4. Enviar email de notificación (opcional)
      try {
         emailPort.sendAccountDeactivatedEmail(
               user.getEmail(),
               user.getFullName(),
               "Eliminación solicitada"
         );
      } catch (Exception e) {
         // Log error pero no fallar
      }
   }

   // ========== RESTORE USER ==========

   @Override
   public UserDto executeRestore(Long userId, Long restoredBy, String actorActiveRole) {
      // 1. Buscar entre los eliminados (findById los excluye, por eso fallaba antes)
      User user = userPersistencePort.findDeletedById(userId)
            .orElseThrow(() -> userPersistencePort.findById(userId).isPresent()
                  ? new IllegalStateException("El usuario no está eliminado")
                  : new UserNotFoundException(userId));

      // 2. Reglas de protección: a un ADMIN solo lo restaura quien entró como ADMIN
      user.ensureCanBeRestoredBy(actorActiveRole);

      // 3. Restaurar (el dominio también lo reactiva: eliminar lo había desactivado)
      user.restore();

      // 4. Registrar auditoría
      user.setUpdatedBy(restoredBy);
      user.setUpdatedAt(LocalDateTime.now());

      // 5. Guardar
      User restored = userPersistencePort.save(user);

      // 6. Las sesiones anteriores a la eliminación no vuelven a servir: debe iniciar sesión otra vez
      tokenPersistencePort.revokeRefreshTokensByUserId(userId);

      // 7. Enviar email de notificación (opcional)
      try {
         emailPort.sendAccountActivatedEmail(user.getEmail(), user.getFullName());
      } catch (Exception e) {
         // Log error pero no fallar
      }

      return mapToDto(restored);
   }

   // ========== RESEND CREDENTIALS ==========

   @Override
   public void execute(Long userId, String plainPassword, Long actorId, String actorActiveRole) {
      // 1. Buscar usuario y validar protección
      User user = userPersistencePort.findById(userId)
            .orElseThrow(() -> new UserNotFoundException(userId));
      user.ensureCredentialsCanBeResetBy(actorId, actorActiveRole);

      // 2. Revocar sesiones: la contraseña anterior deja de ser válida
      tokenPersistencePort.revokeRefreshTokensByUserId(userId);

      // 3. Guardar la nueva contraseña
      user.changePassword(passwordEncoderPort.encode(plainPassword));
      user.setUpdatedBy(actorId);
      userPersistencePort.save(user);

      // 4. Enviar el correo. Si falla, la excepción revierte todo lo anterior
      try {
         if (user.isEmailVerified()) {
            emailPort.sendNewCredentialsEmail(user.getEmail(), user.getFullName(), plainPassword);
         } else {
            sendCredentialsWithVerification(user, plainPassword, actorId);
         }
      } catch (RuntimeException e) {
         log.error("No se pudo enviar las credenciales al usuario {}", userId, e);
         throw new IllegalStateException(
               "No se pudo enviar el correo. La contraseña anterior no se modificó", e);
      }
   }

   /**
    * Envía credenciales con un enlace de verificación nuevo, porque el anterior pudo vencer.
    */
   private void sendCredentialsWithVerification(User user, String plainPassword, Long actorId) {
      tokenPersistencePort.deleteVerificationTokensByUserId(user.getId());

      String tokenValue = java.util.UUID.randomUUID().toString();
      tokenPersistencePort.saveVerificationToken(
            new VerificationToken(tokenValue, user.getId(), actorId));

      emailPort.sendWelcomeEmailWithCredentials(
            user.getEmail(),
            user.getFullName(),
            user.getEmail(),
            plainPassword,
            roleNames(user.getRoles()),
            frontendUrl + "/verify-email?token=" + tokenValue,
            24
      );
   }

   // ========== LIST OPERATORS (NUEVO) ==========

   /**
    * Lista todos los operadores activos del sistema.
    * Un operador es un usuario con rol OPERADOR y status activo.
    *
    * @return lista de operadores disponibles
    */
   @Override
   public List<OperatorDto> listActiveOperators() {
      log.debug("Listando operadores activos");

      // Buscar usuarios con rol OPERADOR y status activo
      List<User> operators = userPersistencePort.findByRoleAndStatus("OPERADOR", true);

      List<OperatorDto> operatorDtos = operators.stream()
            .map(user -> new OperatorDto(
                  user.getId(),
                  user.getFirstName(),
                  user.getLastName(),
                  user.getEmail(),
                  user.getPhoneNumber(),
                  user.getStatus()
            ))
            .toList();

      log.debug("{} operadores activos encontrados", operatorDtos.size());

      return operatorDtos;
   }

   // ========== HELPER METHODS ==========

   private UserDto mapToDto(User user) {
      return new UserDto(
            user.getId(),
            user.getFirstName(),
            user.getLastName(),
            user.getEmail(),
            user.getPhoneNumber(),
            user.getProfilePicture(),
            user.getStatus(),
            user.isEmailVerified(),
            getRoleNames(user),
            user.getCreatedAt(),
            user.getUpdatedAt(),
            user.getDeletedAt()
      );
   }

   private Set<String> getRoleNames(User user) {
      return user.getRoles().stream()
            .map(Role::getName)
            .collect(Collectors.toSet());
   }

   private Set<String> getPermissionNames(User user) {
      return user.getRoles().stream()
            .flatMap(role -> role.getPermissions().stream())
            .map(permission -> permission.getName())
            .collect(Collectors.toSet());
   }
}