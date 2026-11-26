package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.domain.model.Space;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface SpacePersistencePort {

   Space save(Space space);

   /** Guarda varios espacios nuevos en una sola operación */
   List<Space> saveAll(List<Space> spaces);

   /** Espacio no eliminado, de una zona y un parqueo no eliminados */
   Optional<Space> findById(Long id);

   /** Incluye eliminados (y de zonas o parqueos eliminados): para mostrar el historial */
   Optional<Space> findByIdIncludingDeleted(Long id);

   /** Solo espacios eliminados (papelera) */
   Optional<Space> findDeletedById(Long id);

   /**
    * Códigos (en mayúsculas) que ya existen en la zona, también entre los eliminados.
    * Una sola consulta para validar una creación por lote.
    */
   Set<String> findExistingCodes(Long zoneId, Collection<String> codes);

   /** Espacios no eliminados que ya usan alguno de estos sensores */
   List<Space> findBySensorIds(Collection<String> sensorIds);

   /** Espacios vigentes de una zona, por código */
   List<Space> findByZoneId(Long zoneId);

   /** Espacios libres (AVAILABLE) de una zona, por código */
   List<Space> findAvailableByZoneId(Long zoneId);

   /**
    * @param search  texto en código, descripción o sensor (null = sin filtro)
    * @param status  estado exacto (null = todos)
    * @param type    tipo exacto (null = todos)
    * @param zoneId  zona (null = todas)
    * @param parkingId parqueo (null = todos)
    * @param deleted true = solo eliminados (papelera), false = vigentes de zonas y parqueos vigentes
    */
   PageResult<Space> findAll(PageRequest pageRequest, String search, String status, String type,
                             Long zoneId, Long parkingId, boolean deleted);
}
