package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.domain.model.Shift;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;

import java.util.List;
import java.util.Optional;

/**
 * Puerto de salida para persistencia de Shift.
 *
 * @author Edwin Yoner - Winner Systems
 */
public interface ShiftPersistencePort {

   Shift save(Shift shift);

   /** Solo turnos no eliminados */
   Optional<Shift> findById(Long id);

   /** Solo turnos no eliminados */
   Optional<Shift> findByCode(String code);

   /** Solo turnos eliminados (papelera) */
   Optional<Shift> findDeletedById(Long id);

   /**
    * Busca el código también entre los eliminados: en la base de datos es único
    * para todos, así que no se puede reutilizar el de un turno eliminado.
    */
   Optional<Shift> findByCodeIncludingDeleted(String code);

   List<Shift> findAllActive();

   /**
    * @param search  texto a buscar en el nombre o el código (null = sin filtro)
    * @param status  estado (null = todos); no aplica a la papelera
    * @param deleted true = solo eliminados (papelera), false = solo vigentes
    */
   PageResult<Shift> findAll(PageRequest pageRequest, String search, Boolean status, boolean deleted);
}
