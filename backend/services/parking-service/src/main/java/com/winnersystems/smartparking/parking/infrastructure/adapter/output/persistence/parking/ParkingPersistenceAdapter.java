package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parking;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.port.output.ParkingPersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Parking;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parking.entity.ParkingEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parking.mapper.ParkingPersistenceMapper;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parking.repository.ParkingRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador de persistencia de Parking (Oracle).
 */
@Component
public class ParkingPersistenceAdapter implements ParkingPersistencePort {

   private final ParkingRepository repository;
   private final ParkingPersistenceMapper mapper;

   public ParkingPersistenceAdapter(ParkingRepository repository, ParkingPersistenceMapper mapper) {
      this.repository = repository;
      this.mapper = mapper;
   }

   @Override
   public Parking save(Parking parking) {
      ParkingEntity entity;
      if (parking.getId() != null) {
         // findById de JPA incluye eliminados: así también se guarda una restauración
         entity = repository.findById(parking.getId())
               .orElseThrow(() -> new ResourceNotFoundException("Parqueo", parking.getId()));
         mapper.updateEntity(entity, parking);
      } else {
         entity = mapper.toEntity(parking);
      }
      return mapper.toDomain(repository.save(entity));
   }

   @Override
   public Optional<Parking> findById(Long id) {
      return repository.findByIdAndDeletedAtIsNull(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Parking> findByIdIncludingDeleted(Long id) {
      return repository.findById(id).map(mapper::toDomain);
   }

   @Override
   public List<Parking> findAllByIdsIncludingDeleted(java.util.Collection<Long> ids) {
      if (ids == null || ids.isEmpty()) {
         return List.of();
      }
      return repository.findAllById(ids).stream().map(mapper::toDomain).toList();
   }

   @Override
   public Optional<Parking> findDeletedById(Long id) {
      return repository.findByIdAndDeletedAtIsNotNull(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Parking> findByCodeIncludingDeleted(String code) {
      if (code == null || code.isBlank()) {
         return Optional.empty();
      }
      return repository.findFirstByCodeIgnoreCase(code.trim()).map(mapper::toDomain);
   }

   @Override
   public List<Parking> findAllActive() {
      return repository.findByStatusAndDeletedAtIsNullOrderByNameAsc(Parking.STATUS_ACTIVE).stream()
            .map(mapper::toDomain)
            .toList();
   }

   @Override
   public PageResult<Parking> findAll(PageRequest pageRequest, String search, String status, boolean deleted) {
      String text = search == null || search.isBlank() ? null : search.trim().toLowerCase();
      // %, _ y \ escritos por el usuario se buscan como texto, no como comodines
      if (text != null) {
         text = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      }
      Page<ParkingEntity> page = repository.search(text, status, deleted, buildPageable(pageRequest));
      return PageResult.of(
            page.getContent().stream().map(mapper::toDomain).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements()
      );
   }

   private Pageable buildPageable(PageRequest pageRequest) {
      if (pageRequest.hasSorting()) {
         return org.springframework.data.domain.PageRequest.of(
               pageRequest.page(),
               pageRequest.size(),
               Sort.by(Sort.Direction.fromString(pageRequest.sortDirection()), pageRequest.sortBy())
         );
      }
      return org.springframework.data.domain.PageRequest.of(pageRequest.page(), pageRequest.size());
   }
}
