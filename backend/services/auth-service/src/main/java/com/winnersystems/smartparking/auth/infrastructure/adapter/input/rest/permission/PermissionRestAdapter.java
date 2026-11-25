package com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.permission;

import com.winnersystems.smartparking.auth.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.auth.application.dto.query.PermissionDto;
import com.winnersystems.smartparking.auth.application.service.permission.PermissionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller para consultar permisos. Son de solo lectura: cada uno
 * corresponde a una función del código y los crea DataSeeder.
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@RestController
@RequestMapping("/permissions")
@RequiredArgsConstructor
public class PermissionRestAdapter {

   private final PermissionService permissionService;

   /**
    * GET /permissions - Lista permisos con paginación
    */
   @GetMapping
   @PreAuthorize("hasAuthority('permissions.read')")
   public ResponseEntity<PagedResponse<PermissionDto>> listPermissions(
         @RequestParam(required = false) String search,
         @RequestParam(required = false) String module,
         @RequestParam(required = false) Boolean status,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "10") int size
   ) {
      PagedResponse<PermissionDto> response = permissionService.listPermissions(search, module, status, page, size);
      return ResponseEntity.ok(response);
   }

   /**
    * GET /permissions/all - Obtener TODOS los permisos activos
    * Usado para poblar selects en formularios de roles
    */
   @GetMapping("/all")
   @PreAuthorize("hasAuthority('permissions.read')")
   public ResponseEntity<List<PermissionDto>> getAllPermissions() {
      List<PermissionDto> permissions = permissionService.getAllActivePermissions();
      return ResponseEntity.ok(permissions);
   }

   /**
    * GET /permissions/{id} - Obtener permiso por ID
    */
   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('permissions.read')")
   public ResponseEntity<PermissionDto> getPermissionById(@PathVariable Long id) {
      PermissionDto permission = permissionService.getPermissionById(id);
      return ResponseEntity.ok(permission);
   }
}
