package com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.paymenttype.repository;

import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.paymenttype.entity.PaymentTypeEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PaymentTypeRepository extends JpaRepository<PaymentTypeEntity, Long> {

   Optional<PaymentTypeEntity> findByIdAndDeletedAtIsNull(Long id);

   Optional<PaymentTypeEntity> findByIdAndDeletedAtIsNotNull(Long id);

   /** Código sin distinguir mayúsculas, incluidos los eliminados */
   Optional<PaymentTypeEntity> findFirstByCodeIgnoreCase(String code);

   List<PaymentTypeEntity> findByStatusTrueAndDeletedAtIsNullOrderByIdAsc();

   /** Activos que se ofrecen en un canal */
   @Query("""
         SELECT p FROM PaymentTypeEntity p
         WHERE p.status = true AND p.deletedAt IS NULL AND :channel MEMBER OF p.channels
         ORDER BY p.id
         """)
   List<PaymentTypeEntity> findActiveByChannel(@Param("channel") PaymentChannel channel);

   /**
    * Búsqueda paginada por nombre o código. search llega en minúsculas y con %, _ y \ escapados.
    * deleted = true busca en la papelera.
    */
   @Query("""
         SELECT p FROM PaymentTypeEntity p
         WHERE ((:deleted = false AND p.deletedAt IS NULL) OR (:deleted = true AND p.deletedAt IS NOT NULL))
           AND (:search IS NULL
                OR LOWER(p.name) LIKE CONCAT('%', :search, '%') ESCAPE '\\'
                OR LOWER(p.code) LIKE CONCAT('%', :search, '%') ESCAPE '\\')
           AND (:status IS NULL OR p.status = :status)
         """)
   Page<PaymentTypeEntity> search(@Param("search") String search,
                                  @Param("status") Boolean status,
                                  @Param("deleted") boolean deleted,
                                  Pageable pageable);
}
