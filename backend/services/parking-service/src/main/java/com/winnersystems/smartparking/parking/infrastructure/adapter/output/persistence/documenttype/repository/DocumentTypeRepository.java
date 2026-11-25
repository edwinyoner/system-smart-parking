package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.documenttype.repository;

import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.documenttype.entity.DocumentTypeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentTypeRepository extends JpaRepository<DocumentTypeEntity, Long> {

   Optional<DocumentTypeEntity> findByIdAndDeletedAtIsNull(Long id);

   Optional<DocumentTypeEntity> findByIdAndDeletedAtIsNotNull(Long id);

   /** Código sin distinguir mayúsculas, incluidos los eliminados */
   Optional<DocumentTypeEntity> findFirstByCodeIgnoreCase(String code);

   List<DocumentTypeEntity> findByStatusTrueAndDeletedAtIsNullOrderByIdAsc();

   /**
    * Búsqueda paginada por nombre o código. search llega en minúsculas y con %, _ y \ escapados.
    * deleted = true busca en la papelera.
    */
   @Query("""
         SELECT d FROM DocumentTypeEntity d
         WHERE ((:deleted = false AND d.deletedAt IS NULL) OR (:deleted = true AND d.deletedAt IS NOT NULL))
           AND (:search IS NULL
                OR LOWER(d.name) LIKE CONCAT('%', :search, '%') ESCAPE '\\'
                OR LOWER(d.code) LIKE CONCAT('%', :search, '%') ESCAPE '\\')
           AND (:status IS NULL OR d.status = :status)
         """)
   Page<DocumentTypeEntity> search(@Param("search") String search,
                                   @Param("status") Boolean status,
                                   @Param("deleted") boolean deleted,
                                   Pageable pageable);

   /** Clientes con este tipo, también los eliminados (son historial) */
   @Query("SELECT COUNT(c) FROM CustomerEntity c WHERE c.documentTypeId = :id")
   long countCustomersUsing(@Param("id") Long id);

   /** Transacciones que lo usaron en la entrada o en la salida */
   @Query("""
         SELECT COUNT(t) FROM TransactionEntity t
         WHERE t.entryDocumentTypeId = :id OR t.exitDocumentTypeId = :id
         """)
   long countTransactionsUsing(@Param("id") Long id);
}
