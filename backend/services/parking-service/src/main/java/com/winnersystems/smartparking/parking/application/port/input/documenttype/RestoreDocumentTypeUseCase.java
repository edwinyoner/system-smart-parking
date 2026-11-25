package com.winnersystems.smartparking.parking.application.port.input.documenttype;

import com.winnersystems.smartparking.parking.application.dto.query.DocumentTypeDto;

/** Saca un tipo de documento de la papelera; vuelve activo */
public interface RestoreDocumentTypeUseCase {
   DocumentTypeDto restoreDocumentType(Long documentTypeId);
}
