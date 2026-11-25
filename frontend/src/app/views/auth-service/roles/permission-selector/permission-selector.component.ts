import { Component, EventEmitter, Input, Output } from '@angular/core';
import { Permission, PermissionGroup } from '../../../../core/models/auth-service/permission.model';
import { getPermissionActionLabel } from '../../../../core/utils/permission-module.util';

/** Módulos de una misma sección (p. ej. "Seguridad y accesos") */
interface PermissionSection {
  name: string;
  groups: PermissionGroup[];
}

/**
 * Selector de permisos agrupado por sección y módulo.
 * Se usa en crear y editar rol. No guarda estado propio: recibe los IDs
 * seleccionados y emite un Set nuevo en cada cambio ([(selectedIds)]).
 */
@Component({
  selector: 'app-permission-selector',
  standalone: true,
  imports: [],
  templateUrl: './permission-selector.component.html',
  styleUrl: './permission-selector.component.css',
  host: { '[class.is-disabled]': 'disabled' }
})
export class PermissionSelectorComponent {
  @Input() set groups(value: PermissionGroup[]) {
    this.allGroups = value ?? [];
    this.rebuildSections();
  }
  @Input() selectedIds: Set<number> = new Set();
  @Input() disabled = false;
  @Output() selectedIdsChange = new EventEmitter<Set<number>>();

  sections: PermissionSection[] = [];
  searchTerm = '';
  private allGroups: PermissionGroup[] = [];

  readonly actionLabel = getPermissionActionLabel;

  get totalCount(): number {
    return this.allGroups.reduce((sum, g) => sum + g.permissions.length, 0);
  }

  // ---------- Búsqueda ----------

  onSearch(event: Event): void {
    this.searchTerm = (event.target as HTMLInputElement).value;
    this.rebuildSections();
  }

  clearSearch(): void {
    this.searchTerm = '';
    this.rebuildSections();
  }

  /** Filtra por módulo, acción, código o descripción, sin distinguir tildes */
  private rebuildSections(): void {
    const term = normalize(this.searchTerm.trim());
    const sections: PermissionSection[] = [];

    for (const group of this.allGroups) {
      const moduleMatches = term !== '' && normalize(group.label ?? group.module).includes(term);
      const permissions = term === '' || moduleMatches
        ? group.permissions
        : group.permissions.filter(p =>
            normalize(`${p.name} ${p.description ?? ''} ${this.actionLabel(p)}`).includes(term));
      if (permissions.length === 0) continue;

      const sectionName = group.section ?? 'Otros';
      let section = sections.find(s => s.name === sectionName);
      if (!section) {
        section = { name: sectionName, groups: [] };
        sections.push(section);
      }
      section.groups.push({ ...group, permissions });
    }
    this.sections = sections;
  }

  // ---------- Selección ----------

  isSelected(permission: Permission): boolean {
    return this.selectedIds.has(permission.id);
  }

  selectedInGroup(group: PermissionGroup): number {
    return group.permissions.filter(p => this.selectedIds.has(p.id)).length;
  }

  isGroupFull(group: PermissionGroup): boolean {
    return group.permissions.length > 0 && this.selectedInGroup(group) === group.permissions.length;
  }

  isGroupPartial(group: PermissionGroup): boolean {
    const count = this.selectedInGroup(group);
    return count > 0 && count < group.permissions.length;
  }

  toggle(permission: Permission): void {
    this.update(ids => ids.has(permission.id) ? ids.delete(permission.id) : ids.add(permission.id));
  }

  /** Marca o desmarca todo el módulo (solo lo visible si hay búsqueda) */
  toggleGroup(group: PermissionGroup): void {
    const select = !this.isGroupFull(group);
    this.update(ids => group.permissions.forEach(p => select ? ids.add(p.id) : ids.delete(p.id)));
  }

  /** Marca lo visible: todo, o solo lo que coincide con la búsqueda */
  selectAllVisible(): void {
    this.update(ids => this.sections.forEach(s => s.groups.forEach(g => g.permissions.forEach(p => ids.add(p.id)))));
  }

  clearAll(): void {
    this.update(ids => ids.clear());
  }

  private update(change: (ids: Set<number>) => void): void {
    if (this.disabled) return;
    const next = new Set(this.selectedIds);
    change(next);
    this.selectedIds = next;
    this.selectedIdsChange.emit(next);
  }
}

function normalize(value: string): string {
  return value.toLowerCase().normalize('NFD').replace(/[̀-ͯ]/g, '');
}
