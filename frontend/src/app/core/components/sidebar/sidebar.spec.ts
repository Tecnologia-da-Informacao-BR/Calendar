import { TestBed } from '@angular/core/testing';
import { Sidebar } from './sidebar';

describe('Sidebar', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Sidebar],
    }).compileComponents();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(Sidebar);
    fixture.detectChanges();
    return fixture;
  }

  it('should render the brand header and all navigation items', () => {
    const element = createComponent().nativeElement as HTMLElement;

    expect(element.textContent).toContain('Project A');
    expect(element.textContent).toContain('Produtividade Pessoal');
    expect(element.textContent).toContain('Painel');
    expect(element.textContent).toContain('Calendário');
    expect(element.textContent).toContain('Próximos');
    expect(element.textContent).toContain('Histórico');
    expect(element.textContent).toContain('Configurações');
    expect(element.textContent).toContain('Planejador de Estudos');
    expect(element.textContent).toContain('Compartilhamento');
  });

  it('should render the new event button and the static user profile', () => {
    const element = createComponent().nativeElement as HTMLElement;

    const buttons = Array.from(element.querySelectorAll('button'));
    const newEventButton = buttons.find(b => b.textContent?.includes('Novo Evento'));
    expect(newEventButton).toBeTruthy();

    expect(element.textContent).toContain('Estefânio');
    expect(element.textContent).toContain('estefaniossi@gmail.com');
  });

  it('should highlight the active navigation item and update it on click', () => {
    const fixture = createComponent();
    const element = fixture.nativeElement as HTMLElement;

    const activeLink = element.querySelector('a[aria-current="page"]');
    expect(activeLink?.textContent).toContain('Próximos');

    const painelLink = Array.from(element.querySelectorAll('a')).find((link) =>
      link.textContent?.includes('Painel'),
    );
    painelLink?.click();
    fixture.detectChanges();

    const updatedActiveLink = element.querySelector('a[aria-current="page"]');
    expect(updatedActiveLink?.textContent).toContain('Painel');
  });
});
