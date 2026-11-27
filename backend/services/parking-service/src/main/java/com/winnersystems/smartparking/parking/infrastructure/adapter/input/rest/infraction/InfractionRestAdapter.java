package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.infraction;

import com.winnersystems.smartparking.parking.application.dto.command.ChangeInfractionFineCommand;
import com.winnersystems.smartparking.parking.application.dto.command.EditInfractionCommand;
import com.winnersystems.smartparking.parking.application.dto.command.RegisterInfractionCommand;
import com.winnersystems.smartparking.parking.application.dto.query.*;
import com.winnersystems.smartparking.parking.application.port.input.infraction.ManageInfractionsUseCase;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.infraction.dto.request.*;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Infracciones. El cobro de la multa se hace en payment-service (concepto FINE);
 * aquí solo se registra, se consulta, se edita la multa y se resuelve.
 */
@RestController
@RequestMapping("/infractions")
public class InfractionRestAdapter {

   private final ManageInfractionsUseCase useCase;

   public InfractionRestAdapter(ManageInfractionsUseCase useCase) {
      this.useCase = useCase;
   }

   @PostMapping
   @PreAuthorize("hasAuthority('infractions.create')")
   public ResponseEntity<InfractionDto> register(@Valid @RequestBody RegisterInfractionRequest r) {
      InfractionDto dto = useCase.register(new RegisterInfractionCommand(r.parkingId(), r.zoneId(), r.spaceId(),
            r.transactionId(), r.plateNumber(), r.infractionType(), r.severity(), r.description(), r.evidence(),
            r.fineAmount(), r.fineDueDate(), r.detectedAt(), r.notes()));
      return ResponseEntity.status(HttpStatus.CREATED).body(dto);
   }

   /**
    * @param status OPEN, OVERDUE, RESOLVED, PAID, DISMISSED, WARNING o ALL (por defecto)
    * @param search parte de la placa o del código
    */
   @GetMapping
   @PreAuthorize("hasAuthority('infractions.read')")
   public ResponseEntity<PagedResponse<InfractionDto>> list(
         @RequestParam(required = false) String status,
         @RequestParam(required = false) Long parkingId,
         @RequestParam(required = false) String type,
         @RequestParam(required = false) String search,
         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size) {
      PageResult<InfractionDto> result = useCase.list(new InfractionFilter(status, parkingId, type, search, from, to),
            PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 100)));
      return ResponseEntity.ok(PagedResponse.of(result.content(), result.pageNumber(), result.pageSize(),
            result.totalElements()));
   }

   @GetMapping("/summary")
   @PreAuthorize("hasAuthority('infractions.read')")
   public ResponseEntity<InfractionSummaryDto> summary(@RequestParam(required = false) Long parkingId) {
      return ResponseEntity.ok(useCase.summary(parkingId));
   }

   @GetMapping("/transaction/{transactionId}")
   @PreAuthorize("hasAnyAuthority('infractions.read', 'transactions.view')")
   public ResponseEntity<List<InfractionDto>> byTransaction(@PathVariable Long transactionId) {
      return ResponseEntity.ok(useCase.listByTransaction(transactionId));
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('infractions.read')")
   public ResponseEntity<InfractionDto> get(@PathVariable Long id) {
      return ResponseEntity.ok(useCase.get(id));
   }

   @PutMapping("/{id}")
   @PreAuthorize("hasAuthority('infractions.update')")
   public ResponseEntity<InfractionDto> edit(@PathVariable Long id, @Valid @RequestBody EditInfractionRequest r) {
      return ResponseEntity.ok(useCase.edit(id, new EditInfractionCommand(r.infractionType(), r.severity(),
            r.description(), r.evidence(), r.notes())));
   }

   @PatchMapping("/{id}/fine")
   @PreAuthorize("hasAuthority('infractions.update')")
   public ResponseEntity<InfractionDto> changeFine(@PathVariable Long id, @Valid @RequestBody ChangeFineRequest r) {
      return ResponseEntity.ok(useCase.changeFine(id, new ChangeInfractionFineCommand(r.fineAmount(), r.dueDate())));
   }

   @PostMapping("/{id}/dismiss")
   @PreAuthorize("hasAuthority('infractions.resolve')")
   public ResponseEntity<InfractionDto> dismiss(@PathVariable Long id, @Valid @RequestBody ResolveInfractionRequest r) {
      return ResponseEntity.ok(useCase.dismiss(id, r.reason()));
   }

   @PostMapping("/{id}/warning")
   @PreAuthorize("hasAuthority('infractions.resolve')")
   public ResponseEntity<InfractionDto> closeWithWarning(@PathVariable Long id,
                                                         @Valid @RequestBody(required = false) ResolveInfractionRequest r) {
      return ResponseEntity.ok(useCase.closeWithWarning(id, r != null ? r.reason() : null));
   }
}
