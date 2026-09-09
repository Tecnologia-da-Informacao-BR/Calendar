import { Validators } from '@angular/forms';

export const PASSWORD_PATTERN = /^[\x20-\x7E\u00C0-\u00FF]+$/;

export const PASSWORD_VALIDATORS = [
  Validators.required,
  Validators.minLength(8),
  Validators.pattern(PASSWORD_PATTERN),
];
