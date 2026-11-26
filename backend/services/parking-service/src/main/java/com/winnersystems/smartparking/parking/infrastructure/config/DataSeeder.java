package com.winnersystems.smartparking.parking.infrastructure.config;

import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.documenttype.entity.DocumentTypeEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.documenttype.repository.DocumentTypeRepository;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.rate.entity.RateEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.rate.repository.RateRepository;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.shift.entity.ShiftEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.shift.repository.ShiftRepository;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.zone.entity.ZoneEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.zone.repository.ZoneRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

/**
 * Cargador de datos iniciales para el sistema de parking.
 *
 * Datos basados en la Ordenanza Municipal N° 011-MPH de Huaraz.
 *
 * Carga:
 * - Tipos de documento (DNI, CE, Pasaporte). Sin RUC: no se emiten facturas (comprobante interno)
 * (Los tipos de pago se cargan en payment-service)
 * - Turnos (Mañana, Tarde, Noche)
 * - Tarifas (Estándar, Nocturna, Preferencial)
 * - Relación Zona-Turno-Tarifa
 *
 * NOTA: Parking, Zones y Spaces se crean mediante el STEPPER de configuración.
 *
 * @author Edwin Yoner - Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Component
@Slf4j
public class DataSeeder implements CommandLineRunner {

   private final DocumentTypeRepository documentTypeRepository;
   private final ShiftRepository shiftRepository;
   private final RateRepository rateRepository;

   public DataSeeder(
         DocumentTypeRepository documentTypeRepository,
         ShiftRepository shiftRepository,
         RateRepository rateRepository) {
      this.documentTypeRepository = documentTypeRepository;
      this.shiftRepository = shiftRepository;
      this.rateRepository = rateRepository;
   }

   @Override
   public void run(String... args) {
      log.info("========================================");
      log.info("INICIANDO CARGA DE DATOS INICIALES");
      log.info("========================================");

      seedDocumentTypes();
      seedShifts();
      seedRates();

      log.info("========================================");
      log.info("CARGA DE DATOS BASE COMPLETADA");
      log.info("========================================");
      log.info("NOTA: Parking, Zones y Spaces se crean");
      log.info("      mediante el STEPPER de configuración");
   }

   // ========================= TIPOS DE DOCUMENTO =========================

   private void seedDocumentTypes() {
      if (documentTypeRepository.count() > 0) {
         log.info("Tipos de documento ya existen, saltando...");
         return;
      }

      log.info("→ Cargando tipos de documento...");

      // Reglas del número y código SUNAT (Catálogo N.° 06) para el comprobante
      documentTypeRepository.save(documentType("DNI", "Documento Nacional de Identidad",
            "Documento de identidad de ciudadanos peruanos", "1", 8, 8, true));

      documentTypeRepository.save(documentType("CE", "Carné de Extranjería",
            "Documento de extranjeros residentes en el Perú", "4", 9, 12, false));

      documentTypeRepository.save(documentType("PASSPORT", "Pasaporte",
            "Documento de viaje internacional", "7", 6, 12, false));

      log.info("3 tipos de documento cargados");
   }

   private static DocumentTypeEntity documentType(String code, String name, String description, String sunatCode,
                                                  int minLength, int maxLength, boolean numericOnly) {
      return DocumentTypeEntity.builder()
            .code(code)
            .name(name)
            .description(description)
            .sunatCode(sunatCode)
            .minLength(minLength)
            .maxLength(maxLength)
            .numericOnly(numericOnly)
            .status(true)
            .build();
   }

   // ========================= TURNOS =========================

   private void seedShifts() {
      if (shiftRepository.count() > 0) {
         log.info("Turnos ya existen, saltando...");
         return;
      }

      log.info("→ Cargando turnos...");

      // El minuto final se incluye en el turno: terminan en :59 para que turnos seguidos
      // no compartan un minuto (06:00-12:00 y 12:00-18:00 se solaparían a las 12:00)

      shiftRepository.save(ShiftEntity.builder()
            .code("MORNING")
            .name("Mañana")
            .startTime(LocalTime.of(6, 0))
            .endTime(LocalTime.of(11, 59))
            .status(true)
            .description("Turno matutino - 06:00 a 11:59")
            .build());

      shiftRepository.save(ShiftEntity.builder()
            .code("AFTERNOON")
            .name("Tarde")
            .startTime(LocalTime.of(12, 0))
            .endTime(LocalTime.of(17, 59))
            .status(true)
            .description("Turno vespertino - 12:00 a 17:59")
            .build());

      shiftRepository.save(ShiftEntity.builder()
            .code("NIGHT")
            .name("Noche")
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(5, 59))
            .status(true)
            .description("Turno nocturno - 18:00 a 05:59 (cruza la medianoche)")
            .build());

      log.info("3 turnos cargados");
   }

   // ========================= TARIFAS =========================

   private void seedRates() {
      if (rateRepository.count() > 0) {
         log.info("Tarifas ya existen, saltando...");
         return;
      }

      log.info("→ Cargando tarifas...");

      // Por hora o fracción, 5 min sin costo (entró por error) y 5 min de tolerancia:
      // 4 min = gratis; 5 min a 1 h 05 = 1 h; 1 h 06 = 2 h
      rateRepository.save(rate("Tarifa Estándar",
            "Tarifa estándar por hora o fracción",
            "2.50", 60, 0, 5, 5));

      rateRepository.save(rate("Tarifa Nocturna",
            "Tarifa del turno nocturno, por hora o fracción",
            "5.00", 60, 0, 5, 5));

      rateRepository.save(rate("Tarifa Preferencial",
            "Tarifa con descuento para personas con discapacidad y adultos mayores (50%)",
            "1.25", 60, 0, 5, 5));

      log.info("3 tarifas cargadas");
   }

   /** Tarifa activa en soles con sus reglas de cobro (ver Rate) */
   private static RateEntity rate(String name, String description, String amount,
                                  int unitMinutes, int minimumMinutes, int graceMinutes, int toleranceMinutes) {
      return RateEntity.builder()
            .name(name)
            .description(description)
            .amount(new BigDecimal(amount))
            .currency("PEN")
            .billingUnitMinutes(unitMinutes)
            .minimumMinutes(minimumMinutes)
            .graceMinutes(graceMinutes)
            .toleranceMinutes(toleranceMinutes)
            .status(true)
            .build();
   }
}
