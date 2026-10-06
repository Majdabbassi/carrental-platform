import { BreakpointObserver } from '@angular/cdk/layout';
import { CommonModule } from '@angular/common';
import { Component, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { AuthService, Section } from '../core/auth.service';

interface NavItem { path: string; label: string; icon: string; section: Section; }

const NAV: NavItem[] = [
  { path: '/dashboard', label: 'Dashboard', icon: 'dashboard', section: 'dashboard' },
  { path: '/calendar', label: 'Calendar', icon: 'calendar_month', section: 'contracts' },
  { path: '/contracts', label: 'Contracts', icon: 'description', section: 'contracts' },
  { path: '/cars', label: 'Cars', icon: 'directions_car', section: 'cars' },
  { path: '/clients', label: 'Clients', icon: 'groups', section: 'clients' },
  { path: '/partners', label: 'Companies', icon: 'handshake', section: 'partners' },
  { path: '/expenses', label: 'Expenses', icon: 'receipt_long', section: 'expenses' },
  { path: '/pricing-rules', label: 'Pricing', icon: 'sell', section: 'employees' },
  { path: '/employees', label: 'Employees', icon: 'badge', section: 'employees' }
];

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule, MatIconModule,
    MatButtonModule, MatMenuModule],
  template: `
    <mat-sidenav-container class="shell">
      <mat-sidenav [mode]="mobile() ? 'over' : 'side'" [opened]="!mobile() || open()" (closedStart)="open.set(false)" class="sidenav">
        <div class="brand"><mat-icon>directions_car</mat-icon><span>Car Rental</span></div>
        <nav>
          @for (item of items(); track item.path) {
            <a [routerLink]="item.path" routerLinkActive="active" (click)="mobile() && open.set(false)">
              <mat-icon>{{ item.icon }}</mat-icon><span>{{ item.label }}</span>
            </a>
          }
        </nav>
      </mat-sidenav>

      <mat-sidenav-content>
        <mat-toolbar class="topbar">
          @if (mobile()) {
            <button mat-icon-button (click)="open.set(!open())"><mat-icon>menu</mat-icon></button>
          }
          <span class="spacer"></span>
          <button mat-button [matMenuTriggerFor]="menu" class="user">
            <mat-icon>account_circle</mat-icon>
            <span class="who">{{ auth.profile()?.username }} <small>{{ roleLabel() }}</small></span>
            <mat-icon>arrow_drop_down</mat-icon>
          </button>
          <mat-menu #menu="matMenu">
            <button mat-menu-item (click)="auth.logout()"><mat-icon>logout</mat-icon> Sign out</button>
          </mat-menu>
        </mat-toolbar>
        <main class="content"><router-outlet /></main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `
})
export class ShellComponent {
  readonly auth = inject(AuthService);
  mobile = toSignal(inject(BreakpointObserver).observe('(max-width: 900px)').pipe(map(state => state.matches)), { initialValue: false });
  open = signal(false);

  items = () => NAV.filter(item => this.auth.can(item.section));

  roleLabel(): string {
    switch (this.auth.profile()?.role) {
      case 'SUPERADMIN': return 'Super admin';
      case 'AGENCY_ADMIN': return 'Agency admin';
      case 'AGENCY_EMPLOYEE': return 'Employee';
      default: return '';
    }
  }
}
