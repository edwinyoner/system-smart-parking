package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.domain.model.PlateReview;

import java.util.Optional;

/**
 * Puerto de salida: persistencia de la cola de revisión de placas.
 */
public interface PlateReviewPersistencePort {

   PlateReview save(PlateReview review);

   Optional<PlateReview> findById(Long id);

   /** Para la idempotencia: la cámara puede reenviar la misma captura */
   Optional<PlateReview> findByCaptureId(String captureId);

   /**
    * @param status    null = todas
    * @param parkingId null = todos los parqueos
    */
   PageResult<PlateReview> findAll(String status, Long parkingId, PageRequest pageRequest);
}
