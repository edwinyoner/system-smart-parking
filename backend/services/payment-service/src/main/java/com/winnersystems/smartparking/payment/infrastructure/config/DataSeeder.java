package com.winnersystems.smartparking.payment.infrastructure.config;

import com.winnersystems.smartparking.payment.domain.model.ConfirmationMode;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.paymenttype.entity.PaymentTypeEntity;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.paymenttype.repository.PaymentTypeRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.Set;

/**
 * Datos iniciales de payment-service (se cargan en cada arranque con create-drop).
 *
 * Tipos de pago acordados: QR (Yape, Plin o banca; lo confirma la pasarela) y Efectivo
 * (lo confirma un operador). Sin tarjeta ni POS.
 */
@Slf4j
@Component
public class DataSeeder implements CommandLineRunner {

   private final PaymentTypeRepository paymentTypeRepository;

   public DataSeeder(PaymentTypeRepository paymentTypeRepository) {
      this.paymentTypeRepository = paymentTypeRepository;
   }

   @Override
   @Transactional
   public void run(String... args) {
      if (paymentTypeRepository.count() > 0) {
         log.info("Tipos de pago ya existen, saltando...");
         return;
      }

      paymentTypeRepository.save(paymentType("QR", "Pago con QR",
            "Yape, Plin o la app del banco. La pasarela confirma el pago sin intervención del operador",
            ConfirmationMode.AUTOMATIC, false, EnumSet.allOf(PaymentChannel.class)));

      paymentTypeRepository.save(paymentType("CASH", "Efectivo",
            "Billetes y monedas. Solo con un operador presente, que confirma el cobro",
            ConfirmationMode.MANUAL, false, EnumSet.of(PaymentChannel.OPERATOR)));

      log.info("2 tipos de pago cargados (QR y Efectivo)");
   }

   private static PaymentTypeEntity paymentType(String code, String name, String description, ConfirmationMode mode,
                                                boolean requiresReference, Set<PaymentChannel> channels) {
      PaymentTypeEntity entity = new PaymentTypeEntity();
      entity.setCode(code);
      entity.setName(name);
      entity.setDescription(description);
      entity.setConfirmationMode(mode);
      entity.setRequiresReference(requiresReference);
      entity.setChannels(EnumSet.copyOf(channels));
      entity.setStatus(true);
      return entity;
   }
}
