package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.zone;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.port.output.ZonePersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Zone;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.zone.entity.ZoneEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.zone.mapper.ZonePersistenceMapper;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.zone.repository.ZoneRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador de persistencia de Zone (Oracle).
 */
@Component
public class ZonePersistenceAdapter implements ZonePersistencePort {

   private final ZoneRepository repository;
   private final ZonePersistenceMapper mapper;

   public ZonePersistenceAdapter(ZoneRepository repository, ZonePersistenceMapper mapper) {
      this.repository = repository;
      this.mapper = mapper;
   }

   @Override
   public Zone save(Zone zone) {
      ZoneEntity entity;
      if (zone.getId() != null) {
         // findById de JPA incluye eliminadas: así también se guarda una restauración
         entity = repository.findById(zone.getId())
               .orElseThrow(() -> new ResourceNotFoundException("Zona", zone.getId()));
         mapper.updateEntity(entity, zone);
      } else {
         entity = mapper.toEntity(zone);
      }
      return mapper.toDomain(repository.save(entity));
   }

   @Override
   public Optional<Zone> findById(Long id) {
      return repository.findVigentById(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Zone> findByIdIncludingDeleted(Long id) {
      return repository.findById(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Zone> findDeletedById(Long id) {
      return repository.findByIdAndDeletedAtIsNotNull(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Zone> findByParkingIdAndCodeIncludingDeleted(Long parkingId, String code) {
      if (parkingId == null || code == null || code.isBlank()) {
         return Optional.empty();
      }
      return repository.findFirstByParkingIdAndCodeIgnoreCase(parkingId, code.trim()).map(mapper::toDomain);
   }

   @Override
   public Optional<Zone> findByParkingIdAndName(Long parkingId, String name) {
      if (parkingId == null || name == null || name.isBlank()) {
         return Optional.empty();
      }
      return repository.findFirstByParkingIdAndNameIgnoreCaseAndDeletedAtIsNull(parkingId, name.trim())
            .map(mapper::toDomain);
   }

   @Override
   public List<Zone> findByParkingId(Long parkingId) {
      return repository.findByParkingIdAndDeletedAtIsNullOrderByNameAsc(parkingId).stream()
            .map(mapper::toDomain)
            .toList();
   }

   @Override
   public List<Zone> findAllActive(Long parkingId) {
      return repository.findAllActive(parkingId).stream().map(mapper::toDomain).toList();
   }

   @Override
   public PageResult<Zone> findAll(PageRequest pageRequest, String search, String status, Long parkingId,
                                   boolean deleted) {
      String text = search == null || search.isBlank() ? null : search.trim().toLowerCase();
      // %, _ y \ escritos por el usuario se buscan como texto, no como comodines
      if (text != null) {
         text = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      }
      Page<ZoneEntity> page = repository.search(text, status, parkingId, deleted, buildPageable(pageRequest));
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
