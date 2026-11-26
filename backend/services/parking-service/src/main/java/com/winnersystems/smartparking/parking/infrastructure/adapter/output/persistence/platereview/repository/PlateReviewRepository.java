package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.platereview.repository;

import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.platereview.entity.PlateReviewEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PlateReviewRepository extends JpaRepository<PlateReviewEntity, Long> {

   Optional<PlateReviewEntity> findByCaptureId(String captureId);

   /** status y parkingId opcionales (null = sin filtro) */
   @Query("""
         SELECT r FROM PlateReviewEntity r
         WHERE (:status IS NULL OR r.status = :status)
           AND (:parkingId IS NULL OR r.parkingId = :parkingId)
         """)
   Page<PlateReviewEntity> search(@Param("status") String status, @Param("parkingId") Long parkingId,
                                  Pageable pageable);
}
