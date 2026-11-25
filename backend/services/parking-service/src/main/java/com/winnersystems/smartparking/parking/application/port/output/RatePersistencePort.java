package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.domain.model.Rate;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para persistencia de Rate.
 *
 * @author Edwin Yoner - Winner Systems
 */
public interface RatePersistencePort {

   Rate save(Rate rate);

   /** Solo tarifas no eliminadas */
   Optional<Rate> findById(Long id);

   /**
    * Incluye las eliminadas: una transacción antigua debe poder mostrar y cobrar
    * con su tarifa aunque después se haya eliminado.
    */
   Optional<Rate> findByIdIncludingDeleted(Long id);

   /** Solo tarifas eliminadas (papelera) */
   Optional<Rate> findDeletedById(Long id);

   /** Busca el nombre sin distinguir mayúsculas, también entre las eliminadas */
   Optional<Rate> findByNameIncludingDeleted(String name);

   List<Rate> findAllActive();

   /**
    * @param search  texto a buscar en el nombre (null = sin filtro)
    * @param status  estado (null = todos); no aplica a la papelera
    * @param deleted true = solo eliminadas (papelera), false = solo vigentes
    */
   PageResult<Rate> findAll(PageRequest pageRequest, String search, Boolean status, boolean deleted);
}
