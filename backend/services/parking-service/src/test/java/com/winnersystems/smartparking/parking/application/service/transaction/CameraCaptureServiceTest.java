package com.winnersystems.smartparking.parking.application.service.transaction;

import com.winnersystems.smartparking.parking.application.dto.command.CameraCaptureCommand;
import com.winnersystems.smartparking.parking.application.dto.query.CameraCaptureResult;
import com.winnersystems.smartparking.parking.application.dto.query.TransactionDetailDto;
import com.winnersystems.smartparking.parking.application.port.output.ParkingPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.PlateReviewPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.TransactionPersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.VehicleAlreadyInsideException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.domain.model.PlateReview;
import com.winnersystems.smartparking.parking.domain.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Optional;

import static com.winnersystems.smartparking.parking.application.service.transaction.TransactionFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * La cámara decide sola cuando la lectura es confiable y, si no, deja la lectura al operador.
 */
class CameraCaptureServiceTest {

   private TransactionService transactions;
   private TransactionPersistencePort transactionPort;
   private PlateReviewPersistencePort reviews;
   private Parking parking;
   private CameraCaptureService service;

   @BeforeEach
   void setUp() {
      transactions = mock(TransactionService.class);
      transactionPort = mock(TransactionPersistencePort.class);
      reviews = mock(PlateReviewPersistencePort.class);
      ParkingPersistencePort parkings = mock(ParkingPersistencePort.class);
      parking = parking(1L);
      when(parkings.findById(1L)).thenReturn(Optional.of(parking));
      when(transactionPort.findByEntryCaptureId(any())).thenReturn(Optional.empty());
      when(transactionPort.findByExitCaptureId(any())).thenReturn(Optional.empty());
      when(reviews.findByCaptureId(any())).thenReturn(Optional.empty());
      when(reviews.save(any())).thenAnswer(inv -> {
         PlateReview r = inv.getArgument(0);
         r.setId(70L);
         return r;
      });
      service = new CameraCaptureService(transactions, transactionPort, reviews, parkings, CLOCK, 0.85);
   }

   private CameraCaptureCommand capture(String direction, String plate, double confidence) {
      return new CameraCaptureCommand("cap-1", direction, 1L, 10L, "cam-01", plate, confidence, "s3://foto.jpg");
   }

   private TransactionDetailDto detail(Long id) {
      TransactionDetailDto dto = mock(TransactionDetailDto.class);
      when(dto.id()).thenReturn(id);
      return dto;
   }

   @Test
   @DisplayName("lectura confiable: registra la entrada por cámara, sin operador, y abre la barrera")
   void reliableEntry() {
      TransactionDetailDto dto = detail(900L);
      when(transactions.enterVehicle(any())).thenReturn(dto);

      CameraCaptureResult result = service.processCapture(capture("entry", "abc123", 0.93));

      assertThat(result.outcome()).isEqualTo(CameraCaptureResult.ENTRY_REGISTERED);
      assertThat(result.openGate()).isTrue();
      assertThat(result.transactionId()).isEqualTo(900L);
      ArgumentCaptor<EntryRequest> sent = ArgumentCaptor.forClass(EntryRequest.class);
      verify(transactions).enterVehicle(sent.capture());
      assertThat(sent.getValue().plateNumber()).isEqualTo("ABC-123");
      assertThat(sent.getValue().method()).isEqualTo(Transaction.METHOD_CAMERA_AI);
      assertThat(sent.getValue().operatorId()).isNull();
      assertThat(sent.getValue().captureId()).isEqualTo("cap-1");
   }

   @Test
   @DisplayName("confianza baja o placa sin formato: va a revisión y la barrera espera")
   void doubtfulGoesToReview() {
      CameraCaptureResult low = service.processCapture(capture("ENTRY", "ABC123", 0.60));
      assertThat(low.outcome()).isEqualTo(CameraCaptureResult.PENDING_REVIEW);
      assertThat(low.openGate()).isFalse();
      assertThat(low.reviewId()).isEqualTo(70L);

      CameraCaptureResult garbage = service.processCapture(capture("ENTRY", "AB12", 0.99));
      assertThat(garbage.outcome()).isEqualTo(CameraCaptureResult.PENDING_REVIEW);
      verify(transactions, never()).enterVehicle(any());
   }

   @Test
   @DisplayName("parqueo que exige documento: la cámara no registra, va a revisión")
   void documentRequiredGoesToReview() {
      parking.changeDriverDocumentPolicy(true);
      assertThat(service.processCapture(capture("ENTRY", "ABC123", 0.95)).outcome())
            .isEqualTo(CameraCaptureResult.PENDING_REVIEW);
      verify(transactions, never()).enterVehicle(any());
   }

   @Test
   @DisplayName("captura repetida: responde lo mismo sin registrar otra vez")
   void idempotentEntry() {
      Transaction done = activeStay(900L, 500L, 1L, 10L, 100L, 50L, NOW);
      when(transactionPort.findByEntryCaptureId("cap-1")).thenReturn(Optional.of(done));

      CameraCaptureResult result = service.processCapture(capture("ENTRY", "ABC123", 0.95));
      assertThat(result.outcome()).isEqualTo(CameraCaptureResult.ENTRY_REGISTERED);
      assertThat(result.transactionId()).isEqualTo(900L);
      verify(transactions, never()).enterVehicle(any());
   }

   @Test
   @DisplayName("regla de negocio que impide la entrada: REJECTED con el motivo")
   void rejectedEntry() {
      when(transactions.enterVehicle(any())).thenThrow(new VehicleAlreadyInsideException("ABC-123", 5L));
      CameraCaptureResult result = service.processCapture(capture("ENTRY", "ABC123", 0.95));
      assertThat(result.outcome()).isEqualTo(CameraCaptureResult.REJECTED);
      assertThat(result.openGate()).isFalse();
      assertThat(result.message()).isNotBlank();
   }

   @Test
   @DisplayName("salida con deuda: PAYMENT_REQUIRED con el monto; sin deuda: sale")
   void exitPaymentOrLeave() {
      Transaction stay = activeStay(900L, 500L, 1L, 10L, 100L, 50L, NOW.minusHours(2));
      when(transactionPort.findActiveByPlateNumber("ABC-123")).thenReturn(Optional.of(stay));
      when(transactions.amountDueNow(900L)).thenReturn(new BigDecimal("5.00"));

      CameraCaptureResult due = service.processCapture(capture("EXIT", "ABC123", 0.95));
      assertThat(due.outcome()).isEqualTo(CameraCaptureResult.PAYMENT_REQUIRED);
      assertThat(due.amountDue()).isEqualByComparingTo("5.00");
      verify(transactions, never()).exitVehicle(any());

      when(transactions.amountDueNow(900L)).thenReturn(BigDecimal.ZERO);
      TransactionDetailDto dto = detail(900L);
      when(transactions.exitVehicle(any())).thenReturn(dto);
      CameraCaptureResult left = service.processCapture(capture("EXIT", "ABC123", 0.95));
      assertThat(left.outcome()).isEqualTo(CameraCaptureResult.EXIT_REGISTERED);
      assertThat(left.openGate()).isTrue();
   }

   @Test
   @DisplayName("salida sin estadía en este parqueo: puede ser una mala lectura, va a revisión")
   void exitWithoutStay() {
      when(transactionPort.findActiveByPlateNumber("ABC-123")).thenReturn(Optional.empty());
      assertThat(service.processCapture(capture("EXIT", "ABC123", 0.95)).outcome())
            .isEqualTo(CameraCaptureResult.PENDING_REVIEW);

      Transaction elsewhere = activeStay(900L, 500L, 2L, 20L, 200L, 50L, NOW.minusHours(1));
      when(transactionPort.findActiveByPlateNumber("ABC-123")).thenReturn(Optional.of(elsewhere));
      assertThat(service.processCapture(capture("EXIT", "ABC123", 0.95)).outcome())
            .isEqualTo(CameraCaptureResult.PENDING_REVIEW);
   }

   @Test
   @DisplayName("datos incompletos: REJECTED")
   void invalidInput() {
      assertThat(service.processCapture(new CameraCaptureCommand(" ", "ENTRY", 1L, 10L, null, "ABC123", 0.9, null))
            .outcome()).isEqualTo(CameraCaptureResult.REJECTED);
      assertThat(service.processCapture(new CameraCaptureCommand("c", "SIDE", 1L, 10L, null, "ABC123", 0.9, null))
            .outcome()).isEqualTo(CameraCaptureResult.REJECTED);
   }
}
