package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.domain.model.Parking;

import java.util.List;
import java.util.Optional;

public interface ParkingPersistencePort {

   Parking save(Parking parking);

   /** Solo parqueos no eliminados */
   Optional<Parking> findById(Long id);

   /**
    * Incluye los eliminados: el historial (transacciones, multas) debe poder
    * mostrar el parqueo aunque después se haya eliminado.
    */
   Optional<Parking> findByIdIncludingDeleted(Long id);

   /** Varios parqueos por ID, incluidos los eliminados (para mostrar nombres en listas) */
   List<Parking> findAllByIdsIncludingDeleted(java.util.Collection<Long> ids);

   /** Solo parqueos eliminados (papelera) */
   Optional<Parking> findDeletedById(Long id);

   /** Busca el código sin distinguir mayúsculas, también entre los eliminados */
   Optional<Parking> findByCodeIncludingDeleted(String code);

   /** Parqueos ACTIVE no eliminados, por nombre */
   List<Parking> findAllActive();

   /**
    * @param search  texto en nombre, código o dirección (null = sin filtro)
    * @param status  estado exacto (null = todos); no aplica a la papelera
    * @param deleted true = solo eliminados (papelera), false = solo vigentes
    */
   PageResult<Parking> findAll(PageRequest pageRequest, String search, String status, boolean deleted);
}
