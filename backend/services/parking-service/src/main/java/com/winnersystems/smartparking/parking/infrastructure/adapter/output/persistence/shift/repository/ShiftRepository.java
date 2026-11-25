package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.shift.repository;

import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.shift.entity.ShiftEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para Shift.
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Repository
public interface ShiftRepository extends JpaRepository<ShiftEntity, Long> {

   Optional<ShiftEntity> findByIdAndDeletedAtIsNull(Long id);

   Optional<ShiftEntity> findByCodeAndDeletedAtIsNull(String code);

   Optional<ShiftEntity> findByIdAndDeletedAtIsNotNull(Long id);

   /** Incluye los eliminados: CODE es único en toda la tabla */
   Optional<ShiftEntity> findFirstByCodeIgnoreCase(String code);

   List<ShiftEntity> findByStatusTrueAndDeletedAtIsNullOrderByStartTimeAsc();

   /**
    * Lista paginada de turnos vigentes o, con deleted = true, de la papelera.
    *
    * @param search texto en minúsculas, con %, _ y \\ escapados, a buscar en el nombre o el código (null = sin filtro)
    * @param status estado (null = todos)
    */
   @Query("""
         SELECT s FROM ShiftEntity s
         WHERE ((:deleted = false AND s.deletedAt IS NULL) OR (:deleted = true AND s.deletedAt IS NOT NULL))
           AND (:search IS NULL
                OR LOWER(s.name) LIKE CONCAT('%', :search, '%') ESCAPE '\\'
                OR LOWER(s.code) LIKE CONCAT('%', :search, '%') ESCAPE '\\')
           AND (:status IS NULL OR s.status = :status)
         """)
   Page<ShiftEntity> search(@Param("search") String search,
                            @Param("status") Boolean status,
                            @Param("deleted") boolean deleted,
                            Pageable pageable);
}
