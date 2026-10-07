import { Component, inject, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { retry, timer } from 'rxjs';
import { AuthService } from '../../core/auth.service';

/** Free hosting puts the API to sleep: the proxy answers 502-504 (or nothing) until it is back. */
const WAKING = [0, 502, 503, 504];

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatButtonModule, MatIconModule],
  template: `
    <div class="login-page">
      <form class="login-card" [formGroup]="form" (ngSubmit)="submit()">
        <div class="logo"><mat-icon>directions_car</mat-icon></div>
        <h1>Car Rental Manager</h1>
        <p class="muted">Sign in to manage your fleet, clients and contracts</p>

        <mat-form-field appearance="outline">
          <mat-label>Username</mat-label>
          <input matInput formControlName="username" autocomplete="username" autofocus>
        </mat-form-field>
        <mat-form-field appearance="outline">
          <mat-label>Password</mat-label>
          <input matInput [type]="show() ? 'text' : 'password'" formControlName="password" autocomplete="current-password">
          <button mat-icon-button matSuffix type="button" (click)="show.set(!show())" tabindex="-1">
            <mat-icon>{{ show() ? 'visibility_off' : 'visibility' }}</mat-icon>
          </button>
        </mat-form-field>

        @if (waking()) { <p class="muted">Waking up the free demo server. The first sign-in can take a few minutes, please keep this page open.</p> }
        @if (error()) { <p class="err">{{ error() }}</p> }
        <button mat-flat-button color="primary" type="submit" [disabled]="form.invalid || loading()">
          {{ loading() ? 'Signing in...' : 'Sign in' }}
        </button>
      </form>
    </div>
  `
})
export class LoginComponent {
  private auth = inject(AuthService);
  private router = inject(Router);

  show = signal(false);
  loading = signal(false);
  error = signal('');
  waking = signal(false);
  form = new FormGroup({
    username: new FormControl('', Validators.required),
    password: new FormControl('', Validators.required)
  });

  constructor() {
    // Start waking the API while the visitor types; the answer itself does not matter.
    fetch('/api/auth/login').catch(() => undefined);
  }

  submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.loading.set(true);
    this.error.set('');
    const { username, password } = this.form.getRawValue();
    this.auth.login(username!, password!).pipe(
      // ~3.5 minutes of retries while the sleeping API starts; a real answer (401…) stops them
      retry({
        count: 40,
        delay: (err: HttpErrorResponse) => {
          if (!WAKING.includes(err?.status)) throw err;
          this.waking.set(true);
          return timer(5000);
        }
      })
    ).subscribe({
      next: () => this.router.navigateByUrl(this.auth.firstAllowedRoute()),
      error: err => {
        this.loading.set(false);
        this.waking.set(false);
        this.error.set(err?.status === 401 ? 'Wrong username or password.' : 'The demo server is not answering yet. Please try again in a minute.');
      }
    });
  }
}
