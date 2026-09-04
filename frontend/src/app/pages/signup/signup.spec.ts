import { provideRouter } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Signup } from './signup';

describe('Signup', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Signup],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
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
    expect(element.querySelector('button[type="submit"]')?.textContent?.trim()).toBe('Criar Conta');
    expect(element.querySelector('a[routerLink="/entrar"]')).toBeTruthy();
  });

  it('should show required errors after an invalid submit', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance as any;

    component.onSubmit();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('#name-error')).toBeTruthy();
    expect(element.querySelector('#email-error')).toBeTruthy();
    expect(element.querySelector('#password-error')).toBeTruthy();
    expect(element.querySelector('#password-confirmation-error')).toBeTruthy();
    expect(element.querySelector('#terms-message')).toBeTruthy();
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
    expect(element.querySelector('#password-match-error')).toBeTruthy();
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

  it('should toggle password field types between password and text', () => {
    const fixture = createComponent();
    const element = fixture.nativeElement as HTMLElement;
    const passwordInput = element.querySelector('#password') as HTMLInputElement;
    const confirmationInput = element.querySelector('#password_confirmation') as HTMLInputElement;
    const toggleCheckbox = element.querySelector('#show-password-toggle') as HTMLInputElement;

    expect(passwordInput.type).toBe('password');
    expect(confirmationInput.type).toBe('password');

    toggleCheckbox.click();
    fixture.detectChanges();

    expect(passwordInput.type).toBe('text');
    expect(confirmationInput.type).toBe('text');
  });

  it('should reject emojis in name, email, and password fields', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance as any;

    component.form.setValue({
      name: '😂😂😂😂😂',
      email: 'user@example.com',
      password: 'password123',
      password_confirmation: 'password123',
      terms: true,
    });
    expect(component.form.get('name')?.hasError('pattern')).toBe(true);

    component.form.patchValue({
      name: 'José de Alencar',
      email: 'emoji😍@example.com',
    });
    expect(component.form.get('name')?.valid).toBe(true);
    expect(component.form.get('email')?.hasError('pattern')).toBe(true);

    component.form.patchValue({
      email: 'valid@example.com',
      password: 'password123😁',
    });
    expect(component.form.get('password')?.hasError('pattern')).toBe(true);
  });
});
