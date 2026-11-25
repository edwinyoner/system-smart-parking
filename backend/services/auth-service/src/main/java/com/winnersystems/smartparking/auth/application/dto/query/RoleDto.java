package com.winnersystems.smartparking.auth.application.dto.query;

import java.time.LocalDateTime;
import java.util.Set;

/**
 * DTO que representa un Rol para respuestas.
 *
 * @param id ID del rol
 * @param name Nombre del rol
 * @param description Descripción
 * @param status Estado (activo/inactivo)
 * @param systemRole Rol del sistema: no se renombra, desactiva ni elimina
 * @param permissionsEditable Si se pueden cambiar sus permisos
 * @param userCount Usuarios que lo tienen asignado
 * @param permissions Permisos asignados
 * @param createdAt Fecha creación
 * @param createdBy Usuario creador
 * @param updatedAt Fecha actualización
 * @param updatedBy Usuario actualizador
 * @param deletedAt Fecha eliminación
 * @param deletedBy Usuario eliminador
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
public record RoleDto(
      Long id,
      String name,
      String description,
      boolean status,
      boolean systemRole,
      boolean permissionsEditable,
      long userCount,
      Set<PermissionDto> permissions,
      LocalDateTime createdAt,
      Long createdBy,
      LocalDateTime updatedAt,
      Long updatedBy,
      LocalDateTime deletedAt,
      Long deletedBy
) {
}