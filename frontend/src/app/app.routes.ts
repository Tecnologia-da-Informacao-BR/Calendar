import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'cadastro',
    loadComponent: () => import('./pages/signup/signup').then((m) => m.Signup),
    title: 'Criar conta',
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login').then((m) => m.Login),
    title: 'Entrar',
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'cadastro',
  },
];
