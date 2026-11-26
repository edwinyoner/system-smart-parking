package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.space.repository;

import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.space.entity.SpaceEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface SpaceRepository extends JpaRepository<SpaceEntity, Long> {

   /** Condición "vigente": el espacio, su zona y su parqueo no están eliminados */
   String VIGENT = """
         s.deletedAt IS NULL
         AND EXISTS (SELECT 1 FROM ZoneEntity z, ParkingEntity p
                     WHERE z.id = s.zoneId AND p.id = z.parkingId
                       AND z.deletedAt IS NULL AND p.deletedAt IS NULL)
         """;

   @Query("SELECT s FROM SpaceEntity s WHERE s.id = :id AND " + VIGENT)
   Optional<SpaceEntity> findVigentById(@Param("id") Long id);

   Optional<SpaceEntity> findByIdAndDeletedAtIsNotNull(Long id);

   /** Códigos ya usados en la zona (incluye eliminados: siguen reservados en la papelera) */
   @Query("SELECT UPPER(s.code) FROM SpaceEntity s WHERE s.zoneId = :zoneId AND UPPER(s.code) IN :codes")
   List<String> findExistingCodes(@Param("zoneId") Long zoneId, @Param("codes") Collection<String> codes);

   List<SpaceEntity> findBySensorIdInAndDeletedAtIsNull(Collection<String> sensorIds);

   @Query("SELECT s FROM SpaceEntity s WHERE s.zoneId = :zoneId AND " + VIGENT + " ORDER BY s.code ASC")
   List<SpaceEntity> findVigentByZoneId(@Param("zoneId") Long zoneId);

   @Query("SELECT s FROM SpaceEntity s WHERE s.zoneId = :zoneId AND s.status = 'AVAILABLE' AND "
         + VIGENT + " ORDER BY s.code ASC")
   List<SpaceEntity> findAvailableByZoneId(@Param("zoneId") Long zoneId);

   /**
    * Lista paginada. deleted = false: espacios vigentes de zonas y parqueos vigentes.
    * deleted = true: espacios eliminados (papelera).
    *
    * @param search texto en minúsculas, con %, _ y \\ escapados (null = sin filtro)
    */
   @Query("""
         SELECT s FROM SpaceEntity s
         WHERE ((:deleted = false AND s.deletedAt IS NULL
                 AND EXISTS (SELECT 1 FROM ZoneEntity z, ParkingEntity p
                             WHERE z.id = s.zoneId AND p.id = z.parkingId
                               AND z.deletedAt IS NULL AND p.deletedAt IS NULL))
                OR (:deleted = true AND s.deletedAt IS NOT NULL))
           AND (:zoneId IS NULL OR s.zoneId = :zoneId)
           AND (:parkingId IS NULL OR EXISTS (SELECT 1 FROM ZoneEntity z2
                                              WHERE z2.id = s.zoneId AND z2.parkingId = :parkingId))
           AND (:search IS NULL
                OR LOWER(s.code) LIKE CONCAT('%', :search, '%') ESCAPE '\\'
                OR LOWER(s.description) LIKE CONCAT('%', :search, '%') ESCAPE '\\'
                OR LOWER(s.sensorId) LIKE CONCAT('%', :search, '%') ESCAPE '\\')
           AND (:status IS NULL OR s.status = :status)
           AND (:type IS NULL OR s.type = :type)
         """)
   Page<SpaceEntity> search(@Param("search") String search,
                            @Param("status") String status,
                            @Param("type") String type,
                            @Param("zoneId") Long zoneId,
                            @Param("parkingId") Long parkingId,
                            @Param("deleted") boolean deleted,
                            Pageable pageable);
}
