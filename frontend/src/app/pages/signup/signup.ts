import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import {
  LucideCalendarDays,
  LucideCircleCheck,
  LucideLockKeyhole,
  LucideMail,
  LucideUser,
  LucideZap,
} from '@lucide/angular';
import {
  AbstractControl,
  FormBuilder,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';

type SignupField = 'name' | 'email' | 'password' | 'password_confirmation' | 'terms';

/**
 * Group-level validator that checks `password_confirmation` matches `password`.
 * Runs on the FormGroup (not a single control) since it needs to compare two fields.
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

/**
 * Signup screen (route: /cadastro).
 *
 * UI-only for now: it builds and validates the form locally but does not call
 * the backend yet. API integration is tracked as a separate issue.
 */
@Component({
  selector: 'app-signup',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    RouterLink,
    LucideCalendarDays,
    LucideCircleCheck,
    LucideLockKeyhole,
    LucideMail,
    LucideUser,
    LucideZap,
  ],
  templateUrl: './signup.html',
  styleUrl: './signup.css',
})
export class Signup {
  private readonly formBuilder = inject(FormBuilder);

  /** Set to true on the first submit attempt, so errors can surface even on untouched fields. */
  protected readonly submitted = signal(false);

  protected readonly form = this.formBuilder.group(
    {
      name: ['', [Validators.required, Validators.minLength(2)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8)]],
      password_confirmation: ['', [Validators.required]],
      terms: [false, [Validators.requiredTrue]],
    },
    { validators: passwordsMatchValidator() },
  );

  /** Whether a given field's error message should currently be shown. */
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

  protected onSubmit(): void {
    this.submitted.set(true);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    // NOTE: API integration is out of scope for this screen (see issue description).
    // Hook the signup request up here once the endpoint is available.
  }
}
