import { Component, computed, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CardComponent, CardBodyComponent, AccordionModule } from '@coreui/angular';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import type { IconDefinition } from '@fortawesome/free-solid-svg-icons';
import {
  faBook,
  faRocket,
  faGaugeHigh,
  faCar,
  faMoneyBillWave,
  faSquareParking,
  faClock,
  faUserShield,
  faMicrochip,
  faCircleQuestion,
  faPrint,
  faPlay,
  faCircleCheck,
  faTriangleExclamation,
  faKey,
  faMagnifyingGlass,
  faXmark
} from '@fortawesome/free-solid-svg-icons';
import { normalizeText } from '../../../core/utils/text.util';

interface ManualSubsection {
  id: string;
  title: string;
  content: string;
  /** Permiso que necesita el usuario para usar esta función (si aplica). */
  permission?: string;
  /** Ruta del panel donde se hace, para abrirla desde el manual. */
  route?: string;
  steps?: string[];
  tips?: string[];
  warnings?: string[];
}

interface ManualSection {
  id: string;
  title: string;
  icon: IconDefinition;
  subsections: ManualSubsection[];
}

@Component({
  selector: 'app-user-manual',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    RouterLink,
    CardComponent,
    CardBodyComponent,
    AccordionModule,
    FontAwesomeModule
  ],
  templateUrl: './user-manual.component.html',
  styleUrls: ['./user-manual.component.css']
})
export class UserManualComponent {

  icons = {
    book: faBook,
    print: faPrint,
    play: faPlay,
    check: faCircleCheck,
    warning: faTriangleExclamation,
    key: faKey,
    search: faMagnifyingGlass,
    clear: faXmark,
    question: faCircleQuestion
  };

  activeTab = signal<string>('getting-started');
  query = signal<string>('');

  manualSections: ManualSection[] = [
    {
      id: 'getting-started',
      title: 'Primeros pasos',
      icon: faRocket,
      subsections: [
        {
          id: 'intro',
          title: 'Qué es Smart Parking System',
          content:
            'Prototipo académico de tesis (UNASAM) para gestionar los estacionamientos de Huaraz. Las cámaras con ' +
            'inteligencia artificial registran entradas y salidas, los sensores y la cámara aérea indican qué espacio ' +
            'está ocupado, y los paneles LED y la app móvil muestran la disponibilidad a los conductores. Este panel ' +
            'es para administrar el sistema y para que el operador atienda cuando el IoT no puede hacerlo solo. ' +
            'No es un sistema oficial de la Municipalidad Provincial de Huaraz.',
          tips: [
            'Usa una versión reciente de Chrome, Edge, Firefox o Safari.',
            'Las listas y los detalles se actualizan solos cada pocos segundos: no hace falta recargar la página.',
            'Cada usuario ve solo los menús y botones que le permiten sus permisos.'
          ]
        },
        {
          id: 'login',
          title: 'Iniciar sesión',
          content: 'Las cuentas las crea un administrador; no hay registro público. Al crear tu cuenta recibes un correo con tus datos de acceso.',
          route: '/login',
          steps: [
            'Elige el rol con el que vas a trabajar (si tienes varios, por ejemplo ADMIN y OPERADOR).',
            'Escribe tu correo y tu contraseña.',
            'Marca "Recordarme" solo en una computadora personal: la sesión dura 30 días en lugar de 7.',
            'Pulsa "Iniciar sesión".'
          ],
          tips: [
            'Los permisos que tienes dependen del rol con el que entraste. Para usar otro rol, cierra sesión y vuelve a entrar eligiéndolo.',
            'Si tu correo aún no está verificado, usa el enlace que te llegó (dura 24 horas).'
          ]
        },
        {
          id: 'forgot-password',
          title: 'Olvidé mi contraseña',
          content: 'Puedes crear una contraseña nueva desde la pantalla de inicio de sesión.',
          route: '/forgot-password',
          steps: [
            'En la pantalla de inicio, pulsa "¿Olvidaste tu contraseña?".',
            'Escribe tu correo y envía la solicitud.',
            'Abre el enlace del correo (dura 1 hora) y escribe la contraseña nueva.',
            'Al cambiarla, se cierran todas tus sesiones abiertas en otros equipos.'
          ]
        },
        {
          id: 'layout',
          title: 'Recorrido por el panel',
          content: 'El menú lateral agrupa las funciones por área: panel principal, accesos, parqueos, operaciones diarias, infracciones, pagos y reportes.',
          steps: [
            'Pulsa el botón del pie del menú para minimizarlo: quedan solo los íconos. El panel recuerda tu elección.',
            'Desde la cabecera eliges el tema claro, oscuro o automático.',
            'En "Mi perfil" cambias tus datos personales y tu contraseña.',
            'Los botones siguen un mismo código de color: verde crear o guardar, naranja editar, rojo eliminar, azul claro ver.'
          ]
        },
        {
          id: 'session',
          title: 'Por qué se cerró mi sesión',
          content:
            'El panel comprueba tu cuenta cada 10 segundos. Si algo cambió, cierra la sesión y te muestra el motivo en la pantalla de inicio.',
          steps: [
            'Tu cuenta fue desactivada o eliminada por un administrador.',
            'Tu contraseña o tu correo cambiaron.',
            'Te quitaron el rol con el que habías entrado.',
            'La sesión venció por inactividad.'
          ],
          tips: [
            'Si un administrador te quita o te da un permiso, el menú se actualiza sin volver a entrar. Si la página que tenías abierta ya no está permitida, vuelves al dashboard.'
          ]
        }
      ]
    },
    {
      id: 'dashboard',
      title: 'Dashboard y ocupación',
      icon: faGaugeHigh,
      subsections: [
        {
          id: 'dashboard-home',
          title: 'Dashboard',
          content: 'Resumen en vivo (cada 10 segundos) de lo que pasa en los parqueos.',
          route: '/dashboard',
          steps: [
            'Bienvenida con tu turno de hoy y accesos directos según tus permisos.',
            'Indicadores: espacios libres, porcentaje de ocupación, vehículos dentro y monto cobrado hoy.',
            'Disponibilidad de cada parqueo, con libres, ocupados y no disponibles.',
            'Vehículos dentro, cobros de hoy y próximos turnos de operadores.'
          ]
        },
        {
          id: 'occupancy',
          title: 'Mapa de ocupación',
          content: 'Muestra cada espacio de un parqueo como un cuadro de color, actualizado cada 5 segundos.',
          permission: 'occupancy.view',
          route: '/occupancy',
          steps: [
            'Elige un parqueo en las tarjetas de arriba.',
            'Verde = libre, rojo = ocupado (con la placa), naranja = mantenimiento, gris rayado = fuera de servicio.',
            'Pulsa un indicador para ver solo los espacios de ese estado, o una zona para ver solo esa zona.',
            'Busca por código de espacio o por placa.',
            'Pulsa un espacio para ver su detalle y abrir la estadía, registrar la salida o gestionar la zona.'
          ],
          tips: [
            'Las placas solo aparecen si tienes el permiso transactions.view.',
            'Sin el permiso occupancy.view-all solo ves los parqueos que gestionas o donde estás asignado.'
          ]
        }
      ]
    },
    {
      id: 'operation',
      title: 'Operación diaria',
      icon: faCar,
      subsections: [
        {
          id: 'entry',
          title: 'Registrar entrada',
          content:
            'Normalmente la cámara registra la entrada sola. El operador la registra a mano cuando la cámara no está ' +
            'disponible o la lectura no es confiable.',
          permission: 'transactions.entry',
          route: '/transactions/entry',
          steps: [
            'Elige el parqueo y la zona. Solo aparecen aquellos donde estás de turno ahora.',
            'Revisa el turno y la tarifa vigentes. Si el parqueo no atiende a esa hora, no se puede registrar.',
            'Escribe la placa (formato ABC-123 o A1B-234); el guion se añade solo.',
            'Deja el espacio en "Automático" (se toma uno libre al azar, solo para el conteo) o elige uno.',
            'Si el parqueo exige documento, o si quieres registrar al conductor, marca la casilla y completa tipo y número.',
            'Pulsa "Registrar entrada". La pantalla se queda lista para el siguiente vehículo.'
          ],
          tips: [
            'El vehículo y el cliente se crean solos la primera vez.',
            'El conductor puede estacionar donde quiera dentro de la zona; los sensores corrigen el espacio real.'
          ],
          warnings: [
            'Una placa que ya está dentro no puede volver a entrar.',
            'Si la zona está llena, el registro se bloquea.'
          ]
        },
        {
          id: 'reviews',
          title: 'Lecturas por revisar',
          content:
            'Cuando la cámara lee una placa con menos de 85 % de confianza, o el parqueo exige documento, la lectura ' +
            'llega aquí con su foto. La barrera espera hasta que un operador la resuelva.',
          permission: 'transactions.view',
          route: '/transactions/reviews',
          steps: [
            'Abre la pestaña "Pendientes": las más antiguas aparecen primero.',
            'Mira la foto y la placa leída. La barra muestra la confianza y la marca del 85 %.',
            'Corrige la placa si hace falta y, si se pide, completa el documento.',
            'Pulsa "Confirmar entrada" o "Confirmar salida".',
            'Si la lectura no corresponde a un vehículo real, pulsa el botón rojo, elige un motivo y descártala.'
          ],
          tips: [
            'Para confirmar necesitas transactions.entry o transactions.exit, según la dirección de la lectura.',
            'En "Resueltas" ves el historial de confirmadas y descartadas.'
          ],
          warnings: [
            'Una salida con deuda no se puede confirmar: usa el enlace "Ir a Registrar salida" para cobrar primero.'
          ]
        },
        {
          id: 'exit',
          title: 'Registrar salida y cobrar',
          content: 'El vehículo paga antes de salir. La salida se habilita en cuanto el pago queda confirmado.',
          permission: 'transactions.exit',
          route: '/transactions/exit',
          steps: [
            'Busca la placa (o llega desde "Vehículos dentro" o desde el mapa de ocupación).',
            'Revisa el tiempo, el método de entrada y el monto a pagar.',
            'Cobra con QR o en efectivo (ver "Pagos").',
            'Si el parqueo exige documento y el conductor entró con uno, pide el mismo documento.',
            'Pulsa "Registrar salida". El espacio queda libre al instante.'
          ],
          tips: [
            'Si el conductor sale antes de 5 minutos, no paga.',
            'Después de pagar tiene 15 minutos para salir sin pagar más. Pasado ese tiempo solo se cobra la diferencia.'
          ],
          warnings: [
            'Por seguridad, si el documento no coincide la salida se rechaza, y el mensaje no revela el documento con el que entró.'
          ]
        },
        {
          id: 'active',
          title: 'Vehículos dentro',
          content: 'Lista en vivo de las estadías en curso, con el tiempo transcurrido y lo que falta pagar.',
          permission: 'transactions.view',
          route: '/transactions/active',
          steps: [
            'Filtra por parqueo, zona o placa (puedes escribir solo una parte).',
            'Las estadías largas se resaltan.',
            'Usa los botones de cada fila para ver el detalle, cambiar de espacio, registrar la salida o cancelar.'
          ],
          warnings: [
            'Cancelar una estadía exige un motivo y no es posible si ya tiene pagos. Úsalo solo para registros hechos por error.'
          ]
        },
        {
          id: 'history',
          title: 'Historial de transacciones',
          content: 'Todas las estadías, en curso y terminadas, con su detalle.',
          permission: 'transactions.view',
          route: '/transactions',
          steps: [
            'Abre una estadía para ver la entrada y la salida (método, operador o cámara y confianza), el cliente, los pagos y las observaciones.',
            'Puedes editar las observaciones (máximo 500 caracteres).'
          ]
        }
      ]
    },
    {
      id: 'payments',
      title: 'Pagos',
      icon: faMoneyBillWave,
      subsections: [
        {
          id: 'qr',
          title: 'Cobrar con QR',
          content: 'El QR funciona con Yape, Plin y la banca móvil. El pago se confirma solo, sin que el operador tenga que revisarlo.',
          permission: 'payments.process',
          steps: [
            'En el paso de cobro elige "QR".',
            'El panel muestra el QR y una cuenta regresiva (vence a los 10 minutos).',
            'El conductor lo escanea y paga. El panel lo detecta en pocos segundos y habilita la salida.',
            'Si el conductor cambia de opinión, pulsa "Anular QR".'
          ],
          tips: [
            'En este prototipo la pasarela es simulada: los botones "Simular pago" y "Rechazar" reemplazan al banco.',
            'Si vuelves a abrir el cobro, se retoma el QR que seguía vigente.'
          ]
        },
        {
          id: 'cash',
          title: 'Cobrar en efectivo',
          content: 'El operador recibe el dinero y lo registra.',
          permission: 'payments.process',
          steps: [
            'Elige "Efectivo".',
            'Escribe el monto recibido: el panel calcula el vuelto.',
            'Si el tipo de pago lo pide, escribe el número de operación.',
            'Confirma: el pago queda registrado al instante.'
          ]
        },
        {
          id: 'process',
          title: 'Procesar pago',
          content: 'Cobra una estadía sin registrar todavía la salida, por ejemplo cuando el conductor paga antes de ir por su vehículo.',
          permission: 'payments.process',
          route: '/transactions/payment',
          steps: ['Busca la estadía por placa.', 'Cobra con QR o en efectivo.', 'Tiene 15 minutos para salir sin pagar más.']
        },
        {
          id: 'payment-history',
          title: 'Historial, devoluciones y anulaciones',
          content: 'Todos los pagos de estadías y multas, con filtros por estado, concepto, tipo y fechas.',
          permission: 'payments.view',
          route: '/payments',
          steps: [
            'Abre un pago para ver su detalle.',
            'Devolver (payments.refund): devuelve todo o una parte. Si se devuelve todo, la estadía o la multa vuelve a quedar pendiente de pago.',
            'Anular (payments.cancel): solo para un QR que todavía no se pagó.'
          ],
          warnings: ['Las devoluciones se hacen a mano: el sistema las registra, pero el dinero lo devuelve el operador.']
        },
        {
          id: 'payment-types',
          title: 'Tipos de pago',
          content: 'Define con qué se puede pagar y en qué canal.',
          permission: 'payment-types.read',
          route: '/payment-types',
          steps: [
            'Confirmación automática (QR) o manual (efectivo). Los tipos manuales solo se usan con un operador.',
            'Canales: pantalla de salida, kiosco de la calle, app móvil u operador.',
            '"Pide referencia" obliga a escribir el número de operación.'
          ],
          warnings: ['Un tipo de pago que ya tiene pagos no se puede eliminar; desactívalo.']
        }
      ]
    },
    {
      id: 'parkings',
      title: 'Parqueos',
      icon: faSquareParking,
      subsections: [
        {
          id: 'wizard',
          title: 'Crear un parqueo (asistente de 5 pasos)',
          content: 'Cada paso se guarda por separado: si algo falla, al reintentar solo se envía lo que falta.',
          permission: 'parkings.create',
          route: '/parkings',
          steps: [
            'Datos del parqueo: nombre, código, dirección, ubicación en el mapa, responsable y si exige documento del conductor.',
            'Zonas: nombre, código, cuántos espacios generar y cámaras.',
            'Espacios: prefijo, número inicial y vista previa de los códigos (máximo 500 por lote).',
            'Horario: qué turnos atiende y con qué tarifa. Puedes dejarlo para después.',
            'Operadores: quién atiende cada turno, en todo el parqueo o en una zona.'
          ],
          tips: ['Al terminar se abre el detalle del parqueo, con lo que falta configurar.']
        },
        {
          id: 'zones-spaces',
          title: 'Zonas y espacios',
          content: 'Desde el detalle del parqueo entras a cada zona y a sus espacios.',
          permission: 'zones.update / spaces.update',
          steps: [
            'Crea espacios de uno en uno o en lote.',
            'Pon un espacio en mantenimiento o fuera de servicio: deja de contar como disponible.',
            'Si un espacio quedó ocupado por error y no hay ningún vehículo dentro, libéralo.',
            'Los eliminados quedan en la papelera y se pueden restaurar.'
          ],
          warnings: [
            'No se puede eliminar un parqueo, una zona ni un espacio con vehículos dentro.',
            'El código de una zona o de un espacio no se puede cambiar después de crearlo.'
          ]
        },
        {
          id: 'schedule',
          title: 'Horario del parqueo',
          content: 'Indica qué turnos atiende el parqueo y qué tarifa se cobra en cada uno. Fuera de esos turnos el parqueo no atiende.',
          permission: 'parkings.update',
          steps: [
            'En el detalle del parqueo, abre "Horario".',
            'Agrega turnos y elige su tarifa; puedes activar o desactivar cada uno.',
            'La entrada cobra la tarifa del turno vigente; el precio se calcula al salir.'
          ],
          warnings: [
            'Los turnos activos de un parqueo no pueden solaparse.',
            'No se puede quitar un turno que todavía tiene operadores asignados.'
          ]
        },
        {
          id: 'operators',
          title: 'Operadores',
          content: 'Un operador atiende un turno, en todo el parqueo o en una zona, entre dos fechas (la final es opcional).',
          permission: 'operator-assignments.create',
          steps: [
            'En el detalle del parqueo, abre "Operadores" y pulsa "Asignar".',
            'Elige el usuario, el turno, todo el parqueo o una zona, y las fechas.',
            'Para terminar una asignación pulsa "Finalizar"; si aún no empezó, se cancela.'
          ],
          warnings: [
            'En un mismo turno, un parqueo tiene un operador para todo el parqueo o uno por zona, no ambos.',
            'Una misma persona no puede tener dos asignaciones que se crucen en fecha y horario, aunque sean de parqueos distintos.'
          ]
        },
        {
          id: 'document-policy',
          title: 'Exigir documento del conductor',
          content:
            'Interruptor de cada parqueo, apagado por defecto. Apagado, el vehículo entra y sale solo con la placa. ' +
            'Encendido, la entrada pide documento y la salida pide el mismo (protección contra robos).',
          permission: 'parkings.update'
        }
      ]
    },
    {
      id: 'catalogs',
      title: 'Turnos, tarifas y documentos',
      icon: faClock,
      subsections: [
        {
          id: 'shifts',
          title: 'Turnos',
          content: 'Franjas horarias que se usan en el horario de los parqueos.',
          permission: 'shifts.read',
          route: '/shifts',
          steps: [
            'Define nombre, código, hora de inicio y hora de fin.',
            'Un turno puede cruzar la medianoche (por ejemplo, 18:00 - 05:59).',
            'El minuto final se incluye: para que no se solapen, termina un turno a las 11:59 y empieza el siguiente a las 12:00.'
          ],
          warnings: ['Un turno en uso no se puede eliminar; sí desactivar.']
        },
        {
          id: 'rates',
          title: 'Tarifas',
          content: 'Precio por hora en soles (PEN) y reglas de cobro. Cada tarifa tiene un simulador para probarla.',
          permission: 'rates.read',
          route: '/rates',
          steps: [
            'Precio por fracción: por defecto, por hora.',
            'Sin costo: no paga si sale antes de 5 minutos.',
            'Tolerancia: 5 minutos por fracción. Con S/ 2.50 por hora: 1 h 05 min se cobra 1 hora (S/ 2.50); 1 h 06 min se cobra 2 horas (S/ 5.00).',
            'Mínimo a cobrar (opcional).'
          ],
          tips: ['Se cobra el precio vigente al momento de salir.'],
          warnings: ['Una tarifa en uso no se puede eliminar.']
        },
        {
          id: 'document-types',
          title: 'Tipos de documento',
          content: 'Documentos con los que se identifica al conductor: DNI (8 dígitos), carné de extranjería y pasaporte.',
          permission: 'document-types.read',
          route: '/document-types',
          steps: [
            'Cada tipo define su longitud mínima y máxima y si admite solo números.',
            'Usa "Probar un número" para comprobar la validación.',
            'Los espacios y guiones se quitan solos al guardar.'
          ]
        }
      ]
    },
    {
      id: 'access',
      title: 'Usuarios, roles y permisos',
      icon: faUserShield,
      subsections: [
        {
          id: 'users',
          title: 'Usuarios',
          content: 'Solo un administrador crea las cuentas.',
          permission: 'users.create',
          route: '/users',
          steps: [
            'Pulsa "Nuevo usuario" y completa nombre, correo, celular (9 dígitos, empieza en 9) y roles.',
            'El usuario recibe sus credenciales por correo.',
            'Desde "Editar" puedes desactivar la cuenta, cambiar sus roles o el correo, o generar una contraseña nueva.'
          ],
          warnings: [
            'Nadie puede cambiar su propio estado ni sus propios roles, ni eliminarse a sí mismo.',
            'Una cuenta ADMIN solo la administra quien entró con el rol ADMIN.',
            'Si cambias el correo, el usuario debe verificarlo de nuevo y se cierran sus sesiones.'
          ]
        },
        {
          id: 'roles',
          title: 'Roles',
          content: 'Un rol agrupa permisos. ADMIN, AUTORIDAD y OPERADOR son del sistema; además puedes crear roles propios.',
          permission: 'roles.create',
          route: '/roles',
          steps: [
            'Pulsa "Nuevo rol", escribe el nombre y marca sus permisos (agrupados por módulo).',
            'Los roles nuevos aparecen enseguida en la pantalla de inicio de sesión.'
          ],
          warnings: [
            'Los roles del sistema no se renombran, desactivan ni eliminan.',
            'Un rol con usuarios no se puede desactivar ni eliminar.'
          ]
        },
        {
          id: 'permissions',
          title: 'Permisos',
          content: 'Matriz de todos los permisos y de los roles que los tienen. Los permisos son fijos: vienen con el sistema y no se crean ni se borran.',
          permission: 'permissions.read',
          route: '/permissions',
          steps: [
            'Busca por nombre o descripción, o filtra por módulo, por tipo de acción o por rol.',
            'Pulsa la cabecera de un rol para ver solo sus permisos.'
          ]
        },
        {
          id: 'trash',
          title: 'Papelera',
          content: 'Lo que se elimina no se borra de verdad: queda en la papelera de su módulo.',
          permission: '<módulo>.restore',
          steps: [
            'En la lista del módulo, cambia de "Vigentes" a "Eliminados".',
            'Pulsa "Restaurar" en el registro que quieras recuperar.'
          ],
          tips: ['Cada módulo tiene su propio permiso de restaurar (users.restore, shifts.restore, parkings.restore...), que se asigna desde Roles.']
        }
      ]
    },
    {
      id: 'automation',
      title: 'Cámaras, sensores y paneles',
      icon: faMicrochip,
      subsections: [
        {
          id: 'cameras',
          title: 'Cámaras de entrada y salida',
          content:
            'La ESP32-CAM toma una foto, la inteligencia artificial (YOLO26 + EasyOCR) lee la placa y el sistema decide qué hacer.',
          steps: [
            'Confianza de 85 % o más: registra la entrada o la salida y abre la barrera.',
            'Confianza menor, o un parqueo que exige documento: la lectura va a "Por revisar".',
            'Salida con deuda: la barrera no se abre hasta que se pague.'
          ],
          tips: ['En el detalle de la estadía, el método de entrada indica "Cámara" y la confianza de la lectura.']
        },
        {
          id: 'sensors',
          title: 'Sensores de ocupación',
          content: 'Un sensor ultrasónico sobre cada espacio indica si hay un vehículo (a 80 cm o menos).',
          steps: [
            'Si detecta un vehículo en un espacio libre, el sistema le asigna la estadía de la zona que estaba en el espacio equivocado.',
            'Si el espacio queda vacío y no tiene estadía, lo libera.'
          ]
        },
        {
          id: 'panels',
          title: 'Paneles LED y app móvil',
          content: 'Muestran a los conductores la disponibilidad, sin iniciar sesión.',
          steps: [
            'Panel junto al parqueo: nombre, total, disponibles y ocupados de ese parqueo.',
            'Panel de avenida: muestra todos los parqueos, uno cada 10 segundos.',
            'App móvil: lista y mapa de parqueos, horario, tarifa, mapa de espacios y "Cómo llegar".'
          ],
          tips: ['Total = disponibles + ocupados. Los espacios en mantenimiento o fuera de servicio no cuentan.']
        },
        {
          id: 'fallback',
          title: 'Si el IoT falla',
          content: 'El operador desde este panel es el respaldo: todo lo que hacen las cámaras se puede hacer a mano.',
          steps: [
            'Cámara caída: registra la entrada y la salida desde "Operaciones diarias".',
            'Lectura dudosa: resuélvela en "Por revisar".',
            'Espacio mal marcado: corrígelo desde "Vehículos dentro" (cambiar espacio) o desde la zona.'
          ]
        }
      ]
    },
    {
      id: 'faq',
      title: 'Preguntas frecuentes',
      icon: faCircleQuestion,
      subsections: [
        {
          id: 'faq-menu',
          title: 'No veo un menú o un botón',
          content: 'Tu rol activo no tiene ese permiso. Pide al administrador que lo añada a tu rol, o entra con otro rol si tienes varios.'
        },
        {
          id: 'faq-entry',
          title: 'No aparece mi parqueo en "Registrar entrada"',
          content: 'Solo aparecen los parqueos (y zonas) donde estás de turno ahora. Revisa tu asignación en el dashboard o pide al administrador que te asigne.'
        },
        {
          id: 'faq-closed',
          title: 'Dice que el parqueo no atiende a esta hora',
          content: 'El parqueo no tiene un turno activo en su horario para esta hora. Se configura en el detalle del parqueo, en "Horario".'
        },
        {
          id: 'faq-exit',
          title: 'No me deja registrar la salida',
          content: 'Falta pagar. Cobra primero; si pasaron más de 15 minutos desde el pago, hay que cobrar la diferencia.'
        },
        {
          id: 'faq-available',
          title: 'Los disponibles no coinciden con lo que veo',
          content: 'Revisa en el mapa de ocupación si hay espacios en mantenimiento, fuera de servicio o una zona inactiva. Si un espacio quedó ocupado sin vehículo, libéralo desde su zona.'
        }
      ]
    }
  ];

  /** Resultados de búsqueda en todo el manual (sin tildes ni mayúsculas). */
  searchResults = computed(() => {
    const q = normalizeText(this.query());
    if (q.length < 2) return [];
    const results: { section: ManualSection; subsection: ManualSubsection }[] = [];
    for (const section of this.manualSections) {
      for (const sub of section.subsections) {
        const text = [sub.title, sub.content, ...(sub.steps ?? []), ...(sub.tips ?? []), ...(sub.warnings ?? [])].join(' ');
        if (normalizeText(text).includes(q)) results.push({ section, subsection: sub });
      }
    }
    return results;
  });

  searching = computed(() => normalizeText(this.query()).length >= 2);

  setActiveTab(tabId: string): void {
    this.activeTab.set(tabId);
    this.query.set('');
  }

  /** Imprime el manual completo; desde el diálogo del navegador se puede guardar como PDF. */
  print(): void {
    window.print();
  }
}
