package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction;

import com.winnersystems.smartparking.parking.application.dto.command.ConfirmPlateReviewCommand;
import com.winnersystems.smartparking.parking.application.dto.command.DiscardPlateReviewCommand;
import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.PlateReviewDto;
import com.winnersystems.smartparking.parking.application.port.input.transaction.PlateReviewUseCase;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.request.ConfirmPlateReviewRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.request.DiscardPlateReviewRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.dto.response.TransactionDetailResponse;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.transaction.mapper.TransactionRestMapper;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Cola "Por revisar": lecturas dudosas de las cámaras que resuelve un operador.
 * Confirmar exige además transactions.entry o transactions.exit según la dirección (lo
 * comprueba el servicio) y estar de turno en ese parqueo.
 */
@RestController
@RequestMapping("/plate-reviews")
public class PlateReviewRestAdapter {

   private final PlateReviewUseCase plateReviewUseCase;
   private final TransactionRestMapper mapper;

   public PlateReviewRestAdapter(PlateReviewUseCase plateReviewUseCase, TransactionRestMapper mapper) {
      this.plateReviewUseCase = plateReviewUseCase;
      this.mapper = mapper;
   }

   /** status: PENDING (por defecto), CONFIRMED, DISCARDED o ALL */
   @GetMapping
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<PagedResponse<PlateReviewDto>> list(
         @RequestParam(defaultValue = "PENDING") String status,
         @RequestParam(required = false) Long parkingId,
         @RequestParam(defaultValue = "0") int page,
         @RequestParam(defaultValue = "20") int size) {
      String filter = "ALL".equalsIgnoreCase(status) ? null : status;
      return ResponseEntity.ok(plateReviewUseCase.listReviews(filter, parkingId, page, size));
   }

   @GetMapping("/{id}")
   @PreAuthorize("hasAuthority('transactions.view')")
   public ResponseEntity<PlateReviewDto> get(@PathVariable Long id) {
      return ResponseEntity.ok(plateReviewUseCase.getReview(id));
   }

   /** Registra la entrada o la salida con la placa confirmada; si falla, la revisión sigue pendiente */
   @PostMapping("/{id}/confirm")
   @PreAuthorize("hasAnyAuthority('transactions.entry', 'transactions.exit')")
   public ResponseEntity<TransactionDetailResponse> confirm(@PathVariable Long id,
                                                            @Valid @RequestBody ConfirmPlateReviewRequest request) {
      return ResponseEntity.ok(mapper.toDetailResponse(plateReviewUseCase.confirmReview(id,
            new ConfirmPlateReviewCommand(request.plateNumber(), request.documentTypeId(), request.documentNumber()))));
   }

   @PostMapping("/{id}/discard")
   @PreAuthorize("hasAnyAuthority('transactions.entry', 'transactions.exit')")
   public ResponseEntity<PlateReviewDto> discard(@PathVariable Long id,
                                                 @Valid @RequestBody DiscardPlateReviewRequest request) {
      return ResponseEntity.ok(plateReviewUseCase.discardReview(id, new DiscardPlateReviewCommand(request.reason())));
   }
}
