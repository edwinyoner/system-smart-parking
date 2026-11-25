package com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.role.dto.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * Request para actualizar un rol existente.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateRoleRequest {

   @Size(min = 3, max = 50, message = "El nombre debe tener entre 3 y 50 caracteres")
   @Pattern(regexp = "^\\s*[A-Za-zÑñ][A-Za-zÑñ0-9_ ]*$", message = "El nombre solo admite letras sin tilde, números, espacios y guion bajo")
   private String name; // Opcional (no se puede cambiar en roles de sistema)

   @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
   private String description;

   private Boolean status; // true=activo, false=inactivo

   private Set<Long> permissionIds; // IDs de permisos a asignar
}