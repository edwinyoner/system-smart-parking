package com.winnersystems.smartparking.payment.application.port.output;

import com.winnersystems.smartparking.payment.application.dto.query.PageRequest;
import com.winnersystems.smartparking.payment.application.dto.query.PageResult;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;
import com.winnersystems.smartparking.payment.domain.model.PaymentType;

import java.util.List;
import java.util.Optional;

public interface PaymentTypePersistencePort {

   PaymentType save(PaymentType paymentType);

   /** Solo vigentes (no eliminados) */
   Optional<PaymentType> findById(Long id);

   /** Solo de la papelera */
   Optional<PaymentType> findDeletedById(Long id);

   /** Código sin distinguir mayúsculas, también entre los eliminados */
   Optional<PaymentType> findByCodeIncludingDeleted(String code);

   /** Activos; con channel, solo los que se ofrecen en ese canal */
   List<PaymentType> findAllActive(PaymentChannel channel);

   /** @param deleted true = papelera; false = vigentes */
   PageResult<PaymentType> findAll(PageRequest pageRequest, String search, Boolean status, boolean deleted);
}
