import { Component, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import {
  CardComponent,
  CardBodyComponent,
  CardHeaderComponent,
  ColComponent,
  RowComponent,
  FormDirective,
  FormLabelDirective,
  FormControlDirective,
  FormSelectDirective,
  AccordionModule,
  BadgeComponent
} from '@coreui/angular';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import {
  faHeadset,
  faEnvelope,
  faPhone,
  faLocationDot,
  faGlobe,
  faCircleQuestion,
  faCircleInfo,
  faPaperPlane,
  faLifeRing,
  faBook,
  faListCheck
} from '@fortawesome/free-solid-svg-icons';
import { faWhatsapp } from '@fortawesome/free-brands-svg-icons';
import { CurrentUserService } from '../../core/services/auth-service/current-user.service';

interface Faq {
  question: string;
  answer: string;
  category: string;
}

/** Contacto real de Winner Systems (el mismo del README del repositorio). */
const CONTACT = {
  email: 'contacto@winner-systems.com',
  phone: '+51 931 741 355',
  whatsapp: '51931741355',
  address: 'Jangas, Huaraz, Áncash, Perú',
  web: 'https://www.winner-systems.com'
};

const CATEGORIES: Record<string, string> = {
  access: 'Acceso y cuenta',
  operation: 'Entradas, salidas y revisión de placas',
  payments: 'Pagos y cobros',
  parkings: 'Parqueos, horarios y operadores',
  iot: 'Cámaras, sensores o paneles',
  other: 'Otro'
};

const PRIORITIES: Record<string, string> = {
  low: 'Baja: una consulta',
  medium: 'Media: algo no funciona, pero hay cómo seguir',
  high: 'Alta: no se puede operar un parqueo'
};

@Component({
  selector: 'app-support',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    RouterLink,
    CardComponent,
    CardBodyComponent,
    CardHeaderComponent,
    ColComponent,
    RowComponent,
    FormDirective,
    FormLabelDirective,
    FormControlDirective,
    FormSelectDirective,
    AccordionModule,
    BadgeComponent,
    FontAwesomeModule
  ],
  templateUrl: './support.component.html',
  styleUrls: ['./support.component.css']
})
export class SupportComponent implements OnInit {

  private fb = inject(FormBuilder);
  private currentUser = inject(CurrentUserService);

  icons = {
    headset: faHeadset,
    envelope: faEnvelope,
    phone: faPhone,
    location: faLocationDot,
    web: faGlobe,
    question: faCircleQuestion,
    info: faCircleInfo,
    send: faPaperPlane,
    support: faLifeRing,
    book: faBook,
    checklist: faListCheck,
    whatsapp: faWhatsapp
  };

  contact = CONTACT;
  categories = Object.entries(CATEGORIES).map(([value, label]) => ({ value, label }));
  priorities = Object.entries(PRIORITIES).map(([value, label]) => ({ value, label }));

  readonly messageMax = 1000;

  supportForm = this.fb.nonNullable.group({
    name: ['', [Validators.required, Validators.minLength(3)]],
    email: ['', [Validators.required, Validators.email]],
    category: ['', Validators.required],
    priority: ['medium', Validators.required],
    subject: ['', [Validators.required, Validators.minLength(5), Validators.maxLength(120)]],
    message: ['', [Validators.required, Validators.minLength(20), Validators.maxLength(this.messageMax)]]
  });

  /** Se muestra después de abrir el correo o WhatsApp. */
  opened: 'email' | 'whatsapp' | null = null;

  checklist = [
    'Recarga la página: el panel se actualiza solo, pero una recarga descarta datos viejos.',
    'Revisa el Manual de usuario y las preguntas frecuentes de esta página.',
    'Si no ves un menú o un botón, comprueba con qué rol entraste: los permisos son del rol activo.',
    'Anota qué hacías, la placa o el parqueo involucrado y el mensaje de error exacto.'
  ];

  faqs: Faq[] = [
    {
      category: 'Acceso',
      question: 'No veo un menú o un botón',
      answer: 'Tu rol activo no tiene ese permiso. Pide al administrador que lo añada a tu rol, o cierra sesión y entra con otro rol si tienes varios. Los cambios de permisos se aplican sin volver a entrar.'
    },
    {
      category: 'Acceso',
      question: 'Se cerró mi sesión de repente',
      answer: 'En la pantalla de inicio aparece el motivo: la cuenta fue desactivada, cambió tu contraseña o tu correo, te quitaron el rol con el que entraste o la sesión venció.'
    },
    {
      category: 'Acceso',
      question: 'Olvidé mi contraseña',
      answer: 'En la pantalla de inicio pulsa "¿Olvidaste tu contraseña?". El enlace que llega al correo dura 1 hora.'
    },
    {
      category: 'Operación',
      question: 'No aparece mi parqueo en "Registrar entrada"',
      answer: 'Solo aparecen los parqueos y zonas donde estás de turno ahora. Pide al administrador que revise tu asignación en el detalle del parqueo, en "Operadores".'
    },
    {
      category: 'Operación',
      question: 'Dice que el parqueo no atiende a esta hora',
      answer: 'No hay un turno activo en el horario del parqueo para esta hora. Se configura en el detalle del parqueo, en "Horario".'
    },
    {
      category: 'Operación',
      question: 'Una lectura de la cámara quedó "Por revisar"',
      answer: 'La placa se leyó con menos de 85 % de confianza o el parqueo exige documento. Confírmala o corrígela en "Por revisar"; la barrera espera hasta que se resuelva.'
    },
    {
      category: 'Pagos',
      question: 'No me deja registrar la salida',
      answer: 'Falta pagar. Cobra primero con QR o en efectivo. Si pasaron más de 15 minutos desde el pago, se cobra la diferencia.'
    },
    {
      category: 'Pagos',
      question: 'El QR no se confirma',
      answer: 'El panel consulta el pago cada pocos segundos. El QR vence a los 10 minutos: si venció, genera otro. En este prototipo la pasarela es simulada, con los botones "Simular pago" y "Rechazar".'
    },
    {
      category: 'Espacios',
      question: 'Los disponibles no coinciden con lo que veo',
      answer: 'Revisa en el Mapa de ocupación si hay espacios en mantenimiento, fuera de servicio o una zona inactiva. Si un espacio quedó ocupado sin vehículo, libéralo desde su zona.'
    }
  ];

  ngOnInit(): void {
    // Rellena nombre y correo con el usuario de la sesión (editables)
    const user = this.currentUser.getCurrentUser();
    if (user) {
      this.supportForm.patchValue({
        name: `${user.firstName} ${user.lastName}`.trim(),
        email: user.email
      });
    }
  }

  get messageLength(): number {
    return this.supportForm.controls.message.value.length;
  }

  /** Abre el programa de correo con el mensaje ya escrito. */
  sendByEmail(): void {
    if (!this.validate()) return;
    const subject = `[Smart Parking] ${this.label(CATEGORIES)} - ${this.supportForm.controls.subject.value.trim()}`;
    const url = `mailto:${CONTACT.email}?subject=${encodeURIComponent(subject)}&body=${encodeURIComponent(this.buildBody())}`;
    window.location.href = url;
    this.opened = 'email';
  }

  /** Abre WhatsApp (web o app) con el mensaje ya escrito. */
  sendByWhatsapp(): void {
    if (!this.validate()) return;
    const text = `*Smart Parking - ${this.supportForm.controls.subject.value.trim()}*\n\n${this.buildBody()}`;
    window.open(`https://wa.me/${CONTACT.whatsapp}?text=${encodeURIComponent(text)}`, '_blank', 'noopener');
    this.opened = 'whatsapp';
  }

  isFieldInvalid(name: keyof typeof this.supportForm.controls): boolean {
    const field = this.supportForm.controls[name];
    return field.invalid && field.touched;
  }

  getFieldError(name: keyof typeof this.supportForm.controls): string {
    const field = this.supportForm.controls[name];
    if (field.hasError('required')) return 'Este campo es obligatorio';
    if (field.hasError('email')) return 'Correo no válido';
    if (field.hasError('minlength')) return `Mínimo ${field.errors?.['minlength'].requiredLength} caracteres`;
    if (field.hasError('maxlength')) return `Máximo ${field.errors?.['maxlength'].requiredLength} caracteres`;
    return '';
  }

  private validate(): boolean {
    if (this.supportForm.invalid) {
      this.supportForm.markAllAsTouched();
      return false;
    }
    return true;
  }

  private label(map: Record<string, string>): string {
    const key = map === CATEGORIES ? this.supportForm.controls.category.value : this.supportForm.controls.priority.value;
    return map[key] ?? key;
  }

  /** Cuerpo del mensaje con los datos que ayudan a reproducir el problema. No incluye tokens ni contraseñas. */
  private buildBody(): string {
    const v = this.supportForm.getRawValue();
    const user = this.currentUser.getCurrentUser();
    return [
      v.message.trim(),
      '',
      '---',
      `Nombre: ${v.name.trim()}`,
      `Correo: ${v.email.trim()}`,
      `Categoría: ${this.label(CATEGORIES)}`,
      `Prioridad: ${this.label(PRIORITIES)}`,
      `Roles: ${user?.roles?.join(', ') || '-'}`,
      `Fecha: ${new Date().toLocaleString('es-PE')}`,
      `Navegador: ${navigator.userAgent}`
    ].join('\n');
  }
}
