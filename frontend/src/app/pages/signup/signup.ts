import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { AuthService } from '../../core/services/auth.service';
import { PASSWORD_VALIDATORS } from '../../shared/validators/password';

export type SignupField = 'name' | 'email' | 'password' | 'password_confirmation' | 'terms';

/**
 * Name regex: allows letters (including Portuguese accents), spaces, hyphens, and apostrophes.
 * Rejects emojis, numbers, and special symbols.
 */
export const NAME_PATTERN = /^[a-zA-ZÀ-ÖØ-öø-ÿ\s'-]+$/;

/**
 * Standard email regex: prevents emojis and non-standard symbols.
 */
export const EMAIL_PATTERN = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;

/**
 * Group-level validator to ensure password and confirmation match.
 */
function passwordsMatchValidator(): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get('password')?.value;
    const confirmation = group.get('password_confirmation')?.value;

    if (!confirmation) {
      return null;
    }

    return password === confirmation ? null : { passwordsMismatch: true };
  };
}

@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './signup.html',
  styleUrl: './signup.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Signup {
  private readonly fb = inject(FormBuilder).nonNullable;
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  /** Signal tracking form submission attempts. */
  protected readonly submitted = signal(false);

  /** Signal tracking async loading state during signup request. */
  protected readonly isLoading = signal(false);

  /** Signal tracking API error message to display. */
  protected readonly apiError = signal<string | null>(null);

  /** Signal tracking success message to display. */
  protected readonly successMessage = signal<string | null>(null);

  /** Signal tracking visibility toggle for password fields. */
  protected readonly showPassword = signal(false);

  /** Strongly typed reactive signup form. */
  protected readonly form = this.fb.group(
    {
      name: ['', [Validators.required, Validators.minLength(2), Validators.pattern(NAME_PATTERN)]],
      email: ['', [Validators.required, Validators.email, Validators.pattern(EMAIL_PATTERN)]],
      password: ['', PASSWORD_VALIDATORS],
      password_confirmation: ['', [Validators.required]],
      terms: [false, [Validators.requiredTrue]],
    },
    { validators: passwordsMatchValidator() },
  );

  /** Helper method to check if a specific error message should be displayed. */
  protected showError(field: SignupField, errorCode: string): boolean {
    const control = this.form.get(field);
    if (!control) {
      return false;
    }

    const shouldEvaluate = control.touched || this.submitted();

    if (errorCode === 'passwordsMismatch') {
      return shouldEvaluate && this.form.hasError('passwordsMismatch') && !!control.value;
    }

    return shouldEvaluate && control.hasError(errorCode);
  }

  protected toggleShowPassword(): void {
    this.showPassword.update((val) => !val);
  }

  protected onSubmit(): void {
    this.submitted.set(true);
    this.apiError.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const raw = this.form.getRawValue();
    const sanitizedName = raw.name.trim().replace(/\s+/g, ' ');
    const sanitizedEmail = raw.email.trim().toLowerCase();

    this.isLoading.set(true);

    this.authService
      .signup({
        name: sanitizedName,
        email: sanitizedEmail,
        password: raw.password,
      })
      .subscribe({
        next: (res) => {
          this.isLoading.set(false);
          this.successMessage.set('Cadastro realizado com sucesso! Redirecionando...');

          if (typeof localStorage !== 'undefined') {
            localStorage.setItem('access_token', res.accessToken);
          }

          setTimeout(() => {
            this.router.navigate(['/login']);
          }, 1500);
        },
        error: (err) => {
          this.isLoading.set(false);
          if (err.status === 400) {
            this.apiError.set(
              'Não foi possível concluir o cadastro. Verifique os dados ou se este e-mail já está cadastrado.',
            );
          } else {
            this.apiError.set(
              'Ocorreu um erro ao conectar ao servidor. Tente novamente mais tarde.',
            );
          }
        },
      });
  }
}
