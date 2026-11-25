package com.winnersystems.smartparking.parking.domain.model;

import com.winnersystems.smartparking.parking.domain.exception.ParkingDomainException;
import com.winnersystems.smartparking.parking.domain.model.DocumentType.NumberRules;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DocumentTypeTest {

   private static DocumentType dni() {
      return DocumentType.create("dni", "Documento Nacional de Identidad", null, "1",
            new NumberRules(8, 8, true), true, 1L);
   }

   @Test
   @DisplayName("crear: código en mayúsculas, reglas, SUNAT y auditoría")
   void create() {
      DocumentType type = dni();
      assertThat(type.getCode()).isEqualTo("DNI");
      assertThat(type.getSunatCode()).isEqualTo("1");
      assertThat(type.isNumericOnly()).isTrue();
      assertThat(type.describeLength()).isEqualTo("8 dígitos");
      assertThat(type.isActive()).isTrue();
      assertThat(type.getCreatedBy()).isEqualTo(1L);
   }

   @Test
   @DisplayName("reglas por defecto: 1 a 20 caracteres, letras y dígitos; SUNAT vacío queda null")
   void defaults() {
      DocumentType type = DocumentType.create("OTRO", "Otro", " ", "", NumberRules.defaults(), true, 1L);
      assertThat(type.getMinLength()).isEqualTo(1);
      assertThat(type.getMaxLength()).isEqualTo(20);
      assertThat(type.isNumericOnly()).isFalse();
      assertThat(type.getSunatCode()).isNull();
      assertThat(type.getDescription()).isNull();
      assertThat(type.describeLength()).isEqualTo("entre 1 y 20 caracteres");
   }

   @Test
   @DisplayName("normaliza el número: sin espacios ni guiones y en mayúsculas")
   void normalize() {
      assertThat(dni().normalizeNumber(" 4567-8912 ")).isEqualTo("45678912");
      DocumentType ce = DocumentType.create("CE", "Carné", null, "4", new NumberRules(9, 12, false), true, 1L);
      assertThat(ce.normalizeNumber("ab 1234567")).isEqualTo("AB1234567");
   }

   @ParameterizedTest(name = "DNI \"{0}\" se rechaza")
   @ValueSource(strings = {"", "1234567", "123456789", "1234567A", "12.345.678"})
   void invalidDni(String number) {
      assertThatThrownBy(() -> dni().normalizeNumber(number)).isInstanceOf(ParkingDomainException.class);
   }

   @Test
   @DisplayName("mensaje claro cuando la longitud no coincide")
   void lengthMessage() {
      assertThatThrownBy(() -> dni().normalizeNumber("123"))
            .hasMessage("El DNI debe tener 8 dígitos");
   }

   @Test
   @DisplayName("valida código, SUNAT y longitudes")
   void validation() {
      assertThatThrownBy(() -> DocumentType.create("D", "x", null, null, null, true, 1L))
            .isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> DocumentType.create("DNI X", "x", null, null, null, true, 1L))
            .isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> DocumentType.create("DNI", "x", null, "123", null, true, 1L))
            .isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> DocumentType.create("DNI", "x", null, null, new NumberRules(9, 8, true), true, 1L))
            .isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> DocumentType.create("DNI", "x", null, null, new NumberRules(1, 21, true), true, 1L))
            .isInstanceOf(ParkingDomainException.class);
      assertThatThrownBy(() -> DocumentType.create("DNI", " ", null, null, null, true, 1L))
            .isInstanceOf(ParkingDomainException.class);
   }

   @Test
   @DisplayName("eliminar lo desactiva; restaurar lo devuelve activo; no se restaura uno vigente")
   void deleteAndRestore() {
      DocumentType type = dni();
      type.markAsDeleted(5L);
      assertThat(type.isDeleted()).isTrue();
      assertThat(type.isUsable()).isFalse();
      type.restore(6L);
      assertThat(type.isDeleted()).isFalse();
      assertThat(type.isActive()).isTrue();
      assertThat(type.getUpdatedBy()).isEqualTo(6L);
      assertThatThrownBy(() -> type.restore(6L)).isInstanceOf(ParkingDomainException.class);
   }

   @Test
   @DisplayName("igualdad por ID: dos tipos nuevos no son iguales")
   void equality() {
      assertThat(dni()).isNotEqualTo(dni());
      DocumentType a = dni();
      DocumentType b = dni();
      a.setId(3L);
      b.setId(3L);
      assertThat(a).isEqualTo(b).hasSameHashCodeAs(b);
   }
}
