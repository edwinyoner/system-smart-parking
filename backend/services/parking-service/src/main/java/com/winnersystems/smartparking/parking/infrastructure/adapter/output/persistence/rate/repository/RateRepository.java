package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.rate.repository;

import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.rate.entity.RateEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repositorio JPA para Rate.
 */
@Repository
public interface RateRepository extends JpaRepository<RateEntity, Long> {

   Optional<RateEntity> findByIdAndDeletedAtIsNull(Long id);

   Optional<RateEntity> findByIdAndDeletedAtIsNotNull(Long id);

   /** Incluye las eliminadas: el nombre no se reutiliza */
   Optional<RateEntity> findFirstByNameIgnoreCase(String name);

   List<RateEntity> findByStatusTrueAndDeletedAtIsNullOrderByAmountAsc();

   /**
    * Lista paginada de tarifas vigentes o, con deleted = true, de la papelera.
    *
    * @param search texto en minúsculas, con %, _ y \\ escapados (null = sin filtro)
    * @param status estado (null = todos)
    */
   @Query("""
         SELECT r FROM RateEntity r
         WHERE ((:deleted = false AND r.deletedAt IS NULL) OR (:deleted = true AND r.deletedAt IS NOT NULL))
           AND (:search IS NULL OR LOWER(r.name) LIKE CONCAT('%', :search, '%') ESCAPE '\\')
           AND (:status IS NULL OR r.status = :status)
         """)
   Page<RateEntity> search(@Param("search") String search,
                           @Param("status") Boolean status,
                           @Param("deleted") boolean deleted,
                           Pageable pageable);
}
