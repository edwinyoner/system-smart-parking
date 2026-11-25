package com.winnersystems.smartparking.parking.application.service.documenttype;

import com.winnersystems.smartparking.parking.application.dto.command.CreateDocumentTypeCommand;
import com.winnersystems.smartparking.parking.application.dto.command.UpdateDocumentTypeCommand;
import com.winnersystems.smartparking.parking.application.dto.query.DocumentTypeDto;
import com.winnersystems.smartparking.parking.application.port.output.CurrentUserPort;
import com.winnersystems.smartparking.parking.application.port.output.DocumentTypePersistencePort;
import com.winnersystems.smartparking.parking.domain.exception.ResourceConflictException;
import com.winnersystems.smartparking.parking.domain.exception.ResourceNotFoundException;
import com.winnersystems.smartparking.parking.domain.model.DocumentType;
import com.winnersystems.smartparking.parking.domain.model.DocumentType.NumberRules;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class DocumentTypeServiceTest {

   private static final Long USER = 20L;

   private DocumentTypePersistencePort port;
   private DocumentTypeService service;

   @BeforeEach
   void setUp() {
      port = mock(DocumentTypePersistencePort.class);
      CurrentUserPort currentUser = mock(CurrentUserPort.class);
      when(currentUser.requireCurrentUserId()).thenReturn(USER);
      when(port.findByCodeIncludingDeleted(any())).thenReturn(Optional.empty());
      when(port.countCustomersUsing(anyLong())).thenReturn(0L);
      when(port.countTransactionsUsing(anyLong())).thenReturn(0L);
      when(port.save(any())).thenAnswer(inv -> {
         DocumentType d = inv.getArgument(0);
         if (d.getId() == null) d.setId(1L);
         return d;
      });
      service = new DocumentTypeService(port, currentUser);
   }

   private static DocumentType existing(Long id) {
      DocumentType d = DocumentType.create("DNI", "DNI", null, "1", new NumberRules(8, 8, true), true, 1L);
      d.setId(id);
      return d;
   }

   @Test
   @DisplayName("crear: activo por defecto, código en mayúsculas y auditoría del usuario")
   void create() {
      DocumentTypeDto dto = service.createDocumentType(
            new CreateDocumentTypeCommand("ruc", "RUC", null, "6", 11, 11, true, null));
      assertThat(dto.code()).isEqualTo("RUC");
      assertThat(dto.status()).isTrue();
      assertThat(dto.lengthLabel()).isEqualTo("11 dígitos");
      assertThat(dto.createdBy()).isEqualTo(USER);
   }

   @Test
   @DisplayName("crear respeta el estado inactivo")
   void createInactive() {
      DocumentTypeDto dto = service.createDocumentType(
            new CreateDocumentTypeCommand("CE", "Carné", null, null, null, null, null, false));
      assertThat(dto.status()).isFalse();
   }

   @Test
   @DisplayName("código repetido: 409, y si es de uno eliminado sugiere restaurarlo")
   void duplicatedCode() {
      DocumentType deleted = existing(2L);
      deleted.markAsDeleted(1L);
      when(port.findByCodeIncludingDeleted("DNI")).thenReturn(Optional.of(deleted));
      assertThatThrownBy(() -> service.createDocumentType(
            new CreateDocumentTypeCommand("dni", "DNI", null, null, null, null, null, true)))
            .isInstanceOf(ResourceConflictException.class)
            .hasMessageContaining("Restáuralo");
   }

   @Test
   @DisplayName("actualizar cambia reglas y SUNAT, no el código")
   void update() {
      when(port.findById(3L)).thenReturn(Optional.of(existing(3L)));
      DocumentTypeDto dto = service.updateDocumentType(3L,
            new UpdateDocumentTypeCommand("DNI nuevo", null, "1", 8, 9, false, null));
      assertThat(dto.code()).isEqualTo("DNI");
      assertThat(dto.maxLength()).isEqualTo(9);
      assertThat(dto.numericOnly()).isFalse();
      assertThat(dto.updatedBy()).isEqualTo(USER);
   }

   @Test
   @DisplayName("no se elimina si lo usan clientes o transacciones (409)")
   void deleteInUse() {
      when(port.findById(3L)).thenReturn(Optional.of(existing(3L)));
      when(port.countCustomersUsing(3L)).thenReturn(2L);
      when(port.countTransactionsUsing(3L)).thenReturn(5L);
      assertThatThrownBy(() -> service.deleteDocumentType(3L))
            .isInstanceOf(ResourceConflictException.class)
            .hasMessageContaining("2 clientes y 5 transacciones");
      verify(port, never()).save(any());
   }

   @Test
   @DisplayName("eliminar es lógico y guarda quién lo hizo")
   void delete() {
      DocumentType type = existing(3L);
      when(port.findById(3L)).thenReturn(Optional.of(type));
      service.deleteDocumentType(3L);
      assertThat(type.isDeleted()).isTrue();
      assertThat(type.getDeletedBy()).isEqualTo(USER);
      verify(port).save(type);
   }

   @Test
   @DisplayName("desactivar uno en uso está permitido")
   void deactivateInUse() {
      when(port.findById(3L)).thenReturn(Optional.of(existing(3L)));
      when(port.countCustomersUsing(3L)).thenReturn(4L);
      assertThat(service.deactivateDocumentTypeDto(3L).status()).isFalse();
   }

   @Test
   @DisplayName("restaurar: vuelve activo; uno que no está en la papelera da 404")
   void restore() {
      DocumentType type = existing(3L);
      type.markAsDeleted(1L);
      when(port.findDeletedById(3L)).thenReturn(Optional.of(type));
      assertThat(service.restoreDocumentType(3L).status()).isTrue();
      when(port.findDeletedById(4L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.restoreDocumentType(4L)).isInstanceOf(ResourceNotFoundException.class);
   }

   @Test
   @DisplayName("no encontrado: 404")
   void notFound() {
      when(port.findById(9L)).thenReturn(Optional.empty());
      assertThatThrownBy(() -> service.getDocumentTypeById(9L)).isInstanceOf(ResourceNotFoundException.class);
   }
}
