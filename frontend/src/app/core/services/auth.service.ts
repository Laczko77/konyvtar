import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, tap } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse,
  AuthUser,
  LoginRequest,
  MemberResponse,
  RegisterRequest,
  Role,
} from '../models/library.models';

const TOKEN_STORAGE_KEY = 'library_jwt_token';
const USER_STORAGE_KEY = 'library_auth_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);

  private readonly tokenSignal = signal<string | null>(this.readStoredToken());
  private readonly userSignal = signal<AuthUser | null>(this.readStoredUser());

  readonly currentUser = this.userSignal.asReadonly();
  readonly isAuthenticated = computed(() => this.tokenSignal() !== null && !this.isTokenExpired(this.tokenSignal()!));
  readonly isAdmin = computed(() => this.userSignal()?.role === 'ADMIN');

  login(request: LoginRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/login`, request)
      .pipe(tap((response) => this.persistSession(response)));
  }

  register(request: RegisterRequest): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>(`${environment.apiUrl}/auth/register`, request)
      .pipe(tap((response) => this.persistSession(response)));
  }

  refreshProfile(): Observable<MemberResponse> {
    return this.http.get<MemberResponse>(`${environment.apiUrl}/members/me`).pipe(
      tap((member) => {
        const user: AuthUser = {
          email: member.email,
          fullName: member.fullName,
          role: member.role,
        };
        localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
        this.userSignal.set(user);
      })
    );
  }

  logout(redirectToLogin = true): void {
    localStorage.removeItem(TOKEN_STORAGE_KEY);
    localStorage.removeItem(USER_STORAGE_KEY);
    this.tokenSignal.set(null);
    this.userSignal.set(null);
    if (redirectToLogin) {
      this.router.navigate(['/login']);
    }
  }

  getToken(): string | null {
    const token = this.tokenSignal();
    if (!token) {
      return null;
    }
    if (this.isTokenExpired(token)) {
      this.logout(false);
      return null;
    }
    return token;
  }

  private persistSession(response: AuthResponse): void {
    const user: AuthUser = {
      email: response.email,
      fullName: response.fullName,
      role: response.role,
    };
    localStorage.setItem(TOKEN_STORAGE_KEY, response.token);
    localStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user));
    this.tokenSignal.set(response.token);
    this.userSignal.set(user);
  }

  private readStoredToken(): string | null {
    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (!token || this.isTokenExpired(token)) {
      localStorage.removeItem(TOKEN_STORAGE_KEY);
      localStorage.removeItem(USER_STORAGE_KEY);
      return null;
    }
    return token;
  }

  private readStoredUser(): AuthUser | null {
    const token = localStorage.getItem(TOKEN_STORAGE_KEY);
    if (!token || this.isTokenExpired(token)) {
      return null;
    }

    const rawUser = localStorage.getItem(USER_STORAGE_KEY);
    if (rawUser) {
      try {
        return JSON.parse(rawUser) as AuthUser;
      } catch {
        // Fallback to JWT claims if stored user JSON is malformed
      }
    }

    const claims = this.decodeJwtPayload(token);
    if (!claims?.sub || !claims?.role) {
      return null;
    }
    return {
      email: claims.sub,
      fullName: claims.sub,
      role: claims.role as Role,
    };
  }

  private isTokenExpired(token: string): boolean {
    const claims = this.decodeJwtPayload(token);
    if (!claims?.exp) {
      return false;
    }
    return Date.now() >= claims.exp * 1000;
  }

  private decodeJwtPayload(token: string): { sub?: string; role?: string; exp?: number } | null {
    const parts = token.split('.');
    if (parts.length !== 3) {
      return null;
    }
    try {
      const base64 = parts[1].replace(/-/g, '+').replace(/_/g, '/');
      const padded = base64.padEnd(Math.ceil(base64.length / 4) * 4, '=');
      return JSON.parse(atob(padded)) as { sub?: string; role?: string; exp?: number };
    } catch {
      return null;
    }
  }
}
