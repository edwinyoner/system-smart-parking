package com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.documenttype.mapper;

import com.winnersystems.smartparking.parking.application.dto.command.CreateDocumentTypeCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateDocumentTypeCommand;
import com.winnersystems.smartparking.parking.application.dto.query.DocumentTypeDto;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.documenttype.dto.request.CreateDocumentTypeRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.documenttype.dto.request.UpdateDocumentTypeRequest;
import com.winnersystems.smartparking.parking.infrastructure.adapter.input.rest.documenttype.dto.response.DocumentTypeResponse;
import org.springframework.stereotype.Component;

@Component
public class DocumentTypeRestMapper {

   public CreateDocumentTypeCommand toCommand(CreateDocumentTypeRequest r) {
      return new CreateDocumentTypeCommand(r.getCode(), r.getName(), r.getDescription(), r.getSunatCode(),
            r.getMinLength(), r.getMaxLength(), r.getNumericOnly(), r.getStatus());
   }

   public UpdateDocumentTypeCommand toCommand(UpdateDocumentTypeRequest r) {
      return new UpdateDocumentTypeCommand(r.getName(), r.getDescription(), r.getSunatCode(),
            r.getMinLength(), r.getMaxLength(), r.getNumericOnly(), r.getStatus());
   }

   public DocumentTypeResponse toResponse(DocumentTypeDto d) {
      return new DocumentTypeResponse(
            d.id(), d.code(), d.name(), d.description(), d.sunatCode(),
            d.minLength(), d.maxLength(), d.numericOnly(), d.lengthLabel(),
            d.status(), d.customersCount(), d.transactionsCount(), d.inUse(),
            d.createdAt(), d.createdBy(), d.updatedAt(), d.updatedBy(), d.deletedAt(), d.deletedBy());
   }
}
