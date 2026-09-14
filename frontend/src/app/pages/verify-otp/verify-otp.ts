import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';

/** Regex that accepts exactly 6 digits. */
const OTP_PATTERN = /^\d{6}$/;

type OtpField = 'otp';

@Component({
  selector: 'app-verify-otp',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './verify-otp.html',
  styleUrl: './verify-otp.css',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class VerifyOtp {
  private readonly formBuilder = inject(NonNullableFormBuilder);

  /** Tracks whether the form has been submitted at least once. */
  protected readonly submitted = signal(false);

  /** Placeholder for a general/API error message. */
  protected readonly generalError = signal<string | null>(null);

  protected readonly form = this.formBuilder.group({
    otp: [
      '',
      [
        Validators.required,
        Validators.minLength(6),
        Validators.maxLength(6),
        Validators.pattern(OTP_PATTERN),
      ],
    ],
  });

  /** Returns true when a field error should be displayed. */
  protected showError(field: OtpField, errorCode: string): boolean {
    const control = this.form.controls[field];
    const shouldShow = control.touched || this.submitted();
    return shouldShow && control.hasError(errorCode);
  }

  protected onSubmit(): void {
    this.submitted.set(true);
    this.generalError.set(null);

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    // API integration is intentionally outside the scope of issue #29.
  }
}
