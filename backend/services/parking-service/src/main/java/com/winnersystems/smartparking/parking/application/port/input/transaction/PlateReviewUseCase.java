package com.winnersystems.smartparking.parking.application.port.input.transaction;

import com.winnersystems.smartparking.parking.application.dto.command.ConfirmPlateReviewCommand;
import com.winnersystems.smartparking.parking.application.dto.command.DiscardPlateReviewCommand;
import com.winnersystems.smartparking.parking.application.dto.query.PagedResponse;
import com.winnersystems.smartparking.parking.application.dto.query.PlateReviewDto;
import com.winnersystems.smartparking.parking.application.dto.query.TransactionDetailDto;

/**
 * Puerto de entrada: cola de lecturas dudosas que resuelve un operador.
 */
public interface PlateReviewUseCase {

   /**
    * @param status    PENDING, CONFIRMED o DISCARDED (null = todas)
    * @param parkingId filtra por parqueo (null = todos)
    */
   PagedResponse<PlateReviewDto> listReviews(String status, Long parkingId, int page, int size);

   PlateReviewDto getReview(Long reviewId);

   /**
    * Confirma la placa y registra la entrada o la salida en la misma operación: si el registro
    * falla (ya está dentro, falta pagar...), la revisión sigue pendiente.
    *
    * @return la estadía creada o cerrada
    */
   TransactionDetailDto confirmReview(Long reviewId, ConfirmPlateReviewCommand command);

   PlateReviewDto discardReview(Long reviewId, DiscardPlateReviewCommand command);
}
