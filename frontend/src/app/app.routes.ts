import { Routes } from '@angular/router';
import { adminGuard, authGuard, guestGuard } from './core/guards/auth.guard';

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [guestGuard],
    loadComponent: () =>
      import('./features/auth/login-page.component').then((m) => m.LoginPageComponent),
  },
  {
    path: 'books',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/books/books-page.component').then((m) => m.BooksPageComponent),
  },
  {
    path: 'loans',
    canActivate: [authGuard],
    loadComponent: () =>
      import('./features/loans/loans-page.component').then((m) => m.LoansPageComponent),
  },
  {
    path: 'members',
    canActivate: [adminGuard],
    loadComponent: () =>
      import('./features/members/members-page.component').then((m) => m.MembersPageComponent),
  },
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'books',
  },
  {
    path: '**',
    redirectTo: 'books',
  },
];
