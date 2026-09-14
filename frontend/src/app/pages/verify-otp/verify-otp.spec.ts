import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { VerifyOtp } from './verify-otp';

describe('VerifyOtp', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [VerifyOtp],
      providers: [provideRouter([])],
    }).compileComponents();
  });

  function createComponent() {
    const fixture = TestBed.createComponent(VerifyOtp);
    fixture.detectChanges();
    return fixture;
  }

  it('should create the component', () => {
    const fixture = createComponent();
    expect(fixture.componentInstance).toBeTruthy();
  });

  it('should render the OTP input field', () => {
    const fixture = createComponent();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('input[formControlName="otp"]')).toBeTruthy();
  });

  it('should render the Verificar OTP submit button', () => {
    const fixture = createComponent();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('button[type="submit"]')?.textContent?.trim()).toBe(
      'Verificar OTP',
    );
  });

  it('should render navigation links to login and reset-password pages', () => {
    const fixture = createComponent();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('a[routerLink="/login"]')).toBeTruthy();
    expect(element.querySelector('a[routerLink="/resetar-senha"]')).toBeTruthy();
  });

  it('should show a required error when submitting without a code', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance as any;

    component.onSubmit();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('#otp-error')?.textContent).toContain('Informe o código OTP.');
  });

  it('should show a length error when the code has fewer than 6 digits', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance as any;

    component.form.patchValue({ otp: '123' });
    component.onSubmit();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('#otp-error')?.textContent).toContain(
      'O código deve ter exatamente 6 dígitos.',
    );
  });

  it('should show a pattern error when the code contains non-numeric characters', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance as any;

    component.form.patchValue({ otp: 'abc123' });
    component.onSubmit();
    fixture.detectChanges();

    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('#otp-error')?.textContent).toContain(
      'O código deve conter apenas números.',
    );
  });

  it('should consider the form valid when a 6-digit numeric code is provided', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance as any;

    component.form.patchValue({ otp: '123456' });

    expect(component.form.valid).toBe(true);
  });

  it('should mark the form as invalid when an empty code is submitted', () => {
    const fixture = createComponent();
    const component = fixture.componentInstance as any;

    component.onSubmit();

    expect(component.form.invalid).toBe(true);
  });
});
