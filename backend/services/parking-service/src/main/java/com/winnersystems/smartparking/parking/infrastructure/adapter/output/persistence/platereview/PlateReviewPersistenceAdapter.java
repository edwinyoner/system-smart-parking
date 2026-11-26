package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.platereview;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.port.output.PlateReviewPersistencePort;
import com.winnersystems.smartparking.parking.domain.model.PlateReview;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.platereview.entity.PlateReviewEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.platereview.mapper.PlateReviewPersistenceMapper;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.platereview.repository.PlateReviewRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Adaptador de la cola de revisión. El orden se limita a createdAt (el único que usa el panel).
 */
@Component
public class PlateReviewPersistenceAdapter implements PlateReviewPersistencePort {

   private final PlateReviewRepository repository;
   private final PlateReviewPersistenceMapper mapper;

   public PlateReviewPersistenceAdapter(PlateReviewRepository repository, PlateReviewPersistenceMapper mapper) {
      this.repository = repository;
      this.mapper = mapper;
   }

   @Override
   public PlateReview save(PlateReview review) {
      return mapper.toDomain(repository.save(mapper.toEntity(review)));
   }

   @Override
   public Optional<PlateReview> findById(Long id) {
      return repository.findById(id).map(mapper::toDomain);
   }

   @Override
   public Optional<PlateReview> findByCaptureId(String captureId) {
      return repository.findByCaptureId(captureId).map(mapper::toDomain);
   }

   @Override
   public PageResult<PlateReview> findAll(String status, Long parkingId, PageRequest pageRequest) {
      Sort sort = Sort.by("createdAt");
      sort = pageRequest.isDescending() ? sort.descending() : sort.ascending();
      Page<PlateReviewEntity> page = repository.search(status, parkingId,
            org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size(), sort));
      return PageResult.of(page.getContent().stream().map(mapper::toDomain).toList(),
            page.getNumber(), page.getSize(), page.getTotalElements());
   }
}
