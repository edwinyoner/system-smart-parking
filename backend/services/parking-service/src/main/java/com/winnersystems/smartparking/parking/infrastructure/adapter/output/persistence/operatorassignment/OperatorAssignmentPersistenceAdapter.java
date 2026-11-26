package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.operatorassignment;

import com.winnersystems.smartparking.parking.application.port.output.OperatorAssignmentPersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.OperatorAssignment;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.operatorassignment.entity.OperatorAssignmentEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.operatorassignment.mapper.OperatorAssignmentPersistenceMapper;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.operatorassignment.repository.OperatorAssignmentRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Adaptador de persistencia de las asignaciones de operadores (Oracle).
 */
@Component
public class OperatorAssignmentPersistenceAdapter implements OperatorAssignmentPersistencePort {

   private final OperatorAssignmentRepository repository;
   private final OperatorAssignmentPersistenceMapper mapper;

   public OperatorAssignmentPersistenceAdapter(OperatorAssignmentRepository repository,
                                               OperatorAssignmentPersistenceMapper mapper) {
      this.repository = repository;
      this.mapper = mapper;
   }

   @Override
   public OperatorAssignment save(OperatorAssignment assignment) {
      OperatorAssignmentEntity entity = assignment.getId() == null
            ? new OperatorAssignmentEntity()
            : repository.findById(assignment.getId())
              .orElseThrow(() -> new ResourceNotFoundException("Asignación", assignment.getId()));
      return mapper.toDomain(repository.save(mapper.copyInto(entity, assignment)));
   }

   @Override
   public Optional<OperatorAssignment> findById(Long id) {
      return repository.findById(id).map(mapper::toDomain);
   }

   @Override
   public void deleteById(Long id) {
      repository.deleteById(id);
   }

   @Override
   public List<OperatorAssignment> findAll(Long parkingId, Long zoneId, Long operatorId) {
      return repository.search(parkingId, zoneId, operatorId).stream().map(mapper::toDomain).toList();
   }

   @Override
   public List<OperatorAssignment> findByOperatorOverlapping(Long operatorId, LocalDate start, LocalDate end) {
      return repository.findByOperatorOverlapping(operatorId, start, endOrOpen(end)).stream()
            .map(mapper::toDomain).toList();
   }

   @Override
   public List<OperatorAssignment> findByParkingShiftOverlapping(Long parkingId, Long shiftId,
                                                                 LocalDate start, LocalDate end) {
      return repository.findByParkingShiftOverlapping(parkingId, shiftId, start, endOrOpen(end)).stream()
            .map(mapper::toDomain).toList();
   }

   @Override
   public long countCurrentOrFutureByParkingAndShift(Long parkingId, Long shiftId, LocalDate today) {
      return repository.countCurrentOrFuture(parkingId, shiftId, today);
   }

   private static LocalDate endOrOpen(LocalDate end) {
      return end != null ? end : OperatorAssignment.OPEN_END;
   }
}
