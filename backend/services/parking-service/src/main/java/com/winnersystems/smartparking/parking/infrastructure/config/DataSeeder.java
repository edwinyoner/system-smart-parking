package com.winnersystems.smartparking.parking.infrastructure.config;

import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.documenttype.entity.DocumentTypeEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.documenttype.repository.DocumentTypeRepository;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.paymenttype.entity.PaymentTypeEntity;
import com.winnersystems.smartparking.parking.infrastructure.adapter.output.persistence.paymenttype.repository.PaymentTypeRepository;
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
 * - Tipos de documento (DNI, CE, Pasaporte, RUC)
 * - Tipos de pago (Efectivo, Tarjeta, QR, Yape, POS)
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
   private final PaymentTypeRepository paymentTypeRepository;
   private final ShiftRepository shiftRepository;
   private final RateRepository rateRepository;

   public DataSeeder(
         DocumentTypeRepository documentTypeRepository,
         PaymentTypeRepository paymentTypeRepository,
         ShiftRepository shiftRepository,
         RateRepository rateRepository) {
      this.documentTypeRepository = documentTypeRepository;
      this.paymentTypeRepository = paymentTypeRepository;
      this.shiftRepository = shiftRepository;
      this.rateRepository = rateRepository;
   }

   @Override
   public void run(String... args) {
      log.info("========================================");
      log.info("INICIANDO CARGA DE DATOS INICIALES");
      log.info("========================================");

      seedDocumentTypes();
      seedPaymentTypes();
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

      documentTypeRepository.save(DocumentTypeEntity.builder()
            .code("DNI")
            .name("Documento Nacional de Identidad")
            .description("Documento de identidad peruano para ciudadanos (8 dígitos)")
            .status(true)
            .build());

      documentTypeRepository.save(DocumentTypeEntity.builder()
            .code("CE")
            .name("Carné de Extranjería")
            .description("Documento para extranjeros residentes en Perú (9 dígitos)")
            .status(true)
            .build());

      documentTypeRepository.save(DocumentTypeEntity.builder()
            .code("PASSPORT")
            .name("Pasaporte")
            .description("Documento de viaje internacional (formato variable)")
            .status(true)
            .build());

      documentTypeRepository.save(DocumentTypeEntity.builder()
            .code("RUC")
            .name("Registro Único de Contribuyentes")
            .description("Número de identificación tributaria (11 dígitos)")
            .status(true)
            .build());

      log.info("4 tipos de documento cargados");
   }

   // ========================= TIPOS DE PAGO =========================

   private void seedPaymentTypes() {
      if (paymentTypeRepository.count() > 0) {
         log.info("Tipos de pago ya existen, saltando...");
         return;
      }

      log.info("→ Cargando tipos de pago...");

      paymentTypeRepository.save(PaymentTypeEntity.builder()
            .code("CASH")
            .name("Efectivo")
            .description("Pago en efectivo (billetes y monedas)")
            .status(true)
            .build());

      paymentTypeRepository.save(PaymentTypeEntity.builder()
            .code("YAPE")
            .name("Yape")
            .description("Pago con app Yape del BCP")
            .status(true)
            .build());

      paymentTypeRepository.save(PaymentTypeEntity.builder()
            .code("QR")
            .name("Código QR")
            .description("Pago mediante código QR")
            .status(true)
            .build());

      paymentTypeRepository.save(PaymentTypeEntity.builder()
            .code("CARD")
            .name("Tarjeta de Crédito/Débito")
            .description("Pago con tarjeta Visa, Mastercard, etc.")
            .status(true)
            .build());

      paymentTypeRepository.save(PaymentTypeEntity.builder()
            .code("POS")
            .name("POS (Terminal)")
            .description("Pago con terminal punto de venta")
            .status(true)
            .build());

      log.info("5 tipos de pago cargados");
   }

   // ========================= TURNOS =========================

   private void seedShifts() {
      if (shiftRepository.count() > 0) {
         log.info("Turnos ya existen, saltando...");
         return;
      }

      log.info("→ Cargando turnos...");

      shiftRepository.save(ShiftEntity.builder()
            .code("MORNING")
            .name("Mañana")
            .startTime(LocalTime.of(6, 0))
            .endTime(LocalTime.of(12, 0))
            .status(true)
            .description("Turno matutino - 6:00 AM a 12:00 PM")
            .build());

      shiftRepository.save(ShiftEntity.builder()
            .code("AFTERNOON")
            .name("Tarde")
            .startTime(LocalTime.of(12, 0))
            .endTime(LocalTime.of(18, 0))
            .status(true)
            .description("Turno vespertino - 12:00 PM a 6:00 PM")
            .build());

      shiftRepository.save(ShiftEntity.builder()
            .code("NIGHT")
            .name("Noche")
            .startTime(LocalTime.of(18, 0))
            .endTime(LocalTime.of(6, 0))
            .status(true)
            .description("Turno nocturno - 6:00 PM a 6:00 AM")
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
