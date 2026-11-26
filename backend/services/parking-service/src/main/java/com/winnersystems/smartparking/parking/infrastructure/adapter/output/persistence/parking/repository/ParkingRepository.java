package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parking.repository;

import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parking.entity.ParkingEntity;
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
public interface ParkingRepository extends JpaRepository<ParkingEntity, Long> {

   Optional<ParkingEntity> findByIdAndDeletedAtIsNull(Long id);

   Optional<ParkingEntity> findByIdAndDeletedAtIsNotNull(Long id);

   /** Incluye eliminados (el código sigue reservado en la papelera) */
   Optional<ParkingEntity> findFirstByCodeIgnoreCase(String code);

   List<ParkingEntity> findByStatusAndDeletedAtIsNullOrderByNameAsc(String status);

   /**
    * Lista paginada de parqueos vigentes o, con deleted = true, de la papelera.
    *
    * @param search texto en minúsculas, con %, _ y \\ escapados (null = sin filtro)
    * @param status estado exacto (null = todos)
    */
   @Query("""
         SELECT p FROM ParkingEntity p
         WHERE ((:deleted = false AND p.deletedAt IS NULL) OR (:deleted = true AND p.deletedAt IS NOT NULL))
           AND (:search IS NULL
                OR LOWER(p.name) LIKE CONCAT('%', :search, '%') ESCAPE '\\'
                OR LOWER(p.code) LIKE CONCAT('%', :search, '%') ESCAPE '\\'
                OR LOWER(p.address) LIKE CONCAT('%', :search, '%') ESCAPE '\\')
           AND (:status IS NULL OR p.status = :status)
         """)
   Page<ParkingEntity> search(@Param("search") String search,
                              @Param("status") String status,
                              @Param("deleted") boolean deleted,
                              Pageable pageable);

   // ========================= CAPACIDAD (calculada) =========================

   /** Zonas no eliminadas por parqueo: filas [parkingId, cantidad] */
   @Query("""
         SELECT z.parkingId, COUNT(z) FROM ZoneEntity z
         WHERE z.parkingId IN :parkingIds AND z.deletedAt IS NULL
         GROUP BY z.parkingId
         """)
   List<Object[]> countZonesByParking(@Param("parkingIds") Collection<Long> parkingIds);

   /**
    * Espacios no eliminados de zonas no eliminadas, agrupados por parqueo, estado
    * del espacio y estado de la zona: filas [parkingId, estadoEspacio, estadoZona, cantidad].
    */
   @Query("""
         SELECT z.parkingId, s.status, z.status, COUNT(s)
         FROM SpaceEntity s, ZoneEntity z
         WHERE s.zoneId = z.id
           AND z.parkingId IN :parkingIds
           AND z.deletedAt IS NULL AND s.deletedAt IS NULL
         GROUP BY z.parkingId, s.status, z.status
         """)
   List<Object[]> countSpacesByParking(@Param("parkingIds") Collection<Long> parkingIds);
}
