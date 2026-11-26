package com.winnersystems.smartparking.parking.application.service.transaction;

import com.winnersystems.smartparking.parking.application.dto.command.ConfirmPlateReviewCommand;
import com.winnersystems.smartparking.parking.application.dto.command.DiscardPlateReviewCommand;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.PlateReviewDto;
import com.winnersystems.smartparking.parking.application.dto.query.TransactionDetailDto;
import com.winnersystems.smartparking.parking.application.port.input.transaction.PlateReviewUseCase;
import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.application.port.output.ParkingPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.PlateReviewPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ZonePersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ActionNotAllowedException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.PlateReview;
import com.winnersystems.smartparking.parking.domain.model.Transaction;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Cola de lecturas dudosas: el operador mira la foto y confirma la placa o descarta la lectura.
 *
 * Confirmar registra la entrada o la salida en la MISMA transacción de base de datos: si el
 * registro falla (ya está dentro, falta pagar, no está de turno...), se deshace todo y la
 * revisión sigue pendiente, con el motivo en la respuesta.
 *
 * La estadía se registra con método CAMERA_AI (conserva la captura, la foto y la confianza)
 * y con el operador que confirmó, que debe estar de turno en ese parqueo.
 */
@Service
@Transactional
public class PlateReviewService implements PlateReviewUseCase {

   private static final Set<String> STATUSES =
         Set.of(PlateReview.STATUS_PENDING, PlateReview.STATUS_CONFIRMED, PlateReview.STATUS_DISCARDED);

   private final PlateReviewPersistencePort reviewPort;
   private final TransactionService transactionService;
   private final ParkingPersistencePort parkingPort;
   private final ZonePersistencePort zonePort;
   private final CurrentUserPort currentUserPort;
   private final Clock clock;

   public PlateReviewService(PlateReviewPersistencePort reviewPort,
                             TransactionService transactionService,
                             ParkingPersistencePort parkingPort,
                             ZonePersistencePort zonePort,
                             CurrentUserPort currentUserPort,
                             Clock clock) {
      this.reviewPort = reviewPort;
      this.transactionService = transactionService;
      this.parkingPort = parkingPort;
      this.zonePort = zonePort;
      this.currentUserPort = currentUserPort;
      this.clock = clock;
   }

   // ========================= CONSULTAS =========================

   @Override
   @Transactional(readOnly = true)
   public PagedResponse<PlateReviewDto> listReviews(String status, Long parkingId, int page, int size) {
      String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase();
      if (normalized != null && !STATUSES.contains(normalized)) {
         throw new IllegalArgumentException("Estado no válido: " + status);
      }
      // Las pendientes, de la más antigua a la más nueva (la que más espera va primero)
      String direction = PlateReview.STATUS_PENDING.equals(normalized) ? "ASC" : "DESC";
      PageResult<PlateReview> result = reviewPort.findAll(normalized, parkingId,
            PageRequest.of(page, size, "createdAt", direction));
      return PagedResponse.of(result.content().stream().map(this::toDto).toList(),
            result.pageNumber(), result.pageSize(), result.totalElements());
   }

   @Override
   @Transactional(readOnly = true)
   public PlateReviewDto getReview(Long reviewId) {
      return toDto(loadReview(reviewId));
   }

   // ========================= RESOLVER =========================

   @Override
   public TransactionDetailDto confirmReview(Long reviewId, ConfirmPlateReviewCommand command) {
      if (command == null) {
         throw new IllegalArgumentException("Indica la placa");
      }
      PlateReview review = loadReview(reviewId);
      // Confirmar una entrada es registrar una entrada; confirmar una salida, una salida
      String required = review.isEntry() ? "transactions.entry" : "transactions.exit";
      if (!currentUserPort.hasAuthority(required)) {
         throw new ActionNotAllowedException("No tienes permiso para registrar "
               + (review.isEntry() ? "entradas" : "salidas"));
      }
      Long userId = currentUserPort.requireCurrentUserId();
      String plate = review.confirm(command.plateNumber(), userId, LocalDateTime.now(clock));

      TransactionDetailDto registered = review.isEntry()
            ? transactionService.enterVehicle(new EntryRequest(
                  plate, review.getParkingId(), review.getZoneId(), null,
                  command.documentTypeId(), command.documentNumber(), null, null, null, null,
                  Transaction.METHOD_CAMERA_AI, userId,
                  review.getCaptureId(), review.getPhotoUrl(), review.getConfidence(), null))
            : transactionService.exitVehicle(new ExitRequest(
                  null, plate, review.getParkingId(),
                  command.documentTypeId(), command.documentNumber(),
                  Transaction.METHOD_CAMERA_AI, userId,
                  review.getCaptureId(), review.getPhotoUrl(), review.getConfidence(), null));

      review.linkTransaction(registered.id());
      reviewPort.save(review);
      return registered;
   }

   @Override
   public PlateReviewDto discardReview(Long reviewId, DiscardPlateReviewCommand command) {
      PlateReview review = loadReview(reviewId);
      review.discard(command == null ? null : command.reason(), currentUserPort.requireCurrentUserId(),
            LocalDateTime.now(clock));
      return toDto(reviewPort.save(review));
   }

   // ========================= HELPERS =========================

   private PlateReview loadReview(Long reviewId) {
      return reviewPort.findById(reviewId)
            .orElseThrow(() -> new ResourceNotFoundException("Revisión no encontrada: " + reviewId));
   }

   private PlateReviewDto toDto(PlateReview r) {
      String parkingName = parkingPort.findByIdIncludingDeleted(r.getParkingId())
            .map(Parking::getName).orElse(null);
      String zoneName = r.getZoneId() == null ? null
            : zonePort.findByIdIncludingDeleted(r.getZoneId()).map(Zone::getName).orElse(null);
      return new PlateReviewDto(
            r.getId(), r.getCaptureId(), r.getDirection(),
            r.getParkingId(), parkingName, r.getZoneId(), zoneName, r.getDeviceId(),
            r.getDetectedPlate(), r.getSuggestedPlate(), r.getConfidence(), r.getPhotoUrl(),
            r.getStatus(), r.getConfirmedPlate(), r.getTransactionId(), r.getDiscardReason(),
            r.getResolvedBy(), r.getResolvedAt(), r.getCreatedAt());
   }
}
