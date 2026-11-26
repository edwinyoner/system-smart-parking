package com.winnersystems.smartparking.parking.application.port.output;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.domain.model.Zone;

import java.util.List;
import java.util.Optional;

public interface ZonePersistencePort {

   Zone save(Zone zone);

   /** Zona no eliminada y de un parqueo no eliminado */
   Optional<Zone> findById(Long id);

   /** Incluye eliminadas (y de parqueos eliminados): para mostrar el historial */
   Optional<Zone> findByIdIncludingDeleted(Long id);

   /** Varias zonas por ID, incluidas las eliminadas (para mostrar nombres en listas) */
   List<Zone> findAllByIdsIncludingDeleted(java.util.Collection<Long> ids);

   /** Solo zonas eliminadas (papelera) */
   Optional<Zone> findDeletedById(Long id);

   /** Código dentro del parqueo, sin distinguir mayúsculas, también entre las eliminadas */
   Optional<Zone> findByParkingIdAndCodeIncludingDeleted(Long parkingId, String code);

   /** Nombre dentro del parqueo, sin distinguir mayúsculas, solo entre las no eliminadas */
   Optional<Zone> findByParkingIdAndName(Long parkingId, String name);

   /** Zonas no eliminadas de un parqueo, por nombre */
   List<Zone> findByParkingId(Long parkingId);

   /**
    * Zonas ACTIVE de parqueos ACTIVE (ninguno eliminado): las que reciben vehículos.
    *
    * @param parkingId null = de todos los parqueos
    */
   List<Zone> findAllActive(Long parkingId);

   /**
    * @param search    texto en nombre o código (null = sin filtro)
    * @param status    estado exacto (null = todos)
    * @param parkingId parqueo (null = todos)
    * @param deleted   true = solo eliminadas (papelera), false = vigentes de parqueos vigentes
    */
   PageResult<Zone> findAll(PageRequest pageRequest, String search, String status, Long parkingId, boolean deleted);
}
