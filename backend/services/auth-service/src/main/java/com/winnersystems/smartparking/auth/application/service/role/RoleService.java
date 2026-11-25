package com.winnersystems.smartparking.auth.application.service.role;

import com.winnersystems.smartparking.auth.application.dto.query.UserSearchCriteria;
import com.winnersystems.smartparking.auth.application.port.output.UserPersistencePort;
import com.winnersystems.smartparking.auth.domain.exception.RoleNotFoundException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Objects;
import com.winnersystems.smartparking.auth.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.auth.application.dto.query.PermissionDto;
import com.winnersystems.smartparking.auth.application.dto.query.RoleDto;
import com.winnersystems.smartparking.auth.application.port.output.PermissionPersistencePort;
import com.winnersystems.smartparking.auth.application.port.output.RolePersistencePort;
import com.winnersystems.smartparking.auth.domain.model.Permission;
import com.winnersystems.smartparking.auth.domain.model.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio de aplicación para gestión de roles.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Transactional
public class RoleService {

   private final RolePersistencePort rolePersistencePort;
   private final PermissionPersistencePort permissionPersistencePort;
   private final UserPersistencePort userPersistencePort;

   /**
    * Lista roles con paginación y filtros
    */
   public PagedResponse<RoleDto> listRoles(String search, Boolean status, int page, int size) {
      // Incluye los inactivos: si no, un rol desactivado no podría reactivarse
      List<Role> roles = rolePersistencePort.findAll().stream()
            .sorted(Comparator.comparing(Role::getId))
            .toList();

      // Filtrar por búsqueda: todas las palabras, sin distinguir tildes
      if (search != null && !search.isBlank()) {
         String[] words = searchable(search).split("\\s+");
         roles = roles.stream()
               .filter(role -> {
                  String text = searchable(role.getName() + " " + Objects.toString(role.getDescription(), ""));
                  return Arrays.stream(words).allMatch(text::contains);
               })
               .toList();
      }

      // Filtrar por estado si existe
      if (status != null) {
         roles = roles.stream()
               .filter(role -> role.isActive() == status)
               .collect(Collectors.toList());
      }

      // Calcular paginación manual
      int start = page * size;
      int end = Math.min(start + size, roles.size());

      // Validar que start no sea mayor que el tamaño de la lista
      if (start >= roles.size()) {
         return PagedResponse.of(List.of(), page, size, roles.size());
      }

      List<Role> paginatedRoles = roles.subList(start, end);

      // Mapear a DTOs
      List<RoleDto> roleDtos = paginatedRoles.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());

      // Crear respuesta paginada usando el método estático
      return PagedResponse.of(roleDtos, page, size, roles.size());
   }

   /**
    * Obtiene un rol por ID
    */
   public RoleDto getRoleById(Long id) {
      Role role = findRole(id);
      return mapToDto(role);
   }

   /**
    * Crea un nuevo rol
    */
   public RoleDto createRole(String name, String description, boolean status,
                             Set<Long> permissionIds, Long actorId) {
      String roleName = Role.normalizeName(name);
      ensureNameIsAvailable(roleName);

      Role role = new Role(roleName, description);
      if (permissionIds == null || permissionIds.isEmpty()) {
         throw new IllegalArgumentException("El rol debe tener al menos un permiso");
      }
      role.replacePermissions(loadPermissions(permissionIds, Set.of()));
      role.changeStatus(status, 0);
      role.setCreatedBy(actorId);

      return mapToDto(rolePersistencePort.save(role));
   }

   /**
    * Actualiza un rol existente. Los campos null no se modifican.
    */
   public RoleDto updateRole(Long id, String name, String description, Boolean status,
                             Set<Long> permissionIds, Long actorId) {
      Role role = findRole(id);

      if (name != null && !Role.normalizeName(name).equals(role.getName()) && !role.isSystemRole()) {
         ensureNameIsAvailable(Role.normalizeName(name));
      }
      role.updateDetails(
            name != null ? name : role.getName(),
            description != null ? description : role.getDescription());

      if (status != null) {
         role.changeStatus(status, countUsers(role));
      }

      if (permissionIds != null) {
         role.replacePermissions(loadPermissions(permissionIds, role.getPermissions()));
      }

      role.updateModifiedBy(actorId);
      return mapToDto(rolePersistencePort.save(role));
   }

   /**
    * Elimina un rol (soft delete)
    */
   public void deleteRole(Long id, Long actorId) {
      Role role = findRole(id);
      role.ensureCanBeDeleted(countUsers(role));
      role.markAsDeleted(actorId);
      rolePersistencePort.save(role);
   }

   private Role findRole(Long id) {
      return rolePersistencePort.findById(id)
            .orElseThrow(() -> new RoleNotFoundException(id));
   }

   private void ensureNameIsAvailable(String name) {
      if (rolePersistencePort.existsByName(name)) {
         throw new IllegalStateException("El nombre " + name + " ya está en uso o perteneció a un rol eliminado");
      }
   }

   /** Un permiso inactivo solo se admite si el rol ya lo tenía. */
   private Set<Permission> loadPermissions(Set<Long> ids, Set<Permission> current) {
      Set<Permission> permissions = new HashSet<>();
      for (Long permissionId : ids) {
         permissions.add(permissionPersistencePort.findById(permissionId)
               .filter(permission -> permission.isActive() || current.contains(permission))
               .orElseThrow(() -> new IllegalArgumentException("Permiso no disponible: " + permissionId)));
      }
      return permissions;
   }

   private long countUsers(Role role) {
      return userPersistencePort.countByCriteria(
            new UserSearchCriteria(null, role.getId(), null, null, 0, 1, null, null));
   }

   /** Minúsculas y sin tildes, para comparar textos. */
   private static String searchable(String value) {
      return Normalizer.normalize(value.strip().toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
            .replaceAll("\\p{M}", "");
   }

   /**
    * Mapea Role (domain) a RoleDto
    */
   private RoleDto mapToDto(Role role) {
      Set<PermissionDto> permissionDtos = role.getPermissions().stream()
            .map(p -> new PermissionDto(
                  p.getId(),
                  p.getName(),
                  p.getDescription(),
                  p.isActive(),
                  p.getCreatedAt(),
                  p.getCreatedBy(),
                  p.getUpdatedAt(),
                  p.getUpdatedBy(),
                  p.getDeletedAt(),
                  p.getDeletedBy()
            ))
            .collect(Collectors.toSet());

      return new RoleDto(
            role.getId(),
            role.getName(),
            role.getDescription(),
            role.isActive(),
            role.isSystemRole(),
            role.arePermissionsEditable(),
            countUsers(role),
            permissionDtos,
            role.getCreatedAt(),
            role.getCreatedBy(),
            role.getUpdatedAt(),
            role.getUpdatedBy(),
            role.getDeletedAt(),
            role.getDeletedBy()
      );
   }

   /**
    * Nombres de los roles activos, para el selector del login (público).
    */
   public List<String> getLoginRoles() {
      return rolePersistencePort.findAllActive().stream()
            .sorted(Comparator.comparing(Role::getId))
            .map(Role::getName)
            .toList();
   }

   /**
    * Obtiene todos los roles activos sin paginación
    * Usado para selects en formularios
    */
   public List<RoleDto> getAllActiveRoles() {
      List<Role> roles = rolePersistencePort.findAllActive();

      return roles.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
   }
}