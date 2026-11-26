package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.space;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.port.output.SpacePersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Space;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.space.entity.SpaceEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.space.mapper.SpacePersistenceMapper;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.space.repository.SpaceRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Adaptador de persistencia de Space (Oracle).
 */
@Component
public class SpacePersistenceAdapter implements SpacePersistencePort {

   /** Oracle admite como máximo 1000 valores en un IN (...) */
   private static final int IN_CHUNK = 900;

   private final SpaceRepository repository;
   private final SpacePersistenceMapper mapper;

   public SpacePersistenceAdapter(SpaceRepository repository, SpacePersistenceMapper mapper) {
      this.repository = repository;
      this.mapper = mapper;
   }

   @Override
   public Space save(Space space) {
      SpaceEntity entity;
      if (space.getId() != null) {
         // findById de JPA incluye eliminados: así también se guarda una restauración
         entity = repository.findById(space.getId())
               .orElseThrow(() -> new ResourceNotFoundException("Espacio", space.getId()));
         mapper.updateEntity(entity, space);
      } else {
         entity = mapper.toEntity(space);
      }
      return mapper.toDomain(repository.save(entity));
   }

   @Override
   public List<Space> saveAll(List<Space> spaces) {
      List<SpaceEntity> entities = spaces.stream().map(mapper::toEntity).toList();
      return repository.saveAll(entities).stream().map(mapper::toDomain).toList();
   }

   @Override
   public Optional<Space> findById(Long id) {
      return repository.findVigentById(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Space> findByIdIncludingDeleted(Long id) {
      return repository.findById(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Space> findDeletedById(Long id) {
      return repository.findByIdAndDeletedAtIsNotNull(id).map(mapper::toDomain);
   }

   @Override
   public Set<String> findExistingCodes(Long zoneId, Collection<String> codes) {
      Set<String> result = new HashSet<>();
      List<String> upper = codes.stream().map(c -> c.toUpperCase(Locale.ROOT)).distinct().toList();
      for (int i = 0; i < upper.size(); i += IN_CHUNK) {
         result.addAll(repository.findExistingCodes(zoneId, upper.subList(i, Math.min(i + IN_CHUNK, upper.size()))));
      }
      return result;
   }

   @Override
   public List<Space> findBySensorIds(Collection<String> sensorIds) {
      if (sensorIds == null || sensorIds.isEmpty()) {
         return List.of();
      }
      List<String> ids = List.copyOf(new LinkedHashSet<>(sensorIds));
      List<Space> result = new ArrayList<>();
      for (int i = 0; i < ids.size(); i += IN_CHUNK) {
         repository.findBySensorIdInAndDeletedAtIsNull(ids.subList(i, Math.min(i + IN_CHUNK, ids.size())))
               .forEach(e -> result.add(mapper.toDomain(e)));
      }
      return result;
   }

   @Override
   public List<Space> findByZoneId(Long zoneId) {
      return repository.findVigentByZoneId(zoneId).stream().map(mapper::toDomain).toList();
   }

   @Override
   public List<Space> findAvailableByZoneId(Long zoneId) {
      return repository.findAvailableByZoneId(zoneId).stream().map(mapper::toDomain).toList();
   }

   @Override
   public PageResult<Space> findAll(PageRequest pageRequest, String search, String status, String type,
                                    Long zoneId, Long parkingId, boolean deleted) {
      String text = search == null || search.isBlank() ? null : search.trim().toLowerCase();
      // %, _ y \ escritos por el usuario se buscan como texto, no como comodines
      if (text != null) {
         text = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      }
      Page<SpaceEntity> page = repository.search(text, status, type, zoneId, parkingId, deleted,
            buildPageable(pageRequest));
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
