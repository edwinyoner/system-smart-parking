package com.winnersystems.smartparking.auth.application.service.permission;

import com.winnersystems.smartparking.auth.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.auth.application.dto.query.PermissionDto;
import com.winnersystems.smartparking.auth.application.port.output.PermissionPersistencePort;
import com.winnersystems.smartparking.auth.domain.model.Permission;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Servicio de aplicación para gestión de permisos.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
@Transactional
public class PermissionService {

   private final PermissionPersistencePort permissionPersistencePort;

   /**
    * Lista permisos con paginación y filtros
    */
   public PagedResponse<PermissionDto> listPermissions(String search, String module, Boolean status, int page, int size) {
      // Obtener todos los permisos activos
      List<Permission> permissions = permissionPersistencePort.findAllActive();

      // Filtrar por búsqueda si existe
      if (search != null && !search.isBlank()) {
         String searchLower = search.toLowerCase();
         permissions = permissions.stream()
               .filter(permission ->
                     permission.getName().toLowerCase().contains(searchLower) ||
                           (permission.getDescription() != null && permission.getDescription().toLowerCase().contains(searchLower))
               )
               .collect(Collectors.toList());
      }

      // Filtrar por módulo si existe
      if (module != null && !module.isBlank()) {
         permissions = permissions.stream()
               .filter(permission -> permission.getName().startsWith(module + "."))
               .collect(Collectors.toList());
      }

      // Filtrar por estado si existe
      if (status != null) {
         permissions = permissions.stream()
               .filter(permission -> permission.isActive() == status)
               .collect(Collectors.toList());
      }

      // Calcular paginación manual
      int start = page * size;
      int end = Math.min(start + size, permissions.size());

      // Validar que start no sea mayor que el tamaño de la lista
      if (start >= permissions.size()) {
         return PagedResponse.of(List.of(), page, size, permissions.size());
      }

      List<Permission> paginatedPermissions = permissions.subList(start, end);

      // Mapear a DTOs
      List<PermissionDto> permissionDtos = paginatedPermissions.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());

      // Crear respuesta paginada usando el método estático
      return PagedResponse.of(permissionDtos, page, size, permissions.size());
   }

   /**
    * Obtiene un permiso por ID
    */
   public PermissionDto getPermissionById(Long id) {
      Permission permission = permissionPersistencePort.findById(id)
            .orElseThrow(() -> new RuntimeException("Permiso no encontrado: " + id));
      return mapToDto(permission);
   }

   /**
    * Mapea Permission (domain) a PermissionDto
    */
   private PermissionDto mapToDto(Permission permission) {
      return new PermissionDto(
            permission.getId(),
            permission.getName(),
            permission.getDescription(),
            permission.isActive(),
            permission.getCreatedAt(),
            permission.getCreatedBy(),
            permission.getUpdatedAt(),
            permission.getUpdatedBy(),
            permission.getDeletedAt(),
            permission.getDeletedBy()
      );
   }

   /**
    * Obtiene todos los permisos activos sin paginación
    * Usado para selects en formularios
    */
   public List<PermissionDto> getAllActivePermissions() {
      List<Permission> permissions = permissionPersistencePort.findAllActive();

      return permissions.stream()
            .map(this::mapToDto)
            .collect(Collectors.toList());
   }
}