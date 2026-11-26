package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.zone.repository;

import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.zone.entity.ZoneEntity;
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
public interface ZoneRepository extends JpaRepository<ZoneEntity, Long> {

   /** Zona vigente de un parqueo vigente */
   @Query("""
         SELECT z FROM ZoneEntity z
         WHERE z.id = :id AND z.deletedAt IS NULL
           AND EXISTS (SELECT 1 FROM ParkingEntity p WHERE p.id = z.parkingId AND p.deletedAt IS NULL)
         """)
   Optional<ZoneEntity> findVigentById(@Param("id") Long id);

   Optional<ZoneEntity> findByIdAndDeletedAtIsNotNull(Long id);

   /** Incluye eliminadas: el código sigue reservado en la papelera */
   Optional<ZoneEntity> findFirstByParkingIdAndCodeIgnoreCase(Long parkingId, String code);

   Optional<ZoneEntity> findFirstByParkingIdAndNameIgnoreCaseAndDeletedAtIsNull(Long parkingId, String name);

   List<ZoneEntity> findByParkingIdAndDeletedAtIsNullOrderByNameAsc(Long parkingId);

   /** Zonas ACTIVE de parqueos ACTIVE, ninguno eliminado. parkingId null = todos */
   @Query("""
         SELECT z FROM ZoneEntity z
         WHERE z.deletedAt IS NULL AND z.status = 'ACTIVE'
           AND (:parkingId IS NULL OR z.parkingId = :parkingId)
           AND EXISTS (SELECT 1 FROM ParkingEntity p
                       WHERE p.id = z.parkingId AND p.deletedAt IS NULL AND p.status = 'ACTIVE')
         ORDER BY z.name ASC
         """)
   List<ZoneEntity> findAllActive(@Param("parkingId") Long parkingId);

   /**
    * Lista paginada. deleted = false: zonas vigentes de parqueos vigentes.
    * deleted = true: zonas eliminadas (papelera), aunque su parqueo también lo esté.
    *
    * @param search texto en minúsculas, con %, _ y \\ escapados (null = sin filtro)
    */
   @Query("""
         SELECT z FROM ZoneEntity z
         WHERE ((:deleted = false AND z.deletedAt IS NULL
                 AND EXISTS (SELECT 1 FROM ParkingEntity p WHERE p.id = z.parkingId AND p.deletedAt IS NULL))
                OR (:deleted = true AND z.deletedAt IS NOT NULL))
           AND (:parkingId IS NULL OR z.parkingId = :parkingId)
           AND (:search IS NULL
                OR LOWER(z.name) LIKE CONCAT('%', :search, '%') ESCAPE '\\'
                OR LOWER(z.code) LIKE CONCAT('%', :search, '%') ESCAPE '\\')
           AND (:status IS NULL OR z.status = :status)
         """)
   Page<ZoneEntity> search(@Param("search") String search,
                           @Param("status") String status,
                           @Param("parkingId") Long parkingId,
                           @Param("deleted") boolean deleted,
                           Pageable pageable);

   /**
    * Espacios no eliminados por zona y estado: filas [zoneId, estadoEspacio, cantidad].
    */
   @Query("""
         SELECT s.zoneId, s.status, COUNT(s) FROM SpaceEntity s
         WHERE s.zoneId IN :zoneIds AND s.deletedAt IS NULL
         GROUP BY s.zoneId, s.status
         """)
   List<Object[]> countSpacesByZone(@Param("zoneIds") Collection<Long> zoneIds);
}
