import { ChangeDetectionStrategy, Component, signal } from '@angular/core';

type SidebarIcon = 'dashboard' | 'calendar' | 'upcoming' | 'history' | 'settings';

interface SidebarNavItem {
  id: string;
  label: string;
  icon: SidebarIcon;
}

interface SidebarUtilityItem {
  id: string;
  label: string;
}

interface SidebarUser {
  name: string;
  email: string;
  initials: string;
}

@Component({
  selector: 'app-sidebar',
  standalone: true,
  templateUrl: './sidebar.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Sidebar {
  protected readonly navItems: SidebarNavItem[] = [
    { id: 'painel', label: 'Painel', icon: 'dashboard' },
    { id: 'calendario', label: 'Calendário', icon: 'calendar' },
    { id: 'proximos', label: 'Próximos', icon: 'upcoming' },
    { id: 'historico', label: 'Histórico', icon: 'history' },
    { id: 'configuracoes', label: 'Configurações', icon: 'settings' },
  ];

  protected readonly utilityItems: SidebarUtilityItem[] = [
    { id: 'planejador-estudos', label: 'Planejador de Estudos' },
    { id: 'compartilhamento', label: 'Compartilhamento' },
  ];

  protected readonly user: SidebarUser = {
    name: 'Estefânio',
    email: 'estefaniossi@gmail.com',
    initials: 'E',
  };

  // Static navigation: routes are not wired yet, so the active item is local state.
  protected readonly activeItemId = signal('proximos');
  protected readonly isOpen = signal(false);

  protected toggleSidebar(): void {
    this.isOpen.update(v => !v);
  }

  protected onNavClick(event: Event, id: string): void {
    event.preventDefault();
    this.activeItemId.set(id);
    this.isOpen.set(false); // Close sidebar on mobile after clicking a link
  }

  protected onUtilityClick(event: Event): void {
    event.preventDefault();
  }
}
