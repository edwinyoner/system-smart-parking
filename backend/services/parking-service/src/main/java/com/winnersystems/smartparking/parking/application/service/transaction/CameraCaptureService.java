package com.winnersystems.smartparking.parking.application.service.transaction;

import com.winnersystems.smartparking.parking.application.dto.command.CameraCaptureCommand;
import com.winnersystems.smartparking.parking.application.dto.query.CameraCaptureResult;
import com.winnersystems.smartparking.parking.application.dto.query.TransactionDetailDto;
import com.winnersystems.smartparking.parking.application.port.input.transaction.ProcessCameraCaptureUseCase;
import com.winnersystems.smartparking.parking.application.port.output.ParkingPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.PlateReviewPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.TransactionPersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.PaymentRequiredException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.PlateNumber;
import com.winnersystems.smartparking.parking.domain.model.PlateReview;
import com.winnersystems.smartparking.parking.domain.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

/**
 * Procesa las lecturas de las cámaras de entrada y salida (las envía iot-service).
 *
 * Decide sin operador cuando puede y, si no, deja la lectura en la cola de revisión:
 * - Placa válida y confianza >= parking.ai.min-plate-confidence (0.85): registra la entrada o
 *   la salida con las mismas reglas que el operador (TransactionService).
 * - Placa dudosa o ilegible: revisión (la barrera espera al operador).
 * - Parqueo que exige documento: revisión, porque la cámara no ve el documento.
 * - Salida sin estadía en curso para esa placa: revisión (puede ser una mala lectura).
 * - Falta pagar: PAYMENT_REQUIRED con el monto, para que la pantalla de salida cobre.
 *
 * No es @Transactional a propósito: cada paso (registrar, abrir revisión) abre su propia
 * transacción. Así, si la entrada falla, se responde REJECTED sin que la transacción externa
 * quede marcada para deshacerse.
 *
 * Idempotencia: si la cámara reenvía la misma captura (se cortó la red), se responde lo mismo
 * buscando la estadía o la revisión que ya creó esa captura.
 */
@Service
public class CameraCaptureService implements ProcessCameraCaptureUseCase {

   private static final Logger log = LoggerFactory.getLogger(CameraCaptureService.class);

   private final TransactionService transactionService;
   private final TransactionPersistencePort transactionPort;
   private final PlateReviewPersistencePort reviewPort;
   private final ParkingPersistencePort parkingPort;
   private final Clock clock;
   private final double minConfidence;

   public CameraCaptureService(TransactionService transactionService,
                               TransactionPersistencePort transactionPort,
                               PlateReviewPersistencePort reviewPort,
                               ParkingPersistencePort parkingPort,
                               Clock clock,
                               @Value("${parking.ai.min-plate-confidence:0.85}") double minConfidence) {
      this.transactionService = transactionService;
      this.transactionPort = transactionPort;
      this.reviewPort = reviewPort;
      this.parkingPort = parkingPort;
      this.clock = clock;
      this.minConfidence = minConfidence;
   }

   @Override
   public CameraCaptureResult processCapture(CameraCaptureCommand command) {
      if (command == null || command.captureId() == null || command.captureId().isBlank()) {
         return CameraCaptureResult.rejected(null, "Falta el identificador de la captura");
      }
      String direction = command.direction() == null ? "" : command.direction().trim().toUpperCase();
      if (!PlateReview.DIRECTIONS.contains(direction)) {
         return CameraCaptureResult.rejected(null, "Dirección no válida: debe ser ENTRY o EXIT");
      }
      if (command.parkingId() == null) {
         return CameraCaptureResult.rejected(null, "Falta el parqueo de la cámara");
      }
      String captureId = command.captureId().trim();

      // 1. ¿Ya se procesó esta captura?
      Optional<CameraCaptureResult> previous = previousResult(captureId, direction);
      if (previous.isPresent()) {
         return previous.get();
      }

      Parking parking = parkingPort.findById(command.parkingId()).orElse(null);
      if (parking == null) {
         return CameraCaptureResult.rejected(null, "Parqueo no encontrado: " + command.parkingId());
      }

      // 2. Lectura confiable: placa con formato válido y confianza suficiente
      boolean reliable = command.confidence() != null && command.confidence() >= minConfidence
            && PlateNumber.isValid(command.plateNumber());
      if (!reliable) {
         return openReview(command, direction, "Lectura dudosa: un operador debe confirmar la placa");
      }
      String plate = PlateNumber.normalize(command.plateNumber());

      return PlateReview.DIRECTION_ENTRY.equals(direction)
            ? processEntry(command, parking, plate, captureId)
            : processExit(command, parking, plate, captureId);
   }

   // ========================= ENTRADA =========================

   private CameraCaptureResult processEntry(CameraCaptureCommand command, Parking parking, String plate,
                                            String captureId) {
      if (command.zoneId() == null) {
         return CameraCaptureResult.rejected(plate, "Falta la zona de la cámara de entrada");
      }
      if (parking.isDriverDocumentRequired()) {
         return openReview(command, PlateReview.DIRECTION_ENTRY,
               "Este parqueo exige documento: un operador debe registrar la entrada");
      }
      try {
         TransactionDetailDto entered = transactionService.enterVehicle(new EntryRequest(
               plate, parking.getId(), command.zoneId(), null,
               null, null, null, null, null, null,
               Transaction.METHOD_CAMERA_AI, null,
               captureId, command.photoUrl(), command.confidence(), null));
         return CameraCaptureResult.entered(plate, entered.id(),
               entered.space() != null ? entered.space().code() : null);
      } catch (ParkingDomainException e) {
         // Vehículo ya dentro, zona llena, parqueo cerrado o fuera de horario...
         log.info("Entrada por cámara rechazada (captura {}): {}", captureId, e.getMessage());
         return CameraCaptureResult.rejected(plate, e.getMessage());
      }
   }

   // ========================= SALIDA =========================

   private CameraCaptureResult processExit(CameraCaptureCommand command, Parking parking, String plate,
                                           String captureId) {
      Transaction active = transactionPort.findActiveByPlateNumber(plate).orElse(null);
      if (active == null || !parking.getId().equals(active.getParkingId())) {
         return openReview(command, PlateReview.DIRECTION_EXIT,
               "No hay una estadía en curso para " + plate + " en este parqueo: un operador debe revisarlo");
      }
      // Anti-robo: la cámara no ve el documento; si se entró con uno, lo revisa el operador
      if (parking.isDriverDocumentRequired() && active.hasDriverDocument()) {
         return openReview(command, PlateReview.DIRECTION_EXIT,
               "Este parqueo exige el documento de entrada: un operador debe registrar la salida");
      }

      BigDecimal due = transactionService.amountDueNow(active.getId());
      if (due.signum() > 0) {
         return CameraCaptureResult.paymentRequired(plate, active.getId(), due);
      }
      try {
         TransactionDetailDto exited = transactionService.exitVehicle(new ExitRequest(
               active.getId(), null, parking.getId(), null, null,
               Transaction.METHOD_CAMERA_AI, null,
               captureId, command.photoUrl(), command.confidence(), null));
         return CameraCaptureResult.exited(plate, exited.id());
      } catch (PaymentRequiredException e) {
         // Entre la consulta y la salida venció el tiempo para salir tras pagar
         return CameraCaptureResult.paymentRequired(plate, e.getTransactionId(), e.getAmountDue());
      } catch (ParkingDomainException e) {
         log.info("Salida por cámara rechazada (captura {}): {}", captureId, e.getMessage());
         return CameraCaptureResult.rejected(plate, e.getMessage());
      }
   }

   // ========================= REVISIÓN =========================

   private CameraCaptureResult openReview(CameraCaptureCommand command, String direction, String message) {
      try {
         PlateReview review = PlateReview.open(command.captureId().trim(), direction, command.parkingId(),
               command.zoneId(), command.deviceId(), command.plateNumber(),
               command.confidence() == null ? 0.0 : command.confidence(),
               command.photoUrl(), LocalDateTime.now(clock));
         PlateReview saved = reviewPort.save(review);
         return CameraCaptureResult.pendingReview(saved.getSuggestedPlate(), saved.getId(), message);
      } catch (ParkingDomainException e) {
         return CameraCaptureResult.rejected(null, e.getMessage());
      }
   }

   // ========================= IDEMPOTENCIA =========================

   /** Lo que se respondió la primera vez que llegó esta captura, si ya llegó */
   private Optional<CameraCaptureResult> previousResult(String captureId, String direction) {
      Optional<Transaction> done = PlateReview.DIRECTION_ENTRY.equals(direction)
            ? transactionPort.findByEntryCaptureId(captureId)
            : transactionPort.findByExitCaptureId(captureId);
      if (done.isPresent()) {
         Long transactionId = done.get().getId();
         return Optional.of(PlateReview.DIRECTION_ENTRY.equals(direction)
               ? CameraCaptureResult.entered(null, transactionId, null)
               : CameraCaptureResult.exited(null, transactionId));
      }
      return reviewPort.findByCaptureId(captureId).map(review -> {
         if (review.isPending()) {
            return CameraCaptureResult.pendingReview(review.getSuggestedPlate(), review.getId(),
                  "La lectura espera a un operador");
         }
         if (review.isDiscarded()) {
            return CameraCaptureResult.rejected(review.getConfirmedPlate(),
                  "Un operador descartó esta lectura");
         }
         // Confirmada: el operador ya registró la entrada o la salida
         return review.isEntry()
               ? CameraCaptureResult.entered(review.getConfirmedPlate(), review.getTransactionId(), null)
               : CameraCaptureResult.exited(review.getConfirmedPlate(), review.getTransactionId());
      });
   }
}
