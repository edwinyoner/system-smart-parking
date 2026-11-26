package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.shift;

import com.winnersystems.smartparking.parking.application.dto.query.PageRequest;
import com.winnersystems.smartparking.parking.application.dto.query.PageResult;
import com.winnersystems.smartparking.parking.application.port.output.ShiftPersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.Shift;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.shift.entity.ShiftEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.shift.mapper.ShiftPersistenceMapper;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.shift.repository.ShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador de persistencia para Shift.
 * Implementa el puerto de salida ShiftPersistencePort.
 * Traduce entre DTOs puros (Application) y Spring Data (Infrastructure).
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Component
@RequiredArgsConstructor
public class ShiftPersistenceAdapter implements ShiftPersistencePort {

   private final ShiftRepository repository;
   private final ShiftPersistenceMapper mapper;

   // ========================= CREATE/UPDATE =========================

   @Override
   public Shift save(Shift shift) {
      ShiftEntity entity;

      if (shift.getId() != null) {
         // Actualizar existente
         entity = repository.findById(shift.getId())
               .orElseThrow(() -> new ResourceNotFoundException("Turno", shift.getId()));
         mapper.updateEntity(entity, shift);
      } else {
         // Crear nuevo
         entity = mapper.toEntity(shift);
      }

      ShiftEntity savedEntity = repository.save(entity);
      return mapper.toDomain(savedEntity);
   }

   // ========================= READ =========================

   @Override
   public Optional<Shift> findById(Long id) {
      return repository.findByIdAndDeletedAtIsNull(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Shift> findByCode(String code) {
      return repository.findByCodeAndDeletedAtIsNull(code).map(mapper::toDomain);
   }

   @Override
   public Optional<Shift> findByIdIncludingDeleted(Long id) {
      return repository.findById(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Shift> findDeletedById(Long id) {
      return repository.findByIdAndDeletedAtIsNotNull(id).map(mapper::toDomain);
   }

   @Override
   public Optional<Shift> findByCodeIncludingDeleted(String code) {
      return repository.findFirstByCodeIgnoreCase(code).map(mapper::toDomain);
   }

   // ========================= LIST =========================

   /** Ordenados por hora de inicio: así se muestran en la configuración de tarifas */
   @Override
   public List<Shift> findAllActive() {
      return repository.findByStatusTrueAndDeletedAtIsNullOrderByStartTimeAsc()
            .stream()
            .map(mapper::toDomain)
            .toList();
   }

   @Override
   public PageResult<Shift> findAll(PageRequest pageRequest, String search, Boolean status, boolean deleted) {
      String text = search == null || search.isBlank() ? null : search.trim().toLowerCase();
      // %, _ y \ escritos por el usuario se buscan como texto, no como comodines
      if (text != null) {
         text = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
      }
      Page<ShiftEntity> page = repository.search(text, status, deleted, buildPageable(pageRequest));

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
