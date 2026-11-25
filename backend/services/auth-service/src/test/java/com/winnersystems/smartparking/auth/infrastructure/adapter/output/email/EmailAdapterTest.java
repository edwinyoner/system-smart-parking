package com.winnersystems.smartparking.auth.infrastructure.adapter.output.email;

import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Los correos salen con el remitente de email.from y los enlaces apuntan a email.base-url.
 * JavaMailSender está simulado: no se envía nada de verdad.
 */
class EmailAdapterTest {

   private JavaMailSender mailSender;
   private EmailAdapter adapter;

   @BeforeEach
   void setUp() {
      mailSender = mock(JavaMailSender.class);
      when(mailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
      adapter = new EmailAdapter(mailSender);
      // Valores que Spring inyecta desde auth-service.yml
      ReflectionTestUtils.setField(adapter, "fromEmail", "avisos@ejemplo.pe");
      ReflectionTestUtils.setField(adapter, "fromName", "Smart Parking");
      ReflectionTestUtils.setField(adapter, "frontendUrl", "https://panel.ejemplo.pe");
   }

   private MimeMessage sent() {
      ArgumentCaptor<MimeMessage> captor = ArgumentCaptor.forClass(MimeMessage.class);
      verify(mailSender).send(captor.capture());
      return captor.getValue();
   }

   private static String raw(MimeMessage message) throws Exception {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      message.writeTo(out);
      return out.toString(java.nio.charset.StandardCharsets.UTF_8);
   }

   @Test
   @DisplayName("usa el remitente configurado en email.from")
   void usesConfiguredSender() throws Exception {
      adapter.sendHtmlEmail("ana@x.pe", "Prueba", "<p>Hola</p>");
      InternetAddress from = (InternetAddress) sent().getFrom()[0];
      assertThat(from.getAddress()).isEqualTo("avisos@ejemplo.pe");
      assertThat(from.getPersonal()).isEqualTo("Smart Parking");
   }

   @Test
   @DisplayName("reemplaza {{FRONTEND_URL}} por la URL del panel")
   void replacesFrontendUrl() throws Exception {
      adapter.sendHtmlEmail("ana@x.pe", "Prueba", "<a href=\"{{FRONTEND_URL}}/login\">Entrar</a>");
      String content = raw(sent());
      assertThat(content).contains("https://panel.ejemplo.pe/login").doesNotContain("{{FRONTEND_URL}}");
   }

   @Test
   @DisplayName("las plantillas no dejan enlaces a localhost")
   void templatesHaveNoLocalhost() throws Exception {
      adapter.sendNewCredentialsEmail("ana@x.pe", "Ana", "Clave#2026");
      assertThat(raw(sent())).contains("https://panel.ejemplo.pe/login").doesNotContain("localhost");
   }
}
