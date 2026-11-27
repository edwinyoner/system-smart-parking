package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.vehicle;

import com.winnersystems.smartparking.parking.application.dto.query.*;
import com.winnersystems.smartparking.parking.application.port.input.infraction.ManageInfractionsUseCase;
import com.winnersystems.smartparking.parking.application.port.input.vehicle.ManageVehiclesUseCase;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** Vehículos: consulta e historial (solo lectura; se crean solos por placa) */
@RestController
@RequestMapping("/vehicles")
public class VehicleRestAdapter {

   private final ManageVehiclesUseCase vehicles;
   private final ManageInfractionsUseCase infractions;

   public VehicleRestAdapter(ManageVehiclesUseCase vehicles, ManageInfractionsUseCase infractions) {
      this.vehicles = vehicles;
      this.infractions = infractions;
   }

   /** @param search parte de la placa, con o sin guion */
   @GetMapping
   @PreAuthorize("hasAuthority('vehicles.read')")
   public ResponseEntity<PagedResponse<VehicleDto>> list(@RequestParam(required = false) String search,
                                                        @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) {
      return ResponseEntity.ok(paged(vehicles.list(search, pageOf(page, size))));
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('vehicles.read')")
   public ResponseEntity<VehicleDetailDto> get(@PathVariable Long id) {
      return ResponseEntity.ok(vehicles.get(id));
   }

   @GetMapping("/{id}/stays")
   @PreAuthorize("hasAuthority('vehicles.read')")
   public ResponseEntity<PagedResponse<StayHistoryDto>> stays(@PathVariable Long id,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
      return ResponseEntity.ok(paged(vehicles.stays(id, pageOf(page, size))));
   }

   @GetMapping("/{id}/infractions")
   @PreAuthorize("hasAuthority('vehicles.read')")
   public ResponseEntity<PagedResponse<InfractionDto>> infractions(@PathVariable Long id,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "10") int size) {
      vehicles.get(id);
      return ResponseEntity.ok(paged(infractions.listByVehicle(id, pageOf(page, size))));
   }

   private static PageRequest pageOf(int page, int size) {
      return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
   }

   private static <T> PagedResponse<T> paged(PageResult<T> r) {
      return PagedResponse.of(r.content(), r.pageNumber(), r.pageSize(), r.totalElements());
   }
}
