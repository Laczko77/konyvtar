import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';
import { AuthService } from '../../core/services/auth.service';
import { extractApiError } from '../../core/services/library-api.service';

@Component({
  selector: 'app-login-page',
  imports: [ReactiveFormsModule],
  template: `
    <div class="auth-wrapper">
      <div class="card auth-card">
        <div class="auth-header">
          <h1>Könyvtár- és kölcsönzéskezelő</h1>
          <p class="page-subtitle">Jelentkezz be vagy hozz létre új tagfiókot</p>
        </div>

        <div class="tabs" role="tablist">
          <button
            type="button"
            class="tab-btn"
            [class.active]="mode() === 'login'"
            (click)="switchMode('login')"
          >
            Bejelentkezés
          </button>
          <button
            type="button"
            class="tab-btn"
            [class.active]="mode() === 'register'"
            (click)="switchMode('register')"
          >
            Regisztráció
          </button>
        </div>

        @if (errorMessage()) {
          <div class="alert alert-error" role="alert">{{ errorMessage() }}</div>
        }

        @if (mode() === 'login') {
          <form [formGroup]="loginForm" (ngSubmit)="submitLogin()" class="form-grid">
            <div class="form-field">
              <label for="login-email">Email cím</label>
              <input
                id="login-email"
                type="email"
                class="input"
                formControlName="email"
                placeholder="member@library.com"
                autocomplete="email"
              />
              @if (loginForm.controls.email.touched && loginForm.controls.email.invalid) {
                <span class="field-error">Érvényes email cím megadása kötelező.</span>
              }
            </div>

            <div class="form-field">
              <label for="login-password">Jelszó</label>
              <input
                id="login-password"
                type="password"
                class="input"
                formControlName="password"
                placeholder="••••••••"
                autocomplete="current-password"
              />
              @if (loginForm.controls.password.touched && loginForm.controls.password.invalid) {
                <span class="field-error">A jelszó megadása kötelező.</span>
              }
            </div>

            <button type="submit" class="btn btn-primary" [disabled]="submitting()">
              {{ submitting() ? 'Bejelentkezés...' : 'Bejelentkezés' }}
            </button>
          </form>

          <div class="demo-box">
            <span class="demo-title">Teszt fiókok (seed adatok):</span>
            <div class="demo-actions">
              <button type="button" class="btn btn-secondary btn-sm" (click)="fillDemo('member@library.com', 'Member123!')">
                Tag (member)
              </button>
              <button type="button" class="btn btn-secondary btn-sm" (click)="fillDemo('admin@library.com', 'Admin123!')">
                Admin (admin)
              </button>
            </div>
          </div>
        } @else {
          <form [formGroup]="registerForm" (ngSubmit)="submitRegister()" class="form-grid">
            <div class="form-field">
              <label for="reg-name">Teljes név</label>
              <input
                id="reg-name"
                type="text"
                class="input"
                formControlName="fullName"
                placeholder="Kovács Anna"
                autocomplete="name"
              />
              @if (registerForm.controls.fullName.touched && registerForm.controls.fullName.invalid) {
                <span class="field-error">A teljes név kötelező (max. 150 karakter).</span>
              }
            </div>

            <div class="form-field">
              <label for="reg-email">Email cím</label>
              <input
                id="reg-email"
                type="email"
                class="input"
                formControlName="email"
                placeholder="nev@pelda.hu"
                autocomplete="email"
              />
              @if (registerForm.controls.email.touched && registerForm.controls.email.invalid) {
                <span class="field-error">Érvényes email cím megadása kötelező.</span>
              }
            </div>

            <div class="form-field">
              <label for="reg-password">Jelszó (min. 6 karakter)</label>
              <input
                id="reg-password"
                type="password"
                class="input"
                formControlName="password"
                placeholder="Legalább 6 karakter"
                autocomplete="new-password"
              />
              @if (registerForm.controls.password.touched && registerForm.controls.password.invalid) {
                <span class="field-error">A jelszónak 6 és 100 karakter között kell lennie.</span>
              }
            </div>

            <button type="submit" class="btn btn-primary" [disabled]="submitting()">
              {{ submitting() ? 'Regisztráció...' : 'Fiók létrehozása' }}
            </button>
          </form>
        }
      </div>
    </div>
  `,
  styles: [
    `
      .auth-wrapper {
        min-height: 100vh;
        display: flex;
        align-items: center;
        justify-content: center;
        padding: 24px 16px;
      }
      .auth-card {
        width: 100%;
        max-width: 440px;
        padding: 28px;
      }
      .auth-header {
        margin-bottom: 20px;
        text-align: center;
      }
      .auth-header h1 {
        margin: 0;
        font-size: 1.35rem;
      }
      .tabs {
        display: grid;
        grid-template-columns: 1fr 1fr;
        gap: 6px;
        padding: 4px;
        background: var(--surface-alt);
        border: 1px solid var(--border);
        border-radius: 10px;
        margin-bottom: 18px;
      }
      .tab-btn {
        padding: 8px 12px;
        border: none;
        background: transparent;
        border-radius: 7px;
        font-weight: 600;
        font-size: 0.9rem;
        color: var(--text-muted);
        cursor: pointer;
      }
      .tab-btn.active {
        background: var(--surface);
        color: var(--primary);
        box-shadow: var(--shadow);
      }
      .demo-box {
        margin-top: 18px;
        padding-top: 14px;
        border-top: 1px solid var(--border);
        display: flex;
        flex-direction: column;
        gap: 8px;
      }
      .demo-title {
        font-size: 0.82rem;
        color: var(--text-muted);
      }
      .demo-actions {
        display: flex;
        gap: 8px;
      }
    `,
  ],
})
export class LoginPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  readonly mode = signal<'login' | 'register'>('login');
  readonly submitting = signal(false);
  readonly errorMessage = signal<string | null>(null);

  readonly loginForm = this.fb.nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    password: ['', [Validators.required]],
  });

  readonly registerForm = this.fb.nonNullable.group({
    fullName: ['', [Validators.required, Validators.maxLength(150)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(255)]],
    password: ['', [Validators.required, Validators.minLength(6), Validators.maxLength(100)]],
  });

  switchMode(nextMode: 'login' | 'register'): void {
    this.mode.set(nextMode);
    this.errorMessage.set(null);
  }

  fillDemo(email: string, password: string): void {
    this.loginForm.setValue({ email, password });
    this.errorMessage.set(null);
  }

  submitLogin(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.authService
      .login(this.loginForm.getRawValue())
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => this.router.navigate(['/books']),
        error: (err) => this.errorMessage.set(extractApiError(err, 'Sikertelen bejelentkezés.')),
      });
  }

  submitRegister(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    this.submitting.set(true);
    this.errorMessage.set(null);

    this.authService
      .register(this.registerForm.getRawValue())
      .pipe(finalize(() => this.submitting.set(false)))
      .subscribe({
        next: () => this.router.navigate(['/books']),
        error: (err) => this.errorMessage.set(extractApiError(err, 'Sikertelen regisztráció.')),
      });
  }
}
