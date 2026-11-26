package com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.paymenttype;

import com.winnersystems.smartparking.payment.application.dto.query.PageRequest;
import com.winnersystems.smartparking.payment.application.dto.query.PageResult;
import com.winnersystems.smartparking.payment.application.port.output.PaymentTypePersistencePort;
import com.winnersystems.smartparking.payment.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.payment.domain.model.PaymentChannel;
import com.winnersystems.smartparking.payment.domain.model.PaymentType;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.paymenttype.entity.PaymentTypeEntity;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.paymenttype.mapper.PaymentTypePersistenceMapper;
import com.winnersystems.smartparking.payment.infrastructure.adapter.output.persistence.paymenttype.repository.PaymentTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class PaymentTypePersistenceAdapter implements PaymentTypePersistencePort {

   private final PaymentTypeRepository repository;
   private final PaymentTypePersistenceMapper mapper;

   @Override
   public PaymentType save(PaymentType paymentType) {
      PaymentTypeEntity entity = paymentType.getId() == null
            ? new PaymentTypeEntity()
            : repository.findById(paymentType.getId())
                  .orElseThrow(() -> new ResourceNotFoundException("Tipo de pago", paymentType.getId()));
      mapper.copyToEntity(paymentType, entity);
      return mapper.toDomain(repository.save(entity));
   }

   @Override
   public Optional<PaymentType> findById(Long id) {
      return repository.findByIdAndDeletedAtIsNull(id).map(mapper::toDomain);
   }

   @Override
   public Optional<PaymentType> findDeletedById(Long id) {
      return repository.findByIdAndDeletedAtIsNotNull(id).map(mapper::toDomain);
   }

   @Override
   public Optional<PaymentType> findByCodeIncludingDeleted(String code) {
      return repository.findFirstByCodeIgnoreCase(code.trim()).map(mapper::toDomain);
   }

   @Override
   public List<PaymentType> findAllActive(PaymentChannel channel) {
      List<PaymentTypeEntity> entities = channel == null
            ? repository.findByStatusTrueAndDeletedAtIsNullOrderByIdAsc()
            : repository.findActiveByChannel(channel);
      return entities.stream().map(mapper::toDomain).toList();
   }

   @Override
   public PageResult<PaymentType> findAll(PageRequest pageRequest, String search, Boolean status, boolean deleted) {
      String text = search == null || search.isBlank() ? null : search.trim().toLowerCase();
      // %, _, [ y \ escritos por el usuario se buscan como texto, no como comodines
      // (en SQL Server, [ también es comodín de LIKE)
      if (text != null) {
         text = text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_").replace("[", "\\[");
      }
      Page<PaymentTypeEntity> page = repository.search(text, status, deleted, buildPageable(pageRequest));
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
