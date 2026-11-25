package com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.validation;

import com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.auth.dto.request.UpdateProfileRequest;
import com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.user.dto.request.CreateUserRequest;
import com.winnersystems.smartparking.auth.infrastructure.adapter.input.rest.user.dto.request.UpdateUserRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Celular peruano: 9 dígitos que empiezan en 9; vacío permitido.
 * Se comprueba en los tres DTO que reciben teléfono.
 */
class PhoneNumberValidationTest {

   private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

   private static final Object[][] CASES = {
         {"987654321", true}, {"912345678", true}, {"", true}, {null, true},
         {"87654321", false}, {"887654321", false}, {"9876543210", false},
         {"+51987654321", false}, {"98765432a", false}, {"987 654 321", false},
   };

   static Stream<Arguments> cases() {
      List<Arguments> arguments = new ArrayList<>();
      for (Class<?> dto : List.of(CreateUserRequest.class, UpdateUserRequest.class, UpdateProfileRequest.class)) {
         for (Object[] c : CASES) {
            arguments.add(Arguments.of(dto, c[0], c[1]));
         }
      }
      return arguments.stream();
   }

   @ParameterizedTest(name = "{0} \"{1}\" válido={2}")
   @MethodSource("cases")
   @SuppressWarnings({"unchecked", "rawtypes"})
   void validatesPeruvianMobile(Class dto, String phone, boolean expectedValid) {
      boolean valid = VALIDATOR.validateValue(dto, "phoneNumber", phone).isEmpty();
      assertThat(valid).isEqualTo(expectedValid);
   }
}
