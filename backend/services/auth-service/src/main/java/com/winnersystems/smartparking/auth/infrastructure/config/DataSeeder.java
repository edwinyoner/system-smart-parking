package com.winnersystems.smartparking.auth.infrastructure.config;

import com.winnersystems.smartparking.auth.application.port.output.EmailPort;
import com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.permission.entity.PermissionEntity;
import com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.permission.repository.PermissionRepository;
import com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.role.entity.RoleEntity;
import com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.role.repository.RoleRepository;
import com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.token.entity.VerificationTokenEntity;
import com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.token.repository.VerificationTokenRepository;
import com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.user.entity.UserEntity;
import com.winnersystems.smartparking.auth.infrastructure.adapter.output.persistence.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Data Seeder - Inicializa datos por defecto en la base de datos.
 *
 * <p>Se ejecuta automáticamente al iniciar la aplicación (CommandLineRunner).</p>
 *
 * <p><b>IMPORTANTE:</b> Usa JPA Entities directamente (no modelos de dominio)
 * porque está en la capa de Infrastructure.</p>
 *
 * @author Edwin Yoner Winner Systems - Smart Parking Platform
 * @version 1.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

   private final PermissionRepository permissionRepository;
   private final RoleRepository roleRepository;
   private final UserRepository userRepository;
   private final VerificationTokenRepository verificationTokenRepository;
   private final PasswordEncoder passwordEncoder;
   private final EmailPort emailPort;

   // Misma política que exigen los formularios de contraseña (ResetPasswordRequest)
   private static final Pattern PASSWORD_POLICY = Pattern.compile(
         "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&])[A-Za-z\\d@$!%*?&]{8,128}$");
   private static final Pattern EMAIL_FORMAT = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

   // Credenciales del admin inicial: se leen de backend/.env (no versionado).
   // El valor por defecto vacío permite mostrar un mensaje claro si faltan.
   @Value("${ADMIN_EMAIL:}")
   private String adminEmail;

   @Value("${ADMIN_PASSWORD:}")
   private String adminPassword;

   /** URL del panel Angular, para los enlaces de los correos (email.base-url en auth-service.yml) */
   @Value("${email.base-url:http://localhost:4200}")
   private String frontendUrl;

   @Override
   @Transactional
   public void run(String... args) {
      log.info("========================================");
      log.info("Iniciando Data Seeder...");
      log.info("========================================");

      seedPermissions();
      seedRoles();
      seedAdminUser();

      log.info("========================================");
      log.info("Data Seeder completado exitosamente");
      log.info("========================================");
   }

   /**
    * Crea los permisos del sistema si no existen.
    */
   private void seedPermissions() {
      if (permissionRepository.count() > 0) {
         log.info("Permisos ya existen. Saltando...");
         return;
      }

      log.info("Creando permisos...");

      String[][] permissions = {
            // ========== GESTIÓN DE USUARIOS ==========
            {"users.create", "Crear nuevos usuarios en el sistema"},
            {"users.read", "Ver información de usuarios"},
            {"users.update", "Actualizar información de usuarios"},
            {"users.delete", "Eliminar usuarios del sistema"},
            {"users.restore", "Ver usuarios eliminados y restaurarlos"},

            // ========== GESTIÓN DE ROLES ==========
            {"roles.create", "Crear nuevos roles"},
            {"roles.read", "Ver información de roles"},
            {"roles.update", "Actualizar roles existentes"},
            {"roles.restore", "Ver roles eliminados y restaurarlos"},
            {"roles.delete", "Eliminar roles del sistema"},

            // ========== GESTIÓN DE PERMISOS ==========
            // Solo lectura: los permisos nacen aquí, en el código, y no se crean ni editan desde la interfaz
            {"permissions.read", "Ver información de permisos"},

            // ========== GESTIÓN DE ESTACIONAMIENTOS ==========

            // Turnos (Shifts)
            {"shifts.create", "Crear nuevos turnos de trabajo"},
            {"shifts.read", "Ver información de turnos"},
            {"shifts.update", "Actualizar turnos existentes"},
            {"shifts.delete", "Eliminar turnos"},
            {"shifts.restore", "Ver turnos eliminados y restaurarlos"},

            // Parqueos (contienen zonas; turnos y tarifas se configuran por parqueo)
            {"parkings.create", "Crear parqueos"},
            {"parkings.read", "Ver información de parqueos"},
            {"parkings.update", "Actualizar parqueos y cambiar su estado"},
            {"parkings.delete", "Eliminar parqueos"},
            {"parkings.restore", "Ver parqueos eliminados y restaurarlos"},

            // Zonas de Estacionamiento
            {"zones.create", "Crear nuevas zonas de estacionamiento"},
            {"zones.read", "Ver información de zonas"},
            {"zones.update", "Actualizar zonas existentes"},
            {"zones.delete", "Eliminar zonas"},
            {"zones.restore", "Ver zonas eliminadas y restaurarlas"},

            // Tipos de Espacio
            {"space-types.create", "Crear nuevos tipos de espacio"},
            {"space-types.read", "Ver tipos de espacio"},
            {"space-types.update", "Actualizar tipos de espacio"},
            {"space-types.delete", "Eliminar tipos de espacio"},

            // Espacios de Estacionamiento
            {"spaces.create", "Crear nuevos espacios"},
            {"spaces.read", "Ver información de espacios"},
            {"occupancy.view", "Ver el mapa de ocupación en tiempo real"},
            {"occupancy.view-all", "Ver el mapa de ocupación de todos los parqueos (no solo los asignados)"},
            {"spaces.update", "Actualizar espacios existentes"},
            {"spaces.delete", "Eliminar espacios"},
            {"spaces.bulk-create", "Creación masiva de espacios"},
            {"spaces.restore", "Ver espacios eliminados y restaurarlos"},

            // Asignación de operadores
            {"operator-assignments.create", "Asignar operadores a parqueos y zonas"},
            {"operator-assignments.read", "Ver asignaciones de operadores"},
            {"operator-assignments.update", "Cambiar las fechas de una asignación"},
            {"operator-assignments.delete", "Finalizar o cancelar asignaciones"},

            // Tipos de Documento
            {"document-types.create", "Crear nuevos tipos de documento"},
            {"document-types.read", "Ver tipos de documento"},
            {"document-types.update", "Actualizar tipos de documento"},
            {"document-types.delete", "Eliminar tipos de documento"},
            {"document-types.restore", "Ver tipos de documento eliminados y restaurarlos"},

            // Clientes
            {"customers.read", "Ver clientes y su historial (se crean solos al entrar con documento)"},
            {"customers.update", "Editar los datos de contacto de un cliente"},

            // Vehículos
            {"vehicles.read", "Ver vehículos y su historial (se crean solos por placa)"},

            // Transacciones
            {"transactions.entry", "Registrar entrada de vehículos"},
            {"transactions.exit", "Registrar salida de vehículos"},
            {"transactions.view", "Ver transacciones del sistema"},
            {"transactions.cancel", "Cancelar transacciones"},
            {"transactions.any-parking", "Registrar entradas y salidas en cualquier parqueo sin estar de turno"},

            // Tipos de Pago
            {"payment-types.create", "Crear nuevos tipos de pago"},
            {"payment-types.read", "Ver tipos de pago"},
            {"payment-types.update", "Actualizar tipos de pago"},
            {"payment-types.delete", "Eliminar tipos de pago"},
            {"payment-types.restore", "Ver tipos de pago eliminados y restaurarlos"},

            // Pagos
            {"payments.process", "Procesar pagos de estacionamiento"},
            {"payments.view", "Ver información de pagos"},
            {"payments.refund", "Realizar reembolsos"},
            {"payments.cancel", "Cancelar pagos"},

            // Infracciones
            {"infractions.create", "Registrar infracciones (estando de turno en el parqueo)"},
            {"infractions.read", "Ver infracciones registradas"},
            {"infractions.update", "Editar infracciones pendientes y su multa"},
            {"infractions.resolve", "Anular infracciones o cerrarlas con amonestación"},

            // ========== GESTIÓN DE TARIFAS ==========
            {"rates.create", "Crear nuevas tarifas"},
            {"rates.read", "Ver tarifas configuradas"},
            {"rates.update", "Actualizar tarifas existentes"},
            {"rates.delete", "Eliminar tarifas"},
            {"rates.restore", "Ver tarifas eliminadas y restaurarlas"},

            // ========== REPORTES ==========
            {"reports.view", "Ver reportes: diario, ingresos y ocupación por hora"},
            {"reports.export", "Exportar reportes (CSV y PDF)"}
      };

      for (String[] perm : permissions) {
         PermissionEntity permission = PermissionEntity.builder()
               .name(perm[0])
               .description(perm[1])
               .status(true)
               .build();

         permissionRepository.save(permission);
      }

      log.info("{} permisos creados", permissions.length);
   }

   /**
    * Crea los 3 roles del sistema con sus permisos.
    */
   private void seedRoles() {
      if (roleRepository.count() > 0) {
         log.info("Roles ya existen. Saltando...");
         return;
      }

      log.info("Creando roles...");

      // ========== ROL: ADMIN ==========
      RoleEntity adminRole = RoleEntity.builder()
            .name("ADMIN")
            .description("Acceso completo al sistema Smart Parking")
            .status(true)
            .permissions(getAllPermissions())
            .build();

      roleRepository.save(adminRole);
      log.info("Rol ADMIN creado con {} permisos (TODOS)", adminRole.getPermissions().size());

      // ========== ROL: AUTORIDAD ==========
      RoleEntity autoridadRole = RoleEntity.builder()
            .name("AUTORIDAD")
            .description("Gestión de usuarios, zonas, tarifas y reportes municipales")
            .status(true)
            .permissions(getPermissionsByNames(
                  // Gestión de usuarios (crear, leer, actualizar - SIN eliminar)
                  "users.create", "users.read", "users.update",
                  // Gestión de roles (solo lectura)
                  "roles.read",
                  // Gestión de permisos (solo lectura)
                  "permissions.read",

                  // Gestión de Turnos
                  "shifts.create", "shifts.read", "shifts.update", "shifts.delete", "shifts.restore",
                  // Gestión de Parqueos
                  "parkings.create", "parkings.read", "parkings.update", "parkings.delete", "parkings.restore",
                  // Gestión de Zonas
                  "zones.create", "zones.read", "zones.update", "zones.delete", "zones.restore",
                  // Gestión de Tipos de Espacio
                  "space-types.create", "space-types.read", "space-types.update", "space-types.delete",
                  // Gestión de Espacios
                  "spaces.create", "spaces.read", "spaces.update", "spaces.delete", "spaces.bulk-create", "spaces.restore",
                  "occupancy.view", "occupancy.view-all",
                  // Asignación de operadores
                  "operator-assignments.create", "operator-assignments.read", "operator-assignments.update",
                  "operator-assignments.delete",
                  // Gestión de Tipos de Documento
                  "document-types.create", "document-types.read", "document-types.update", "document-types.delete",
                  "document-types.restore",
                  // Gestión de Clientes
                  "customers.read", "customers.update",
                  // Gestión de Vehículos
                  "vehicles.read",
                  // Ver transacciones (sin registrar)
                  "transactions.view",
                  // Gestión de Tipos de Pago
                  "payment-types.create", "payment-types.read", "payment-types.update", "payment-types.delete",
                  "payment-types.restore",
                  // Ver pagos
                  "payments.view",
                  // Gestión de Infracciones
                  "infractions.create", "infractions.read", "infractions.update", "infractions.resolve",
                  // Gestión completa de tarifas
                  "rates.create", "rates.read", "rates.update", "rates.delete", "rates.restore",
                  // Reportes
                  "reports.view", "reports.export"
            ))
            .build();

      roleRepository.save(autoridadRole);
      log.info("Rol AUTORIDAD creado con {} permisos", autoridadRole.getPermissions().size());

      // ========== ROL: OPERADOR ==========
      RoleEntity operadorRole = RoleEntity.builder()
            .name("OPERADOR")
            .description("Operación diaria del sistema de estacionamiento")
            .status(true)
            .permissions(getPermissionsByNames(
                  // Solo lectura de usuarios (ver su perfil)
                  "users.read",

                  // Ver información de parqueos, zonas y espacios
                  "parkings.read",
                  "zones.read",
                  "spaces.read",
                  "occupancy.view",
                  "space-types.read",
                  // Ver tipos de documento
                  "document-types.read",
                  // Gestión de clientes
                  "customers.read", "customers.update",
                  // Gestión de vehículos
                  "vehicles.read",
                  // Operaciones de transacciones
                  "transactions.entry", "transactions.exit", "transactions.view",
                  // Ver tipos de pago
                  "payment-types.read",
                  // Procesar pagos
                  "payments.process", "payments.view",
                  // Registrar y ver infracciones (el cobro de la multa va con payments.process)
                  "infractions.create", "infractions.read",
                  // Solo lectura de tarifas
                  // Solo lectura de tarifas (los reportes, con ingresos de todos los parqueos, son de gestión)
                  "rates.read"
            ))
            .build();

      roleRepository.save(operadorRole);
      log.info("Rol OPERADOR creado con {} permisos", operadorRole.getPermissions().size());
   }

   /**
    * Crea el usuario admin por defecto CON LOS 3 ROLES.
    */
   private void seedAdminUser() {
      validateAdminCredentials();

      if (userRepository.existsByEmail(adminEmail)) {
         log.info("Usuario admin ya existe. Saltando...");
         return;
      }

      log.info("Creando usuario admin con los 3 roles...");

      // Obtener los 3 roles
      RoleEntity adminRole = roleRepository.findByName("ADMIN")
            .orElseThrow(() -> new RuntimeException("Rol ADMIN no encontrado"));

      RoleEntity autoridadRole = roleRepository.findByName("AUTORIDAD")
            .orElseThrow(() -> new RuntimeException("Rol AUTORIDAD no encontrado"));

      RoleEntity operadorRole = roleRepository.findByName("OPERADOR")
            .orElseThrow(() -> new RuntimeException("Rol OPERADOR no encontrado"));

      String tempPassword = adminPassword;

      // Crear usuario con los 3 roles
      UserEntity admin = UserEntity.builder()
            .firstName("Admin")
            .lastName("System")
            .email(adminEmail)
            .password(passwordEncoder.encode(tempPassword))
            .phoneNumber("987654321")
            .status(true)
            .emailVerified(false)
            .roles(Set.of(adminRole, autoridadRole, operadorRole))
            .build();

      userRepository.save(admin);

      log.info("Usuario admin creado con {} roles", admin.getRoles().size());
      log.info("   - Email: {}", adminEmail);
      log.info("   - Password: definida en ADMIN_PASSWORD (backend/.env)");
      log.info("   - Roles: ADMIN, AUTORIDAD, OPERADOR");
      log.info("  IMPORTANTE: Verificar email antes del primer login");

      // GENERAR TOKEN Y ENVIAR EMAIL DE VERIFICACIÓN
      try {
         String verificationToken = UUID.randomUUID().toString();

         VerificationTokenEntity tokenEntity = VerificationTokenEntity.builder()
               .token(verificationToken)
               .userId(admin.getId())
               .expiresAt(LocalDateTime.now().plusHours(24))
               .build();

         verificationTokenRepository.save(tokenEntity);

         String verificationLink = frontendUrl + "/verify-email?token=" + verificationToken;

         sendAdminWelcomeEmailWithVerification(admin, tempPassword, verificationLink);

         log.info("Email de verificación enviado a: {}", admin.getEmail());
         log.info("Expira en: 24 horas");
      } catch (Exception e) {
         log.error("Error al enviar email de verificación: {}", e.getMessage());
         log.warn("El usuario fue creado pero el email no pudo enviarse");
      }
   }

   // ========== HELPERS ==========

   /**
    * Verifica que ADMIN_EMAIL y ADMIN_PASSWORD estén definidos en backend/.env
    * y que la contraseña cumpla la misma política que el resto del sistema.
    * Si fallan, el servicio no arranca: es preferible a crear un admin inválido.
    */
   private void validateAdminCredentials() {
      if (adminEmail == null || adminEmail.isBlank() || adminPassword == null || adminPassword.isBlank()) {
         throw new IllegalStateException(
               "Faltan ADMIN_EMAIL o ADMIN_PASSWORD en backend/.env (ver backend/.env.example)");
      }
      adminEmail = adminEmail.trim().toLowerCase();
      if (!EMAIL_FORMAT.matcher(adminEmail).matches()) {
         throw new IllegalStateException("ADMIN_EMAIL no tiene un formato de correo válido");
      }
      if (!PASSWORD_POLICY.matcher(adminPassword).matches()) {
         throw new IllegalStateException(
               "ADMIN_PASSWORD debe tener de 8 a 128 caracteres, con mayúscula, minúscula, "
                     + "número y un carácter especial (@$!%*?&)");
      }
   }

   /**
    * Obtiene todos los permisos del sistema.
    */
   private Set<PermissionEntity> getAllPermissions() {
      return new HashSet<>(permissionRepository.findAll());
   }

   /**
    * Obtiene permisos por sus nombres.
    */
   private Set<PermissionEntity> getPermissionsByNames(String... names) {
      Set<PermissionEntity> permissions = new HashSet<>();
      for (String name : names) {
         permissionRepository.findByName(name)
               .ifPresent(permissions::add);
      }
      return permissions;
   }

   /**
    * Envía email de bienvenida con credenciales Y verificación.
    */
   private void sendAdminWelcomeEmailWithVerification(UserEntity user, String tempPassword, String verificationLink) {
      String subject = "Bienvenido a Smart Parking - Verifica tu Email";

      String htmlBody = String.format("""
            <!DOCTYPE html>
            <html>
            <head>
                <meta charset="UTF-8">
                <style>
                    body { 
                        font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; 
                        line-height: 1.6;
                        color: #333;
                        margin: 0;
                        padding: 0;
                        background-color: #f4f4f4;
                    }
                    .email-wrapper {
                        padding: 20px;
                    }
                    .container { 
                        max-width: 600px; 
                        margin: 0 auto; 
                        background: #ffffff;
                        border-radius: 10px;
                        overflow: hidden;
                        box-shadow: 0 4px 6px rgba(0,0,0,0.1);
                    }
                    .header { 
                        background: linear-gradient(135deg, #4CAF50 0%%, #45a049 100%%);
                        color: white; 
                        padding: 30px 20px; 
                        text-align: center;
                    }
                    .header h1 {
                        margin: 0;
                        font-size: 28px;
                    }
                    .header p {
                        margin: 5px 0 0 0;
                        opacity: 0.9;
                    }
                    .content { 
                        padding: 30px 20px; 
                    }
                    .verification-box {
                        background: #f0f8f0;
                        padding: 20px;
                        margin: 20px 0;
                        border-left: 4px solid #4CAF50;
                        border-radius: 5px;
                        text-align: center;
                    }
                    .verification-box h3 {
                        margin-top: 0;
                        color: #4CAF50;
                    }
                    .credentials { 
                        background: #f8f9fa; 
                        padding: 20px; 
                        margin: 20px 0; 
                        border-left: 4px solid #198754;
                        border-radius: 5px;
                    }
                    .credentials h3 {
                        margin-top: 0;
                        color: #198754;
                    }
                    .credentials p {
                        margin: 10px 0;
                    }
                    .credentials strong {
                        color: #146c43;
                    }
                    .password-box {
                        background: white;
                        padding: 10px 15px;
                        border: 2px dashed #198754;
                        border-radius: 5px;
                        font-family: 'Courier New', monospace;
                        font-size: 18px;
                        font-weight: bold;
                        color: #146c43;
                        text-align: center;
                        margin: 10px 0;
                    }
                    .button {
                        display: inline-block;
                        padding: 14px 35px;
                        background: #4CAF50;
                        color: white !important;
                        text-decoration: none;
                        border-radius: 5px;
                        margin: 15px 0;
                        font-weight: bold;
                        font-size: 16px;
                    }
                    .button:hover {
                        background: #45a049;
                    }
                    .warning { 
                        background: #fff3cd; 
                        padding: 15px; 
                        margin: 20px 0; 
                        border-left: 4px solid #ffc107;
                        border-radius: 5px;
                    }
                    .warning h3 {
                        margin-top: 0;
                        color: #856404;
                    }
                    .warning ul {
                        margin: 10px 0;
                        padding-left: 20px;
                    }
                    .footer {
                        text-align: center;
                        padding: 20px;
                        background: #2d3748;
                        color: white;
                    }
                    .footer p {
                        margin: 5px 0;
                    }
                </style>
            </head>
            <body>
                <div class="email-wrapper">
                    <div class="container">
                        <div class="header">
                            <h1>Smart Parking Platform</h1>
                            <p>Sistema de Gestión de Estacionamiento Inteligente</p>
                        </div>
                        
                        <div class="content">
                            <h2 style="color: #4CAF50;">¡Bienvenido, Administrador!</h2>
                            <p>Hola <strong>%s %s</strong>,</p>
                            <p>Se ha creado exitosamente tu cuenta de <strong>Administrador</strong> en Smart Parking Platform.</p>
                            
                            <div class="verification-box">
                                <h3>Verifica tu correo electrónico</h3>
                                <p>Para activar tu cuenta y garantizar la seguridad, por favor confirma tu dirección de correo haciendo clic en el botón:</p>
                                <a href="%s" class="button">Verificar mi correo</a>
                                <p style="font-size: 12px; margin-top: 15px; color: #666;">
                                    Si el botón no funciona, copia y pega este enlace en tu navegador:<br>
                                    <span style="word-break: break-all; color: #4CAF50;">%s</span>
                                </p>
                                <p style="font-size: 12px; color: #666; margin-top: 10px;">
                                    Este enlace expira en 24 horas.
                                </p>
                            </div>
                            
                            <div class="credentials">
                                <h3>Tus credenciales de acceso</h3>
                                <p>Una vez verificado tu email, podrás iniciar sesión con:</p>
                                
                                <p style="margin-top: 15px;"><strong>Usuario:</strong></p>
                                <p style="font-size: 16px;">%s</p>
                                
                                <p style="margin-top: 15px;"><strong>Contraseña temporal:</strong></p>
                                <div class="password-box">%s</div>
                                
                                <p style="margin-top: 15px;"><strong>Roles asignados:</strong> Administrador, Autoridad, Operador</p>
                                <p style="font-size: 14px; color: #666;">Tendrás acceso completo al sistema y podrás probar todas las funcionalidades.</p>
                            </div>
                            
                            <div class="warning">
                                <h3>IMPORTANTE - Seguridad</h3>
                                <ul>
                                    <li>Primero <strong>verifica tu email</strong> haciendo clic en el botón verde de arriba</li>
                                    <li>Esta es una <strong>contraseña temporal</strong></li>
                                    <li>Debes cambiarla en tu <strong>primer inicio de sesión</strong></li>
                                    <li><strong>No compartas</strong> estas credenciales con nadie</li>
                                    <li>Utiliza una contraseña segura:
                                        <ul>
                                            <li>Mínimo 8 caracteres</li>
                                            <li>Incluye mayúsculas y minúsculas</li>
                                            <li>Incluye números y símbolos</li>
                                        </ul>
                                    </li>
                                </ul>
                            </div>
                            
                            <p style="margin-top: 30px; color: #666; font-size: 14px;">
                                Si tienes problemas para acceder o necesitas ayuda, contacta con el equipo de soporte técnico.
                            </p>
                        </div>
                        
                        <div class="footer">
                            <p style="margin: 0; font-weight: bold;">Winner Systems Corporation S.A.C.</p>
                            <p style="opacity: 0.9;">Smart Parking Platform</p>
                            <p style="opacity: 0.8;">Huaraz, Áncash, Perú</p>
                            <p style="font-size: 12px; opacity: 0.7; margin-top: 10px;">
                                Este es un correo automático, por favor no responder directamente.
                            </p>
                        </div>
                    </div>
                </div>
            </body>
            </html>
            """,
            user.getFirstName(),
            user.getLastName(),
            verificationLink,
            verificationLink,
            user.getEmail(),
            HtmlUtils.htmlEscape(tempPassword)
      );

      emailPort.sendHtmlEmail(user.getEmail(), subject, htmlBody);
   }
}