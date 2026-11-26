package com.winnersystems.smartparking.parking.application.service.transaction;

import com.winnersystems.smartparking.parking.application.dto.command.ConfirmPlateReviewCommand;
import com.winnersystems.smartparking.parking.application.dto.command.DiscardPlateReviewCommand;
import com.winnersystems.smartparking.parking.application.dto.query.TransactionDetailDto;
import com.winnersystems.smartparking.parking.application.port.output.ParkingPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.PlateReviewPersistencePort;
import com.winnersystems.smartparking.parking.application.port.output.ZonePersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ActionNotAllowedException;
import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.exception.VehicleAlreadyInsideException;
import com.winnersystems.smartparking.parking.domain.model.PlateReview;
import com.winnersystems.smartparking.parking.domain.model.Transaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static com.winnersystems.smartparking.parking.application.service.transaction.TransactionFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * El operador confirma o descarta una lectura dudosa.
 */
class PlateReviewServiceTest {

   private PlateReviewPersistencePort reviews;
   private TransactionService transactions;
   private PlateReview review;
   private PlateReviewService service;
   private FakeUser user;

   @BeforeEach
   void setUp() {
      reviews = mock(PlateReviewPersistencePort.class);
      transactions = mock(TransactionService.class);
      review = PlateReview.open("cap-9", "ENTRY", 1L, 10L, "cam-01", "A8C l23", 0.41, "s3://f.jpg", NOW);
      review.setId(70L);
      when(reviews.findById(70L)).thenReturn(Optional.of(review));
      when(reviews.save(any())).thenAnswer(inv -> inv.getArgument(0));
      ParkingPersistencePort parkings = mock(ParkingPersistencePort.class);
      when(parkings.findByIdIncludingDeleted(1L)).thenReturn(Optional.of(parking(1L)));
      user = new FakeUser();
      user.authorities.add("transactions.entry");
      service = new PlateReviewService(reviews, transactions, parkings, mock(ZonePersistencePort.class),
            user, CLOCK);
   }

   @Test
   @DisplayName("confirmar: registra la entrada con la placa corregida, la captura y el operador")
   void confirmEntry() {
      TransactionDetailDto dto = mock(TransactionDetailDto.class);
      when(dto.id()).thenReturn(900L);
      when(transactions.enterVehicle(any())).thenReturn(dto);

      service.confirmReview(70L, new ConfirmPlateReviewCommand("abc-123", null, null));

      ArgumentCaptor<EntryRequest> sent = ArgumentCaptor.forClass(EntryRequest.class);
      verify(transactions).enterVehicle(sent.capture());
      assertThat(sent.getValue().plateNumber()).isEqualTo("ABC-123");
      assertThat(sent.getValue().method()).isEqualTo(Transaction.METHOD_CAMERA_AI);
      assertThat(sent.getValue().operatorId()).isEqualTo(OPERATOR);
      assertThat(sent.getValue().captureId()).isEqualTo("cap-9");
      assertThat(review.isConfirmed()).isTrue();
      assertThat(review.getTransactionId()).isEqualTo(900L);
      verify(reviews).save(review);
   }

   @Test
   @DisplayName("si el registro falla, la excepción sube y no se guarda la revisión (sigue pendiente)")
   void confirmFails() {
      when(transactions.enterVehicle(any())).thenThrow(new VehicleAlreadyInsideException("ABC-123", 5L));
      assertThatThrownBy(() -> service.confirmReview(70L, new ConfirmPlateReviewCommand("ABC-123", null, null)))
            .isInstanceOf(VehicleAlreadyInsideException.class);
      verify(reviews, never()).save(any());
   }

   @Test
   @DisplayName("confirmar una entrada exige transactions.entry (403)")
   void confirmNeedsPermission() {
      user.authorities.clear();
      assertThatThrownBy(() -> service.confirmReview(70L, new ConfirmPlateReviewCommand("ABC-123", null, null)))
            .isInstanceOf(ActionNotAllowedException.class);
      verifyNoInteractions(transactions);
   }

   @Test
   @DisplayName("descartar exige motivo; una revisión resuelta no se vuelve a resolver")
   void discard() {
      assertThatThrownBy(() -> service.discardReview(70L, new DiscardPlateReviewCommand(" ")))
            .isInstanceOf(ParkingDomainException.class);
      assertThat(service.discardReview(70L, new DiscardPlateReviewCommand("No era un vehículo")).status())
            .isEqualTo(PlateReview.STATUS_DISCARDED);
      assertThatThrownBy(() -> service.confirmReview(70L, new ConfirmPlateReviewCommand("ABC-123", null, null)))
            .isInstanceOf(ParkingDomainException.class);
   }
}
