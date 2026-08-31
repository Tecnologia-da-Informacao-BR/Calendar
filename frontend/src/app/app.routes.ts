import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'signup',
    loadComponent: () => import('./pages/signup/signup').then((m) => m.Signup),
    title: 'Criar conta',
  },
  {
    path: 'cadastro',
    redirectTo: 'signup',
    pathMatch: 'full',
  },
  {
    path: 'entrar',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
    title: 'Entrar',
  },
  {
    path: 'login',
    redirectTo: 'entrar',
    pathMatch: 'full',
  },
  {
    path: 'resetar-senha',
    loadComponent: () =>
      import('./pages/reset-password/reset-password').then((m) => m.ResetPassword),
    title: 'Resetar senha',
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'entrar',
  },
  {
    path: '**',
    redirectTo: 'entrar',
  },
];
