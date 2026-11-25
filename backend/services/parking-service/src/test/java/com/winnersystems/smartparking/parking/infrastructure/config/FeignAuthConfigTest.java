package com.winnersystems.smartparking.parking.infrastructure.config;

import feign.RequestTemplate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Las llamadas Feign a auth-service llevan el token del usuario que originó la petición.
 */
class FeignAuthConfigTest {

   private final feign.RequestInterceptor interceptor = new FeignAuthConfig().forwardAuthorizationHeader();

   @AfterEach
   void clear() {
      RequestContextHolder.resetRequestAttributes();
   }

   @Test
   @DisplayName("reenvía la cabecera Authorization de la petición entrante")
   void forwardsToken() {
      MockHttpServletRequest incoming = new MockHttpServletRequest();
      incoming.addHeader("Authorization", "Bearer abc.def.ghi");
      RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(incoming));

      RequestTemplate template = new RequestTemplate();
      interceptor.apply(template);

      assertThat(template.headers().get("Authorization")).containsExactly("Bearer abc.def.ghi");
   }

   @Test
   @DisplayName("fuera de una petición HTTP (proceso interno) no agrega nada")
   void noRequest() {
      RequestTemplate template = new RequestTemplate();
      interceptor.apply(template);
      assertThat(template.headers()).doesNotContainKey("Authorization");
   }
}
