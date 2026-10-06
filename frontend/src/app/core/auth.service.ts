import { HttpClient } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, map, of, switchMap, tap, throwError } from 'rxjs';

export type Section =
  | 'dashboard' | 'cars' | 'clients' | 'contracts' | 'payments' | 'partners' | 'expenses' | 'reports' | 'employees';

export interface Profile {
  username: string;
  email: string | null;
  role: 'SUPERADMIN' | 'AGENCY_ADMIN' | 'AGENCY_EMPLOYEE' | 'CLIENT';
  sections: Record<Section, boolean>;
}

interface LoginResponse {
  token: string;
  refreshToken: string;
  username: string;
  role: string;
}

const TOKEN = 'cr_token';
const REFRESH = 'cr_refresh';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private router = inject(Router);

  readonly profile = signal<Profile | null>(null);
  readonly isAdmin = computed(() => ['SUPERADMIN', 'AGENCY_ADMIN'].includes(this.profile()?.role ?? ''));

  private refreshing$: Observable<string> | null = null;

  get token(): string | null {
    return localStorage.getItem(TOKEN);
  }

  get hasToken(): boolean {
    return !!this.token;
  }

  can(section: Section): boolean {
    return !!this.profile()?.sections?.[section];
  }

  /** First screen the user is allowed to open (used after login and for forbidden pages). */
  firstAllowedRoute(): string {
    const order: [Section, string][] = [
      ['dashboard', '/dashboard'], ['contracts', '/contracts'], ['cars', '/cars'], ['clients', '/clients'],
      ['expenses', '/expenses'], ['partners', '/partners'], ['employees', '/employees']
    ];
    return order.find(([section]) => this.can(section))?.[1] ?? '/login';
  }

  login(username: string, password: string): Observable<Profile> {
    return this.http.post<LoginResponse>('/api/auth/login', { username, password }).pipe(
      tap(res => this.store(res.token, res.refreshToken)),
      switchMap(() => this.loadProfile()),
      map(profile => {
        if (!profile) {
          throw new Error('Could not load the profile');
        }
        return profile;
      })
    );
  }

  loadProfile(): Observable<Profile | null> {
    return this.http.get<Profile>('/api/auth/me').pipe(
      tap(profile => this.profile.set(profile)),
      catchError(() => {
        this.clear();
        return of(null);
      })
    );
  }

  /** One refresh at a time, shared by every request that hit an expired token. */
  refresh(): Observable<string> {
    const refreshToken = localStorage.getItem(REFRESH);
    if (!refreshToken) {
      return throwError(() => new Error('No refresh token'));
    }
    if (!this.refreshing$) {
      this.refreshing$ = this.http.post<LoginResponse>('/api/auth/refresh', { refreshToken }).pipe(
        tap(res => this.store(res.token, res.refreshToken)),
        map(res => res.token),
        tap({ finalize: () => (this.refreshing$ = null) }),
        catchError(err => {
          this.refreshing$ = null;
          return throwError(() => err);
        })
      );
    }
    return this.refreshing$;
  }

  logout(): void {
    this.clear();
    this.router.navigate(['/login']);
  }

  private store(token: string, refresh: string): void {
    localStorage.setItem(TOKEN, token);
    localStorage.setItem(REFRESH, refresh);
  }

  private clear(): void {
    localStorage.removeItem(TOKEN);
    localStorage.removeItem(REFRESH);
    this.profile.set(null);
  }
}
