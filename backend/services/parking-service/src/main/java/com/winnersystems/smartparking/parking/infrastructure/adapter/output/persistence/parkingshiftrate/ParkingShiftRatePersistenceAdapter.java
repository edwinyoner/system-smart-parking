package com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parkingshiftrate;

import com.winnersystems.smartparking.parking.application.port.output.ParkingShiftRatePersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.ParkingShiftRate;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parkingshiftrate.entity.ParkingShiftRateEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parkingshiftrate.mapper.ParkingShiftRatePersistenceMapper;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.parkingshiftrate.repository.ParkingShiftRateRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adaptador de persistencia de los horarios y tarifas de cada parqueo (Oracle).
 */
@Component
public class ParkingShiftRatePersistenceAdapter implements ParkingShiftRatePersistencePort {

   private final ParkingShiftRateRepository repository;
   private final ParkingShiftRatePersistenceMapper mapper;

   public ParkingShiftRatePersistenceAdapter(ParkingShiftRateRepository repository,
                                             ParkingShiftRatePersistenceMapper mapper) {
      this.repository = repository;
      this.mapper = mapper;
   }

   @Override
   public ParkingShiftRate save(ParkingShiftRate config) {
      ParkingShiftRateEntity entity;
      if (config.getId() != null) {
         entity = repository.findById(config.getId())
               .orElseThrow(() -> new ResourceNotFoundException("Configuración de turno", config.getId()));
         mapper.updateEntity(entity, config);
      } else {
         entity = mapper.toEntity(config);
      }
      return mapper.toDomain(repository.save(entity));
   }

   @Override
   public List<ParkingShiftRate> saveAll(List<ParkingShiftRate> configs) {
      return repository.saveAll(configs.stream().map(mapper::toEntity).toList()).stream()
            .map(mapper::toDomain)
            .toList();
   }

   @Override
   public Optional<ParkingShiftRate> findById(Long id) {
      return repository.findById(id).map(mapper::toDomain);
   }

   @Override
   public List<ParkingShiftRate> findByParkingId(Long parkingId) {
      return repository.findByParkingId(parkingId).stream().map(mapper::toDomain).toList();
   }

   @Override
   public List<ParkingShiftRate> findActiveByShiftId(Long shiftId) {
      return repository.findByShiftIdAndStatusTrue(shiftId).stream().map(mapper::toDomain).toList();
   }

   /** Borrado directo en la base (antes de insertar la configuración nueva, por la UK parqueo + turno) */
   @Override
   public void deleteByParkingId(Long parkingId) {
      repository.deleteByParkingId(parkingId);
   }

   @Override
   public void deleteById(Long id) {
      repository.deleteById(id);
   }

   @Override
   public List<String> findParkingNamesUsingShift(Long shiftId) {
      return repository.findParkingNamesUsingShift(shiftId);
   }

   @Override
   public List<String> findParkingNamesUsingRate(Long rateId) {
      return repository.findParkingNamesUsingRate(rateId);
   }
}
