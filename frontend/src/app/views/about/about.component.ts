import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  BadgeComponent,
  ProgressComponent
} from '@coreui/angular';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import type { IconDefinition } from '@fortawesome/free-solid-svg-icons';
import {
  faCircleInfo,
  faCode,
  faUsers,
  faGraduationCap,
  faBuildingColumns,
  faLocationDot,
  faEnvelope,
  faCamera,
  faWaveSquare,
  faTv,
  faSquareParking,
  faRoute,
  faMap,
  faDiagramProject,
  faLayerGroup,
  faMicrochip,
  faBrain,
  faCloud,
  faTowerBroadcast,
  faCrosshairs,
  faFont
} from '@fortawesome/free-solid-svg-icons';
import { faGithub, faLinkedin } from '@fortawesome/free-brands-svg-icons';

/** Pilares tecnológicos de la tesis: se marcan en el diagrama. */
type Pillar = 'iot' | 'ia' | 'cloud';

/** Logo de una tecnología: SVG en src/assets/tech o, si no hay logo oficial, un ícono genérico. */
interface Logo {
  label: string;
  src?: string;
  icon?: IconDefinition;
}

/** Ruta de un logo dentro de los assets del panel. */
const logo = (file: string, label: string): Logo => ({ src: `assets/tech/${file}.svg`, label });

const L = {
  angular: logo('angular', 'Angular'),
  typescript: logo('typescript', 'TypeScript'),
  flutter: logo('flutter', 'Flutter'),
  dart: logo('dart', 'Dart'),
  microcontroller: { label: 'Microcontrolador', icon: faMicrochip } as Logo,
  espressif: logo('espressif', 'ESP32'),
  c: logo('c', 'C'),
  cpp: logo('cplusplus', 'C++'),
  mqtt: logo('mqtt', 'MQTT'),
  mosquitto: logo('eclipsemosquitto', 'Eclipse Mosquitto'),
  spring: logo('spring', 'Spring'),
  java: logo('java', 'Java'),
  go: logo('go', 'Go'),
  python: logo('python', 'Python'),
  fastapi: logo('fastapi', 'FastAPI'),
  pytorch: logo('pytorch', 'PyTorch'),
  opencv: logo('opencv', 'OpenCV'),
  postgresql: logo('postgresql', 'PostgreSQL'),
  oracle: logo('oracle', 'Oracle'),
  sqlserver: logo('microsoftsqlserver', 'SQL Server'),
  mysql: logo('mysql', 'MySQL'),
  mongodb: logo('mongodb', 'MongoDB'),
  redis: logo('redis', 'Redis'),
  docker: logo('docker', 'Docker'),
  gcp: logo('googlecloud', 'Google Cloud')
};

interface ClientNode {
  name: string;
  audience: string;
  logo: Logo;
  items: string[];
  protocol: string;
  pillar?: Pillar;
}

interface ServiceNode {
  name: string;
  port: number;
  architecture: string;
  role: string;
  logo: Logo;
  stack: string;
  databases: { logo: Logo; name: string; detail: string }[];
  pillar?: Pillar;
}

interface TechGroup {
  title: string;
  pillar?: Pillar;
  items: { logo: Logo; name: string; version: string; use: string }[];
}

interface Scenario {
  title: string;
  hardware: string;
  ai: string;
  result: string;
  icon: IconDefinition;
}

interface Phase {
  title: string;
  items: { text: string; done: boolean }[];
}

@Component({
  selector: 'app-about',
  standalone: true,
  imports: [
    CommonModule,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ColComponent,
    RowComponent,
    BadgeComponent,
    ProgressComponent,
    FontAwesomeModule
  ],
  templateUrl: './about.component.html',
  styleUrls: ['./about.component.css']
})
export class AboutComponent {

  icons = {
    info: faCircleInfo,
    code: faCode,
    users: faUsers,
    graduation: faGraduationCap,
    university: faBuildingColumns,
    location: faLocationDot,
    email: faEnvelope,
    parking: faSquareParking,
    route: faRoute,
    diagram: faDiagramProject,
    layers: faLayerGroup,
    github: faGithub,
    linkedin: faLinkedin
  };

  logos = L;

  /** Nombre e ícono de cada pilar (leyenda y marcas del diagrama). */
  pillars: Record<Pillar, { label: string; short: string; icon: IconDefinition }> = {
    iot: { label: 'Internet of Things', short: 'IoT', icon: faTowerBroadcast },
    ia: { label: 'Artificial Intelligence', short: 'IA', icon: faBrain },
    cloud: { label: 'Cloud Computing', short: 'Cloud', icon: faCloud }
  };
  pillarOrder: Pillar[] = ['iot', 'ia', 'cloud'];

  /** Datos de un pilar (en las plantillas el contexto de ng-template no tiene tipo). */
  pillarOf(p: Pillar) {
    return this.pillars[p];
  }

  systemInfo = {
    name: 'Smart Parking System',
    version: '1.0.0',
    releaseDate: 'Noviembre 2026',
    stage: 'Prototipo académico',
    description:
      'Prototipo de Smart Parking con tecnologías de Internet of Things, Artificial Intelligence y ' +
      'Cloud Computing para una Smart City en Huaraz - Áncash.',
    university: 'Universidad Nacional Santiago Antúnez de Mayolo (UNASAM)',
    faculty: 'Facultad de Ciencias · Escuela de Ingeniería de Sistemas e Informática',
    company: 'Winner Systems Corporation S.A.C.',
    location: 'Huaraz, Áncash - Perú',
    pilots: 'Parqueo San Martín y Parqueo Las Banderas',
    advisor: 'M.Sc. Franz Martín Villanueva Cochachin'
  };

  // ==================== DIAGRAMA DE ARQUITECTURA ====================

  /** Los tres clientes del sistema (fuera de la nube). */
  clients: ClientNode[] = [
    {
      name: 'Web',
      audience: 'Administradores, autoridades y operadores',
      logo: L.angular,
      items: ['Panel de gestión y operación diaria'],
      protocol: 'HTTPS · REST · JWT'
    },
    {
      name: 'App Móvil',
      audience: 'Conductores y ciudadanos',
      logo: L.flutter,
      items: ['Disponibilidad pública, sin login'],
      protocol: 'HTTPS · REST pública'
    },
    {
      name: 'Microcontroladores (IoT)',
      audience: 'Parqueos y avenidas',
      logo: L.microcontroller,
      items: ['ESP32-CAM: entrada, salida y vista aérea', 'ESP32 + sensores ultrasónicos HC-SR04', 'ESP32 + paneles LED de disponibilidad'],
      protocol: 'ESP32-CAM: HTTP → Gateway · ESP32 de sensores y paneles: MQTT → Broker',
      pillar: 'iot'
    }
  ];

  /** Caché compartida: hoy la usa iot-service; preparada para el gateway y auth-service. */
  sharedCache = { logo: L.redis, name: 'Redis 7', detail: 'Caché compartida y límite de peticiones · hoy: iot-service' };

  /** Microservicios de negocio, cada uno con su propia base de datos. */
  services: ServiceNode[] = [
    {
      name: 'auth-service',
      port: 8081,
      architecture: 'Hexagonal',
      role: 'Usuarios, roles, permisos y tokens',
      logo: L.java,
      stack: 'Java 25 · Spring Boot',
      databases: [{ logo: L.postgresql, name: 'PostgreSQL 18', detail: 'auth_service_db' }]
    },
    {
      name: 'parking-service',
      port: 8082,
      architecture: 'Hexagonal',
      role: 'Parqueos, estadías, ocupación y disponibilidad',
      logo: L.java,
      stack: 'Java 25 · Spring Boot',
      databases: [{ logo: L.oracle, name: 'Oracle 21c XE', detail: 'XEPDB1' }]
    },
    {
      name: 'payment-service',
      port: 8085,
      architecture: 'Hexagonal',
      role: 'Cobros con QR y efectivo',
      logo: L.java,
      stack: 'Java 25 · Spring Boot',
      databases: [{ logo: L.sqlserver, name: 'SQL Server 2022', detail: 'payment_service_db' }]
    },
    {
      name: 'iot-service',
      port: 8083,
      architecture: 'Por capas',
      role: 'Orquesta cámaras, sensores y paneles',
      logo: L.go,
      stack: 'Go 1.26 · Gin · MQTT',
      databases: [{ logo: L.mysql, name: 'MySQL 8.0', detail: 'iot_service_db' }],
      pillar: 'iot'
    },
    {
      name: 'ia-service',
      port: 8084,
      architecture: 'Por capas, sin estado',
      role: 'Lectura de placas y ocupación por visión',
      logo: L.python,
      stack: 'Python 3.12 · FastAPI · YOLO26 · EasyOCR',
      databases: [{ logo: L.mongodb, name: 'MongoDB', detail: 'ia_service_db' }],
      pillar: 'ia'
    }
  ];

  // ==================== TECNOLOGÍAS ====================

  techGroups: TechGroup[] = [
    {
      title: 'Clientes',
      items: [
        { logo: L.angular, name: 'Angular', version: '20', use: 'Panel web' },
        { logo: L.typescript, name: 'TypeScript', version: '5.9', use: 'Lenguaje del panel' },
        { logo: L.flutter, name: 'Flutter', version: '3.38', use: 'App móvil' },
        { logo: L.dart, name: 'Dart', version: '3.10', use: 'Lenguaje de la app' }
      ]
    },
    {
      title: 'Backend',
      items: [
        { logo: L.java, name: 'Java', version: '25', use: 'auth, parking y payment' },
        { logo: L.spring, name: 'Spring Boot', version: '3.5', use: 'Microservicios Java' },
        { logo: L.go, name: 'Go', version: '1.26', use: 'iot-service' },
        { logo: L.python, name: 'Python', version: '3.12', use: 'ia-service' },
        { logo: L.fastapi, name: 'FastAPI', version: '0.115', use: 'API de ia-service' }
      ]
    },
    {
      title: 'Bases de datos',
      items: [
        { logo: L.postgresql, name: 'PostgreSQL', version: '18', use: 'auth-service' },
        { logo: L.oracle, name: 'Oracle', version: '21c XE', use: 'parking-service' },
        { logo: L.sqlserver, name: 'SQL Server', version: '2022', use: 'payment-service' },
        { logo: L.mysql, name: 'MySQL', version: '8.0', use: 'iot-service' },
        { logo: L.mongodb, name: 'MongoDB', version: 'Community', use: 'ia-service' },
        { logo: L.redis, name: 'Redis', version: '7', use: 'Caché de disponibilidad y límite de peticiones' }
      ]
    },
    {
      title: 'Internet of Things',
      pillar: 'iot',
      items: [
        { logo: L.espressif, name: 'ESP32 / ESP32-CAM', version: '—', use: 'Cámaras, sensores y paneles' },
        { logo: L.cpp, name: 'C / C++', version: '—', use: 'Firmware de los microcontroladores' },
        { logo: { label: 'HC-SR04', icon: faWaveSquare }, name: 'HC-SR04', version: '—', use: 'Sensor ultrasónico por espacio' },
        { logo: L.mqtt, name: 'MQTT', version: '3.1.1', use: 'Protocolo de sensores y paneles' },
        { logo: L.mosquitto, name: 'Eclipse Mosquitto', version: '2', use: 'Broker MQTT' }
      ]
    },
    {
      title: 'Artificial Intelligence',
      pillar: 'ia',
      items: [
        { logo: { label: 'YOLO26', icon: faCrosshairs }, name: 'YOLO26 (Ultralytics)', version: '8.3', use: 'Detección de placas y vehículos' },
        { logo: { label: 'EasyOCR', icon: faFont }, name: 'EasyOCR', version: '1.7', use: 'Lectura de la placa' },
        { logo: L.pytorch, name: 'PyTorch', version: 'CPU', use: 'Motor de los modelos' },
        { logo: L.opencv, name: 'OpenCV', version: '4', use: 'Procesamiento de imágenes' }
      ]
    },
    {
      title: 'Cloud Computing',
      pillar: 'cloud',
      items: [
        { logo: L.spring, name: 'Spring Cloud', version: '2025.0', use: 'Gateway, Config Server y Eureka' },
        { logo: L.docker, name: 'Docker', version: 'Compose', use: 'Contenedores de datos y broker' },
        { logo: L.gcp, name: 'Google Cloud', version: '—', use: 'Despliegue previsto: Compute Engine y Cloud Storage' }
      ]
    }
  ];

  // ==================== CÓMO FUNCIONA ====================

  scenarios: Scenario[] = [
    {
      title: 'A. Parqueo con barrera',
      hardware: 'ESP32-CAM en la entrada y en la salida',
      ai: 'YOLO26 + EasyOCR leen la placa',
      result: 'Con 85 % de confianza o más registra la estadía sola; con menos, pasa a "Por revisar".',
      icon: faCamera
    },
    {
      title: 'B. Parqueo abierto (calle)',
      hardware: 'Cámara aérea',
      ai: 'YOLO26 con un polígono por espacio',
      result: 'Si el centro de un vehículo cae dentro del polígono, el espacio está ocupado.',
      icon: faMap
    },
    {
      title: 'Sensores HC-SR04',
      hardware: 'Sensor ultrasónico + ESP32 por MQTT',
      ai: 'Distancia de 80 cm o menos = vehículo',
      result: 'Confirma el espacio real del vehículo o libera uno que quedó vacío.',
      icon: faWaveSquare
    },
    {
      title: 'Paneles LED',
      hardware: 'ESP32 + panel LED por MQTT',
      ai: 'Reciben los números al instante',
      result: 'Junto al parqueo: sus disponibles y ocupados. En avenidas: todos los parqueos, uno cada 10 s.',
      icon: faTv
    }
  ];

  // ==================== AVANCE ====================

  /** El porcentaje de cada fase se calcula con los puntos terminados. */
  phases: Phase[] = [
    {
      title: 'Infraestructura y seguridad',
      items: [
        { text: 'Config Server, Eureka y API Gateway', done: true },
        { text: 'JWT con permisos del rol activo', done: true },
        { text: 'Tokens de servicio entre microservicios', done: true },
        { text: 'Secretos fuera del repositorio', done: true }
      ]
    },
    {
      title: 'Gestión de parqueos y operación',
      items: [
        { text: 'Parqueos, zonas y espacios', done: true },
        { text: 'Turnos, tarifas y horario por parqueo', done: true },
        { text: 'Operadores por parqueo o zona', done: true },
        { text: 'Entradas, salidas, revisión de placas y mapa de ocupación', done: true },
        { text: 'Cobro de multas desde el panel', done: false }
      ]
    },
    {
      title: 'Pagos',
      items: [
        { text: 'Tipos de pago, QR y efectivo', done: true },
        { text: 'Pago antes de salir y devoluciones', done: true },
        { text: 'QR dibujado como imagen', done: false },
        { text: 'Comprobante interno de estacionamiento', done: false }
      ]
    },
    {
      title: 'IoT e inteligencia artificial',
      items: [
        { text: 'Cámara → IA → registro de la estadía', done: true },
        { text: 'Sensores y paneles por MQTT', done: true },
        { text: 'Registro mínimo de lecturas en MongoDB', done: true },
        { text: 'Ocupación por visión aérea de punta a punta', done: false },
        { text: 'Modelo ajustado con placas peruanas', done: false }
      ]
    },
    {
      title: 'App móvil',
      items: [
        { text: 'Versión 1 pública: lista, mapa, detalle y espacios', done: true },
        { text: 'Cuenta del conductor con Google', done: false }
      ]
    },
    {
      title: 'Hardware, piloto y nube',
      items: [
        { text: 'Firmware de ESP32-CAM, sensores y paneles', done: false },
        { text: 'Instalación piloto en Huaraz', done: false },
        { text: 'Despliegue en Google Cloud', done: false }
      ]
    }
  ];

  teamMembers = [
    {
      name: 'Edwin Yoner Flores Rupay',
      role: 'Tesista · Desarrollo full stack y arquitectura',
      email: 'edwinyoner@winner-systems.com',
      github: 'edwinyoner',
      linkedin: 'edwinyoner'
    }
  ];

  progressOf(phase: Phase): number {
    const done = phase.items.filter(i => i.done).length;
    return Math.round((done / phase.items.length) * 100);
  }
}
