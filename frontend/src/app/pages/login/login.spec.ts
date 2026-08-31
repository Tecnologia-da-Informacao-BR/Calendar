import { provideRouter } from '@angular/router';
import { TestBed } from '@angular/core/testing';
import { Login } from './login';

describe('Login', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    return fixture;
  }

  it('should render the login fields and entry actions', () => {
    const fixture = createComponent();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('#email')).not.toBeNull();
    expect(element.querySelector('#password')).not.toBeNull();
    expect(element.textContent).toContain('Bem-vindo de volta');
    expect(element.textContent).toContain('Google');
    expect(element.textContent).toContain('GitHub');
  });

  it('should reject an empty form and expose validation messages after submit', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance as any;

    component.onSubmit();
    fixture.detectChanges();

    expect(component.form.invalid).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Informe seu e-mail.');
    expect(fixture.nativeElement.textContent).toContain('Informe sua senha.');
  });

  it('should accept valid credentials and toggle password visibility', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance as any;

    component.form.setValue({
      email: 'test@example.com',
      password: 'secure-password',
    });
    component.togglePassword();
    fixture.detectChanges();

    expect(component.form.valid).toBe(true);
    expect(fixture.nativeElement.querySelector('#password').type).toBe('text');
  });
});
