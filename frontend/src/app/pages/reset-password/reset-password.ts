import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import {
  AbstractControl,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
  NonNullableFormBuilder,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { PASSWORD_VALIDATORS } from '../../shared/validators/password';
type ResetPasswordField = 'password' | 'password_confirmation';
function passwordsMatchValidator(): ValidatorFn {
  return (group: AbstractControl): ValidationErrors | null => {
    const password = group.get('password')?.value;
    const passwordConfirmation = group.get('password_confirmation')?.value;

    if (!passwordConfirmation) {
      return null;
    }

    return password === passwordConfirmation ? null : { passwordsMismatch: true };
  };
}

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './reset-password.html',
  styleUrl: './reset-password.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResetPassword {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly submitted = signal(false);
  protected readonly showPassword = signal(false);
  protected readonly generalError = signal<string | null>(null);

  protected readonly form = this.formBuilder.group(
    {
      password: ['', PASSWORD_VALIDATORS],
      password_confirmation: ['', Validators.required],
    },
    { validators: passwordsMatchValidator() },
  );

  protected showError(field: ResetPasswordField, errorCode: string): boolean {
    const control = this.form.controls[field];
    const shouldShow = control.touched || this.submitted();

    if (errorCode === 'passwordsMismatch') {
      return shouldShow && this.form.hasError('passwordsMismatch') && !!control.value;
    }

    return shouldShow && control.hasError(errorCode);
  }

  protected togglePasswordVisibility(): void {
    this.showPassword.update((isVisible) => !isVisible);
  }

  protected onSubmit(): void {
    this.submitted.set(true);
    this.generalError.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    // API integration is intentionally outside the scope of issue #30.
  }
}
