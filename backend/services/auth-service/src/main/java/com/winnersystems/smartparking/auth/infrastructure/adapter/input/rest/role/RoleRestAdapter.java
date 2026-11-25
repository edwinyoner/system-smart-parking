package com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.role;

import com.winnersystems.smartparking.auth.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.auth.application.dto.query.RoleDto;
import com.winnersystems.smartparking.auth.application.service.role.RoleService;
import com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.role.dto.request.CreateRoleRequest;
import com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.role.dto.request.UpdateRoleRequest;
import com.winnersystems.smartparking.auth.infrastructure.config.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller para gestión de roles.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@RestController
@RequestMapping("/roles")
@RequiredArgsConstructor
public class RoleRestAdapter {

   /** No basta con tener ADMIN: hay que haber iniciado sesión con ese rol */
   private static final String ACTIVE_ADMIN = "hasRole('ADMIN') and principal.activeRole == 'ADMIN'";

   private final RoleService roleService;

   /**
    * GET /roles - Lista roles con paginación
    */
   @GetMapping
   @PreAuthorize("hasAuthority('roles.read')")
   public ResponseEntity<PagedResponse<RoleDto>> listRoles(
         @RequestParam(required = false) String search,
         @RequestParam(required = false) Boolean status,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "10") int size
   ) {
      PagedResponse<RoleDto> response = roleService.listRoles(search, status, false, page, size);
      return ResponseEntity.ok(response);
   }

   /**
    * GET /roles?deleted=true - Papelera: roles eliminados.
    * Solo quien puede restaurarlos (ADMIN activo) los ve.
    */
   @GetMapping(params = "deleted=true")
   @PreAuthorize(ACTIVE_ADMIN)
   public ResponseEntity<PagedResponse<RoleDto>> listDeletedRoles(
         @RequestParam(required = false) String search,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "10") int size
   ) {
      return ResponseEntity.ok(roleService.listRoles(search, null, true, page, size));
   }

   /**
    * GET /roles/all - Obtener TODOS los roles activos
    * Usado para poblar selects en formularios de usuarios
    */
   @GetMapping("/all")
   @PreAuthorize("hasAnyAuthority('roles.read', 'users.read')")
   public ResponseEntity<List<RoleDto>> getAllRoles() {
      List<RoleDto> roles = roleService.getAllActiveRoles();
      return ResponseEntity.ok(roles);
   }

   /**
    * GET /roles/{id} - Obtener rol por ID
    */
   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('roles.read')")
   public ResponseEntity<RoleDto> getRoleById(@PathVariable Long id) {
      RoleDto role = roleService.getRoleById(id);
      return ResponseEntity.ok(role);
   }

   /**
    * POST /roles - Crear nuevo rol
    */
   @PostMapping
   @PreAuthorize(ACTIVE_ADMIN)
   public ResponseEntity<RoleDto> createRole(
         @Valid @RequestBody CreateRoleRequest request,
         @AuthenticationPrincipal CustomUserDetails actor) {
      RoleDto role = roleService.createRole(
            request.getName(),
            request.getDescription(),
            request.getStatus() != null ? request.getStatus() : true, // ✅ Default true
            request.getPermissionIds(),
            actor.getUserId()
      );
      return ResponseEntity.status(HttpStatus.CREATED).body(role);
   }

   /**
    * PUT /roles/{id} - Actualizar rol
    */
   @PutMapping("/{id}")
   @PreAuthorize(ACTIVE_ADMIN)
   public ResponseEntity<RoleDto> updateRole(
         @PathVariable Long id,
         @Valid @RequestBody UpdateRoleRequest request,
         @AuthenticationPrincipal CustomUserDetails actor
   ) {
      RoleDto role = roleService.updateRole(
            id,
            request.getName(),
            request.getDescription(),
            request.getStatus(),
            request.getPermissionIds(),
            actor.getUserId()
      );
      return ResponseEntity.ok(role);
   }

   /**
    * DELETE /roles/{id} - Eliminar rol
    */
   @DeleteMapping("/{id}")
   @PreAuthorize(ACTIVE_ADMIN)
   public ResponseEntity<Void> deleteRole(
         @PathVariable Long id,
         @AuthenticationPrincipal CustomUserDetails actor) {
      roleService.deleteRole(id, actor.getUserId());
      return ResponseEntity.noContent().build();
   }

   /**
    * POST /roles/{id}/restore - Restaurar un rol eliminado
    */
   @PostMapping("/{id}/restore")
   @PreAuthorize(ACTIVE_ADMIN)
   public ResponseEntity<RoleDto> restoreRole(
         @PathVariable Long id,
         @AuthenticationPrincipal CustomUserDetails actor) {
      return ResponseEntity.ok(roleService.restoreRole(id, actor.getUserId()));
   }
}