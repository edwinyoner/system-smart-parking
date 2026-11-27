package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.customer;

import com.winnersystems.smartparking.parking.application.dto.command.UpdateCustomerContactCommand;
import com.winnersystems.smartparking.parking.application.dto.query.*;
import com.winnersystems.smartparking.parking.application.port.input.customer.ManageCustomersUseCase;
import com.winnersystems.smartparking.parking.application.port.input.infraction.ManageInfractionsUseCase;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.customer.dto.UpdateCustomerContactRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Clientes: consulta e historial. Se crean solos en la entrada con documento;
 * aquí solo se editan sus datos de contacto.
 */
@RestController
@RequestMapping("/customers")
public class CustomerRestAdapter {

   private final ManageCustomersUseCase customers;
   private final ManageInfractionsUseCase infractions;

   public CustomerRestAdapter(ManageCustomersUseCase customers, ManageInfractionsUseCase infractions) {
      this.customers = customers;
      this.infractions = infractions;
   }

   /** @param search parte del nombre, del documento, del celular o del correo */
   @GetMapping
   @PreAuthorize("hasAuthority('customers.read')")
   public ResponseEntity<PagedResponse<CustomerDto>> list(@RequestParam(required = false) String search,
                                                         @RequestParam(defaultValue = "0") int page,
                                                         @RequestParam(defaultValue = "20") int size) {
      return ResponseEntity.ok(paged(customers.list(search, pageOf(page, size))));
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('customers.read')")
   public ResponseEntity<CustomerDetailDto> get(@PathVariable Long id) {
      return ResponseEntity.ok(customers.get(id));
   }

   @GetMapping("/{id}/stays")
   @PreAuthorize("hasAuthority('customers.read')")
   public ResponseEntity<PagedResponse<StayHistoryDto>> stays(@PathVariable Long id,
                                                             @RequestParam(defaultValue = "0") int page,
                                                             @RequestParam(defaultValue = "10") int size) {
      return ResponseEntity.ok(paged(customers.stays(id, pageOf(page, size))));
   }

   @GetMapping("/{id}/infractions")
   @PreAuthorize("hasAuthority('customers.read')")
   public ResponseEntity<PagedResponse<InfractionDto>> infractions(@PathVariable Long id,
                                                                  @RequestParam(defaultValue = "0") int page,
                                                                  @RequestParam(defaultValue = "10") int size) {
      customers.get(id);
      return ResponseEntity.ok(paged(infractions.listByCustomer(id, pageOf(page, size))));
   }

   @PatchMapping("/{id}/contact")
   @PreAuthorize("hasAuthority('customers.update')")
   public ResponseEntity<CustomerDto> updateContact(@PathVariable Long id,
                                                    @Valid @RequestBody UpdateCustomerContactRequest r) {
      return ResponseEntity.ok(customers.updateContact(id, new UpdateCustomerContactCommand(r.phone(), r.email(),
            r.address())));
   }

   static PageRequest pageOf(int page, int size) {
      return PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100));
   }

   static <T> PagedResponse<T> paged(PageResult<T> r) {
      return PagedResponse.of(r.content(), r.pageNumber(), r.pageSize(), r.totalElements());
   }
}
