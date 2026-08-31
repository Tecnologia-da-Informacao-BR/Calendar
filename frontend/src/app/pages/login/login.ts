import { Component, inject, signal } from '@angular/core';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { LucideCalendar } from '@lucide/angular';

type LoginField = 'email' | 'password';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink, LucideCalendar],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly formBuilder = inject(FormBuilder);

  protected readonly submitted = signal(false);
  protected readonly passwordVisible = signal(false);
  protected readonly feedback = signal('');

  protected readonly form = this.formBuilder.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required, Validators.minLength(8)]],
  });

  protected togglePassword(): void {
    this.passwordVisible.update((visible) => !visible);
  }

  protected onForgotPassword(): void {
    this.feedback.set('A recuperação de senha estará disponível em breve.');
  }

  protected onSocialLogin(provider: string): void {
    this.feedback.set(`A entrada com ${provider} estará disponível em breve.`);
  }

  protected onSubmit(): void {
    this.submitted.set(true);
    this.feedback.set('');

    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.feedback.set('Dados válidos. A integração com o servidor será conectada em breve.');
  }

  protected showError(field: LoginField, errorCode: string): boolean {
    const control = this.form.controls[field];
    return (control.touched || this.submitted()) && control.hasError(errorCode);
  }
}
