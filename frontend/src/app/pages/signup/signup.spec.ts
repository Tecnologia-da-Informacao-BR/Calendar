import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { Signup } from './signup';

describe('Signup', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Signup],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(Signup);
    fixture.detectChanges();
    return fixture;
  }

  it('should render the required controls and login navigation', () => {
    const fixture = createComponent();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelectorAll('input[formControlName]').length).toBe(5);
    expect(element.querySelector('button[type="submit"]')?.textContent?.trim()).toBe('Cadastrar');
    expect(element.querySelector('a[routerLink="/login"]')).toBeTruthy();
  });

  it('should show required errors after an invalid submit', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance as any;

    component.onSubmit();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('#name-error.is-visible')).toBeTruthy();
    expect(element.querySelector('#email-error.is-visible')).toBeTruthy();
    expect(element.querySelector('#password-error.is-visible')).toBeTruthy();
    expect(element.querySelector('#password-confirmation-error.is-visible')).toBeTruthy();
    expect(element.querySelector('#terms-message.is-visible')).toBeTruthy();
  });

  it('should show a mismatch error when passwords differ', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance as any;

    component.form.setValue({
      name: 'Test User',
      email: 'test@example.com',
      password: 'secure-password',
      password_confirmation: 'different-password',
      terms: true,
    });
    component.onSubmit();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('#password-match-error.is-visible')).toBeTruthy();
    expect(component.form.invalid).toBe(true);
  });

  it('should accept a valid form when passwords match', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance as any;

    component.form.setValue({
      name: 'Test User',
      email: 'test@example.com',
      password: 'secure-password',
      password_confirmation: 'secure-password',
      terms: true,
    });

    expect(component.form.valid).toBe(true);
  });
});
