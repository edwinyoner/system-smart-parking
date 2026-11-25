package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.rate;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.port.output.RatePersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Rate;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.rate.entity.RateEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.rate.mapper.RatePersistenceMapper;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.rate.repository.RateRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador de persistencia para Rate.
 * Implementa el puerto de salida RatePersistencePort.
 * Traduce entre DTOs puros (Application) y Spring Data (Infrastructure).
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
public class RatePersistenceAdapter implements RatePersistencePort {

   private final RateRepository repository;
   private final RatePersistenceMapper mapper;

   // ========================= CREATE/UPDATE =========================

   @Override
   public Rate save(Rate rate) {
      RateEntity entity;

      if (rate.getId() != null) {
         // Actualizar existente
         entity = repository.findById(rate.getId())
               .orElseThrow(() -> new ResourceNotFoundException("Tarifa", rate.getId()));
         mapper.updateEntity(entity, rate);
      } else {
         // Crear nueva
         entity = mapper.toEntity(rate);
      }

      RateEntity savedEntity = repository.save(entity);
      return mapper.toDomain(savedEntity);
   }

   // ========================= READ =========================

   @Override
   public Optional<Rate> findById(Long id) {
      return repository.findByIdAndDeletedAtIsNull(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Rate> findByIdIncludingDeleted(Long id) {
      return repository.findById(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Rate> findDeletedById(Long id) {
      return repository.findByIdAndDeletedAtIsNotNull(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Rate> findByNameIncludingDeleted(String name) {
      return repository.findFirstByNameIgnoreCase(name).map(mapper::toDomain);
   }

   // ========================= LIST =========================

   /** De la más barata a la más cara: así se muestran al configurar un parqueo */
   @Override
   public List<Rate> findAllActive() {
      return repository.findByStatusTrueAndDeletedAtIsNullOrderByAmountAsc().stream()
            .map(mapper::toDomain)
            .toList();
   }

   @Override
   public PageResult<Rate> findAll(PageRequest pageRequest, String search, Boolean status, boolean deleted) {
      String text = search == null || search.isBlank() ? null : search.trim().toLowerCase();
      // %, _ y \ escritos por el usuario se buscan como texto, no como comodines
      if (text != null) {
         text = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      }
      Page<RateEntity> page = repository.search(text, status, deleted, buildPageable(pageRequest));
      return PageResult.of(
            page.getContent().stream().map(mapper::toDomain).toList(),
            page.getNumber(),
            page.getSize(),
            page.getTotalElements()
      );
   }

   private Pageable buildPageable(PageRequest req) {
      Sort sort = req.hasSorting()
            ? Sort.by(Sort.Direction.fromString(req.sortDirection()), req.sortBy())
            : Sort.by(Sort.Direction.ASC, "id");
      return org.springframework.data.domain.PageRequest.of(req.page(), req.size(), sort);
   }
}
